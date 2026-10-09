package com.texfi.w0y.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil3.compose.AsyncImage
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.icons.SmoothIcons

/** Обычный текст — читаемый; пиксельный шрифт только для заголовков и подписей разделов. */
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.Text,
    size: Int = 14,
    pixel: Boolean = false,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style =
            TextStyle(
                color = color,
                fontSize = size.sp,
                fontFamily = if (pixel && !Look.smooth) PixelFont else FontFamily.Default,
                fontWeight = if (pixel && Look.smooth) FontWeight.SemiBold else weight,
                lineHeight = (size * 1.35f).sp,
            ),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    if (Look.smooth) Txt(text, modifier, color = C.Muted, size = 13, pixel = true) else Txt(text.uppercase(), modifier, color = C.Blue, size = 11, pixel = true)

/** Карточка: у Pixel жёсткая граница и сдвинутая тень без размытия, у Smooth — скруглённая подложка (стекло или матовая). */
@Composable
fun PixelCard(modifier: Modifier = Modifier, padding: Dp = 14.dp, content: @Composable () -> Unit) {
    if (Look.smooth) {
        val shape = RoundedCornerShape(Look.card)
        Box(modifier.smoothSurface(shape).padding(padding)) { content() }
    } else {
        Box(
            modifier
                .drawBehind {
                    drawRect(C.Shadow, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = size)
                }.background(C.Surface, RoundedCornerShape(6.dp))
                .border(2.dp, C.Border, RoundedCornerShape(6.dp))
                .padding(padding),
        ) { content() }
    }
}

/** Кнопка: у Pixel при нажатии тень «уезжает» внутрь, как на телефоне; у Smooth — мягкая таблетка. */
@Composable
fun PixelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill = if (primary) C.Blue else C.SurfaceHigh
    if (Look.smooth) {
        val shape = RoundedCornerShape(Look.control)
        Box(
            modifier.hoverable(source).clip(shape)
                .background(if (hovered && enabled) fill.copy(alpha = 0.88f) else fill)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Txt(text, color = if (enabled) (if (primary) C.OnAccent else C.Text) else C.Muted, size = 14, weight = FontWeight.SemiBold) }
        return
    }
    val shadow = if (primary) C.BlueDeep else C.Shadow
    Box(
        modifier
            .hoverable(source)
            .drawBehind {
                drawRect(shadow, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = size)
            }.background(if (hovered && enabled) fill.copy(alpha = 0.9f) else fill, RoundedCornerShape(4.dp))
            .border(2.dp, if (primary) C.BlueDeep else C.Border, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Txt(text.uppercase(), color = if (enabled) (if (primary) C.OnAccent else C.Text) else C.Muted, size = 10, pixel = true) }
}

/**
 * Иконки. Часть рисуется кодом (медиа-кнопки, громкость), остальные берутся
 * из тех же наборов, что на телефоне: пиксельная сетка у Pixel, контур
 * Material Rounded у Smooth. [sprite]/[smooth] пусты — значит, рисуется кодом в обоих стилях.
 */
