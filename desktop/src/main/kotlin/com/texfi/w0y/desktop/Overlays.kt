package com.texfi.w0y.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texfi.w0y.desktop.i18n.S
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Режим подставки: чёрный экран с крупным названием, как на телефоне. Окно
 * разворачивается на весь экран, система не гасит монитор (если выбрано), через
 * заданное время режим закрывается сам.
 */
class StandMode(private val app: AppState) {
    var on by mutableStateOf(false)
        private set
    private var inhibit: AutoCloseable? = null
    private var timer: Job? = null
    private var windowedBefore = false

    fun toggle() = if (on) off() else enter()

    fun enter() {
        if (on) return
        on = true
        windowedBefore = !Actions.fullscreen
        Actions.fullscreen = true
        if (app.st.standKeepAwake) {
            inhibit = Platform.keepAwake()
        }
        val max = app.st.standMaxMinutes
        if (max > 0) timer = app.scope.launch { delay(max * 60_000L); off() }
    }

    fun off() {
        if (!on) return
        on = false
        timer?.cancel()
        timer = null
        runCatching { inhibit?.close() }
        inhibit = null
        if (windowedBefore) Actions.fullscreen = false
    }
}

/** Текст песни с синхронизацией: null, пока грузится или если не нашёлся. */
@Composable
fun rememberLyricsOf(app: AppState, song: com.texfi.w0y.data.SongItem?): Lyrics? {
    val state by produceState<Lyrics?>(null, song?.id) {
        value = null
        if (song != null) value = app.lyrics.lyrics(song)
    }
    return state
}

/** Строка текста, звучащая сейчас. */
fun currentLine(lyrics: Lyrics?, positionSec: Double, delayMs: Int = 0): String? {
    val lines = lyrics?.synced.orEmpty()
    if (lines.isEmpty()) return null
    val now = (positionSec * 1000).toLong()
    return lines.lastOrNull { it.timeMs + delayMs <= now }?.text
}

@Composable
fun Overlays(app: AppState) {
    if (!app.st.welcomeSeen) {
        WelcomeOverlay(app)
        return
    }
    if (app.stand.on) StandOverlay(app)
    if (Actions.helpOpen) HelpOverlay(app)
}

// ---- подставка

