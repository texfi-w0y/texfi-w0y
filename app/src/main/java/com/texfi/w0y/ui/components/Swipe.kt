package com.texfi.w0y.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Строка со смахиванием: вправо — одно действие, влево — другое.
 *
 * За строкой, пока её тянут, проступает цветная плашка с названием
 * действия; когда палец прошёл порог, короткий отклик говорит «отпускай».
 * Возврат идёт ступенями — тем же языком движения, что и остальное.
 * Смещение читается только при отрисовке: строка внутри не пересобирается.
 */
@Composable
fun SwipeRow(
    modifier: Modifier = Modifier,
    onSwipeRight: (() -> Unit)? = null,
    rightLabel: String = "",
    onSwipeLeft: (() -> Unit)? = null,
    leftLabel: String = "",
    backdrop: Color? = null,
    content: @Composable () -> Unit,
) {
    if (onSwipeRight == null && onSwipeLeft == null) {
        Box(modifier) { content() }
        return
    }
    val colors = LocalW0yColors.current
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { THRESHOLD.toPx() }
    var shift by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableFloatStateOf(0f) }

    Box(
        modifier.pointerInput(onSwipeRight, onSwipeLeft) {
            detectHorizontalDragGestures(
                onDragStart = { armed = 0f },
                onHorizontalDrag = { change, delta ->
                    val next = shift + delta * DRAG_RESISTANCE
                    val allowed =
                        when {
                            next > 0f && onSwipeRight == null -> 0f
                            next < 0f && onSwipeLeft == null -> 0f
                            else -> next.coerceIn(-thresholdPx * MAX_PULL, thresholdPx * MAX_PULL)
                        }
                    if (allowed != shift) change.consume()
                    val crossed = abs(allowed) >= thresholdPx
                    if (crossed && armed == 0f) haptic(Buzz.SELECT)
                    armed = if (crossed) 1f else 0f
                    shift = allowed
                },
                onDragEnd = {
                    val from = shift
                    val fire = abs(from) >= thresholdPx
                    if (fire) {
                        if (from > 0f) onSwipeRight?.invoke() else onSwipeLeft?.invoke()
                    }
                    scope.launch {
                        animate(from, 0f, animationSpec = tween(W0yMotion.FAST_MS, easing = W0yMotion.Step)) { v, _ ->
                            shift = v
                        }
                    }
                },
                onDragCancel = {
                    val from = shift
                    scope.launch {
                        animate(from, 0f, animationSpec = tween(W0yMotion.FAST_MS, easing = W0yMotion.Step)) { v, _ ->
                            shift = v
                        }
                    }
                },
            )
        },
    ) {
        Reveal(rightLabel, Alignment.CenterStart, colors.accent, colors.background) { shift > 0f }
        Reveal(leftLabel, Alignment.CenterEnd, colors.secondary, colors.background) { shift < 0f }
        // Подложка рисуется только пока строку тянут: в покое строки
        // прозрачные, и фон экосистемы виден между ними, как и раньше.
        val cover = backdrop ?: colors.background
        Box(
            Modifier
                .graphicsLayer { translationX = shift }
                .drawBehind { if (shift != 0f) drawRect(cover) },
        ) { content() }
    }
}

@Composable
private fun BoxScope.Reveal(
    label: String,
    alignment: Alignment,
    fill: Color,
    text: Color,
    visible: () -> Boolean,
) {
    Box(
        // matchParentSize, а не fillMaxSize: подложка повторяет размер строки
        // и сама его не задаёт, иначе строка растягивалась бы на весь экран.
        Modifier
            .matchParentSize()
            .graphicsLayer { alpha = if (visible()) 1f else 0f }
            .background(if (com.texfi.w0y.ui.theme.isHair) Color.Transparent else fill)
            .padding(horizontal = 16.dp),
        contentAlignment = alignment,
    ) {
        // Hairline: без цветного блока — только подпись действия акцентом.
        Text(text = label, style = PixelSectionLabel, color = if (com.texfi.w0y.ui.theme.isHair) fill else text)
    }
}

private val THRESHOLD = 72.dp
private const val DRAG_RESISTANCE = 0.85f
private const val MAX_PULL = 1.4f
