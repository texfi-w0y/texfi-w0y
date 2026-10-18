package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Уровень анимаций. «Авто» — полные, пока компьютер не слабый (мало ядер или памяти): тогда лёгкие. */
@kotlinx.serialization.Serializable
enum class MotionMode(override val key: String) : Labeled {
    AUTO("hl_motion_auto"),
    FULL("hl_motion_full"),
    LITE("hl_motion_lite"),
    OFF("hl_motion_off"),
}

object Motion {
    var mode by mutableStateOf(MotionMode.AUTO)

    /** Слабый компьютер: два ядра и меньше либо меньше гигабайта кучи. */
    val weak: Boolean = Runtime.getRuntime().availableProcessors() <= 2 || Runtime.getRuntime().maxMemory() < (1L shl 30)

    val off: Boolean get() = mode == MotionMode.OFF
    val full: Boolean get() = mode == MotionMode.FULL || (mode == MotionMode.AUTO && !weak)

    /** Длительность перехода в мс: полная, треть или ноль. */
    fun ms(full: Int): Int = if (off) 0 else if (this.full) full else full / 3
}

/** Состояние оформления: меняется в настройках, всё нарисованное перерисовывается само. */
object ThemeState {
    var mode by mutableStateOf(ThemeMode.DARK)
    var accent by mutableStateOf(Accent.SAND)
    var custom by mutableStateOf(0xFFA06CFF.toInt())
    var style by mutableStateOf(UiStyle.PIXEL)
    var glass by mutableStateOf(true)

    // Вид Hairline (настройки «Вид»).
    var weight by mutableStateOf(LineWeight.THIN)
    var corners by mutableStateOf(CornerStyle.SHARP)
    var coverShape by mutableStateOf(CoverShape.ROUNDED)
    var layout by mutableStateOf(PlayerLayout.COVER)
    var caps by mutableStateOf(CapsMode.TITLES_LABELS)
    var density by mutableStateOf(Density.COMFORTABLE)
    var backdrop by mutableStateOf(BackdropMode.TOPO)
    var coverSize by mutableStateOf(CoverSize.MEDIUM)
    var coverRing by mutableStateOf(true)
    var coverSpin by mutableStateOf(false)
    var posterDim by mutableStateOf(45)
}

/**
 * Что отличает стили. Компоненты не спрашивают «какой стиль» у экранов —
 * форма, рамка и тень приходят отсюда, поэтому новый экран сразу рисуется в обоих.
 */
object Look {
    /** Hairline: линия 1px вместо теней, плоские карточки, один акцент. */
    val hair: Boolean get() = ThemeState.style == UiStyle.HAIRLINE

    /** Скруглённые формы: Smooth и Hairline (у Pixel квадратные). Особенности Hairline — через [hair]. */
    val smooth: Boolean get() = ThemeState.style != UiStyle.PIXEL

    /** Стекло: панели полупрозрачные и размывают то, что под ними. Только у Smooth. */
    val glass: Boolean get() = ThemeState.style == UiStyle.SMOOTH && ThemeState.glass

    /** Скругление карточек: Pixel почти квадрат, Smooth крупно, Hairline 10. */
    val card: Dp get() = if (hair) when (ThemeState.corners) { CornerStyle.SHARP -> 10.dp; CornerStyle.SOFT -> 14.dp; else -> 22.dp } else if (smooth) 22.dp else 6.dp
    val control: Dp get() = if (hair) ThemeState.corners.dp.dp else if (smooth) 50.dp else 4.dp
    val cover: Dp get() = if (hair) when (ThemeState.corners) { CornerStyle.SHARP -> 6.dp; CornerStyle.SOFT -> 8.dp; else -> 12.dp } else if (smooth) 12.dp else 4.dp
    val row: Dp get() = if (hair) when (ThemeState.corners) { CornerStyle.SHARP -> 8.dp; CornerStyle.SOFT -> 10.dp; else -> 14.dp } else if (smooth) 14.dp else 6.dp

    /** Толщина линии: у Hairline из настроек (1 / 1,5 / 2), у Pixel 2. */
    val line: Dp get() = if (hair) ThemeState.weight.dp.dp else 2.dp

    /** Плотные строки: общий выключатель или «Плотно» в Hairline. */
    val compact: Boolean get() = hair && ThemeState.density == Density.COMPACT

