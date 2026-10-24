package com.texfi.w0y.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.StatsBar
import com.texfi.w0y.data.StatsBucket
import com.texfi.w0y.ui.theme.LocalW0yColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil
import kotlin.math.max

/**
 * График минут по дням или месяцам — столбики из квадратных клеток.
 *
 * Рисуется одним Canvas без сторонних библиотек: столбик — стопка клеток
 * с зазором, как сегменты прогресса в плеере. Вырастает ступенями при
 * смене периода. Нажатие на столбик показывает его дату и минуты.
 */
@Composable
fun ListenChart(
    bars: List<StatsBar>,
    bucket: StatsBucket,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
) {
    val colors = LocalW0yColors.current
    val peak = max(1, bars.maxOfOrNull { it.minutes } ?: 1)
    var selected by remember(bars) { mutableStateOf(bars.indexOfLast { it.minutes > 0 }) }
    val hair = com.texfi.w0y.ui.theme.isHair
    val grow = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(W0yMotion.SLOW_MS, easing = if (hair) com.texfi.w0y.ui.theme.HairTokens.EaseOut else W0yMotion.StepWide))
    }
    // Язык даты — язык приложения, а не системы: иначе в русском интерфейсе «Oct 24, 2026».
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val formatter =
        remember(bucket, locale) {
            if (bucket == StatsBucket.MONTH) DateTimeFormatter.ofPattern("LLL yyyy", locale) else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        }

    Column(modifier.fillMaxWidth()) {
        val pick = bars.getOrNull(selected)
        Text(
            text =
                pick?.let { "${it.start.format(formatter)} · ${stringResource(R.string.stats_minutes_short, it.minutes)}" }
                    ?: stringResource(R.string.stats_chart_empty),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        if (bars.isNotEmpty()) {
                            selected = (offset.x / (size.width.toFloat() / bars.size)).toInt().coerceIn(0, bars.lastIndex)
                        }
                    }
                },
        ) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            if (hair) {
                // Hairline: тонкие сплошные столбики на линии основания, выбранный — акцентом.
                val line = 1.dp.toPx()
                drawRect(colors.border, Offset(0f, size.height - line), Size(size.width, line))
                val w = (slot * 0.5f).coerceIn(2f, 8.dp.toPx())
                bars.forEachIndexed { i, bar ->
                    if (bar.minutes <= 0) return@forEachIndexed
                    val h = max(2.dp.toPx(), (size.height - line) * bar.minutes / peak * grow.value)
                    drawRect(
                        if (i == selected) colors.accent else colors.text.copy(alpha = 0.32f),
                        Offset(i * slot + (slot - w) / 2f, size.height - line - h),
                        Size(w, h),
                    )
                }
                return@Canvas
            }
            val gap = max(1f, slot * 0.22f)
            val barWidth = slot - gap
            // Клетка — квадрат по ширине столбика, но не мельче 3 px и не
            // крупнее 10 dp: иначе у длинного периода выйдут нити, у
            // короткого — кирпичи.
            val cell = barWidth.coerceIn(3f, 10.dp.toPx())
            val rows = max(1, (size.height / (cell + gap)).toInt())
            bars.forEachIndexed { i, bar ->
                val x = i * slot + gap / 2
                val lit = ceil(bar.minutes.toFloat() / peak * rows * grow.value).toInt()
                    .let { if (bar.minutes > 0) max(1, it) else 0 }
                for (r in 0 until rows) {
                    val y = size.height - (r + 1) * (cell + gap) + gap
                    val color =
                        when {
                            r >= lit -> colors.surfaceHigh
                            i == selected -> colors.secondary
                            else -> colors.accent
                        }
                    // Пустые клетки показываем только в нижнем ряду — сетка
                    // целиком шумела бы сильнее самих данных.
                    if (r < lit || r == 0) drawRect(color, Offset(x, y), Size(barWidth, cell))
                }
            }
        }
        if (bars.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(bars.first().start.format(formatter), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                Text(bars.last().start.format(formatter), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        }
    }
}
