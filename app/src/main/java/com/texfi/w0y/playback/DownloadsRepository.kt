package com.texfi.w0y.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.data.db.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Состояние одной загрузки для интерфейса. */
data class DownloadProgress(
    val songId: String,
    val percent: Float,
    val state: Int,
    /** Байт в секунду по последним замерам; 0 — ещё не знаем. */
    val bytesPerSecond: Long = 0,
    /** Сколько осталось, мс; null — не знаем размера. */
    val remainingMs: Long? = null,
)

/**
 * Состояния media3 — нестабильное API, поэтому интерфейсу отдаются свои
 * смыслы, а не семь чужих констант.
 */
object DownloadState {
    const val WAITING = 0
    const val PAUSED = 1
    const val RUNNING = 2
    const val DONE = 3
}

/** Что сказать человеку после нажатия «скачать» и по итогам. */
sealed interface DownloadNotice {
    /** [waitingForWifi] — очередь стоит, потому что в настройках «только Wi-Fi». */
    data class Queued(val title: String, val count: Int, val waitingForWifi: Boolean) : DownloadNotice

    data class Finished(val title: String?, val count: Int) : DownloadNotice

    data class Failed(val title: String, val reason: DownloadFailure) : DownloadNotice

    /** Новые загрузки не начаты: упёрлись в предел места из настроек. */
    data class StorageFull(val limitMb: Int) : DownloadNotice

    /** Очередь встала из-за низкого заряда. */
    data object PausedForBattery : DownloadNotice
}

