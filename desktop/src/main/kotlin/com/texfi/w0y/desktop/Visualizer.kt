package com.texfi.w0y.desktop

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.texfi.w0y.playback.SpectrumAnalyzer
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Звук для визуализатора. На телефоне копия звука берётся из цепочки плеера;
 * здесь mpv — отдельный процесс, поэтому слушаем его поток в PipeWire/PulseAudio:
 * `parec --monitor-stream` снимает ровно то, что играет w0y (а не всю систему),
 * без микрофона и без задержки звука. Работает, пока визуализатор на экране.
 */
object SpectrumCapture {
    const val SAMPLE_RATE = 44_100
    private const val RING = 8192

    /** Есть ли чем снимать звук: нужны pactl и parec (пакет pipewire-pulse / pulseaudio-utils). */
    val available: Boolean by lazy { which("pactl") && which("parec") }

    private fun which(name: String) =
        Platform.has(name)

    private val ring = FloatArray(RING)
    private var write = 0
    private var users = 0
    private var process: Process? = null

    @Synchronized
    fun acquire() {
        if (!available) return
        if (users++ == 0) begin()
    }

    @Synchronized
    fun release() {
        if (users > 0 && --users == 0) end()
    }

    private fun end() {
        runCatching { process?.destroy() }
        process = null
    }

    private fun begin() {
        thread(isDaemon = true, name = "spectrum-capture") {
            while (synchronized(this) { users > 0 }) {
                val index = findStream()
                if (index == null) {
                    Thread.sleep(500)
                    continue
                }
                val proc =
                    runCatching {
                        ProcessBuilder("parec", "--monitor-stream=$index", "--rate=$SAMPLE_RATE", "--channels=1", "--format=float32le", "--latency-msec=20")
                            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
                    }.getOrNull()
                if (proc == null) {
                    Thread.sleep(1000)
                    continue
                }
                synchronized(this) { process = proc }
                val input = DataInputStream(proc.inputStream.buffered())
                val chunk = ByteArray(2048 * 4)
                runCatching {
                    while (synchronized(this) { users > 0 }) {
                        input.readFully(chunk)
                        val buf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                        synchronized(ring) { while (buf.remaining() >= 4) push(buf.float) }
                    }
                }
                runCatching { proc.destroy() }
                // Поток исчез (трек сменился, mpv пересоздал выход) — ищем заново.
                Thread.sleep(200)
            }
        }
    }

    private fun push(v: Float) {
        ring[write] = v
        write = (write + 1) % RING
    }

    fun latest(out: FloatArray) {
        synchronized(ring) {
            var at = (write - out.size + RING) % RING
            for (i in out.indices) {
                out[i] = ring[at]
                at = (at + 1) % RING
            }
        }
    }

    /** Номер потока w0y среди воспроизводимых потоков звукового сервера. */
    private fun findStream(): Int? =
        runCatching {
            val p = ProcessBuilder("pactl", "-f", "json", "list", "sink-inputs").redirectErrorStream(false).start()
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor()
            val items = Json.parseToJsonElement(text).jsonArray
            // Наш поток зовётся w0y; если запущено два экземпляра, берём тот, что играет, а не на паузе.
            items.filter {
                val props = it.jsonObject["properties"] as? JsonObject ?: return@filter false
                props["application.name"]?.jsonPrimitive?.contentOrNull == "w0y"
            }.sortedBy { it.jsonObject["corked"]?.jsonPrimitive?.contentOrNull == "true" }
                .firstOrNull()?.jsonObject?.get("index")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        }.getOrNull()
}

private const val BANDS = 32
private const val WAVE_POINTS = 128
private const val HISTORY = 14
private const val HISTORY_EVERY = 3L
private const val PEAK_FALL = 0.014f
private const val IDLE_FRAMES = 60
private const val IDLE_MS = 250L

/**
 * Визуализатор на месте обложки. БПФ считается на фоновом потоке; главный поток
 * только рисует готовые уровни. На паузе столбики оседают, а когда осели —
 * кадры почти не считаются.
 */