enum class Glyph(val sprite: List<String>? = null, val smooth: ImageVector? = null) {
    Play, Pause, Next, Prev, Heart, HeartOff, Search, Plus, Close, Queue, Home, Library, Volume, Trash, Download, Check, Timer, Mic, Wave, Gear,
    Shuffle(Sprites.shuffle, SmoothIcons.shuffle),
    Repeat(Sprites.repeat, SmoothIcons.repeat),
    Stand(Sprites.stand, SmoothIcons.stand),
    Sliders(Sprites.sliders, SmoothIcons.sliders),
    Palette(Sprites.palette, SmoothIcons.palette),
    Globe(Sprites.globe, SmoothIcons.globe),
    Headphones(Sprites.headphones, SmoothIcons.headphones),
    Sync(Sprites.sync, SmoothIcons.sync),
    Shield(Sprites.shield, SmoothIcons.shield),
    Stats(Sprites.stats, SmoothIcons.stats),
    Pin(Sprites.pin, SmoothIcons.pin),
    ChevronRight(Sprites.chevronRight, SmoothIcons.chevronRight),
    ChevronLeft(Sprites.chevronLeft, SmoothIcons.chevronLeft),
    ChevronUp(Sprites.chevronUp, SmoothIcons.chevronUp),
    Collapse(Sprites.collapse, SmoothIcons.collapse),
    Fullscreen(Sprites.fullscreen, SmoothIcons.fullscreen),
    Share(Sprites.share, SmoothIcons.share),
    Note(Sprites.note, SmoothIcons.note),
    Grip(Sprites.grip, SmoothIcons.grip),
    Box(Sprites.box, SmoothIcons.box),
    Artist(Sprites.artist, SmoothIcons.artist),
    Release(Sprites.release, SmoothIcons.release),
    Rocket(Sprites.rocket, SmoothIcons.rocket),
    License(Sprites.license, SmoothIcons.license),
    Github(Sprites.github, SmoothIcons.github),
    Rotate(Sprites.rotate, SmoothIcons.rotate),
    ;

    /** Пара из телефонных наборов для кодовой иконки в плавном стиле; null — рисуется кодом. */
    internal val smoothPair: ImageVector? get() = smooth ?: when (this) {
        Play -> SmoothIcons.play
        Pause -> SmoothIcons.pause
        Next -> SmoothIcons.next
        Prev -> SmoothIcons.previous
        Heart -> SmoothIcons.heart
        Search -> SmoothIcons.search
        Plus -> SmoothIcons.plus
        Close -> SmoothIcons.close
        Home -> SmoothIcons.home
        Library -> SmoothIcons.library
        Trash -> SmoothIcons.trash
        Download -> SmoothIcons.download
        Check -> SmoothIcons.check
        Timer -> SmoothIcons.timer
        Wave -> SmoothIcons.wave
        Gear -> SmoothIcons.gear
        else -> null
    }
}

