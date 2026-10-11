package com.texfi.w0y.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Фон экрана. В обычных темах — сплошной цвет темы.
 *
 * В стекле сплошной фон убивает весь эффект: полупрозрачной карточке
 * нечего показывать сквозь себя. Поэтому под ней два мягких цветных пятна —
 * акцент сверху слева и фиолетовое снизу справа. Это градиенты, а не
 * размытие: никакой цены по кадрам, и работает на любой версии Android.
 */
@Composable
fun Modifier.screenBackground(): Modifier {
    val colors = LocalW0yColors.current
    if (isHair) {
        // Hairline: тот же «чертёжный лист», что под всем приложением — холодный отсвет и сетка.
        // Оверлеи лежат под строкой состояния: заливку продлеваем вверх, чтобы не было шва.
        val statusTop = WindowInsets.statusBars.getTop(androidx.compose.ui.platform.LocalDensity.current).toFloat()
        return background(colors.background).drawBehind {
            drawRect(colors.background, Offset(0f, -statusTop), androidx.compose.ui.geometry.Size(size.width, statusTop))
            drawRect(Brush.verticalGradient(0f to colors.progress.copy(alpha = 0.10f), 0.5f to Color.Transparent))
            val step = 48.dp.toPx()
            val grid = androidx.compose.ui.graphics.Path()
            var x = step
            while (x < size.width) { grid.moveTo(x, 0f); grid.lineTo(x, size.height); x += step }
            var y = step
            while (y < size.height) { grid.moveTo(0f, y); grid.lineTo(size.width, y); y += step }
            drawPath(grid, colors.text.copy(alpha = 0.04f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
    }
    if (!colors.glass) return background(colors.background)
    val accent = colors.accent
    return background(colors.background).drawBehind {
        val big = size.maxDimension
        drawRect(
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = GLOW_ALPHA), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.08f),
                radius = big * 0.75f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(GlassViolet.copy(alpha = GLOW_ALPHA * 0.8f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.85f),
                radius = big * 0.7f,
            ),
        )
    }
}

private val GlassViolet = Color(0xFF8A5CF6)
private const val GLOW_ALPHA = 0.32f
