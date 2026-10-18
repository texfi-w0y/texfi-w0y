package com.texfi.w0y.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Карта контуров загружается один раз и только если выбран фон «Контуры». */
private val topoImage: ImageBitmap? by lazy {
    runCatching {
        TopoAnchor::class.java.getResourceAsStream("/backdrop_topo.webp")?.use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
    }.getOrNull()
}

private object TopoAnchor

/**
 * Фон Hairline под всеми экранами: контуры, сетка, чёрный, свет обложки или переливы — как на телефоне.
 * Переливы двигаются только при полных анимациях.
 */
@Composable
fun HairBackdrop(app: AppState, modifier: Modifier = Modifier) {
    val mode = ThemeState.backdrop
    if (mode == BackdropMode.PURE) return
    val cover = rememberCoverColor(app.player.current?.thumbnailUrl)
    val tint by animateColorAsState(cover ?: C.Blue, tween(600))
    val coverOn = if (cover != null) 1f else 0f
    val phase = if (mode == BackdropMode.AURORA && Motion.full) {
        rememberInfiniteTransition(label = "aurora").animateFloat(0f, 1f, infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    } else {
        0.25f
    }
    val dark = !C.isLight
    val accent = C.Blue
    val grid = C.Text.copy(alpha = 0.04f)
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        when (mode) {
            BackdropMode.PURE -> Unit
            BackdropMode.TOPO -> {
                val image = topoImage
                if (image != null && dark) {
                    // Карта узкая и высокая: на широком окне кладём её плиткой, зеркаля соседние, чтобы не было швов.
                    val tw = image.width * 0.9f
                    val th = image.height * 0.9f
                    var j = 0
                    var y = 0f
                    while (y < h) {
                        var i = 0
                        var x = 0f
                        while (x < w) {
                            val fx = if (i % 2 == 1) -1f else 1f
                            val fy = if (j % 2 == 1) -1f else 1f
                            withTransform({
                                translate(x, y)
                                scale(fx, fy, Offset(tw / 2f, th / 2f))
                            }) { drawImage(image, dstSize = IntSize(tw.toInt(), th.toInt())) }
                            x += tw
                            i++
                        }
                        y += th
                        j++
                    }
                    drawRect(Color.Black.copy(alpha = 0.10f), Offset.Zero, Size(w, h))
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.25f), startY = h * 0.6f, endY = h), Offset.Zero, Size(w, h))
                } else {
                    grid(w, h, grid)
                }
            }
            BackdropMode.GRID -> {
                drawRect(Brush.verticalGradient(0f to accent.copy(alpha = 0.10f), 0.5f to Color.Transparent, startY = 0f, endY = h), Offset.Zero, Size(w, h))
                val a = coverOn * 0.30f
                if (a > 0.01f) drawRect(Brush.radialGradient(listOf(tint.copy(alpha = a), Color.Transparent), Offset(w * 0.9f, 0f), w * 0.8f), Offset.Zero, Size(w, h))
                grid(w, h, grid)
            }
            BackdropMode.COVER -> {
                val a = if (coverOn > 0f) 0.55f else 0.25f
                val c = if (coverOn > 0f) tint else accent
                drawRect(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(w * 0.5f, h * 0.22f), w * 0.8f), Offset.Zero, Size(w, h))
                drawRect(Brush.radialGradient(listOf(c.copy(alpha = a * 0.5f), Color.Transparent), Offset(w * 0.1f, h * 0.95f), w * 0.6f), Offset.Zero, Size(w, h))
            }
            BackdropMode.AURORA -> {
                val t = phase * 2f * Math.PI.toFloat()
                val tones = listOf(accent, if (coverOn > 0f) tint else C.Sand, C.Blue)
                tones.forEachIndexed { i, tone ->
                    val k = i * 2.1f
                    val cx = w * (0.5f + 0.38f * sin(t + k))
                    val cy = h * (0.30f + 0.22f * cos(t + k * 1.3f) + i * 0.18f)
                    drawRect(Brush.radialGradient(listOf(tone.copy(alpha = 0.26f), Color.Transparent), Offset(cx, cy), w * 0.55f), Offset.Zero, Size(w, h))
                }
            }
        }
    }
}

private fun DrawScope.grid(w: Float, h: Float, color: Color) {
    val step = 48.dp.toPx()
    val path = Path()
    var x = step
    while (x < w) { path.moveTo(x, 0f); path.lineTo(x, h); x += step }
    var y = step
    while (y < h) { path.moveTo(0f, y); path.lineTo(w, y); y += step }
    drawPath(path, color, style = Stroke(1f))
}
