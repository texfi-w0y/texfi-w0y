package com.texfi.w0y.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.MotionLevel
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.hypot
import kotlin.math.max

/**
 * Где палец последний раз коснулся экрана — в координатах корня. Отсюда раскрываются
 * экраны: плейлист растёт из плитки, по которой нажали, артист — из строки, настройки — из
 * шестерёнки. Отдельно у каждой плитки ничего не размечено: касание ловится один раз у корня
 * и не поглощается, кнопки получают его как обычно.
 */
@Stable
class TouchOrigin {
    var last: Offset? = null
        internal set
}

val LocalTouchOrigin = staticCompositionLocalOf<TouchOrigin?> { null }

/** Запоминает каждое новое касание; ничего не поглощает. Ставится на корень оболочки. */
fun Modifier.recordTouches(origin: TouchOrigin): Modifier =
    pointerInput(origin) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { origin.last = it.position }
            }
        }
    }

/** Положение элемента в корне: для перевода точки касания в его координаты. */
private class RootPos {
    var value = Offset.Zero
}

/**
 * Круглое окно, которое раскрывается из точки [origin] (координаты корня) на весь элемент,
 * с тонким кольцом по кромке. [progress] читается при рисовании: 0 — окно в точку, 1 — всё
 * видно и обрезки нет. Без точки окно растёт из верхней трети элемента.
 */
@Composable
fun Modifier.hairReveal(progress: () -> Float, origin: Offset?, ring: Color? = null): Modifier {
    val pos = remember { RootPos() }
    val colors = LocalW0yColors.current
    val tone = ring ?: Contrast.ensure(colors.accent, colors.background)
    return onGloballyPositioned { pos.value = it.positionInRoot() }
        .drawWithContent {
            val p = progress()
            if (p >= 0.999f) {
                drawContent()
                return@drawWithContent
            }
            if (p <= 0f) return@drawWithContent
            val o = origin?.let { it - pos.value }?.let { Offset(it.x.coerceIn(0f, size.width), it.y.coerceIn(0f, size.height)) }
                ?: Offset(size.width / 2f, size.height * 0.35f)
            val far = max(
                max(hypot(o.x, o.y), hypot(size.width - o.x, o.y)),
                max(hypot(o.x, size.height - o.y), hypot(size.width - o.x, size.height - o.y)),
            )
            val start = 18.dp.toPx()
            val r = start + (far - start) * p
            val path = Path().apply { addOval(Rect(o, r)) }
            clipPath(path) { this@drawWithContent.drawContent() }
            // Кольцо по кромке и короткий отсвет внутрь: окно «вычерчивается», а не просто растёт.
            val a = (1f - p).coerceIn(0f, 1f)
            drawCircle(tone.copy(alpha = 0.9f * a), r, o, style = Stroke(1.5.dp.toPx()))
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    0.86f to Color.Transparent,
                    1f to tone.copy(alpha = 0.18f * a),
                    center = o,
                    radius = r.coerceAtLeast(1f),
                ),
                r,
                o,
            )
        }
}

