package com.texfi.w0y.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Стекло, как у нижних панелей на телефоне («Easy Glass»).
 *
 * Экран рисуется в слой ([source]); панель поверх него берёт размытую копию
 * этого слоя ([blurred]) под своей формой, добавляет дымку и тонкий блик по
 * кромке — и то, что под панелью, видно сквозь неё мягким пятном. Слои
 * записываются заново только когда меняется сам экран, так что покой стоит
 * почти ничего.
 */
@Stable
class Backdrop(val source: GraphicsLayer, val blurred: GraphicsLayer) {
    /** Где в окне лежит записанный экран: по нему панель находит свой кусок. */
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    return remember(source, blurred) { Backdrop(source, blurred) }
}

/** То, что лежит под плавающими панелями; null — стекла нет, панели плотные. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Сколько снизу и слева закрыто плавающими панелями: списки докручиваются до места над ними. */
data class BarsInset(val bottom: Dp = 0.dp, val start: Dp = 0.dp)

val LocalBarsInset = staticCompositionLocalOf { BarsInset() }

/** Записывает содержимое в [Backdrop], не меняя того, как оно выглядит. */
fun Modifier.backdropSource(backdrop: Backdrop, blur: Dp = 22.dp): Modifier =
    this
        .onGloballyPositioned { backdrop.origin = it.positionInWindow() }
        .drawWithContent {
            val radius = blur.toPx()
            backdrop.blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            // Чуть живее цвета: размытие бледнит, а стекло должно оставаться цветным.
            backdrop.blurred.colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.45f) })
            backdrop.source.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.source)
            backdrop.blurred.record { drawLayer(backdrop.source) }
        }

private fun Color.a(alpha: Float) = copy(alpha = alpha)

/** Блик по кромке: ярче сверху слева и снизу справа, посередине почти пропадает. */
private fun rimBrush(width: Float, height: Float, strength: Float): Brush =
    Brush.linearGradient(
        0f to Color.White.a(0.85f * strength),
        0.38f to Color.White.a(0.10f * strength),
        0.62f to Color.White.a(0.10f * strength),
        1f to Color.White.a(0.60f * strength),
        start = Offset.Zero,
        end = Offset(width, height),
    )

/**
 * Панель-стекло с размытием под собой: нижняя панель плеера, боковое меню,
 * всплывающие окна. Без [backdrop] — просто плотная панель той же формы.
 */
@Composable
fun Modifier.glassBar(shape: Shape, backdrop: Backdrop?): Modifier {
    val light = C.isLight
    var pos by remember { mutableStateOf(Offset.Zero) }
    val rim = if (light) 1f else 0.85f
    return this
        .shadow(
            elevation = 16.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.a(if (light) 0.10f else 0.40f),
            spotColor = Color.Black.a(if (light) 0.12f else 0.45f),
        ).onGloballyPositioned { pos = it.positionInWindow() }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            if (backdrop != null) {
                val at = pos - backdrop.origin
                clipPath(path) { translate(-at.x, -at.y) { drawLayer(backdrop.blurred) } }
                clipPath(path) { drawRect(C.Haze.a(0.58f)) }
            } else {
                clipPath(path) { drawRect(C.Haze.a(0.94f)) }
            }
            drawOutline(outline, rimBrush(size.width, size.height, rim), style = Stroke(1.dp.toPx()))
        }
}

/**
 * Карточка-стекло внутри экрана: полупрозрачная заливка, мягкий отсвет сверху
 * и тонкий блик по кромке. Размытия нет — карточке под собой нечего размывать,
 * кроме самого экрана, в который она входит.
 */
@Composable
fun Modifier.glassCard(shape: Shape, tint: Color? = null): Modifier {
    val light = C.isLight
    val base = tint ?: C.Surface
    return this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        clipPath(path) {
            drawRect(base.a(if (light) 0.72f else 0.62f))
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.a(if (light) 0.30f else 0.06f), Color.Transparent),
                    endY = size.height * 0.5f,
                ),
            )
        }
        drawOutline(outline, rimBrush(size.width, size.height, if (light) 1f else 0.75f), style = Stroke(1.dp.toPx()))
    }
}

/** Плотная (матовая) подложка Smooth: та же форма, без бликов. */
fun Modifier.matteCard(shape: Shape, color: Color): Modifier =
    this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color)
    }

/** Подложка по оформлению: стекло или матовая; в Pixel не используется. */
@Composable
fun Modifier.smoothSurface(shape: Shape, tint: Color? = null): Modifier =
    if (Look.glass) glassCard(shape, tint) else matteCard(shape, tint ?: C.Surface)
