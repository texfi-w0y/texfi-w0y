package com.texfi.w0y.desktop

import com.texfi.w0y.desktop.i18n.S
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SoundProfile
import java.util.Locale

private enum class PlayerTab(val key: String) {
    Queue("player_queue"),
    Similar("d_tab_similar"),
    Lyrics("player_lyrics"),
    Sound("player_sound"),
    ;

    val label: String get() = S(key)
}

/** Полноэкранный плеер: большая обложка слева, справа очередь, текст и звук. */
@Composable
fun PlayerScreen(app: AppState) {
    val p = app.player
    var tab by remember { mutableStateOf(PlayerTab.Queue) }
    val song = p.current
    val glow = if (app.st.playerCoverGlow) rememberCoverColor(song?.thumbnailUrl) else null
    val tabs = PlayerTab.entries.filter { it != PlayerTab.Lyrics || app.st.showLyrics }
    if (tab !in tabs) tab = PlayerTab.Queue
    val glowBrush = glow?.let { androidx.compose.ui.graphics.Brush.verticalGradient(listOf(it.copy(alpha = 0.30f), androidx.compose.ui.graphics.Color.Transparent)) }
    Row(Modifier.fillMaxSize().then(if (glowBrush != null) Modifier.background(glowBrush) else Modifier).padding(28.dp)) {
        Column(Modifier.width(340.dp).fillMaxHeight(), horizontalAlignment = Alignment.Start) {
            val viz = app.st.playerArt == PlayerArt.VISUALIZER && SpectrumCapture.available
            Box(Modifier.clickable(enabled = SpectrumCapture.available) { app.setSt { it.copy(playerArt = if (viz) PlayerArt.COVER else PlayerArt.VISUALIZER) } }) {
                if (viz) {
                    Box(Modifier.width(340.dp).height(340.dp).clip(RoundedCornerShape(Look.cover)).background(C.Surface)) {
                        if (app.st.visualizerCoverBackdrop) Cover(song?.thumbnailUrl, 340.dp, Modifier.alpha(0.3f))
                        Visualizer(app.st.visualizerStyle, p.playing, app.st.visualizerSensitivity, app.st.visualizerFps, Modifier.fillMaxSize().padding(12.dp))
                    }
                } else {
                    PixelCover(song?.thumbnailUrl)
                }
            }
            Gap(h = 20)
            Txt(song?.title ?: S("d_idle"), size = 22, weight = FontWeight.Medium, maxLines = 2)
            Gap(h = 4)
            if (song != null) {
                Txt(
                    song.artist,
                    color = C.Blue,
                    size = 15,
                    modifier = Modifier.clickable(enabled = song.artistId != null) {
                        song.artistId?.let { app.open(Screen.Artist(it, song.artist, null)) }
                    },
                )
                song.album?.let { a ->
                    Txt(
                        a, color = C.Muted, size = 13,
                        modifier = Modifier.clickable(enabled = song.albumId != null) {
                            song.albumId?.let { app.open(Screen.Album(it, a, song.thumbnailUrl)) }
                        },
                    )
                }
                if (!p.soundNow.isPlain) Txt(S("d_your_version", p.soundNow.label), color = C.Sand, size = 12, modifier = Modifier.padding(top = 6.dp))
            }
            Gap(h = 18)
            SeekRow(p, app.st.seekStepSec)
            Gap(h = 14)
            RowCenter {
                IconButton(Glyph.Shuffle, onClick = p::toggleShuffle, color = if (app.st.shuffle) C.Blue else C.Muted, size = 18.dp, box = 38.dp)
                IconButton(Glyph.Prev, onClick = p::previous, size = 24.dp, box = 44.dp)
                Gap(w = 8)
                Box(
                    (if (Look.smooth) Modifier.width(54.dp).height(54.dp).clip(androidx.compose.foundation.shape.CircleShape).background(C.Blue)
                    else Modifier.width(54.dp).height(54.dp).clip(RoundedCornerShape(4.dp)).background(C.Blue).border(2.dp, C.BlueDeep, RoundedCornerShape(4.dp)))
                        .clickable(onClick = p::toggle),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (p.playing) Glyph.Pause else Glyph.Play, C.OnAccent, 28.dp) }
                Gap(w = 8)
                IconButton(Glyph.Next, onClick = { p.nextClick() }, size = 24.dp, box = 44.dp)
                Box {
                    IconButton(Glyph.Repeat, onClick = p::cycleRepeat, color = if (app.st.repeat != RepeatMode.OFF) C.Blue else C.Muted, size = 18.dp, box = 38.dp)
                    if (app.st.repeat == RepeatMode.ONE) Txt("1", size = 9, color = C.Blue, weight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                }
                Box(Modifier.weight(1f))
                if (song != null) {
                    val liked = song.id in app.likedIds
                    IconButton(if (liked) Glyph.Heart else Glyph.HeartOff, onClick = { app.toggleLike(song) }, color = if (liked) C.Danger else C.Muted, size = 20.dp)
                    DownloadButton(app, song)
                }
                SleepMenu(app)
            }
        }
        Gap(w = 32)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            RowCenter {
                tabs.forEach { t ->
                    val active = t == tab
                    Box(
                        Modifier.clip(RoundedCornerShape(if (Look.smooth) 50.dp else 4.dp)).background(if (active) (if (Look.smooth) C.Blue.copy(alpha = 0.22f) else C.SurfaceHigh) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { tab = t }.padding(horizontal = 8.dp, vertical = 10.dp),
                    ) { Txt(if (Look.smooth) t.label else t.label.uppercase(), pixel = true, size = if (Look.smooth) 14 else 9, color = if (active) C.Blue else C.Muted) }
                    Gap(w = 2)
                }
            }
            Gap(h = 12)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    PlayerTab.Queue -> QueueList(app)
                    PlayerTab.Similar -> SimilarPane(app)
                    PlayerTab.Lyrics -> LyricsPane(app)
                    PlayerTab.Sound -> SoundPane(app)
                }
            }
        }
    }
}

