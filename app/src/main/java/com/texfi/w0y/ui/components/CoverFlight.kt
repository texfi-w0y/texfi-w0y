package com.texfi.w0y.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.texfi.w0y.data.CoverShape
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.theme.coverRadiusPx
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.roundToInt

/**
 * Общее состояние «полёта обложки»: пока шторка плеера едет, обложка не прячется вместе с ним, а
 * перелетает с места на обложке плеера на место обложки в мини-плеере (и обратно при открытии).
 *
 * Держатель знает три вещи: насколько шторка раскрыта, где сейчас обложка мини-плеера и где обложка
 * плеера. Положение обложки плеера берётся «как в покое»: с него снимается сдвиг и сжатие самой
 * шторки, поэтому полёт строится правильно и когда шторку тянут пальцем, и при самом первом
 * открытии. Рисует полёт [CoverFlightLayer] поверх всего; настоящие обложки на это время прозрачны,
 * так что двойной картинки нет. Если одной из обложек нет (другая раскладка плеера, виден
 * визуализатор или текст), полёта нет и шторка едет как обычно.
 */
@Stable
class CoverFlight {
    /** 0 — плеер закрыт, 1 — открыт. Пишет шторка. */
    var openness by mutableFloatStateOf(0f)

    var miniReady by mutableStateOf(false)
    var playerReady by mutableStateOf(false)

    internal var miniCoords: LayoutCoordinates? = null
    internal var miniRadiusPx = 0f

    internal var playerCoords: LayoutCoordinates? = null
    internal var playerInsetPx = 0f
    internal var playerRadiusPx = 0f
    internal var playerFadePx = 0f

    /** «Дыхание» и пауза обложки плеера: в полёте стартуем с того же вида, чтобы не было скачка. */
    @JvmField var playerScale = 1f
    @JvmField var playerAlpha = 1f

    /** Преобразование, которое шторка сейчас применила к себе (пишется при отрисовке). */
    @JvmField var curtainTy = 0f
    @JvmField var curtainScale = 1f
    @JvmField var curtainW = 0f
    @JvmField var curtainH = 0f

    val flying: Boolean get() = miniReady && playerReady && openness > OPEN_EDGE_LOW && openness < OPEN_EDGE_HIGH

    /** Обложка плеера в покое (шторка раскрыта, без «дыхания»), в координатах окна. */
    internal fun playerRest(): Rect? {
        val c = playerCoords?.takeIf { it.isAttached } ?: return null
        val p = c.positionInRoot()
        val s = curtainScale.coerceAtLeast(0.01f)
        val ox = curtainW / 2f
        val oy = curtainH
        // Шторка: окно = O + s·(точка − O) + (0, ty); обратное преобразование.
        val left = ox + (p.x - ox) / s
        val top = oy + (p.y - oy - curtainTy) / s
        val inset = playerInsetPx
        val r = Rect(left + inset, top + inset, left + c.size.width - inset, top + c.size.height - inset)
        val sc = playerScale
        val cx = r.center.x
        val cy = r.center.y
        return Rect(cx - r.width / 2f * sc, cy - r.height / 2f * sc, cx + r.width / 2f * sc, cy + r.height / 2f * sc)
    }

    internal fun miniRect(): Rect? {
        val c = miniCoords?.takeIf { it.isAttached } ?: return null
        val p = c.positionInRoot()
        return Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
    }

    private companion object {
        const val OPEN_EDGE_LOW = 0.001f
        const val OPEN_EDGE_HIGH = 0.999f
    }
}

val LocalCoverFlight = staticCompositionLocalOf<CoverFlight?> { null }