/** Иконки рисуются кодом: ни растровых ассетов, ни шрифта с символами. */
@Composable
fun Icon(glyph: Glyph, color: Color = C.Text, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    if (Look.smooth) {
        val vector = glyph.smoothPair
        if (vector != null) {
            Image(rememberVectorPainter(vector), null, modifier.size(size), colorFilter = ColorFilter.tint(color))
            return
        }
        if (glyph == Glyph.HeartOff) {
            Box(modifier.size(size).drawBehind { drawHeartOutline(color) })
            return
        }
    } else if (glyph.sprite != null) {
        Spacer(modifier.size(size).drawWithCache { spriteDrawer(glyph.sprite, color) })
        return
    }
    Box(
        modifier.size(size).drawBehind {
            val w = this.size.width
            val h = this.size.height
            fun tri(a: Offset, b: Offset, c: Offset) =
                drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
            when (glyph) {
                Glyph.Play -> tri(Offset(w * .25f, h * .12f), Offset(w * .25f, h * .88f), Offset(w * .88f, h * .5f))
                Glyph.Pause -> {
                    drawRect(color, Offset(w * .2f, h * .14f), Size(w * .22f, h * .72f))
                    drawRect(color, Offset(w * .58f, h * .14f), Size(w * .22f, h * .72f))
                }
                Glyph.Next -> {
                    tri(Offset(w * .12f, h * .18f), Offset(w * .12f, h * .82f), Offset(w * .66f, h * .5f))
                    drawRect(color, Offset(w * .7f, h * .18f), Size(w * .16f, h * .64f))
                }
                Glyph.Prev -> {
                    tri(Offset(w * .88f, h * .18f), Offset(w * .88f, h * .82f), Offset(w * .34f, h * .5f))
                    drawRect(color, Offset(w * .14f, h * .18f), Size(w * .16f, h * .64f))
                }
                Glyph.Heart, Glyph.HeartOff -> {
                    val cell = w / 7f
                    val rows = listOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000")
                    rows.forEachIndexed { y, row ->
                        row.forEachIndexed { x, ch ->
                            if (ch == '1') {
                                val fillCell = glyph == Glyph.Heart
                                if (fillCell) {
                                    drawRect(color, Offset(x * cell, (y + .5f) * cell), Size(cell + .6f, cell + .6f))
                                } else {
                                    val edge = y == 0 || y == rows.lastIndex || row.getOrNull(x - 1) != '1' || row.getOrNull(x + 1) != '1' ||
                                        rows.getOrNull(y - 1)?.getOrNull(x) != '1' || rows.getOrNull(y + 1)?.getOrNull(x) != '1'
                                    if (edge) drawRect(color, Offset(x * cell, (y + .5f) * cell), Size(cell + .6f, cell + .6f))
                                }
                            }
                        }
                    }
                }
                Glyph.Search -> {
                    drawCircle(color, radius = w * .3f, center = Offset(w * .42f, h * .42f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * .12f))
                    drawLine(color, Offset(w * .64f, h * .64f), Offset(w * .9f, h * .9f), strokeWidth = w * .14f)
                }
                Glyph.Gear -> {
                    val cell = w / 7f
                    val rows = listOf("0010100", "0111110", "1110111", "1100011", "1110111", "0111110", "0010100")
                    rows.forEachIndexed { y, row ->
                        row.forEachIndexed { x, ch -> if (ch == '1') drawRect(color, Offset(x * cell, y * cell), Size(cell + .6f, cell + .6f)) }
                    }
                }
                Glyph.Plus -> {
                    drawRect(color, Offset(w * .43f, h * .12f), Size(w * .14f, h * .76f))
                    drawRect(color, Offset(w * .12f, h * .43f), Size(w * .76f, h * .14f))
                }
                Glyph.Close -> {
                    drawLine(color, Offset(w * .2f, h * .2f), Offset(w * .8f, h * .8f), strokeWidth = w * .13f)
                    drawLine(color, Offset(w * .8f, h * .2f), Offset(w * .2f, h * .8f), strokeWidth = w * .13f)
                }
                Glyph.Queue -> {
                    for (i in 0..2) drawRect(color, Offset(w * .12f, h * (.2f + i * .26f)), Size(w * .76f, h * .12f))
                }
                Glyph.Home -> {
                    tri(Offset(w * .5f, h * .1f), Offset(w * .08f, h * .5f), Offset(w * .92f, h * .5f))
                    drawRect(color, Offset(w * .22f, h * .5f), Size(w * .56f, h * .38f))
                }
                Glyph.Library -> {
                    drawRect(color, Offset(w * .12f, h * .15f), Size(w * .76f, h * .26f))
                    drawRect(color, Offset(w * .12f, h * .55f), Size(w * .76f, h * .26f))
                }
                Glyph.Volume -> {
                    drawRect(color, Offset(w * .1f, h * .38f), Size(w * .22f, h * .24f))
                    tri(Offset(w * .32f, h * .38f), Offset(w * .32f, h * .62f), Offset(w * .6f, h * .82f))
                    tri(Offset(w * .32f, h * .38f), Offset(w * .6f, h * .18f), Offset(w * .6f, h * .82f))
                    drawRect(color, Offset(w * .72f, h * .36f), Size(w * .1f, h * .28f))
                }
                Glyph.Download -> {
                    drawRect(color, Offset(w * .43f, h * .1f), Size(w * .14f, h * .46f))
                    tri(Offset(w * .22f, h * .46f), Offset(w * .78f, h * .46f), Offset(w * .5f, h * .76f))
                    drawRect(color, Offset(w * .15f, h * .84f), Size(w * .7f, h * .1f))
                }
                Glyph.Check -> {
                    drawLine(color, Offset(w * .15f, h * .52f), Offset(w * .4f, h * .76f), strokeWidth = w * .14f)
                    drawLine(color, Offset(w * .4f, h * .76f), Offset(w * .86f, h * .22f), strokeWidth = w * .14f)
                }
                Glyph.Timer -> {
                    drawCircle(color, radius = w * .38f, center = Offset(w * .5f, h * .54f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * .1f))
                    drawLine(color, Offset(w * .5f, h * .54f), Offset(w * .5f, h * .3f), strokeWidth = w * .1f)
                    drawLine(color, Offset(w * .5f, h * .54f), Offset(w * .66f, h * .62f), strokeWidth = w * .1f)
                    drawRect(color, Offset(w * .4f, h * .04f), Size(w * .2f, h * .09f))
                }
                Glyph.Mic -> {
                    drawRect(color, Offset(w * .12f, h * .15f), Size(w * .76f, h * .1f))
                    drawRect(color, Offset(w * .12f, h * .4f), Size(w * .76f, h * .1f))
                    drawRect(color, Offset(w * .12f, h * .65f), Size(w * .46f, h * .1f))
                }
                Glyph.Wave -> {
                    listOf(.35f, .6f, .9f, .6f, .35f).forEachIndexed { i, hh ->
                        drawRect(color, Offset(w * (.1f + i * .17f), h * (.5f - hh / 2)), Size(w * .1f, h * hh))
                    }
                }
                Glyph.Trash -> {
                    drawRect(color, Offset(w * .2f, h * .3f), Size(w * .6f, h * .56f))
                    drawRect(color, Offset(w * .12f, h * .18f), Size(w * .76f, h * .1f))
                }
                else -> if (glyph.sprite != null) drawSprite(glyph.sprite, color) else Unit
            }
        },
    )
}