/**
 * Подставка: чёрный экран, на котором из обложки мягко растёт свечение. Обложка крупно слева, справа часы,
 * название, исполнитель и три строки текста (прошлая, звучащая, следующая); тонкая линия внизу — ход трека.
 * Кнопки появляются по движению мыши и через пять секунд исчезают. Раз в минуту всё чуть сдвигается,
 * чтобы экран не «выгорал».
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StandOverlay(app: AppState) {
    val p = app.player
    val song = p.current
    val st = app.st
    val lyrics = if (st.standLyrics) rememberLyricsOf(app, song) else null
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableStateOf(0) }
    var shift by remember { mutableStateOf(IntOffset.Zero) }
    var clock by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            shift = IntOffset(Random.nextInt(-14, 15), Random.nextInt(-14, 15))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            clock = java.time.LocalTime.now()
            delay(10_000)
        }
    }
    LaunchedEffect(controls, touch) {
        if (controls) {
            delay(5_000)
            controls = false
        }
    }
    val text = Color(0xFFE8E4DA)
    val dim = Color(0xFF8A877F)
    val palette = rememberCoverPalette(song?.thumbnailUrl)
    val glowA by androidx.compose.animation.animateColorAsState(palette.a, androidx.compose.animation.core.tween(Motion.ms(900)))
    val glowB by androidx.compose.animation.animateColorAsState(palette.b, androidx.compose.animation.core.tween(Motion.ms(900)))
    val strength = if (palette.tinted) 1f else 0.5f
    val cover = st.standView == StandView.COVER
    Box(
        Modifier.fillMaxSize().background(Color.Black).rotate(if (st.standFlipped) 180f else 0f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { app.setSt { it.copy(standView = if (it.standView == StandView.TEXT) StandView.COVER else StandView.TEXT) } },
                    onTap = { controls = !controls; touch++ },
                )
            }.onPointerEvent(PointerEventType.Move) {
                // Мышь шевельнулась — показываем кнопки.
                if (!controls) controls = true
                touch++
            },
    ) {
        // Свечение: у обложки (слева) или по центру, когда обложки нет; тёмное, чтобы ночью не слепило.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val c = if (cover) Offset(size.width * 0.27f, size.height * 0.5f) else Offset(size.width * 0.5f, size.height * 0.45f)
                val reach = maxOf(size.width, size.height) * 0.85f
                drawRect(Brush.radialGradient(0f to glowA.copy(alpha = 0.34f * strength), 0.45f to glowB.copy(alpha = 0.13f * strength), 1f to Color.Transparent, center = c, radius = reach))
            },
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 80.dp).offset { shift }) {
            val boxW = maxWidth
            val boxH = maxHeight
            val wide = boxW > boxH * 1.2f
            if (cover && wide) {
                val side = minOf(boxH * 0.92f, boxW * 0.38f)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    StandCover(song?.thumbnailUrl, side, glowA)
                    Gap(w = 72)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        StandTexts(app, song, lyrics, text, dim, clock, center = false, titleSize = 52)
                    }
                }
            } else if (cover) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    StandCover(song?.thumbnailUrl, minOf(boxW * 0.7f, boxH * 0.42f), glowA)
                    Gap(h = 32)
                    StandTexts(app, song, lyrics, text, dim, clock, center = true, titleSize = 40)
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (st.standVisualizer && SpectrumCapture.available) {
                        Visualizer(st.visualizerStyle, p.playing, st.visualizerSensitivity, 30, Modifier.fillMaxWidth().height(180.dp))
                        Gap(h = 28)
                    }
                    StandTexts(app, song, lyrics, text, dim, clock, center = true, titleSize = 60)
                }
            }
        }
        // Ход трека: тонкая линия по нижнему краю видна всегда, время — вместе с кнопками.
        val fraction = if (p.duration > 0) (p.position / p.duration).coerceIn(0.0, 1.0).toFloat() else 0f
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(glowA.copy(alpha = 0.9f)))
        }
        if (controls) {
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("${fmtTime(p.position)}  /  ${fmtTime(p.duration)}", color = dim, size = 13)
                Gap(h = 12)
                RowCenter {
                    IconButton(Glyph.Prev, onClick = { p.previous(); touch++ }, color = text, size = 34.dp, box = 64.dp)
                    Gap(w = 20)
                    Box(Modifier.size(72.dp).clip(CircleShape).background(text).clickable { p.toggle(); touch++ }, contentAlignment = Alignment.Center) {
                        Icon(if (p.playing) Glyph.Pause else Glyph.Play, Color.Black, 34.dp)
                    }
                    Gap(w = 20)
                    IconButton(Glyph.Next, onClick = { p.nextClick(); touch++ }, color = text, size = 34.dp, box = 64.dp)
                }
            }
            RowCenter(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                IconButton(Glyph.Rotate, onClick = { app.setSt { it.copy(standFlipped = !it.standFlipped) }; touch++ }, color = if (st.standFlipped) C.Blue else text, size = 24.dp, box = 48.dp)
                IconButton(Glyph.Close, onClick = { app.stand.off() }, color = text, size = 26.dp, box = 48.dp)
            }
        }
    }
}

/** Обложка подставки: скруглённый квадрат с тенью цвета самой обложки. */
@Composable
private fun StandCover(url: String?, size: Dp, glow: Color) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.size(size).shadow(48.dp, shape, ambientColor = glow, spotColor = glow).clip(shape).background(Color(0xFF15151B))) {
        coil3.compose.AsyncImage(
            model = sizedThumb(url, 900),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Часы, название, исполнитель и строки текста: общий столбец подставки. */
@Composable
private fun StandTexts(app: AppState, song: com.texfi.w0y.data.SongItem?, lyrics: Lyrics?, text: Color, dim: Color, clock: java.time.LocalTime, center: Boolean, titleSize: Int) {
    val st = app.st
    val align = if (center) Alignment.CenterHorizontally else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        if (st.standClock) {
            Txt("%02d:%02d".format(clock.hour, clock.minute), color = dim, size = 44, weight = FontWeight.Light)
            Gap(h = 28)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
            Txt(song?.title ?: S("d_idle"), color = text, size = titleSize, weight = FontWeight.Bold, maxLines = if (st.standMarquee) 1 else 3)
        }
        Gap(h = 10)
        Box(Modifier.fillMaxWidth(), contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
            Txt(song?.artist.orEmpty(), color = dim, size = (titleSize * 0.5f).toInt())
        }
        val lines = lyrics?.synced.orEmpty()
        if (lines.isNotEmpty()) {
            Gap(h = 36)
            val now = (app.player.position * 1000).toLong()
            val at = lines.indexOfLast { it.timeMs + st.lyricsDelayMs <= now }
            androidx.compose.animation.Crossfade(at, animationSpec = androidx.compose.animation.core.tween(Motion.ms(350)), label = "stand-lyrics") { i ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
                    lines.getOrNull(i - 1)?.let { Txt(it.text, color = dim.copy(alpha = 0.55f), size = 22, maxLines = 1) }
                    Gap(h = 6)
                    if (i >= 0) Txt(lines[i].text, color = text, size = 34, weight = FontWeight.SemiBold, maxLines = 2)
                    Gap(h = 6)
                    lines.getOrNull(i + 1)?.let { Txt(it.text, color = dim.copy(alpha = 0.55f), size = 22, maxLines = 1) }
                }
            }
        }
    }
}

