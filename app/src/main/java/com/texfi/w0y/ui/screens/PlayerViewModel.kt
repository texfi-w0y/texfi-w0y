package com.texfi.w0y.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.EditKind
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.Lyrics
import com.texfi.w0y.data.LyricsRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SoundPreset
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SoundProfile
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.playback.AudioDevicesRepository
import com.texfi.w0y.playback.AudioOutput
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.PlayerConnection
import com.texfi.w0y.share.ShareCardRenderer
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: PlayerConnection,
    private val library: LibraryRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: SettingsRepository,
    private val downloads: DownloadsRepository,
    private val playback: PlaybackStarter,
    private val shareCards: ShareCardRenderer,
    private val youtube: YouTubeRepository,
    private val httpClient: io.ktor.client.HttpClient,
    private val recs: com.texfi.w0y.recs.RecEngine,
    devices: AudioDevicesRepository,
    /** Отвод звука для визуализатора. */
    val spectrum: com.texfi.w0y.playback.SpectrumBus,
) : ViewModel() {
    // ---- «Похожее»: набор подбирается в фоне при смене трека, одним запросом.

    private val _similar = MutableStateFlow<com.texfi.w0y.recs.RecSet?>(null)
    val similar: StateFlow<com.texfi.w0y.recs.RecSet?> = _similar.asStateFlow()

    private val _similarLoading = MutableStateFlow(false)
    val similarLoading: StateFlow<Boolean> = _similarLoading.asStateFlow()

    private var similarJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            player.state
                .map { it.song }
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collectLatest { song ->
                    // Быстрое пролистывание не должно слать запрос на каждый трек.
                    delay(SIMILAR_DEBOUNCE_MS)
                    loadSimilar(song, manual = false)
                }
        }
    }

    private fun loadSimilar(song: SongItem, manual: Boolean) {
        similarJob?.cancel()
        similarJob =
            viewModelScope.launch {
                _similarLoading.value = true
                runCatching { recs.similar(song, manual) }
                    .onSuccess { _similar.value = it }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                _similarLoading.value = false
            }
    }

    /** Кнопка REFRESH: другой набор, а не тот же самый. */
    fun refreshSimilar() {
        val song = player.state.value.song ?: return
        loadSimilar(song, manual = true)
    }

    /** MORE LIKE THIS: радио от этого трека. */
    fun moreLikeThis(song: SongItem) = playback.radioFrom(song)

    /** LESS LIKE THIS: исполнитель уходит из вкуса и больше не предлагается. */
    fun lessLikeThis(song: SongItem) {
        viewModelScope.launch {
            val now = com.texfi.w0y.recs.TasteProfile.parseExcluded(settingsRepository.settings.first().tasteExcluded)
            settingsRepository.setTasteExcluded(com.texfi.w0y.recs.TasteProfile.formatExcluded(now + com.texfi.w0y.recs.TasteProfile.key(song.artist)))
            _similar.value = _similar.value?.let { set -> set.copy(items = set.items.filterNot { com.texfi.w0y.recs.TasteProfile.key(it.song.artist) == com.texfi.w0y.recs.TasteProfile.key(song.artist) }) }
        }
    }

    fun enqueue(song: SongItem) = player.enqueue(song)

    fun playNextSong(song: SongItem) = player.playNext(song)

    /** Обложка или визуализатор — выбор запоминается. */
    fun setPlayerArt(art: com.texfi.w0y.data.PlayerArt) = viewModelScope.launch { settingsRepository.setPlayerArt(art) }

    fun setPlayerLayout(layout: com.texfi.w0y.data.PlayerLayout) = viewModelScope.launch { settingsRepository.setPlayerLayout(layout) }

    fun setVisualizerStyle(style: com.texfi.w0y.data.VisualizerStyle) = viewModelScope.launch { settingsRepository.setVisualizerStyle(style) }

    fun setLyricsSize(size: com.texfi.w0y.data.LyricsSize) = viewModelScope.launch { settingsRepository.setLyricsSize(size) }

    /** Яркость, выставленная прямо в подставке, запоминается. */
    fun setStandBrightness(value: Float) = viewModelScope.launch { settingsRepository.setStandBrightness(value) }

    fun setStandView(view: com.texfi.w0y.data.StandView) = viewModelScope.launch { settingsRepository.setStandView(view) }

    fun setStandVisualizer(value: Boolean) = viewModelScope.launch { settingsRepository.setStandVisualizer(value) }

    fun setStandClock(value: Boolean) = viewModelScope.launch { settingsRepository.setStandClock(value) }

    fun setStandFlipped(value: Boolean) = viewModelScope.launch { settingsRepository.setStandFlipped(value) }

    /** Видео ли играющий трек; null, пока поток не получен. */
    /** Источник для видео, которое YouTube отдаёт по SABR. */
    fun videoFactory(): androidx.media3.datasource.DataSource.Factory =
        com.texfi.w0y.playback.SabrDataSource.Factory(httpClient, youtube::sabrBootstrap)

    fun isVideoTrack(videoId: String): Boolean? = youtube.isVideoTrack(videoId)

    suspend fun videoSource(videoId: String): com.texfi.w0y.data.VideoSource? = youtube.videoSource(videoId)

    private val _edit = MutableStateFlow<EditSearch>(EditSearch.Idle)

    /** Поиск готовой переделки: идёт, не нашлось или уже играет. */
    val edit: StateFlow<EditSearch> = _edit

    /**
     * Ищет готовую slowed / sped up переделку играющего трека и включает её
     * следующей же секундой. Оригинал остаётся в очереди позади — «назад»
     * возвращает к нему.
     */
    fun findEdit(kind: EditKind) {
        val song = player.state.value.song ?: return
        if (_edit.value is EditSearch.Searching) return
        _edit.value = EditSearch.Searching(song.id, kind)
        viewModelScope.launch {
            val found = runCatching { youtube.findEdit(song, kind) }.getOrNull()
            if (found == null) {
                _edit.value = EditSearch.NotFound(song.id, kind)
                return@launch
            }
            player.playNext(found)
            player.skipNext()
            _edit.value = EditSearch.Playing(found.id, found.title)
        }
    }

    /**
     * Через что идёт звук прямо сейчас.
     *
     * Плеер показывает это только когда выход не сам телефон: «играет
     * через динамик» — очевидность, а вот «играет через колонку» объясняет
     * тишину в наушниках.
     */
    val activeOutput: StateFlow<AudioOutput?> =
        devices.outputs
            .map { outputs -> outputs.firstOrNull { it.active } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /** Что играет после текущего трека и не подбираются ли сейчас похожие. */
    val queueMode: StateFlow<QueueMode> = playback.mode
    val loadingRadio: StateFlow<Boolean> = playback.loadingRadio
    val findingClean: StateFlow<Boolean> = playback.findingClean

    fun setQueueMode(mode: QueueMode) = playback.applyMode(mode)

    /**
     * «Твоя версия» этого трека: своё звучание, запомненное за ним.
     *
     * Пусто — трек играет как все. Раньше ручка была одна на приложение,
     * и замедлить одну песню означало замедлить всю очередь.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trackSound: StateFlow<SoundProfile?> =
        player.state
            .map { it.song?.id }
            .flatMapLatest { id -> if (id == null) MutableStateFlow(null) else library.sound(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Что реально звучит: версия трека, если есть, иначе общие настройки. */
    val effectiveSound: StateFlow<SoundProfile> =
        combine(trackSound, settingsRepository.settings) { own, global ->
            own ?: SoundProfile.of(global)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SoundProfile.Plain)

    /**
     * Запоминает пресет за текущим треком.
     *
     * Даже «обычное» пишется явно: иначе трек, которому вернули обычное
     * звучание, снова поехал бы за общей настройкой, если та не обычная.
     * Снять версию совсем — [clearTrackSound].
     */
    fun setSoundPreset(preset: SoundPreset) {
        val song = player.state.value.song ?: return
        viewModelScope.launch {
            library.setSound(song, SoundProfile(preset.speed, preset.pitch, preset.reverb))
        }
    }

    /** Возвращает трек к общим настройкам приложения. */
    fun clearTrackSound() {
        val song = player.state.value.song ?: return
        viewModelScope.launch { library.setSound(song, null) }
    }

    val settings: StateFlow<W0ySettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), W0ySettings())

    val playlists: StateFlow<List<PlaylistEntity>> =
        library.editablePlaylists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val isLiked: StateFlow<Boolean> =
        player.state
            .map { it.song?.id }
            .flatMapLatest { id -> if (id == null) MutableStateFlow(false) else library.isLiked(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Закреплён ли текущий трек в быстром наборе. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isPinned: StateFlow<Boolean> =
        player.state
            .map { it.song?.id }
            .flatMapLatest { id ->
                library.pins.map { pins -> id != null && pins.any { it.key == PinEntity.key(PinEntity.KIND_SONG, id) } }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun togglePin(song: SongItem) = viewModelScope.launch {
        library.togglePin(
            kind = PinEntity.KIND_SONG,
            targetId = song.id,
            title = song.title,
            subtitle = song.artist.takeIf { it.isNotBlank() },
            thumbnailUrl = song.thumbnailUrl,
        )
    }

    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()

    private var lyricsForId: String? = null

    private val _lyricsLoading = MutableStateFlow(false)

    /** Текст ещё ищется (в нескольких источниках это занимает секунды). */
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    private val _details = MutableStateFlow<SongItem?>(null)

    /** Исполнители и альбом текущего трека, догруженные, если YouTube не дал их сразу. */
    val details: StateFlow<SongItem?> = _details.asStateFlow()

    private var detailsForId: String? = null

    fun ensureDetails(song: SongItem?) {
        if (song == null || com.texfi.w0y.data.LocalMusic.isLocal(song.id)) return
        if (song.id != detailsForId) {
            detailsForId = song.id
            _details.value = null
        }
        if (song.artistLinks().isNotEmpty() && song.albumId != null) return
        if (_details.value?.id == song.id) return
        viewModelScope.launch {
            val found = runCatching { youtube.songDetails(song.id) }.getOrNull()
            if (found != null && detailsForId == song.id) _details.value = found
        }
    }

    /** Лирика тянется один раз на трек и только если она включена. */
    fun ensureLyrics(song: SongItem?) {
        if (song == null || song.id == lyricsForId) return
        lyricsForId = song.id
        _lyrics.value = null
        _lyricsLoading.value = true
        viewModelScope.launch {
            val prefs = settingsRepository.settings.first()
            if (!prefs.showLyrics) {
                _lyricsLoading.value = false
                return@launch
            }
            val found = lyricsRepository.lyrics(song, prefs.lyricsAllSources)
            if (lyricsForId == song.id) {
                _lyrics.value = found
                _lyricsLoading.value = false
            }
        }
    }

    val lyricsLang: StateFlow<String> =
        settingsRepository.settings
            .map { it.lyricsLang }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setLyricsLang(lang: String) {
        viewModelScope.launch { settingsRepository.setLyricsLang(lang) }
    }

    private val _translating = MutableStateFlow(false)
    val translating: StateFlow<Boolean> = _translating.asStateFlow()

    /** Перевод строк лирики на выбранный язык; null — выключен, ещё грузится или не вышло. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val translation: StateFlow<List<String>?> =
        combine(_lyrics, lyricsLang) { text, lang -> text to lang }
            .flatMapLatest { (text, lang) ->
                kotlinx.coroutines.flow.flow {
                    _translating.value = text != null && lang.isNotBlank()
                    emit(null)
                    val id = player.state.value.song?.id
                    if (text != null && lang.isNotBlank() && id != null) {
                        _translating.value = true
                        try {
                            emit(lyricsRepository.translate(id, text, lang))
                        } finally {
                            _translating.value = false
                        }
                    } else {
                        _translating.value = false
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Своя скорость трека с ползунка: тон и эхо остаются как звучат сейчас. */
    fun setTrackSpeed(speed: Float) {
        val song = player.state.value.song ?: return
        val now = effectiveSound.value
        viewModelScope.launch { library.setSound(song, SoundProfile(speed, now.pitch, now.reverb)) }
    }

    /** Свой тон трека: скорость и эхо остаются как звучат сейчас. */
    fun setTrackPitch(pitch: Float) {
        val song = player.state.value.song ?: return
        val now = effectiveSound.value
        viewModelScope.launch { library.setSound(song, SoundProfile(now.speed, pitch, now.reverb)) }
    }

    /** Своё эхо трека: скорость и тон остаются как звучат сейчас. */
    fun setTrackReverb(reverb: Reverb) {
        val song = player.state.value.song ?: return
        val now = effectiveSound.value
        viewModelScope.launch { library.setSound(song, SoundProfile(now.speed, now.pitch, reverb)) }
    }

    /** Подсказка жестов показана — больше не повторяем. */
    fun markGestureHintSeen() = viewModelScope.launch { settingsRepository.setGestureHintSeen(true) }

    fun toggleLike(song: SongItem) {
        viewModelScope.launch {
            val liked = library.toggleLike(song)
            // Авто-скачивание лайкнутого — отдельная настройка, поэтому
            // проверяем её здесь, а не в репозитории лайков.
            if (liked && settingsRepository.settings.first().autoDownloadLiked) {
                downloads.download(song)
            }
        }
    }

    fun download(song: SongItem) = downloads.download(song)

    fun addToPlaylist(playlistId: Long, song: SongItem) =
        viewModelScope.launch { library.addToPlaylist(playlistId, song) }

    fun createPlaylistWith(name: String, song: SongItem) =
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            library.addToPlaylist(id, song)
        }

    /** Быстрый таймер из шапки — на столько минут, сколько выбрано в настройках. */
    fun startSleepTimer() =
        viewModelScope.launch {
            player.startSleepTimer(settingsRepository.settings.first().sleepTimerDefaultMin)
        }

    fun startSleepTimer(minutes: Int) = player.startSleepTimer(minutes)

    fun cancelSleepTimer() = player.cancelSleepTimer()

    /** Перемотка на фиксированный шаг — и назад, и вперёд одной функцией. */
    fun seekBy(deltaMs: Long) = player.seekBy(deltaMs)

    fun sleepAfterTrack() = player.sleepAfterCurrentTrack()

    fun removeFromQueue(index: Int) = player.removeFromQueue(index)

    fun insertInQueue(index: Int, song: SongItem) = player.insertInQueue(index, song)

    fun moveUpInQueue(index: Int) = player.moveUp(index)

    fun moveInQueue(from: Int, to: Int) = player.moveInQueue(from, to)

    private val _shareFile = MutableStateFlow<File?>(null)

    /** Готовая карточка трека: экран забирает её и отдаёт системе. */
    val shareFile: StateFlow<File?> = _shareFile.asStateFlow()

    private val _sharing = MutableStateFlow(false)
    val sharing: StateFlow<Boolean> = _sharing.asStateFlow()

    /**
     * Рисует карточку текущего трека.
     *
     * Сама она никуда не уходит: файл возвращается экрану, а выбор, куда
     * его отправить, делает человек в системном окне. Приложение ничего не
     * публикует за него.
     */
    fun shareCard() {
        val song = player.state.value.song ?: return
        if (_sharing.value) return
        viewModelScope.launch {
            _sharing.value = true
            _shareFile.value =
                runCatching { shareCards.render(song, trackSound.value ?: effectiveSound.value) }
                    .onFailure { timber.log.Timber.w(it, "Карточка трека не нарисовалась") }
                    .getOrNull()
            _sharing.value = false
        }
    }

    fun consumeShare() {
        _shareFile.value = null
    }
}

/** Состояние поиска переделки; [songId] — к какому треку относится ответ. */
sealed interface EditSearch {
    data object Idle : EditSearch

    data class Searching(val songId: String, val kind: EditKind) : EditSearch

    data class NotFound(val songId: String, val kind: EditKind) : EditSearch

    data class Playing(val songId: String, val title: String) : EditSearch
}


private const val SIMILAR_DEBOUNCE_MS = 1_200L