/** Квадратная иконка-кнопка с подсветкой при наведении. */
@Composable
fun IconButton(glyph: Glyph, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Text, size: Dp = 20.dp, box: Dp = 34.dp) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier
            .size(box)
            .hoverable(source)
            .clip(if (Look.smooth) CircleShape else RoundedCornerShape(4.dp))
            .background(if (hovered) C.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(glyph, if (hovered) C.Text else color, size) }
}

private val ThumbRegex = Regex("=[wsh]\\d+[^/]*$")

/** Обложка: googleusercontent просим нужного размера, остальное берём как есть. */
fun sizedThumb(url: String?, px: Int): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
            url.substringBefore('=') + "=w$px-h$px-l90-rj"
        url.contains("i.ytimg.com/pl_c/") -> url
        url.contains("i.ytimg.com") ->
            url.substringBefore('?').replace(Regex("/(default|mqdefault|sddefault|maxresdefault|hq720)\\.jpg"), "/hqdefault.jpg")
        else -> url
    }
}

@Composable
fun Cover(url: String?, size: Dp, modifier: Modifier = Modifier, circle: Boolean = false) {
    val shape = if (circle) CircleShape else RoundedCornerShape(Look.cover)
    Box(modifier.size(size).clip(shape).background(C.SurfaceHigh), contentAlignment = Alignment.Center) {
        Icon(Glyph.Queue, C.Border, size / 3)
        val model = sizedThumb(url, (size.value * 2).toInt().coerceAtLeast(120))
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
fun hoverBackground(source: MutableInteractionSource): Color {
    val hovered by source.collectIsHoveredAsState()
    return if (hovered) C.Surface else Color.Transparent
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(C.Border))

@Composable
fun Gap(w: Int = 0, h: Int = 0) = Box(Modifier.width(w.dp).height(h.dp))

@Composable
fun RowCenter(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, content = content)

fun Modifier.pad(h: Int = 0, v: Int = 0) = padding(horizontal = h.dp, vertical = v.dp)
fun Modifier.shift(x: Int = 0, y: Int = 0) = offset(x.dp, y.dp)

/** Ползунок из сегментов, как громкость на телефоне: значение в [range], шаг [step]. */
@Composable
fun PixelSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier = Modifier, color: Color = C.Blue, step: Float = 0.05f, segments: Int = 24) {
    fun at(x: Float, width: Int): Float {
        val raw = range.start + (x / width).coerceIn(0f, 1f) * (range.endInclusive - range.start)
        return (Math.round(raw / step) * step).coerceIn(range.start, range.endInclusive)
    }
    Box(
        modifier.height(18.dp)
            .pointerInput(Unit) { detectTapGestures { onChange(at(it.x, size.width)) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> onChange(at(c.position.x, size.width)) } }
            .drawBehind {
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (segments - 1)) / segments
                val filled = (((value - range.start) / (range.endInclusive - range.start)) * segments).toInt()
                repeat(segments) { i ->
                    drawRect(if (i < filled) color else C.SurfaceHigh, Offset(i * (w + gap), size.height / 2 - 4.dp.toPx()), Size(w, 8.dp.toPx()))
                }
            },
    )
}

