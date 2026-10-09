package com.texfi.w0y.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
    private var inhibit: Process? = null
    private var timer: Job? = null
    private var windowedBefore = false

    fun toggle() = if (on) off() else enter()

    fun enter() {
        if (on) return
        on = true
        windowedBefore = !Actions.fullscreen
        Actions.fullscreen = true
        if (app.st.standKeepAwake) {
            inhibit = runCatching {
                ProcessBuilder("systemd-inhibit", "--what=idle:sleep", "--who=w0y", "--why=stand mode", "--mode=block", "sleep", "infinity")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            }.getOrNull()
        }
        val max = app.st.standMaxMinutes
        if (max > 0) timer = app.scope.launch { delay(max * 60_000L); off() }
    }

    fun off() {
        if (!on) return
        on = false
        timer?.cancel()
        timer = null
        runCatching { inhibit?.destroyForcibly() }
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
fun currentLine(lyrics: Lyrics?, positionSec: Double): String? {
    val lines = lyrics?.synced.orEmpty()
    if (lines.isEmpty()) return null
    val now = (positionSec * 1000).toLong()
    return lines.lastOrNull { it.timeMs <= now }?.text
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
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            shift = IntOffset(Random.nextInt(-14, 15), Random.nextInt(-14, 15))
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
        Column(
            Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 96.dp).offset { shift },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (st.standView == StandView.COVER) {
                Cover(song?.thumbnailUrl, 360.dp)
                Gap(h = 28)
            } else if (st.standVisualizer && SpectrumCapture.available) {
                Visualizer(st.visualizerStyle, p.playing, st.visualizerSensitivity, 30, Modifier.fillMaxWidth().height(180.dp))
                Gap(h = 28)
            }
            Txt(song?.title ?: S("d_idle"), color = text, size = 40, weight = FontWeight.Bold, maxLines = if (st.standMarquee) 1 else 3)
            Gap(h = 10)
            Txt(song?.artist.orEmpty(), color = dim, size = 22)
            val line = currentLine(lyrics, p.position)
            if (line != null) {
                Gap(h = 22)
                Txt(line, color = C.Blue, size = 24, weight = FontWeight.SemiBold, maxLines = 2)
            }
        }
        if (controls) {
            RowCenter(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)) {
                IconButton(Glyph.Prev, onClick = { p.previous(); touch++ }, color = text, size = 34.dp, box = 64.dp)
                Gap(w = 20)
                Box(Modifier.size(72.dp).clip(CircleShape).background(C.Blue).clickable { p.toggle(); touch++ }, contentAlignment = Alignment.Center) {
                    Icon(if (p.playing) Glyph.Pause else Glyph.Play, C.OnAccent, 34.dp)
                }
                Gap(w = 20)
                IconButton(Glyph.Next, onClick = { p.nextClick(); touch++ }, color = text, size = 34.dp, box = 64.dp)
            }
            RowCenter(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                IconButton(Glyph.Rotate, onClick = { app.setSt { it.copy(standFlipped = !it.standFlipped) }; touch++ }, color = if (st.standFlipped) C.Blue else text, size = 24.dp, box = 48.dp)
                IconButton(Glyph.Close, onClick = { app.stand.off() }, color = text, size = 26.dp, box = 48.dp)
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
    var style by remember { mutableStateOf(app.st.uiStyle) }
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
            PixelButton(S("welcome_start"), onClick = { app.setSt { it.copy(welcomeSeen = true, stylePicked = true, uiStyle = style) } })
        }
    }
}

@Composable
private fun StyleCard(s: UiStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val smooth = s == UiStyle.SMOOTH
    val shape = RoundedCornerShape(if (smooth) 22.dp else 6.dp)
    Column(
        modifier.clip(shape).background(if (selected) C.SurfaceHigh else C.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) C.Blue else C.Border, shape)
            .clickable(onClick = onClick).padding(16.dp),
    ) {
        // Миниатюра: боковая панель и плеер так, как их рисует этот стиль.
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(if (smooth) 14.dp else 3.dp)).background(Color(0xFF15151B)).drawBehind {
            val bar = if (smooth) 14.dp.toPx() else 2.dp.toPx()
            if (smooth) {
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
    val shape = RoundedCornerShape(if (Look.smooth) 50.dp else 4.dp)
    Box(
        Modifier.clip(shape).background(if (selected) C.Blue else C.SurfaceHigh)
            .border(if (Look.smooth) 0.dp else 2.dp, if (selected) C.BlueDeep else C.Border, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Txt(text, color = if (selected) C.OnAccent else if (enabled) C.Text else C.Muted, size = 13, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}
