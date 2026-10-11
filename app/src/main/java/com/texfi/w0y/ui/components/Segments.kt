package com.texfi.w0y.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import com.texfi.w0y.ui.theme.styleTokens

/**
 * Сегментированный индикатор: квадратики зажигаются по одному, как в сцене
 * ролика с частотой экрана, а не гладкая полоса.
 *
 * Прогресс приходит лямбдой и читается в фазе отрисовки: полоса двигается,
 * а экран вокруг неё не пересобирается.
 */
@Composable
fun SegmentedBar(
    progress: () -> Float,
    lit: Color,
    dim: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    cell: Dp = 8.dp,
    gap: Dp = 2.dp,
) {
    if (com.texfi.w0y.ui.theme.isHair) {
        HairProgressLine(progress, modifier, height)
        return
    }
    if (!styleTokens.segmented) {
        // Плавный стиль: сплошная скруглённая полоса вместо клеток.
        Canvas(modifier.height(height)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(dim, cornerRadius = r)
            val w = size.width * progress().coerceIn(0f, 1f)
            if (w > 0f) drawRoundRect(lit, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
        }
        return
    }
    Canvas(modifier.height(height)) {
        val cellPx = cell.toPx()
        val gapPx = gap.toPx()
        val count = floor((size.width + gapPx) / (cellPx + gapPx)).toInt().coerceAtLeast(1)
        val width = (size.width - gapPx * (count - 1)) / count
        val on = floor(progress().coerceIn(0f, 1f) * count + EPSILON).toInt()
        for (index in 0 until count) {
            drawRect(
                color = if (index < on) lit else dim,
                topLeft = Offset(index * (width + gapPx), 0f),
                size = Size(width, size.height),
            )
        }
    }
}

private const val EPSILON = 0.0001f

/**
 * Число, которое заливается снизу вверх по маске цифр поверх бледной копии.
 *
 * Заливка идёт ступенями при каждой смене текста. Анимированное значение
 * читается только при рисовании, поэтому текст не перекомпоновывается.
 */
@Composable
fun FillText(
    text: String,
    style: TextStyle,
    ghost: Color,
    fill: Color,
    modifier: Modifier = Modifier,
    durationMs: Int = FILL_MS,
) {
    val level = remember { Animatable(0f) }
    LaunchedEffect(text) {
        level.snapTo(0f)
        level.animateTo(1f, tween(durationMs, easing = SteppedEasing(FILL_STEPS)))
    }
    Box(modifier) {
        Text(text = text, style = style, color = ghost)
        Text(
            text = text,
            style = style,
            color = fill,
            modifier =
                Modifier.drawWithContent {
                    val top = size.height * (1f - level.value)
                    clipRect(top = top) { this@drawWithContent.drawContent() }
                },
        )
    }
}

private const val FILL_MS = 700
private const val FILL_STEPS = 20
