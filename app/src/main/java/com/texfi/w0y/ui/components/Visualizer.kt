package com.texfi.w0y.ui.components

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.data.VisualizerStyle
import com.texfi.w0y.playback.SpectrumAnalyzer
import com.texfi.w0y.playback.SpectrumBus
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.styleTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Стиль визуализатора под оформление: чужой заменяется ближайшим своим. */
fun VisualizerStyle.forUi(style: UiStyle): VisualizerStyle =
    when (style) {
        UiStyle.PIXEL ->
            when (this) {
                VisualizerStyle.BARS -> VisualizerStyle.SEGMENTS
                VisualizerStyle.WAVE -> VisualizerStyle.SCOPE
                else -> this
            }
        UiStyle.SMOOTH, UiStyle.HAIRLINE ->
            when (this) {
                VisualizerStyle.SEGMENTS, VisualizerStyle.MOSAIC -> VisualizerStyle.BARS
                VisualizerStyle.SCOPE, VisualizerStyle.RING -> VisualizerStyle.WAVE
                else -> this
            }
    }

/**
 * Визуализатор на месте обложки.
 *
 * Звук берётся из отвода в аудиоцепочке ([SpectrumBus]) — без микрофона и
 * без задержки звука. Отвод включён, только пока визуализатор на экране и
 * приложение на переднем плане: при выключенном экране и в фоне копирование
 * и расчёт останавливаются. БПФ считается на фоновом потоке; главный поток
 * только рисует готовые уровни. На паузе столбики спокойно оседают. При
 * выключенных системных анимациях частота кадров падает до 10.
 */
@Composable
fun Visualizer(
    bus: SpectrumBus,
    style: VisualizerStyle,
    playing: Boolean,
    sensitivity: Float,
    fps: Int,
    backdropUrl: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalW0yColors.current
    val tokens = styleTokens
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val reduceMotion =
        remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val analyzer = remember { SpectrumAnalyzer(size = 1024, bands = BANDS) }
    // Двойной буфер: фон пишет в один, рисование читает другой.
    val levels = remember { FloatArray(BANDS) }
    val wave = remember { FloatArray(WAVE_POINTS) }
    var frame by remember { mutableLongStateOf(0L) }
    val isPlaying by rememberUpdatedState(playing)
    val gain by rememberUpdatedState(sensitivity)

    DisposableEffect(visible) {
        bus.active = visible
        onDispose { bus.active = false }
    }
    LaunchedEffect(visible, fps, reduceMotion) {
        if (!visible) return@LaunchedEffect
        val frameMs = 1000L / (if (reduceMotion) 10 else fps.coerceIn(30, 60))
        withContext(Dispatchers.Default) {
            var idle = 0
            while (isActive) {
                val started = System.nanoTime()
                if (isPlaying) {
                    bus.latest(analyzer.samples)
                    analyzer.analyze(bus.sampleRate, gain)
                    idle = 0
                } else {
                    analyzer.settle()
                    idle++
                }
                synchronized(levels) {
                    analyzer.levels.copyInto(levels)
                    val step = analyzer.samples.size / WAVE_POINTS
                    for (i in 0 until WAVE_POINTS) wave[i] = if (isPlaying) analyzer.samples[i * step] * gain else wave[i] * 0.85f
                }
                frame++
                // Пауза: когда всё осело, кадры больше не нужны.
                val wait = if (idle > IDLE_FRAMES) IDLE_MS else frameMs - (System.nanoTime() - started) / 1_000_000
                delay(wait.coerceAtLeast(4))
            }
        }
    }

    Box(modifier) {
        if (backdropUrl != null) {
            // Размытая обложка под визуализатором; до Android 12 размытия нет —
            // остаётся притушенная обложка.
            CoverImage(
                url = backdropUrl,
                px = Thumbnails.TILE,
                corner = 10,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(0.35f)
                        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(24.dp) else Modifier),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            frame // чтение кадра — перерисовка только слоя рисования
            synchronized(levels) {
                when (style) {
                    VisualizerStyle.SEGMENTS -> segments(levels, colors.accent, colors.secondary, colors.border)
                    VisualizerStyle.RING -> ring(levels, colors.accent, colors.secondary, tokens.segmented)
                    VisualizerStyle.SCOPE -> scope(wave, colors.accent, tokens.segmented)
                    VisualizerStyle.MOSAIC -> mosaic(levels, analyzer.bass, colors.accent, colors.secondary, colors.surfaceHigh)
                    VisualizerStyle.BARS -> bars(levels, colors.accent, colors.secondary)
                    VisualizerStyle.WAVE -> smoothWave(levels, colors.accent, colors.secondary)
                }
            }
        }
    }
}

