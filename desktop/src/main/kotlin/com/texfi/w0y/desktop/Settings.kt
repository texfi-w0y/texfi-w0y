package com.texfi.w0y.desktop

import com.texfi.w0y.data.Reverb
import com.texfi.w0y.desktop.i18n.S
import kotlinx.serialization.Serializable

/*
 * Настройки десктопной версии. Названия, значения по умолчанию и тексты
 * совпадают с телефонными: подписи берутся из тех же строковых ресурсов
 * (ключ хранится в самом значении, перевод — через S). Нет только тех
 * настроек, что про телефон: вибрация, наушники, мобильная сеть, шторка.
 */

/** Значение настройки, у которого есть подпись в строках. */
interface Labeled {
    val key: String
    val label: String get() = S(key)
}

/** Качество звука: с чем идти в извлечение потока. */
@Serializable
enum class Quality(override val key: String) : Labeled {
    LOW("quality_low"),
    MEDIUM("quality_auto"),
    HIGH("quality_high"),
}

/** Что играет после выбранного трека — то же, что на телефоне. */
@Serializable
enum class QueueMode(override val key: String, val hintKey: String) : Labeled {
    ORDER("queue_mode_order", "queue_mode_order_hint"),
    SHUFFLE("queue_mode_shuffle", "queue_mode_shuffle_hint"),
    RADIO("queue_mode_radio", "queue_mode_radio_hint"),
    ;

    val hint: String get() = S(hintKey)
}

@Serializable
enum class ExplicitFallback(override val key: String) : Labeled {
    PLAY("explicit_play"),
    SKIP("explicit_skip"),
}

@Serializable
enum class StartTab(override val key: String) : Labeled {
    HOME("start_tab_home"),
    SEARCH("start_tab_search"),
    LIBRARY("start_tab_library"),
    LAST("start_tab_last"),
}

@Serializable
enum class Language(override val key: String, val tag: String?) : Labeled {
    SYSTEM("language_system", null),
    EN("language_en", "en"),
    RU("language_ru", "ru"),
    UK("language_uk", "uk"),
    PL("language_pl", "pl"),
}

@Serializable
enum class ThemeMode(override val key: String) : Labeled {
    DARK("theme_dark"),
    OLED("theme_oled"),
    LIGHT("theme_light"),
}

/** Пара «акцент + вторичный», те же значения, что на телефоне. */
@Serializable
enum class Accent(override val key: String, val accent: Long, val deep: Long, val secondary: Long) : Labeled {
    BLUE("accent_blue", 0xFF4A7CFB, 0xFF1E3F8F, 0xFFE0A860),
    PINK("accent_pink", 0xFFFB4A8D, 0xFF8F1E4C, 0xFFFFB2CF),
    VIOLET("accent_violet", 0xFF9A6BFF, 0xFF4C2E99, 0xFFE0A860),
    MINT("accent_mint", 0xFF3ED9A4, 0xFF167A5B, 0xFFE0A860),
    CRIMSON("accent_crimson", 0xFFFF5A5A, 0xFF8F2020, 0xFFE0A860),
    SAND("accent_sand", 0xFFE0A860, 0xFF8A5F26, 0xFF7FB5FF),
    LIGHT_BLUE("accent_light_blue", 0xFF7FB5FF, 0xFF2F5C94, 0xFFE0A860),
    WARM("accent_warm", 0xFFF0B49A, 0xFF8A4A32, 0xFF7FB5FF),
    CUSTOM("accent_custom", 0xFFA06CFF, 0xFF4E3580, 0xFFE0A860),
}

/** Оформление: Pixel — родной язык TexFi, Smooth — скругления и стекло. */
@Serializable
enum class UiStyle(override val key: String, val hintKey: String) : Labeled {
    HAIRLINE("style_hairline", "style_hairline_hint"),
    PIXEL("style_pixel", "style_pixel_hint"),
    SMOOTH("style_smooth", "style_smooth_hint"),
    ;