/** Обложка мини-плеера: сообщает своё место и гаснет на время полёта. [sizeDp] — сторона для расчёта скругления. */
@Composable
fun Modifier.coverFlightMini(flight: CoverFlight?, sizeDp: Dp): Modifier {
    if (flight == null) return this
    val density = LocalDensity.current.density
    val look = hairLook
    val side = sizeDp.value * density
    val radius =
        when (look.coverShape) {
            CoverShape.DISC -> side / 2f
            CoverShape.ROUNDED -> coverRadiusPx(side, density)
            CoverShape.SQUARE -> 0f
        }
    DisposableEffect(flight) {
        onDispose {
            flight.miniCoords = null
            flight.miniReady = false
        }
    }
    return this
        .onGloballyPositioned {
            flight.miniCoords = it
            flight.miniRadiusPx = radius
            flight.miniReady = true
        }
        .graphicsLayer { alpha = if (flight.flying) 0f else 1f }
}

/**
 * Обложка в плеере: сообщает место (без «дыхания» и входной анимации, которые идут слоями ниже по
 * цепочке). [insetDp] — рамка вокруг самой картинки, [fadeDp] — высота растворения книзу у постера.
 * [enabled] = false, когда вместо обложки показаны визуализатор, текст или видео.
 */
@Composable
fun Modifier.coverFlightPlayer(flight: CoverFlight?, enabled: Boolean, insetDp: Dp = 0.dp, radiusDp: Dp = 0.dp, fadeDp: Dp = 0.dp): Modifier {
    if (flight == null) return this
    val density = LocalDensity.current.density
    DisposableEffect(enabled) {
        if (!enabled) {
            flight.playerCoords = null
            flight.playerReady = false
        }
        onDispose {
            flight.playerCoords = null
            flight.playerReady = false
        }
    }
    if (!enabled) return this
    return this.onGloballyPositioned {
        flight.playerCoords = it
        flight.playerInsetPx = insetDp.value * density
        flight.playerRadiusPx = radiusDp.value * density
        flight.playerFadePx = fadeDp.value * density
        flight.playerReady = true
    }
}

/** Прячет содержимое на время полёта: вместо него в воздухе летит копия. */
fun Modifier.coverFlightHide(flight: CoverFlight?): Modifier =
    if (flight == null) this else graphicsLayer { alpha = if (flight.flying) 0f else 1f }

/** Рисует летящую обложку поверх всего. Вне полёта не занимает ничего. */
@Composable
fun CoverFlightLayer(flight: CoverFlight, url: String?) {
    val active by remember { derivedStateOf { flight.flying } }
    if (!active || url == null) return
    val context = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(Thumbnails.sized(url, Thumbnails.HERO)).build() }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val rect = flightRect(flight)
                        val w = rect?.width?.roundToInt()?.coerceAtLeast(1) ?: 1
                        val h = rect?.height?.roundToInt()?.coerceAtLeast(1) ?: 1
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            if (rect != null) placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                        }
                    }
                    .graphicsLayer {
                        val t = 1f - flight.openness
                        val radius = lerp(flight.playerRadiusPx, flight.miniRadiusPx, t)
                        shape = RoundedCornerShape(radius.coerceAtLeast(0f).coerceAtMost(minOf(size.width, size.height) / 2f))
                        clip = true
                        alpha = lerp(flight.playerAlpha, 1f, t)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // У постера низ обложки растворяется в фоне; в полёте растворение уходит по мере сжатия.
                        val fade = lerp(flight.playerFadePx, 0f, 1f - flight.openness)
                        if (fade > 1f && size.height > 1f) {
                            val f = (fade / size.height).coerceIn(0.02f, 0.9f)
                            drawRect(Brush.verticalGradient(0f to Color.Black, (1f - f) to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                        }
                    },
        )
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

private fun flightRect(flight: CoverFlight): Rect? {
    val from = flight.playerRest() ?: return null
    val to = flight.miniRect() ?: return null
    val t = 1f - flight.openness
    return Rect(
        lerp(from.left, to.left, t),
        lerp(from.top, to.top, t),
        lerp(from.right, to.right, t),
        lerp(from.bottom, to.bottom, t),
    )
}
