package com.texfi.w0y.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.texfi.w0y.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("w0y")

/** Качество звука: с чем идти в извлечение потока. */
enum class Quality {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * Что играет после выбранного трека.
 *
 * Выбор запоминается: это привычка слушателя, а не настройка одного
 * нажатия, и спрашивать о ней при каждом запуске трека — издевательство.
 */
enum class QueueMode(
    @StringRes val label: Int,
    @StringRes val hint: Int,
) {
    ORDER(R.string.queue_mode_order, R.string.queue_mode_order_hint),
    SHUFFLE(R.string.queue_mode_shuffle, R.string.queue_mode_shuffle_hint),
    RADIO(R.string.queue_mode_radio, R.string.queue_mode_radio_hint),
}

/**
 * Реверб поверх трека.
 *
 * Не «эффект ради эффекта»: львиная доля того, что слушают в этом жанре, —
 * чужие slowed-переделки одних и тех же песен. Замедление с эхом прямо в
 * плеере делает такие переделки ненужными: любой оригинал звучит так же,
 * только без потери качества и без поиска нужной версии.
 */
enum class Reverb(@StringRes val label: Int) {
    OFF(R.string.reverb_none),
    ROOM(R.string.reverb_room),
    HALL(R.string.reverb_hall),
    CAVE(R.string.reverb_cave),
}

/**
 * Готовые сочетания скорости, тона и эха.
 *
 * Ровно те два, ради которых слушают чужие переделки: замедленное с эхом
 * и ускоренное. Тонкая настройка — в настройках, здесь одно нажатие.
 */
enum class SoundPreset(
    @StringRes val label: Int,
    val speed: Float,
    val pitch: Float,
    val reverb: Reverb,
) {
    SLOWED(R.string.preset_slowed, 0.85f, 0.92f, Reverb.HALL),
    NORMAL(R.string.preset_normal, 1f, 1f, Reverb.OFF),
    SPED(R.string.preset_sped, 1.25f, 1.06f, Reverb.OFF),
    ;

    fun matches(settings: W0ySettings): Boolean =
        settings.speed == speed && settings.pitch == pitch && settings.reverb == reverb

    fun matches(profile: SoundProfile): Boolean =
        profile.speed == speed && profile.pitch == pitch && profile.reverb == reverb
}

/**
 * Что делать с записью, помеченной «E», когда чистой версии не нашлось.
 *
 * Вырезать отдельное слово из готовой записи приложение не умеет и делать
 * вид, что умеет, не будет: выбор честный — играть как есть или пропустить.
 */
enum class ExplicitFallback(@StringRes val label: Int) {
    PLAY(R.string.explicit_play),
    SKIP(R.string.explicit_skip),
}

/** Откуда брать звук, когда YouTube Music не отдал трек. */
enum class FallbackSource(@StringRes val label: Int) {
    AUTO(R.string.fallback_auto),
    YOUTUBE(R.string.fallback_youtube),
    PIPED(R.string.fallback_piped),
}

/** Куда попадаешь при запуске. */
enum class StartTab(@StringRes val label: Int) {
    HOME(R.string.start_tab_home),
    SEARCH(R.string.start_tab_search),
    LIBRARY(R.string.start_tab_library),

    /** Там, где закрыл приложение в прошлый раз. */
    LAST(R.string.start_tab_last),
}

/**
 * Язык приложения.
 *
 * SYSTEM — как на телефоне; остальное — явный выбор, который сильнее
 * системного. Тег хранится рядом с остальными настройками, но дублируется
 * в SharedPreferences: локаль нужна ещё до того, как успеет прочитаться
 * DataStore, в attachBaseContext (см. [LocalePrefs]).
 */
enum class Language(@StringRes val label: Int, val tag: String?) {
    SYSTEM(R.string.language_system, null),
    EN(R.string.language_en, "en"),
    RU(R.string.language_ru, "ru"),
    UK(R.string.language_uk, "uk"),
    PL(R.string.language_pl, "pl"),
}

enum class ThemeMode {
    DARK,
    OLED,
    LIGHT,

    /** По системе: тёмная или светлая — как включено в телефоне. Только Hairline. */
    SYSTEM,

    /** Стекло — скрытая, экспериментальная; открывается в «О приложении». */
    GLASS,
}

/**
 * Цветовая схема: пара «акцент + вторичный».
 *
 * Синяя — по умолчанию и остаётся фирменной: `#4a7cfb` — цвет всей
 * экосистемы TexFi, по нему приложения читаются как семья. Остальные
 * схемы — выбор пользователя, а не новое лицо приложения.
 *
 * Цвета хранятся числами, а не `Color`: слой данных не должен тянуть за
 * собой Compose ради четырёх констант.
 */
enum class Accent(
    @StringRes val label: Int,
    val accent: Long,
    val deep: Long,
    val secondary: Long,
) {
    BLUE(R.string.accent_blue, 0xFF4A7CFB, 0xFF1E3F8F, 0xFFE0A860),
    PINK(R.string.accent_pink, 0xFFFB4A8D, 0xFF8F1E4C, 0xFFFFB2CF),
    VIOLET(R.string.accent_violet, 0xFF9A6BFF, 0xFF4C2E99, 0xFFE0A860),
    MINT(R.string.accent_mint, 0xFF3ED9A4, 0xFF167A5B, 0xFFE0A860),
    CRIMSON(R.string.accent_crimson, 0xFFFF5A5A, 0xFF8F2020, 0xFFE0A860),
    SAND(R.string.accent_sand, 0xFFE0A860, 0xFF8A5F26, 0xFF7FB5FF),
    LIGHT_BLUE(R.string.accent_light_blue, 0xFF7FB5FF, 0xFF2F5C94, 0xFFE0A860),
    WARM(R.string.accent_warm, 0xFFF0B49A, 0xFF8A4A32, 0xFF7FB5FF),

    /** Свой цвет: значение лежит в настройках, здесь только заглушка. */
    CUSTOM(R.string.accent_custom, 0xFFA06CFF, 0xFF4E3580, 0xFFE0A860),
}

/**
 * Стиль оформления — независимо от темы.
 *
 * Pixel — родной язык TexFi: пиксельный шрифт, спрайты, жёсткие смещённые
 * тени, ступенчатое движение. Smooth — без пикселей: скругления, мягкие
 * тени, плавные пружины, обычный шрифт. Палитра, нумерация разделов и
 * тон текста у обоих общие.
 */
enum class UiStyle(@StringRes val label: Int, @StringRes val hint: Int) {
    /** Тонкие линии, чистая геометрия, диск-обложка с кольцом прогресса. По умолчанию. */
    HAIRLINE(R.string.style_hairline, R.string.style_hairline_hint),
    PIXEL(R.string.style_pixel, R.string.style_pixel_hint),
    SMOOTH(R.string.style_smooth, R.string.style_smooth_hint),
}

/** Размер текста лирики. */
enum class LyricsSize(@StringRes val label: Int, val sp: Int) {
    SMALL(R.string.lyrics_size_small, 18),
    MEDIUM(R.string.lyrics_size_medium, 23),
    LARGE(R.string.lyrics_size_large, 29),
}

/**
 * Стиль визуализатора. Первые четыре — для Pixel, последние два — для
 * Smooth; если выбран стиль чужого оформления, берётся ближайший свой.
 */
enum class VisualizerStyle(@StringRes val label: Int, val smooth: Boolean, val hair: Boolean = false) {
    SEGMENTS(R.string.viz_segments, false),
    RING(R.string.viz_ring, false),
    SCOPE(R.string.viz_scope, false),
    MOSAIC(R.string.viz_mosaic, false),
    BARS(R.string.viz_bars, true),
    WAVE(R.string.viz_wave, true),

