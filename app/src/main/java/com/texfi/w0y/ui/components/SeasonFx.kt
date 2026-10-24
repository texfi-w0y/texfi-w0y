package com.texfi.w0y.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.texfi.w0y.data.SeasonFx
import com.texfi.w0y.data.SeasonMode
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.MotionLevel
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Праздник на фоне приложения: зимой медленно идёт мелкий снег, на Хэллоуин по низу стелется
 * туман, поднимаются угольки, изредка пролетает летучая мышь и в темноте моргают глаза.
 *
 * Лежит между фоном и экранами: видно в промежутках, текст не закрывает. Всё считается от
 * одного числа — времени, — частицы не хранят состояния и не создают объектов на кадр: снег
 * рисуется тремя вызовами drawPoints на весь экран. Пока экран не виден (плеер раскрыт, другое
 * приложение), часы стоят. На лёгком уровне частиц меньше и кадр через раз, без анимаций —
 * неподвижная картинка.
 */
@Composable
fun SeasonLayer(mode: SeasonMode, fx: SeasonFx, active: Boolean, modifier: Modifier = Modifier) {
    val season = seasonOf(mode)
    if (season == Season.NONE || fx == SeasonFx.OFF) return
    val motion = hairLook.motion
    val light = fx == SeasonFx.LIGHT || motion == MotionLevel.LITE
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateAsState()
    val running = active && motion != MotionLevel.OFF && resumed.isAtLeast(Lifecycle.State.RESUMED)
    var timeMs by remember { mutableLongStateOf(STILL_MS) }
    LaunchedEffect(running, light) {
        if (!running) return@LaunchedEffect
        var last = 0L
        var frame = 0
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                val ms = now / 1_000_000L
                if (last != 0L) {
                    frame++
                    // Лёгкий уровень: кадр через раз — снег медленный, глазу разницы нет, батарее есть.
                    if (!light || frame % 2 == 0) timeMs += (ms - last).coerceAtMost(64L)
                }
                last = ms
            }
        }
    }
    val colors = LocalW0yColors.current
    val dark = colors.background.red + colors.background.green + colors.background.blue < 1.5f
    when (season) {
        Season.WINTER -> {
            val flakes = remember(light) { Snow(if (light) SNOW_LIGHT else SNOW_FULL) }
            val tone = if (dark) Color.White else Color(0xFF8EA4C8)
            Canvas(modifier) { flakes.draw(this, timeMs / 1000f, tone) }
        }
        Season.HALLOWEEN -> {
            val night = remember(light) { Night(if (light) EMBERS_LIGHT else EMBERS_FULL) }
            Canvas(modifier) { night.draw(this, timeMs / 1000f, dark, colors.text) }
        }
        Season.NONE -> Unit
    }
}

/** Снежинки: место, скорость, размер, раскачка — всё задано при создании, на кадр ничего не выделяется. */
private class Snow(count: Int) {
    private val rnd = Random(2024)
    private val x0 = FloatArray(count) { rnd.nextFloat() }
    private val y0 = FloatArray(count) { rnd.nextFloat() }
    private val layer = IntArray(count) { i -> (i * 3) / count } // 0 — дальний, 2 — ближний
    private val speed = FloatArray(count) { i -> (8f + layer[i] * 9f) * (0.8f + rnd.nextFloat() * 0.4f) }
    private val sway = FloatArray(count) { 2f + rnd.nextFloat() * 8f }
    private val freq = FloatArray(count) { 0.25f + rnd.nextFloat() * 0.5f }
    private val phase = FloatArray(count) { rnd.nextFloat() * 2f * PI.toFloat() }
    private val points = Array(3) { l -> FloatArray(layer.count { it == l } * 2) }
    private val fill = IntArray(3)
    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    fun draw(scope: DrawScope, t: Float, tone: Color) = with(scope) {
        val dp = density
        val h = size.height
        val w = size.width
        val margin = 8f * dp
        fill.fill(0)
        // Лёгкий ветер: весь снег чуть сносит то влево, то вправо за минуту.
        val wind = sin(t * 0.07f) * 14f * dp
        for (i in x0.indices) {
            val l = layer[i]
            val travel = (y0[i] * (h + margin) + speed[i] * dp * t) % (h + margin)
            val y = travel - margin / 2f
            val x = (x0[i] * w + sin(t * freq[i] + phase[i]) * sway[i] * dp + wind * (0.4f + 0.3f * l)).let { ((it % w) + w) % w }
            val arr = points[l]
            arr[fill[l]++] = x
            arr[fill[l]++] = y
        }
        drawIntoCanvas { canvas ->
            for (l in 0..2) {
                paint.strokeWidth = (0.9f + l * 0.7f) * dp
                paint.color = tone.copy(alpha = 0.28f + l * 0.22f).toArgb()
                canvas.nativeCanvas.drawPoints(points[l], 0, fill[l], paint)
            }
        }
    }
}

/**
 * Хэллоуин: редкие мелкие угольки поднимаются вверх, раз в несколько секунд через верх экрана
 * пролетает летучая мышь (тонкий контур, крылья машут), а у края зажигаются и моргают два глаза.
 * Без тумана, свечений и ореолов — только точки и линии.
 */
