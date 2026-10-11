package com.texfi.w0y.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlin.math.floor
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * Обновление жестом: потянул содержимое от самого верха и отпустил.
 *
 * Вместо круглого спиннера — сегментный индикатор из ролика: пока тянешь,
 * квадраты зажигаются по одному по длине жеста; на пороге он «щёлкает»
 * (отклик и смена цвета на песочный); пока идёт загрузка, свет бегает
 * по сегментам; по завершении короткая вспышка и возврат ступенями.
 *
 * Интерфейс не блокируется: жест живёт только на вложенной прокрутке, а
 * во время обновления список листается и нажимается как обычно. Содержимое
 * не очищается — старые данные остаются, пока приходят новые.
 */
@Composable
fun PixelPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalW0yColors.current
    val haptic = rememberHaptics()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { THRESHOLD.toPx() }
    val hair = com.texfi.w0y.ui.theme.isHair
    val holdPx = with(density) { (if (hair) HOLD_HAIR else HOLD).toPx() }
    val maxPx = thresholdPx * MAX_PULL
    var pull by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableFloatStateOf(0f) }
    var flash by remember { mutableFloatStateOf(0f) }
    val currentRefresh by rememberUpdatedState(onRefresh)
    val isRefreshing by rememberUpdatedState(refreshing)

    fun settle(target: Float) {
        scope.launch {
            animate(
                pull,
                target,
                animationSpec = tween(W0yMotion.FAST_MS, easing = W0yMotion.StepWide),
            ) { v, _ -> pull = v }
        }
    }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            animate(pull, holdPx, animationSpec = tween(W0yMotion.FAST_MS, easing = W0yMotion.StepWide)) { v, _ ->
                pull = v
            }
        } else if (pull > 0f) {
            // Конец загрузки: вспышка всей полосой, потом возврат на место.
            flash = 1f
            animate(1f, 0f, animationSpec = tween(FLASH_MS, easing = SteppedEasing(4))) { v, _ -> flash = v }
            animate(pull, 0f, animationSpec = tween(W0yMotion.FAST_MS, easing = W0yMotion.StepWide)) { v, _ ->
                pull = v
            }
        }
    }

    val connection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || isRefreshing) return Offset.Zero
                    if (pull > 0f && available.y < 0f) {
                        val take = maxOf(available.y, -pull)
                        pull += take
                        return Offset(0f, take)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || isRefreshing || available.y <= 0f) return Offset.Zero
                    val next = min(pull + available.y * PULL_RESISTANCE, maxPx)
                    val crossed = next >= thresholdPx
                    if (crossed && armed == 0f) haptic(Buzz.SELECT)
                    armed = if (crossed) 1f else 0f
                    pull = next
                    return Offset(0f, available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (pull <= 0f || isRefreshing) return Velocity.Zero
                    if (pull >= thresholdPx) {
                        haptic(Buzz.TAP)
                        currentRefresh()
                        // Держим полосу, пока не придёт состояние «грузится».
                        settle(holdPx)
                    } else {
                        settle(0f)
                    }
                    armed = 0f
                    return available
                }
            }
        }

    Box(modifier.nestedScroll(connection)) {
        val shift = if (hair) CONTENT_SHIFT_HAIR else CONTENT_SHIFT
        Box(Modifier.graphicsLayer { translationY = pull * shift }) { content() }
        val phase = rememberAnimationPhase(RUN_MS, RUN_FPS, enabled = refreshing)
        val lit = colors.accent
        val alt = colors.secondary
        val dim = colors.border
        if (hair) {
            // Hairline: кольцо делений — набирается пальцем, на пороге щёлкает,
            // при загрузке по нему бежит комета, на конце — круг-вспышка.
            val tone = com.texfi.w0y.ui.theme.Contrast.ensure(colors.accent, colors.background)
            val line = com.texfi.w0y.ui.theme.hairLook.line
            val text = colors.text
            Canvas(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(HAIR_ZONE)) {
                if (pull <= 0f && flash <= 0f) return@Canvas
                val c = Offset(size.width / 2f, (pull * CONTENT_SHIFT_HAIR / 2f).coerceAtLeast(0f))
                val r = 9.dp.toPx()
                val inner = r - 5.dp.toPx()
                val progress = (pull / thresholdPx).coerceIn(0f, 1f)
                val head = phase.floatValue / TWO_PI
                val spokes = HAIR_TICKS
                val lit = floor(progress * spokes + EPS).toInt()
                for (i in 0 until spokes) {
                    val f = i.toFloat() / spokes
                    val a = (f * TWO_PI - Math.PI.toFloat() / 2f)
                    val behind = ((head - f) % 1f + 1f) % 1f
                    val (col, alpha) =
                        when {
                            isRefreshing -> (if (behind < 0.08f) tone else text) to (1f - behind * 2.2f).coerceAtLeast(0.15f)
                            i < lit -> (if (progress >= 1f) tone else text) to 0.95f
                            else -> text to 0.16f
                        }
                    val d = Offset(kotlin.math.cos(a), kotlin.math.sin(a))
                    drawLine(col.copy(alpha = alpha), c + d * inner, c + d * r, line.toPx() * 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                if (flash > 0f) {
                    drawCircle(tone.copy(alpha = flash), r + (1f - flash) * 26.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(line.toPx()))
                }
            }
        }
        if (!hair) Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(BAR_HEIGHT),
        ) {
            val visible = pull > 0f || flash > 0f
            if (!visible) return@Canvas
            val count = SEGMENTS
            val gap = 3.dp.toPx()
            val width = (size.width - gap * (count - 1)) / count
            val progress = (pull / thresholdPx).coerceIn(0f, 1f)
            val pulling = !isRefreshing && flash == 0f
            val onCount = floor(progress * count + EPS).toInt()
            val runner = (phase.floatValue / TWO_PI * count).toInt() % count
            for (i in 0 until count) {
                val color =
                    when {
                        flash > 0f -> alt
                        isRefreshing -> if ((i - runner + count) % count < RUN_TAIL) alt else lit.copy(alpha = 0.35f)
                        pulling && i < onCount -> if (progress >= 1f) alt else lit
                        else -> dim
                    }
                drawRect(color, Offset(i * (width + gap), 0f), Size(width, size.height))
            }
        }
    }
}

private val THRESHOLD = 88.dp
private val HOLD = 28.dp
private val HOLD_HAIR = 56.dp
private val HAIR_ZONE = 96.dp
private const val CONTENT_SHIFT_HAIR = 0.5f
private const val HAIR_TICKS = 20
private val BAR_HEIGHT = 8.dp
private const val MAX_PULL = 1.35f
private const val PULL_RESISTANCE = 0.55f
private const val CONTENT_SHIFT = 0.35f
private const val SEGMENTS = 14
private const val EPS = 0.0001f
private const val FLASH_MS = 220
private const val RUN_MS = 900
private const val RUN_FPS = 30
private const val RUN_TAIL = 4