    val hint: String get() = S(hintKey)
}

@Serializable
enum class LyricsSize(override val key: String, val sp: Int) : Labeled {
    SMALL("lyrics_size_small", 16),
    MEDIUM("lyrics_size_medium", 20),
    LARGE("lyrics_size_large", 26),
}

/** Стиль визуализатора: первые четыре — Pixel, следующие два — Smooth, последние три — Hairline. */
@Serializable
enum class VisualizerStyle(override val key: String, val smooth: Boolean, val hair: Boolean = false) : Labeled {
    SEGMENTS("viz_segments", false),
    RING("viz_ring", false),
    SCOPE("viz_scope", false),
    MOSAIC("viz_mosaic", false),
    BARS("viz_bars", true),
    WAVE("viz_wave", true),
    LINES("viz_lines", false, hair = true),
    ORBIT("viz_orbit", false, hair = true),
    TERRAIN("viz_terrain", false, hair = true),
    ;

    /** Стиль под оформление: чужой заменяется ближайшим своим (те же правила, что на телефоне). */
    fun forUi(style: UiStyle): VisualizerStyle =
        when (style) {
            UiStyle.PIXEL ->
                when (this) {
                    BARS, LINES, TERRAIN -> SEGMENTS
                    WAVE, ORBIT -> SCOPE
                    else -> this
                }
            UiStyle.SMOOTH ->
                when (this) {
                    SEGMENTS, MOSAIC, LINES -> BARS
                    SCOPE, RING, ORBIT, TERRAIN -> WAVE
                    else -> this
                }
            UiStyle.HAIRLINE ->
                when (this) {
                    SEGMENTS, MOSAIC, BARS -> LINES
                    SCOPE, RING, WAVE -> ORBIT
                    else -> this
                }
        }

    companion object {
        /** Стили, доступные в оформлении. */
        fun of(style: UiStyle): List<VisualizerStyle> =
            entries.filter {
                when (style) {
                    UiStyle.HAIRLINE -> it.hair
                    UiStyle.SMOOTH -> it.smooth && !it.hair
                    UiStyle.PIXEL -> !it.smooth && !it.hair
                }
            }
    }
}

/** Что занимает место обложки в плеере. */
@Serializable
enum class PlayerArt { COVER, VISUALIZER }

/** Что крупно в режиме подставки: название с исполнителем или обложка. */
@Serializable
enum class StandView(override val key: String) : Labeled {
    TEXT("stand_view_text"),
    COVER("stand_view_cover"),
}

/** Толщина линии Hairline, dp. */
@Serializable
enum class LineWeight(override val key: String, val dp: Float) : Labeled {
    THIN("hl_weight_thin", 1f),
    REGULAR("hl_weight_regular", 1.5f),
    BOLD("hl_weight_bold", 2f),
}

/** Скругление карточек и кнопок Hairline, dp. */
@Serializable
enum class CornerStyle(override val key: String, val dp: Int) : Labeled {
    SHARP("hl_corner_sharp", 3),
    SOFT("hl_corner_soft", 8),
    ROUND("hl_corner_round", 16),
    CIRCLE("hl_corner_circle", 999),
}

/** Форма обложки в плеере. */
@Serializable
enum class CoverShape(override val key: String) : Labeled {
    DISC("hl_cover_disc"),
    ROUNDED("hl_cover_rounded"),
    SQUARE("hl_cover_square"),
}

/** Раскладка большого плеера: обложка в рамке и панель сбоку, только обложка, постер с размытым фоном. */
@Serializable
enum class PlayerLayout(override val key: String) : Labeled {
    COVER("hl_layout_cover"),
    MINIMAL("hl_layout_minimal"),
    POSTER("hl_layout_poster"),
}

/** Размер диска в плеере. */
@Serializable
enum class CoverSize(override val key: String, val share: Float) : Labeled {
    SMALL("lyrics_size_small", 0.32f),
    MEDIUM("lyrics_size_medium", 0.40f),
    LARGE("lyrics_size_large", 0.50f),
}