private class Night(count: Int) {
    private val rnd = Random(1031)
    private val x0 = FloatArray(count) { rnd.nextFloat() }
    private val y0 = FloatArray(count) { rnd.nextFloat() }
    private val rise = FloatArray(count) { 6f + rnd.nextFloat() * 14f }
    private val phase = FloatArray(count) { rnd.nextFloat() * 2f * PI.toFloat() }
    private val points = FloatArray(count * 2)
    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val bat = Path()

    fun draw(scope: DrawScope, t: Float, dark: Boolean, ink: Color) = with(scope) {
        val dp = density
        val w = size.width
        val h = size.height
        // Угольки: поднимаются ровно, без мерцания.
        var n = 0
        for (i in x0.indices) {
            val travel = (y0[i] * h + rise[i] * dp * t) % h
            val y = h - travel
            val x = x0[i] * w + sin(t * 0.6f + phase[i]) * 6f * dp
            points[n++] = x
            points[n++] = y
        }
        drawIntoCanvas { canvas ->
            paint.strokeWidth = 1.6f * dp
            paint.color = Color(0xFFE07A3A).copy(alpha = 0.45f).toArgb()
            canvas.nativeCanvas.drawPoints(points, 0, n, paint)
        }
        drawBat(t, w, h, dp, ink)
        drawEyes(t, w, h, dp)
    }

    /** Летучая мышь раз в 11 секунд: 4 секунды полёта по волне через верхнюю треть. */
    private fun DrawScope.drawBat(t: Float, w: Float, h: Float, dp: Float, ink: Color) {
        val period = 11f
        val k = floor(t / period).toInt()
        val local = t - k * period
        val flight = 4f
        if (local > flight) return
        val r = Random(k * 7919)
        val leftToRight = r.nextBoolean()
        val p = local / flight
        val baseY = h * (0.08f + r.nextFloat() * 0.22f)
        val x = if (leftToRight) -40f * dp + (w + 80f * dp) * p else w + 40f * dp - (w + 80f * dp) * p
        val y = baseY + sin(p * PI.toFloat() * 3f) * 18f * dp
        val s = (14f + r.nextFloat() * 8f) * dp
        val flap = sin(t * 18f) // взмах крыльев
        bat.rewind()
        // Тело и два крыла с зубцами — тонкий контур, как все значки Hairline.
        val wy = -flap * 0.55f * s
        bat.moveTo(x, y)
        bat.quadraticTo(x - 0.45f * s, y + wy - 0.2f * s, x - s, y + wy)
        bat.quadraticTo(x - 0.8f * s, y + wy * 0.4f + 0.18f * s, x - 0.62f * s, y + 0.22f * s)
        bat.quadraticTo(x - 0.45f * s, y + 0.12f * s, x - 0.3f * s, y + 0.3f * s)
        bat.quadraticTo(x - 0.15f * s, y + 0.15f * s, x, y + 0.28f * s)
        bat.quadraticTo(x + 0.15f * s, y + 0.15f * s, x + 0.3f * s, y + 0.3f * s)
        bat.quadraticTo(x + 0.45f * s, y + 0.12f * s, x + 0.62f * s, y + 0.22f * s)
        bat.quadraticTo(x + 0.8f * s, y + wy * 0.4f + 0.18f * s, x + s, y + wy)
        bat.quadraticTo(x + 0.45f * s, y + wy - 0.2f * s, x, y)
        bat.close()
        val fade = (minOf(p, 1f - p) * 6f).coerceIn(0f, 1f)
        drawPath(bat, ink.copy(alpha = 0.55f * fade), style = Stroke(1.2f * dp))
        // Ушки.
        drawLine(ink.copy(alpha = 0.55f * fade), Offset(x - 0.08f * s, y), Offset(x - 0.12f * s, y - 0.16f * s), 1.2f * dp, StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.55f * fade), Offset(x + 0.08f * s, y), Offset(x + 0.12f * s, y - 0.16f * s), 1.2f * dp, StrokeCap.Round)
    }

    /** Глаза в темноте: раз в 14 секунд в случайном месте у края зажигаются, моргают и гаснут. */
    private fun DrawScope.drawEyes(t: Float, w: Float, h: Float, dp: Float) {
        val period = 14f
        val k = floor(t / period).toInt()
        val local = t - k * period - 5f
        if (local < 0f || local > 3f) return
        val r = Random(k * 104729)
        val side = r.nextBoolean()
        val x = if (side) w * (0.06f + r.nextFloat() * 0.12f) else w * (0.82f + r.nextFloat() * 0.12f)
        val y = h * (0.35f + r.nextFloat() * 0.4f)
        val on = (minOf(local, 3f - local) * 3f).coerceIn(0f, 1f)
        // Моргание: веки смыкаются на 0.12 с посередине.
        val blink = if (abs(local - 1.6f) < 0.06f) 0.15f else 1f
        val tone = Color(0xFFFF7A1A)
        for (dx in listOf(-5f, 5f)) {
            val c = Offset(x + dx * dp, y)
            drawOval(tone.copy(alpha = 0.9f * on), Offset(c.x - 2.2f * dp, c.y - 1.6f * dp * blink), androidx.compose.ui.geometry.Size(4.4f * dp, 3.2f * dp * blink))
        }
    }
}

private const val SNOW_FULL = 150
private const val SNOW_LIGHT = 60
private const val EMBERS_FULL = 36
private const val EMBERS_LIGHT = 16

/** Без движения кадр берётся не с нуля: снег уже рассыпан по экрану, а не стоит строкой наверху. */
private const val STILL_MS = 37_000L
