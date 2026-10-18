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

/** Стиль визуализатора: первые четыре — Pixel, последние два — Smooth. */
@Serializable
enum class VisualizerStyle(override val key: String, val smooth: Boolean) : Labeled {
    SEGMENTS("viz_segments", false),
    RING("viz_ring", false),
    SCOPE("viz_scope", false),
    MOSAIC("viz_mosaic", false),
    BARS("viz_bars", true),
    WAVE("viz_wave", true),
    ;

    /** Стиль под оформление: чужой заменяется ближайшим своим. */
    fun forUi(style: UiStyle): VisualizerStyle =
        if (style != UiStyle.PIXEL) {
            when (this) {
                SEGMENTS, MOSAIC -> BARS
                SCOPE, RING -> WAVE
                else -> this
            }
        } else {
            when (this) {
                BARS -> SEGMENTS
                WAVE -> SCOPE
                else -> this
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
    val normalizeVolume: Boolean = true,
    val skipSilence: Boolean = false,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val reverb: Reverb = Reverb.OFF,
    val eqEnabled: Boolean = false,
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
    val compactRows: Boolean = false,
    val dialMinPlays: Int = 1,
    // ---- устройства и система
    val audioDevice: String = "auto",
    val notifyTrackChange: Boolean = false,
    /** Свои сочетания клавиш: id действия → сочетания; чего нет — действует стандартное. */
    val keymap: Map<String, List<String>> = emptyMap(),
)
