package com.texfi.w0y.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Фон Hairline: не пустая заливка, а «чертёжный лист». Холодный отсвет
 * сверху, едва заметная сетка 48 dp (как у PhotoCraft) и тёплое пятно цвета
 * играющей обложки в верхнем углу. Всё — градиенты и одна линия-путь: ни
 * размытия, ни растра, а анимируемые значения читаются только при
 * рисовании.
 */
@Composable
fun HairBackdrop(coverUrl: String?, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val cover by rememberCoverTint(coverUrl, colors.accent)
    val glow = animateColorAsState(cover, tween(600), label = "backdropGlow")
    val on = animateFloatAsState(if (coverUrl != null) 1f else 0f, tween(600), label = "backdropOn")
    Box(
        modifier.drawWithCache {
            val step = GRID.toPx()
            val grid = Path()
            var x = step
            while (x < size.width) {
                grid.moveTo(x, 0f)
                grid.lineTo(x, size.height)
                x += step
            }
            var y = step
            while (y < size.height) {
                grid.moveTo(0f, y)
                grid.lineTo(size.width, y)
                y += step
            }
            val wash = Brush.verticalGradient(0f to colors.progress.copy(alpha = 0.10f), 0.5f to Color.Transparent)
            val gridColor = colors.text.copy(alpha = 0.04f)
            onDrawBehind {
                drawRect(colors.background)
                drawRect(wash)
                val a = on.value * 0.30f
                if (a > 0.01f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(glow.value.copy(alpha = a), Color.Transparent),
                            center = Offset(size.width * 0.9f, 0f),
                            radius = size.width * 1.05f,
                        ),
                    )
                }
                drawPath(grid, gridColor, style = Stroke(1f))
            }
        },
    )
}

private val GRID = 48.dp