/** Значок приложения: пять вариантов, как на телефоне. */
@Serializable
enum class AppIcon(override val key: String, val file: String) : Labeled {
    W("hl_icon_w", "w"),
    LEAF("hl_icon_leaf", "leaf"),
    BARS("hl_icon_bars", "bars"),
    RINGS("hl_icon_rings", "rings"),
    LEAF_LINE("hl_icon_leaf_line", "leafline"),
}

/** Фон за большим плеером. */
@Serializable
enum class PlayerBack(override val key: String) : Labeled {
    GLOW("d_pback_glow"),
    BLUR("d_pback_blur"),
    AURORA("d_pback_aurora"),
    APP("d_pback_app"),
    PURE("d_pback_pure"),
}

/** Какой текст пишется заглавными. */
@Serializable
enum class CapsMode(override val key: String) : Labeled {
    TITLES_LABELS("hl_caps_titles"),
    LABELS("hl_caps_labels"),
    OFF("hl_caps_off"),
}

@Serializable
enum class Density(override val key: String) : Labeled {
    COMFORTABLE("hl_density_comfortable"),
    COMPACT("hl_density_compact"),
}

/** Что делать, когда очередь кончилась. */
@Serializable
enum class RecEndMode(override val key: String) : Labeled {
    CONTINUE("rec_end_continue"),
    REPEAT("rec_end_repeat"),
    STOP("rec_end_stop"),
}

/** Фон приложения в Hairline. */
@Serializable
enum class BackdropMode(override val key: String) : Labeled {
    TOPO("backdrop_topo"),
    GRID("backdrop_grid"),
    PURE("backdrop_pure"),
    COVER("backdrop_cover"),
    AURORA("backdrop_aurora"),
}

/** Повтор: выключен, весь список, один трек. */
@Serializable
enum class RepeatMode { OFF, ALL, ONE }

/**
 * Настройки десктопной версии. Новые поля всегда со значением по умолчанию:
 * старый файл библиотеки читается без потерь.
 */
