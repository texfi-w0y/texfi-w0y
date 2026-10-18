package com.texfi.w0y.desktop

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texfi.w0y.desktop.i18n.S
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «Быстрый набор» как на телефоне: сетка квадратных обложек без коробок (подпись под обложкой), по два ряда
 * на страницу, страницы листаются. Закреплённое — первым, дальше то, что слушается чаще, затем свои плейлисты.
 * Нажатие открывает или играет, долгое нажатие и правая кнопка — меню «закрепить / убрать».
 */
@Composable
fun DialSection(app: AppState) {
    val entries = remember(app.lib) { app.dialEntries() }
    // Адреса первых треков готовятся заранее: туда нажимают чаще всего, и старт с плитки не должен ждать извлечения.
    LaunchedEffect(entries.firstOrNull { it.song != null }?.key) {
        if (!app.st.preloadNext) return@LaunchedEffect
        delay(2500)
        entries.mapNotNull { it.song }.filterNot { LocalFiles.isLocal(it.id) }.take(DIAL_PREFETCH).forEach { runCatching { app.yt.stream(it.id, app.st.quality) } }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val columns = ((maxWidth + gap) / (150.dp + gap)).toInt().coerceIn(3, 8)
        val tile = (maxWidth - gap * (columns - 1)) / columns
        val labelHeight = 6.dp + 38.dp
        if (entries.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { repeat(columns.coerceAtMost(4)) { GhostTile(tile) } }
            Txt(S("home_dial_empty"), color = C.Muted, size = 13, maxLines = 3, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp))
            return@BoxWithConstraints
        }
        val perPage = columns * DIAL_ROWS
        val pages = entries.chunked(perPage)
        val rows = if (pages.size > 1) DIAL_ROWS else ((entries.size + columns - 1) / columns).coerceIn(1, DIAL_ROWS)
        val pageHeight = (tile + labelHeight) * rows + gap * (rows - 1)
        val pager = rememberPagerState(pageCount = { pages.size })
        val scope = rememberCoroutineScope()
        Column {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(pageHeight), pageSpacing = 24.dp, userScrollEnabled = true) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    pages[page].chunked(columns).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEachIndexed { c, e -> DialTile(app, e, entries, tile, order = r * columns + c) }
                            repeat(columns - row.size) { GhostTile(tile, label = true) }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                RowCenter(Modifier.fillMaxWidth().padding(top = 10.dp), ) {
                    Box(Modifier.weight(1f))
                    IconButton(Glyph.ChevronLeft, onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0), animationSpec = tween(Motion.ms(300), easing = EaseOut)) } }, color = C.Muted, size = 14.dp, box = 28.dp)
                    repeat(pages.size) { i ->
                        val on = i == pager.currentPage
                        Box(
                            Modifier.padding(horizontal = 4.dp).size(if (on) 7.dp else 5.dp).clip(CircleShape)
                                .border(Look.line, if (on) C.Blue else C.Border, CircleShape)
                                .background(if (on) C.Blue else Color.Transparent, CircleShape)
                                .clickable { scope.launch { pager.animateScrollToPage(i, animationSpec = tween(Motion.ms(300), easing = EaseOut)) } },
                        )
                    }
                    IconButton(Glyph.ChevronRight, onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pages.size - 1), animationSpec = tween(Motion.ms(300), easing = EaseOut)) } }, color = C.Muted, size = 14.dp, box = 28.dp)
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
    Gap(h = 14)
}

/** Кнопка «закрепить на быстром наборе» в шапке артиста, альбома, плейлиста. */
@Composable
fun PinButton(app: AppState, key: String, title: String, subtitle: String?, thumb: String?) {
    val pinned = app.isPinned(key)
    IconButton(Glyph.Pin, onClick = { app.togglePin(key, title, subtitle, thumb) }, color = if (pinned) C.Blue else C.Muted, size = 20.dp, box = 40.dp)
}

