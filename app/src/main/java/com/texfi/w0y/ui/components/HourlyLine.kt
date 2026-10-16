package com.texfi.w0y.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlin.math.max

/**
 * Активность по часам суток — тонкая линия в одну клетку высотой, без анимации.
 * Подписи 00 · 06 · 12 · 18 · 24 моно-метками; сама линия 1.5 dp акцентом.
 */
@Composable
fun HourlyLine(hours: List<Int>, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val peak = max(1, hours.maxOrNull() ?: 1)
    val summary = stringResource(R.string.stats_hourly_title)
    Column(modifier.fillMaxWidth().semantics { contentDescription = summary }) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            if (hours.size < 2) return@Canvas
            val step = size.width / (hours.size - 1)
            val base = size.height - 1.dp.toPx()
            drawLine(colors.border, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
            val path = Path()
            hours.forEachIndexed { i, v ->
                val x = i * step
                val y = base - (base - 4.dp.toPx()) * v / peak
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colors.accent, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "24").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        }
    }
}
