package com.texfi.w0y.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R
import com.texfi.w0y.data.CapsMode
import com.texfi.w0y.data.CornerStyle
import com.texfi.w0y.data.CoverShape
import com.texfi.w0y.data.Density
import com.texfi.w0y.data.IconStyle
import com.texfi.w0y.data.LineWeight
import com.texfi.w0y.data.PlayerLayout
import com.texfi.w0y.data.UiStyle

/**
 * Hairline — тонкие линии, чистая геометрия, один герой на экран.
 *
 * Здесь всё, чем Hairline отличается: токены движения, шрифты, подпись
 * регистра и «вид» (толщина линии, скругления, форма обложки…), который
 * пользователь меняет в настройках. Компоненты берут вид из [LocalHairLook]
 * и не знают, откуда он пришёл.
 */
object HairTokens {
    /** Короткая: нажатие, переключатель. */
    const val SHORT_MS = 120

    /** Средняя: появление, смена вкладки. */
    const val MID_MS = 220

    /** Длинная: смена трека, раскрытие плеера. */
    const val LONG_MS = 360

    /** Шаг «лесенки» и её предел: больше шести строк разом уже шум. */
    const val STAGGER_MS = 20L
    const val STAGGER_MAX = 6

    /** Единственная кривая Hairline: быстрый старт, мягкая посадка. */
    val EaseOut: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Единственная пружина: плеер, тянущийся за пальцем, и возврат при недоборе. */
    const val SPRING_DAMPING = 0.78f
    const val SPRING_STIFFNESS = 420f

    /** Линия тоньше 1 dp на плотных экранах пропадает. */
    val MIN_LINE: Dp = 1.dp

    /** Диск плеера: доля ширины экрана. */
    const val DISC_SHARE = 0.78f

    /** Скруглённый квадрат обложки: чуть-чуть — доля стороны, но в пределах 3…10 dp. */
    const val COVER_ROUND_SHARE = 0.07f
    val COVER_ROUND_MIN: Dp = 3.dp
    val COVER_ROUND_MAX: Dp = 10.dp

    /** Минимальная цель касания. */
    val TOUCH: Dp = 48.dp
}

/** Что делать с анимациями: всё, только fade/сдвиг, ничего. */
enum class MotionLevel {
    FULL,
    LITE,
    OFF,
    ;

    /** Рисующие анимации — draw-on, лесенки, морфы. */
    val draws: Boolean get() = this == FULL

    /** Хоть какое-то движение. */
    val moves: Boolean get() = this != OFF
}

/**
 * Вид Hairline одним неизменяемым значением. Равенство по значению: смена
 * одного поля перекомпонует только тех, кто его читает.
 */
@Immutable
data class HairlineLook(
    val weight: LineWeight = LineWeight.THIN,
    val corners: CornerStyle = CornerStyle.SHARP,
    val coverShape: CoverShape = CoverShape.ROUNDED,
    val layout: PlayerLayout = PlayerLayout.RING,
    val caps: CapsMode = CapsMode.TITLES_LABELS,
    val density: Density = Density.COMFORTABLE,
    val motion: MotionLevel = MotionLevel.FULL,
    val iconStyle: IconStyle = IconStyle.LINE,
) {
    /** Толщина основной линии. */
    val line: Dp get() = weight.dp.dp.coerceAtLeast(HairTokens.MIN_LINE)

    /** Скругление карточек: «круг» для карточки — это крупный радиус, а не капсула. */
    val cardRadius: Dp get() = if (corners == CornerStyle.CIRCLE) 24.dp else corners.dp.dp

    /** Скругление кнопок, чипов и обложек: здесь «круг» — настоящий круг. */
    val pillRadius: Dp get() = if (corners == CornerStyle.CIRCLE) 999.dp else corners.dp.dp

    val compact: Boolean get() = density == Density.COMPACT

    /** Вертикальный шаг списков. */
    val rowPad: Dp get() = if (compact) 6.dp else 10.dp
}

