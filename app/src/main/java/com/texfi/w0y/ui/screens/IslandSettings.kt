package com.texfi.w0y.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.components.rememberTapHaptic
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import kotlin.math.roundToInt

/**
 * Линейка выбора в настройках Hairline New — как линейка хода в плеере: мелкие штрихи, на
 * каждом значении штрих выше, выбранное — черта акцента с квадратиком. Тянуть или нажать —
 * встаёт на ближайшее значение, с коротким щелчком вибро. Под линейкой крайние значения.
 */
@Composable
internal fun IslandStepRuler(count: Int, selected: Int, startLabel: String, endLabel: String, description: String, onSelect: (Int) -> Unit) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.surface)
    val tap = rememberTapHaptic()
    val select = rememberUpdatedState(onSelect)
    val current = rememberUpdatedState(selected)
    val steps = (count - 1).coerceAtLeast(1)
    val at by animateFloatAsState(selected.coerceAtLeast(0).toFloat() / steps, hairSpec(HairTokens.SHORT_MS), label = "stepRuler")
    fun pick(x: Float, width: Float) {
        val index = ((x / width).coerceIn(0f, 1f) * steps).roundToInt()
        if (index != current.value) {
            tap()
            select.value(index)
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(selected.coerceAtLeast(0).toFloat() / steps, 0f..1f, steps = (count - 2).coerceAtLeast(0))
                    setProgress { target -> select.value((target.coerceIn(0f, 1f) * steps).roundToInt()); true }
                }
                .pointerInput(count) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
                .pointerInput(count) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        pick(change.position.x, size.width.toFloat())
                    }
                },
        ) {
            val line = 1.dp.toPx()
            val base = size.height - 4.dp.toPx()
            val gap = 6.dp.toPx()
            val n = (size.width / gap).toInt()
            val played = colors.text.copy(alpha = 0.78f)
            val rest = colors.text.copy(alpha = 0.24f)
            for (i in 0..n) {
                val x = i * gap
                val h = if (i % 4 == 0) 7.dp.toPx() else 4.dp.toPx()
                drawLine(if (x <= at * size.width) played else rest, Offset(x, base), Offset(x, base - h), line)
            }
            // Штрихи значений — выше остальных: видно, куда линейка встанет.
            for (i in 0..steps) {
                val x = (size.width - line) * i / steps
                drawLine(if (i <= selected) played else rest, Offset(x, base), Offset(x, base - 12.dp.toPx()), line)
            }
            drawLine(rest, Offset(0f, base), Offset(size.width, base), line)
            val px = (size.width - 2.dp.toPx()) * at
            drawLine(tone, Offset(px + 1.dp.toPx(), base), Offset(px + 1.dp.toPx(), 4.dp.toPx()), 2.dp.toPx())
            drawRect(tone, Offset(px - 2.dp.toPx(), 0f), Size(6.dp.toPx(), 6.dp.toPx()))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(startLabel.asLabel(), style = HairLabelStyle, color = colors.textMuted.copy(alpha = 0.7f), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(endLabel.asLabel(), style = HairLabelStyle, color = colors.textMuted.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}
