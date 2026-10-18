package com.texfi.w0y.desktop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.desktop.i18n.S

/*
 * Большой плеер в раскладке «Обложка» (Hairline): слева обложка, название, перемотка и все кнопки
 * управления, справа крупный текст песни. Очередь, «Похожее», звук и вид — вкладки той же панели.
 */

private val Gutter = 28.dp

/** Ширина левой колонки и размер обложки в ней: растут с окном, но в разумных пределах. */
private fun leftColumn(maxWidth: Dp): Dp = (maxWidth * 0.38f).coerceIn(320.dp, 500.dp)

private fun coverIn(column: Dp, maxHeight: Dp): Dp = minOf(column, maxHeight - Gutter * 2 - 44.dp - 262.dp).coerceIn(180.dp, 500.dp)

@Composable
internal fun NowPlayingLayout(app: AppState, tab: PlayerTab, tabs: List<PlayerTab>, onTab: (PlayerTab) -> Unit, maxWidth: Dp, maxHeight: Dp) {
    val column = leftColumn(maxWidth)
    val cover = coverIn(column, maxHeight)
    // Свечение растёт из середины обложки: она стоит в левой колонке под строкой значков.
    PlayerBackdrop(app, DpOffset(Gutter + column / 2, Gutter + 44.dp + cover / 2), cover / 2)
    Row(Modifier.fillMaxSize().padding(Gutter)) {
        NowColumn(app, column, cover, onTab)
        Gap(w = 36)
        Column(Modifier.weight(1f).fillMaxHeight()) { PlayerPanel(app, tab, tabs, onTab) }
    }
}