/** Ход раскрытия внутри [AnimatedVisibilityScope]: 0 до входа и после выхода, 1 — на месте. */
@Composable
fun AnimatedVisibilityScope.revealProgress(enterMs: Int = HairTokens.LONG_MS + 60, exitMs: Int = HairTokens.MID_MS + 40): androidx.compose.runtime.State<Float> {
    val level = hairLook.motion
    return transition.animateFloat(
        transitionSpec = {
            when {
                level == MotionLevel.OFF -> snap()
                targetState == EnterExitState.Visible -> tween(if (level == MotionLevel.FULL) enterMs else HairTokens.SHORT_MS, easing = HairTokens.EaseOut)
                else -> tween(if (level == MotionLevel.FULL) exitMs else HairTokens.SHORT_MS, easing = HairTokens.EaseOut)
            }
        },
        label = "reveal",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
}

/** Что было и что стало: нужно, чтобы понять, кто из двух экранов «глубже». */
private class NavMemo<T>(var current: T) {
    var from: T = current
    val origins = HashMap<T, Offset?>()
}

/**
 * Смена экранов Hairline внутри вкладки.
 *
 *  - вглубь (плейлист из «Моё», список из плитки): новый экран раскрывается кругом из точки
 *    касания, по кромке бежит тонкое кольцо; старый отступает вглубь — чуть уменьшается и гаснет;
 *  - назад: экран сворачивается в ту же точку, из которой открылся, а прежний выходит из глубины;
 *  - на том же уровне — мягкая смена с лёгким приближением.
 *
 * Лёгкий уровень анимаций — только проявление, без окна; без анимаций — сразу.
 */
@Composable
fun <T : Any> HairNavHost(
    target: T,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val touch = LocalTouchOrigin.current
    val level = hairLook.motion
    val memo = remember { NavMemo(target) }
    if (memo.current != target) {
        memo.from = memo.current
        memo.current = target
        if (depth(target) > depth(memo.from)) memo.origins[target] = touch?.last
    }
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            val deeper = depth(targetState) > depth(initialState)
            val back = depth(targetState) < depth(initialState)
            val long = if (level == MotionLevel.FULL) HairTokens.LONG_MS + 60 else HairTokens.SHORT_MS
            val mid = if (level == MotionLevel.FULL) HairTokens.MID_MS + 40 else HairTokens.SHORT_MS
            if (level == MotionLevel.OFF) {
                fadeIn(snap()).togetherWith(fadeOut(snap()))
            } else if (deeper) {
                // Новый экран рисуется окном поверх, старый уходит вглубь под ним.
                fadeIn(snap())
                    .togetherWith(fadeOut(tween(long, easing = HairTokens.EaseOut)) + scaleOut(tween(long, easing = HairTokens.EaseOut), targetScale = 0.93f))
                    .apply { targetContentZIndex = 1f }
            } else if (back) {
                // Прежний выходит из глубины, закрываемый сворачивается окном поверх него.
                (fadeIn(tween(long, easing = HairTokens.EaseOut)) + scaleIn(tween(long, easing = HairTokens.EaseOut), initialScale = 0.93f))
                    .togetherWith(fadeOut(tween(mid, delayMillis = mid / 2)))
                    .apply { targetContentZIndex = -1f }
            } else {
                (fadeIn(tween(long, easing = HairTokens.EaseOut)) + scaleIn(tween(long, easing = HairTokens.EaseOut), initialScale = 0.97f))
                    .togetherWith(fadeOut(tween(mid)))
            }
        },
        label = "hairNav",
    ) { route ->
        val other = if (route == memo.current) memo.from else memo.current
        val child = depth(route) > depth(other)
        if (child && level == MotionLevel.FULL) {
            val p by revealProgress()
            Box(Modifier.fillMaxSize().hairReveal({ p }, memo.origins[route])) { content(route) }
        } else {
            content(route)
        }
    }
}

/**
 * Смена вкладки в Hairline: экран проявляется с лёгким приближением и сдвигом в сторону
 * перехода, а по верхнему краю пробегает тонкая линия акцента — «прочерк», как перелистнули
 * лист. Экран стоит на месте с первого кадра и уже виден: ожидания нет. На первом показе
 * перехода нет — это запуск, а не смена.
 */
@Composable
fun Modifier.hairTabEnter(trigger: Any, direction: Int): Modifier {
    val level = hairLook.motion
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val progress = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    val dir = if (direction < 0) -1f else 1f
    LaunchedEffect(trigger) {
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        if (level == MotionLevel.OFF) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (level == MotionLevel.FULL) TAB_MS else HairTokens.SHORT_MS, easing = HairTokens.EaseOut))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = 0.3f + 0.7f * p
        if (level == MotionLevel.FULL) {
            val s = 0.975f + 0.025f * p
            scaleX = s
            scaleY = s
            translationX = (1f - p) * dir * 18.dp.toPx()
        }
    }.drawWithContent {
        drawContent()
        val p = progress.value
        if (p < 1f && level == MotionLevel.FULL) {
            // Голова линии идёт по ходу перехода, хвост тянется за ней и тает.
            val head = if (dir > 0) size.width * p else size.width * (1f - p)
            val tail = (size.width * 0.45f) * (1f - p * 0.5f)
            val from = if (dir > 0) (head - tail).coerceAtLeast(0f) else (head + tail).coerceAtMost(size.width)
            val a = (1f - p).coerceIn(0f, 1f)
            drawLine(
                Brush.horizontalGradient(
                    if (dir > 0) listOf(Color.Transparent, tone.copy(alpha = a)) else listOf(tone.copy(alpha = a), Color.Transparent),
                    startX = minOf(from, head),
                    endX = maxOf(from, head),
                ),
                Offset(from, 0f),
                Offset(head, 0f),
                1.5.dp.toPx(),
            )
        }
    }
}

private const val TAB_MS = 320