    /** Hairline: тонкие линии с «зависающими» пиками и отражением. */
    LINES(R.string.viz_lines, false, hair = true),

    /** Hairline: кольца, дышащие от баса, и лучи спектра по окружности. */
    ORBIT(R.string.viz_orbit, false, hair = true),

    /** Hairline: рельеф — стопка линий-спектров, уходящих вглубь по времени. */
    TERRAIN(R.string.viz_terrain, false, hair = true),
}

/** Что занимает место обложки в плеере. */
enum class PlayerArt { COVER, VISUALIZER }

/** Что крупно в режиме подставки: название с исполнителем или обложка. */
enum class StandView { TEXT, COVER }

// ── Hairline: настройки вида ─────────────────────────────────────────────

/** Толщина линий Hairline, dp. Тоньше одного dp не бывает: на плотных экранах линия пропадает. */
enum class LineWeight(@StringRes val label: Int, val dp: Float) {
    THIN(R.string.hl_weight_thin, 1f),
    REGULAR(R.string.hl_weight_regular, 1.5f),
    BOLD(R.string.hl_weight_bold, 2f),
}

/** Скругления карточек и кнопок: от острых до кругов. */
enum class CornerStyle(@StringRes val label: Int, val dp: Int) {
    SHARP(R.string.hl_corner_sharp, 3),
    SOFT(R.string.hl_corner_soft, 8),
    ROUND(R.string.hl_corner_round, 16),
    CIRCLE(R.string.hl_corner_circle, 999),
}

/** Форма обложки в плеере и списках. */
enum class CoverShape(@StringRes val label: Int) {
    DISC(R.string.hl_cover_disc),
    ROUNDED(R.string.hl_cover_rounded),
    SQUARE(R.string.hl_cover_square),
}

/** Раскладка полного плеера. */
enum class PlayerLayout(@StringRes val label: Int) {
    RING(R.string.hl_layout_ring),
    MINIMAL(R.string.hl_layout_minimal),
    COMPACT(R.string.hl_layout_compact),
}

/** Где текст набирается заглавными. */
enum class CapsMode(@StringRes val label: Int) {
    TITLES_LABELS(R.string.hl_caps_titles),
    LABELS(R.string.hl_caps_labels),
    OFF(R.string.hl_caps_off),
}

enum class Density(@StringRes val label: Int) {
    COMFORTABLE(R.string.hl_density_comfortable),
    COMPACT(R.string.hl_density_compact),
}

/**
 * Качество анимаций. AUTO сам выбирает по системному «убрать анимацию»,
 * экономии заряда и классу устройства; остальное — явный выбор.
 */
enum class MotionMode(@StringRes val label: Int) {
    AUTO(R.string.hl_motion_auto),
    FULL(R.string.hl_motion_full),
    LITE(R.string.hl_motion_lite),
    OFF(R.string.hl_motion_off),
}

enum class HapticLevel(@StringRes val label: Int) {
    OFF(R.string.hl_haptic_off),
    LIGHT(R.string.hl_haptic_light),
    FULL(R.string.hl_haptic_full),
}

/** Набор иконок интерфейса. */
enum class IconStyle(@StringRes val label: Int) {
    LINE(R.string.hl_icons_line),
    FILLED_OUTLINE(R.string.hl_icons_filled),
    DOTS(R.string.hl_icons_dots),
}

/** Иконка приложения на рабочем столе: по одному activity-alias на вариант. */
enum class AppIcon(@StringRes val label: Int, val alias: String, @androidx.annotation.DrawableRes val foreground: Int) {
    W(R.string.hl_icon_w, ".IconW", R.drawable.ic_icon_w_fg),
    LEAF(R.string.hl_icon_leaf, ".IconLeaf", R.drawable.ic_icon_leaf_fg),
    BARS(R.string.hl_icon_bars, ".IconBars", R.drawable.ic_icon_bars_fg),
    RINGS(R.string.hl_icon_rings, ".IconRings", R.drawable.ic_icon_rings_fg),
    LEAF_LINE(R.string.hl_icon_leaf_line, ".IconLeafLine", R.drawable.ic_icon_leaf_line_fg),
}

/**
 * Все настройки одним снимком — так экраны и плеер читают согласованное
 * состояние, а не собирают его из десятка отдельных потоков.
 */
data class W0ySettings(
    val qualityWifi: Quality = Quality.HIGH,
    val qualityMobile: Quality = Quality.MEDIUM,
    val preloadNext: Boolean = true,
    val cacheLimitMb: Int = 512,
    val autoDownloadLiked: Boolean = false,
    val normalizeVolume: Boolean = true,
    val skipSilence: Boolean = false,
    val pauseOnHeadphonesOut: Boolean = true,
    val resumeOnHeadphonesIn: Boolean = false,
    val sleepTimerDefaultMin: Int = 30,
    val theme: ThemeMode = ThemeMode.DARK,
    val showLyrics: Boolean = true,
    val keepHistory: Boolean = true,
    val queueMode: QueueMode = QueueMode.ORDER,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val reverb: Reverb = Reverb.OFF,
    val showRecommendations: Boolean = true,
    val compactRows: Boolean = false,
    val saveSearchHistory: Boolean = true,
    val downloadOnWifiOnly: Boolean = true,
    val startTab: StartTab = StartTab.LAST,
    /** Вкладка, на которой приложение закрыли: HOME, SEARCH или LIBRARY. */
    val lastTab: StartTab = StartTab.HOME,
    val cleanMode: Boolean = false,
    val explicitFallback: ExplicitFallback = ExplicitFallback.PLAY,
    val hideExplicit: Boolean = false,
    val muteSwearLines: Boolean = false,
    val welcomeSeen: Boolean = false,
    val language: Language = Language.SYSTEM,
    val animatedBackground: Boolean = true,
    val accent: Accent = Accent.SAND,
    /** Свой цвет схемы CUSTOM, ARGB. */
    val customAccent: Int = 0xFFA06CFF.toInt(),
    val syncPlaylists: Boolean = true,
    val searchSuggestions: Boolean = true,
    val haptics: Boolean = true,
    val seekStepSec: Int = 10,
    val playerCoverGlow: Boolean = true,
    val fallbackAudio: Boolean = true,
    val fallbackSource: FallbackSource = FallbackSource.AUTO,
    val lyricsLang: String = "",
    val experiments: Boolean = false,
    // ── Загрузки ─────────────────────────────────────────────────────────
    val downloadQualityWifi: Quality = Quality.HIGH,
    val downloadQualityMobile: Quality = Quality.HIGH,
    /** Сколько треков качается одновременно. */
    val downloadParallel: Int = 2,
    /** Лимит скорости загрузок, КБ/с; 0 — без лимита. */
    val downloadSpeedLimitKb: Int = 0,
    val downloadPauseOnLowBattery: Boolean = true,
    val downloadAutoResume: Boolean = true,
    val downloadRetries: Int = 3,
    /** Предел места под загрузки, МБ; 0 — без предела. */
    val downloadStorageLimitMb: Int = 0,
    val downloadNotifications: Boolean = true,
    /** Папка «Сохранить на устройство» (SAF, tree URI); пусто — «Музыка/w0y music». */
    val exportTreeUri: String = "",
    val autoExport: Boolean = false,
    val exportWarningSeen: Boolean = false,
    /** Спросили ли на экране «Скачано», куда сохранять на телефон. */
    val exportFolderAsked: Boolean = false,
    /** Свои плейлисты, которые докачиваются сами. */
    val autoDownloadPlaylists: Set<Long> = emptySet(),
    // ── Плейлисты и быстрый набор ────────────────────────────────────────
    val showPlaylistRecommendations: Boolean = false,
    /** Со скольких прослушиваний трек попадает в быстрый набор. */
    val dialMinPlays: Int = 1,
    // ── Стиль ────────────────────────────────────────────────────────────
    val uiStyle: UiStyle = UiStyle.HAIRLINE,
    /** Smooth: жидкое стекло с бликами (true) или матовые плотные поверхности. */
    val smoothGlass: Boolean = true,
    /** Показан ли выбор стиля — на первом запуске и один раз после обновления. */
    val stylePicked: Boolean = false,
    // ── Лирика и визуализатор ────────────────────────────────────────────
    val lyricsSize: LyricsSize = LyricsSize.MEDIUM,
    /** Насколько позже звучащей строки подсвечивать текст, мс (минус — раньше). */
    val lyricsDelayMs: Int = 0,
    /** Искать текст не только в LRCLIB, но и в других базах. */
    val lyricsAllSources: Boolean = true,
    val visualizerStyle: VisualizerStyle = VisualizerStyle.SEGMENTS,
    val visualizerSensitivity: Float = 1f,
    val visualizerFps: Int = 60,
    val visualizerCoverBackdrop: Boolean = true,
    val playerArt: PlayerArt = PlayerArt.COVER,
    // ── Режим подставки ──────────────────────────────────────────────────
    val standLyrics: Boolean = false,
    val standVisualizer: Boolean = false,
    /**
     * Яркость экрана в режиме, 0.01–1; меньше нуля — как в системе. По
     * умолчанию системная: подставка не должна делать экран ярче, чем был.
     */
    val standBrightness: Float = STAND_BRIGHTNESS_SYSTEM,
    /** Название бежит строкой, а не переносится. */
    val standMarquee: Boolean = false,
    /** Подставка вверх ногами: телефон стоит разъёмом вверх. */
    val standFlipped: Boolean = false,
    val standView: StandView = StandView.TEXT,
    val standChargingOnly: Boolean = false,
    /** Сколько режим держится сам, минут; 0 — пока не выйдешь. */
    val standMaxMinutes: Int = 60,
    val standLowBatteryExit: Boolean = true,
    // ── Hairline ─────────────────────────────────────────────────────────
    val lineWeight: LineWeight = LineWeight.THIN,
    val corners: CornerStyle = CornerStyle.SHARP,
    val coverShape: CoverShape = CoverShape.ROUNDED,
    val playerLayout: PlayerLayout = PlayerLayout.RING,
    val caps: CapsMode = CapsMode.TITLES_LABELS,
    val density: Density = Density.COMFORTABLE,
    val motion: MotionMode = MotionMode.AUTO,
    val hapticLevel: HapticLevel = HapticLevel.FULL,
    val iconStyle: IconStyle = IconStyle.LINE,
    val appIcon: AppIcon = AppIcon.W,
    /** Потянуть мини-плеер вниз — остановить и убрать. */
    val swipeDismiss: Boolean = true,
    /** Подсказки жестов показаны. */
    val gestureHintSeen: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val settings: Flow<W0ySettings> =
        context.dataStore.data
            // Повреждённый файл настроек не должен ронять приложение —
            // лучше стартовать со значениями по умолчанию.
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs ->
                W0ySettings(
                    qualityWifi = prefs.enum(Keys.QUALITY_WIFI, Quality.HIGH),
                    qualityMobile = prefs.enum(Keys.QUALITY_MOBILE, Quality.MEDIUM),
                    preloadNext = prefs[Keys.PRELOAD] ?: true,
                    cacheLimitMb = prefs[Keys.CACHE_MB] ?: 512,
                    autoDownloadLiked = prefs[Keys.AUTO_DOWNLOAD] ?: false,
                    normalizeVolume = prefs[Keys.NORMALIZE] ?: true,
                    skipSilence = prefs[Keys.SKIP_SILENCE] ?: false,
                    pauseOnHeadphonesOut = prefs[Keys.PAUSE_ON_UNPLUG] ?: true,
                    resumeOnHeadphonesIn = prefs[Keys.RESUME_ON_PLUG] ?: false,
                    sleepTimerDefaultMin = prefs[Keys.SLEEP_MIN] ?: 30,
                    theme = prefs.enum(Keys.THEME, ThemeMode.DARK),
                    showLyrics = prefs[Keys.LYRICS] ?: true,
                    keepHistory = prefs[Keys.HISTORY] ?: true,
                    queueMode = prefs.enum(Keys.QUEUE_MODE, QueueMode.ORDER),
                    speed = prefs[Keys.SPEED] ?: 1f,
                    pitch = prefs[Keys.PITCH] ?: 1f,
                    reverb = prefs.enum(Keys.REVERB, Reverb.OFF),
                    showRecommendations = prefs[Keys.SHOW_RECOMMENDATIONS] ?: true,
                    compactRows = prefs[Keys.COMPACT_ROWS] ?: false,
                    saveSearchHistory = prefs[Keys.SAVE_SEARCHES] ?: true,
                    downloadOnWifiOnly = prefs[Keys.WIFI_ONLY_DOWNLOADS] ?: true,
                    startTab = prefs.enum(Keys.START_TAB, StartTab.LAST),
                    lastTab = prefs.enum(Keys.LAST_TAB, StartTab.HOME),
                    cleanMode = prefs[Keys.CLEAN_MODE] ?: false,
                    explicitFallback = prefs.enum(Keys.EXPLICIT_FALLBACK, ExplicitFallback.PLAY),
                    hideExplicit = prefs[Keys.HIDE_EXPLICIT] ?: false,
                    muteSwearLines = prefs[Keys.MUTE_SWEAR_LINES] ?: false,
                    welcomeSeen = prefs[Keys.WELCOME_SEEN] ?: false,
                    language = prefs.enum(Keys.LANGUAGE, Language.SYSTEM),
                    animatedBackground = prefs[Keys.ANIMATED_BACKGROUND] ?: true,
                    accent = prefs.enum(Keys.ACCENT, Accent.SAND),
                    customAccent = prefs[Keys.CUSTOM_ACCENT] ?: 0xFFA06CFF.toInt(),
                    syncPlaylists = prefs[Keys.SYNC_PLAYLISTS] ?: true,
                    searchSuggestions = prefs[Keys.SEARCH_SUGGESTIONS] ?: true,
                    haptics = hapticLevelOf(prefs) != HapticLevel.OFF,
                    hapticLevel = hapticLevelOf(prefs),
                    seekStepSec = prefs[Keys.SEEK_STEP] ?: 10,
                    playerCoverGlow = prefs[Keys.COVER_GLOW] ?: true,
                    fallbackAudio = prefs[Keys.FALLBACK_AUDIO] ?: true,
                    fallbackSource = prefs.enum(Keys.FALLBACK_SOURCE, FallbackSource.AUTO),
                    lyricsLang = prefs[Keys.LYRICS_LANG].orEmpty(),
                    experiments = prefs[Keys.EXPERIMENTS] ?: false,
                    downloadQualityWifi = prefs.enum(Keys.DL_QUALITY_WIFI, Quality.HIGH),
                    downloadQualityMobile = prefs.enum(Keys.DL_QUALITY_MOBILE, Quality.HIGH),
                    downloadParallel = (prefs[Keys.DL_PARALLEL] ?: 2).coerceIn(1, 4),
                    downloadSpeedLimitKb = prefs[Keys.DL_SPEED_LIMIT] ?: 0,
                    downloadPauseOnLowBattery = prefs[Keys.DL_LOW_BATTERY] ?: true,
                    downloadAutoResume = prefs[Keys.DL_AUTO_RESUME] ?: true,
                    downloadRetries = (prefs[Keys.DL_RETRIES] ?: 3).coerceIn(0, 10),
                    downloadStorageLimitMb = prefs[Keys.DL_STORAGE_LIMIT] ?: 0,
                    downloadNotifications = prefs[Keys.DL_NOTIFICATIONS] ?: true,
                    exportTreeUri = prefs[Keys.EXPORT_TREE].orEmpty(),
                    autoExport = prefs[Keys.AUTO_EXPORT] ?: false,
                    exportWarningSeen = prefs[Keys.EXPORT_WARNING] ?: false,
                    exportFolderAsked = prefs[Keys.EXPORT_FOLDER_ASKED] ?: false,
                    autoDownloadPlaylists =
                        prefs[Keys.AUTO_DL_PLAYLISTS].orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet(),
                    showPlaylistRecommendations = prefs[Keys.PLAYLIST_RECS] ?: false,
                    dialMinPlays = (prefs[Keys.DIAL_MIN_PLAYS] ?: 1).coerceIn(1, 20),
                    uiStyle = prefs.enum(Keys.UI_STYLE, UiStyle.HAIRLINE),
                    smoothGlass = prefs[Keys.SMOOTH_GLASS] ?: true,
                    stylePicked = prefs[Keys.STYLE_PICKED] ?: false,
                    lyricsSize = prefs.enum(Keys.LYRICS_SIZE, LyricsSize.MEDIUM),
                    lyricsDelayMs = prefs[Keys.LYRICS_DELAY] ?: 0,
                    lyricsAllSources = prefs[Keys.LYRICS_ALL] ?: true,
                    visualizerStyle = prefs.enum(Keys.VIZ_STYLE, VisualizerStyle.SEGMENTS),
                    visualizerSensitivity = prefs[Keys.VIZ_SENSITIVITY] ?: 1f,
                    visualizerFps = prefs[Keys.VIZ_FPS] ?: 60,
                    visualizerCoverBackdrop = prefs[Keys.VIZ_BACKDROP] ?: true,
                    playerArt = prefs.enum(Keys.PLAYER_ART, PlayerArt.COVER),
                    standLyrics = prefs[Keys.STAND_LYRICS] ?: false,
                    standVisualizer = prefs[Keys.STAND_VIZ] ?: false,
                    standBrightness = prefs[Keys.STAND_BRIGHTNESS] ?: STAND_BRIGHTNESS_SYSTEM,
                    standMarquee = prefs[Keys.STAND_MARQUEE] ?: false,
                    standFlipped = prefs[Keys.STAND_FLIPPED] ?: false,
                    standView = prefs.enum(Keys.STAND_VIEW, StandView.TEXT),
                    standChargingOnly = prefs[Keys.STAND_CHARGING] ?: false,
                    standMaxMinutes = prefs[Keys.STAND_MAX_MIN] ?: 60,
                    standLowBatteryExit = prefs[Keys.STAND_LOW_BATTERY] ?: true,
                    lineWeight = prefs.enum(Keys.HL_WEIGHT, LineWeight.THIN),
                    corners = prefs.enum(Keys.HL_CORNERS, CornerStyle.SHARP),
                    coverShape = prefs.enum(Keys.HL_COVER, CoverShape.ROUNDED),
                    playerLayout = prefs.enum(Keys.HL_LAYOUT, PlayerLayout.RING),
                    caps = prefs.enum(Keys.HL_CAPS, CapsMode.TITLES_LABELS),
                    density = prefs.enum(Keys.HL_DENSITY, Density.COMFORTABLE),
                    motion = prefs.enum(Keys.HL_MOTION, MotionMode.AUTO),
                    iconStyle = prefs.enum(Keys.HL_ICONS, IconStyle.LINE),
                    appIcon = prefs.enum(Keys.HL_APP_ICON, AppIcon.W),
                    swipeDismiss = prefs[Keys.HL_SWIPE_DISMISS] ?: true,
                    gestureHintSeen = prefs[Keys.HL_HINT_SEEN] ?: false,
                )
            }