    /** Подписи разделов и кнопок: заглавными, если не выключено (только Hairline; у остальных всегда заглавные). */
    fun label(text: String): String = if (!hair || ThemeState.caps != CapsMode.OFF) text.uppercase() else text

    /** Заголовки экранов: заглавными только при «Заголовки и метки». */
    fun title(text: String): String = if (!hair || ThemeState.caps == CapsMode.TITLES_LABELS) text.uppercase() else text
}

/**
 * Токены TexFi: графитовый фон, акцент и вторичный — те же значения, что в
 * Android-теме. Свойства читаются при отрисовке, поэтому смена темы
 * подхватывается сразу.
 */
object C {
    private val light get() = ThemeState.mode == ThemeMode.LIGHT
    private val oled get() = ThemeState.mode == ThemeMode.OLED
    private val accent get() = ThemeState.accent

    val Blue: Color get() = if (accent == Accent.CUSTOM) Color(ThemeState.custom) else Color(accent.accent)
    val BlueDeep: Color get() = if (accent == Accent.CUSTOM) lerp(Color(ThemeState.custom), Color.Black, 0.45f) else Color(accent.deep)
    val BlueLight = Color(0xFF7FB5FF)
    val Sand: Color get() = if (light && accent == Accent.BLUE) Color(0xFFB98232) else Color(accent.secondary)
    val SandDeep = Color(0xFF8A5F26)
    private val hair get() = Look.hair
    val Background: Color get() = if (hair) (if (light) Color(0xFFF6F6F3) else if (oled) Color(0xFF000000) else Color(0xFF0A0A0C)) else if (light) Color(0xFFF7F1E4) else if (oled) Color(0xFF000000) else Color(0xFF15151B)
    val Surface: Color get() = if (hair) (if (light) Color(0xFFFFFFFF) else if (oled) Color(0xFF0A0A0C) else Color(0xFF111114)) else if (light) Color(0xFFFFFDF7) else if (oled) Color(0xFF000000) else Color(0xFF1F1F27)
    val SurfaceHigh: Color get() = if (hair) (if (light) Color(0xFFEFEFEB) else if (oled) Color(0xFF111114) else Color(0xFF18181C)) else if (light) Color(0xFFEDE4D2) else if (oled) Color(0xFF101014) else Color(0xFF2A2A34)
    val Border: Color get() = if (hair) (if (light) Color(0xFFDCDCD6) else Color(0xFF26262C)) else if (light) Color(0xFFD5C7AC) else if (oled) Color(0xFF2C2C36) else Color(0xFF383845)
    val Shadow: Color get() = if (light) Color(0xFFC9B99B) else if (oled) Color(0xFF17171D) else Color(0xFF0A0A0E)
    val Text: Color get() = if (hair) (if (light) Color(0xFF0C0C0F) else Color(0xFFF4F4F6)) else if (light) Color(0xFF191921) else Color(0xFFF6F1E5)
    val Muted: Color get() = if (hair) (if (light) Color(0xFF55555E) else Color(0xFFA3A3AD)) else if (light) Color(0xFF6B6455) else Color(0xFF8A8A96)
    val Danger = Color(0xFFFF5A5A)

    /** Текст на заливке акцентом — светлый или тёмный по контрасту, как на телефоне. */
    val OnAccent: Color
        get() {
            val bg = Blue
            val luminance = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
            return if (luminance > 0.55f) Color(0xFF111116) else Color(0xFFF6F1E5)
        }

    /** Фиолетовое пятно стеклянного фона, как на телефоне. */
    val GlassViolet = Color(0xFF8A5CF6)

    /** Серое стекло групп настроек: светлее фона, как в iOS. */
    val GlassGroup: Color get() = if (light) Surface else Color(0xFF2E2E33)

    /** Дымка стеклянных панелей: тёмная или белая по теме. */
    val Haze: Color get() = if (light) Color.White else Color(0xFF0E0E11)

    val isLight: Boolean get() = light
}

val PixelFont = FontFamily(Font(resource = "press_start_2p.ttf"))

/** Шрифты Hairline: Unbounded — заголовки и крупные числа, JetBrains Mono — подписи; текст читают системным. */
val UnboundedFont = FontFamily(
    Font(resource = "unbounded_semibold.ttf", weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    Font(resource = "unbounded_extrabold.ttf", weight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
)
val MonoFont = FontFamily(Font(resource = "jetbrains_mono_medium.ttf"))
