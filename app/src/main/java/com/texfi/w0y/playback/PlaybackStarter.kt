package com.texfi.w0y.playback

import com.texfi.w0y.data.ExplicitFallback
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.RecommendationRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.data.YouTubeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        setCollection(true)
        launch(songs, index)
    }

    /**
     * Кнопка «перемешать» у альбома или артиста: их треки вперемешку и
     * только они — явная кнопка сильнее выбранного режима очереди.
     */
    fun shuffleCollection(songs: List<SongItem>) {
        if (songs.isEmpty()) return
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
        setCollection(false)
        radioJob?.cancel()
        player.play(listOf(song), 0)
        extendWithRadio(song)
    }

    private fun extendWithRadio(seed: SongItem) {
        extendedFor = seed.id
        radioJob =
            scope.launch {
                _loadingRadio.value = true
                val played = player.state.value.queue.map { it.id }.toSet()
                val next =
                    runCatching {
                        withContext(Dispatchers.IO) { recommendations.continuation(seed, exclude = played) }
                    }.onFailure { Timber.w(it, "Рекомендации для ${seed.id} не пришли") }
                        .getOrDefault(emptyList())
                        .let(::prepare)
                if (next.isNotEmpty()) player.replaceUpcoming(next)
                _loadingRadio.value = false
            }
    }
}