val LocalHairLook = staticCompositionLocalOf { HairlineLook() }

val hairLook: HairlineLook
    @Composable @ReadOnlyComposable
    get() = LocalHairLook.current

/** Включён ли Hairline. Компоненты проверяют это раньше, чем «плавный/пиксельный». */
val isHair: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalStyleTokens.current.style == UiStyle.HAIRLINE

// ── Шрифты ───────────────────────────────────────────────────────────────

/** Широкий жирный гротеск — заголовки и крупные числа. Только они: абзацы им не набирают. */
val UnboundedFamily =
    FontFamily(
        Font(R.font.unbounded_semibold, FontWeight.SemiBold),
        Font(R.font.unbounded_extrabold, FontWeight.ExtraBold),
    )

/** Моно — мелкие метки заглавными с разрядкой, всегда от 11 sp и весом не меньше Medium. */
val MonoFamily = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))

internal val HairScreenTitleStyle =
    TextStyle(
        fontFamily = UnboundedFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp,
    )

internal val HairTitleStyle =
    TextStyle(
        fontFamily = UnboundedFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    )

internal val HairLabelStyle =
    TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.4.sp,
    )

internal val HairBigNumberStyle =
    TextStyle(
        fontFamily = UnboundedFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
    )

/** Крупное число героя блока (счётчики «Моё»). */
val hairBigNumber: TextStyle get() = HairBigNumberStyle

// ── Регистр ──────────────────────────────────────────────────────────────

/** Строка, набранная целиком заглавными: в строках приложения так записаны старые метки. */
private fun isShouting(text: String): Boolean = text.any { it.isLetter() } && text == text.uppercase()

/** Обычный регистр: первая буква заглавная; «КРИЧАЩАЯ» строка опускается целиком. */
private fun sentence(text: String): String {
    val base = if (isShouting(text)) text.lowercase() else text
    return base.replaceFirstChar { it.uppercase() }
}

/** Регистр метки: заглавные, пока не выключены совсем. Названия треков сюда не попадают. */
fun hairLabelCase(text: String, caps: CapsMode): String = if (caps == CapsMode.OFF) sentence(text) else text.uppercase()

/** Регистр заголовка экрана и ключевого слова. */
fun hairTitleCase(text: String, caps: CapsMode): String = if (caps == CapsMode.TITLES_LABELS) text.uppercase() else sentence(text)

/** Радиус скруглённого квадрата обложки в пикселях для стороны [sidePx]. */
fun coverRadiusPx(sidePx: Float, density: Float): Float =
    (sidePx * HairTokens.COVER_ROUND_SHARE).coerceIn(HairTokens.COVER_ROUND_MIN.value * density, HairTokens.COVER_ROUND_MAX.value * density)

/** Скруглённый квадрат: мелкая обложка почти острая, крупная — чуть мягче, но не «пузырь». */
object CoverRoundedShape : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val r = coverRadiusPx(minOf(size.width, size.height), density.density)
        return androidx.compose.ui.graphics.Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size), androidx.compose.ui.geometry.CornerRadius(r)),
        )
    }
}

/** Форма обложки по настройке: диск, скруглённый квадрат или квадрат. */
fun hairCoverShape(look: HairlineLook): androidx.compose.ui.graphics.Shape =
    when (look.coverShape) {
        CoverShape.DISC -> androidx.compose.foundation.shape.CircleShape
        CoverShape.ROUNDED -> CoverRoundedShape
        CoverShape.SQUARE -> androidx.compose.ui.graphics.RectangleShape
    }

/** Метка: заглавные в Hairline, как есть в остальных стилях. */
@Composable
@ReadOnlyComposable
fun String.asLabel(): String = if (isHair) hairLabelCase(this, hairLook.caps) else this

/** Заголовок: заглавные в Hairline, как есть в остальных стилях. */
@Composable
@ReadOnlyComposable
fun String.asTitle(): String = if (isHair) hairTitleCase(this, hairLook.caps) else this