// ---- справка по клавишам

@Composable
private fun HelpOverlay(app: AppState) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { Actions.helpOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        PixelCard(Modifier.width(760.dp).fillMaxHeight(0.86f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, padding = 22.dp) {
            Column {
                RowCenter {
                    Txt(S("d_keys_title"), pixel = true, size = 14, color = C.Blue)
                    Box(Modifier.weight(1f))
                    IconButton(Glyph.Close, onClick = { Actions.helpOpen = false }, color = C.Muted, size = 18.dp)
                }
                Gap(h = 12)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Action.groupKeys.forEachIndexed { g, key ->
                        SectionLabel(S(key), Modifier.padding(top = if (g == 0) 0.dp else 14.dp, bottom = 6.dp))
                        Action.entries.filter { it.group == g }.forEach { a ->
                            RowCenter(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Txt(a.label, modifier = Modifier.weight(1f), size = 14)
                                Keymap.bindings(a, app.st.keymap).forEach { KeyCap(it); Gap(w = 6) }
                            }
                        }
                    }
                }
                Gap(h = 10)
                Txt(S("d_keys_footer"), color = C.Muted, size = 12, maxLines = 2)
            }
        }
    }
}

/** Плашка с сочетанием: «Ctrl+K». */
@Composable
fun KeyCap(combo: String) {
    val shape = RoundedCornerShape(if (Look.smooth) 8.dp else 3.dp)
    Box(Modifier.clip(shape).background(C.SurfaceHigh).border(1.dp, C.Border, shape).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Txt(Keymap.pretty(combo), size = 12, color = C.Text, weight = FontWeight.Medium)
    }
}

// ---- первый запуск

@Composable
private fun WelcomeOverlay(app: AppState) {
    // Новому пользователю сразу показываем Hairline — он основной; выбор сохраняется кнопкой «Начать».
    var style by remember { mutableStateOf(if (app.st.stylePicked) app.st.uiStyle else UiStyle.HAIRLINE) }
    LaunchedEffect(Unit) { if (!app.st.stylePicked) app.setSt { it.copy(uiStyle = UiStyle.HAIRLINE, accent = Accent.BLUE) } }
    val langs = Language.entries
    Box(Modifier.fillMaxSize().background(C.Background), contentAlignment = Alignment.Center) {
        Column(Modifier.width(720.dp).verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt("w0y", pixel = true, size = 40, color = C.Blue, weight = FontWeight.Bold)
            Gap(h = 6)
            Txt(S("welcome_intro"), color = C.Muted, size = 15, maxLines = 3)
            Gap(h = 26)
            SectionLabel(S("settings_language_title"))
            Gap(h = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                langs.forEach { l ->
                    ChoiceChip(l.label, app.st.language == l) { app.setSt { it.copy(language = l) } }
                }
            }
            Gap(h = 26)
            SectionLabel(S("style_pick_title"))
            Gap(h = 4)
            Txt(S("style_pick_text"), color = C.Muted, size = 13, maxLines = 2)
            Gap(h = 10)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UiStyle.entries.forEach { s ->
                    StyleCard(s, style == s, Modifier.weight(1f)) {
                        style = s
                        app.setSt { it.copy(uiStyle = s) }
                    }
                }
            }
            if (style == UiStyle.SMOOTH) {
                Gap(h = 14)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(S("style_glass"), app.st.smoothGlass) { app.setSt { it.copy(smoothGlass = true) } }
                    ChoiceChip(S("style_matte"), !app.st.smoothGlass) { app.setSt { it.copy(smoothGlass = false) } }
                }
            }
            Gap(h = 28)
            PixelButton(S("welcome_start"), onClick = { app.setSt { it.copy(welcomeSeen = true, stylePicked = true, uiStyle = style, accent = if (style == UiStyle.HAIRLINE) Accent.BLUE else it.accent) } })
        }
    }
}