/** Призрачная плитка: пустое место набора не выглядит недогруженным. */
@Composable
private fun GhostTile(size: Dp, label: Boolean = false) {
    val shape = RoundedCornerShape(Look.cover)
    Column {
        Box(Modifier.size(size).clip(shape).border(Look.line, C.Border.copy(alpha = 0.6f), shape), contentAlignment = Alignment.Center) { Icon(Glyph.Plus, C.Border, size / 5) }
        if (label) Gap(h = 44)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialTile(app: AppState, e: DialEntry, all: List<DialEntry>, tile: Dp, order: Int) {
    val source = remember { MutableInteractionSource() }
    val menu = remember(e.key, e.pinned) { listOf(
        androidx.compose.foundation.ContextMenuItem(if (e.pinned) S("dial_unpin") else S("dial_pin")) { app.togglePin(e.key, e.title, e.subtitle, e.thumb) },
        androidx.compose.foundation.ContextMenuItem(S("dial_hide")) { hide(app, e) },
    ) }
    androidx.compose.foundation.ContextMenuArea(items = { menu }) {
        Column(
            Modifier.width(tile).hairEnter(order).hoverLift(source).pressScale(source, 0.97f).hoverable(source)
                .combinedClickable(interactionSource = source, indication = null, onLongClick = { app.dialMenu = e }, onClick = { open(app, e, all) }),
        ) {
            Box(Modifier.size(tile)) {
                Cover(e.thumb, tile, circle = e.kind == "artist")
                if (e.pinned) {
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(C.Background.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        Icon(Glyph.Pin, C.Blue, 14.dp)
                    }
                }
            }
            Gap(h = 6)
            RowCenter {
                val kind = when (e.kind) { "artist" -> Glyph.Artist; "album", "remote", "playlist" -> Glyph.Release; else -> null }
                if (kind != null) { Icon(kind, C.Blue, 12.dp); Gap(w = 5) }
                Txt(e.title, size = 13)
            }
            e.subtitle?.let { Txt(it, color = C.Muted, size = 12) }
        }
    }
}

/** Что делает нажатие: трек играет вместе с остальными треками набора, остальное открывается. */
private fun open(app: AppState, e: DialEntry, all: List<DialEntry>) {
    when (e.kind) {
        "artist" -> app.open(Screen.Artist(e.id, e.title, e.thumb))
        "album" -> app.open(Screen.Album(e.id, e.title, e.thumb))
        "remote" -> app.open(Screen.Remote(e.id, e.title, e.thumb))
        "playlist" -> app.open(Screen.Local(e.id))
        else -> {
            val queue = all.mapNotNull { it.song }
            val index = queue.indexOfFirst { it.id == e.song?.id }
            if (index >= 0) app.player.play(queue, index) else e.song?.let { app.player.play(listOf(it), 0) }
        }
    }
}

private fun hide(app: AppState, e: DialEntry) {
    app.hideFromDial(e.key)
    app.dialUndo = e
}

/** Меню плитки: закрепить или убрать на время. */
@Composable
fun DialMenu(app: AppState, e: DialEntry) {
    Box(
        Modifier.fillMaxSize().background(C.Background.copy(alpha = 0.85f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { app.dialMenu = null },
        contentAlignment = Alignment.Center,
    ) {
        PixelCard(Modifier.padding(24.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            Column {
                SectionLabel(S("track_panel_title"))
                Gap(h = 6)
                Txt(e.title, color = C.Muted, size = 13)
                Gap(h = 14)
                RowCenter {
                    PixelButton(S(if (e.pinned) "dial_unpin" else "dial_pin"), onClick = { app.togglePin(e.key, e.title, e.subtitle, e.thumb); app.dialMenu = null })
                    Gap(w = 10)
                    PixelButton(S("dial_hide"), onClick = { hide(app, e); app.dialMenu = null }, primary = false)
                }
            }
        }
    }
}

/** Плашка после «убрать»: можно сразу вернуть (пять секунд). */
@Composable
fun DialUndoBar(app: AppState, e: DialEntry, modifier: Modifier = Modifier) {
    LaunchedEffect(e.key) {
        delay(5_000)
        if (app.dialUndo?.key == e.key) app.dialUndo = null
    }
    val shape = RoundedCornerShape(Look.card.coerceAtMost(14.dp))
    RowCenter(modifier.padding(16.dp).background(C.SurfaceHigh, shape).border(Look.line, C.Border, shape).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)) {
        Txt(S("dial_hidden_toast"), size = 13)
        Gap(w = 14)
        PixelButton(S("dial_undo"), onClick = { app.restoreDial(e.key, e.pinned); app.dialUndo = null })
    }
}

private const val DIAL_ROWS = 2
private const val DIAL_PREFETCH = 4