/** Диск (или визуализатор) в рамке-прогрессе: общий для всех раскладок Hairline. */
@Composable
internal fun CoverArt(app: AppState, size: Dp) {
    val p = app.player
    val song = p.current
    val viz = app.st.playerArt == PlayerArt.VISUALIZER && SpectrumCapture.available
    val vizStyle = app.st.visualizerStyle.forUi(ThemeState.style)
    HairDisc(
        url = song?.thumbnailUrl,
        size = size,
        progress = { if (p.duration > 0) (p.position / p.duration).toFloat() else 0f },
        playing = p.playing,
        onToggle = p::toggle,
        onNext = { p.nextClick() },
        onPrevious = p::previous,
        onSeek = p::seekFraction,
        time = { fraction -> fmtTime(fraction * p.duration) },
    ) { inner ->
        if (viz) {
            Box(Modifier.fillMaxSize().background(C.Surface)) {
                if (app.st.visualizerCoverBackdrop) Cover(song?.thumbnailUrl, inner, Modifier.alpha(0.3f))
                Visualizer(vizStyle, p.playing, app.st.visualizerSensitivity, app.st.visualizerFps, Modifier.fillMaxSize().padding(8.dp))
            }
        } else {
            androidx.compose.animation.AnimatedContent(song?.thumbnailUrl, transitionSpec = { Anim.coverTransition() }, label = "disc-cover") { shown ->
                coil3.compose.AsyncImage(
                    model = sizedThumb(shown, 640),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Левая колонка: значки сверху, обложка, название, перемотка, кнопки, действия и громкость. */
@Composable
private fun NowColumn(app: AppState, width: Dp, cover: Dp, onTab: (PlayerTab) -> Unit) {
    val p = app.player
    val song = p.current
    val viz = app.st.playerArt == PlayerArt.VISUALIZER && SpectrumCapture.available
    Column(Modifier.width(width).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        RowCenter(Modifier.fillMaxWidth().height(44.dp)) {
            Txt(if (p.queue.isEmpty()) "" else "${p.index + 1} / ${p.queue.size}", color = C.Muted, size = 11, pixel = true)
            Box(Modifier.weight(1f))
            if (app.st.showLyrics) IconButton(Glyph.Mic, onClick = { onTab(PlayerTab.Lyrics) }, color = C.Muted, size = 18.dp)
            if (SpectrumCapture.available) {
                IconButton(Glyph.Wave, onClick = { app.setSt { it.copy(playerArt = if (viz) PlayerArt.COVER else PlayerArt.VISUALIZER) } }, color = if (viz) C.Blue else C.Muted, size = 18.dp)
            }
            IconButton(Glyph.Stand, onClick = { app.stand.enter() }, color = C.Muted, size = 18.dp)
        }
        CoverArt(app, cover)
        Gap(h = 18)
        Column(Modifier.fillMaxWidth()) {
            Txt(Look.title(song?.title ?: S("d_idle")), pixel = true, size = 20, maxLines = 2)
            if (song != null) {
                Gap(h = 4)
                Txt(
                    song.artist, color = C.Muted, size = 15,
                    modifier = Modifier.clickable(enabled = song.artistId != null) { song.artistId?.let { app.open(Screen.Artist(it, song.artist, null)) } },
                )
                if (!p.soundNow.isPlain) Txt(S("d_your_version", p.soundNow.label), color = C.Sand, size = 12, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Gap(h = 12)
        SeekRow(p, app.st.seekStepSec)
        Gap(h = 4)
        Transport(app)
        Gap(h = 10)
        if (song != null) Actions(app, song)
        Gap(h = 6)
        RowCenter(Modifier.fillMaxWidth()) {
            Icon(Glyph.Volume, C.Muted, 16.dp)
            Gap(w = 10)
            VolumeBar(p.volume, onChange = p::changeVolume, modifier = Modifier.weight(1f).height(14.dp))
        }
    }
}

/** Перемешать, назад, пауза, вперёд, повтор: крупно и в одну строку, как в проигрывателях на компьютере. */
@Composable
private fun Transport(app: AppState) {
    val p = app.player
    RowCenter(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(Glyph.Shuffle, onClick = p::toggleShuffle, color = if (app.st.shuffle) C.Blue else C.Muted, size = 20.dp, box = 44.dp)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { IconButton(Glyph.Prev, onClick = p::previous, size = 28.dp, box = 52.dp) }
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(Look.card)).background(C.Text).clickable(onClick = p::toggle).testTag("now-toggle"),
            contentAlignment = Alignment.Center,
        ) { Icon(if (p.playing) Glyph.Pause else Glyph.Play, C.Background, 30.dp) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { IconButton(Glyph.Next, onClick = { p.nextClick() }, size = 28.dp, box = 52.dp) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box {
                IconButton(Glyph.Repeat, onClick = p::cycleRepeat, color = if (app.st.repeat != RepeatMode.OFF) C.Blue else C.Muted, size = 20.dp, box = 44.dp)
                if (app.st.repeat == RepeatMode.ONE) Txt("1", size = 9, color = C.Blue, weight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Всё, что можно сделать с треком, не уходя из плеера: сердце, закрепить, скачать, в плейлист, ссылка, таймер сна. */
@Composable
private fun Actions(app: AppState, song: SongItem) {
    SpreadRow(Modifier.fillMaxWidth()) {
        val liked = song.id in app.likedIds
        IconButton(if (liked) Glyph.Heart else Glyph.HeartOff, onClick = { app.toggleLike(song) }, color = if (liked) C.Danger else C.Muted, size = 20.dp)
        val pinned = app.isPinned(app.dialKey(song))
        IconButton(Glyph.Pin, onClick = { app.togglePinSong(song) }, color = if (pinned) C.Sand else C.Muted, size = 18.dp)
        DownloadButton(app, song)
        PlaylistMenu(app, song)
        if (!LocalFiles.isLocal(song.id)) {
            IconButton(Glyph.Share, onClick = {
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection("https://music.youtube.com/watch?v=${song.id}"), null)
            }, color = C.Muted, size = 18.dp)
        }
        SleepMenu(app)
    }
}

@Composable
private fun SpreadRow(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, content = content)

// ---- текст песни

@Composable
internal fun LyricsPane(app: AppState) {
    val song = app.player.current
    if (song == null) {
        Empty(S("d_lyrics_idle"))
        return
    }
    val lyrics by produceState<Lyrics?>(null, song.id) {
        value = null
        value = app.lyrics.lyrics(song) ?: Lyrics(null, emptyList())
    }
    var translateTo by remember(song.id) { mutableStateOf<String?>(null) }
    val data = lyrics
    if (data == null) {
        Empty(S("d_lyrics_searching"))
        return
    }
    if (data.isEmpty) {
        Empty(S("player_lyrics_none"))
        return
    }
    val synced = data.synced.isNotEmpty()
    val lines = if (synced) data.synced.map { it.text } else data.plain.orEmpty().lines()
    val translated by produceState<List<String>?>(null, song.id, translateTo) {
        value = null
        value = translateTo?.let { app.lyrics.translate(song.id, data, it) }
    }
    val active by remember(data) {
        derivedStateOf {
            if (!synced) -1 else data.synced.indexOfLast { it.timeMs + app.st.lyricsDelayMs <= (app.player.position * 1000).toLong() }
        }
    }
    val state = rememberLazyListState()
    LaunchedEffect(song.id) { state.scrollToItem(0) }
    // Строка, что звучит, стоит чуть выше середины: сверху остаётся прошедшее, снизу видно, что дальше.
    LaunchedEffect(active) {
        if (active >= 0) if (Motion.full) state.animateScrollToItem(active) else state.scrollToItem(active)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = app.st.lyricsSize.sp + (if (synced) 14 else 6)
        LazyColumn(
            Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Края текста растворяются, а не обрезаются.
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.07f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                },
            state = state,
            contentPadding = PaddingValues(top = if (synced) maxHeight * 0.28f else 24.dp, bottom = maxHeight * 0.55f, end = 28.dp),
        ) {
            itemsIndexed(lines) { i, line ->
                LyricLineView(
                    text = line,
                    translation = translated?.getOrNull(i)?.takeIf { it.isNotBlank() && it != line },
                    distance = if (synced) kotlin.math.abs(i - active) else 0,
                    synced = synced,
                    sizeSp = base,
                    onClick = if (synced) ({ app.player.seekSeconds(data.synced[i].timeMs / 1000.0) }) else null,
                )
            }
        }
        // Перевод включается маленькой кнопкой в углу: текст — главное на экране.
        RowCenter(Modifier.align(Alignment.TopEnd).padding(end = 28.dp)) {
            if (!synced) Txt(S("d_lyrics_untimed"), color = C.Muted, size = 12)
            if (translateTo != null && translated == null) Txt("…  ", color = C.Muted, size = 12)
            val lang = com.texfi.w0y.desktop.i18n.Strings.tag
            Txt(
                Look.label(if (translateTo == null) S("player_lyrics_translate") else S("player_lyrics_translate_off")),
                color = if (translateTo == null) C.Muted else C.Blue, size = 11, pixel = true,
                modifier = Modifier.clickable { translateTo = if (translateTo == null) lang else null }.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Одна строка: чем дальше от звучащей, тем она бледнее и размытее, а звучащая — чётко и крупно.
 * Под мышью строка проясняется: её можно нажать, чтобы перемотать к ней.
 */
@Composable
private fun LyricLineView(text: String, translation: String?, distance: Int, synced: Boolean, sizeSp: Int, onClick: (() -> Unit)?) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focus = !synced || distance == 0 || hovered
    val targetAlpha = when {
        focus -> 1f
        distance == 1 -> 0.52f
        distance == 2 -> 0.38f
        else -> 0.28f
    }
    val alpha by animateFloatAsState(targetAlpha, tween(Motion.ms(320)))
    val blur by animateDpAsState(if (focus) 0.dp else (distance * 1.0f).coerceAtMost(3.2f).dp, tween(Motion.ms(320)))
    val scale by animateFloatAsState(if (focus) 1f else 0.95f, tween(Motion.ms(320)))
    Column(
        Modifier.fillMaxWidth().hoverable(source)
            .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, onClick = onClick) else Modifier)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .then(if (blur > 0.1.dp && Motion.full) Modifier.blur(blur, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else Modifier)
            .padding(vertical = if (synced) 9.dp else 5.dp),
    ) {
        BasicText(
            text,
            style = TextStyle(color = C.Text, fontSize = sizeSp.sp, fontWeight = if (synced) FontWeight.Bold else FontWeight.SemiBold, lineHeight = (sizeSp * 1.18f).sp),
        )
        if (translation != null) {
            Txt(translation, color = if (focus) C.Sand else C.SandDeep, size = (sizeSp * 0.5f).toInt().coerceIn(13, 20), maxLines = 3, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---- очередь и история

/** Вкладка «Очередь»: что играет сейчас, что дальше, что уже играло; рядом — общая история прослушивания. */
@Composable
fun QueueList(app: AppState) {
    var history by remember { mutableStateOf(false) }
    val p = app.player
    val upcoming = if (p.index in p.queue.indices) p.queue.size - p.index - 1 else p.queue.size
    Column(Modifier.fillMaxSize()) {
        RowCenter(Modifier.fillMaxWidth()) {
            ChoiceChip(S("player_queue"), !history) { history = false }
            Gap(w = 8)
            ChoiceChip(S("library_history"), history) { history = true }
            Box(Modifier.weight(1f))
            if (!history && upcoming > 0) {
                Txt(S("d_queue_clear"), color = C.Muted, size = 12, modifier = Modifier.clickable { p.clearUpcoming() }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Gap(h = 14)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (history) HistoryPane(app) else QueuePane(app)
        }
    }
}

@Composable
private fun HistoryPane(app: AppState) {
    val songs = remember(app.lib.history) { app.lib.history.take(150).map { it.toItem() } }
    if (songs.isEmpty()) Empty(S("library_empty_text")) else SongList(app, songs)
}

@Composable
private fun QueuePane(app: AppState) {
    val p = app.player
    if (p.queue.isEmpty()) {
        Empty(S("d_queue_empty"))
        return
    }
    var showPlayed by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    val now = p.index
    val played = if (now > 0) (0 until now).toList() else emptyList()
    val upcoming = if (now in p.queue.indices) ((now + 1)..p.queue.lastIndex).toList() else emptyList()
    // Ключ — id и порядковый номер повтора, а не позиция: строка, которую тащат за ручку, остаётся той же
    // и не теряет жест при каждом шаге перестановки.
    fun keyOf(i: Int) = p.queue.getOrNull(i)?.let { s -> "${s.id}#${p.queue.take(i).count { it.id == s.id }}" } ?: "gone#$i"
    LaunchedEffect(now) { state.animateScrollToItem(0) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom)) {
            if (now in p.queue.indices) {
                item(key = "head-now") { QueueHeader(S("d_queue_now")) }
                item(key = "now") { NowCard(app, p.queue[now]) }
            }
            item(key = "head-next") { QueueHeader(S("d_queue_next"), if (upcoming.isEmpty()) null else "${upcoming.size}") }
            if (upcoming.isEmpty()) {
                item(key = "end") { Txt(S("d_queue_end"), color = C.Muted, size = 13, maxLines = 3, modifier = Modifier.padding(vertical = 10.dp)) }
            }
            itemsIndexed(upcoming, key = { _, i -> keyOf(i) }) { _, i -> QueueRow(app, i, keyOf(i), state, dim = false) }
            if (played.isNotEmpty()) {
                item(key = "head-played") {
                    QueueHeader(S("d_queue_played"), "${played.size}", Modifier.clickable { showPlayed = !showPlayed }, chevron = if (showPlayed) "▾" else "▸")
                }
                if (showPlayed) itemsIndexed(played, key = { _, i -> keyOf(i) }) { _, i -> QueueRow(app, i, keyOf(i), state, dim = true) }
            }
            item(key = "tail") { Gap(h = 24) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun QueueHeader(title: String, count: String? = null, modifier: Modifier = Modifier, chevron: String? = null) {
    RowCenter(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        if (chevron != null) { Txt(chevron, color = C.Muted, size = 12); Gap(w = 6) }
        SectionLabel(title)
        if (count != null) { Gap(w = 8); Txt(count, color = C.Muted, size = 11, pixel = true) }
        Gap(w = 12)
        Box(Modifier.weight(1f).height(1.dp).background(C.Border))
    }
}

/** Играющий трек: крупнее остальных, с живыми столбиками, пока звучит. */
@Composable
private fun NowCard(app: AppState, song: SongItem) {
    val p = app.player
    val shape = RoundedCornerShape(Look.card)
    RowCenter(Modifier.fillMaxWidth().clip(shape).background(C.Surface).border(1.dp, C.Blue.copy(alpha = 0.55f), shape).padding(12.dp)) {
        Cover(song.thumbnailUrl, 56.dp)
        Gap(w = 14)
        Column(Modifier.weight(1f)) {
            Txt(song.title, size = 16, weight = FontWeight.SemiBold, color = C.Text)
            Txt(song.artist, color = C.Muted, size = 13)
        }
        Gap(w = 10)
        EqBars(p.playing, C.Blue)
    }
}

/** Три столбика: пока трек звучит, ходят вверх-вниз; на паузе стоят. */
@Composable
private fun EqBars(playing: Boolean, color: Color) {
    val moving = playing && Motion.full
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "eq-a")
    val b by t.animateFloat(0.9f, 0.2f, infiniteRepeatable(tween(680, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "eq-b")
    val c by t.animateFloat(0.35f, 0.85f, infiniteRepeatable(tween(440, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "eq-c")
    Canvas(Modifier.size(22.dp, 18.dp)) {
        val w = 4.dp.toPx()
        val gap = (size.width - w * 3) / 2
        listOf(if (moving) a else 0.4f, if (moving) b else 0.7f, if (moving) c else 0.5f).forEachIndexed { i, h ->
            val bar = size.height * h
            drawRoundRect(color, Offset(i * (w + gap), size.height - bar), Size(w, bar), CornerRadius(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun QueueRow(app: AppState, i: Int, key: String, state: LazyListState, dim: Boolean) {
    val p = app.player
    // Очередь могли укоротить, пока строка ещё перерисовывается.
    val song = p.queue.getOrNull(i) ?: return
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    RowCenter(
        Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(Look.row))
            .background(if (hovered) C.Surface else Color.Transparent)
            .clickable { p.jump(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (dim) Gap(w = 22) else DragHandle(i, key, p, state)
        Gap(w = 6)
        Txt("${i + 1}", color = C.Muted, size = 12, modifier = Modifier.width(30.dp))
        Cover(song.thumbnailUrl, 44.dp, Modifier.alpha(if (dim) 0.6f else 1f))
        Gap(w = 12)
        Column(Modifier.weight(1f)) {
            Txt(song.title, color = if (dim) C.Muted else C.Text, size = 14)
            Txt(song.artist, color = C.Muted, size = 12)
        }
        if (hovered && !dim) {
            if (i > p.index + 1) Txt("▲", color = C.Muted, size = 12, modifier = Modifier.clickable { p.moveInQueue(i, i - 1) }.padding(8.dp))
            if (i < p.queue.lastIndex) Txt("▼", color = C.Muted, size = 12, modifier = Modifier.clickable { p.moveInQueue(i, i + 1) }.padding(8.dp))
        }
        song.durationText?.let { Txt(it, color = C.Muted, size = 12, modifier = Modifier.padding(horizontal = 8.dp)) }
        IconButton(Glyph.Close, onClick = { p.removeFromQueue(i) }, color = C.Muted, size = 14.dp)
    }
}

/**
 * Ручка перестановки: перетаскивание работает только за неё, поэтому случайное движение мыши по строке
 * ничего не меняет. Сдвиг на высоту строки меняет трек местами с соседом; выше играющего он не поднимается.
 */
@Composable
private fun DragHandle(index: Int, rowKey: String, p: PlayerCtl, state: LazyListState) {
    var current by remember { mutableIntStateOf(index) }
    current = index
    var offset by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.width(22.dp).height(32.dp).testTag("queue-handle-$index")
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { offset = 0f }, onDragEnd = { offset = 0f }, onDragCancel = { offset = 0f }) { change, drag ->
                    change.consume()
                    offset += drag.y
                    val row = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rowKey }?.size?.toFloat() ?: 60f
                    while (offset > row / 2 && current < p.queue.lastIndex) {
                        p.moveInQueue(current, current + 1)
                        current++
                        offset -= row
                    }
                    while (offset < -row / 2 && current > p.index + 1) {
                        p.moveInQueue(current, current - 1)
                        current--
                        offset += row
                    }
                }
            }.drawBehind {
                // Две колонки по три точки.
                for (c in 0..1) for (r in 0..2) {
                    drawCircle(C.Muted, 1.6.dp.toPx(), Offset(size.width / 2 + (c - 0.5f) * 6.dp.toPx(), size.height / 2 + (r - 1) * 6.dp.toPx()))
                }
            },
    )
}