@Composable
private fun StyleCard(s: UiStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val smooth = s == UiStyle.SMOOTH
    val hair = s == UiStyle.HAIRLINE
    val shape = RoundedCornerShape(if (hair) 10.dp else if (smooth) 22.dp else 6.dp)
    Column(
        modifier.clip(shape).background(if (selected) C.SurfaceHigh else C.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) C.Blue else C.Border, shape)
            .clickable(onClick = onClick).padding(16.dp),
    ) {
        // Миниатюра: боковая панель и плеер так, как их рисует этот стиль.
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(if (smooth) 14.dp else 3.dp)).background(Color(0xFF15151B)).drawBehind {
            val bar = if (smooth) 14.dp.toPx() else 2.dp.toPx()
            if (hair) {
                drawRect(Color(0xFF0A0A0C))
                drawRect(Color(0xFF26262C), Offset(60.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height))
                drawRect(Color(0xFF26262C), Offset(0f, size.height - 28.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                drawRect(Color(0xFF4A7DFB), Offset(0f, 20.dp.toPx()), androidx.compose.ui.geometry.Size(2.dp.toPx(), 14.dp.toPx()))
                drawRect(Color(0xFFF4F4F6), Offset(size.width - 34.dp.toPx(), size.height - 22.dp.toPx()), androidx.compose.ui.geometry.Size(16.dp.toPx(), 16.dp.toPx()))
            } else if (smooth) {
                drawCircle(Brush.radialGradient(listOf(C.Blue.copy(alpha = .4f), Color.Transparent), Offset(size.width * .2f, size.height * .1f), size.width * .5f), size.width * .5f, Offset(size.width * .2f, size.height * .1f))
                drawRoundRect(Color.White.copy(alpha = .12f), Offset(8.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.Size(52.dp.toPx(), size.height - 16.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(bar))
                drawRoundRect(Color.White.copy(alpha = .12f), Offset(68.dp.toPx(), size.height - 30.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 76.dp.toPx(), 22.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(bar))
                drawCircle(C.Blue, 7.dp.toPx(), Offset(size.width - 26.dp.toPx(), size.height - 19.dp.toPx()))
            } else {
                drawRect(Color(0xFF1F1F27), Offset.Zero, androidx.compose.ui.geometry.Size(60.dp.toPx(), size.height))
                drawRect(Color(0xFF383845), Offset(60.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(bar, size.height))
                drawRect(Color(0xFF1F1F27), Offset(60.dp.toPx(), size.height - 28.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 28.dp.toPx()))
                drawRect(C.Blue, Offset(size.width - 38.dp.toPx(), size.height - 22.dp.toPx()), androidx.compose.ui.geometry.Size(16.dp.toPx(), 16.dp.toPx()))
            }
        })
        Gap(h = 12)
        Txt(s.label, size = 17, weight = FontWeight.SemiBold, pixel = true)
        Gap(h = 4)
        Txt(s.hint, color = C.Muted, size = 13, maxLines = 3)
    }
}

/** Выбор из нескольких: плашка, выбранная подсвечена акцентом. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (Look.hair) 3.dp else if (Look.smooth) 50.dp else 4.dp)
    Box(
        Modifier.clip(shape).background(if (selected) C.Blue else C.SurfaceHigh)
            .border(if (Look.smooth) 0.dp else 2.dp, if (selected) C.BlueDeep else C.Border, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Txt(text, color = if (selected) C.OnAccent else if (enabled) C.Text else C.Muted, size = 13, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}
