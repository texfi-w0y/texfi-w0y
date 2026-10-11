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
 * Заставка Hairline: «W» и кольцо-ноль рисуются одной линией, потом
 * надпись проявляется и всё растворяется. Тот же знак, что на иконке.
 * Без анимаций заставки нет вовсе.
 */
@Composable
fun HairIntro(onFinished: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val tone = Contrast.ensure(colors.accent, colors.background)
    val w = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (!look.motion.draws) {
            onFinished()
            return@LaunchedEffect
        }
        w.animateTo(1f, tween(INTRO_W_MS, easing = HairTokens.EaseOut))
        ring.animateTo(1f, tween(INTRO_RING_MS, easing = HairTokens.EaseOut))
        kotlinx.coroutines.delay(INTRO_HOLD_MS)
        fade.animateTo(0f, tween(HairTokens.MID_MS))
        onFinished()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(colors.background), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val k = size.minDimension / 108f
            val stroke = 7f * k * 0.5f
            val wPath = Path().apply {
                moveTo(28f * k, 40f * k)
                lineTo(41f * k, 74f * k)
                lineTo(54f * k, 50f * k)
                lineTo(67f * k, 74f * k)
                lineTo(80f * k, 40f * k)
            }
            val part = Path()
            val m = PathMeasure().also { it.setPath(wPath, false) }
            m.getSegment(0f, m.length * w.value, part, true)
            drawPath(part, colors.text, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val circle = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(54f * k - 7.5f * k, 33f * k - 7.5f * k), androidx.compose.ui.geometry.Size(15f * k, 15f * k)))
            }
            val cm = PathMeasure().also { it.setPath(circle, false) }
            val cp = Path()
            cm.getSegment(0f, cm.length * ring.value, cp, true)
            drawPath(cp, tone, style = Stroke(3.6f * k * 0.5f, cap = StrokeCap.Round))
        }
    }
}

private const val LOADER_MS = 1_100
private const val TICKS = 28
private const val INTRO_W_MS = 650
private const val INTRO_RING_MS = 320
private const val INTRO_HOLD_MS = 260L
