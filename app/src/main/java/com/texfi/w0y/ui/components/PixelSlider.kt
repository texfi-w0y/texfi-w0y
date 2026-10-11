package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.isSmooth
import com.texfi.w0y.ui.theme.styleTokens
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt

/** Квадратный ползунок: плоская дорожка и квадратный бегунок, без Material-теней. */
@Composable
fun PixelSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalW0yColors.current
    val density = LocalDensity.current
    val thumb = 18.dp
    val thumbPx = with(density) { thumb.toPx() }
    var widthPx by remember { mutableStateOf(1) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: value
    val fraction = ((shown - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    fun toValue(x: Float): Float {
        val f = ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)
        val raw = range.start + f * (range.endInclusive - range.start)
        return ((raw / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                detectTapGestures { onValueChange(toValue(it.x)) }
            }.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { drag = toValue(it.x) },
                    onDragEnd = {
                        drag?.let(onValueChange)
                        drag = null
                    },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    change.consume()
                    drag = toValue(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (com.texfi.w0y.ui.theme.isHair) {
            HairSliderVisual(fraction, widthPx, thumbPx, dragging = drag != null, color = colors.accent)
        } else if (isSmooth) {
            // Плавный: тонкая скруглённая дорожка, пройденная часть акцентом, круглый бегунок.
            val tokens = styleTokens
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(tokens.switchTrack)
                    .background(colors.surfaceHigh),
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                    .height(6.dp)
                    .clip(tokens.switchTrack)
                    .background(colors.accent),
            )
            Box(
                Modifier
                    .offset { IntOffset(((widthPx - thumbPx) * fraction).roundToInt(), 0) }
                    .size(thumb)
                    .clip(tokens.sliderThumb)
                    .background(colors.accent),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(colors.surface)
                    .border(2.dp, colors.border),
            )
            Box(
                Modifier
                    .offset { IntOffset(((widthPx - thumbPx) * fraction).roundToInt(), 0) }
                    .size(thumb)
                    .background(colors.accent)
                    .border(2.dp, colors.border),
            )
        }
    }
}