@Serializable
data class DSettings(
    // ---- звук
    val quality: Quality = Quality.HIGH,
    val normalizeVolume: Boolean = false,
    val skipSilence: Boolean = false,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val reverb: Reverb = Reverb.OFF,
    val eqEnabled: Boolean = true,
    /** Десять полос, дБ ×1: 31 Гц … 16 кГц. */
    val eqBands: List<Int> = List(10) { 0 },
    // ---- плеер
    val queueMode: QueueMode = QueueMode.ORDER,
    val seekStepSec: Int = 10,
    val playerCoverGlow: Boolean = true,
    val showLyrics: Boolean = true,
    val sleepTimerDefaultMin: Int = 30,
    val lyricsSize: LyricsSize = LyricsSize.MEDIUM,
    val lyricsLang: String = "",
    val playerArt: PlayerArt = PlayerArt.COVER,
    val visualizerStyle: VisualizerStyle = VisualizerStyle.SEGMENTS,
    val visualizerSensitivity: Float = 1f,
    val visualizerFps: Int = 60,
    val visualizerCoverBackdrop: Boolean = true,
    val lyricsDelayMs: Int = 0,
    val lyricsAllSources: Boolean = true,
    val recEndMode: RecEndMode = RecEndMode.CONTINUE,
    val recInsteadOfList: Boolean = false,
    val deleteProtection: Boolean = true,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val showPlaylistRecommendations: Boolean = false,
    // ---- подставка («всегда включён»)
    val standLyrics: Boolean = false,
    val standVisualizer: Boolean = false,
    val standMarquee: Boolean = false,
    val standFlipped: Boolean = false,
    val standView: StandView = StandView.TEXT,
    val standMaxMinutes: Int = 60,
    val standKeepAwake: Boolean = true,
    // ---- скорость и память
    val preloadNext: Boolean = true,
    val autoDownloadLiked: Boolean = false,
    val downloadQuality: Quality = Quality.HIGH,
    val downloadParallel: Int = 2,
    val downloadSpeedLimitKb: Int = 0,
    val downloadRetries: Int = 3,
    val downloadStorageLimitMb: Int = 0,
    val downloadNotifications: Boolean = true,
    val downloadAsMp3: Boolean = false,
    val downloadFolder: String = "",
    val autoDownloadPlaylists: Set<String> = emptySet(),
    // ---- поиск и аккаунт
    val searchSuggestions: Boolean = true,
    val saveSearchHistory: Boolean = true,
    val syncPlaylists: Boolean = true,
    val keepHistory: Boolean = true,
    // ---- без мата
    val cleanMode: Boolean = false,
    val explicitFallback: ExplicitFallback = ExplicitFallback.PLAY,
    val hideExplicit: Boolean = false,
    val muteSwearLines: Boolean = false,
    // ---- вид
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Accent = Accent.SAND,
    val customAccent: Int = 0xFFA06CFF.toInt(),
    val uiStyle: UiStyle = UiStyle.PIXEL,
    val smoothGlass: Boolean = true,
    val stylePicked: Boolean = false,
    val welcomeSeen: Boolean = false,
    val language: Language = Language.SYSTEM,
    val startTab: StartTab = StartTab.LAST,
    val lastTab: StartTab = StartTab.HOME,
    val showRecommendations: Boolean = true,
    val animatedBackground: Boolean = true,
    /** Уровень анимаций: «Авто» на слабом компьютере сам берёт лёгкие. */
    val motion: MotionMode = MotionMode.AUTO,
    val compactRows: Boolean = false,
    val dialMinPlays: Int = 1,
    /** Учитывать мой вкус: подборки по прослушиваниям и лайкам. */
    val tasteLearning: Boolean = true,
    /** Знакомое ↔ новое в подборках, % нового. */
    val recNovelty: Int = 30,
    /** Исполнители (нижний регистр), которых не предлагать. */
    val tasteExcluded: Set<String> = emptySet(),
    val weeklyDiscovery: Boolean = false,
    val lastWeekly: Long = 0L,
    /** Подборки на главной: быстрый выбор, забытые любимые, под время суток. */
    val homeRecBlocks: Boolean = true,
    // ---- вид Hairline
    val hlWeight: LineWeight = LineWeight.THIN,
    val hlCorners: CornerStyle = CornerStyle.SHARP,
    val hlCoverShape: CoverShape = CoverShape.ROUNDED,
    val hlLayout: PlayerLayout = PlayerLayout.COVER,
    val hlCaps: CapsMode = CapsMode.TITLES_LABELS,
    val hlDensity: Density = Density.COMFORTABLE,
    val backdrop: BackdropMode = BackdropMode.TOPO,
    val appIcon: AppIcon = AppIcon.W,
    val hlPlayerBack: PlayerBack = PlayerBack.GLOW,
    val hlCoverSize: CoverSize = CoverSize.MEDIUM,
    val hlCoverRing: Boolean = true,
    val hlCoverSpin: Boolean = false,
    /** Затемнение обложки за «Постером», %. */
    val hlPosterDim: Int = 45,
    // ---- устройства и система
    val audioDevice: String = "auto",
    val notifyTrackChange: Boolean = false,
    // ---- окно и система (ПК)
    /** Масштаб интерфейса: 0.8–1.6. */
    val uiScale: Float = 1f,
    /** Крестик окна убирает приложение в трей, а не закрывает. */
    val closeToTray: Boolean = false,
    val autostart: Boolean = false,
    // ---- обновления
    val checkUpdates: Boolean = true,
    /** Двойной клик по треку включает его, одиночный выделяет строку. */
    val doubleClickPlay: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    /** Релиз, для которого нажали «Позже»: плашка про него больше не всплывает. */
    val skippedUpdate: String = "",
    /** Свои сочетания клавиш: id действия → сочетания; чего нет — действует стандартное. */
    val keymap: Map<String, List<String>> = emptyMap(),
)