    suspend fun setQualityWifi(value: Quality) = put(Keys.QUALITY_WIFI, value.name)

    suspend fun setQualityMobile(value: Quality) = put(Keys.QUALITY_MOBILE, value.name)

    suspend fun setPreload(value: Boolean) = put(Keys.PRELOAD, value)

    suspend fun setCacheLimit(mb: Int) = put(Keys.CACHE_MB, mb)

    suspend fun setAutoDownload(value: Boolean) = put(Keys.AUTO_DOWNLOAD, value)

    suspend fun setNormalize(value: Boolean) = put(Keys.NORMALIZE, value)

    suspend fun setSkipSilence(value: Boolean) = put(Keys.SKIP_SILENCE, value)

    suspend fun setPauseOnUnplug(value: Boolean) = put(Keys.PAUSE_ON_UNPLUG, value)

    suspend fun setResumeOnPlug(value: Boolean) = put(Keys.RESUME_ON_PLUG, value)

    suspend fun setSleepDefault(minutes: Int) = put(Keys.SLEEP_MIN, minutes)

    suspend fun setTheme(value: ThemeMode) = put(Keys.THEME, value.name)

    suspend fun setShowLyrics(value: Boolean) = put(Keys.LYRICS, value)

    suspend fun setKeepHistory(value: Boolean) = put(Keys.HISTORY, value)

