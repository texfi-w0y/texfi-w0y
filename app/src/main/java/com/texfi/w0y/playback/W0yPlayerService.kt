package com.texfi.w0y.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.PresetReverb
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.texfi.w0y.data.ListenSession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.MainActivity
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.LyricsRepository
import com.texfi.w0y.data.Profanity
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.SoundProfile
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Фоновое воспроизведение с медиа-сессией: уведомление, экран блокировки
 * и наушники работают через неё же.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class W0yPlayerService : MediaSessionService() {
    @Inject lateinit var repository: YouTubeRepository

    @Inject lateinit var library: LibraryRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject
    @Named("player")
    lateinit var dataSourceFactory: DataSource.Factory

    @Inject lateinit var audioSession: AudioSessionHolder

    @Inject lateinit var startupMetrics: StartupMetrics

    @Inject lateinit var lyricsRepository: LyricsRepository

    @Inject lateinit var widgetUpdater: WidgetUpdater

    @Inject lateinit var spectrum: SpectrumBus

    @Inject lateinit var readAhead: ReadAhead

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Короткие записи истории, которые должны дойти до базы даже при остановке сервиса. */
    private val writeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings = MutableStateFlow(W0ySettings())
    private var session: MediaSession? = null
    private var playRequestedAt: Long = 0L
    private var headsetReceiver: BroadcastReceiver? = null
    private var reverbEffect: PresetReverb? = null
    private var reverbSession: Int? = null

    /** Что уже стоит на плеере — чтобы не пересоздавать эффект впустую. */
    private var appliedSound: SoundProfile? = null

    /** Трек на плеере — от него зависит, чью версию звучания слушать. */
    private val currentId = MutableStateFlow<String?>(null)

    /** Громкость трека без учёта заглушения — к ней возвращаемся после строки с матом. */
    private var baseVolume: Float = 1f
    private var swearWindows: List<LongRange> = emptyList()
    private var swearJob: Job? = null
    private var lyricsForId: String? = null

    /** Текущее включение для правила прослушивания и его строка в истории. */
    private var listen: ListenSession? = null
    private var listenRowId: Long? = null
    private var listenSavedMs = 0L
    private var listenJob: Job? = null

    /** Части запуска текущего трека — для разбора «нажал → звук». */
    @Volatile private var firstByteAt: Long = 0L

    @Volatile private var firstByteFromDisk = false
    private var readyAt: Long = 0L
    private var startingId: String? = null

    override fun onCreate() {
        super.onCreate()
        // Значок в шторке — монохромная «W» с кольцом, а не стандартный кружок с play.
        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(com.texfi.w0y.R.drawable.ic_stat_w0y)
            },
        )

        val loadControl =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15_000,
                    /* maxBufferMs = */ 50_000,
                    // Старт после 250 мс буфера, а не после 2.5 с по умолчанию:
                    // это самая дешёвая доля секунды между нажатием и звуком.
                    /* bufferForPlaybackMs = */ 250,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_000,
                ).build()

        // Отвод звука для визуализатора: копия PCM, сам звук идёт дальше без
        // изменений. Остальная цепочка (пропуск тишины, скорость) — как была.
        val renderers =
            object : DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink =
                    DefaultAudioSink
                        .Builder(context)
                        .setAudioProcessors(arrayOf(TeeAudioProcessor(spectrum)))
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .build()
            }
        // Первый байт звука после команды — граница между «ждали адрес и
        // сеть» и «ждали буфер». Слушатель только ставит отметку времени.
        val timing =
            object : TransferListener {
                override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

                override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

                override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                    if (playRequestedAt != 0L && firstByteAt == 0L) {
                        firstByteAt = System.nanoTime()
                        firstByteFromDisk = !isNetwork
                    }
                }

                override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
            }
        val timedFactory =
            DataSource.Factory { dataSourceFactory.createDataSource().also { it.addTransferListener(timing) } }

        val player =
            ExoPlayer
                .Builder(this, renderers)
                .setMediaSourceFactory(DefaultMediaSourceFactory(timedFactory))
                .setLoadControl(loadControl)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true,
                ).setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

        settingsRepository.settings
            .onEach { current ->
                settings.value = current
                player.skipSilenceEnabled = current.skipSilence
                player.setHandleAudioBecomingNoisy(current.pauseOnHeadphonesOut)
                applyLoudness(player)
                if (!current.muteSwearLines) {
                    swearWindows = emptyList()
                    lyricsForId = null
                    updateVolume(player)
                } else {
                    loadSwearWindows(player.currentMediaItem)
                }
            }.launchIn(scope)
        watchSound(player)

        audioSession.update(player.audioSessionId)
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onAudioSessionIdChanged(
                    eventTime: AnalyticsListener.EventTime,
                    audioSessionId: Int,
                ) {
                    audioSession.update(audioSessionId)
                    // Эхо висит на сессии: переехала она — переезжает и эффект.
                    appliedSound?.let { commitSound(player, it) }
                }
            },
        )
        player.addListener(TrackListener(player))
        player.addListener(WidgetListener(player))
        startListenWatch(player)
        startSwearWatch(player)
        attachFirstAudioTrace(player)

        val sessionActivity =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        session =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build()

        registerHeadsetReceiver(player)
    }

    /**
     * «Продолжать при подключении наушников». Первое событие после
     * регистрации приходит липким — текущее состояние гнезда, а не
     * подключение, — поэтому его пропускаем, иначе музыка включалась бы
     * сама при каждом старте сервиса.
     */
    private fun registerHeadsetReceiver(player: Player) {
        var skippedSticky = false
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != AudioManager.ACTION_HEADSET_PLUG) return
                    if (!skippedSticky) {
                        skippedSticky = true
                        return
                    }
                    val plugged = intent.getIntExtra("state", 0) == 1
                    if (plugged &&
                        settings.value.resumeOnHeadphonesIn &&
                        player.mediaItemCount > 0 &&
                        !player.isPlaying
                    ) {
                        player.play()
                    }
                }
            }
        headsetReceiver = receiver
        registerReceiver(receiver, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Виджет на домашнем экране узнаёт об этом первым: строка с треком
        // и кнопки после смерти сервиса адресованы уже никому.
        widgetUpdater.clear()
        swearJob?.cancel()
        finishListen()
        readAhead.cancel()
        releaseReverb()
        headsetReceiver?.let { runCatching { unregisterReceiver(it) } }
        headsetReceiver = null
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Ставит звучание: своё у трека, если оно задано, иначе общее.
     *
     * Скорость и тон — отдельные ручки: замедление без сдвига тона звучит
     * иначе, чем настоящий slowed-эдит, где падает и то и другое. Пусть
     * выбирает слушатель.
     *
     * Версия трека читается подпиской на базу, а не разовым запросом при
     * переключении: иначе пресет, выбранный в плеере для играющего трека,
     * ложился в базу и не звучал до следующего перехода. Именно так он и
     * «не работал» в одной из ранних сборок.
     *
     * Пока база не ответила про новый трек, он играет на общих настройках:
     * onStart(null) не даёт ему унаследовать замедление предыдущего.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchSound(player: Player) {
        val own =
            currentId.flatMapLatest { id ->
                if (id.isNullOrEmpty()) {
                    flowOf(null)
                } else {
                    library.sound(id).onStart { emit(null) }.catch { emit(null) }
                }
            }
        combine(own, settings) { mine, global -> mine ?: SoundProfile.of(global) }
            .distinctUntilChanged()
            .onEach { commitSound(player, it) }
            .launchIn(scope)
    }

    /**
     * Реверб пересоздавать на каждом треке нельзя: это освобождение и
     * создание системного эффекта, то есть щелчок в звуке на ровном месте.
     * Поэтому применяем только изменившееся.
     */
    private fun commitSound(player: Player, profile: SoundProfile) {
        val session = player.audioSessionId()
        if (profile != appliedSound) {
            player.playbackParameters = PlaybackParameters(profile.speed, profile.pitch)
        }
        // Эффект переставляем при смене эха и при смене самой аудиосессии:
        // на части устройств она меняется при переключении вывода, и старый
        // эффект остаётся висеть на мёртвой — звук тогда просто сухой.
        val movedSession = profile.reverb != Reverb.OFF && session != reverbSession
        if (profile.reverb != appliedSound?.reverb || movedSession) {
            applyReverb(session, profile.reverb)
        }
        appliedSound = profile
    }

    /** Сессия нужна для реверба, а у интерфейса Player её нет. */
    private fun Player.audioSessionId(): Int =
        (this as? ExoPlayer)?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

    /**
     * Реверб вешается на аудиосессию плеера как вставка.
     *
     * Эффект пересоздаётся при смене сессии: сессия меняется на некоторых
     * устройствах при переключении вывода, и старый эффект остаётся висеть
     * на мёртвой сессии — звук тогда просто сухой, без всякой ошибки.
     */
    private fun applyReverb(sessionId: Int, reverb: Reverb) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (reverb == Reverb.OFF) {
            releaseReverb()
            return
        }
        val effect =
            reverbEffect?.takeIf { reverbSession == sessionId } ?: runCatching {
                releaseReverb()
                PresetReverb(REVERB_PRIORITY, sessionId).also {
                    reverbEffect = it
                    reverbSession = sessionId
                }
            }.onFailure { Timber.w(it, "Реверб недоступен на этом устройстве") }.getOrNull()
                ?: return
        runCatching {
            effect.preset =
                when (reverb) {
                    Reverb.ROOM -> PresetReverb.PRESET_MEDIUMROOM
                    Reverb.HALL -> PresetReverb.PRESET_LARGEHALL
                    Reverb.CAVE -> PresetReverb.PRESET_PLATE
                    Reverb.OFF -> PresetReverb.PRESET_NONE
                }
            effect.enabled = true
        }.onFailure { Timber.w(it, "Реверб не применился") }
    }

    private fun releaseReverb() {
        runCatching { reverbEffect?.release() }
        reverbEffect = null
        reverbSession = null
    }

    /**
     * Выравнивание громкости по данным самого YouTube: в потоке приходит
     * измеренная громкость трека, и тихие записи перестают теряться после
     * громких. Выключено — играем как есть.
     */
    private fun applyLoudness(player: Player) {
        val id = player.currentMediaItem?.mediaId
        val loudness = id?.let { repository.cachedLoudnessDb(it) }
        baseVolume =
            if (!settings.value.normalizeVolume || loudness == null) {
                1f
            } else {
                // Приводим к -14 дБ: типовой ориентир стриминговых сервисов.
                val gainDb = (-14.0 - loudness).coerceIn(-6.0, 6.0)
                10.0.pow(gainDb / 20.0).toFloat().coerceIn(0.2f, 1f)
            }
        updateVolume(player)
    }

    /**
     * Громкость с учётом заглушения строк.
     *
     * Выравнивание громкости и режим «без мата» пишут в одно и то же поле
     * плеера, поэтому базовый уровень хранится отдельно — иначе первое же
     * выравнивание снимало бы заглушение посреди строки.
     */
    private fun updateVolume(player: Player) {
        val ducked =
            settings.value.muteSwearLines &&
                swearWindows.any { player.currentPosition in it }
        player.volume = if (ducked) 0f else baseVolume
    }

    /**
     * Строки с матом по синхронной лирике.
     *
     * Приглушается строка целиком: вырезать отдельное слово из готовой
     * записи нельзя — для этого нужна дорожка без вокала, которой нет ни у
     * нас, ни у YouTube. Поэтому режим помечен бетой, а основной путь
     * «без мата» — подмена на официальную чистую версию.
     */
    private fun loadSwearWindows(item: MediaItem?) {
        val id = item?.mediaId ?: return
        if (lyricsForId == id) return
        lyricsForId = id
        swearWindows = emptyList()
        scope.launch {
            val song =
                SongItem(
                    id = id,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                )
            val lines =
                runCatching { lyricsRepository.lyrics(song)?.synced.orEmpty() }
                    .onFailure { Timber.w(it, "Лирика для заглушения не пришла") }
                    .getOrDefault(emptyList())
            swearWindows =
                lines.mapIndexedNotNull { index, line ->
                    if (!Profanity.inText(line.text)) return@mapIndexedNotNull null
                    val end = lines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + LAST_LINE_MS)
                    line.timeMs until end
                }
        }
    }

    /** Следит за позицией: тишина включается ровно на время строки. */
    private fun startSwearWatch(player: Player) {
        swearJob?.cancel()
        swearJob =
            scope.launch {
                while (true) {
                    if (settings.value.muteSwearLines && swearWindows.isNotEmpty()) {
                        withContext(Dispatchers.Main) { updateVolume(player) }
                    }
                    delay(WATCH_INTERVAL_MS)
                }
            }
    }

    /**
     * Держит виджет в курсе: название, исполнитель, обложка и то, играет
     * ли сейчас звук.
     *
     * Отдельным слушателем, а не строкой в [TrackListener]: тот отвечает
     * за воспроизведение, и смешивать с ним обновление домашнего экрана
     * значило бы, что поломка одного тянет второе.
     */
    private inner class WidgetListener(
        private val player: Player,
    ) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = push(mediaItem)

        override fun onIsPlayingChanged(isPlaying: Boolean) = push(player.currentMediaItem)

        private fun push(mediaItem: MediaItem?) {
            val metadata = mediaItem?.mediaMetadata
            widgetUpdater.push(
                title = metadata?.title?.toString().orEmpty(),
                artist = metadata?.artist?.toString().orEmpty(),
                playing = player.isPlaying,
                coverUrl = metadata?.artworkUri?.toString(),
            )
        }
    }

    private inner class TrackListener(
        private val player: Player,
    ) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            armStuckWatch()
            prefetchNext()
            // Версия принадлежит треку, а не приложению: на каждом
            // переключении её надо перечитать, иначе следующий трек
            // унаследовал бы замедление предыдущего.
            currentId.value = mediaItem?.mediaId
            applyLoudness(player)
            if (settings.value.muteSwearLines) loadSwearWindows(mediaItem) else swearWindows = emptyList()
            // Каждый переход — новое включение, в том числе повтор того же
            // трека: он считается отдельно.
            finishListen()
            val item = mediaItem ?: return
            if (!settings.value.keepHistory) return
            startListen(item)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                listen?.seeked(newPosition.positionMs, System.currentTimeMillis())
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = prefetchNext()

        private var stuckJob: Job? = null

        /** Трек, который 10 секунд не стартует, пропускаем: тишина хуже следующей песни. */
        private fun armStuckWatch() {
            stuckJob?.cancel()
            if (!player.playWhenReady) return
            val state = player.playbackState
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) return
            if (player.mediaItemCount == 0) return
            stuckJob =
                scope.launch {
                    delay(STUCK_MS)
                    if (player.playWhenReady && player.playbackState != Player.STATE_READY) skipStuck()
                }
        }

        /**
         * Свой файл не открылся (удалён или повреждён): ищем тот же трек в каталоге
         * и подставляем его на то же место. Нет такого — идём дальше.
         */
        private fun replaceBrokenFile(item: MediaItem) {
            val index = player.currentMediaItemIndex
            val broken =
                SongItem(
                    id = item.mediaId,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                )
            scope.launch {
                val twin = runCatching { repository.findEquivalent(broken) }.getOrNull()
                if (twin == null || player.currentMediaItem?.mediaId != item.mediaId) {
                    skipStuck()
                    return@launch
                }
                val replacement =
                    MediaItem
                        .Builder()
                        .setMediaId(twin.id)
                        .setUri(w0yUri(twin))
                        .setCustomCacheKey(twin.id)
                        .setMediaMetadata(item.mediaMetadata.buildUpon().setTitle(twin.title).setArtist(twin.artist).build())
                        .build()
                player.replaceMediaItem(index, replacement)
                player.prepare()
                player.play()
            }
        }

        private fun skipStuck() {
            Timber.w("Трек не запустился за ${STUCK_MS / 1000} с, иду дальше")
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) = armStuckWatch()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = armStuckWatch()

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Timber.w(error, "Ошибка воспроизведения")
            val current = player.currentMediaItem
            if (current != null && com.texfi.w0y.data.LocalMusic.isLocal(current.mediaId)) {
                replaceBrokenFile(current)
            } else {
                skipStuck()
            }
        }

        /**
         * Греет следующие треки: адрес ближайшего — сразу, а начало его
         * файла и адреса ещё двух — через [ReadAhead]. Переход должен быть
         * без паузы, и пролистывание подряд — тоже.
         */
        private fun prefetchNext() {
            if (!settings.value.preloadNext) {
                readAhead.cancel()
                return
            }
            val next = player.nextMediaItemIndex
            if (next == C.INDEX_UNSET) {
                readAhead.cancel()
                return
            }
            val id = player.getMediaItemAt(next).mediaId
            if (id.isNotEmpty()) repository.prefetch(id)
            val upcoming =
                (next until minOf(player.mediaItemCount, next + 3)).map { index ->
                    val item = player.getMediaItemAt(index)
                    SongItem(
                        id = item.mediaId,
                        title = item.mediaMetadata.title?.toString().orEmpty(),
                        artist = item.mediaMetadata.artist?.toString().orEmpty(),
                    )
                }
            readAhead.prepare(upcoming, repository.onWifi())
        }
    }

    /** Главная метрика проекта: время от команды до первого звука. */
    private fun attachFirstAudioTrace(player: ExoPlayer) {
        player.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady) markRequest(player.currentMediaItem?.mediaId)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    markRequest(mediaItem?.mediaId)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && playRequestedAt != 0L && readyAt == 0L) {
                        readyAt = System.nanoTime()
                    }
                }
            },
        )
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onAudioPositionAdvancing(
                    eventTime: AnalyticsListener.EventTime,
                    playoutStartSystemTimeMs: Long,
                ) {
                    if (playRequestedAt == 0L) return
                    val now = System.nanoTime()
                    val start = playRequestedAt
                    fun part(at: Long) = if (at == 0L || at < start) null else (at - start) / 1_000_000
                    val sample =
                        StartupSample(
                            totalMs = (now - start) / 1_000_000,
                            resolveMs = startingId?.let(repository::takeResolveMs),
                            firstByteMs = part(firstByteAt),
                            readyMs = part(readyAt),
                            fromDisk = firstByteFromDisk,
                        )
                    playRequestedAt = 0L
                    startupMetrics.record(sample)
                    if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "bench") {
                        android.util.Log.i("w0y-start", sample.toString())
                    }
                }
            },
        )
    }

    /** Отметка «команда дана» для разбора старта по частям. */
    private fun markRequest(mediaId: String?) {
        playRequestedAt = System.nanoTime()
        firstByteAt = 0L
        firstByteFromDisk = false
        readyAt = 0L
        startingId = mediaId
        // Замер от предзагрузки не в счёт: адрес тогда уже в кэше, и
        // получение для этого запуска честно равно нулю.
        if (mediaId != null) repository.takeResolveMs(mediaId)
    }

    /** Новое включение: строка истории сразу, засчитывание — по правилу. */
    private fun startListen(item: MediaItem) {
        val session = ListenSession(item.mediaId, durationMs = null)
        listen = session
        listenRowId = null
        listenSavedMs = 0
        scope.launch {
            val row =
                runCatching {
                    library.remember(
                        SongItem(
                            id = item.mediaId,
                            title = item.mediaMetadata.title?.toString().orEmpty(),
                            artist = item.mediaMetadata.artist?.toString().orEmpty(),
                            album = item.mediaMetadata.albumTitle?.toString(),
                            thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
                        ),
                    )
                }.onFailure { Timber.w(it, "История: строка не записалась") }.getOrNull() ?: return@launch
            if (listen === session) {
                listenRowId = row
            } else if (session.listenedMs > 0) {
                // Трек успели переключить, пока строка создавалась: чужой
                // номер новому включению не отдаём, свою долю дописываем.
                runCatching { library.updateListen(row, session.listenedMs, session.counted) }
            }
        }
    }

    /** Закрыть включение: дописать проигранное время. */
    private fun finishListen() {
        val session = listen ?: return
        val row = listenRowId
        listen = null
        listenRowId = null
        if (row != null && session.listenedMs != listenSavedMs) {
            // Отдельная область: при остановке сервиса `scope` отменяется
            // раньше, чем запись доходит до базы, и последнее включение терялось.
            writeScope.launch { runCatching { library.updateListen(row, session.listenedMs, session.counted) } }
        }
    }

    /**
     * Раз в секунду: снимок позиции для правила прослушивания. Засчитанное
     * пишется сразу, проигранное время — не чаще раза в 15 секунд.
     */
    private fun startListenWatch(player: Player) {
        listenJob?.cancel()
        listenJob =
            scope.launch {
                while (true) {
                    delay(LISTEN_TICK_MS)
                    val session = listen ?: continue
                    if (player.currentMediaItem?.mediaId != session.songId) continue
                    val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                    if (duration != null && session.durationMs == null) {
                        session.durationMs = duration
                        launch { library.rememberDuration(session.songId, duration) }
                    }
                    val crossed =
                        session.sample(
                            positionMs = player.currentPosition,
                            wallMs = System.currentTimeMillis(),
                            playing = player.isPlaying,
                            speed = player.playbackParameters.speed,
                        )
                    val row = listenRowId ?: continue
                    if (crossed || session.listenedMs - listenSavedMs >= LISTEN_SAVE_MS) {
                        listenSavedMs = session.listenedMs
                        // Сбой записи не должен гасить счётчик до конца жизни сервиса.
                        runCatching { library.updateListen(row, session.listenedMs, session.counted) }
                            .onFailure { Timber.w(it, "История: прослушивание не записалось") }
                    }
                }
            }
    }

    private companion object {
        const val LISTEN_TICK_MS = 1_000L
        const val LISTEN_SAVE_MS = 15_000L

        /** Приоритет вставки эффекта: выше нуля, чтобы система не вытеснила его чужим. */
        const val REVERB_PRIORITY = 1

        /** Сколько держать заглушение на последней строке лирики. */
        const val STUCK_MS = 10_000L

        const val LAST_LINE_MS = 6_000L

        /** Шаг проверки позиции: чаще незачем, реже — слышно край слова. */
        const val WATCH_INTERVAL_MS = 120L
    }
}