@Composable
private fun PixelCover(url: String?) {
    if (Look.smooth) {
        Box(Modifier.shadow(18.dp, RoundedCornerShape(Look.cover + 6.dp), clip = false)) { Cover(url, 340.dp) }
        return
    }
    Box(Modifier.drawBehind { drawRect(C.Shadow, androidx.compose.ui.geometry.Offset(6.dp.toPx(), 6.dp.toPx()), size) }.border(2.dp, C.Border, RoundedCornerShape(4.dp))) {
        Cover(url, 340.dp)
    }
}

@Composable
private fun SeekRow(p: PlayerCtl, stepSec: Int) {
    Column {
        Box(Modifier.fillMaxWidth()) { SegmentedSeek(p.position, p.duration, onSeek = p::seekFraction) }
        RowCenter(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Txt(fmtTime(p.position), color = C.Muted, size = 12)
            Box(Modifier.weight(1f))
            val step = stepSec
            Txt("−$step", color = C.Muted, size = 12, modifier = Modifier.clickable { p.seekSeconds((p.position - step).coerceAtLeast(0.0)) }.padding(horizontal = 8.dp, vertical = 2.dp))
            Txt("+$step", color = C.Muted, size = 12, modifier = Modifier.clickable { p.seekSeconds((p.position + step).coerceAtMost(p.duration)) }.padding(horizontal = 8.dp, vertical = 2.dp))
            Box(Modifier.weight(1f))
            Txt(fmtTime(p.duration), color = C.Muted, size = 12)
        }
    }
}

