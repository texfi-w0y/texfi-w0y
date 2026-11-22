package com.texfi.w0y.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.UpdateState
import com.texfi.w0y.ui.components.Buzz
import com.texfi.w0y.ui.components.rememberHaptics
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairBigNumberStyle
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import kotlin.math.exp

/**
 * Путь обновления — та же линейка с делениями, что в плеере. До скачивания она пустая: слева
 * под ней своя версия, справа новая. Пошло скачивание — линейка заливается цветом, у края заливки
 * деления приподнимаются волной, которая едет вместе с ним, а проценты справа от новой версии
 * перекатываются цифрами. Скачано — волна пробегает всю линейку, на конце рисуется галочка, новая
 * версия окрашивается. Если размер файла неизвестен, волна просто ходит по линейке туда-обратно.
 */
@Composable
internal fun UpdateRoute(state: UpdateState, from: String, to: String, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val haptic = rememberHaptics()
    val downloading = state as? UpdateState.Downloading
    val ready = state is UpdateState.Ready
    val known = downloading != null && downloading.total > 0
    val share = if (known) (downloading!!.done.toFloat() / downloading.total).coerceIn(0f, 1f) else 0f
    // Заливка догоняет реальные байты плавно: куски приходят рывками, линия не должна дёргаться.
    val fill by animateFloatAsState(if (ready) 1f else share, tween(if (ready) 260 else 320, easing = LinearEasing), label = "updateFill")
    // Финал: волна пробегает всю линейку, потом рисуется галочка.
    val finale = remember { Animatable(0f) }
    LaunchedEffect(ready) {
        if (ready) {
            finale.snapTo(0f)
            finale.animateTo(1f, tween(FINALE_MS, easing = LinearEasing))
            haptic(Buzz.DONE)
        } else {
            finale.snapTo(0f)
        }
    }
    val sweep = rememberInfiniteTransition(label = "updateSweep")
    val wander by sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "updateWander")
    val waveAt =
        when {
            ready -> if (finale.value < 0.7f) finale.value / 0.7f else -1f
            known -> fill
            downloading != null -> wander
            else -> -1f
        }
    val check = if (ready) ((finale.value - 0.62f) / 0.38f).coerceIn(0f, 1f) else 0f
    val toColor = lerp(colors.text, tone, check)

    Column(modifier.testTag("update-route")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(to, style = HairBigNumberStyle, color = toColor, maxLines = 1, modifier = Modifier.weight(1f))
            if (downloading != null && known) {
                RollingNumber((share * 100).toInt(), HairBigNumberStyle, tone)
                Text("%", style = HairBigNumberStyle, color = tone.copy(alpha = 0.6f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val line = 1.dp.toPx()
            val step = 6.dp.toPx()
            val endPad = 14.dp.toPx() // место под стрелку и галочку
            val width = size.width - endPad
            val base = size.height - line
            val count = (width / step).toInt()
            val reach = 14.dp.toPx()
            for (i in 0..count) {
                val x = i * step
                val major = i % 5 == 0
                val passed = fill > 0f && x <= width * fill
                var h = if (major) 8.dp.toPx() else 4.dp.toPx()
                if (waveAt >= 0f) {
                    val d = (x - width * waveAt) / reach
                    h += 9.dp.toPx() * exp(-d * d)
                }
                drawLine(if (passed) tone else colors.outline, Offset(x, base), Offset(x, base - h), line)
            }
            // Основание: серое целиком, цветное по заливке.
            drawLine(colors.outline, Offset(0f, base), Offset(width, base), line)
            if (fill > 0f) drawLine(tone, Offset(0f, base), Offset(width * fill, base), line * 1.5f)
            // Конец пути: стрелка, после скачивания — галочка, прорисованная штрихом.
            val cx = size.width - endPad / 2f
            val cy = base - 5.dp.toPx()
            val r = 4.dp.toPx()
            val arrow = 1f - check
            if (arrow > 0f) {
                val c = (if (fill >= 1f) tone else colors.outline).copy(alpha = arrow)
                drawLine(c, Offset(cx - r * 0.6f, cy - r), Offset(cx + r * 0.4f, cy), line * 1.4f, StrokeCap.Round)
                drawLine(c, Offset(cx - r * 0.6f, cy + r), Offset(cx + r * 0.4f, cy), line * 1.4f, StrokeCap.Round)
            }
            if (check > 0f) {
                val path =
                    Path().apply {
                        moveTo(cx - r, cy)
                        lineTo(cx - r * 0.25f, cy + r * 0.8f)
                        lineTo(cx + r * 1.1f, cy - r * 0.9f)
                    }
                val measure = PathMeasure().apply { setPath(path, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * check, drawn, true)
                drawPath(drawn, tone, style = Stroke(line * 1.6f, cap = StrokeCap.Round))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            val left =
                when {
                    downloading != null && known -> megabytes(downloading.done, downloading.total)
                    else -> from
                }
            Text(left.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1, modifier = Modifier.weight(1f))
            Text(to.asLabel(), style = HairLabelStyle, color = if (ready) tone else colors.textMuted, maxLines = 1)
        }
    }
}

/** Число, цифры которого перекатываются: новая въезжает снизу, старая уходит вверх — 140 мс. */
@Composable
private fun RollingNumber(value: Int, style: TextStyle, color: androidx.compose.ui.graphics.Color) {
    val digits = value.toString()
    Row {
        // Разряды считаем справа: единицы остаются единицами, когда появляется десяток.
        digits.forEachIndexed { i, ch ->
            androidx.compose.runtime.key(digits.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(tween(ROLL_MS)) { it / 2 } + fadeIn(tween(ROLL_MS)))
                            .togetherWith(slideOutVertically(tween(ROLL_MS)) { -it / 2 } + fadeOut(tween(ROLL_MS)))
                    },
                    label = "digit",
                ) { d -> Text(d.toString(), style = style, color = color) }
            }
        }
    }
}

/** «12,4 / 31,8 МБ» — десятичный знак по языку телефона. */
@Composable
private fun megabytes(done: Long, total: Long): String {
    val mb = androidx.compose.ui.res.stringResource(com.texfi.w0y.R.string.update_mb)
    return "%.1f / %.1f %s".format(java.util.Locale.getDefault(), done / MB, total / MB, mb)
}

private const val MB = 1_048_576.0
private const val ROLL_MS = 140
private const val FINALE_MS = 900
