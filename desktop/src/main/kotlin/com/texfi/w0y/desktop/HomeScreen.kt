package com.texfi.w0y.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.desktop.i18n.S
import java.util.Calendar
import java.util.Random

/**
 * Главная. Как на телефоне: быстрый набор, «Быстрый выбор» строками по четыре в колонке, свои подборки
 * (забытые любимые, под время суток), ленты YouTube, недавнее и лайки. В Hairline разделы пронумерованы.
 */
@Composable
fun HomeScreen(app: AppState) {
    ScreenFrame(S("tab_home"), app, canBack = false, actions = {
        PixelButton(if (app.homeLoading) S("d_loading") else S("library_sync"), onClick = app::loadHome, primary = false, enabled = !app.homeLoading)
    }) {
        val state = rememberLazyListState()
        val shelves = app.shelves
        val quickShelf = if (app.st.homeRecBlocks) shelves.firstOrNull { it.songs.isNotEmpty() } else null
        val forgotten = remember(app.lib.liked.size, app.lib.playLog.size) { if (app.st.homeRecBlocks) forgottenFavorites(app) else emptyList() }
        val myFiles = remember(app.lib.localFiles.size) { dailyShuffle(app.localSongs) }
        val byTime = remember(app.lib.playLog.size, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { if (app.st.homeRecBlocks) timeOfDayPicks(app) else emptyList() }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom)) {
                if (app.updater.bannerVisible) item { UpdateBanner(app) }
                item {
                    HairSection(1, S("home_dial"), if (app.dialEntries().isEmpty()) null else S("home_dial_hint"))
                    DialSection(app, header = false)
                }
                quickShelf?.let { shelf ->
                    // Свои файлы подмешиваются в «Быстрый выбор»: несколько штук на сутки, на 3-м, 7-м и 11-м местах.
                    item { PicksShelf(app, S("home_quick_picks"), withOwnFiles(shelf.songs, myFiles), Modifier.padding(top = 4.dp)) }
                }
                if (myFiles.isNotEmpty()) item { PicksShelf(app, S("lm_card"), myFiles.take(12), Modifier.padding(top = 16.dp), playAll = false) }
                if (forgotten.size >= 4) item { PicksShelf(app, S("home_block_forgotten"), forgotten, Modifier.padding(top = 16.dp), playAll = false) }
                if (byTime.size >= 4) item { PicksShelf(app, S("home_block_time"), byTime, Modifier.padding(top = 16.dp), playAll = false) }
                if (!app.st.showRecommendations) {
                    item { Empty(S("home_empty")) }
                } else {
                    item {
                        Gap(h = 14)
                        HairSection(2, S("home_recommendations")) {
                            IconButton(Glyph.Repeat, onClick = app::loadHome, size = 16.dp, color = C.Muted)
                        }
                    }
                    if (shelves.isEmpty()) {
                        item { Empty(app.homeError ?: if (app.homeLoading) S("common_loading") else S("home_recommendations_failed")) }
                    }
                    items(shelves.filter { it !== quickShelf }) { shelf ->
                        if (shelf.songs.isEmpty() && shelf.cards.isEmpty()) return@items
                        if (shelf.songs.isNotEmpty()) {
                            PicksShelf(app, shelf.title, shelf.songs, Modifier.padding(top = 10.dp))
                        } else {
                            SectionLabel(shelf.title, Modifier.padding(top = 12.dp, bottom = 8.dp))
                        }
                        if (shelf.cards.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(shelf.cards) { card -> CardTile(card.title, card.subtitle, card.thumbnailUrl) { app.openCard(card) } }
                            }
                        }
                        Gap(h = 8)
                    }
                }
                val recent = app.lib.history.take(20).map { it.toItem() }
                if (recent.isNotEmpty()) {
                    item {
                        Gap(h = 18)
                        HairSection(3, S("home_recent"))
                    }
                    itemsIndexed(recent, key = { i, s -> "recent-${s.id}#$i" }) { i, song ->
                        SongRow(app, song, onPlay = { app.player.play(recent, i) }, index = i, songs = recent)
                    }
                }
                val liked = app.lib.liked.take(10).map { it.toItem() }
                if (liked.isNotEmpty()) {
                    item {
                        Gap(h = 18)
                        HairSection(4, S("library_likes"))
                    }
                    itemsIndexed(liked, key = { i, s -> "liked-${s.id}#$i" }) { i, song ->
                        SongRow(app, song, onPlay = { app.player.play(liked, i) }, index = i, songs = liked)
                    }
                }
                item { Gap(h = 24) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

/** Заголовок раздела: у Hairline «01 ❯ ПОДПИСЬ ———— действие», у остальных обычная подпись раздела. */
@Composable
fun HairSection(index: Int, label: String, hint: String? = null, action: @Composable () -> Unit = {}) {
    if (!Look.hair) {
        RowCenter(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) { SectionLabel(label, Modifier.weight(1f)); action() }
        hint?.let { Txt(it, color = C.Muted, size = 11, modifier = Modifier.padding(bottom = 8.dp)) }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp).hairEnter(0)) {
        RowCenter(Modifier.fillMaxWidth()) {
            Txt("%02d".format(index), color = C.Muted.copy(alpha = 0.6f), size = 11, pixel = true)
            Gap(w = 8)
            Txt("❯", color = C.Muted.copy(alpha = 0.6f), size = 11, pixel = true)
            Gap(w = 8)
            Txt(Look.label(label), color = C.Muted, size = 11, pixel = true)
            Gap(w = 12)
            // Линия выезжает слева направо.
            val draw = remember { androidx.compose.animation.core.Animatable(if (Anim.fresh && !Motion.off) 0f else 1f) }
            androidx.compose.runtime.LaunchedEffect(Unit) { if (draw.value < 1f) draw.animateTo(1f, tween(Motion.ms(700), delayMillis = 120, easing = EaseOut)) }
            Box(Modifier.weight(1f).height(Look.line).graphicsLayer { scaleX = draw.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }.background(C.Border))
            Gap(w = 8)
            action()
        }
        hint?.let { Txt(it, color = C.Muted.copy(alpha = 0.8f), size = 11, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/**
 * Лента треков: строки по четыре в колонке, колонки листаются вбок. Справа от названия — «слушать всё»;
 * правая кнопка мыши открывает общее меню трека.
 */
@Composable
fun PicksShelf(app: AppState, title: String, songs: List<SongItem>, modifier: Modifier = Modifier, playAll: Boolean = true) {
    if (songs.isEmpty()) return
    val columns = remember(songs) { songs.withIndex().chunked(4) }
    Column(modifier.fillMaxWidth()) {
        RowCenter(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            SectionLabel(title, Modifier.weight(1f))
            if (playAll) PixelButton(S("home_play_all"), onClick = { app.player.play(songs, 0) }, primary = false)
        }
        val state = rememberLazyListState()
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(columns) { ci, column ->
                Column(Modifier.width(380.dp).hairEnter(ci)) {
                    column.forEach { (index, song) -> PickRow(app, song, songs, index) }
                }
            }
        }
        if (columns.size > 3) HorizontalScrollbar(rememberScrollbarAdapter(state), Modifier.fillMaxWidth())
    }
}

@Composable
private fun PickRow(app: AppState, song: SongItem, all: List<SongItem>, index: Int) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val current = app.player.current?.id == song.id
    val menu = rowMenu(app, song, all) { app.player.play(all, index) }
    ContextMenuArea(items = { menu(false) }) {
        RowCenter(
            Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(Look.row)).background(if (hovered) C.Surface else androidx.compose.ui.graphics.Color.Transparent)
                .clickable { app.player.play(all, index) }.padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Cover(song.thumbnailUrl, 52.dp)
            Gap(w = 14)
            Column(Modifier.weight(1f)) {
                Txt(song.title, color = if (current) C.Blue else C.Text, size = 14, weight = if (current) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal)
                Txt(listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.plays).joinToString(" · "), color = C.Muted, size = 12)
            }
            if (hovered) IconButton(Glyph.Plus, onClick = { app.player.enqueue(song, next = false) }, color = C.Muted, size = 16.dp)
        }
    }
}

/** Свои файлы в порядке, который меняется раз в сутки: каждый день наверху разные. */
private fun dailyShuffle(files: List<SongItem>): List<SongItem> = files.shuffled(Random(System.currentTimeMillis() / (24L * 3600 * 1000)))

private fun withOwnFiles(picks: List<SongItem>, files: List<SongItem>): List<SongItem> {
    if (files.isEmpty()) return picks
    val out = picks.toMutableList()
    files.take(3).forEachIndexed { i, f -> out.add((2 + i * 4).coerceAtMost(out.size), f) }
    return out
}

/** Забытые любимые: лайкнутое, чего давно не включали (или не включали вовсе). Набор меняется раз в сутки. */
private fun forgottenFavorites(app: AppState): List<SongItem> {
    val cutoff = System.currentTimeMillis() - 21L * 24 * 3600 * 1000
    val recent = app.lib.playLog.filter { it.at >= cutoff }.mapTo(HashSet()) { it.song.id }
    val day = System.currentTimeMillis() / (24L * 3600 * 1000)
    return app.lib.liked.filter { it.id !in recent && it.artist.substringBefore(',').trim().lowercase() !in app.st.tasteExcluded }.shuffled(Random(day)).take(12).map { it.toItem() }
}

/** Под это время суток: что чаще всего включали в такие же часы (±2 ч), кроме слушанного за последние два часа. */
private fun timeOfDayPicks(app: AppState): List<SongItem> {
    val cal = Calendar.getInstance()
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val now = System.currentTimeMillis()
    fun hourOf(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)
    fun near(h: Int) = minOf((h - hour + 24) % 24, (hour - h + 24) % 24) <= 2
    val fresh = app.lib.playLog.filter { it.at >= now - 2 * 3600 * 1000 }.mapTo(HashSet()) { it.song.id }
    val counts = HashMap<String, Int>()
    val songs = HashMap<String, com.texfi.w0y.desktop.StoredSong>()
    app.lib.playLog.filter { near(hourOf(it.at)) && it.song.id !in fresh && it.song.artist.substringBefore(',').trim().lowercase() !in app.st.tasteExcluded }.forEach {
        songs[it.song.id] = it.song
        counts.merge(it.song.id, 1, Int::plus)
    }
    return counts.entries.sortedByDescending { it.value }.take(12).mapNotNull { songs[it.key]?.toItem() }
}