fun fmtTime(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun QueueList(app: AppState) {
    val p = app.player
    if (p.queue.isEmpty()) {
        Empty(S("d_queue_empty"))
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(p.index) { if (p.index >= 0) state.animateScrollToItem((p.index - 2).coerceAtLeast(0)) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom)) {
            // Ключ — id и порядковый номер повтора, а не позиция: строка, которую тащат за ручку, остаётся той же
            // и не теряет жест при каждом шаге перестановки.
            itemsIndexed(p.queue, key = { i, s -> "${s.id}#${p.queue.take(i).count { it.id == s.id }}" }) { i, song ->
                val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                RowCenter(
                    Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(Look.row))
                        .background(if (i == p.index) C.Surface else hoverBackground(source))
                        .clickable { p.jump(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    DragHandle(i, p, state)
                    Gap(w = 6)
                    Txt(if (i == p.index) "▶" else "${i + 1}", color = if (i == p.index) C.Blue else C.Muted, size = 12, modifier = Modifier.width(30.dp))
                    Cover(song.thumbnailUrl, 40.dp)
                    Gap(w = 12)
                    Column(Modifier.weight(1f)) {
                        Txt(song.title, color = if (i == p.index) C.Blue else C.Text, size = 14)
                        Txt(song.artist, color = C.Muted, size = 12)
                    }
                    if (i > 0 && i != p.index) Txt("▲", color = C.Muted, size = 12, modifier = Modifier.clickable { p.moveInQueue(i, i - 1) }.padding(8.dp))
                    if (i < p.queue.lastIndex && i != p.index) Txt("▼", color = C.Muted, size = 12, modifier = Modifier.clickable { p.moveInQueue(i, i + 1) }.padding(8.dp))
                    IconButton(Glyph.Close, onClick = { p.removeFromQueue(i) }, color = C.Muted, size = 14.dp)
                }
            }
            item { Gap(h = 24) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/**
 * Ручка перестановки: перетаскивание работает только за неё, поэтому случайное движение мыши по строке
 * ничего не меняет. Сдвиг на высоту строки меняет трек местами с соседом.
 */
@Composable
private fun DragHandle(index: Int, p: PlayerCtl, state: androidx.compose.foundation.lazy.LazyListState) {
    var current by remember { androidx.compose.runtime.mutableIntStateOf(index) }
    current = index
    var offset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    Box(
        Modifier.width(22.dp).height(32.dp).testTag("queue-handle-$index")
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { offset = 0f }, onDragEnd = { offset = 0f }, onDragCancel = { offset = 0f }) { change, drag ->
                    change.consume()
                    offset += drag.y
                    val row = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }?.size?.toFloat() ?: 60f
                    while (offset > row / 2 && current < p.queue.lastIndex) {
                        p.moveInQueue(current, current + 1)
                        current++
                        offset -= row
                    }
                    while (offset < -row / 2 && current > 0) {
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

@Composable
private fun LyricsPane(app: AppState) {
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
    val lines = if (data.synced.isNotEmpty()) data.synced.map { it.text } else data.plain.orEmpty().lines()
    val translated by produceState<List<String>?>(null, song.id, translateTo) {
        value = null
        value = translateTo?.let { app.lyrics.translate(song.id, data, it) }
    }
    val active by remember(data) {
        derivedStateOf {
            if (data.synced.isEmpty()) -1 else data.synced.indexOfLast { it.timeMs <= (app.player.position * 1000).toLong() }
        }
    }
    val state = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) state.animateScrollToItem((active - 3).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize()) {
        RowCenter {
            val lang = com.texfi.w0y.desktop.i18n.Strings.tag
            PixelButton(if (translateTo == null) S("player_lyrics_translate") else S("player_lyrics_translate_off"), onClick = { translateTo = if (translateTo == null) lang else null }, primary = false)
            if (translateTo != null && translated == null) Txt("  …", color = C.Muted, size = 12)
            if (data.synced.isEmpty()) Txt("  " + S("d_lyrics_untimed"), color = C.Muted, size = 12)
        }
        Gap(h = 10)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom)) {
                itemsIndexed(lines) { i, line ->
                    val current = i == active
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = data.synced.isNotEmpty()) { app.player.seekSeconds(data.synced[i].timeMs / 1000.0) }.padding(vertical = 5.dp),
                    ) {
                        Txt(line, color = if (current) C.Text else C.Muted, size = if (current) app.st.lyricsSize.sp + 3 else app.st.lyricsSize.sp, weight = if (current) FontWeight.Medium else FontWeight.Normal, maxLines = 3)
                        translated?.getOrNull(i)?.takeIf { it.isNotBlank() && it != line }?.let { Txt(it, color = if (current) C.Sand else C.SandDeep, size = 14, maxLines = 3) }
                    }
                }
                item { Gap(h = 120) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun SoundPane(app: AppState) {
    val p = app.player
    val song = p.current
    if (song == null) {
        Empty(S("d_sound_idle"))
        return
    }
    val s = p.soundNow
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Txt(S("player_version_own"), color = C.Muted, size = 13, maxLines = 2)
        RowCenter {
            PixelButton(S("preset_normal"), onClick = { p.setSound(SoundProfile.Plain) }, primary = s.isPlain, modifier = Modifier)
            Gap(w = 8)
            PixelButton(S("preset_slowed"), onClick = { p.setSound(SoundProfile.Slowed) }, primary = s == SoundProfile.Slowed)
            Gap(w = 8)
            PixelButton(S("preset_sped"), onClick = { p.setSound(SoundProfile.Sped) }, primary = s == SoundProfile.Sped)
        }
        LabeledSlider(S("settings_speed_title"), "%.2f×".format(s.speed), s.speed, 0.5f..2f) { p.setSound(s.copy(speed = it)) }
        LabeledSlider(S("settings_pitch_title"), "%.2f".format(s.pitch), s.pitch, 0.7f..1.4f) { p.setSound(s.copy(pitch = it)) }
        Column {
            SectionLabel(S("settings_reverb_title"))
            Gap(h = 8)
            RowCenter {
                Reverb.entries.forEach { r ->
                    PixelButton(r.label, onClick = { p.setSound(s.copy(reverb = r)) }, primary = s.reverb == r)
                    Gap(w = 8)
                }
            }
        }
        Txt(S("d_sound_hint"), color = C.Muted, size = 11)
    }
}

@Composable
private fun LabeledSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        RowCenter { SectionLabel(label, Modifier.weight(1f)); Txt(valueText, size = 14) }
        Gap(h = 8)
        PixelSlider(value, range, onChange, Modifier.fillMaxWidth())
    }
}

/** Таймер сна: через N минут или до конца трека. */
@Composable
fun SleepMenu(app: AppState) {
    val p = app.player
    var open by remember { mutableStateOf(false) }
    val active = p.sleepRemaining != null || p.sleepAfterTrack
    Box {
        RowCenter {
            if (p.sleepRemaining != null) Txt(fmtTime(p.sleepRemaining!!.toDouble()), color = C.Sand, size = 12)
            if (p.sleepAfterTrack) Txt(S("player_sleep_track"), color = C.Sand, size = 12)
            IconButton(Glyph.Timer, onClick = { open = !open }, color = if (active) C.Sand else C.Muted, size = 18.dp)
        }
        if (open) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, -260),
                onDismissRequest = { open = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Column(Modifier.width(220.dp).background(C.SurfaceHigh, RoundedCornerShape(6.dp)).border(2.dp, C.Border, RoundedCornerShape(6.dp)).padding(6.dp)) {
                    Txt(S("player_sleep_title"), pixel = true, size = 9, color = C.Blue, modifier = Modifier.padding(8.dp))
                    listOf(app.st.sleepTimerDefaultMin, 15, 30, 45, 60).distinct().forEach { m ->
                        Txt(if (m == app.st.sleepTimerDefaultMin) S("d_sleep_default", m) else S("settings_sleep_minutes", m), size = 14, modifier = Modifier.fillMaxWidth().clickable { p.startSleep(m); open = false }.padding(8.dp))
                    }
                    Txt(S("player_sleep_track"), size = 14, modifier = Modifier.fillMaxWidth().clickable { p.sleepUntilTrackEnds(); open = false }.padding(8.dp))
                    if (active) Txt(S("player_sleep_off"), color = C.Sand, size = 14, modifier = Modifier.fillMaxWidth().clickable { p.cancelSleep(); open = false }.padding(8.dp))
                }
            }
        }
    }
}

/** «Похожее»: умные рекомендации к текущему треку (см. Recommender). */
@Composable
private fun SimilarPane(app: AppState) {
    val song = app.player.current
    if (song == null) {
        Empty(S("d_similar_idle"))
        return
    }
    var reload by remember { mutableStateOf(0) }
    val recs by produceState<List<com.texfi.w0y.data.SongItem>?>(null, song.id, reload) {
        value = null
        value = runCatching { app.recommender.forSeed(song, exclude = app.player.queue.map { it.id }.toSet()) }.getOrDefault(emptyList())
    }
    val list = recs
    Column(Modifier.fillMaxSize()) {
        Txt(S("d_similar_note"), color = C.Muted, size = 12, maxLines = 3)
        Gap(h = 10)
        RowCenter {
            PixelButton(S("queue_enqueue"), onClick = { list?.forEach { app.player.enqueue(it, next = false) } }, enabled = !list.isNullOrEmpty())
            Gap(w = 8)
            PixelButton(S("library_sync"), onClick = { reload++ }, primary = false)
        }
        Gap(h = 10)
        when {
            list == null -> Empty(S("player_finding_similar"))
            list.isEmpty() -> Empty(S("d_similar_none"))
            else -> SongList(app, list)
        }
    }
}