/** Главный цвет обложки: средний по уменьшенной картинке, без почти чёрных и серых пикселей. */
@Composable
fun rememberCoverColor(url: String?): Color? {
    val color by androidx.compose.runtime.produceState<Color?>(null, url) {
        value = null
        val model = sizedThumb(url, 120) ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val img = javax.imageio.ImageIO.read(java.net.URI(model).toURL()) ?: return@runCatching null
                var r = 0L; var g = 0L; var b = 0L; var n = 0L
                val step = (img.width / 16).coerceAtLeast(1)
                for (x in 0 until img.width step step) for (y in 0 until img.height step step) {
                    val p = img.getRGB(x, y)
                    val pr = (p shr 16) and 0xFF; val pg = (p shr 8) and 0xFF; val pb = p and 0xFF
                    val mx = maxOf(pr, pg, pb); val mn = minOf(pr, pg, pb)
                    if (mx < 40 || mx - mn < 25) continue
                    r += pr; g += pg; b += pb; n++
                }
                if (n == 0L) null else Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
            }.getOrNull()
        }
    }
    return color
}

/** Контур сердца для «не в избранном» в плавном стиле. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeartOutline(color: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * .5f, h * .86f)
        cubicTo(w * .1f, h * .6f, w * .06f, h * .3f, w * .26f, h * .2f)
        cubicTo(w * .4f, h * .13f, w * .5f, h * .26f, w * .5f, h * .3f)
        cubicTo(w * .5f, h * .26f, w * .6f, h * .13f, w * .74f, h * .2f)
        cubicTo(w * .94f, h * .3f, w * .9f, h * .6f, w * .5f, h * .86f)
        close()
    }
    drawPath(p, color, style = androidx.compose.ui.graphics.drawscope.Stroke(w * .09f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSprite(rows: List<String>, color: Color) {
    val cols = rows.maxOf { it.length }
    val cell = minOf(size.width / cols, size.height / rows.size)
    val ox = (size.width - cell * cols) / 2f
    val oy = (size.height - cell * rows.size) / 2f
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch -> if (ch == '#') drawRect(color, Offset(ox + x * cell, oy + y * cell), Size(cell + .5f, cell + .5f)) }
    }
}

private fun androidx.compose.ui.draw.CacheDrawScope.spriteDrawer(rows: List<String>, color: Color): androidx.compose.ui.draw.DrawResult {
    val cols = rows.maxOf { it.length }
    val cell = minOf(size.width / cols, size.height / rows.size)
    val ox = (size.width - cell * cols) / 2f
    val oy = (size.height - cell * rows.size) / 2f
    val path = Path()
    rows.forEachIndexed { y, row ->
        var x = 0
        while (x < row.length) {
            if (row[x] != '#') { x++; continue }
            var run = x
            while (run < row.length && row[run] == '#') run++
            path.addRect(androidx.compose.ui.geometry.Rect(ox + x * cell, oy + y * cell, ox + run * cell + .5f, oy + (y + 1) * cell + .5f))
            x = run
        }
    }
    return onDrawBehind { drawPath(path, color) }
}