/** Столбики из квадратных клеток; верхняя горящая — песочная. */
private fun DrawScope.segments(levels: FloatArray, lit: Color, peak: Color, dim: Color) {
    val slot = size.width / levels.size
    val gap = slot * 0.2f
    val cell = slot - gap
    val rows = (size.height / (cell + gap)).toInt().coerceAtLeast(1)
    levels.forEachIndexed { i, v ->
        val on = (v * rows).toInt()
        for (r in 0 until rows) {
            val y = size.height - (r + 1) * (cell + gap)
            val color = if (r < on - 1) lit else if (r == on - 1) peak else dim.copy(alpha = 0.25f)
            drawRect(color, Offset(i * slot + gap / 2, y), Size(cell, cell))
        }
    }
}

/** Кольцо: лучи от круга, длина — уровень полосы. */
private fun DrawScope.ring(levels: FloatArray, lit: Color, peak: Color, pixel: Boolean) {
    val c = center
    val r0 = min(size.width, size.height) * 0.24f
    val span = min(size.width, size.height) * 0.24f
    val n = levels.size * 2
    for (i in 0 until n) {
        val v = levels[if (i < levels.size) i else n - 1 - i]
        val a = (i.toFloat() / n) * 2 * PI.toFloat() - PI.toFloat() / 2
        val len = span * v
        val dir = Offset(cos(a), sin(a))
        if (pixel) {
            val cell = 6.dp.toPx()
            var d = 0f
            while (d < len) {
                val p = c + dir * (r0 + d)
                drawRect(if (d + cell >= len) peak else lit, p - Offset(cell / 2, cell / 2), Size(cell, cell))
                d += cell * 1.4f
            }
        } else {
            drawLine(lit, c + dir * r0, c + dir * (r0 + len.coerceAtLeast(2f)), strokeWidth = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** Осциллограф: форма волны; в Pixel — ступенями по клеткам. */
private fun DrawScope.scope(wave: FloatArray, color: Color, pixel: Boolean) {
    val mid = size.height / 2
    val amp = size.height * 0.42f
    if (pixel) {
        val cell = 5.dp.toPx()
        val cols = (size.width / cell).toInt()
        for (x in 0 until cols) {
            val v = wave[(x.toFloat() / cols * wave.size).toInt().coerceIn(0, wave.lastIndex)].coerceIn(-1f, 1f)
            val y = mid - v * amp
            drawRect(color, Offset(x * cell, (y / cell).toInt() * cell), Size(cell, cell))
        }
    } else {
        val path = Path()
        wave.forEachIndexed { i, v ->
            val x = i.toFloat() / (wave.size - 1) * size.width
            val y = mid - v.coerceIn(-1f, 1f) * amp
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(3.dp.toPx()))
    }
}

/** Мозаика: решётка клеток, яркость которых качает бас, с узором по полосам. */
private fun DrawScope.mosaic(levels: FloatArray, bass: Float, lit: Color, peak: Color, dim: Color) {
    val n = 8
    val cell = min(size.width, size.height) / n
    val ox = (size.width - cell * n) / 2
    val oy = (size.height - cell * n) / 2
    for (y in 0 until n) {
        for (x in 0 until n) {
            val band = levels[((x * 7 + y * 13) % levels.size)]
            val v = (bass * 0.7f + band * 0.5f).coerceIn(0f, 1f)
            val color = if (v > 0.8f) peak else lit.copy(alpha = 0.15f + v * 0.85f)
            drawRect(dim, Offset(ox + x * cell, oy + y * cell), Size(cell - 3f, cell - 3f))
            drawRect(color, Offset(ox + x * cell, oy + y * cell), Size(cell - 3f, cell - 3f))
        }
    }
}

/** Скруглённые столбики плавного стиля, зеркально от середины. */
private fun DrawScope.bars(levels: FloatArray, lit: Color, peak: Color) {
    val slot = size.width / levels.size
    val w = slot * 0.62f
    val mid = size.height / 2
    levels.forEachIndexed { i, v ->
        val h = (size.height * 0.9f * v).coerceAtLeast(w)
        drawRoundRect(
            Brush.verticalGradient(listOf(peak, lit, peak), startY = mid - h / 2, endY = mid + h / 2),
            topLeft = Offset(i * slot + (slot - w) / 2, mid - h / 2),
            size = Size(w, h),
            cornerRadius = CornerRadius(w / 2),
        )
    }
}

/** Волна плавного стиля: гладкая кривая по уровням, залитая градиентом. */
private fun DrawScope.smoothWave(levels: FloatArray, lit: Color, peak: Color) {
    val mid = size.height / 2
    val step = size.width / (levels.size - 1)
    for (mirror in listOf(1f, -1f)) {
        val path = Path().apply { moveTo(0f, mid) }
        var px = 0f
        var py = mid
        levels.forEachIndexed { i, v ->
            val x = i * step
            val y = mid - mirror * v * size.height * 0.42f
            val cx = (px + x) / 2
            path.cubicTo(cx, py, cx, y, x, y)
            px = x
            py = y
        }
        path.lineTo(size.width, mid)
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(lit.copy(alpha = 0.85f), peak.copy(alpha = 0.25f))))
    }
}

private const val BANDS = 32
private const val WAVE_POINTS = 128
private const val IDLE_FRAMES = 60
private const val IDLE_MS = 250L
