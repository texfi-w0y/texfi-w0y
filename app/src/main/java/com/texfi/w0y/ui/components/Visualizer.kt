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
import com.texfi.w0y.ui.theme.Contrast
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
                VisualizerStyle.BARS, VisualizerStyle.LINES, VisualizerStyle.TERRAIN -> VisualizerStyle.SEGMENTS
                VisualizerStyle.WAVE, VisualizerStyle.ORBIT, VisualizerStyle.PULSE, VisualizerStyle.HORIZON -> VisualizerStyle.SCOPE
                VisualizerStyle.DOTS -> VisualizerStyle.MOSAIC
                else -> this
            }
        UiStyle.SMOOTH ->
            when (this) {
                VisualizerStyle.SEGMENTS, VisualizerStyle.MOSAIC, VisualizerStyle.LINES, VisualizerStyle.DOTS -> VisualizerStyle.BARS
                VisualizerStyle.SCOPE, VisualizerStyle.RING, VisualizerStyle.ORBIT, VisualizerStyle.TERRAIN, VisualizerStyle.PULSE, VisualizerStyle.HORIZON -> VisualizerStyle.WAVE
                else -> this
            }
        UiStyle.HAIRLINE ->
            when (this) {
                VisualizerStyle.SEGMENTS, VisualizerStyle.MOSAIC, VisualizerStyle.BARS -> VisualizerStyle.LINES
                VisualizerStyle.SCOPE, VisualizerStyle.RING, VisualizerStyle.WAVE -> VisualizerStyle.ORBIT
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
    val look = com.texfi.w0y.ui.theme.hairLook
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
    // Hairline: «зависшие» пики и стопка прошлых спектров для рельефа.
    val peaks = remember { FloatArray(BANDS) }
    val history = remember { Array(HISTORY) { FloatArray(BANDS) } }
    var historyHead by remember { mutableLongStateOf(0L) }
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
                    for (i in 0 until BANDS) peaks[i] = maxOf(levels[i], peaks[i] - PEAK_FALL)
                    if (frame % HISTORY_EVERY == 0L) {
                        historyHead++
                        levels.copyInto(history[(historyHead % HISTORY).toInt()])
                    }
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
                    VisualizerStyle.LINES -> hairLines(levels, peaks, colors.text, Contrast.ensure(colors.accent, colors.surface), colors.border, look.line.toPx())
                    VisualizerStyle.ORBIT -> hairOrbit(levels, analyzer.bass, frame, colors.text, Contrast.ensure(colors.accent, colors.surface), colors.progress, colors.border, look.line.toPx())
                    VisualizerStyle.TERRAIN -> hairTerrain(history, historyHead, colors.text, Contrast.ensure(colors.accent, colors.surface), colors.surface, look.line.toPx())
                    VisualizerStyle.PULSE -> hairPulse(levels, history, historyHead, frame, analyzer.bass, colors.text, Contrast.ensure(colors.accent, colors.surface), colors.border, look.line.toPx())
                    VisualizerStyle.HORIZON -> hairHorizon(levels, history, historyHead, frame, colors.text, Contrast.ensure(colors.accent, colors.surface), colors.border, look.line.toPx())
                    VisualizerStyle.DOTS -> hairDots(levels, peaks, colors.text, Contrast.ensure(colors.accent, colors.surface))
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

/** Hairline: «линии» — вертикальные штрихи, пик висит точкой, под основанием дрожит отражение. */
private fun DrawScope.hairLines(levels: FloatArray, peaks: FloatArray, line: Color, accent: Color, grid: Color, w: Float) {
    val slot = size.width / levels.size
    val base = size.height * 0.58f
    val room = base - size.height * 0.06f
    // Основание и деления под ним.
    drawLine(grid, Offset(0f, base), Offset(size.width, base), w)
    levels.forEachIndexed { i, v ->
        val x = i * slot + slot / 2f
        val h = (room * v).coerceAtLeast(w * 2f)
        drawLine(line.copy(alpha = 0.9f), Offset(x, base), Offset(x, base - h), w * 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // Отражение: короче и бледнее.
        drawLine(line.copy(alpha = 0.22f), Offset(x, base + w * 3f), Offset(x, base + w * 3f + h * 0.45f), w * 1.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // Пик — акцентная точка над штрихом.
        val py = base - (room * peaks[i]).coerceAtLeast(w * 2f) - 5.dp.toPx()
        drawCircle(accent, w * 1.6f, Offset(x, py))
    }
}

/** Hairline: «орбита» — три кольца дышат от баса, по окружности лучи спектра и медленно бегущая метка. */
private fun DrawScope.hairOrbit(levels: FloatArray, bass: Float, frame: Long, line: Color, accent: Color, progress: Color, grid: Color, w: Float) {
    val c = center
    val base = min(size.width, size.height) * 0.17f
    val pulse = bass.coerceIn(0f, 1f)
    for (k in 0..2) {
        val r = base * (1f + 0.34f * k) + pulse * base * (0.10f + 0.07f * k)
        drawCircle(line.copy(alpha = (0.55f - 0.15f * k)), r, c, style = Stroke(w))
    }
    val n = levels.size * 2
    val spin = frame * 0.004f
    val r0 = base * 1.75f
    val reach = min(size.width, size.height) * 0.15f
    for (i in 0 until n) {
        val v = levels[if (i < levels.size) i else n - 1 - i]
        val a = (i.toFloat() / n) * 2f * PI.toFloat() - PI.toFloat() / 2f + spin
        val dir = Offset(cos(a), sin(a))
        val len = (reach * v).coerceAtLeast(2.dp.toPx())
        drawLine(line.copy(alpha = 0.85f), c + dir * r0, c + dir * (r0 + len), w * 1.3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if (v > 0.75f) drawCircle(accent, w * 1.8f, c + dir * (r0 + len + 4.dp.toPx()))
    }
    // Бегущая метка по внешнему кольцу.
    val ma = frame * 0.012f
    drawCircle(progress, w * 2.4f, c + Offset(cos(ma), sin(ma)) * (base * 1.68f))
    drawCircle(grid, base * 0.5f, c, style = Stroke(w))
}

/** Hairline: «рельеф» — прошлые спектры стопкой линий; ближние закрывают дальние, как на обложке Unknown Pleasures. */
private fun DrawScope.hairTerrain(history: Array<FloatArray>, head: Long, line: Color, accent: Color, ground: Color, w: Float) {
    val rows = history.size
    val top = size.height * 0.16f
    val bottom = size.height * 0.92f
    val step = (bottom - top) / rows
    val amp = step * 3.4f
    val points = 46
    val padX = size.width * 0.08f
    val width = size.width - padX * 2f
    // От дальних (старых) к ближним (новым): новые закрывают старые.
    for (row in 0 until rows) {
        val age = rows - 1 - row
        val data = history[((head - age) % rows + rows).toInt() % rows]
        val y0 = top + (row + 1) * step
        val path = Path()
        for (p in 0..points) {
            val f = p.toFloat() / points
            val band = (f * (data.size - 1))
            val i0 = band.toInt().coerceIn(0, data.size - 1)
            val i1 = (i0 + 1).coerceAtMost(data.size - 1)
            val v = data[i0] + (data[i1] - data[i0]) * (band - i0)
            // Края гасим: холмик по центру, как на оригинале.
            val edge = sin(f * PI.toFloat()).coerceIn(0f, 1f)
            val x = padX + width * f
            val y = y0 - v * amp * edge
            if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(padX + width, y0 + step)
            lineTo(padX, y0 + step)
            close()
        }
        drawPath(fill, ground)
        val t = row.toFloat() / (rows - 1)
        drawPath(path, if (row == rows - 1) accent else line.copy(alpha = 0.25f + 0.6f * t), style = Stroke(w * 1.2f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** Средний уровень нижних полос — «бас» прошлого спектра. */
private fun bassOf(row: FloatArray): Float {
    var sum = 0f
    for (i in 0 until 4) sum += row[i]
    return sum / 4f
}

/** Доля пути между двумя записями истории: 0 сразу после новой записи, к следующей — почти 1. Волны идут плавно, а не шагами. */
private fun historyPhase(frame: Long): Float = ((frame + HISTORY_EVERY - 1) % HISTORY_EVERY).toFloat() / HISTORY_EVERY

/**
 * Hairline: «пульс». Каждое прошлое мгновение — кольцо, которое уже отошло от центра: чем
 * сильнее тогда был бас, тем ярче и толще кольцо, сильные удары — акцентом. Кольца плывут
 * между записями плавно. По внешнему кругу — шкала делений, в центре — замкнутая линия спектра.
 */
private fun DrawScope.hairPulse(levels: FloatArray, history: Array<FloatArray>, head: Long, frame: Long, bass: Float, line: Color, accent: Color, grid: Color, w: Float) {
    val c = center
    val m = min(size.width, size.height)
    val base = m * 0.14f
    val reach = m * 0.47f
    val rows = history.size
    val phase = historyPhase(frame)
    val b = bass.coerceIn(0f, 1f)
    // Волны: от старых (дальних) к новым.
    val start = base * 1.6f
    for (age in rows - 1 downTo 1) {
        val row = history[((head - age) % rows + rows).toInt() % rows]
        val e = bassOf(row)
        if (e < 0.08f) continue
        val f = ((age - 1 + phase) / (rows - 1)).coerceIn(0f, 1f)
        val fade = 1f - f
        val r = start + (reach - start) * f
        val tone = if (e > 0.62f) accent else line
        drawCircle(tone.copy(alpha = (e * fade * fade * 0.95f).coerceIn(0f, 0.95f)), r, c, style = Stroke(w * (1f + 1.6f * e * fade)))
    }
    // Шкала по внешнему кругу: короткие деления, каждое восьмое длиннее, как на приборе.
    drawCircle(grid, reach, c, style = Stroke(w))
    val ticks = 64
    for (t in 0 until ticks) {
        val a = t.toFloat() / ticks * 2f * PI.toFloat()
        val dir = Offset(cos(a), sin(a))
        val len = if (t % 8 == 0) 7.dp.toPx() else 3.dp.toPx()
        drawLine(grid, c + dir * reach, c + dir * (reach + len), w)
    }
    // Замкнутый спектр вокруг центра: плавная линия через середины соседних точек.
    val n = levels.size * 2
    fun pt(i: Int): Offset {
        val k = i % n
        val v = levels[if (k < levels.size) k else n - 1 - k]
        val a = (k.toFloat() / n) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val r = base * 1.25f + base * 0.95f * v
        return Offset(c.x + cos(a) * r, c.y + sin(a) * r)
    }
    val path = Path()
    for (i in 0..n) {
        val p0 = pt(i)
        val p1 = pt(i + 1)
        val mx = (p0.x + p1.x) / 2f
        val my = (p0.y + p1.y) / 2f
        if (i == 0) path.moveTo(mx, my) else path.quadraticTo(p0.x, p0.y, mx, my)
    }
    path.close()
    drawPath(path, accent, style = Stroke(w * 1.5f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawCircle(line.copy(alpha = 0.9f), base * (0.55f + 0.35f * b), c, style = Stroke(w * 1.3f))
    drawCircle(accent, w * 2.4f, c)
}

/**
 * Hairline: «горизонт». Плавная линия спектра по ширине, отражённая вниз и бледнее. Прошлые линии отплывают вдаль — чуть выше и ниже ростом, плавно, без рывков.
 * Над самой громкой полосой висит акцентная точка с тонкой нитью до горизонта.
 */
private fun DrawScope.hairHorizon(levels: FloatArray, history: Array<FloatArray>, head: Long, frame: Long, line: Color, accent: Color, grid: Color, w: Float) {
    val mid = size.height * 0.56f
    val amp = size.height * 0.36f
    val pad = size.width * 0.06f
    val width = size.width - pad * 2f
    drawLine(grid, Offset(0f, mid), Offset(size.width, mid), w)
    fun yOf(data: FloatArray, i: Int, base: Float, dir: Float, scale: Float): Float {
        val f = (i + 0.5f) / data.size
        val edge = sin(f * PI.toFloat()).coerceIn(0f, 1f)
        return base - dir * data[i] * amp * scale * edge
    }
    fun curve(data: FloatArray, base: Float, dir: Float, scale: Float): Path {
        val path = Path()
        var px = pad
        var py = base
        path.moveTo(px, py)
        for (i in data.indices) {
            val x = pad + width * (i + 0.5f) / data.size
            val y = yOf(data, i, base, dir, scale)
            val cx = (px + x) / 2f
            path.cubicTo(cx, py, cx, y, x, y)
            px = x
            py = y
        }
        val endX = pad + width
        path.cubicTo((px + endX) / 2f, py, (px + endX) / 2f, base, endX, base)
        return path
    }
    // След: прошлые линии уходят вдаль, плывут между записями плавно.
    val rows = history.size
    val phase = historyPhase(frame)
    val echoes = 5
    for (age in echoes downTo 1) {
        val row = history[((head - age) % rows + rows).toInt() % rows]
        val f = ((age - 1 + phase) / echoes).coerceIn(0f, 1f)
        val lift = size.height * 0.16f * f
        drawPath(curve(row, mid - lift, 1f, 1f - 0.35f * f), line.copy(alpha = 0.22f * (1f - f)), style = Stroke(w))
    }
    drawPath(curve(levels, mid, -1f, 0.55f), line.copy(alpha = 0.22f), style = Stroke(w))
    drawPath(curve(levels, mid, 1f, 1f), accent, style = Stroke(w * 1.6f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    // Пик: самая громкая полоса.
    var top = 0
    for (i in levels.indices) if (levels[i] > levels[top]) top = i
    if (levels[top] > 0.08f) {
        val x = pad + width * (top + 0.5f) / levels.size
        val y = yOf(levels, top, mid, 1f, 1f)
        drawLine(accent.copy(alpha = 0.35f), Offset(x, y), Offset(x, mid), w)
        drawCircle(accent, w * 2.4f, Offset(x, y))
    }
}

/**
 * Hairline: «точки». Матрица круглых точек: в колонке горит столько, сколько уровень полосы,
 * верхняя горящая разгорается плавно, а не щёлкает. Над колонкой висит акцентная точка пика,
 * остальные — едва видны. Как табло из точек.
 */
private fun DrawScope.hairDots(levels: FloatArray, peaks: FloatArray, line: Color, accent: Color) {
    val cols = levels.size / 2
    val step = size.width / (cols + 1)
    val rows = ((size.height * 0.84f) / step).toInt().coerceIn(6, 18)
    val top = (size.height - rows * step) / 2f + step / 2f
    val r = step * 0.30f
    val dim = r * 0.55f
    for (cx in 0 until cols) {
        val v = ((levels[cx * 2] + levels[cx * 2 + 1]) / 2f).coerceIn(0f, 1f)
        val pk = ((peaks[cx * 2] + peaks[cx * 2 + 1]) / 2f).coerceIn(0f, 1f)
        val fill = v * rows
        val peakRow = (pk * rows).toInt().coerceIn(0, rows - 1)
        val x = step * (cx + 1)
        for (ry in 0 until rows) {
            val y = top + (rows - 1 - ry) * step
            val c = Offset(x, y)
            if (ry == peakRow && pk > 0.05f) {
                drawCircle(accent, r, c)
                continue
            }
            // Сколько точка горит: целиком, частично (верхняя) или нет.
            val lit = (fill - ry).coerceIn(0f, 1f)
            val height = ry.toFloat() / rows
            val alpha = 0.08f + lit * (0.5f + 0.42f * height)
            drawCircle(line.copy(alpha = alpha), dim + (r - dim) * lit, c)
        }
    }
}

private const val BANDS = 32
private const val WAVE_POINTS = 128
private const val HISTORY = 14
private const val HISTORY_EVERY = 3L
private const val PEAK_FALL = 0.014f
private const val IDLE_FRAMES = 60
private const val IDLE_MS = 250L
