package com.texfi.w0y.ui.screens

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import com.texfi.w0y.R
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.AccountRepository
import com.texfi.w0y.data.Quality
import com.texfi.w0y.data.ExplicitFallback
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.StartTab
import com.texfi.w0y.data.Language
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.playback.AudioDevicesRepository
import com.texfi.w0y.playback.AudioOutput
import com.texfi.w0y.playback.AudioSessionHolder
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.StartupMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// SimpleCache помечен в media3 как нестабильное API: другого способа
// узнать и почистить объём кэша библиотека не даёт, а размер кэша —
// настройка, которую обещали пользователю.
@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val youtube: YouTubeRepository,
    @param:Named("stream") private val streamCache: SimpleCache,
    private val audioSession: AudioSessionHolder,
    private val playback: PlaybackStarter,
    private val account: AccountRepository,
    devices: AudioDevicesRepository,
    startupMetrics: StartupMetrics,
    private val downloads: com.texfi.w0y.playback.DownloadsRepository,
    library: com.texfi.w0y.data.LibraryRepository,
) : ViewModel() {
    /** Свои плейлисты — для выбора автозагрузки. */
    val playlists: StateFlow<List<com.texfi.w0y.data.db.PlaylistEntity>> =
        library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _downloadBytes = MutableStateFlow(0L)

    /** Сколько занимают загрузки. */
    val downloadBytes: StateFlow<Long> = _downloadBytes.asStateFlow()

    fun refreshDownloadSize() {
        viewModelScope.launch { _downloadBytes.value = withContext(Dispatchers.IO) { downloads.usedBytes() } }
    }

    /** Проверить хранилище: неполные файлы уходят на перекачку. */
    fun verifyDownloads() {
        viewModelScope.launch {
            val broken = downloads.verifyAll()
            _message.value =
                if (broken == 0) {
                    context.getString(R.string.dl_verify_ok)
                } else {
                    context.getString(R.string.dl_verify_fixing, broken)
                }
        }
    }

    fun clearDownloads() {
        downloads.clearAll()
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            refreshDownloadSize()
            _message.value = context.getString(R.string.dl_cleared)
        }
    }

    /** Выходы звука: то, что система видит подключённым прямо сейчас. */
    val outputs: StateFlow<List<AudioOutput>> =
        devices.outputs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val signedIn: StateFlow<Boolean> =
        account.isSignedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val accountName: StateFlow<String?> =
        account.accountName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun signOut() = update { account.signOut() }
    /** Замер «нажал → пошёл звук»: главное обещание приложения, измеренное. */
    val startupAverage: StateFlow<Long?> = startupMetrics.average
    val startupLast: StateFlow<Long?> = startupMetrics.last
    val startupCount: StateFlow<Int> = startupMetrics.count
    val startupBreakdown: StateFlow<com.texfi.w0y.playback.StartupBreakdown?> = startupMetrics.breakdown

    val audioSessionId: Int get() = audioSession.sessionId
    val settings: StateFlow<W0ySettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), W0ySettings())

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshCacheSize()
        refreshDownloadSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch { _cacheBytes.value = withContext(Dispatchers.IO) { streamCache.cacheSpace } }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                streamCache.keys.toList().forEach { key ->
                    streamCache.removeResource(key)
                }
            }
            refreshCacheSize()
            _message.value = context.getString(R.string.settings_cache_cleared)
        }
    }

    fun setQualityWifi(value: Quality) = update { repository.setQualityWifi(value) }

    fun setQualityMobile(value: Quality) = update { repository.setQualityMobile(value) }

    fun setPreload(value: Boolean) = update { repository.setPreload(value) }

    fun setCacheLimit(mb: Int) = update { repository.setCacheLimit(mb) }

    fun setAutoDownload(value: Boolean) = update { repository.setAutoDownload(value) }

    fun setNormalize(value: Boolean) = update { repository.setNormalize(value) }

    // Вид Hairline
    fun setLineWeight(value: com.texfi.w0y.data.LineWeight) = update { repository.setLineWeight(value) }

    fun setCorners(value: com.texfi.w0y.data.CornerStyle) = update { repository.setCorners(value) }

    fun setCoverShape(value: com.texfi.w0y.data.CoverShape) = update { repository.setCoverShape(value) }

    fun setPlayerLayout(value: com.texfi.w0y.data.PlayerLayout) = update { repository.setPlayerLayout(value) }

    fun setCaps(value: com.texfi.w0y.data.CapsMode) = update { repository.setCaps(value) }

    fun setDensity(value: com.texfi.w0y.data.Density) = update { repository.setDensity(value) }

    fun setMotion(value: com.texfi.w0y.data.MotionMode) = update { repository.setMotion(value) }

    fun setHapticLevel(value: com.texfi.w0y.data.HapticLevel) = update { repository.setHapticLevel(value) }

    fun setIconStyle(value: com.texfi.w0y.data.IconStyle) = update { repository.setIconStyle(value) }

    fun setSwipeDismiss(value: Boolean) = update { repository.setSwipeDismiss(value) }

    fun resetLook() = update { repository.resetLook() }

    /** Значок на рабочем столе: сначала переключаем алиас, потом запоминаем выбор. */
    fun setAppIcon(value: com.texfi.w0y.data.AppIcon) {
        com.texfi.w0y.data.AppIconSwitcher.apply(context, value)
        update { repository.setAppIcon(value) }
    }

    fun setSkipSilence(value: Boolean) = update { repository.setSkipSilence(value) }

    fun setPauseOnUnplug(value: Boolean) = update { repository.setPauseOnUnplug(value) }

    fun setResumeOnPlug(value: Boolean) = update { repository.setResumeOnPlug(value) }

    fun setSleepDefault(minutes: Int) = update { repository.setSleepDefault(minutes) }

    fun setTheme(value: ThemeMode) = update { repository.setTheme(value) }

    fun unlockExperiments() = update { repository.setExperiments(true) }

    fun setShowLyrics(value: Boolean) = update { repository.setShowLyrics(value) }

    fun setLyricsLang(value: String) = update { repository.setLyricsLang(value) }

    fun setKeepHistory(value: Boolean) = update { repository.setKeepHistory(value) }

    /**
     * Смена режима идёт через стартер, а не прямо в настройки: он ещё и
     * перестроит хвост уже играющей очереди, иначе выбор подействовал бы
     * только на следующий запуск.
     */
    fun setQueueMode(value: QueueMode) = playback.applyMode(value)

    fun setSpeed(value: Float) = update { repository.setSpeed(value) }

    fun setPitch(value: Float) = update { repository.setPitch(value) }

    fun setReverb(value: Reverb) = update { repository.setReverb(value) }

    fun setShowRecommendations(value: Boolean) = update { repository.setShowRecommendations(value) }

    fun setCompactRows(value: Boolean) = update { repository.setCompactRows(value) }

    fun setAnimatedBackground(value: Boolean) = update { repository.setAnimatedBackground(value) }

    fun setAccent(value: Accent) = update { repository.setAccent(value) }

    /**
     * Полный сброс: система стирает всё, что есть у приложения, — настройки,
     * библиотеку, историю, вход в аккаунт, загрузки и кэш — и закрывает его.
     * Следующий запуск — как первый. Файлы, сохранённые в папку на телефоне,
     * не трогаются: они уже не принадлежат приложению.
     */
    fun fullReset() {
        context.getSystemService(android.app.ActivityManager::class.java).clearApplicationUserData()
    }

    fun setCustomAccent(argb: Int) = update { repository.setCustomAccent(argb) }

    fun setSaveSearchHistory(value: Boolean) = update { repository.setSaveSearchHistory(value) }

    fun setDownloadOnWifiOnly(value: Boolean) = update { repository.setDownloadOnWifiOnly(value) }

    fun setStartTab(value: StartTab) = update { repository.setStartTab(value) }

    fun setCleanMode(value: Boolean) = update { repository.setCleanMode(value) }

    fun setExplicitFallback(value: ExplicitFallback) = update { repository.setExplicitFallback(value) }

    fun setHideExplicit(value: Boolean) = update { repository.setHideExplicit(value) }

    fun setMuteSwearLines(value: Boolean) = update { repository.setMuteSwearLines(value) }

    fun setSyncPlaylists(value: Boolean) = update { repository.setSyncPlaylists(value) }

    fun setSearchSuggestions(value: Boolean) = update { repository.setSearchSuggestions(value) }

    fun setHaptics(value: Boolean) = update { repository.setHaptics(value) }

    fun setSeekStep(seconds: Int) = update { repository.setSeekStep(seconds) }

    fun setFallbackAudio(value: Boolean) = update { repository.setFallbackAudio(value) }

    fun setFallbackSource(value: com.texfi.w0y.data.FallbackSource) = update { repository.setFallbackSource(value) }

    fun setPlayerCoverGlow(value: Boolean) = update { repository.setPlayerCoverGlow(value) }

    fun setDownloadQualityWifi(value: Quality) = update { repository.setDownloadQualityWifi(value) }

    fun setDownloadQualityMobile(value: Quality) = update { repository.setDownloadQualityMobile(value) }

    fun setDownloadParallel(value: Int) = update { repository.setDownloadParallel(value) }

    fun setDownloadSpeedLimit(kb: Int) = update { repository.setDownloadSpeedLimit(kb) }

    fun setDownloadPauseOnLowBattery(value: Boolean) = update { repository.setDownloadPauseOnLowBattery(value) }

    fun setDownloadAutoResume(value: Boolean) = update { repository.setDownloadAutoResume(value) }

    fun setDownloadRetries(value: Int) = update { repository.setDownloadRetries(value) }

    fun setDownloadStorageLimit(mb: Int) = update { repository.setDownloadStorageLimit(mb) }

    fun setDownloadNotifications(value: Boolean) = update { repository.setDownloadNotifications(value) }

    fun setAutoExport(value: Boolean) = update { repository.setAutoExport(value) }

    fun setExportWarningSeen(value: Boolean) = update { repository.setExportWarningSeen(value) }

    fun setAutoDownloadPlaylist(id: Long, enabled: Boolean) = update { repository.setAutoDownloadPlaylist(id, enabled) }

    fun setShowPlaylistRecommendations(value: Boolean) = update { repository.setShowPlaylistRecommendations(value) }

    fun setDialMinPlays(value: Int) = update { repository.setDialMinPlays(value) }

    fun setUiStyle(value: com.texfi.w0y.data.UiStyle) = update { repository.setUiStyle(value) }

    fun setSmoothGlass(value: Boolean) = update { repository.setSmoothGlass(value) }

    fun setStylePicked(value: Boolean) = update { repository.setStylePicked(value) }

    fun setLyricsSize(value: com.texfi.w0y.data.LyricsSize) = update { repository.setLyricsSize(value) }

    fun setLyricsDelayMs(value: Int) = update { repository.setLyricsDelayMs(value) }

    fun setLyricsAllSources(value: Boolean) = update { repository.setLyricsAllSources(value) }

    fun setVisualizerStyle(value: com.texfi.w0y.data.VisualizerStyle) = update { repository.setVisualizerStyle(value) }

    fun setVisualizerSensitivity(value: Float) = update { repository.setVisualizerSensitivity(value) }

    fun setVisualizerFps(value: Int) = update { repository.setVisualizerFps(value) }

    fun setVisualizerCoverBackdrop(value: Boolean) = update { repository.setVisualizerCoverBackdrop(value) }

    fun setStandLyrics(value: Boolean) = update { repository.setStandLyrics(value) }

    fun setStandVisualizer(value: Boolean) = update { repository.setStandVisualizer(value) }

    fun setStandBrightness(value: Float) = update { repository.setStandBrightness(value) }

    fun setStandMarquee(value: Boolean) = update { repository.setStandMarquee(value) }

    fun setStandFlipped(value: Boolean) = update { repository.setStandFlipped(value) }

    fun setStandClock(value: Boolean) = update { repository.setStandClock(value) }

    fun setBackdropMode(value: com.texfi.w0y.data.BackdropMode) = update { repository.setBackdropMode(value) }

    /** Своя картинка фона: копия в приложении, ужатая до экрана, — оригинал можно удалять. */
    fun setBackdropPhoto(uri: android.net.Uri) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(context.filesDir, "backdrop").apply { mkdirs() }
                val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    ?: return@runCatching null
                val side = 1600
                val k = side.toFloat() / maxOf(bitmap.width, bitmap.height)
                val scaled = if (k < 1f) android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt(), (bitmap.height * k).toInt(), true) else bitmap
                dir.listFiles()?.forEach { it.delete() }
                val file = java.io.File(dir, "bg-${System.currentTimeMillis()}.jpg")
                file.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                "file://" + file.absolutePath
            }.getOrNull()
        }
        if (path != null) {
            repository.setBackdropImage(path)
            repository.setBackdropMode(com.texfi.w0y.data.BackdropMode.PHOTO)
        }
    }

    fun setSeasonMode(value: com.texfi.w0y.data.SeasonMode) = update { repository.setSeasonMode(value) }

    fun setStandView(value: com.texfi.w0y.data.StandView) = update { repository.setStandView(value) }

    fun setStandChargingOnly(value: Boolean) = update { repository.setStandChargingOnly(value) }

    fun setStandMaxMinutes(value: Int) = update { repository.setStandMaxMinutes(value) }

    fun setStandLowBatteryExit(value: Boolean) = update { repository.setStandLowBatteryExit(value) }

    suspend fun exportJson(): String = repository.export(repository.settings.first())

    fun importJson(json: String) =
        update {
            runCatching { repository.import(json) }
                .onSuccess { _message.value = context.getString(R.string.settings_imported) }
                .onFailure { _message.value = context.getString(R.string.settings_import_failed, it.message.orEmpty()) }
        }

    fun reportNoEqualizer() {
        _message.value = context.getString(R.string.settings_no_equalizer)
    }

    /**
     * Смена языка.
     *
     * Ресурсы читаются при создании экрана, поэтому после записи настройки
     * активити пересоздаётся — иначе половина экрана осталась бы на старом
     * языке до следующего запуска.
     */
    fun setLanguage(value: Language, onApplied: () -> Unit) =
        update {
            repository.setLanguage(value)
            // Язык уходит и в запросы к YouTube: иначе ленты остались бы
            // на языке телефона до перезапуска процесса.
            youtube.applyAppLocale()
            onApplied()
        }

    fun consumeMessage() {
        _message.value = null
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