@Composable
fun Visualizer(
    style: VisualizerStyle,
    playing: Boolean,
    sensitivity: Float,
    fps: Int,
    modifier: Modifier = Modifier,
) {
    val analyzer = remember { SpectrumAnalyzer(size = 1024, bands = BANDS) }
    val levels = remember { FloatArray(BANDS) }
    val wave = remember { FloatArray(WAVE_POINTS) }
    // Hairline: «зависшие» пики и стопка прошлых спектров для рельефа.
    val peaks = remember { FloatArray(BANDS) }
    val history = remember { Array(HISTORY) { FloatArray(BANDS) } }
    var historyHead by remember { mutableLongStateOf(0L) }
    var frame by remember { mutableLongStateOf(0L) }
    val isPlaying by rememberUpdatedState(playing)
    val gain by rememberUpdatedState(sensitivity)

    DisposableEffect(Unit) {
        SpectrumCapture.acquire()
        onDispose { SpectrumCapture.release() }
    }
    LaunchedEffect(fps) {
        val frameMs = 1000L / fps.coerceIn(30, 60)
        withContext(Dispatchers.Default) {
            var idle = 0
            while (isActive) {
                val started = System.nanoTime()
                if (isPlaying) {
                    SpectrumCapture.latest(analyzer.samples)
                    analyzer.analyze(SpectrumCapture.SAMPLE_RATE, gain)
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
                val wait = if (idle > IDLE_FRAMES) IDLE_MS else frameMs - (System.nanoTime() - started) / 1_000_000
                delay(wait.coerceAtLeast(4))
            }
        }
    }
    val pixel = !Look.smooth
    Canvas(modifier) {
        frame
        synchronized(levels) {
            when (style.forUi(ThemeState.style)) {
                VisualizerStyle.SEGMENTS -> segments(levels, C.Blue, C.Sand, C.Border)
                VisualizerStyle.RING -> ring(levels, C.Blue, C.Sand, pixel)
                VisualizerStyle.SCOPE -> scope(wave, C.Blue, pixel)
                VisualizerStyle.MOSAIC -> mosaic(levels, analyzer.bass, C.Blue, C.Sand, C.SurfaceHigh)
                VisualizerStyle.BARS -> bars(levels, C.Blue, C.Sand)
                VisualizerStyle.WAVE -> smoothWave(levels, C.Blue, C.Sand)
                VisualizerStyle.LINES -> hairLines(levels, peaks, C.Text, C.Blue, C.Border, Look.line.toPx())
                VisualizerStyle.ORBIT -> hairOrbit(levels, analyzer.bass, frame, C.Text, C.Blue, C.Blue, C.Border, Look.line.toPx())
                VisualizerStyle.TERRAIN -> hairTerrain(history, historyHead, C.Text, C.Blue, C.Background, Look.line.toPx())
            }
        }
    }
}

/** Столбики из квадратных клеток; верхняя горящая — песочная. */
internal fun DrawScope.segments(levels: FloatArray, lit: Color, peak: Color, dim: Color) {
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
internal fun DrawScope.ring(levels: FloatArray, lit: Color, peak: Color, pixel: Boolean) {
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
internal fun DrawScope.scope(wave: FloatArray, color: Color, pixel: Boolean) {
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
internal fun DrawScope.mosaic(levels: FloatArray, bass: Float, lit: Color, peak: Color, dim: Color) {
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
internal fun DrawScope.bars(levels: FloatArray, lit: Color, peak: Color) {
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
internal fun DrawScope.smoothWave(levels: FloatArray, lit: Color, peak: Color) {
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
internal fun DrawScope.hairLines(levels: FloatArray, peaks: FloatArray, line: Color, accent: Color, grid: Color, w: Float) {
    val slot = size.width / levels.size
    val base = size.height * 0.58f
    val room = base - size.height * 0.06f
    drawLine(grid, Offset(0f, base), Offset(size.width, base), w)
    levels.forEachIndexed { i, v ->
        val x = i * slot + slot / 2f
        val h = (room * v).coerceAtLeast(w * 2f)
        drawLine(line.copy(alpha = 0.9f), Offset(x, base), Offset(x, base - h), w * 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(line.copy(alpha = 0.22f), Offset(x, base + w * 3f), Offset(x, base + w * 3f + h * 0.45f), w * 1.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val py = base - (room * peaks[i]).coerceAtLeast(w * 2f) - 5.dp.toPx()
        drawCircle(accent, w * 1.6f, Offset(x, py))
    }
}

/** Hairline: «орбита» — три кольца дышат от баса, по окружности лучи спектра и медленно бегущая метка. */
internal fun DrawScope.hairOrbit(levels: FloatArray, bass: Float, frame: Long, line: Color, accent: Color, progress: Color, grid: Color, w: Float) {
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
    val ma = frame * 0.012f
    drawCircle(progress, w * 2.4f, c + Offset(cos(ma), sin(ma)) * (base * 1.68f))
    drawCircle(grid, base * 0.5f, c, style = Stroke(w))
}

/** Hairline: «рельеф» — прошлые спектры стопкой линий; ближние закрывают дальние, как на обложке Unknown Pleasures. */
internal fun DrawScope.hairTerrain(history: Array<FloatArray>, head: Long, line: Color, accent: Color, ground: Color, w: Float) {
    val rows = history.size
    val top = size.height * 0.16f
    val bottom = size.height * 0.92f
    val step = (bottom - top) / rows
    val amp = step * 3.4f
    val points = 46
    val padX = size.width * 0.08f
    val width = size.width - padX * 2f
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
