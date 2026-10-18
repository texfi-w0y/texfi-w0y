package com.texfi.w0y.desktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.ArtistCard
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.Shelf
import com.texfi.w0y.data.SongItem
import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Съёмка промо-ролика «w0y with desktop»: настоящее приложение рисуется кадр за кадром вне экрана
 * (экран автора не занят), данные — выдуманная англоязычная библиотека с нарисованными обложками.
 * Запускается только с -Dw0y.promo=<файл.mp4>; обычные тесты это пропускают.
 *
 *   w0y.promo          итоговый mp4 (без звука) или путь для кадров при w0y.promo.still
 *   w0y.promo.assets   папка с обложками c01.png … (tools/promo/cache/covers)
 *   w0y.promo.still    «12.5» — отрисовать один кадр на этой секунде и выйти (для подгонки вида)
 *   w0y.promo.range    «10-20» — снять только этот кусок (секунды)
 */
@OptIn(ExperimentalTestApi::class)
class PromoRender {
    private val out: String? = System.getProperty("w0y.promo")
    private val assets: File = File(System.getProperty("w0y.promo.assets") ?: "")
    private val fps = 30
    // Черновик: -Dw0y.promo.scale=0.5 рисует вчетверо меньше пикселей и идёт заметно быстрее.
    private val scale = System.getProperty("w0y.promo.scale")?.toFloatOrNull() ?: 1f
    private val w = (1920 * scale).toInt()
    private val h = (1080 * scale).toInt()
    private val total = 46.0

    // ------------------------------------------------------------ данные

    private val names = listOf(
        "Afterglow" to "Neon Static", "Paper Planes at Midnight" to "Paper Moons", "Salt and Static" to "Glass Harbor",
        "Slow Motion Summer" to "Velvet Circuit", "Low Tide" to "Low Tide Club", "Northern Lights Drive" to "Aurora Vale",
        "Honey Signal" to "Static Bloom", "Last Train Home" to "Night Courier", "Halcyon" to "Halcyon Drive",
        "Kite Season" to "Mono Kite", "Blue Hour" to "Neon Static", "City of Mirrors" to "Glass Harbor",
        "Open Window" to "Paper Moons", "Starlit Parking Lot" to "Velvet Circuit", "Sunday Static" to "Low Tide Club",
        "Wildflower Radio" to "Aurora Vale", "Daydream Protocol" to "Static Bloom", "Midnight Courier" to "Night Courier",
    )
    private val durations = listOf("3:24", "3:41", "2:58", "4:02", "3:15", "3:37", "2:49", "3:56", "3:08", "3:29", "3:44", "4:11", "3:02", "3:33", "2:55", "3:48", "3:21", "3:39")

    private fun cover(i: Int) = File(assets, "c%02d.png".format(i % 12 + 1)).toURI().toString()

    private fun song(i: Int): StoredSong {
        val (title, artist) = names[i % names.size]
        return StoredSong("demo$i", title, artist, album = "$title (Deluxe)", duration = durations[i % durations.size], thumb = cover(i), artistId = "UC_demo_${artist.hashCode().toUInt()}")
    }

    private val lyricsLines = listOf(
        "Turn the lights down low", "Let the city fall away", "We were never meant to stay", "But tonight we're here to play",
        "Static in the speakers", "Gold across your face", "Every little heartbeat", "Finds its own sweet pace",
        "Hold on to the moment", "Let the chorus rise", "Colours in the window", "Stars behind your eyes",
        "We could run forever", "Chasing neon rain", "Say it once together", "Never the same again",
    )

