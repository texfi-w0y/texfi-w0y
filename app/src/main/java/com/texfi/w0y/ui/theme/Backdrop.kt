package com.texfi.w0y.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.BackdropMode
import com.texfi.w0y.ui.components.rememberCoverTint
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Что рисовать под экранами: режим, цвет обложки, картинка и фаза переливов. */
class BackdropSpec(
    val mode: BackdropMode,
    val tint: State<Color>,
    val coverOn: Float,
    val image: ImageBitmap?,
    val phase: State<Float>,
)

val LocalBackdrop = staticCompositionLocalOf<BackdropSpec?> { null }

/** Кладёт общий фон в композицию: оболочка и оверлеи рисуют его одинаково. */
@Composable
fun ProvideBackdrop(mode: BackdropMode, coverUrl: String?, imagePath: String?, content: @Composable () -> Unit) {
    val colors = LocalW0yColors.current
    val cover by rememberCoverTint(coverUrl, colors.accent)
    val tint = animateColorAsState(cover, tween(600), label = "backdropTint")
    val reduce = hairLook.motion != MotionLevel.FULL
    val phase = if (mode == BackdropMode.AURORA && !reduce) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            0f, 1f, infiniteRepeatable(tween(AURORA_MS, easing = LinearEasing), RepeatMode.Restart), label = "auroraPhase",
        )
    } else {
        remember { mutableStateOf(0.25f) }
    }
    var bitmap by remember(imagePath) { mutableStateOf<ImageBitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Контурная карта: картинка из ресурсов, в аппаратный битмап — он лежит в памяти видеокарты,
    // а не в куче приложения (10 МБ несжатого кадра). Грузится только пока выбран этот фон.
    var topo by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(mode) {
        if (mode != BackdropMode.TOPO || topo != null) return@LaunchedEffect
        topo = withContext(Dispatchers.IO) {
            runCatching {
                val options = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.HARDWARE }
                android.graphics.BitmapFactory.decodeResource(context.resources, com.texfi.w0y.R.drawable.backdrop_topo, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    LaunchedEffect(imagePath, mode) {
        if (mode != BackdropMode.PHOTO || imagePath == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.BitmapFactory.decodeFile(imagePath.removePrefix("file://"))?.asImageBitmap()
            }.getOrNull()
        }
    }
    val spec = BackdropSpec(mode, tint, if (coverUrl != null) 1f else 0f, if (mode == BackdropMode.TOPO) topo else bitmap, phase)
    CompositionLocalProvider(LocalBackdrop provides spec, content = content)
}

/**
 * Рисует фон на всю высоту [size] + [extraTop]: оверлеи лежат под строкой
 * состояния, и без этого сверху оставался отдельный тёмный прямоугольник.
 */
fun DrawScope.drawBackdrop(spec: BackdropSpec?, colors: W0yColors, extraTop: Float) {
    val mode = spec?.mode ?: BackdropMode.GRID
    val w = size.width
    val h = size.height + extraTop
    translate(top = -extraTop) {
        drawRect(colors.background, Offset.Zero, Size(w, h))
        when (mode) {
            BackdropMode.PURE -> Unit
            BackdropMode.TOPO -> {
                val image = spec?.image
                // Картинка тёмная: на светлой теме она бы утопила текст, и там остаётся сетка.
                if (image != null && colors.background.luminance() < 0.5f) {
                    val scale = maxOf(w / image.width, h / image.height)
                    val dw = image.width * scale
                    val dh = image.height * scale
                    drawImage(
                        image,
                        dstOffset = IntOffset(((w - dw) / 2f).toInt(), ((h - dh) / 2f).toInt()),
                        dstSize = IntSize(dw.toInt(), dh.toInt()),
                    )
                    // Затемнение: линии остаются, но не спорят с текстом (множитель яркости ≈ 0,6); снизу чуть темнее.
                    drawRect(Color.Black.copy(alpha = 0.40f), Offset.Zero, Size(w, h))
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.25f), startY = h * 0.6f, endY = h),
                        Offset.Zero, Size(w, h),
                    )
                } else {
                    grid(w, h, colors.text.copy(alpha = 0.04f))
                }
            }
            BackdropMode.GRID -> {
                drawRect(
                    Brush.verticalGradient(0f to colors.progress.copy(alpha = 0.10f), 0.5f to Color.Transparent, startY = 0f, endY = h),
                    Offset.Zero, Size(w, h),
                )
                val a = (spec?.coverOn ?: 0f) * 0.30f
                if (a > 0.01f) {
                    drawRect(
                        Brush.radialGradient(listOf(spec!!.tint.value.copy(alpha = a), Color.Transparent), Offset(w * 0.9f, 0f), w * 1.05f),
                        Offset.Zero, Size(w, h),
                    )
                }
                grid(w, h, colors.text.copy(alpha = 0.04f))
            }
            BackdropMode.COVER -> {
                val c = spec?.tint?.value ?: colors.accent
                val a = if ((spec?.coverOn ?: 0f) > 0f) 0.55f else 0.25f
                drawRect(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(w * 0.5f, h * 0.22f), w * 1.25f), Offset.Zero, Size(w, h))
                drawRect(Brush.radialGradient(listOf(c.copy(alpha = a * 0.5f), Color.Transparent), Offset(w * 0.1f, h * 0.95f), w * 0.9f), Offset.Zero, Size(w, h))
            }
            BackdropMode.AURORA -> {
                val t = (spec?.phase?.value ?: 0.25f) * 2f * Math.PI.toFloat()
                val tones = listOf(colors.accent, spec?.tint?.value ?: colors.secondary, colors.progress)
                tones.forEachIndexed { i, tone ->
                    val k = i * 2.1f
                    val cx = w * (0.5f + 0.38f * sin(t + k))
                    val cy = h * (0.30f + 0.22f * cos(t * (1f + i * 0.0f) + k * 1.3f) + i * 0.18f)
                    drawRect(Brush.radialGradient(listOf(tone.copy(alpha = 0.30f), Color.Transparent), Offset(cx, cy), w * 0.95f), Offset.Zero, Size(w, h))
                }
            }
            BackdropMode.PHOTO -> {
                val image = spec?.image
                if (image != null) {
                    val scale = maxOf(w / image.width, h / image.height)
                    val dw = image.width * scale
                    val dh = image.height * scale
                    drawImage(
                        image,
                        dstOffset = IntOffset(((w - dw) / 2f).toInt(), ((h - dh) / 2f).toInt()),
                        dstSize = IntSize(dw.toInt(), dh.toInt()),
                    )
                    // Затемнение: текст поверх картинки должен читаться.
                    drawRect(colors.background.copy(alpha = 0.62f), Offset.Zero, Size(w, h))
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 1f to colors.background.copy(alpha = 0.55f), startY = h * 0.5f, endY = h),
                        Offset.Zero, Size(w, h),
                    )
                } else {
                    grid(w, h, colors.text.copy(alpha = 0.04f))
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

private const val AURORA_MS = 28_000
