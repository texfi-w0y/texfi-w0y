package com.texfi.w0y.ui.shell

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.components.Buzz
import com.texfi.w0y.ui.components.W0yMotion
import com.texfi.w0y.ui.components.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val OpenSpring = tween<Float>(W0yMotion.SLOW_MS, easing = W0yMotion.StepWideBack)
private val CloseSpring = tween<Float>(W0yMotion.FAST_MS, easing = W0yMotion.StepWide)

/** Hairline: плавная пружина вместо ступеней — шторка тянется за пальцем и мягко садится. */
private val HairOpen = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.82f, stiffness = 260f)
private val HairClose = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.92f, stiffness = 340f)

/**
 * Полноэкранный плеер как шторка над мини-плеером.
 *
 * Открытие и закрытие идут одной пружиной: плеер вырастает из нижней
 * кромки (где стоит мини-плеер) и в неё же уходит, слегка сжимаясь. Шторку
 * можно тянуть вниз с любого места, где список уже прокручен к началу, —
 * она идёт за пальцем, а на отпускании либо закрывается, либо
 * возвращается пружиной.
 */
@Composable
fun PlayerSheet(
    expanded: Boolean,
    onCollapse: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = rememberHaptics()
    val hair = com.texfi.w0y.ui.theme.isHair
    val moves = com.texfi.w0y.ui.theme.hairLook.motion.moves
    val scope = rememberCoroutineScope()
    // Раскрытость живёт в общем держателе: по ней летит обложка между плеером и мини-плеером.
    val flight = com.texfi.w0y.ui.components.LocalCoverFlight.current ?: remember { com.texfi.w0y.ui.components.CoverFlight() }
    remember(flight) { if (flight.openness == 0f && expanded) flight.openness = 1f; flight }
    var openness by flight::openness
    var job by remember { mutableStateOf<Job?>(null) }
    var firstRun by remember { mutableStateOf(true) }
    val travelHolder = remember { floatArrayOf(1f) }
    val visible by remember { derivedStateOf { openness > 0.001f } }

    // Актуальное состояние для жестов: onCollapse() не меняет `expanded`, если оно уже false
    // (бросок во время закрытия), и тогда шторка застревала на середине чёрным полотном.
    val expandedNow by androidx.compose.runtime.rememberUpdatedState(expanded)

    var settleTarget by remember { mutableFloatStateOf(-1f) }

    fun settle(target: Float, velocityY: Float = 0f) {
        settleTarget = target
        job?.cancel()
        job =
            scope.launch {
                // Скорость пальца переходит в движение шторки: закрывается с той же скоростью, с какой смахнули.
                val v = if (travelHolder[0] > 1f) -velocityY / travelHolder[0] else 0f
                animate(openness, target, initialVelocity = v.coerceIn(-12f, 12f), animationSpec = if (hair && moves) { if (target > 0.5f) HairOpen else HairClose } else if (target > 0.5f) OpenSpring else CloseSpring) { value, _ ->
                    openness = value
                }
            }
    }

    LaunchedEffect(expanded) {
        if (firstRun) {
            firstRun = false
            return@LaunchedEffect
        }
        val want = if (expanded) 1f else 0f
        // Уже едем туда же (смахнули жестом со скоростью) — не перебиваем.
        if (!(job?.isActive == true && settleTarget == want)) settle(want)
    }

    val connection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val dragged = (1f - openness) * travelHolder[0]
                    if (dragged > 0f && available.y < 0f) {
                        val next = (dragged + available.y).coerceAtLeast(0f)
                        job?.cancel()
                        openness = 1f - next / travelHolder[0]
                        return Offset(0f, next - dragged)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                    job?.cancel()
                    val next = ((1f - openness) * travelHolder[0] + available.y).coerceAtMost(travelHolder[0])
                    openness = 1f - next / travelHolder[0]
                    return Offset(0f, available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val dragged = 1f - openness
                    if (dragged <= 0f) return Velocity.Zero
                    if (dragged > 0.22f || available.y > 1600f) {
                        onCollapse()
                        // Даже если плеер уже «закрыт» по состоянию — шторка садится вниз сама.
                        settle(0f, available.y)
                    } else {
                        settle(1f)
                    }
                    return available
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    // Палец отпущен без броска: шторка не должна остаться на полпути.
                    if (openness in 0.001f..0.999f && job?.isActive != true) settle(if (expandedNow) 1f else 0f)
                    return Velocity.Zero
                }
            }
        }

    if (!visible && !expanded) return

    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(connection)
            .graphicsLayer {
                val travel = size.height.coerceAtLeast(1f) * 0.92f
                travelHolder[0] = travel
                val closed = 1f - openness
                translationY = closed * travel
                flight.curtainW = size.width
                flight.curtainH = size.height
                // Hairline: при тяге плеер сжимается к мини-плееру и скругляется, а не просто едет вниз.
                val scale = 1f - (if (hair) 0.16f else 0.1f) * closed.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
                flight.curtainTy = translationY
                flight.curtainScale = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
                alpha = if (hair) (1f - closed * 0.9f).coerceIn(0f, 1f).let { if (closed < 0.3f) 1f else it } else (openness * 2.4f).coerceIn(0f, 1f)
                clip = true
                shape = RoundedCornerShape(closed.coerceIn(0f, 1f) * (if (hair) 36.dp else 20.dp).toPx())
            },
    ) {
        content()
    }
}