    private fun newApp(): AppState {
        System.setProperty("w0y.data", Files.createTempDirectory("w0y-promo").toString())
        System.setProperty("w0y.ao", "null")
        val app = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Yt(), Store())
        // Смена языка в setSt ходит за лентами YouTube: пока ставим настройки, «загрузка» занята.
        app.homeLoading = true
        app.setSt {
            it.copy(
                uiStyle = UiStyle.HAIRLINE, accent = Accent.BLUE, language = Language.EN, welcomeSeen = true, stylePicked = true,
                showLyrics = true, standKeepAwake = false, standLyrics = true, standView = StandView.COVER, standMaxMinutes = 0,
                saveSearchHistory = false, motion = MotionMode.FULL,
            )
        }
        val now = System.currentTimeMillis()
        app.update {
            it.copy(
                liked = (0 until 9).map(::song),
                history = (3 until 13).map(::song),
                playLog = (0 until 14).flatMap { i -> List(14 - i) { k -> StoredPlay(song(i), now - (i * 3 + k) * 3_600_000L, 200) } },
                playlists = listOf(
                    StoredPlaylist(name = "Night drive", songs = (0..5).map(::song), note = "Late, loud, windows down.", accent = 0xFF5C3CFF.toInt(), glyph = "MOON"),
                    StoredPlaylist(name = "Focus", songs = (6..11).map(::song), note = "Nothing in the way.", accent = 0xFF14A0AA.toInt(), glyph = "HEADPHONES"),
                    StoredPlaylist(name = "Gym", songs = (12..17).map(::song), note = "Heavy.", accent = 0xFFF05A28.toInt(), glyph = "ROCKET"),
                ),
                localFiles = listOf(
                    StoredSong("local:a1.mp3", "Demo tape 07", "Me", "Bedroom sessions", "2:41", cover(7)),
                    StoredSong("local:a2.flac", "Voice memo — chorus idea", "Me", null, "0:58", cover(8)),
                    StoredSong("local:a3.mp3", "Sunset loop", "Me", "Beats", "1:52", cover(9)),
                    StoredSong("local:a4.m4a", "Road trip mix part 1", "Various", null, "4:20", cover(10)),
                ),
            )
        }
        app.togglePin("artist:UC_demo_neon", "Neon Static", "Artist", cover(0))
        app.togglePinSong(song(1).toItem())
        app.togglePinSong(song(5).toItem())
        app.shelves = listOf(
            Shelf("Quick picks", songs = (0 until 12).map { song(it).toItem() }),
            Shelf("Fresh this week", songs = (6 until 14).map { song(it).toItem() }),
            Shelf("Mixes for you", cards = (1..8).map { PlaylistCard("PLdemo$it", listOf("Chill mix", "Deep focus", "Night drive", "Summer haze", "Gym", "Road trip", "Late study", "Sunrise")[it - 1], "Mix", cover(it + 2)) }),
        )
        app.homeLoading = false
        // Текст для каждого трека: строка на каждые два такта (4 с при 120 BPM быстрее, чем кадры сцен): строка = 2 с.
        names.indices.forEach { i ->
            app.lyrics.seed("demo$i", Lyrics(null, lyricsLines.mapIndexed { k, line -> LyricLine(k * 2_000L, line) }))
        }
        val queue = (0 until 9).map { song(it).toItem() }
        app.player.demoShow(queue, 0, true)
        app.player.duration = 204.0
        app.player.position = 0.0
        return app
    }

    // ------------------------------------------------------------ режиссёр

    /** Состояние поверх приложения: курсор, подписи, титры, вспышка и камера. Всё меняется из сценария. */
    private class Director {
        var cursor by mutableStateOf(Offset(960f, 540f))
        var cursorVisible by mutableStateOf(false)
        var clickAt by mutableDoubleStateOf(-10.0)
        var clickPos by mutableStateOf(Offset.Zero)
        var time by mutableDoubleStateOf(0.0)
        var caption by mutableStateOf("")
        var captionSub by mutableStateOf("")
        var captionAt by mutableDoubleStateOf(-10.0)
        var scale by mutableStateOf(1.0f)
        var focus by mutableStateOf(Offset(0.5f, 0.5f))
        var flash by mutableStateOf(0f)
        var title by mutableStateOf(0.0)
        var windowShown by mutableStateOf(1f)
        var endCard by mutableDoubleStateOf(-1.0)
        var dim by mutableStateOf(0f)
        var key by mutableStateOf("")
        var keyAt by mutableDoubleStateOf(-10.0)
        var sceneAt by mutableDoubleStateOf(-10.0)
        var captionEnd by mutableDoubleStateOf(0.0)
        var captionIndex by mutableStateOf(1)
    }

    private fun ease(x: Double) = FastOutSlowInEasing.transform(x.coerceIn(0.0, 1.0).toFloat()).toDouble()

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    // ------------------------------------------------------------ сцена (окно, фон, надписи)

    private val calm = androidx.compose.animation.core.CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

    /** Спокойное затухание в конце, как в Hairline: быстро в начале, мягко у цели. */
    private fun out(x: Double) = calm.transform(x.coerceIn(0.0, 1.0).toFloat()).toDouble()

    private val blue = Color(0xFF4A7DFB)
    private val ink = Color(0xFF050507)
    private val muted = Color(0xFFA3A3AD)

    @androidx.compose.runtime.Composable
    private fun Stage(app: AppState, d: Director) {
        val t = d.time
        Box(Modifier.fillMaxSize().background(ink)) {
            // Фон: синее свечение, которое медленно дышит, и точечная сетка.
            Canvas(Modifier.fillMaxSize()) {
                val breathe = 1f + 0.05f * sin(t.toFloat() * 0.9f)
                drawRect(Brush.radialGradient(0f to blue.copy(alpha = 0.28f), 1f to Color.Transparent, center = Offset(size.width * 0.5f, size.height * 0.42f), radius = size.width * 0.7f * breathe))
                val step = 48.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    var y = 0f
                    while (y < size.height) { drawCircle(Color.White.copy(alpha = 0.06f), 1.4f, Offset(x, y)); y += step }
                    x += step
                }
            }
            // Камера: приближение к окну, лёгкий дрейф и «вдох» в начале каждой сцены.
            val sinceScene = t - d.sceneAt
            val kick = 0.035f * (1f - out(sinceScene / 0.9).toFloat())
            val drift = 0.012f * sin(t.toFloat() * 0.7f)
            // Появление окна из вступления: из 0.9 в рабочий масштаб, без вспышек.
            val enter = out((t - 3.4) / 2.6).toFloat()
            val entrance = if (t < 6.0) 0.88f + 0.12f * enter else 1f
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val sc = (d.scale + kick + drift) * entrance
                    scaleX = sc; scaleY = sc
                    translationX = (d.focus.x - 0.5f) * -90f * (d.scale - 1f) * 6f + drift * 120f
                    translationY = (d.focus.y - 0.5f) * -90f * (d.scale - 1f) * 6f
                    transformOrigin = TransformOrigin(d.focus.x, d.focus.y)
                    alpha = d.windowShown * (if (t < 6.0) enter else 1f)
                },
                contentAlignment = Alignment.Center,
            ) {
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.size(1664.dp, 936.dp).shadow(80.dp, shape, ambientColor = blue, spotColor = blue).clip(shape)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), shape).background(Color(0xFF0A0A0C)),
                ) {
                    CaptionBar(d)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        // 1664 px ширины окна = 1280 dp: так интерфейс не мелкий на кадре 1080p (и в черновике тоже).
                        CompositionLocalProvider(LocalDensity provides Density(1.3f * scale)) { App(app) }
                    }
                }
            }
            PromoOverlays(d)
        }
    }

    /** Строка заголовка окна: точки, подпись сцены «NN ❯ заголовок | подсказка» и линия, что вычерчивается под ней. */
    @androidx.compose.runtime.Composable
    private fun CaptionBar(d: Director) {
        val t = d.time
        val age = t - d.captionAt
        val line = out(age / 0.8).toFloat()
        val text = out((age - 0.15) / 0.55).toFloat()
        val gone = if (d.captionEnd > 0 && t > d.captionEnd - 0.35) out((t - (d.captionEnd - 0.35)) / 0.35).toFloat() else 0f
        Box(
            Modifier.fillMaxWidth().height(58.dp).background(Color(0xFF0D0D10)).drawWithContent {
                drawContent()
                drawRect(Color.White.copy(alpha = 0.08f), Offset(0f, size.height - 1f), androidx.compose.ui.geometry.Size(size.width, 1f))
                if (d.caption.isNotEmpty()) drawRect(blue.copy(alpha = 0.9f * (1f - gone)), Offset(0f, size.height - 1.5f), androidx.compose.ui.geometry.Size(size.width * line, 1.5f))
            }.padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { Box(Modifier.size(11.dp).background(Color(0xFF3A3A44), CircleShape)); Box(Modifier.width(7.dp)) }
            }
            if (d.caption.isNotEmpty() && age >= 0) {
                Row(Modifier.graphicsLayer { alpha = text * (1f - gone); translationY = (1f - text) * 12f - gone * 8f }, verticalAlignment = Alignment.CenterVertically) {
                    Txt("%02d".format(d.captionIndex), color = Color.White.copy(alpha = 0.45f), size = 14, pixel = true)
                    Box(Modifier.width(10.dp)); Txt("❯", color = blue, size = 14, pixel = true); Box(Modifier.width(14.dp))
                    Txt(d.caption, color = Color.White, size = 30, pixel = true)
                    if (d.captionSub.isNotEmpty()) { Box(Modifier.width(22.dp)); Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.25f))); Box(Modifier.width(22.dp)); Txt(d.captionSub, color = muted, size = 13, pixel = true) }
                }
            }
            Row(Modifier.align(Alignment.CenterEnd)) { Txt("W0Y WITH DESKTOP", color = Color(0xFF6B6B76), size = 11, pixel = true) }
        }
    }

    /** Рамка, которая «рисует себя»: от верхнего левого угла по часовой на долю [p]. */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(p: Float, radius: Float, color: Color, width: Float = 1.5f) {
        if (p <= 0f) return
        val path = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(radius)))
        }
        val m = androidx.compose.ui.graphics.PathMeasure().also { it.setPath(path, false) }
        val seg = androidx.compose.ui.graphics.Path()
        m.getSegment(0f, m.length * p.coerceIn(0f, 1f), seg, true)
        drawPath(seg, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }

    @androidx.compose.runtime.Composable
    private fun androidx.compose.foundation.layout.BoxScope.PromoOverlays(d: Director) {
        val t = d.time
        // Переход между сценами: тонкая вертикальная линия проходит слева направо и тянет за собой слабый свет.
        val wipe = (t - d.sceneAt) / 0.7
        if (wipe in 0.0..1.0 && d.sceneAt > 6.5) {
            val k = out(wipe).toFloat()
            val fade = (1f - ((wipe - 0.7) / 0.3).toFloat().coerceIn(0f, 1f))
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * k
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to blue.copy(alpha = 0.16f * fade), startX = x - 260f, endX = x), Offset(x - 260f, 0f), androidx.compose.ui.geometry.Size(260f, size.height))
                drawRect(Color.White.copy(alpha = 0.85f * fade), Offset(x, 0f), androidx.compose.ui.geometry.Size(1.5f, size.height))
            }
        }
        // Нажатая клавиша: у каждой клавиши своя плашка с тонкой рамкой.
        val keyAge = t - d.keyAt
        if (d.key.isNotEmpty() && keyAge in 0.0..1.4) {
            val a = (out(keyAge / 0.25) * (1.0 - out((keyAge - 1.0) / 0.4))).toFloat()
            Row(Modifier.align(Alignment.TopCenter).padding(top = 76.dp).graphicsLayer { alpha = a; translationY = (1f - a) * -14f }, verticalAlignment = Alignment.CenterVertically) {
                d.key.split(" + ").forEachIndexed { i, k ->
                    if (i > 0) { Box(Modifier.width(10.dp)); Txt("+", color = muted, size = 18, pixel = true); Box(Modifier.width(10.dp)) }
                    Box(Modifier.background(Color(0xE60A0A0C), RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).padding(horizontal = 22.dp, vertical = 10.dp)) { Txt(k, color = Color.White, size = 24, pixel = true) }
                }
            }
        }
        // Курсор: двойное кольцо от клика и сам указатель, на клике слегка «нажимается».
        if (d.cursorVisible) {
            val c = d.cursor
            val clickAge = t - d.clickAt
            val press = if (clickAge in 0.0..0.18) 0.86f + 0.14f * (clickAge / 0.18).toFloat() else 1f
            Canvas(Modifier.fillMaxSize()) {
                if (clickAge in 0.0..0.7) {
                    val k = out(clickAge / 0.7).toFloat()
                    drawCircle(blue.copy(alpha = 0.9f * (1f - k)), 8.dp.toPx() + 46.dp.toPx() * k, d.clickPos, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    val k2 = out((clickAge - 0.1) / 0.7).toFloat()
                    if (clickAge > 0.1) drawCircle(Color.White.copy(alpha = 0.5f * (1f - k2)), 8.dp.toPx() + 30.dp.toPx() * k2, d.clickPos, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                }
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f); lineTo(0f, 30f); lineTo(7f, 23f); lineTo(12f, 34f); lineTo(17f, 32f); lineTo(12f, 21f); lineTo(21f, 21f); close()
                }
                withTransform({ translate(c.x, c.y); scale(press, press, Offset.Zero) }) {
                    drawPath(p, Color.Black.copy(alpha = 0.45f), style = androidx.compose.ui.graphics.drawscope.Fill)
                    withTransform({ translate(-1f, -1f) }) { drawPath(p, Color.White) }
                }
            }
        }
        TitleCards(d)
        if (d.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = d.dim)))
    }

    @androidx.compose.runtime.Composable
    private fun androidx.compose.foundation.layout.BoxScope.TitleCards(d: Director) {
        val t = d.time
        if (t < 6.4) {
            // Вступление целиком из тонких линий: сетка растёт от центра, кольца расходятся, буквы всплывают по одной.
            val fadeOut = out((t - 5.2) / 1.0).toFloat()
            val grow = out(t / 4.5).toFloat()
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - fadeOut }.background(ink.copy(alpha = 1f - 0.9f * fadeOut)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2; val cy = size.height / 2
                    drawRect(Brush.radialGradient(0f to blue.copy(alpha = 0.50f * grow), 1f to Color.Transparent, center = Offset(cx, cy), radius = size.width * (0.2f + 0.5f * grow)))
                    // Линии сетки: появляются от середины к краям.
                    val step = 96.dp.toPx()
                    val reach = size.width * 0.75f * grow
                    var gx = cx % step
                    while (gx < size.width) { val a = (1f - kotlin.math.abs(gx - cx) / reach).coerceIn(0f, 1f) * 0.10f; if (a > 0f) drawLine(Color.White.copy(alpha = a), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += step }
                    var gy = cy % step
                    while (gy < size.height) { val a = (1f - kotlin.math.abs(gy - cy) / (reach * 0.6f)).coerceIn(0f, 1f) * 0.10f; if (a > 0f) drawLine(Color.White.copy(alpha = a), Offset(0f, gy), Offset(size.width, gy), 1f); gy += step }
                    // Кольца.
                    listOf(0.0, 0.7, 1.4).forEach { delay ->
                        val k = out((t - 0.4 - delay) / 3.6).toFloat()
                        if (k > 0f) drawCircle(Color.White.copy(alpha = 0.22f * (1f - k)), 80.dp.toPx() + 420.dp.toPx() * k, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                    }
                    // Уголки кадра.
                    val m = 60.dp.toPx(); val len = 46.dp.toPx() * out((t - 0.2) / 1.2).toFloat()
                    val ca = Color.White.copy(alpha = 0.5f)
                    listOf(Offset(m, m) to Offset(1f, 1f), Offset(size.width - m, m) to Offset(-1f, 1f), Offset(m, size.height - m) to Offset(1f, -1f), Offset(size.width - m, size.height - m) to Offset(-1f, -1f)).forEach { (o, dir) ->
                        drawLine(ca, o, Offset(o.x + dir.x * len, o.y), 1.5f); drawLine(ca, o, Offset(o.x, o.y + dir.y * len), 1.5f)
                    }
                    // Тонкая линия хода к дропу по нижнему краю.
                    drawRect(Color.White.copy(alpha = 0.12f), Offset(m, size.height - m * 1.4f), androidx.compose.ui.geometry.Size(size.width - 2 * m, 1.5f))
                    drawRect(blue, Offset(m, size.height - m * 1.4f), androidx.compose.ui.geometry.Size((size.width - 2 * m) * (t / 6.0).toFloat().coerceIn(0f, 1f), 1.5f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        "w0y".forEachIndexed { i, ch ->
                            val a = out((t - 0.5 - i * 0.14) / 0.9).toFloat()
                            Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 46f }) { Txt(ch.toString(), color = Color.White, size = 190, pixel = true) }
                        }
                    }
                    // Линия под названием вычерчивается слева направо.
                    Canvas(Modifier.size(520.dp, 2.dp)) { drawRect(blue, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width * out((t - 1.5) / 1.1).toFloat(), 2f)) }
                    Box(Modifier.height(14.dp))
                    val typed = "with desktop"
                    val shown = ((t - 2.2) / 1.4 * typed.length).toInt().coerceIn(0, typed.length)
                    Txt(typed.take(shown) + if (shown < typed.length && (t * 2).toInt() % 2 == 0) "▌" else "", color = blue, size = 56, pixel = true)
                }
                Row(Modifier.align(Alignment.TopStart).padding(start = 76.dp, top = 74.dp).graphicsLayer { alpha = out((t - 1.0) / 0.8).toFloat() }) { Txt("V0.0.4 BETA-1", color = muted, size = 13, pixel = true) }
                Row(Modifier.align(Alignment.TopEnd).padding(end = 76.dp, top = 74.dp).graphicsLayer { alpha = out((t - 1.2) / 0.8).toFloat() }) { Txt("WINDOWS · LINUX", color = muted, size = 13, pixel = true) }
            }
            // Дроп: тонкая синяя линия проходит экран сверху вниз и открывает окно.
            val scan = (t - 6.0) / 0.7
            if (scan in 0.0..1.0) {
                val k = out(scan).toFloat()
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height * k
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to blue.copy(alpha = 0.22f * (1f - k)), startY = y - 300f, endY = y), Offset(0f, y - 300f), androidx.compose.ui.geometry.Size(size.width, 300f))
                    drawRect(Color.White.copy(alpha = 0.9f * (1f - k)), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1.5f))
                }
            }
        }
        if (d.endCard >= 0) {
            val e = d.endCard
            val k = out(e / 0.9).toFloat()
            Box(Modifier.fillMaxSize().background(ink.copy(alpha = 0.94f * k)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.radialGradient(0f to blue.copy(alpha = 0.40f * k), 1f to Color.Transparent, center = Offset(size.width / 2, size.height * 0.45f), radius = size.width * 0.55f))
                    val m = 60.dp.toPx()
                    // Рамка вычерчивает себя по периметру.
                    withTransform({ translate(m, m) }) {
                        val inner = androidx.compose.ui.geometry.Size(size.width - 2 * m, size.height - 2 * m)
                        val p = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, inner.width, inner.height, androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))) }
                        val meas = androidx.compose.ui.graphics.PathMeasure().also { it.setPath(p, false) }
                        val seg = androidx.compose.ui.graphics.Path()
                        meas.getSegment(0f, meas.length * out(e / 1.8).toFloat(), seg, true)
                        drawPath(seg, Color.White.copy(alpha = 0.45f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row { "w0y".forEachIndexed { i, ch -> val a = out((e - 0.3 - i * 0.12) / 0.8).toFloat(); Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 40f }) { Txt(ch.toString(), color = Color.White, size = 150, pixel = true) } } }
                    val a2 = out((e - 0.9) / 0.7).toFloat()
                    Box(Modifier.graphicsLayer { alpha = a2; translationY = (1f - a2) * 24f }) { Txt("with desktop", color = blue, size = 52, pixel = true) }
                    Box(Modifier.height(40.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                        listOf("WINDOWS", "LINUX", "FREE", "NO ACCOUNT").forEachIndexed { i, label ->
                            val a = out((e - 1.3 - i * 0.12) / 0.6).toFloat()
                            Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 18f }.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp)).padding(horizontal = 22.dp, vertical = 10.dp)) { Txt(label, color = Color.White, size = 16, pixel = true) }
                        }
                    }
                    Box(Modifier.height(26.dp))
                    val a3 = out((e - 1.9) / 0.7).toFloat()
                    Box(Modifier.graphicsLayer { alpha = a3; translationY = (1f - a3) * 14f }) { Txt("github.com/texfi-w0y/texfi-w0y", color = muted, size = 18, pixel = true) }
                }
            }
        }
    }

    // ------------------------------------------------------------ сценарий

    private class Script {
        val events = ArrayList<Pair<Double, () -> Unit>>()
        var next = 0
        fun at(t: Double, block: () -> Unit) { events += t to block }
        fun sort() { events.sortBy { it.first } }
    }

    @Test
    fun render() {
        val target = out ?: return
        // «8.5,14,20» — снять только эти кадры в папку target; иначе целиком в mp4.
        val stills = System.getProperty("w0y.promo.stills")?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.sorted()
        val range = System.getProperty("w0y.promo.range")?.split("-")?.mapNotNull { it.toDoubleOrNull() }
        val from = range?.getOrNull(0) ?: 0.0
        val to = range?.getOrNull(1) ?: total
        val app = newApp()
        val d = Director()
        runDesktopComposeUiTest(w, h) {
            mainClock.autoAdvance = false
            setContent { CompositionLocalProvider(LocalDensity provides Density(scale)) { Stage(app, d) } }
            val s = Script()
            val tools = Tools(this, app, d)
            buildScript(s, tools)
            s.sort()
            repeat(6) { mainClock.advanceTimeByFrame() }
            Thread.sleep(400)
            var ff: Process? = null
            if (stills == null) {
                ff = ProcessBuilder(
                    "ffmpeg", "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "bgra", "-s", "${w}x$h", "-r", "$fps", "-i", "-",
                    "-c:v", "libx264", "-preset", "medium", "-crf", "15", "-pix_fmt", "yuv420p", "-movflags", "+faststart", target,
                ).redirectError(ProcessBuilder.Redirect.INHERIT).start()
            } else {
                File(target).mkdirs()
            }
            val buf = ByteArray(w * h * 4)
            val stillFrames = stills?.map { Math.round(it * fps).toInt() }
            val endFrame = if (stillFrames != null) stillFrames.max() else Math.round(to * fps).toInt()
            val firstFrame = Math.round(from * fps).toInt()
            var frame = 0
            while (frame <= endFrame) {
                val t = frame / fps.toDouble()
                d.time = t
                tools.beforeFrame(t)
                while (s.next < s.events.size && s.events[s.next].first <= t + 1e-6) { s.events[s.next].second(); s.next++ }
                tools.animate(t)
                // Стоп-кадры: далёкое прошлое пролистывается крупным шагом, последняя секунда перед кадром — обычным.
                val near = stillFrames != null && stillFrames.any { it - frame in 0..28 }
                var next = frame + if (stillFrames != null && !near) 3 else 1
                if (stillFrames != null && !near) stillFrames.firstOrNull { it - 28 > frame }?.let { if (next > it - 28) next = it - 28 }
                mainClock.advanceTimeBy(Math.round(next * 1000.0 / fps) - Math.round(frame * 1000.0 / fps))
                val capture = if (stillFrames != null) frame in stillFrames else frame >= firstFrame
                if (capture) {
                    val img = onRoot().captureToImage().toAwtImage()
                    if (stillFrames != null) {
                        javax.imageio.ImageIO.write(img, "png", File(target, "still_%05.1f.png".format(t)))
                    } else {
                        val px = (img.raster.dataBuffer as java.awt.image.DataBufferInt).data
                        var o = 0
                        for (v in px) { buf[o] = v.toByte(); buf[o + 1] = (v shr 8).toByte(); buf[o + 2] = (v shr 16).toByte(); buf[o + 3] = -1; o += 4 }
                        ff!!.outputStream.write(buf)
                    }
                }
                frame = next
            }
            ff?.outputStream?.close()
            ff?.waitFor()
        }
    }

    // ------------------------------------------------------------ инструменты сценария

    @OptIn(ExperimentalTestApi::class)
    private inner class Tools(val ui: androidx.compose.ui.test.ComposeUiTest, val app: AppState, val d: Director) {
        private var moveFrom = d.cursor
        private var moveTo = d.cursor
        private var moveStart = 0.0
        private var moveEnd = 0.0
        private var songStart = 0.0
        private var songBase = 0.0
        private var songPlaying = false
        var camTarget = 1.12f
        var camFocus = Offset(0.5f, 0.5f)
        private var camFrom = 1.0f
        private var camFromFocus = Offset(0.5f, 0.5f)
        private var camStart = 0.0
        private var camEnd = 0.01

        fun beforeFrame(t: Double) {}

        /** Позиция узла с таким текстом в координатах кадра, null — на экране его нет. */
        fun where(text: String, index: Int = 0): Offset? = runCatching {
            val nodes = ui.onAllNodes(hasText(text, substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes()
            nodes.getOrNull(index)?.boundsInRoot?.center
        }.getOrNull()

        fun cursorTo(p: Offset?, t: Double, dur: Double = 0.55) {
            if (p == null) return
            moveFrom = d.cursor; moveTo = p; moveStart = t; moveEnd = t + dur
            d.cursorVisible = true
        }

        fun click(t: Double, real: Boolean = true) {
            d.clickAt = t; d.clickPos = d.cursor
            if (real) runCatching { ui.onRoot().performMouseInput { moveTo(d.cursor); press(); release() } }
        }

        private var wheelFrom = 0.0
        private var wheelTo = 0.0
        private var wheelPerSec = 0f
        private var wheelAt = Offset.Zero

        /** Колесо мыши над точкой [at] с [from] по [to] секунду: всего [total] пикселей прокрутки. */
        fun wheel(at: Offset, from: Double, to: Double, total: Float) { wheelAt = at; wheelFrom = from; wheelTo = to; wheelPerSec = total / (to - from).toFloat() }

        fun key(t: Double, label: String) { d.key = label; d.keyAt = t }

        /** Начало сцены: подпись «NN ❯ заголовок», линия-переход и «вдох» камеры. */
        fun scene(t: Double, index: Int, title: String, sub: String, end: Double) {
            d.caption = title; d.captionSub = sub; d.captionAt = t; d.sceneAt = t; d.captionIndex = index; d.captionEnd = end
        }

        fun caption(t: Double, title: String, sub: String = "") { d.caption = title; d.captionSub = sub; d.captionAt = t }

        fun camera(t: Double, scale: Float, focus: Offset = Offset(0.5f, 0.5f), dur: Double = 0.7) {
            camFrom = d.scale; camFromFocus = d.focus; camTarget = scale; camFocus = focus; camStart = t; camEnd = t + dur
        }

        /** Трек «играет»: позиция бежит с секунды [from] (кратной 2 — строки текста меняются на каждом такте). */
        fun playing(t: Double, from: Double, on: Boolean = true) {
            songStart = t; songBase = from; songPlaying = on
            app.player.playing = on
        }

        fun show(index: Int, queue: List<SongItem>? = null) {
            val q = queue ?: (0 until 9).map { song(it).toItem() }
            app.player.demoShow(q, index, songPlaying)
        }

        fun animate(t: Double) {
            if (moveEnd > moveStart) {
                val k = ease((t - moveStart) / (moveEnd - moveStart))
                d.cursor = Offset(lerp(moveFrom.x.toDouble(), moveTo.x.toDouble(), k).toFloat(), lerp(moveFrom.y.toDouble(), moveTo.y.toDouble(), k).toFloat())
                if (d.cursorVisible) runCatching { ui.onRoot().performMouseInput { moveTo(d.cursor) } }
            }
            if (camEnd > camStart) {
                val k = ease((t - camStart) / (camEnd - camStart)).toFloat()
                d.scale = camFrom + (camTarget - camFrom) * k
                d.focus = Offset(camFromFocus.x + (camFocus.x - camFromFocus.x) * k, camFromFocus.y + (camFocus.y - camFromFocus.y) * k)
            }
            if (t >= 43.0) d.endCard = t - 43.0
            if (songPlaying) {
                app.player.position = songBase + (t - songStart)
            }
            if (t in wheelFrom..wheelTo && wheelPerSec != 0f) {
                runCatching { ui.onRoot().performMouseInput { moveTo(wheelAt); scroll(wheelPerSec / 30f) } }
            }
        }
    }

    // ------------------------------------------------------------ раскадровка

    private fun buildScript(s: Script, x: Tools) {
        val app = x.app
        val d = x.d
        val results = SearchResults(
            songs = listOf(song(0), song(10), song(3), song(14), song(7), song(11), song(1)).map { it.toItem() },
            albums = listOf(PlaylistCard("MPREb_d1", "Afterglow (Deluxe)", "Album · Neon Static · 2026", cover(0)), PlaylistCard("MPREb_d2", "Blue Hour", "Album · Neon Static · 2025", cover(10)), PlaylistCard("MPREb_d3", "Static Dreams", "EP · Neon Static · 2024", cover(4))),
            artists = listOf(ArtistCard("UC_demo_neon", "Neon Static", "1.2M subscribers", cover(0)), ArtistCard("UC_demo_two", "Static Bloom", "640K subscribers", cover(6))),
        )
        val videos = SearchResults(songs = results.songs.take(5).map { it.copy(title = it.title + " (Official Video)") }, videos = true)
        val typed = "neon static"
        // 0–6 с: вступление (титры рисуются по времени). На дропе — вспышка и приближение к окну.
        s.at(0.0) { d.scale = 1.0f; app.open(Screen.Home); x.playing(0.0, 8.0, false) }
        s.at(6.0) { x.camera(6.0, 1.12f, dur = 0.9) }
        // 6–10 с: Главная — быстрый набор, играет по одному клику, дальше быстрый выбор и рекомендации.
        s.at(6.0) { x.scene(6.0, 1, "Your music. One click.", "Speed dial · Quick picks", 10.0) }
        s.at(7.0) { x.cursorTo(x.where("Afterglow", 0), 7.0) }
        s.at(8.0) { x.click(8.0, real = false); x.playing(8.0, 8.0); x.show(0) }
        s.at(8.4) { x.wheel(Offset(960f, 500f), 8.4, 9.8, 1500f) }
        // 10–14 с: умный поиск.
        s.at(10.0) { x.scene(10.0, 2, "Smart search", "Tracks · videos · albums · artists", 14.0); x.cursorTo(x.where("search", 0), 10.0, 0.4) }
        s.at(10.4) { x.click(10.4); app.searchModel.immediate = true }
        s.at(10.4) {
            (2..typed.length).forEach { n -> app.searchModel.seed(typed.take(n), SearchFilter.ALL, results) }
            SearchFilter.entries.forEach { f -> app.searchModel.seed(typed, f, if (f == SearchFilter.VIDEOS) videos else results) }
        }
        typed.forEachIndexed { i, _ -> s.at(10.9 + i * 0.1) { app.searchModel.query = typed.take(i + 1) } }
        s.at(12.0) { x.cursorTo(x.where("videos", 0), 12.0, 0.4) }
        s.at(12.4) { x.click(12.4) }
        s.at(12.8) { x.cursorTo(x.where("albums", 0), 12.8, 0.3) }
        s.at(13.1) { x.click(13.1) }
        s.at(13.4) { x.cursorTo(x.where("artists", 0), 13.4, 0.3) }
        s.at(13.7) { x.click(13.7) }
        // 14–20 с: большой плеер с крупным текстом.
        s.at(14.0) { x.scene(14.0, 3, "Lyrics in focus", "Synced · translated · sing along", 20.0); x.cursorTo(x.where("player", 0), 14.0, 0.4); x.playing(14.0, 14.0) }
        s.at(14.4) { x.click(14.4); app.open(Screen.Player); x.camera(14.4, 1.14f, Offset(0.6f, 0.5f), 0.8) }
        s.at(17.0) { x.camera(17.0, 1.25f, Offset(0.72f, 0.45f), 1.0) }
        s.at(18.4) { x.camera(18.4, 1.12f, Offset(0.5f, 0.5f), 0.8); app.setSt { it.copy(hlCoverShape = CoverShape.DISC, hlCoverSpin = true) } }
        s.at(19.0) { app.setSt { it.copy(hlPlayerBack = PlayerBack.AURORA) } }
        // 20–24 с: очередь и история.
        s.at(20.0) { x.scene(20.0, 4, "Queue & history", "Drag to reorder · what is up next", 24.0); x.cursorTo(x.where("queue", 0), 20.0, 0.4) }
        s.at(20.4) { x.click(20.4) }
        s.at(21.6) { x.cursorTo(x.where("Honey Signal", 0)?.let { Offset(it.x - 520f, it.y) }, 21.6, 0.5) }
        s.at(22.2) { app.player.moveInQueue(6, 2) }
        s.at(22.8) { x.cursorTo(x.where("already played", 0), 22.8, 0.4) }
        s.at(23.2) { x.click(23.2) }
        s.at(23.6) { x.cursorTo(x.where("history", 0), 23.6, 0.3) }
        s.at(23.9) { x.click(23.9) }
        // 24–28 с: свои версии трека: скорость, тон, реверберация, эквалайзер.
        s.at(24.0) { x.scene(24.0, 5, "Make your own version", "Speed · pitch · reverb · 10-band equaliser", 28.0); x.cursorTo(x.where("sound", 0), 24.0, 0.4) }
        s.at(24.4) { x.click(24.4) }
        s.at(25.0) { x.cursorTo(x.where("slowed", 0), 25.0, 0.4) }
        s.at(25.4) { x.click(25.4) }
        s.at(26.2) { x.cursorTo(x.where("cave", 0), 26.2, 0.4) }
        s.at(26.6) { x.click(26.6) }
        s.at(27.0) { x.wheel(Offset(1300f, 600f), 27.0, 27.9, 700f) }
        // 28–32 с: подставка.
        s.at(28.0) { x.scene(28.0, 6, "Stand mode", "Glow · clock · lyrics on your desk", 32.0); x.key(28.0, "B"); app.setSt { it.copy(hlCoverShape = CoverShape.ROUNDED, hlCoverSpin = false) }; app.stand.enter() }
        s.at(31.9) { app.stand.off() }
        // 32–36 с: свои файлы.
        s.at(32.0) { x.scene(32.0, 7, "Your own files", "mp3 · flac · m4a — tags, covers, playlists", 36.0); app.open(Screen.Library); x.cursorTo(x.where("my files", 0), 32.0, 0.5) }
        s.at(32.6) { x.click(32.6); app.open(Screen.LocalFiles) }
        // 36–40 с: оформление и панели.
        s.at(36.0) { x.scene(36.0, 8, "Make it yours", "Light · dark · cover shapes · hide any panel", 40.0); app.open(Screen.Player); app.setSt { it.copy(hlLayout = PlayerLayout.COVER, hlCoverShape = CoverShape.SQUARE, hlPlayerBack = PlayerBack.GLOW) } }
        s.at(37.0) { app.setSt { it.copy(theme = ThemeMode.LIGHT) } }
        s.at(38.0) { app.setSt { it.copy(theme = ThemeMode.DARK) }; x.key(38.0, "Ctrl + B"); app.setSt { it.copy(sidebarHidden = true) } }
        s.at(38.8) { x.key(38.8, "Ctrl + Shift + B"); app.setSt { it.copy(barHidden = true) } }
        s.at(39.4) { app.setSt { it.copy(sidebarHidden = false, barHidden = false, hlCoverShape = CoverShape.ROUNDED) } }
        // 40–43 с: клавиатура.
        s.at(40.0) { x.scene(40.0, 9, "Keyboard first", "Hotkeys · media keys · mini player · tray", 43.0); app.open(Screen.Home); x.key(40.0, "?"); Actions.helpOpen = true }
        s.at(42.8) { Actions.helpOpen = false }
        // 43–46 с: финал.
        s.at(43.0) { d.caption = "" }
        s.sort()
    }
}
