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
        var rot by mutableStateOf(0f)
        var focus by mutableStateOf(Offset(0.5f, 0.5f))
        var hitAt by mutableDoubleStateOf(-10.0)
        var flashAt by mutableDoubleStateOf(-10.0)
        var flashPow by mutableStateOf(0f)
        var word by mutableStateOf("")
        var wordAt by mutableDoubleStateOf(-10.0)
        var fx by mutableStateOf(Fx())
        var windowShown by mutableStateOf(1f)
        var endCard by mutableDoubleStateOf(-1.0)
        var dim by mutableStateOf(0f)
        var key by mutableStateOf("")
        var keyAt by mutableDoubleStateOf(-10.0)
        var sceneAt by mutableDoubleStateOf(-10.0)
        var captionEnd by mutableDoubleStateOf(0.0)
        var captionIndex by mutableStateOf(1)
        /** Преобразование, с которым слой окна был нарисован в последний раз (для пересчёта координат курсора). */
        var drawnCam: Cam = Cam(1f, 0f, 0f, 0f, 960f, 540f)
    }

    /** Эффект перехода между сценами: меняется только по времени. */
    private data class Fx(val scale: Float = 1f, val rotX: Float = 0f, val rotY: Float = 0f, val rotZ: Float = 0f, val tx: Float = 0f, val alpha: Float = 1f)

    private enum class Tr { FLIPY, FLIPX, SPIN, ZOOM, WHIP }

    /** Камера как матрица: поворот и масштаб вокруг точки (ox, oy), потом сдвиг. */
    private class Cam(val s: Float, val rz: Float, val tx: Float, val ty: Float, val ox: Float, val oy: Float) {
        private val c = kotlin.math.cos(Math.toRadians(rz.toDouble())).toFloat()
        private val n = sin(Math.toRadians(rz.toDouble())).toFloat()
        fun map(p: Offset): Offset { val dx = (p.x - ox) * s; val dy = (p.y - oy) * s; return Offset(ox + c * dx - n * dy + tx, oy + n * dx + c * dy + ty) }
        fun inv(p: Offset): Offset { val dx = p.x - ox - tx; val dy = p.y - oy - ty; return Offset(ox + (c * dx + n * dy) / s, oy + (-n * dx + c * dy) / s) }
    }

    private fun ease(x: Double) = FastOutSlowInEasing.transform(x.coerceIn(0.0, 1.0).toFloat()).toDouble()

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    /** Быстрый выход с «перелётом» — удар. */
    private fun back(x: Double): Double { val p = x.coerceIn(0.0, 1.0) - 1.0; return 1.0 + 3.2 * p * p * p + 2.2 * p * p }

    /** Разгон: медленно, потом резко. */
    private fun inn(x: Double): Double { val p = x.coerceIn(0.0, 1.0); return p * p * p }

    /** Эффект перехода в момент [t]: 0.16 с окно «уходит», потом 0.4 с возвращается с переворотом и отскоком. */
    private fun fxAt(t: Double, trs: List<Pair<Double, Tr>>): Fx {
        for ((t0, kind) in trs) {
            val tt = t - t0
            if (tt < -0.16 || tt > 0.4) continue
            if (tt < 0) {
                val p = inn((tt + 0.16) / 0.16).toFloat()
                return when (kind) {
                    Tr.FLIPY -> Fx(rotY = 90f * p, scale = 1f - 0.1f * p)
                    Tr.FLIPX -> Fx(rotX = -90f * p, scale = 1f - 0.1f * p)
                    Tr.SPIN -> Fx(rotZ = -80f * p, scale = 1f - 0.6f * p, alpha = 1f - p)
                    Tr.ZOOM -> Fx(scale = 1f + 2.4f * p, alpha = 1f - p)
                    Tr.WHIP -> Fx(tx = -1.1f * p)
                }
            }
            val q = tt / 0.4
            val b = back(q).toFloat()
            val al = (q * 5).coerceAtMost(1.0).toFloat()
            return when (kind) {
                Tr.FLIPY -> Fx(rotY = -90f * (1f - b))
                Tr.FLIPX -> Fx(rotX = 90f * (1f - b))
                Tr.SPIN -> Fx(rotZ = 110f * (1f - b), scale = 0.4f + 0.6f * b, alpha = al)
                Tr.ZOOM -> Fx(scale = 0.45f + 0.55f * b, alpha = al)
                Tr.WHIP -> Fx(tx = 1.1f * (1f - b))
            }
        }
        return Fx()
    }

    /** Итоговая камера окна в кадре [t]: рабочий масштаб + пульс на долю + дрейф + встряска после удара + переход. */
    private fun camOf(d: Director, t: Double): Cam {
        val ph = (t % 0.5) / 0.5
        val live = t in 6.0..43.0
        val pulse = if (live) 1f + (if (t % 2.0 < 0.5) 0.028f else 0.014f) * (1f - out(ph / 0.55).toFloat()) else 1f
        val drift = 0.01f * sin(t.toFloat() * 0.7f)
        val age = t - d.hitAt
        val shake = if (age in 0.0..0.3) (1f - out(age / 0.3).toFloat()) * 11f * scale else 0f
        val fx = d.fx
        val sc = (d.scale + drift) * pulse * fx.scale
        val shift = ((d.scale - 1f) * 3f).coerceIn(0f, 1f)
        return Cam(
            s = sc, rz = d.rot + fx.rotZ,
            tx = (0.5f - d.focus.x) * w * shift + fx.tx * w + shake * sin(age.toFloat() * 75f),
            ty = (0.5f - d.focus.y) * h * shift + shake * 0.6f * sin(age.toFloat() * 63f + 1f),
            ox = d.focus.x * w, oy = d.focus.y * h,
        )
    }

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
        val dens = androidx.compose.ui.platform.LocalDensity.current.density
        Box(Modifier.fillMaxSize().background(ink)) {
            // Фон: синее свечение, вспыхивающее на каждую долю, и точечная сетка.
            val beatGlow = if (t in 6.0..43.0) 0.12f * (1f - out(((t % 0.5) / 0.5) / 0.5).toFloat()) else 0f
            Canvas(Modifier.fillMaxSize()) {
                val breathe = 1f + 0.05f * sin(t.toFloat() * 0.9f)
                drawRect(Brush.radialGradient(0f to blue.copy(alpha = 0.28f + beatGlow), 1f to Color.Transparent, center = Offset(size.width * 0.5f, size.height * 0.42f), radius = size.width * 0.7f * breathe))
                val step = 48.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    var y = 0f
                    while (y < size.height) { drawCircle(Color.White.copy(alpha = 0.06f), 1.4f, Offset(x, y)); y += step }
                    x += step
                }
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val cam = camOf(d, t)
                    d.drawnCam = cam
                    scaleX = cam.s; scaleY = cam.s
                    rotationZ = cam.rz
                    rotationX = d.fx.rotX; rotationY = d.fx.rotY
                    cameraDistance = 22f * dens
                    translationX = cam.tx; translationY = cam.ty
                    transformOrigin = TransformOrigin(d.focus.x, d.focus.y)
                    alpha = if (t < 6.0) 0f else d.windowShown * d.fx.alpha
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
        // Метки долей по нижнему краю: тонкая шкала, текущая доля тянется вверх и горит синим.
        if (t in 6.0..43.0) {
            Canvas(Modifier.fillMaxSize()) {
                val left = size.width * 0.5f - 832f * scale; val step = 22f * scale
                val n = ((t - 6.0) / 0.5).toInt()
                val ph = ((t - 6.0) % 0.5) / 0.5
                for (i in 0..73) {
                    val x = left + i * step
                    val cur = i == n
                    val hgt = (if (cur) 13f - 8f * out(ph / 0.7).toFloat() else if (i % 4 == 0) 7f else 4f) * scale
                    val col = if (cur) blue else Color.White.copy(alpha = if (i < n) 0.38f else 0.14f)
                    drawLine(col, Offset(x, size.height - 30f * scale), Offset(x, size.height - 30f * scale - hgt), 1.5f)
                }
            }
        }
        // Видоискатель: четыре уголка схлопываются к центру на каждом ударе камеры, если она приблизилась.
        val hitAge = t - d.hitAt
        if (hitAge in 0.0..0.55 && d.scale > 1.15f && t < 43.0) {
            val k = out(hitAge / 0.22).toFloat()
            val fade = 1f - inn((hitAge - 0.25) / 0.3).toFloat()
            Canvas(Modifier.fillMaxSize()) {
                val inset = (0.03f + 0.04f * (1f - k)) * size.width
                val len = 46f * scale
                val col = Color.White.copy(alpha = 0.75f * fade)
                listOf(Offset(inset, inset) to Offset(1f, 1f), Offset(size.width - inset, inset) to Offset(-1f, 1f), Offset(inset, size.height - inset) to Offset(1f, -1f), Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f)).forEach { (o, dr) ->
                    drawLine(col, o, Offset(o.x + dr.x * len, o.y), 2f); drawLine(col, o, Offset(o.x, o.y + dr.y * len), 2f)
                }
            }
        }
        // Слово-удар в начале сцены: вылетает крупным, держится долю и уходит вверх.
        val wa = t - d.wordAt
        if (d.word.isNotEmpty() && wa in 0.0..0.85) {
            val inK = back(wa / 0.2).toFloat()
            val outK = inn((wa - 0.5) / 0.3).toFloat()
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * (1f - outK)))) {}
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val cy = size.height / 2; val reach = size.width * 0.42f * out(wa / 0.25).toFloat()
                    val a = 0.8f * (1f - outK)
                    drawLine(blue.copy(alpha = a), Offset(size.width / 2 - reach, cy - 150f * scale), Offset(size.width / 2 + reach, cy - 150f * scale), 2f)
                    drawLine(blue.copy(alpha = a), Offset(size.width / 2 - reach, cy + 150f * scale), Offset(size.width / 2 + reach, cy + 150f * scale), 2f)
                }
                Box(Modifier.graphicsLayer {
                    val sc = 1.8f - 0.8f * inK + 0.25f * outK
                    scaleX = sc; scaleY = sc; rotationZ = -4f * (1f - inK); translationY = -outK * 90f * scale
                    alpha = (wa / 0.05).coerceIn(0.0, 1.0).toFloat() * (1f - outK)
                }) { Txt(d.word, color = Color.White, size = 190, pixel = true) }
            }
        }
        // Вспышка на сильную долю.
        val fa = t - d.flashAt
        if (fa in 0.0..0.4 && d.flashPow > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = d.flashPow * (1f - out(fa / 0.4).toFloat()))))
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
        if (d.cursorVisible && kotlin.math.abs(d.fx.rotX) + kotlin.math.abs(d.fx.rotY) < 1f) {
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
            // Вступление: нарастание к дропу. Линия, уголки, «w0y» ударом на 1.0 с, «with desktop» на 2.0 с,
            // дальше кольца на каждую долю, на 5.0 с надпись летит на камеру и пролетает сквозь неё на дропе.
            val rush = inn((t - 5.0) / 1.0).toFloat()
            val gone = inn((t - 5.55) / 0.45).toFloat()
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - out((t - 5.9) / 0.5).toFloat() }.background(ink), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2; val cy = size.height / 2
                    val grow = out(t / 3.0).toFloat()
                    drawRect(Brush.radialGradient(0f to blue.copy(alpha = 0.30f + 0.25f * grow + 0.2f * rush), 1f to Color.Transparent, center = Offset(cx, cy), radius = size.width * (0.2f + 0.45f * grow + 0.5f * rush)))
                    // Сетка растёт от центра.
                    val step = 96.dp.toPx()
                    val reach = size.width * 0.75f * grow
                    var gx = cx % step
                    while (gx < size.width) { val a = (1f - kotlin.math.abs(gx - cx) / reach).coerceIn(0f, 1f) * 0.10f; if (a > 0f) drawLine(Color.White.copy(alpha = a), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += step }
                    var gy = cy % step
                    while (gy < size.height) { val a = (1f - kotlin.math.abs(gy - cy) / (reach * 0.6f)).coerceIn(0f, 1f) * 0.10f; if (a > 0f) drawLine(Color.White.copy(alpha = a), Offset(0f, gy), Offset(size.width, gy), 1f); gy += step }
                    // Горизонтальная линия разгоняется от центра за первую секунду.
                    val lr = size.width * 0.46f * out(t / 1.0).toFloat()
                    drawLine(Color.White.copy(alpha = 0.55f * (1f - 0.7f * out((t - 1.0) / 0.5).toFloat())), Offset(cx - lr, cy), Offset(cx + lr, cy), 1.5f)
                    // Кольца: по одному на долю с 1.0 с, с 4.0 с вдвое чаще.
                    val births = (2..7).map { 0.5 * it } + (0..7).map { 4.0 + 0.25 * it }
                    births.filter { it <= t }.forEach { b ->
                        val k = out((t - b) / 1.2).toFloat()
                        drawCircle(Color.White.copy(alpha = 0.34f * (1f - k)), 70.dp.toPx() + 520.dp.toPx() * k, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                    }
                    // Уголки кадра защёлкиваются на 1.0 с и дальше сжимаются.
                    val snap = back((t - 0.8) / 0.25).toFloat().coerceAtLeast(0f)
                    val m = (60.dp.toPx() + 40.dp.toPx() * (1f - snap) + 70.dp.toPx() * rush); val len = 56.dp.toPx() * snap
                    val ca = Color.White.copy(alpha = 0.6f)
                    listOf(Offset(m, m) to Offset(1f, 1f), Offset(size.width - m, m) to Offset(-1f, 1f), Offset(m, size.height - m) to Offset(1f, -1f), Offset(size.width - m, size.height - m) to Offset(-1f, -1f)).forEach { (o, dr) ->
                        drawLine(ca, o, Offset(o.x + dr.x * len, o.y), 2f); drawLine(ca, o, Offset(o.x, o.y + dr.y * len), 2f)
                    }
                }
                // Название: удар на 1.0, пульс на каждую долю, рывок к камере с 5.0.
                Column(
                    Modifier.graphicsLayer {
                        val slam = if (t >= 1.0) back((t - 1.0) / 0.22).toFloat() else 0f
                        val beat = if (t >= 2.0) 0.07f * (1f - out(((t % 0.5) / 0.5) / 0.5).toFloat()) else 0f
                        val sc = (2.4f - 1.4f * slam + beat) * (1f + 16f * rush)
                        scaleX = sc; scaleY = sc; rotationZ = -3f * (1f - slam)
                        alpha = (if (t < 1.0) 0f else 1f) * (1f - gone)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Txt("w0y", color = Color.White, size = 230, pixel = true)
                    Canvas(Modifier.size(560.dp, 2.dp)) { drawRect(blue, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width * out((t - 1.1) / 0.5).toFloat(), 2f)) }
                    Box(Modifier.height(16.dp))
                    val pop = if (t >= 2.0) back((t - 2.0) / 0.2).toFloat() else 0f
                    Box(Modifier.graphicsLayer { val sc = 1.6f - 0.6f * pop; scaleX = sc; scaleY = sc; alpha = if (t >= 2.0) 1f else 0f }) { Txt("with desktop", color = blue, size = 62, pixel = true) }
                }
                Row(Modifier.align(Alignment.TopStart).padding(start = 110.dp, top = 110.dp).graphicsLayer { alpha = (if (t >= 3.0) 1f else 0f) * (1f - gone) }) { Txt("V0.0.4 BETA-1", color = muted, size = 14, pixel = true) }
                Row(Modifier.align(Alignment.TopEnd).padding(end = 110.dp, top = 110.dp).graphicsLayer { alpha = (if (t >= 3.5) 1f else 0f) * (1f - gone) }) { Txt("WINDOWS · LINUX", color = muted, size = 14, pixel = true) }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp).graphicsLayer { alpha = (if (t >= 4.0) 1f else 0f) * (1f - gone) }) { Txt("FREE · NO ACCOUNT", color = muted, size = 14, pixel = true) }
            }
        }
        if (d.endCard >= 0) {
            val e = d.endCard
            val k = out(e / 0.25).toFloat()
            val bye = inn((e - 2.5) / 0.5).toFloat()
            Box(Modifier.fillMaxSize().background(ink.copy(alpha = 0.97f * k)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val pulse = if (e >= 0) 0.15f * (1f - out(((e % 0.5) / 0.5) / 0.5).toFloat()) else 0f
                    drawRect(Brush.radialGradient(0f to blue.copy(alpha = 0.42f * k + pulse), 1f to Color.Transparent, center = Offset(size.width / 2, size.height * 0.45f), radius = size.width * 0.55f))
                    val m = 60.dp.toPx()
                    withTransform({ translate(m, m) }) {
                        val inner = androidx.compose.ui.geometry.Size(size.width - 2 * m, size.height - 2 * m)
                        val p = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, inner.width, inner.height, androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))) }
                        val meas = androidx.compose.ui.graphics.PathMeasure().also { it.setPath(p, false) }
                        val seg = androidx.compose.ui.graphics.Path()
                        meas.getSegment(0f, meas.length * out(e / 1.0).toFloat(), seg, true)
                        drawPath(seg, Color.White.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val a1 = back(e / 0.22).toFloat()
                    Box(Modifier.graphicsLayer { val sc = 2.0f - 1.0f * a1; scaleX = sc; scaleY = sc; rotationZ = -3f * (1f - a1); alpha = if (e >= 0.0) 1f else 0f }) { Txt("w0y", color = Color.White, size = 190, pixel = true) }
                    val a2 = back((e - 0.5) / 0.2).toFloat()
                    Box(Modifier.graphicsLayer { val sc = 1.7f - 0.7f * a2; scaleX = sc; scaleY = sc; alpha = if (e >= 0.5) 1f else 0f }) { Txt("with desktop", color = blue, size = 64, pixel = true) }
                    Box(Modifier.height(44.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                        listOf("WINDOWS", "LINUX", "FREE", "NO ACCOUNT").forEachIndexed { i, label ->
                            val a = back((e - 1.0 - i * 0.25) / 0.2).toFloat()
                            Box(Modifier.graphicsLayer { val sc = 0.4f + 0.6f * a; scaleX = sc; scaleY = sc; alpha = if (e >= 1.0 + i * 0.25) 1f else 0f }.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) { Txt(label, color = Color.White, size = 18, pixel = true) }
                        }
                    }
                    Box(Modifier.height(30.dp))
                    val a3 = out((e - 2.0) / 0.4).toFloat()
                    Box(Modifier.graphicsLayer { alpha = a3; translationY = (1f - a3) * 14f }) { Txt("github.com/texfi-w0y/texfi-w0y", color = muted, size = 20, pixel = true) }
                }
            }
            if (bye > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bye)))
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
        val trs = ArrayList<Pair<Double, Tr>>()
        private var moveFrom = d.cursor
        private var moveStart = 0.0
        private var moveEnd = 0.0
        /** Цель курсора в координатах окна без камеры: так она «едет» вместе с приближением и поворотом. */
        private var targetLocal: Offset? = null
        private var songStart = 0.0
        private var songBase = 0.0
        private var songPlaying = false
        private var camFrom = 1.0f
        private var camFromRot = 0f
        private var camFromFocus = Offset(0.5f, 0.5f)
        private var camTarget = 1.0f
        private var camTargetRot = 0f
        private var camFocus = Offset(0.5f, 0.5f)
        private var camStart = 0.0
        private var camEnd = 0.01
        private var camSnap = true

        fun beforeFrame(t: Double) {}

        /** Позиция узла с таким текстом в координатах кадра (с учётом камеры в последнем нарисованном кадре). */
        fun where(text: String, index: Int = 0): Offset? = runCatching {
            val nodes = ui.onAllNodes(hasText(text, substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes()
            nodes.getOrNull(index)?.boundsInRoot?.center
        }.getOrNull()

        /** Курсор к элементу с этим текстом: цель запоминается без камеры и дальше едет вместе с ней. */
        fun cursorTo(text: String, t: Double, dur: Double = 0.3, dx: Float = 0f, dy: Float = 0f, index: Int = 0) {
            val p = where(text, index) ?: return
            moveFrom = d.cursor; moveStart = t; moveEnd = t + dur
            targetLocal = d.drawnCam.inv(Offset(p.x + dx, p.y + dy))
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

        /** Начало сцены: подпись «NN ❯ заголовок» в строке окна и слово-удар. */
        fun scene(t: Double, index: Int, title: String, sub: String, end: Double, word: String? = null) {
            d.caption = title; d.captionSub = sub; d.captionAt = t; d.sceneAt = t; d.captionIndex = index; d.captionEnd = end
            if (word != null) { d.word = word; d.wordAt = t }
        }

        fun flash(t: Double, power: Float) { d.flashAt = t; d.flashPow = power }

        /** Камера прыгает к масштабу [scale] вокруг [focus] с поворотом [rot]°; [snap] — быстро и с отскоком, иначе плавно за [dur]. */
        fun camera(t: Double, scale: Float, focus: Offset = Offset(0.5f, 0.5f), rot: Float = 0f, snap: Boolean = true, dur: Double = if (snap) 0.22 else 1.0) {
            camFrom = d.scale; camFromRot = d.rot; camFromFocus = d.focus
            camTarget = scale; camTargetRot = rot; camFocus = focus; camStart = t; camEnd = t + dur; camSnap = snap
            if (snap) d.hitAt = t
        }

        /** Трек «играет»: позиция бежит с секунды [from]; при from = t строки текста меняются на каждом такте. */
        fun playing(t: Double, from: Double, on: Boolean = true) {
            songStart = t; songBase = from; songPlaying = on
            app.player.playing = on
        }

        fun show(index: Int, queue: List<SongItem>? = null) {
            val q = queue ?: (0 until 9).map { song(it).toItem() }
            app.player.demoShow(q, index, songPlaying)
        }

        fun animate(t: Double) {
            d.fx = fxAt(t, trs)
            if (camEnd > camStart) {
                val q = ((t - camStart) / (camEnd - camStart)).coerceIn(0.0, 1.0)
                val k = (if (camSnap) back(q) else ease(q)).toFloat()
                d.scale = camFrom + (camTarget - camFrom) * k
                d.rot = camFromRot + (camTargetRot - camFromRot) * k
                val f = out(q).toFloat()
                d.focus = Offset(camFromFocus.x + (camFocus.x - camFromFocus.x) * f, camFromFocus.y + (camFocus.y - camFromFocus.y) * f)
            }
            val tl = targetLocal
            if (tl != null) {
                val k = if (t >= moveEnd) 1.0 else out((t - moveStart) / (moveEnd - moveStart))
                val target = camOf(d, t).map(tl)
                d.cursor = Offset(lerp(moveFrom.x.toDouble(), target.x.toDouble(), k).toFloat(), lerp(moveFrom.y.toDouble(), target.y.toDouble(), k).toFloat())
            }
            if (t >= 43.0) d.endCard = t - 43.0
            if (songPlaying) app.player.position = songBase + (t - songStart)
            if (t in wheelFrom..wheelTo && wheelPerSec != 0f) {
                runCatching { ui.onRoot().performMouseInput { moveTo(camOf(d, t).map(wheelAt)); scroll(wheelPerSec / 30f) } }
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
        /** Кадр (0..1 от экрана) — для краткости. */
        fun f(x: Float, y: Float) = Offset(x, y)
        /** Переход на долю [t]: окно уходит, на самой доле меняется содержимое, возвращается с отскоком. */
        fun cut(t: Double, kind: Tr, swap: () -> Unit) {
            x.trs += t to kind
            s.at(t) { d.cursorVisible = false; swap() }
        }

        // 0–6 с: вступление рисуется по времени. На дропе окно вылетает переворотом со вспышкой.
        x.trs += 6.0 to Tr.FLIPY
        s.at(0.0) { d.scale = 1.0f; app.open(Screen.Home); x.playing(0.0, 8.0, false) }
        s.at(6.0) { x.flash(6.0, 0.7f); d.hitAt = 6.0; d.scale = 1.0f; d.caption = "" }

        // 6–10 с: главная. Удары камеры по быстрому набору, клик, зум в нижнюю панель, прокрутка.
        s.at(6.0) { x.scene(6.0, 1, "Your music. One click.", "Speed dial · Quick picks", 10.0) }
        s.at(6.5) { d.word = "HOME"; d.wordAt = 6.5; x.camera(6.5, 1.3f, f(0.30f, 0.36f), -2f) }
        s.at(7.0) { x.camera(7.0, 1.55f, f(0.28f, 0.33f), 2f); x.cursorTo("Afterglow", 7.0, 0.35) }
        s.at(7.5) { x.click(7.5, real = false); x.playing(7.5, 7.5); x.show(0); x.flash(7.5, 0.18f) }
        s.at(8.0) { x.camera(8.0, 1.9f, f(0.5f, 0.96f), 0f) }
        s.at(8.5) { x.camera(8.5, 1.18f, f(0.5f, 0.5f), 3f); x.wheel(Offset(960f, 500f), 8.5, 9.6, 1500f) }
        s.at(9.0) { x.camera(9.0, 1.4f, f(0.7f, 0.6f), -3f) }
        s.at(9.5) { x.camera(9.5, 1.0f, f(0.5f, 0.5f), 0f) }

        // 10–14 с: поиск.
        cut(10.0, Tr.FLIPY) {
            app.open(Screen.Search); app.searchModel.immediate = true
            (2..typed.length).forEach { n -> app.searchModel.seed(typed.take(n), SearchFilter.ALL, results) }
            SearchFilter.entries.forEach { fl -> app.searchModel.seed(typed, fl, if (fl == SearchFilter.VIDEOS) videos else results) }
            x.scene(10.0, 2, "Smart search", "Tracks · videos · albums · artists", 14.0, "SEARCH")
            x.camera(10.0, 1.3f, f(0.45f, 0.2f), 0f, snap = false, dur = 0.01)
        }
        typed.forEachIndexed { i, _ -> s.at(10.6 + i * 0.07) { app.searchModel.query = typed.take(i + 1) } }
        s.at(11.5) { x.camera(11.5, 1.55f, f(0.42f, 0.45f), -2f) }
        s.at(12.0) { x.camera(12.0, 1.3f, f(0.42f, 0.22f), 2f); x.cursorTo("videos", 12.0, 0.25) }
        s.at(12.3) { x.click(12.3) }
        s.at(12.5) { x.camera(12.5, 1.5f, f(0.45f, 0.45f), -2f); x.cursorTo("albums", 12.5, 0.25) }
        s.at(12.8) { x.click(12.8) }
        s.at(13.0) { x.camera(13.0, 1.7f, f(0.4f, 0.4f), 2f); x.cursorTo("artists", 13.0, 0.25) }
        s.at(13.3) { x.click(13.3) }
        s.at(13.5) { x.camera(13.5, 1.0f, f(0.5f, 0.5f), 0f); x.cursorTo("player", 13.5, 0.3) }
        s.at(13.8) { x.click(13.8) }

        // 14–20 с: текст песни крупно.
        cut(14.0, Tr.SPIN) {
            app.open(Screen.Player)
            x.scene(14.0, 3, "Lyrics in focus", "Synced · translated · sing along", 20.0, "LYRICS")
            x.camera(14.0, 1.15f, f(0.6f, 0.5f), 0f, snap = false, dur = 0.01)
        }
        s.at(14.5) { x.camera(14.5, 1.5f, f(0.72f, 0.5f), 0f) }
        s.at(15.0) { x.camera(15.0, 1.7f, f(0.25f, 0.42f), -3f) }
        s.at(15.5) { x.camera(15.5, 1.25f, f(0.5f, 0.5f), 0f) }
        s.at(16.0) { x.camera(16.0, 1.9f, f(0.72f, 0.5f), 2f); x.flash(16.0, 0.12f) }
        s.at(16.5) { x.camera(16.5, 1.6f, f(0.75f, 0.35f), -2f) }
        s.at(17.0) { x.camera(17.0, 1.3f, f(0.3f, 0.5f), 2f) }
        s.at(17.5) { x.camera(17.5, 1.55f, f(0.28f, 0.4f), -3f); app.setSt { it.copy(hlCoverShape = CoverShape.DISC, hlCoverSpin = true) } }
        s.at(18.0) { x.camera(18.0, 1.15f, f(0.5f, 0.5f), 0f) }
        s.at(18.5) { x.camera(18.5, 1.4f, f(0.5f, 0.5f), -2f); app.setSt { it.copy(hlPlayerBack = PlayerBack.AURORA) }; x.flash(18.5, 0.25f) }
        s.at(19.0) { x.camera(19.0, 1.7f, f(0.72f, 0.5f), 2f) }
        s.at(19.5) { x.camera(19.5, 1.0f, f(0.5f, 0.5f), 0f); x.cursorTo("queue", 19.5, 0.3) }
        s.at(19.85) { x.click(19.85) }

        // 20–24 с: очередь и история.
        cut(20.0, Tr.FLIPX) {
            x.scene(20.0, 4, "Queue & history", "Drag to reorder · what is up next", 24.0, "QUEUE")
            x.camera(20.0, 1.3f, f(0.75f, 0.5f), 0f, snap = false, dur = 0.01)
        }
        s.at(20.5) { x.camera(20.5, 1.6f, f(0.76f, 0.45f), -2f) }
        s.at(21.0) { x.camera(21.0, 1.4f, f(0.76f, 0.4f), 2f); x.cursorTo("Honey Signal", 21.0, 0.3, dx = -520f) }
        s.at(21.5) { app.player.moveInQueue(6, 2); x.flash(21.5, 0.15f); x.camera(21.5, 1.6f, f(0.78f, 0.4f), -2f) }
        s.at(22.0) { x.camera(22.0, 1.2f, f(0.5f, 0.5f), 0f); x.cursorTo("already played", 22.0, 0.3) }
        s.at(22.4) { x.click(22.4) }
        s.at(22.5) { x.camera(22.5, 1.5f, f(0.78f, 0.7f), 2f) }
        s.at(23.0) { x.camera(23.0, 1.4f, f(0.78f, 0.4f), -2f); x.cursorTo("history", 23.0, 0.25) }
        s.at(23.3) { x.click(23.3) }
        s.at(23.5) { x.camera(23.5, 1.0f, f(0.5f, 0.5f), 0f); x.cursorTo("sound", 23.5, 0.3) }
        s.at(23.85) { x.click(23.85) }

        // 24–28 с: свой вариант трека.
        cut(24.0, Tr.WHIP) {
            x.scene(24.0, 5, "Make your own version", "Speed · pitch · reverb · 10-band equaliser", 28.0, "SOUND")
            x.camera(24.0, 1.35f, f(0.75f, 0.4f), 0f, snap = false, dur = 0.01)
        }
        s.at(24.5) { x.camera(24.5, 1.55f, f(0.78f, 0.35f), -2f); x.cursorTo("slowed", 24.5, 0.3) }
        s.at(25.0) { x.click(25.0); x.camera(25.0, 1.9f, f(0.78f, 0.42f), 2f); x.flash(25.0, 0.12f) }
        s.at(25.3) { x.cursorTo("cave", 25.3, 0.2) }
        s.at(25.5) { x.click(25.5); x.camera(25.5, 1.7f, f(0.78f, 0.5f), -2f) }
        s.at(26.0) { x.camera(26.0, 1.5f, f(0.78f, 0.75f), 0f); x.wheel(Offset(1300f, 600f), 26.0, 27.5, 700f) }
        s.at(26.5) { x.camera(26.5, 2.0f, f(0.8f, 0.78f), 3f) }
        s.at(27.0) { x.camera(27.0, 1.6f, f(0.78f, 0.8f), -3f) }
        s.at(27.5) { x.camera(27.5, 1.0f, f(0.5f, 0.5f), 0f) }

        // 28–32 с: подставка.
        cut(28.0, Tr.ZOOM) {
            x.scene(28.0, 6, "Stand mode", "Glow · clock · lyrics on your desk", 32.0, "STAND")
            x.key(28.5, "B")
            app.setSt { it.copy(hlCoverShape = CoverShape.ROUNDED, hlCoverSpin = false) }; app.stand.enter()
            x.camera(28.0, 1.0f, f(0.5f, 0.5f), 0f, snap = false, dur = 0.01)
        }
        s.at(28.5) { x.camera(28.5, 1.2f, f(0.3f, 0.5f), -2f) }
        s.at(29.0) { x.camera(29.0, 1.5f, f(0.55f, 0.35f), 2f) }
        s.at(29.5) { x.camera(29.5, 1.15f, f(0.5f, 0.5f), 0f) }
        s.at(30.0) { x.camera(30.0, 1.6f, f(0.66f, 0.62f), -2f); x.flash(30.0, 0.1f) }
        s.at(30.5) { x.camera(30.5, 1.3f, f(0.3f, 0.5f), 2f) }
        s.at(31.0) { x.camera(31.0, 1.8f, f(0.3f, 0.5f), -3f) }
        s.at(31.5) { x.camera(31.5, 1.0f, f(0.5f, 0.5f), 0f) }
        s.at(31.85) { app.stand.off() }

        // 32–36 с: свои файлы.
        cut(32.0, Tr.FLIPY) {
            app.open(Screen.LocalFiles)
            x.scene(32.0, 7, "Your own files", "mp3 · flac · m4a — tags, covers, playlists", 36.0, "FILES")
            x.camera(32.0, 1.3f, f(0.5f, 0.3f), 0f, snap = false, dur = 0.01)
        }
        s.at(32.5) { x.camera(32.5, 1.8f, f(0.45f, 0.3f), -2f) }
        s.at(33.0) { x.camera(33.0, 1.5f, f(0.55f, 0.45f), 2f) }
        s.at(33.5) { x.camera(33.5, 1.9f, f(0.6f, 0.4f), -2f) }
        s.at(34.0) { x.camera(34.0, 1.3f, f(0.5f, 0.5f), 0f); x.flash(34.0, 0.1f) }
        s.at(34.5) { x.camera(34.5, 1.7f, f(0.7f, 0.25f), 2f) }
        s.at(35.0) { x.camera(35.0, 1.4f, f(0.4f, 0.5f), -2f) }
        s.at(35.5) { x.camera(35.5, 1.0f, f(0.5f, 0.5f), 0f) }

        // 36–40 с: оформление и панели.
        cut(36.0, Tr.SPIN) {
            app.open(Screen.Player)
            app.setSt { it.copy(hlLayout = PlayerLayout.COVER, hlCoverShape = CoverShape.SQUARE, hlPlayerBack = PlayerBack.GLOW, hlCoverSpin = false) }
            x.scene(36.0, 8, "Make it yours", "Light · dark · cover shapes · hide any panel", 40.0, "LOOK")
            x.camera(36.0, 1.15f, f(0.4f, 0.5f), 0f, snap = false, dur = 0.01)
        }
        s.at(36.5) { x.camera(36.5, 1.6f, f(0.3f, 0.45f), -2f); app.setSt { it.copy(hlCoverShape = CoverShape.DISC, hlCoverSpin = true) } }
        s.at(37.0) { x.camera(37.0, 1.3f, f(0.5f, 0.5f), 3f); x.flash(37.0, 0.45f); app.setSt { it.copy(theme = ThemeMode.LIGHT, hlCoverShape = CoverShape.SQUARE, hlCoverSpin = false) } }
        s.at(37.5) { x.camera(37.5, 1.5f, f(0.5f, 0.5f), -3f); x.flash(37.5, 0.2f); app.setSt { it.copy(theme = ThemeMode.DARK) } }
        s.at(38.0) { x.camera(38.0, 1.25f, f(0.5f, 0.5f), 0f); x.key(38.0, "Ctrl + B"); app.setSt { it.copy(sidebarHidden = true) } }
        s.at(38.5) { x.camera(38.5, 1.4f, f(0.5f, 0.55f), 2f); x.key(38.5, "Ctrl + Shift + B"); app.setSt { it.copy(barHidden = true) } }
        s.at(39.0) { x.camera(39.0, 1.1f, f(0.5f, 0.5f), 0f); app.setSt { it.copy(sidebarHidden = false, barHidden = false, hlCoverShape = CoverShape.ROUNDED) } }
        s.at(39.5) { x.camera(39.5, 1.0f, f(0.5f, 0.5f), 0f) }

        // 40–43 с: клавиатура.
        cut(40.0, Tr.WHIP) {
            app.open(Screen.Home); x.key(40.5, "?"); Actions.helpOpen = true
            x.scene(40.0, 9, "Keyboard first", "Hotkeys · media keys · mini player · tray", 43.0, "KEYS")
            x.camera(40.0, 1.2f, f(0.5f, 0.5f), 0f, snap = false, dur = 0.01)
        }
        s.at(40.5) { x.camera(40.5, 1.55f, f(0.5f, 0.55f), -2f) }
        s.at(41.0) { x.camera(41.0, 1.6f, f(0.5f, 0.5f), 2f) }
        s.at(41.5) { x.camera(41.5, 1.6f, f(0.48f, 0.7f), -2f); x.flash(41.5, 0.1f) }
        s.at(42.0) { x.camera(42.0, 1.3f, f(0.5f, 0.5f), 0f) }
        s.at(42.5) { x.camera(42.5, 1.0f, f(0.5f, 0.5f), 0f) }
        s.at(42.85) { Actions.helpOpen = false }

        // 43–46 с: финал — окно улетает в камеру, карточка собирается ударами.
        x.trs += 43.0 to Tr.ZOOM
        s.at(43.0) { d.caption = ""; d.cursorVisible = false; x.flash(43.0, 0.5f); d.hitAt = 43.0 }
        s.sort()
    }
}