    suspend fun setQueueMode(value: QueueMode) = put(Keys.QUEUE_MODE, value.name)

    suspend fun setSpeed(value: Float) = put(Keys.SPEED, value)

    suspend fun setPitch(value: Float) = put(Keys.PITCH, value)

    suspend fun setReverb(value: Reverb) = put(Keys.REVERB, value.name)

    /** Пресет звучания меняет скорость, тон и эхо разом — как одну ручку. */
    suspend fun setSoundPreset(speed: Float, pitch: Float, reverb: Reverb) {
        context.dataStore.edit {
            it[Keys.SPEED] = speed
            it[Keys.PITCH] = pitch
            it[Keys.REVERB] = reverb.name
        }
    }

    suspend fun setShowRecommendations(value: Boolean) = put(Keys.SHOW_RECOMMENDATIONS, value)

    suspend fun setCompactRows(value: Boolean) = put(Keys.COMPACT_ROWS, value)

    suspend fun setSaveSearchHistory(value: Boolean) = put(Keys.SAVE_SEARCHES, value)

    suspend fun setDownloadOnWifiOnly(value: Boolean) = put(Keys.WIFI_ONLY_DOWNLOADS, value)

    suspend fun setStartTab(value: StartTab) = put(Keys.START_TAB, value.name)

