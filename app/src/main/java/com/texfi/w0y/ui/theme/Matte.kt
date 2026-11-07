package com.texfi.w0y.ui.theme

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import kotlin.random.Random

/**
 * Матовый остров Hairline New — как капсула навигации на сайте TexFi.
 *
 * Полупрозрачная тёмная подложка, под ней (если дан [backdrop], с Android 12) размытое то, что
 * лежит ниже, поверх — мелкое зерно, по краю линия в 1 dp, сверху чуть светлее: свет падает
 * на кромку, а не льётся изнутри. Ни свечения, ни цветной тени.
 *
 * Без [backdrop] или на старом Android подложка плотнее: остров всё равно читается поверх
 * любого экрана.
 */
@Composable
fun Modifier.matte(shape: Shape, backdrop: Backdrop? = null, fill: Color? = null, alpha: Float? = null, edge: Boolean = true): Modifier {
    val colors = LocalW0yColors.current
    val light = colors.background.luminance() > 0.5f
    val blurs = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = fill ?: if (light) Color.White else Color(0xFF18181C)
    // [alpha] задают панели внутри экрана: размывать им нечего, а сквозь лёгкую подложку видна точка фона.
    val surface = base.copy(alpha = alpha ?: if (blurs) (if (light) 0.6f else 0.58f) else (if (light) 0.9f else 0.88f))
    val grainAlpha = if (light) 0.5f else 1f
    val edgeBrush =
        Brush.verticalGradient(
            if (light) {
                listOf(Color.Black.copy(alpha = 0.07f), Color.Black.copy(alpha = 0.1f))
            } else {
                listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.07f))
            },
        )
    val inner: Modifier =
        if (blurs) {
            drawBackdrop(
                backdrop = backdrop!!,
                shape = { shape },
                effects = { blur(MATTE_BLUR.toPx()) },
                highlight = null,
                shadow = null,
                onDrawSurface = {
                    drawRect(surface)
                    drawGrain(grainAlpha)
                },
            )
        } else {
            clip(shape).drawBehind {
                drawRect(surface)
                drawGrain(grainAlpha)
            }
        }
    return if (edge) inner.border(1.dp, edgeBrush, shape) else inner
}

/**
 * То, что лежит под островами плеера-постера: обложка и её мягкая копия. Острова внутри
 * слотов плеера берут его отсюда — сами слоты собираются в другом файле.
 */
val LocalIslandBackdrop = androidx.compose.runtime.staticCompositionLocalOf<Backdrop?> { null }

/** Зерно поверх заливки: одна плитка шума, повтором на всю площадь. */
fun DrawScope.drawGrain(alpha: Float = 1f) {
    drawRect(GrainBrush, alpha = alpha)
}

/**
 * Плитка шума 96×96: белые точки разной прозрачности, сид постоянный — зерно не «кипит»
 * между запусками и не мерцает при перерисовке.
 */
private val GrainTile: ImageBitmap by lazy {
    val n = GRAIN_TILE
    val random = Random(7)
    val pixels = IntArray(n * n) { (random.nextInt(GRAIN_MAX_ALPHA) shl 24) or 0x00FFFFFF }
    android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val GrainBrush: ShaderBrush by lazy { ShaderBrush(ImageShader(GrainTile, TileMode.Repeated, TileMode.Repeated)) }

/**
 * Фон Hairline New: точки в узлах сетки, как на сайте. Шаг крупный, точка в 1 dp и почти
 * прозрачная — это фактура листа, а не узор.
 */
fun DrawScope.drawDotGrid(color: Color, step: Float = 22.dp.toPx(), radius: Float = 0.75.dp.toPx()) {
    val points = ArrayList<Offset>()
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            points += Offset(x, y)
            x += step
        }
        y += step
    }
    // Одним вызовом: сотни точек — один проход отрисовки, а не сотни.
    drawPoints(points, androidx.compose.ui.graphics.PointMode.Points, color, strokeWidth = radius * 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
}

private val MATTE_BLUR = 22.dp
private const val GRAIN_TILE = 96
private const val GRAIN_MAX_ALPHA = 26
