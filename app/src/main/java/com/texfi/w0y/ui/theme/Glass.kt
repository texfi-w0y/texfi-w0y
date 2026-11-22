package com.texfi.w0y.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * «Жидкое стекло» плавного стиля — то же, что у нижних панелей.
 *
 * Полупрозрачная поверхность с тонким бликом по кромке (ярче сверху слева
 * и снизу справа) и мягким отсветом сверху. Если элементу дан [backdrop] —
 * то, что под ним, — стекло ещё и размывает его и преломляет у краёв, как
 * панели. Внутри экрана карточкам backdrop не даётся: размывать они могли
 * бы только сам экран, в который входят. В матовом варианте Smooth
 * поверхность просто плотная.
 */
@Composable
fun Modifier.liquidGlass(shape: Shape, tint: Color? = null, backdrop: Backdrop? = LocalPanelBackdrop.current): Modifier {
    val colors = LocalW0yColors.current
    val light = colors.background.luminance() > 0.5f
    val base = tint ?: colors.surface
    // Матовый вариант Smooth: та же форма, плотная заливка без бликов.
    if (!LocalStyleTokens.current.glass) return clip(shape).background(base)
    // Лёгкий режим: стекло без размытия — только полупрозрачная поверхность и блик.
    @Suppress("NAME_SHADOWING")
    val backdrop = if (LocalLiteGraphics.current) null else backdrop
    val blurs = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val fill = base.copy(alpha = if (blurs) 0.5f else if (light) 0.72f else 0.62f)
    val sheen = Color.White.copy(alpha = if (light) 0.3f else 0.06f)
    return drawBackdrop(
        backdrop = backdrop ?: emptyBackdrop(),
        shape = { shape },
        effects = {
            if (backdrop != null) {
                vibrancy()
                blur(GLASS_BLUR.toPx())
                if (this.shape is CornerBasedShape) lens(GLASS_REFRACTION_HEIGHT.toPx(), GLASS_REFRACTION.toPx())
            }
        },
        highlight = { Highlight(width = 1.dp, alpha = if (light) 1f else 0.75f) },
        shadow = null,
        onDrawSurface = {
            drawRect(fill)
            drawRect(Brush.verticalGradient(listOf(sheen, Color.Transparent), endY = size.height * 0.5f))
        },
    )
}

/**
 * То, что лежит под всплывающей панелью (меню трека): стекло её карточки
 * размывает экран под ней. Внутри карточки снова null — кнопкам на ней
 * размывать нечего.
 */
val LocalPanelBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Подложка элемента по стилю: в Pixel — заливка с рамкой и квадратными
 * углами, в Smooth — стекло с крупным скруглением.
 */
@Composable
fun Modifier.styledSurface(corner: Int, color: Color = LocalW0yColors.current.surface): Modifier =
    if (isSmooth) {
        liquidGlass(androidx.compose.foundation.shape.RoundedCornerShape(minOf(corner * 5 / 2 + 6, 24).dp), color)
    } else {
        styledClip(corner).background(color).styledBorder(corner)
    }

/**
 * То, что лежит под нижними панелями: экран вместе с фоном. Есть только в
 * стеклянном Smooth — тогда панели не просто полупрозрачные, а
 * по-настоящему стеклянные: размывают и преломляют то, что под ними.
 */
val LocalBarBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Стекло нижних панелей — как в iOS 26: тёмная дымка поверх размытого
 * экрана, у краёв стекло чуть преломляет картинку, по кромке — тонкий
 * блик, ярче сверху слева и снизу справа.
 *
 * Размытие есть с Android 12, преломление и блик — с 13. Где их нет,
 * дымка плотнее: панель всё равно должна читаться поверх любого экрана.
 */
@Composable
fun Modifier.glassBar(shape: Shape, backdrop: Backdrop): Modifier {
    val colors = LocalW0yColors.current
    val light = colors.background.luminance() > 0.5f
    // Лёгкий режим: размытия нет — дымка плотная, как на старом Android, без отрисовки экрана под панелью.
    if (LocalLiteGraphics.current) {
        val dense = if (light) Color.White.copy(alpha = 0.9f) else Color(0xFF0E0E11).copy(alpha = 0.9f)
        return clip(shape).background(dense)
    }
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val haze =
        if (light) {
            Color.White.copy(alpha = if (blurs) 0.5f else 0.9f)
        } else {
            Color(0xFF0E0E11).copy(alpha = if (blurs) 0.6f else 0.9f)
        }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(GLASS_BLUR.toPx())
            if (this.shape is CornerBasedShape) lens(GLASS_REFRACTION_HEIGHT.toPx(), GLASS_REFRACTION.toPx())
        },
        highlight = { Highlight(width = 1.dp, alpha = if (light) 1f else 0.85f) },
        shadow = { Shadow(radius = 18.dp, color = Color.Black.copy(alpha = if (light) 0.12f else 0.45f)) },
        onDrawSurface = { drawRect(haze) },
    )
}

/** Выбранный пункт на стекле: светлая капсула, а не цветной блок. */
val glassSelection: Color
    @Composable get() =
        if (LocalW0yColors.current.background.luminance() > 0.5f) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.13f)

private val GLASS_BLUR = 20.dp
private val GLASS_REFRACTION_HEIGHT = 12.dp
private val GLASS_REFRACTION = 24.dp