    suspend fun setLastTab(value: StartTab) = put(Keys.LAST_TAB, value.name)

    suspend fun setCleanMode(value: Boolean) = put(Keys.CLEAN_MODE, value)

    suspend fun setExplicitFallback(value: ExplicitFallback) = put(Keys.EXPLICIT_FALLBACK, value.name)

    suspend fun setHideExplicit(value: Boolean) = put(Keys.HIDE_EXPLICIT, value)

    suspend fun setMuteSwearLines(value: Boolean) = put(Keys.MUTE_SWEAR_LINES, value)

    suspend fun setWelcomeSeen(value: Boolean) = put(Keys.WELCOME_SEEN, value)

    suspend fun setAnimatedBackground(value: Boolean) = put(Keys.ANIMATED_BACKGROUND, value)

    suspend fun setAccent(value: Accent) = put(Keys.ACCENT, value.name)

    /** Свой цвет сразу включает схему CUSTOM: выбрал цвет — увидел его. */
    suspend fun setCustomAccent(argb: Int) {
        context.dataStore.edit {
            it[Keys.CUSTOM_ACCENT] = argb
            it[Keys.ACCENT] = Accent.CUSTOM.name
        }
    }

    suspend fun setSyncPlaylists(value: Boolean) = put(Keys.SYNC_PLAYLISTS, value)

    suspend fun setSearchSuggestions(value: Boolean) = put(Keys.SEARCH_SUGGESTIONS, value)

    suspend fun setHaptics(value: Boolean) {
        put(Keys.HAPTICS, value)
        put(Keys.HL_HAPTIC, (if (value) HapticLevel.FULL else HapticLevel.OFF).name)
    }

    suspend fun setHapticLevel(value: HapticLevel) {
        put(Keys.HAPTICS, value != HapticLevel.OFF)
        put(Keys.HL_HAPTIC, value.name)
    }

    suspend fun setLineWeight(value: LineWeight) = put(Keys.HL_WEIGHT, value.name)

    suspend fun setCorners(value: CornerStyle) = put(Keys.HL_CORNERS, value.name)

    suspend fun setCoverShape(value: CoverShape) = put(Keys.HL_COVER, value.name)

    suspend fun setPlayerLayout(value: PlayerLayout) = put(Keys.HL_LAYOUT, value.name)

    suspend fun setCaps(value: CapsMode) = put(Keys.HL_CAPS, value.name)

    suspend fun setDensity(value: Density) = put(Keys.HL_DENSITY, value.name)

    suspend fun setMotion(value: MotionMode) = put(Keys.HL_MOTION, value.name)

    suspend fun setIconStyle(value: IconStyle) = put(Keys.HL_ICONS, value.name)

    suspend fun setAppIcon(value: AppIcon) = put(Keys.HL_APP_ICON, value.name)

    suspend fun setSwipeDismiss(value: Boolean) = put(Keys.HL_SWIPE_DISMISS, value)

    suspend fun setGestureHintSeen(value: Boolean) = put(Keys.HL_HINT_SEEN, value)

    /** Вернуть всё, что настроено в разделе Look → Hairline, к значениям по умолчанию. */
    suspend fun resetLook() {
        context.dataStore.edit {
            listOf(
                Keys.HL_WEIGHT, Keys.HL_CORNERS, Keys.HL_COVER, Keys.HL_LAYOUT, Keys.HL_CAPS,
                Keys.HL_DENSITY, Keys.HL_MOTION, Keys.HL_ICONS,
            ).forEach { key -> it.remove(key) }
            it.remove(Keys.ACCENT)
        }
    }

    /** Старые установки хранили вибрацию флагом: нет уровня — берём флаг. */
    private fun hapticLevelOf(prefs: Preferences): HapticLevel =
        prefs[Keys.HL_HAPTIC]?.let { name -> runCatching { enumValueOf<HapticLevel>(name) }.getOrNull() }
            ?: if (prefs[Keys.HAPTICS] == false) HapticLevel.OFF else HapticLevel.FULL

    suspend fun setSeekStep(seconds: Int) = put(Keys.SEEK_STEP, seconds)

    suspend fun setPlayerCoverGlow(value: Boolean) = put(Keys.COVER_GLOW, value)

    suspend fun setFallbackAudio(value: Boolean) = put(Keys.FALLBACK_AUDIO, value)

    suspend fun setLyricsLang(value: String) = put(Keys.LYRICS_LANG, value)

    suspend fun setFallbackSource(value: FallbackSource) = put(Keys.FALLBACK_SOURCE, value.name)

    suspend fun setExperiments(value: Boolean) = put(Keys.EXPERIMENTS, value)

    suspend fun setDownloadQualityWifi(value: Quality) = put(Keys.DL_QUALITY_WIFI, value.name)

    suspend fun setDownloadQualityMobile(value: Quality) = put(Keys.DL_QUALITY_MOBILE, value.name)

    suspend fun setDownloadParallel(value: Int) = put(Keys.DL_PARALLEL, value.coerceIn(1, 4))

    suspend fun setDownloadSpeedLimit(kb: Int) = put(Keys.DL_SPEED_LIMIT, kb.coerceAtLeast(0))

