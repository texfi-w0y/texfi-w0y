package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.isSmooth

/**
 * Обложка: сразу в нужном размере и с проявлением вместо рывка.
 *
 * Отдельный компонент, а не голый `AsyncImage`, чтобы размер картинки
 * нельзя было забыть — забытый размер и есть та самая «плохая обложка».
 */
@Composable
fun CoverImage(
    url: String?,
    px: Int,
    modifier: Modifier = Modifier,
    corner: Int = 4,
    /** Hairline: всегда круг (артисты). */
    circle: Boolean = false,
    /** Hairline: прямоугольный кадр (видео 16:9), форма «диск» тут не годится. */
    rect: Boolean = false,
) {
    val colors = LocalW0yColors.current
    val hair = com.texfi.w0y.ui.theme.isHair
    val look = com.texfi.w0y.ui.theme.hairLook
    // В плавном стиле углы обложек крупнее: та же пропорция к размеру, что у карточек.
    val shape: androidx.compose.ui.graphics.Shape =
        if (hair) {
            when {
                circle -> androidx.compose.foundation.shape.CircleShape
                rect -> RoundedCornerShape(percent = 8)
                else ->
                    when (look.coverShape) {
                        com.texfi.w0y.data.CoverShape.DISC -> androidx.compose.foundation.shape.CircleShape
                        com.texfi.w0y.data.CoverShape.ROUNDED -> RoundedCornerShape(percent = 14)
                        com.texfi.w0y.data.CoverShape.SQUARE -> androidx.compose.ui.graphics.RectangleShape
                    }
            }
        } else {
            RoundedCornerShape((if (isSmooth) corner * 3 else corner).dp())
        }
    Box(
        modifier
            .clip(shape)
            .background(colors.surfaceHigh)
            .then(if (hair) Modifier.border(look.line, colors.border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Нота лежит под картинкой: пока та грузится или если не пришла
        // вовсе, на месте обложки видно, что это трек, а не чёрная дыра.
        PixelSprite(
            rows = Sprites.note,
            color = colors.border,
            modifier = Modifier.fillMaxSize(0.38f),
        )
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(Thumbnails.sized(url, px))
                    .crossfade(180)
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                if (Thumbnails.isVideoFrame(url)) {
                    Modifier.matchParentSize().graphicsLayer { zoomPastLetterbox() }
                } else {
                    Modifier.matchParentSize()
                },
        )
    }
}

/**
 * Кадр видео YouTube (`hqdefault`) — это 4:3 с чёрными полосами сверху и
 * снизу вокруг кадра 16:9. Обычный Crop в квадрат оставлял полосы на
 * обложке. Здесь картинка увеличивается ровно настолько, чтобы полосы
 * ушли за край, — для любого соотношения рамки: в широкой рамке 16:9
 * увеличения нет вовсе, в квадрате оно 4/3.
 */
private fun GraphicsLayerScope.zoomPastLetterbox() {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val fit = maxOf(w / FRAME_W, h / FRAME_H)
    val zoom = maxOf(1f, h / (VIDEO_H * fit))
    scaleX = zoom
    scaleY = zoom
}

private const val FRAME_W = 4f
private const val FRAME_H = 3f

/** Высота кадра 16:9 внутри картинки 4:3 шириной [FRAME_W]. */
private const val VIDEO_H = FRAME_W * 9f / 16f

private fun Int.dp() = androidx.compose.ui.unit.Dp(toFloat())
