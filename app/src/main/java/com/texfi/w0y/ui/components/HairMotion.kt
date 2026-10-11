package com.texfi.w0y.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.MotionMode
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.MotionLevel
import com.texfi.w0y.ui.theme.hairLook
import kotlinx.coroutines.delay

/**
 * Какой уровень анимаций включать. Чистая функция, без Android: её проверяет
 * тест. Явный выбор пользователя главнее всего; «Авто» отступает, когда
 * система просит убрать анимации, включена экономия заряда или телефон слабый.
 */
fun resolveMotion(mode: MotionMode, animatorScale: Float, powerSave: Boolean, lowRam: Boolean): MotionLevel =
    when (mode) {
        MotionMode.FULL -> MotionLevel.FULL
        MotionMode.LITE -> MotionLevel.LITE
        MotionMode.OFF -> MotionLevel.OFF
        MotionMode.AUTO ->
            when {
                animatorScale <= 0f -> MotionLevel.OFF
                powerSave || lowRam -> MotionLevel.LITE
                else -> MotionLevel.FULL
            }
    }

/** Уровень анимаций с учётом системы: следит за экономией заряда. */
@Composable
fun rememberMotionLevel(mode: MotionMode): MotionLevel {
    val context = LocalContext.current
    var powerSave by remember { mutableStateOf(isPowerSave(context)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    powerSave = isPowerSave(c)
                }
            }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    val scale =
        remember(context) {
            runCatching {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            }.getOrDefault(1f)
        }
    val lowRam =
        remember(context) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.isLowRamDevice == true || (am?.memoryClass ?: 256) <= LOW_MEMORY_CLASS_MB
        }
    return resolveMotion(mode, scale, powerSave, lowRam)
}

private fun isPowerSave(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

private const val LOW_MEMORY_CLASS_MB = 128

/**
 * Анимация под текущий уровень: полная — по кривой Hairline, лёгкая — не
 * длиннее короткой, без движения — мгновенно.
 */
@Composable
fun <T> hairSpec(ms: Int = HairTokens.MID_MS): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    when (hairLook.motion) {
        MotionLevel.FULL -> tween(ms, easing = HairTokens.EaseOut)
        MotionLevel.LITE -> tween(minOf(ms, HairTokens.SHORT_MS), easing = HairTokens.EaseOut)
        MotionLevel.OFF -> snap()
    }

/**
 * Контур, который «рисуется» при появлении: штрих растёт от нуля до целого
 * за 220 мс. После — обычная линия. Значение читается только в фазе
 * отрисовки, содержимое не перекомпоновывается, а путь и его мерка
 * пересчитываются лишь при смене размера.
 */
@Composable
fun Modifier.hairOutline(
    shape: Shape,
    color: Color,
    width: Dp = hairLook.line,
    delayMs: Int = 0,
    drawOn: Boolean = true,
): Modifier {
    val animate = drawOn && hairLook.motion.draws
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            if (delayMs > 0) delay(delayMs.toLong())
            progress.animateTo(1f, tween(HairTokens.MID_MS, easing = HairTokens.EaseOut))
        }
    }
    return drawWithCache {
        val stroke = width.toPx().coerceAtLeast(1f)
        val half = stroke / 2f
        val inner = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f))
        val path = Path().apply { addOutline(shape.createOutline(inner, layoutDirection, this@drawWithCache)) }
        val measure = PathMeasure().also { it.setPath(path, false) }
        val length = measure.length
        val part = Path()
        val style = Stroke(stroke)
        onDrawWithContent {
            drawContent()
            translate(half, half) {
                val p = progress.value
                if (p >= 1f) {
                    drawPath(path, color, style = style)
                } else {
                    part.rewind()
                    measure.getSegment(0f, length * p, part, true)
                    drawPath(part, color, style = style)
                }
            }
        }
    }
}

/**
 * Появление лесенкой: строки приезжают снизу по очереди, шаг 20 мс, не больше
 * шести. Лёгкий уровень — только проявление, без сдвига и очереди; без
 * движения — сразу на месте. Элементы дальше шестого не анимируются вовсе:
 * при прокрутке они появляются сами, и задержка была бы лагом.
 */
@Composable
fun Modifier.hairStagger(index: Int): Modifier {
    val level = hairLook.motion
    if (!level.moves || index >= HairTokens.STAGGER_MAX * 2) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (level.draws) delay(minOf(index, HairTokens.STAGGER_MAX - 1) * HairTokens.STAGGER_MS)
        progress.animateTo(1f, hairTweenOf(level))
    }
    val shift = if (level.draws) STAGGER_SHIFT else 0.dp
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * shift.toPx()
    }
}

private fun hairTweenOf(level: MotionLevel): AnimationSpec<Float> =
    when (level) {
        MotionLevel.FULL -> tween(HairTokens.MID_MS, easing = HairTokens.EaseOut)
        else -> tween(HairTokens.SHORT_MS, easing = HairTokens.EaseOut)
    }

private val STAGGER_SHIFT = 10.dp
