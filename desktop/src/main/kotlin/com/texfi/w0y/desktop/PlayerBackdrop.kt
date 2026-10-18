package com.texfi.w0y.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Два главных цвета обложки; [tinted] — false, если обложка серая и цвета условные. */
class CoverPalette(val a: Color, val b: Color, val tinted: Boolean)

/**
 * Палитра обложки: цвета считаются по оттенкам (12 корзин), весом служит насыщенность и яркость, поэтому
 * яркое пятно побеждает серый фон. Вторая краска — другой оттенок, а если его нет, светлая версия первой.
 */
@Composable
fun rememberCoverPalette(url: String?): CoverPalette {
    val fallback = CoverPalette(Color(0xFF6E7BA8), Color(0xFF4A5A8A), false)
    val value by produceState(fallback, url) {
        value = fallback
        val model = sizedThumb(url, 120) ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val img = javax.imageio.ImageIO.read(java.net.URI(model).toURL()) ?: return@runCatching fallback
                val weight = DoubleArray(12)
                val r = DoubleArray(12); val g = DoubleArray(12); val b = DoubleArray(12)
                val step = (img.width / 24).coerceAtLeast(1)
                val hsv = FloatArray(3)
                for (x in 0 until img.width step step) for (y in 0 until img.height step step) {
                    val p = img.getRGB(x, y)
                    val pr = (p shr 16) and 0xFF; val pg = (p shr 8) and 0xFF; val pb = p and 0xFF
                    java.awt.Color.RGBtoHSB(pr, pg, pb, hsv)
                    if (hsv[2] < 0.15f) continue
                    val w = (hsv[1] * hsv[2]).toDouble()
                    if (w < 0.04) continue
                    val bin = ((hsv[0] * 12).toInt()).coerceIn(0, 11)
                    weight[bin] += w; r[bin] += pr * w; g[bin] += pg * w; b[bin] += pb * w
                }
                val first = weight.indices.maxByOrNull { weight[it] } ?: return@runCatching fallback
                if (weight[first] < 0.5) return@runCatching fallback
                fun color(i: Int) = Color((r[i] / weight[i]).toInt().coerceIn(0, 255), (g[i] / weight[i]).toInt().coerceIn(0, 255), (b[i] / weight[i]).toInt().coerceIn(0, 255))
                val a = color(first)
                val second = weight.indices.filter { d -> minOf((d - first + 12) % 12, (first - d + 12) % 12) >= 2 }.maxByOrNull { weight[it] }
                val bColor = if (second != null && weight[second] > weight[first] * 0.25) color(second) else androidx.compose.ui.graphics.lerp(a, Color.White, 0.35f)
                CoverPalette(a, bColor, true)
            }.getOrDefault(fallback)
        }
    }
    return value
}

/**
 * Фон за большим плеером. Без линий приложения: только свечение в цветах обложки, размытая обложка или переливы.
 * Цвета плавно перетекают при смене трека; «дыхание» свечения — только при полных анимациях.
 */
@Composable
fun PlayerBackdrop(app: AppState, center: DpOffset = DpOffset(300.dp, 300.dp), coverRadius: Dp = 150.dp, modifier: Modifier = Modifier) {
    val mode = ThemeState.playerBack
    if (mode == PlayerBack.APP) return
    val url = app.player.current?.thumbnailUrl
    val palette = rememberCoverPalette(url)
    val ca by animateColorAsState(palette.a, tween(Motion.ms(800)))
    val cb by animateColorAsState(palette.b, tween(Motion.ms(800)))
    val strength = if (palette.tinted) 1f else 0.45f
    val moving = Motion.full
    val phase = if (moving && (mode == PlayerBack.GLOW || mode == PlayerBack.AURORA)) {
        rememberInfiniteTransition(label = "pback").animateFloat(0f, 1f, infiniteRepeatable(tween(if (mode == PlayerBack.AURORA) 24_000 else 9_000, easing = LinearEasing), RepeatMode.Restart), label = "pback-phase").value
    } else {
        0f
    }
    when (mode) {
        PlayerBack.PURE -> Box(modifier.fillMaxSize().background(C.Background))
        PlayerBack.BLUR -> Box(modifier.fillMaxSize().background(C.Background)) {
            val model = sizedThumb(url, 400)
            if (model != null) {
                coil3.compose.AsyncImage(
                    model = model, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(70.dp).alpha(0.55f),
                )
            }
            Box(Modifier.fillMaxSize().background(C.Background.copy(alpha = 0.35f)))
        }
        PlayerBack.AURORA -> Canvas(modifier.fillMaxSize().background(C.Background)) {
            val w = size.width; val h = size.height
            val t = phase * 2f * Math.PI.toFloat()
            listOf(ca, cb, androidx.compose.ui.graphics.lerp(ca, cb, 0.5f)).forEachIndexed { i, tone ->
                val k = i * 2.1f
                val cx = w * (0.5f + 0.38f * sin(t + k))
                val cy = h * (0.35f + 0.25f * cos(t + k * 1.3f) + i * 0.12f)
                drawRect(Brush.radialGradient(listOf(tone.copy(alpha = 0.42f * strength), Color.Transparent), Offset(cx, cy), w * 0.6f), Offset.Zero, Size(w, h))
            }
        }
        else -> Canvas(modifier.fillMaxSize().background(C.Background)) {
            val w = size.width; val h = size.height
            val breathe = 1f + 0.05f * sin(phase * 2f * Math.PI.toFloat())
            val c = Offset(center.x.toPx(), center.y.toPx())
            val r = coverRadius.toPx()
            val reach = maxOf(w, h) * 0.95f * breathe
            val mid = androidx.compose.ui.graphics.lerp(ca, cb, 0.5f)
            // Свечение растёт из самой обложки и плавно переходит в чёрный: до дальних краёв доходит лишь слабый отсвет.
            drawRect(
                Brush.radialGradient(
                    0f to ca.copy(alpha = 0.80f * strength),
                    0.30f to ca.copy(alpha = 0.50f * strength),
                    0.55f to mid.copy(alpha = 0.24f * strength),
                    0.80f to cb.copy(alpha = 0.08f * strength),
                    1f to Color.Transparent,
                    center = c, radius = reach,
                ),
                Offset.Zero, Size(w, h),
            )
            // Плотный ореол вплотную к обложке: граница обложки не режет глаз.
            drawRect(Brush.radialGradient(listOf(ca.copy(alpha = 0.60f * strength), Color.Transparent), c, r * 1.9f * breathe), Offset.Zero, Size(w, h))
        }
    }
}

/** Крупная обложка для «Постера»: просим у YouTube самый большой размер (маленькая картинка на весь экран даёт «пиксели»). */
fun hiresThumb(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.contains("googleusercontent.com") || url.contains("ggpht.com") -> url.substringBefore('=') + "=w1600-h1600-l95-rj"
        url.contains("i.ytimg.com") -> url.substringBefore('?').replace(Regex("/(default|mqdefault|hqdefault|sddefault|hq720)\\.jpg"), "/maxresdefault.jpg")
        else -> url
    }
}
