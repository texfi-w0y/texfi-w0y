package com.texfi.w0y.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.UiStyle

/**
 * Всё, чем стили отличаются друг от друга, — в одном месте.
 *
 * Экраны не знают, какой стиль включён: они берут карточку, кнопку,
 * переключатель и иконку по имени, а форма, рамка, тень и движение
 * приходят отсюда. Поэтому второй стиль не дублирует ни одного экрана, и
 * новый экран сразу рисуется в обоих.
 */
@Immutable
data class StyleTokens(
    val style: UiStyle,
    /** Карточки, панели, плитки. */
    val card: Shape,
    /** Толщина рамки карточки; 0 — без рамки. */
    val cardBorder: Dp,
    /** Смещённая тень без размытия; 0 — тени нет. */
    val cardShadow: Dp,
    val button: Shape,
    val buttonBorder: Dp,
    val buttonShadow: Dp,
    /** Мелкие элементы: чипы, сегменты, бейджи. */
    val chip: Shape,
    /** Обложки в строках и плитках. */
    val cover: Shape,
    /** Нижние панели и диалоги. */
    val sheet: Shape,
    /** Плавающая навигация и мини-плеер. */
    val bar: Shape,
    val switchTrack: Shape,
    val switchThumb: Shape,
    val sliderThumb: Shape,
    /** Насколько уменьшается нажатый элемент вместо вдавливания в тень. */
    val pressScale: Float,
    /** Ступенчатое движение (Pixel) или плавное (Smooth). */
    val stepped: Boolean,
    /** Сегментный прогресс (Pixel) или сплошная полоса (Smooth). */
    val segmented: Boolean,
    /** Поверхности — жидкое стекло; иначе плотные матовые. Только Smooth. */
    val glass: Boolean = false,
)

val PixelTokens =
    StyleTokens(
        style = UiStyle.PIXEL,
        card = RoundedCornerShape(8.dp),
        cardBorder = 2.dp,
        cardShadow = 4.dp,
        button = RoundedCornerShape(8.dp),
        buttonBorder = 2.dp,
        buttonShadow = 4.dp,
        chip = RectangleShape,
        cover = RoundedCornerShape(4.dp),
        sheet = RectangleShape,
        bar = RectangleShape,
        switchTrack = RectangleShape,
        switchThumb = RectangleShape,
        sliderThumb = RectangleShape,
        pressScale = 1f,
        stepped = true,
        segmented = true,
    )

val SmoothTokens =
    StyleTokens(
        style = UiStyle.SMOOTH,
        card = RoundedCornerShape(22.dp),
        cardBorder = 0.dp,
        cardShadow = 0.dp,
        button = RoundedCornerShape(50),
        buttonBorder = 0.dp,
        buttonShadow = 0.dp,
        chip = RoundedCornerShape(50),
        cover = RoundedCornerShape(12.dp),
        sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        bar = RoundedCornerShape(28.dp),
        switchTrack = RoundedCornerShape(50),
        switchThumb = CircleShape,
        sliderThumb = CircleShape,
        pressScale = 0.96f,
        stepped = false,
        segmented = false,
    )

/**
 * Токены Hairline: форма и толщина линии приходят из настроек вида.
 * Тень нулевая, заливка редкая — карточку держит только контур.
 */
fun hairlineTokens(look: HairlineLook): StyleTokens {
    val card = RoundedCornerShape(look.cardRadius)
    val pill = RoundedCornerShape(look.pillRadius)
    return StyleTokens(
        style = UiStyle.HAIRLINE,
        card = card,
        cardBorder = look.line,
        cardShadow = 0.dp,
        button = pill,
        buttonBorder = look.line,
        buttonShadow = 0.dp,
        chip = pill,
        cover = if (look.coverShape == com.texfi.w0y.data.CoverShape.DISC) CircleShape else RoundedCornerShape(minOf(look.pillRadius, 16.dp)),
        sheet = RoundedCornerShape(topStart = look.cardRadius, topEnd = look.cardRadius),
        bar = card,
        switchTrack = RoundedCornerShape(50),
        switchThumb = CircleShape,
        sliderThumb = CircleShape,
        pressScale = 0.96f,
        stepped = false,
        segmented = false,
    )
}

/** Токены стиля; ветка на каждый стиль обязательна — компилятор не даст забыть новый. */
fun tokensFor(style: UiStyle, glass: Boolean = true, look: HairlineLook = HairlineLook()): StyleTokens =
    when (style) {
        UiStyle.HAIRLINE -> hairlineTokens(look)
        UiStyle.PIXEL -> PixelTokens
        UiStyle.SMOOTH -> if (glass) SmoothGlassTokens else SmoothTokens
    }

private val SmoothGlassTokens = SmoothTokens.copy(glass = true)

val LocalStyleTokens = staticCompositionLocalOf { PixelTokens }

/** Короткий доступ из компонентов. */
val styleTokens: StyleTokens
    @Composable @ReadOnlyComposable
    get() = LocalStyleTokens.current

/**
 * «Не пиксельный»: Smooth и Hairline. Всё, что рисуется без спрайтов, ступеней
 * и смещённых теней, берёт эту ветку; чем Hairline отличается от Smooth
 * дальше, решает [isHair].
 */
val isSmooth: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalStyleTokens.current.style != UiStyle.PIXEL

/**
 * Рамка элемента по стилю: в Pixel — сплошная 2 dp, в Smooth — никакой
 * (элемент отделяется тоном). Для мест, которые рисуют подложку сами, а не
 * через карточку.
 */
@Composable
fun androidx.compose.ui.Modifier.styledBorder(
    corner: Int,
    color: androidx.compose.ui.graphics.Color = LocalW0yColors.current.border,
): androidx.compose.ui.Modifier =
    if (isSmooth) this else border(2.dp, color, if (corner == 0) RectangleShape else RoundedCornerShape(corner.dp))

/** Скругление по стилю: в Smooth углы крупнее — в той же пропорции, что у карточек. */
@Composable
fun androidx.compose.ui.Modifier.styledClip(corner: Int): androidx.compose.ui.Modifier =
    clip(RoundedCornerShape((if (isSmooth) minOf(corner * 5 / 2 + 6, 24) else corner).dp))