@Singleton
@OptIn(UnstableApi::class)
class DownloadsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val manager: DownloadManager,
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    @param:Named("download") private val downloadCache: SimpleCache,
    private val exporter: MusicExporter,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val _notices = MutableSharedFlow<DownloadNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<DownloadNotice> = _notices.asSharedFlow()

    /** Причины сбоев, лежащие в базе: строка загрузки показывает их сразу. */
    val failures: kotlinx.coroutines.flow.Flow<Map<String, String>> = library.downloadErrors

    private var settings = W0ySettings()
    private var finishedInBatch = 0
    private var lastFinishedTitle: String? = null
    private var ticker: Job? = null
    private var pausedForBattery = false

    /** Последние замеры байтов для скорости: id → (время, байты). */
    private val samples = HashMap<String, ArrayDeque<Pair<Long, Long>>>()

    init {
        W0yDownloadService.ensureChannel(context)
        settingsRepository.settings
            .onEach { current ->
                val previous = settings
                settings = current
                // Менеджер загрузок живёт на главном потоке — трогать его из
                // фонового значит ловить гонку на пустом месте.
                withContext(Dispatchers.Main) {
                    if (previous.downloadOnWifiOnly != current.downloadOnWifiOnly || manager.requirements.requirements == 0) {
                        manager.requirements =
                            Requirements(
                                if (current.downloadOnWifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK,
                            )
                    }
                    manager.maxParallelDownloads = current.downloadParallel.coerceIn(1, 4)
                    manager.minRetryCount = current.downloadRetries.coerceIn(0, 10)
                }
            }.launchIn(scope)
        watchAutoPlaylists()
        settingsRepository.settings
            .map { it.downloadPauseOnLowBattery }
            .distinctUntilChanged()
            .onEach { watchBattery(it) }
            .launchIn(scope)
        manager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    publish(downloadManager)
                    announce(downloadManager, download, finalException)
                    val id = download.request.id
                    scope.launch {
                        when (download.state) {
                            Download.STATE_COMPLETED -> onCompleted(download)
                            Download.STATE_FAILED -> {
                                val reason = DownloadFailure.of(finalException)
                                Timber.w(finalException, "Загрузка %s не удалась: %s", id, reason)
                                library.markDownload(id, SongEntity.DOWNLOAD_NONE)
                                library.markDownloadError(id, reason.name)
                            }
                            Download.STATE_REMOVING -> library.markDownload(id, SongEntity.DOWNLOAD_NONE)
                            else -> {
                                library.markDownload(id, SongEntity.DOWNLOAD_QUEUED)
                                library.markDownloadError(id, null)
                            }
                        }
                    }
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    publish(downloadManager)
                    scope.launch { library.markDownload(download.request.id, SongEntity.DOWNLOAD_NONE) }
                }
            },
        )
        publish(manager)
    }

    /**
     * Готово — но сначала проверка: если в хранилище лежит меньше байтов,
     * чем обещал сервер, файл неполный, и он перекачивается, а не
     * объявляется скачанным.
     */
    private suspend fun onCompleted(download: Download) {
        val id = download.request.id
        if (!isComplete(id)) {
            Timber.w("Загрузка %s неполная, перекачиваю", id)
            library.markDownloadError(id, DownloadFailure.INCOMPLETE.name)
            redownload(listOf(id))
            return
        }
        library.markDownload(id, SongEntity.DOWNLOAD_DONE)
        library.markDownloadError(id, null)
        if (settings.autoExport) {
            library.songOnce(id)?.let { song -> runCatching { exporter.export(listOf(song)) } }
        }
    }

    /** Полностью ли трек лежит в хранилище загрузок. */
    fun isComplete(songId: String): Boolean {
        val length = ContentMetadata.getContentLength(downloadCache.getContentMetadata(songId))
        // Размер неизвестен (старая загрузка) — проверить нечем, считаем целым.
        if (length <= 0) return downloadCache.getCachedSpans(songId).isNotEmpty()
        return downloadCache.isCached(songId, 0, length)
    }

    fun download(song: SongItem) {
        if (overLimit()) return
        enqueue(song)
        _notices.tryEmit(DownloadNotice.Queued(song.title, 1, waitingForWifi()))
    }

    fun downloadAll(songs: List<SongItem>) {
        if (songs.isEmpty() || overLimit()) return
        songs.forEach(::enqueue)
        _notices.tryEmit(DownloadNotice.Queued(songs.first().title, songs.size, waitingForWifi()))
    }

    /** Предел места из настроек: при нём новые загрузки не начинаются. */
    private fun overLimit(): Boolean {
        val limit = settings.downloadStorageLimitMb
        if (limit <= 0) return false
        val full = usedBytes() >= limit * MB
        if (full) _notices.tryEmit(DownloadNotice.StorageFull(limit))
        return full
    }

    private fun enqueue(song: SongItem) {
        if (com.texfi.w0y.data.LocalMusic.isLocal(song.id)) return
        scope.launch {
            library.saveSong(song)
            library.markDownloadError(song.id, null)
        }
        val request =
            DownloadRequest
                .Builder(song.id, w0yUri(song))
                .setCustomCacheKey(song.id)
                // Название едет вместе с запросом: по нему потом говорится,
                // что именно скачалось, без похода в базу.
                .setData(song.title.toByteArray())
                .build()
        DownloadService.sendAddDownload(
            context,
            W0yDownloadService::class.java,
            request,
            /* foreground = */ false,
        )
    }

    /**
     * Перекачать заново: убрать то, что лежит, и поставить в очередь. Для
     * неполных и повреждённых файлов и для массового «перекачать».
     */
    fun redownload(songIds: List<String>) {
        scope.launch {
            songIds.forEach { id ->
                val song = library.songOnce(id) ?: return@forEach
                withContext(Dispatchers.Main) { cancel(id) }
                // Удаление идёт через сервис; ждём, пока запись уйдёт.
                delay(REMOVE_SETTLE_MS)
                withContext(Dispatchers.Main) { enqueue(song) }
            }
        }
    }

    /**
     * Проверка хранилища: всё, что числится скачанным, но лежит не целиком,
     * перекачивается. Возвращает, сколько нашлось битых.
     */
    suspend fun verifyAll(): Int = withContext(Dispatchers.IO) {
        val ids = library.downloadedIds()
        val broken = ids.filterNot(::isComplete)
        broken.forEach { library.markDownloadError(it, DownloadFailure.INCOMPLETE.name) }
        if (broken.isNotEmpty()) redownload(broken)
        broken.size
    }

    /**
     * Автозагрузка выбранных плейлистов: всё, что в них есть и ещё не
     * скачано, ставится в очередь — и при выборе, и когда в плейлист
     * добавили трек.
     */
    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun watchAutoPlaylists() {
        settingsRepository.settings
            .map { it.autoDownloadPlaylists }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    kotlinx.coroutines.flow.combine(ids.map { library.playlistSongs(it) }) { lists -> lists.flatMap { it.toList() } }
                }
            }.debounce(AUTO_SETTLE_MS)
            .onEach { songs ->
                if (songs.isEmpty()) return@onEach
                val have = library.downloadedIds().toSet() + _progress.value.keys
                val missing = songs.distinctBy { it.id }.filterNot { it.id in have }
                if (missing.isEmpty()) return@onEach
                withContext(Dispatchers.Main) {
                    if (!overLimit()) missing.forEach(::enqueue)
                }
            }.launchIn(scope)
    }

    /** Удалить все загрузки. */
    fun clearAll() {
        DownloadService.sendRemoveAllDownloads(context, W0yDownloadService::class.java, false)
    }

    /** Стоит ли очередь прямо сейчас из-за «только Wi-Fi». */
    private fun waitingForWifi(): Boolean =
        settings.downloadOnWifiOnly && Requirements(Requirements.NETWORK_UNMETERED).getNotMetRequirements(context) != 0

    /**
     * Итог говорится один раз на пачку: альбом из двенадцати треков — это
     * одно «скачано: 12», а не двенадцать всплывающих строк подряд.
     */
    private fun announce(manager: DownloadManager, download: Download, error: Exception?) {
        // У загрузок из прошлой сборки названия в запросе нет — тогда id.
        val title = download.request.data.decodeToString().ifBlank { download.request.id }
        when (download.state) {
            Download.STATE_COMPLETED -> {
                finishedInBatch++
                lastFinishedTitle = title
            }
            Download.STATE_FAILED -> _notices.tryEmit(DownloadNotice.Failed(title, DownloadFailure.of(error)))
            Download.STATE_DOWNLOADING -> startTicker(manager)
        }
        val busy = manager.currentDownloads.any { it.state != Download.STATE_COMPLETED && it.state != Download.STATE_FAILED }
        if (!busy && finishedInBatch > 0) {
            val single = lastFinishedTitle.takeIf { finishedInBatch == 1 }
            _notices.tryEmit(DownloadNotice.Finished(single, finishedInBatch))
            if (settings.downloadNotifications) W0yDownloadService.notifyFinished(context, single, finishedInBatch)
            finishedInBatch = 0
            lastFinishedTitle = null
        }
    }

    /**
     * Менеджер сообщает о смене состояния, но не о байтах: проценты он
     * считает молча. Пока что-то качается, раз в полсекунды забираем их
     * из памяти менеджера — без чтения базы на главном потоке — и по ним же
     * считаем скорость и оставшееся время.
     */
    private fun startTicker(manager: DownloadManager) {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch(Dispatchers.Main) {
                while (true) {
                    val running = manager.currentDownloads.filter { it.state == Download.STATE_DOWNLOADING }
                    if (running.isEmpty()) break
                    val now = System.currentTimeMillis()
                    _progress.value =
                        _progress.value + running.associate { d ->
                            val id = d.request.id
                            val bytes = d.bytesDownloaded
                            val window = samples.getOrPut(id) { ArrayDeque() }
                            window.addLast(now to bytes)
                            while (window.size > SPEED_WINDOW) window.removeFirst()
                            val speed = speedOf(window)
                            val total = d.contentLength
                            val remaining =
                                if (speed > 0 && total > 0) ((total - bytes).coerceAtLeast(0) * 1000 / speed) else null
                            id to
                                DownloadProgress(
                                    songId = id,
                                    percent = d.percentDownloaded.coerceAtLeast(0f),
                                    state = DownloadState.RUNNING,
                                    bytesPerSecond = speed,
                                    remainingMs = remaining,
                                )
                        }
                    delay(TICK_MS)
                }
            }
    }

    fun cancel(songId: String) {
        DownloadService.sendRemoveDownload(
            context,
            W0yDownloadService::class.java,
            songId,
            /* foreground = */ false,
        )
        scope.launch { library.markDownloadError(songId, null) }
    }

    fun pauseAll() =
        DownloadService.sendPauseDownloads(context, W0yDownloadService::class.java, false)

    fun resumeAll() =
        DownloadService.sendResumeDownloads(context, W0yDownloadService::class.java, false)

    /** Сколько места занято скачанным — показывается на экране загрузок. */
    fun usedBytes(): Long = downloadCache.cacheSpace

    private var batteryReceiver: BroadcastReceiver? = null

    /**
     * Пауза при низком заряде: система шлёт «заряд низкий» и «снова в
     * норме», по ним очередь встаёт и продолжается. Только если сами её
     * ставили на паузу — ручную паузу не отменяем.
     */
    private suspend fun watchBattery(enabled: Boolean) = withContext(Dispatchers.Main) {
        batteryReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        batteryReceiver = null
        if (!enabled) {
            if (pausedForBattery) {
                pausedForBattery = false
                resumeAll()
            }
            return@withContext
        }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_BATTERY_LOW -> {
                            if (manager.currentDownloads.isNotEmpty() && !manager.downloadsPaused) {
                                pausedForBattery = true
                                pauseAll()
                                _notices.tryEmit(DownloadNotice.PausedForBattery)
                            }
                        }
                        Intent.ACTION_BATTERY_OKAY ->
                            if (pausedForBattery) {
                                pausedForBattery = false
                                resumeAll()
                            }
                    }
                }
            }
        batteryReceiver = receiver
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
            },
        )
        // Если заряд уже низкий, широковещания не будет — проверим сами.
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        if (level in 1..LOW_BATTERY && battery?.isCharging != true && manager.currentDownloads.isNotEmpty()) {
            pausedForBattery = true
            pauseAll()
        }
    }

    private fun publish(manager: DownloadManager) {
        val cursor = manager.downloadIndex.getDownloads()
        val map = mutableMapOf<String, DownloadProgress>()
        cursor.use {
            while (it.moveToNext()) {
                val download = it.download
                map[download.request.id] =
                    DownloadProgress(
                        songId = download.request.id,
                        percent = download.percentDownloaded.coerceAtLeast(0f),
                        state =
                            when (download.state) {
                                Download.STATE_COMPLETED -> DownloadState.DONE
                                Download.STATE_DOWNLOADING -> DownloadState.RUNNING
                                Download.STATE_FAILED, Download.STATE_REMOVING -> continue
                                Download.STATE_STOPPED -> DownloadState.PAUSED
                                else -> if (manager.downloadsPaused) DownloadState.PAUSED else DownloadState.WAITING
                            },
                    )
            }
        }
        _progress.value = map
    }

    companion object {
        private const val TICK_MS = 500L
        private const val SPEED_WINDOW = 8
        private const val MB = 1024L * 1024
        private const val LOW_BATTERY = 15
        private const val REMOVE_SETTLE_MS = 600L
        private const val AUTO_SETTLE_MS = 2_000L

        /** Скорость по окну замеров: байты между крайними точками на время. */
        fun speedOf(window: Collection<Pair<Long, Long>>): Long {
            if (window.size < 2) return 0
            val (t0, b0) = window.first()
            val (t1, b1) = window.last()
            val dt = t1 - t0
            return if (dt <= 0) 0 else ((b1 - b0).coerceAtLeast(0) * 1000 / dt)
        }
    }
}
