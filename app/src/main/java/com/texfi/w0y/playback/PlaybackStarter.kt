package com.texfi.w0y.playback

import com.texfi.w0y.data.ExplicitFallback
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.RecommendationRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.recs.FlowChoice
import com.texfi.w0y.recs.FlowEngine
import com.texfi.w0y.recs.FlowNow
import com.texfi.w0y.recs.FlowSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Единая точка запуска воспроизведения.
 *
 * Все экраны включают треки через неё, а не через плеер напрямую: выбранный
 * режим очереди должен действовать одинаково откуда угодно — из поиска, из
 * плейлиста, с плитки на главной.
 */
@Singleton
class PlaybackStarter @Inject constructor(
    private val player: PlayerConnection,
    private val settings: SettingsRepository,
    private val recommendations: RecommendationRepository,
    private val youtube: YouTubeRepository,
    private val queueStore: QueueStore,
    private val flowSession: FlowSession,
    private val flowEngine: javax.inject.Provider<FlowEngine>,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var radioJob: Job? = null
    private var cleanJob: Job? = null

    private val _mode = MutableStateFlow(QueueMode.ORDER)
    val mode: StateFlow<QueueMode> = _mode.asStateFlow()

    private val _loadingRadio = MutableStateFlow(false)
    val loadingRadio: StateFlow<Boolean> = _loadingRadio.asStateFlow()

    /** Идёт поиск чистой версии — пользователю видно, почему пауза перед стартом. */
    private val _findingClean = MutableStateFlow(false)
    val findingClean: StateFlow<Boolean> = _findingClean.asStateFlow()

    private var current: W0ySettings = W0ySettings()

    /** Играет очередь fl0w: трек из собранного им. null — обычное воспроизведение. */
    val flow: StateFlow<FlowNow?> =
        combine(flowSession.now, player.state) { now, state -> now?.takeIf { state.song?.id in it.ids } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Для какого трека уже подобрано продолжение — чтобы не просить дважды. */
    private var extendedFor: String? = null

    /**
     * Очередь — это плейлист или альбом: только его треки, без
     * дозаполнения рекомендациями. Снимается, когда режим «рекомендации»
     * включают вручную из плеера.
     */
    private var collection = false

    init {
        // Восстановленная после перезапуска очередь помнит, была ли она плейлистом.
        scope.launch { collection = queueStore.collection() }
        // Режим держим под рукой готовым значением: решать, что играть
        // дальше, приходится в момент нажатия, и ждать чтение настроек там
        // нельзя — это прямая задержка между нажатием и звуком.
        scope.launch {
            settings.settings.collect {
                current = it
                _mode.value = it.queueMode
            }
        }
        // В режиме рекомендаций очередь не должна заканчиваться: дойдя до
        // последнего трека, продолжаем от него же. Иначе «дальше похожее»
        // упиралось бы в тишину через двадцать пять треков.
        scope.launch {
            player.state.collect { state ->
                val song = state.song ?: return@collect
                if (state.currentIndex < state.queue.lastIndex) {
                    extendedFor = null
                    return@collect
                }
                if (extendedFor == song.id) return@collect
                // fl0w кончился — дособираем по тому же выбору, а не радио от последнего трека.
                flowSession.now.value?.takeIf { song.id in it.ids }?.let {
                    extendWithFlow(song, it)
                    return@collect
                }
                val radio = (_mode.value == QueueMode.RADIO && !collection) || current.recInsteadOfList
                when {
                    radio -> extendWithRadio(song)

                    // Список кончился: что дальше — выбор пользователя. Повтор и
                    // «стоп» не трогают сеть, продолжение — один запрос на трек.
                    state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF -> Unit

                    current.recEndMode == com.texfi.w0y.data.RecEndMode.CONTINUE -> extendWithRadio(song)

                    current.recEndMode == com.texfi.w0y.data.RecEndMode.REPEAT && state.queue.size > 1 -> {
                        extendedFor = song.id
                        player.replaceUpcoming(state.queue)
                    }

                    else -> Unit
                }
            }
        }
    }

    fun play(songs: List<SongItem>, index: Int) {
        flowSession.stop()
        setCollection(false)
        launch(songs, index)
    }

    /**
     * Запуск плейлиста или альбома: в очередь попадают только его треки.
     * Режим «рекомендации» здесь играет плейлист по порядку — дозаполнять
     * чужими треками то, что человек собрал сам, нельзя. Вперемешку
     * остаётся вперемешку.
     */
    fun playCollection(songs: List<SongItem>, index: Int) {
        flowSession.stop()
        setCollection(true)
        launch(songs, index)
    }

    /**
     * Кнопка «перемешать» у альбома или артиста: их треки вперемешку и
     * только они — явная кнопка сильнее выбранного режима очереди.
     */
    fun shuffleCollection(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        flowSession.stop()
        setCollection(true)
        radioJob?.cancel()
        player.play(prepare(songs).shuffled(), 0)
    }

    private fun setCollection(value: Boolean) {
        collection = value
        scope.launch { queueStore.setCollection(value) }
    }

    private fun launch(songs: List<SongItem>, index: Int) {
        if (songs.isEmpty()) return
        val chosen = songs[index.coerceIn(songs.indices)]
        radioJob?.cancel()
        if (current.cleanMode && chosen.explicit) {
            startClean(songs, chosen)
            return
        }
        start(prepare(songs), chosen)
    }

    /**
     * Запуск в режиме «без мата»: сначала ищем официальную чистую версию,
     * и только если её нет — поступаем так, как выбрал пользователь.
     * Поиск занимает доли секунды, но он до звука: включить сначала
     * матерную версию, а потом «исправиться» — хуже, чем подождать.
     */
    private fun startClean(songs: List<SongItem>, chosen: SongItem) {
        cleanJob?.cancel()
        cleanJob =
            scope.launch {
                _findingClean.value = true
                val clean =
                    runCatching { withContext(Dispatchers.IO) { youtube.cleanVersion(chosen) } }
                        .onFailure { Timber.w(it, "Чистая версия не искалась") }
                        .getOrNull()
                _findingClean.value = false
                when {
                    clean != null -> start(prepare(songs.map { if (it.id == chosen.id) clean else it }), clean)

                    current.explicitFallback == ExplicitFallback.SKIP -> {
                        // Пропускаем к ближайшему подходящему, а не молчим.
                        val queue = prepare(songs)
                        if (queue.isNotEmpty()) start(queue, queue.first())
                    }

                    else -> start(prepare(songs), chosen)
                }
            }
    }

    /**
     * Очередь под текущие правила: с «пропускать» помеченные записи из неё
     * убираются целиком, иначе следующий же трек снова окажется матерным.
     */
    private fun prepare(songs: List<SongItem>): List<SongItem> =
        if (current.cleanMode && current.explicitFallback == ExplicitFallback.SKIP) {
            songs.filterNot { it.explicit }.ifEmpty { songs }
        } else {
            songs
        }

    private fun start(songs: List<SongItem>, chosen: SongItem) {
        val index = songs.indexOfFirst { it.id == chosen.id }.coerceAtLeast(0)
        val mode =
            when {
                current.recInsteadOfList -> QueueMode.RADIO
                collection && _mode.value == QueueMode.RADIO -> QueueMode.ORDER
                else -> _mode.value
            }
        when (mode) {
            QueueMode.ORDER -> player.play(songs, index)

            QueueMode.SHUFFLE -> {
                // Выбранный трек остаётся первым: человек нажал именно на
                // него, а не «включи что-нибудь».
                val rest = songs.filterNot { it.id == chosen.id }.shuffled()
                player.play(listOf(chosen) + rest, 0)
            }

            QueueMode.RADIO -> {
                // Звук начинается сразу, рекомендации догружаются следом:
                // ждать сеть перед первым тактом — недопустимо.
                player.play(listOf(chosen), 0)
                extendWithRadio(chosen)
            }
        }
    }

    /** Смена режима на ходу: перестраивает хвост очереди, не трогая текущий трек. */
    fun applyMode(mode: QueueMode) {
        scope.launch { settings.setQueueMode(mode) }
        _mode.value = mode
        // Режим включили руками — значит, рекомендации хотят и для плейлиста.
        if (mode == QueueMode.RADIO) setCollection(false)
        radioJob?.cancel()
        val current = player.state.value.song ?: return
        when (mode) {
            QueueMode.ORDER -> Unit
            QueueMode.SHUFFLE -> player.replaceUpcoming(player.upcoming().shuffled())
            QueueMode.RADIO -> extendWithRadio(current)
        }
    }

    /** MORE LIKE THIS: трек играет первым, дальше — радио от него. */
    fun radioFrom(song: SongItem) {
        flowSession.stop()
        setCollection(false)
        radioJob?.cancel()
        player.play(listOf(song), 0)
        extendWithRadio(song)
    }

    /**
     * Запуск fl0w: очередь играет ровно как собрана — режим очереди («рекомендации»,
     * «перемешать») её не трогает, иначе от сорока треков оставалось радио первого.
     */
    fun playFlow(songs: List<SongItem>, choice: FlowChoice, russian: Boolean) {
        val queue = prepare(songs)
        if (queue.isEmpty()) return
        radioJob?.cancel()
        setCollection(false)
        flowSession.start(FlowNow(choice, russian, queue.mapTo(HashSet()) { it.id }))
        player.play(queue, 0)
    }

    private fun extendWithFlow(seed: SongItem, now: FlowNow) {
        extendedFor = seed.id
        radioJob =
            scope.launch {
                _loadingRadio.value = true
                val played = player.state.value.queue.mapTo(HashSet()) { it.id }
                val next =
                    runCatching { withContext(Dispatchers.IO) { flowEngine.get().build(now.choice, now.russian, exclude = played) } }
                        .onFailure { Timber.w(it, "fl0w не дособрался") }
                        .getOrNull()
                        ?.items
                        ?.map { it.song }
                        ?.filterNot { it.id in played }
                        ?.let(::prepare)
                        .orEmpty()
                if (next.isNotEmpty()) {
                    flowSession.extend(next.map { it.id })
                    player.replaceUpcoming(next)
                }
                _loadingRadio.value = false
            }
    }

    private fun extendWithRadio(seed: SongItem) {
        extendedFor = seed.id
        radioJob =
            scope.launch {
                _loadingRadio.value = true
                // Сеть на холодном старте может не ответить с первого раза: одна повторная попытка
                // через пару секунд, иначе очередь так и оставалась из одного трека.
                repeat(RADIO_ATTEMPTS) { attempt ->
                    if (attempt > 0) {
                        kotlinx.coroutines.delay(RADIO_RETRY_MS)
                        // Пока ждали, включили другое — продолжение уже не для этого трека.
                        if (player.state.value.song?.id != seed.id) {
                            _loadingRadio.value = false
                            return@launch
                        }
                    }
                    val played = (player.state.value.queue.map { it.id } + seed.id).toSet()
                    val next =
                        runCatching {
                            withContext(Dispatchers.IO) { recommendations.continuation(seed, exclude = played) }
                        }.onFailure { Timber.w(it, "Рекомендации для ${seed.id} не пришли") }
                            .getOrDefault(emptyList())
                            .let(::prepare)
                    if (next.isNotEmpty()) {
                        player.replaceUpcoming(next)
                        _loadingRadio.value = false
                        return@launch
                    }
                }
                _loadingRadio.value = false
            }
    }

    private companion object {
        const val RADIO_ATTEMPTS = 2
        const val RADIO_RETRY_MS = 2_500L
    }
}
