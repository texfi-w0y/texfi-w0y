package com.texfi.w0y.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/**
 * Край экрана Hairline New: то, что уезжает под строку состояния или под нижний остров, не
 * обрывается и не гаснет в чёрное, а уходит в размытие, которое нарастает к краю.
 *
 * Настоящего размытия с переменной силой в библиотеке нет, поэтому полоса — два слоя одной записи
 * экрана: слабое размытие на всю ширину полосы и сильное у самого края, каждый со своей маской.
 * Вместе они дают переход «резко → мягко → совсем размыто» без видимой границы. [solid] — сколько
 * от края закрыто полностью (строка состояния, жестовая планка), дальше на [fade] размытие тает.
 *
 * До Android 12 размытия нет: остаётся прежнее затемнение в цвет фона.
 */
@Composable
fun EdgeBlur(
    backdrop: Backdrop,
    top: Boolean,
    solid: Dp,
    fade: Dp,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
) {
    val background = LocalW0yColors.current.background
    val shown = Modifier.graphicsLayer { this.alpha = alpha() }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Box(
            modifier.then(shown).drawWithContent {
                drawRect(edgeMask(top, solid.toPx(), fade.toPx(), size.height, background.copy(alpha = 0.92f)))
            },
        )
        return
    }
    Box(modifier.then(shown)) {
        EdgeLayer(backdrop, top, SOFT_BLUR, solid, fade, background.copy(alpha = if (top) 0.22f else 0.12f))
        EdgeLayer(backdrop, top, HARD_BLUR, solid * 0.6f, fade * 0.45f, background.copy(alpha = if (top) 0.3f else 0.2f))
    }
}

@Composable
private fun BoxScope.EdgeLayer(backdrop: Backdrop, top: Boolean, radius: Dp, solid: Dp, fade: Dp, haze: Color) {
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(edgeMask(top, solid.toPx(), fade.toPx(), size.height, Color.Black), blendMode = BlendMode.DstIn)
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(radius.toPx()) },
                highlight = null,
                shadow = null,
                onDrawSurface = { drawRect(haze) },
            ),
    )
}

/** Маска полосы: [color] у края на [solid], дальше на [fade] плавно (smoothstep) уходит в прозрачное. */
private fun edgeMask(top: Boolean, solid: Float, fade: Float, height: Float, color: Color): Brush {
    val stops = ArrayList<Pair<Float, Color>>(EASE_STEPS + 2)
    for (i in 0..EASE_STEPS) {
        val k = i.toFloat() / EASE_STEPS
        val eased = k * k * (3f - 2f * k)
        val y = ((solid + fade * k) / height).coerceIn(0f, 1f)
        stops += (if (top) y else 1f - y) to color.copy(alpha = color.alpha * (1f - eased))
    }
    stops += (if (top) 0f else 1f) to color
    stops.sortBy { it.first }
    return Brush.verticalGradient(*stops.toTypedArray(), startY = 0f, endY = height)
}

/**
 * Насколько список ушёл вверх от начала — по прокрутке, которая проходит через экран. Пока
 * экран в начале, верхнее размытие не нужно: оно легло бы на заголовок.
 *
 * Сдвиг копится по прокрученному; если список не смог уехать вниз (упёрся в начало), он точно
 * наверху — счёт сбрасывается. Ошибиться счёт может только в сторону «наверху», а это всего
 * лишь отсутствие размытия, а не размытый заголовок.
 */
@Stable
class TopScrollTracker : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        offset = if (available.y > 0.5f) 0f else (offset + consumed.y).coerceAtMost(0f)
        return Offset.Zero
    }

    /** Доля появления верхнего размытия: полностью — после [reach] прокрутки. */
    fun shown(reach: Float): Float = (-offset / reach).coerceIn(0f, 1f)
}

private val SOFT_BLUR = 8.dp
private val HARD_BLUR = 22.dp
private const val EASE_STEPS = 8