    suspend fun setDownloadPauseOnLowBattery(value: Boolean) = put(Keys.DL_LOW_BATTERY, value)

    suspend fun setDownloadAutoResume(value: Boolean) = put(Keys.DL_AUTO_RESUME, value)

    suspend fun setDownloadRetries(value: Int) = put(Keys.DL_RETRIES, value.coerceIn(0, 10))

    suspend fun setDownloadStorageLimit(mb: Int) = put(Keys.DL_STORAGE_LIMIT, mb.coerceAtLeast(0))

    suspend fun setDownloadNotifications(value: Boolean) = put(Keys.DL_NOTIFICATIONS, value)

    suspend fun setAutoExport(value: Boolean) = put(Keys.AUTO_EXPORT, value)

    suspend fun setExportWarningSeen(value: Boolean) = put(Keys.EXPORT_WARNING, value)

    /** Ответ на вопрос о папке: своя папка (uri) или пустая строка — по умолчанию. */
    suspend fun answerExportFolder(uri: String) {
        context.dataStore.edit {
            it[Keys.EXPORT_TREE] = uri
            it[Keys.EXPORT_FOLDER_ASKED] = true
            it[Keys.EXPORT_WARNING] = true
        }
    }

    suspend fun setAutoDownloadPlaylist(id: Long, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val now = prefs[Keys.AUTO_DL_PLAYLISTS].orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toMutableSet()
            if (enabled) now += id else now -= id
            prefs[Keys.AUTO_DL_PLAYLISTS] = now.joinToString(",")
        }
    }

    suspend fun setShowPlaylistRecommendations(value: Boolean) = put(Keys.PLAYLIST_RECS, value)

    suspend fun setDialMinPlays(value: Int) = put(Keys.DIAL_MIN_PLAYS, value.coerceIn(1, 20))

    suspend fun setUiStyle(value: UiStyle) = put(Keys.UI_STYLE, value.name)

    suspend fun setSmoothGlass(value: Boolean) = put(Keys.SMOOTH_GLASS, value)

    suspend fun setStylePicked(value: Boolean) = put(Keys.STYLE_PICKED, value)

    suspend fun setLyricsSize(value: LyricsSize) = put(Keys.LYRICS_SIZE, value.name)

    suspend fun setLyricsDelayMs(value: Int) = put(Keys.LYRICS_DELAY, value)

    suspend fun setLyricsAllSources(value: Boolean) = put(Keys.LYRICS_ALL, value)

    suspend fun setVisualizerStyle(value: VisualizerStyle) = put(Keys.VIZ_STYLE, value.name)

    suspend fun setVisualizerSensitivity(value: Float) = put(Keys.VIZ_SENSITIVITY, value.coerceIn(0.3f, 3f))

    suspend fun setVisualizerFps(value: Int) = put(Keys.VIZ_FPS, if (value >= 60) 60 else 30)

    suspend fun setVisualizerCoverBackdrop(value: Boolean) = put(Keys.VIZ_BACKDROP, value)

    suspend fun setPlayerArt(value: PlayerArt) = put(Keys.PLAYER_ART, value.name)

    suspend fun setStandLyrics(value: Boolean) = put(Keys.STAND_LYRICS, value)

    suspend fun setStandVisualizer(value: Boolean) = put(Keys.STAND_VIZ, value)

    suspend fun setStandBrightness(value: Float) =
        put(Keys.STAND_BRIGHTNESS, if (value < 0f) STAND_BRIGHTNESS_SYSTEM else value.coerceIn(0.01f, 1f))

    suspend fun setStandMarquee(value: Boolean) = put(Keys.STAND_MARQUEE, value)

    suspend fun setStandFlipped(value: Boolean) = put(Keys.STAND_FLIPPED, value)

    suspend fun setStandView(value: StandView) = put(Keys.STAND_VIEW, value.name)

    suspend fun setStandChargingOnly(value: Boolean) = put(Keys.STAND_CHARGING, value)

    suspend fun setStandMaxMinutes(value: Int) = put(Keys.STAND_MAX_MIN, value.coerceAtLeast(0))

    suspend fun setStandLowBatteryExit(value: Boolean) = put(Keys.STAND_LOW_BATTERY, value)

    /**
     * Язык пишется сразу в двух местах.
     *
     * DataStore — источник истины для экрана настроек, SharedPreferences —
     * потому что локаль нужна в `attachBaseContext`, где нельзя ждать
     * корутину: там читают [LocalePrefs].
     */
    suspend fun setLanguage(value: Language) {
        LocalePrefs.save(context, value)
        put(Keys.LANGUAGE, value.name)
    }

    /** Экспорт всех настроек одной строкой JSON — её можно сохранить в файл. */
    suspend fun export(current: W0ySettings): String =
        JSONObject()
            .put("qualityWifi", current.qualityWifi.name)
            .put("qualityMobile", current.qualityMobile.name)
            .put("preloadNext", current.preloadNext)
            .put("cacheLimitMb", current.cacheLimitMb)
            .put("autoDownloadLiked", current.autoDownloadLiked)
            .put("normalizeVolume", current.normalizeVolume)
            .put("skipSilence", current.skipSilence)
            .put("pauseOnHeadphonesOut", current.pauseOnHeadphonesOut)
            .put("resumeOnHeadphonesIn", current.resumeOnHeadphonesIn)
            .put("sleepTimerDefaultMin", current.sleepTimerDefaultMin)
            .put("theme", current.theme.name)
            .put("showLyrics", current.showLyrics)
            .put("keepHistory", current.keepHistory)
            .put("language", current.language.name)
            .put("animatedBackground", current.animatedBackground)
            .put("accent", current.accent.name)
            .put("customAccent", current.customAccent)
            .put("syncPlaylists", current.syncPlaylists)
            .put("searchSuggestions", current.searchSuggestions)
            .put("haptics", current.haptics)
            .put("seekStepSec", current.seekStepSec)
            .put("playerCoverGlow", current.playerCoverGlow)
            .put("fallbackAudio", current.fallbackAudio)
            .put("fallbackSource", current.fallbackSource.name)
            .put("uiStyle", current.uiStyle.name)
            .put("smoothGlass", current.smoothGlass)
            .put("downloadQualityWifi", current.downloadQualityWifi.name)
            .put("downloadQualityMobile", current.downloadQualityMobile.name)
            .put("downloadParallel", current.downloadParallel)
            .put("dialMinPlays", current.dialMinPlays)
            .put("lyricsSize", current.lyricsSize.name)
            .put("lyricsDelayMs", current.lyricsDelayMs)
            .put("lyricsAllSources", current.lyricsAllSources)
            .put("visualizerStyle", current.visualizerStyle.name)
            .put("lineWeight", current.lineWeight.name)
            .put("corners", current.corners.name)
            .put("coverShape", current.coverShape.name)
            .put("playerLayout", current.playerLayout.name)
            .put("caps", current.caps.name)
            .put("density", current.density.name)
            .put("motion", current.motion.name)
            .put("hapticLevel", current.hapticLevel.name)
            .put("iconStyle", current.iconStyle.name)
            .put("swipeDismiss", current.swipeDismiss)
            .toString(2)

    suspend fun import(json: String) {
        val obj = JSONObject(json)
        context.dataStore.edit { prefs ->
            obj.optString("qualityWifi").takeIf { it.isNotBlank() }?.let { prefs[Keys.QUALITY_WIFI] = it }
            obj.optString("qualityMobile").takeIf { it.isNotBlank() }?.let { prefs[Keys.QUALITY_MOBILE] = it }
            obj.optString("theme").takeIf { it.isNotBlank() }?.let { prefs[Keys.THEME] = it }
            if (obj.has("preloadNext")) prefs[Keys.PRELOAD] = obj.getBoolean("preloadNext")
            if (obj.has("cacheLimitMb")) prefs[Keys.CACHE_MB] = obj.getInt("cacheLimitMb")
            if (obj.has("autoDownloadLiked")) prefs[Keys.AUTO_DOWNLOAD] = obj.getBoolean("autoDownloadLiked")
            if (obj.has("normalizeVolume")) prefs[Keys.NORMALIZE] = obj.getBoolean("normalizeVolume")
            if (obj.has("skipSilence")) prefs[Keys.SKIP_SILENCE] = obj.getBoolean("skipSilence")
            if (obj.has("pauseOnHeadphonesOut")) prefs[Keys.PAUSE_ON_UNPLUG] = obj.getBoolean("pauseOnHeadphonesOut")
            if (obj.has("resumeOnHeadphonesIn")) prefs[Keys.RESUME_ON_PLUG] = obj.getBoolean("resumeOnHeadphonesIn")
            if (obj.has("sleepTimerDefaultMin")) prefs[Keys.SLEEP_MIN] = obj.getInt("sleepTimerDefaultMin")
            if (obj.has("showLyrics")) prefs[Keys.LYRICS] = obj.getBoolean("showLyrics")
            if (obj.has("keepHistory")) prefs[Keys.HISTORY] = obj.getBoolean("keepHistory")
            if (obj.has("animatedBackground")) {
                prefs[Keys.ANIMATED_BACKGROUND] = obj.getBoolean("animatedBackground")
            }
            obj.optString("accent").takeIf { it.isNotBlank() }?.let { prefs[Keys.ACCENT] = it }
            if (obj.has("customAccent")) prefs[Keys.CUSTOM_ACCENT] = obj.getInt("customAccent")
            if (obj.has("syncPlaylists")) prefs[Keys.SYNC_PLAYLISTS] = obj.getBoolean("syncPlaylists")
            if (obj.has("searchSuggestions")) prefs[Keys.SEARCH_SUGGESTIONS] = obj.getBoolean("searchSuggestions")
            if (obj.has("haptics")) prefs[Keys.HAPTICS] = obj.getBoolean("haptics")
            if (obj.has("seekStepSec")) prefs[Keys.SEEK_STEP] = obj.getInt("seekStepSec")
            if (obj.has("playerCoverGlow")) prefs[Keys.COVER_GLOW] = obj.getBoolean("playerCoverGlow")
            if (obj.has("fallbackAudio")) prefs[Keys.FALLBACK_AUDIO] = obj.getBoolean("fallbackAudio")
            obj.optString("fallbackSource").takeIf { it.isNotBlank() }?.let { prefs[Keys.FALLBACK_SOURCE] = it }
            obj.optString("uiStyle").takeIf { it.isNotBlank() }?.let { prefs[Keys.UI_STYLE] = it }
            if (obj.has("smoothGlass")) prefs[Keys.SMOOTH_GLASS] = obj.getBoolean("smoothGlass")
            obj.optString("downloadQualityWifi").takeIf { it.isNotBlank() }?.let { prefs[Keys.DL_QUALITY_WIFI] = it }
            obj.optString("downloadQualityMobile").takeIf { it.isNotBlank() }?.let { prefs[Keys.DL_QUALITY_MOBILE] = it }
            if (obj.has("downloadParallel")) prefs[Keys.DL_PARALLEL] = obj.getInt("downloadParallel")
            if (obj.has("dialMinPlays")) prefs[Keys.DIAL_MIN_PLAYS] = obj.getInt("dialMinPlays")
            obj.optString("lyricsSize").takeIf { it.isNotBlank() }?.let { prefs[Keys.LYRICS_SIZE] = it }
            if (obj.has("lyricsDelayMs")) prefs[Keys.LYRICS_DELAY] = obj.getInt("lyricsDelayMs")
            if (obj.has("lyricsAllSources")) prefs[Keys.LYRICS_ALL] = obj.getBoolean("lyricsAllSources")
            obj.optString("visualizerStyle").takeIf { it.isNotBlank() }?.let { prefs[Keys.VIZ_STYLE] = it }
            obj.optString("lineWeight").takeIf { it.isNotBlank() }?.let { prefs[Keys.HL_WEIGHT] = it }
            obj.optString("corners").takeIf { it.isNotBlank() }?.let { prefs[Keys.HL_CORNERS] = it }
            obj.optString("coverShape").takeIf { it.isNotBlank() }?.let { prefs[Keys.HL_COVER] = it }
            obj.optString("playerLayout").takeIf { it.isNotBlank() }?.let { prefs[Keys.HL_LAYOUT] = it }
            obj.optString("caps").takeIf { it.isNotBlank() }?.let { prefs[Keys.HL_CAPS] = it }
            obj.optString("density").takeIf { it.isNotBlank() }?.let { prefs[Keys.HL_DENSITY] = it }
            obj.optString("motion").takeIf { it.isNotBlank() }?.let { prefs[Keys.HL_MOTION] = it }
            obj.optString("hapticLevel").takeIf { it.isNotBlank() }?.let { prefs[Keys.HL_HAPTIC] = it }
            obj.optString("iconStyle").takeIf { it.isNotBlank() }?.let { prefs[Keys.HL_ICONS] = it }
            if (obj.has("swipeDismiss")) prefs[Keys.HL_SWIPE_DISMISS] = obj.getBoolean("swipeDismiss")
            // Язык из выгрузки нужно продублировать в синхронное хранилище:
            // именно оттуда его читает attachBaseContext при следующем старте.
            obj.optString("language").takeIf { it.isNotBlank() }?.let { name ->
                prefs[Keys.LANGUAGE] = name
                Language.entries.firstOrNull { it.name == name }?.let { LocalePrefs.save(context, it) }
            }
        }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) =
        context.dataStore.edit { it[key] = value }.let { }

    private suspend fun put(key: Preferences.Key<Int>, value: Int) =
        context.dataStore.edit { it[key] = value }.let { }

    private suspend fun put(key: Preferences.Key<String>, value: String) =
        context.dataStore.edit { it[key] = value }.let { }

    private suspend fun put(key: Preferences.Key<Float>, value: Float) =
        context.dataStore.edit { it[key] = value }.let { }

    private inline fun <reified T : Enum<T>> Preferences.enum(
        key: Preferences.Key<String>,
        fallback: T,
    ): T = this[key]?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: fallback

    private object Keys {
        val QUALITY_WIFI = stringPreferencesKey("quality_wifi")
        val QUALITY_MOBILE = stringPreferencesKey("quality_mobile")
        val PRELOAD = booleanPreferencesKey("preload_next")
        val CACHE_MB = intPreferencesKey("cache_limit_mb")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_liked")
        val NORMALIZE = booleanPreferencesKey("normalize_volume")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val PAUSE_ON_UNPLUG = booleanPreferencesKey("pause_on_unplug")
        val RESUME_ON_PLUG = booleanPreferencesKey("resume_on_plug")
        val SLEEP_MIN = intPreferencesKey("sleep_timer_default")
        val THEME = stringPreferencesKey("theme")
        val EXPERIMENTS = booleanPreferencesKey("experiments")
        val LYRICS = booleanPreferencesKey("show_lyrics")
        val HISTORY = booleanPreferencesKey("keep_history")
        val QUEUE_MODE = stringPreferencesKey("queue_mode")
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("pitch")
        val REVERB = stringPreferencesKey("reverb")
        val SHOW_RECOMMENDATIONS = booleanPreferencesKey("show_recommendations")
        val COMPACT_ROWS = booleanPreferencesKey("compact_rows")
        val SAVE_SEARCHES = booleanPreferencesKey("save_searches")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val START_TAB = stringPreferencesKey("start_tab")
        val LAST_TAB = stringPreferencesKey("last_tab")
        val CLEAN_MODE = booleanPreferencesKey("clean_mode")
        val EXPLICIT_FALLBACK = stringPreferencesKey("explicit_fallback")
        val HIDE_EXPLICIT = booleanPreferencesKey("hide_explicit")
        val MUTE_SWEAR_LINES = booleanPreferencesKey("mute_swear_lines")
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
        val LANGUAGE = stringPreferencesKey("language")
        val ANIMATED_BACKGROUND = booleanPreferencesKey("animated_background")
        val ACCENT = stringPreferencesKey("accent")
        val CUSTOM_ACCENT = intPreferencesKey("custom_accent")
        val SYNC_PLAYLISTS = booleanPreferencesKey("sync_playlists")
        val SEARCH_SUGGESTIONS = booleanPreferencesKey("search_suggestions")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SEEK_STEP = intPreferencesKey("seek_step_sec")
        val COVER_GLOW = booleanPreferencesKey("player_cover_glow")
        val FALLBACK_AUDIO = booleanPreferencesKey("fallback_audio")
        val FALLBACK_SOURCE = stringPreferencesKey("fallback_source")
        val LYRICS_LANG = stringPreferencesKey("lyrics_lang")
        val DL_QUALITY_WIFI = stringPreferencesKey("dl_quality_wifi")
        val DL_QUALITY_MOBILE = stringPreferencesKey("dl_quality_mobile")
        val DL_PARALLEL = intPreferencesKey("dl_parallel")
        val DL_SPEED_LIMIT = intPreferencesKey("dl_speed_limit_kb")
        val DL_LOW_BATTERY = booleanPreferencesKey("dl_pause_low_battery")
        val DL_AUTO_RESUME = booleanPreferencesKey("dl_auto_resume")
        val DL_RETRIES = intPreferencesKey("dl_retries")
        val DL_STORAGE_LIMIT = intPreferencesKey("dl_storage_limit_mb")
        val DL_NOTIFICATIONS = booleanPreferencesKey("dl_notifications")
        val EXPORT_TREE = stringPreferencesKey("export_tree_uri")
        val AUTO_EXPORT = booleanPreferencesKey("auto_export")
        val EXPORT_WARNING = booleanPreferencesKey("export_warning_seen")
        val EXPORT_FOLDER_ASKED = booleanPreferencesKey("export_folder_asked")
        val AUTO_DL_PLAYLISTS = stringPreferencesKey("auto_download_playlists")
        val PLAYLIST_RECS = booleanPreferencesKey("playlist_recommendations")
        val DIAL_MIN_PLAYS = intPreferencesKey("dial_min_plays")
        val UI_STYLE = stringPreferencesKey("ui_style")
        val SMOOTH_GLASS = booleanPreferencesKey("smooth_glass")
        val STYLE_PICKED = booleanPreferencesKey("style_picked")
        val LYRICS_SIZE = stringPreferencesKey("lyrics_size")
        val LYRICS_DELAY = intPreferencesKey("lyrics_delay_ms")
        val LYRICS_ALL = booleanPreferencesKey("lyrics_all_sources")
        val VIZ_STYLE = stringPreferencesKey("visualizer_style")
        val VIZ_SENSITIVITY = floatPreferencesKey("visualizer_sensitivity")
        val VIZ_FPS = intPreferencesKey("visualizer_fps")
        val VIZ_BACKDROP = booleanPreferencesKey("visualizer_backdrop")
        val PLAYER_ART = stringPreferencesKey("player_art")
        val STAND_LYRICS = booleanPreferencesKey("stand_lyrics")
        val STAND_VIZ = booleanPreferencesKey("stand_visualizer")
        val STAND_BRIGHTNESS = floatPreferencesKey("stand_brightness")
        val STAND_MARQUEE = booleanPreferencesKey("stand_marquee")
        val STAND_FLIPPED = booleanPreferencesKey("stand_flipped")
        val STAND_VIEW = stringPreferencesKey("stand_view")
        val STAND_CHARGING = booleanPreferencesKey("stand_charging_only")
        val STAND_MAX_MIN = intPreferencesKey("stand_max_minutes")
        val STAND_LOW_BATTERY = booleanPreferencesKey("stand_low_battery_exit")
        val HL_WEIGHT = stringPreferencesKey("hl_line_weight")
        val HL_CORNERS = stringPreferencesKey("hl_corners")
        val HL_COVER = stringPreferencesKey("hl_cover_shape")
        val HL_LAYOUT = stringPreferencesKey("hl_player_layout")
        val HL_CAPS = stringPreferencesKey("hl_caps")
        val HL_DENSITY = stringPreferencesKey("hl_density")
        val HL_MOTION = stringPreferencesKey("hl_motion")
        val HL_HAPTIC = stringPreferencesKey("hl_haptic_level")
        val HL_ICONS = stringPreferencesKey("hl_icon_style")
        val HL_APP_ICON = stringPreferencesKey("hl_app_icon")
        val HL_SWIPE_DISMISS = booleanPreferencesKey("hl_swipe_dismiss")
        val HL_HINT_SEEN = booleanPreferencesKey("hl_gesture_hint_seen")
    }
}

/** Яркость подставки «как в системе» — окно яркость не переопределяет. */
const val STAND_BRIGHTNESS_SYSTEM = -1f
