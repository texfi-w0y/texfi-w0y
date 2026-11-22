package com.texfi.w0y.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.texfi.w0y.playback.SpectrumAnalyzer
import com.texfi.w0y.playback.SpectrumBus
import com.texfi.w0y.ui.theme.LocalLiteGraphics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Играет очередь fl0w — мини-плеер и плеер показывают это. null — обычное воспроизведение. */
val LocalFlowNow = androidx.compose.runtime.compositionLocalOf<com.texfi.w0y.recs.FlowNow?> { null }

/**
 * Волна звука на фоне fl0w — как cava в терминале: ряд тонких столбиков от низа, подъём сразу,
 * спад с ускорением, как под тяжестью. Цвет — тот же, что у текста, но почти прозрачный: это фон,
 * а не экран визуализатора. На паузе столбики оседают и гаснут.
 *
 * Звук — из того же отвода, что у визуализатора плеера ([SpectrumBus]): отвод работает, только
 * пока волна на экране и приложение впереди. Расчёт на фоновом потоке, главный только рисует.
 */
@Composable
fun FlowCava(bus: SpectrumBus, playing: Boolean, color: Color, modifier: Modifier = Modifier, bars: Int = CAVA_BARS) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateAsState()
    val visible = resumed.isAtLeast(Lifecycle.State.RESUMED)
    val lite = LocalLiteGraphics.current
    val analyzer = remember(bars) { SpectrumAnalyzer(size = 2048, bands = bars) }
    val heights = remember(bars) { FloatArray(bars) }
    val fall = remember(bars) { FloatArray(bars) }
    var frame by remember { mutableLongStateOf(0L) }
    val isPlaying by rememberUpdatedState(playing)
    val shown by animateFloatAsState(if (playing) 1f else 0f, tween(if (playing) 600 else 900), label = "cavaShown")

    DisposableEffect(visible) {
        if (visible) bus.attach()
        onDispose { if (visible) bus.detach() }
    }
    LaunchedEffect(visible, lite) {
        if (!visible) return@LaunchedEffect
        val frameMs = 1000L / if (lite) 30 else 60
        withContext(Dispatchers.Default) {
            var idle = 0
            while (isActive) {
                val started = System.nanoTime()
                if (isPlaying) {
                    bus.latest(analyzer.samples)
                    analyzer.analyze(bus.sampleRate, 1f)
                    idle = 0
                } else {
                    analyzer.settle()
                    idle++
                }
                synchronized(heights) {
                    for (i in 0 until bars) {
                        val target = analyzer.levels[i]
                        if (target >= heights[i]) {
                            heights[i] = target
                            fall[i] = 0f
                        } else {
                            // Гравитация cava: чем дольше падает, тем быстрее.
                            fall[i] += GRAVITY
                            heights[i] = (heights[i] - fall[i]).coerceAtLeast(target)
                        }
                    }
                }
                frame++
                val wait = if (idle > IDLE_FRAMES) IDLE_MS else frameMs - (System.nanoTime() - started) / 1_000_000
                delay(wait.coerceAtLeast(4))
            }
        }
    }

    Canvas(modifier.graphicsLayer { alpha = shown }) {
        frame // чтение кадра — перерисовка только этого слоя
        val slot = size.width / bars
        val w = (slot * 0.56f).coerceAtLeast(1f)
        val r = CornerRadius(w / 2f, w / 2f)
        synchronized(heights) {
            for (i in 0 until bars) {
                val h = (heights[i] * size.height).coerceAtLeast(w)
                drawRoundRect(color, Offset(i * slot + (slot - w) / 2f, size.height - h), Size(w, h + r.x), r)
            }
        }
    }
}

private const val CAVA_BARS = 40
private const val GRAVITY = 0.0018f
private const val IDLE_FRAMES = 90
private const val IDLE_MS = 250L
