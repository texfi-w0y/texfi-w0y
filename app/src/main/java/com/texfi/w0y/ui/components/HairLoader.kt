package com.texfi.w0y.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Загрузчик Hairline: кольцо из тонких делений и яркая «комета», которая
 * бежит по нему с шлейфом. Угол читается при рисовании — перекомпоновок нет.
 * Без анимаций (режим «Без движения») — неподвижное кольцо с головой наверху.
 */
@Composable
fun HairLoader(modifier: Modifier = Modifier, color: Color? = null) {
    val colors = LocalW0yColors.current
    val tone = color ?: Contrast.ensure(colors.accent, colors.background)
    val moves = hairLook.motion.moves
    val spin = rememberInfiniteTransition(label = "hairLoader").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOADER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "loaderAngle",
    )
    val line = hairLook.line
    Canvas(modifier.size(40.dp)) {
        val head = if (moves) spin.value else 0f
        val r = size.minDimension / 2f - 2.dp.toPx()
        val inner = r - 7.dp.toPx()
        val w = line.toPx() * 1.4f
        repeat(TICKS) { i ->
            val f = i.toFloat() / TICKS
            // Расстояние позади головы по ходу вращения: 0 — сама голова.
            val behind = ((head - f) % 1f + 1f) % 1f
            val a = (1f - behind * 2.2f).coerceAtLeast(0.14f)
            val ang = (f * 2f * PI - PI / 2).toFloat()
            val c = center
            drawLine(
                color = (if (behind < 0.06f) tone else colors.text).copy(alpha = a),
                start = Offset(c.x + cos(ang) * inner, c.y + sin(ang) * inner),
                end = Offset(c.x + cos(ang) * r, c.y + sin(ang) * r),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Заставка Hairline: от центра расходится тонкая линия, по ней одной линией рисуется «W»,
 * замыкается кольцо-ноль и проявляется подпись. Потом знак уходит на зрителя, а в фоне
 * раскрывается круглое окно с тонким кольцом по краю — сквозь него уже видно приложение.
 * Заставка не держит касания и не ждёт сети: экран под ней живой с первого кадра.
 * Без анимаций заставки нет вовсе.
 */
@Composable
fun HairIntro(onFinished: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val tone = Contrast.ensure(colors.accent, colors.background)
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!look.motion.draws) {
            onFinished()
            return@LaunchedEffect
        }
        t.animateTo(1f, tween(INTRO_MS, easing = LinearEasing))
        onFinished()
    }
    // Доли общей шкалы: каждая часть со своей кривой Hairline.
    fun part(from: Float, to: Float): Float = HairTokens.EaseOut.transform(((t.value - from) / (to - from)).coerceIn(0f, 1f))
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawBehind {
                // Фон с круглым окном: окно растёт из центра до углов экрана, по кромке — кольцо.
                drawRect(colors.background)
                val open = part(0.62f, 1f)
                if (open > 0f) {
                    val maxR = kotlin.math.hypot(size.width / 2f, size.height / 2f)
                    val r = maxR * open
                    drawCircle(Color.Black, r, center, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                    drawCircle(tone.copy(alpha = (1f - open) * 0.9f), r, center, style = Stroke(1.5.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Знак уходит на зрителя и гаснет, пока раскрывается окно.
                    val out = part(0.6f, 0.92f)
                    val s = 1f + 0.22f * out
                    scaleX = s
                    scaleY = s
                    alpha = 1f - out
                },
        ) {
            val side = 180.dp.toPx()
            val k = side / 108f
            val ox = center.x - side / 2f
            val oy = center.y - side / 2f
            // Горизонт: линия расходится от центра и гаснет, когда знак готов.
            val line = part(0f, 0.22f)
            val lineFade = 1f - part(0.4f, 0.6f)
            if (line > 0f && lineFade > 0f) {
                val half = size.width * 0.32f * line
                val y = oy + 54f * k
                drawLine(colors.text.copy(alpha = 0.35f * lineFade), Offset(center.x - half, y), Offset(center.x + half, y), 1.dp.toPx())
            }
            val wPath = Path().apply {
                moveTo(ox + 28f * k, oy + 40f * k)
                lineTo(ox + 41f * k, oy + 74f * k)
                lineTo(ox + 54f * k, oy + 50f * k)
                lineTo(ox + 67f * k, oy + 74f * k)
                lineTo(ox + 80f * k, oy + 40f * k)
            }
            val seg = Path()
            val m = PathMeasure().also { it.setPath(wPath, false) }
            m.getSegment(0f, m.length * part(0.08f, 0.42f), seg, true)
            drawPath(seg, colors.text, style = Stroke(7f * k * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val circle = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(ox + 54f * k - 7.5f * k, oy + 33f * k - 7.5f * k), androidx.compose.ui.geometry.Size(15f * k, 15f * k)))
            }
            val cm = PathMeasure().also { it.setPath(circle, false) }
            val cp = Path()
            cm.getSegment(0f, cm.length * part(0.3f, 0.52f), cp, true)
            drawPath(cp, tone, style = Stroke(3.6f * k * 0.5f, cap = StrokeCap.Round))
            // Засечки по углам знака — как на чертёжном листе; сходятся к нему.
            val ticks = part(0.18f, 0.48f)
            if (ticks > 0f) {
                val pad = 14.dp.toPx() * (2f - ticks)
                val len = 10.dp.toPx() * ticks
                val a = colors.text.copy(alpha = 0.5f * ticks)
                val l = ox + 18f * k - pad
                val r = ox + 90f * k + pad
                val tp = oy + 18f * k - pad
                val bt = oy + 90f * k + pad
                listOf(Offset(l, tp) to Offset(1f, 1f), Offset(r, tp) to Offset(-1f, 1f), Offset(l, bt) to Offset(1f, -1f), Offset(r, bt) to Offset(-1f, -1f)).forEach { (c, d) ->
                    drawLine(a, c, c + Offset(len * d.x, 0f), 1.dp.toPx())
                    drawLine(a, c, c + Offset(0f, len * d.y), 1.dp.toPx())
                }
            }
        }
        // Подпись под знаком: моно, с разрядкой, проявляется снизу.
        val label = part(0.36f, 0.56f)
        androidx.compose.material3.Text(
            text = "W0Y",
            style = com.texfi.w0y.ui.theme.HairLabelStyle.copy(letterSpacing = 6.sp),
            color = colors.textMuted,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(top = 150.dp)
                    .graphicsLayer {
                        alpha = label * (1f - part(0.6f, 0.8f))
                        translationY = (1f - label) * 10.dp.toPx()
                    },
        )
    }
}

private const val LOADER_MS = 1_100
private const val TICKS = 28
private const val INTRO_MS = 1_250
