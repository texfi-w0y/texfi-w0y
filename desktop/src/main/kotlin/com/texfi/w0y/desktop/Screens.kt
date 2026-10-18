package com.texfi.w0y.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.desktop.i18n.S
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem
import kotlinx.coroutines.launch

@Composable
fun ScreenFrame(title: String, app: AppState, canBack: Boolean, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Gap(h = 22)
        RowCenter {
            if (canBack) {
                IconButton(Glyph.Prev, onClick = app::back)
                Gap(w = 8)
            }
            if (Look.smooth) Txt(if (Look.hair) Look.title(title) else title.replaceFirstChar(Char::uppercase), size = 28, pixel = Look.hair, weight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
            else Txt(Look.title(title), pixel = true, size = 18, modifier = Modifier.weight(1f))
            actions()
        }
        if (!Look.smooth) Box(Modifier.padding(top = 10.dp).width(56.dp).height(3.dp).background(C.Blue))
        Gap(h = 14)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
fun Empty(text: String) = Txt(text, color = C.Muted, size = 14, modifier = Modifier.padding(vertical = 18.dp), maxLines = 4)

/** Строка трека: обложка, название, исполнитель, сердце и «в очередь». */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun SongRow(app: AppState, song: SongItem, onPlay: () -> Unit, index: Int = -1, songs: List<SongItem>? = null, extra: @Composable () -> Unit = {}) {
    val source = remember { MutableInteractionSource() }
    val bg = hoverBackground(source)
    val liked = song.id in app.likedIds
    val current = app.player.current?.id == song.id
    val selected = song.id in app.selection.ids
    val focused by source.collectIsFocusedAsState()
    val window = androidx.compose.ui.platform.LocalWindowInfo.current
    val rowShape = RoundedCornerShape(Look.row)
    // Двойной клик играет, одиночный выделяет (Ctrl — добавить, Shift — диапазон); вне списков с номерами — сразу играет.
    val selectable = songs != null && index >= 0
    val menu = rowMenu(app, song, songs, onPlay)
    androidx.compose.foundation.ContextMenuArea(items = { menu(selected) }) {
        RowCenter(
            Modifier.fillMaxWidth().then(if (Look.hair && index in 0..7) Modifier.hairEnter(index) else Modifier).hoverable(source).clip(rowShape).background(if (selected) C.SurfaceHigh else bg)
                .then(if (focused) Modifier.border(1.dp, C.Blue, rowShape) else Modifier)
                .drawBehind { if (selected && Look.hair) drawRect(C.Blue, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }
                .focusable(interactionSource = source)
                .onKeyEvent {
                    if (it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && (it.key == androidx.compose.ui.input.key.Key.Enter || it.key == androidx.compose.ui.input.key.Key.NumPadEnter)) {
                        onPlay(); true
                    } else false
                }
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onDoubleClick = if (selectable && app.st.doubleClickPlay) onPlay else null,
                    onClick = {
                        val m = window.keyboardModifiers
                        when {
                            selectable && m.isShiftPressed -> app.selection.range(songs!!, index)
                            selectable && m.isCtrlPressed -> app.selection.toggle(song.id, index)
                            selectable && app.st.doubleClickPlay -> app.selection.only(song.id, index)
                            else -> onPlay()
                        }
                    },
                ).padding(horizontal = 8.dp, vertical = if (app.st.compactRows || Look.compact) 2.dp else 6.dp),
        ) {
            Cover(song.thumbnailUrl, if (app.st.compactRows || Look.compact) 32.dp else 44.dp)
            Gap(w = 12)
            Column(Modifier.weight(1f)) {
                Txt(song.title, color = if (current) C.Blue else C.Text, size = 15, weight = if (current) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal)
                Txt(song.artist, color = C.Muted, size = 12)
            }
            song.durationText?.let { Txt(it, color = C.Muted, size = 12, modifier = Modifier.padding(end = 8.dp)) }
            extra()
            DownloadButton(app, song)
            IconButton(Glyph.Plus, onClick = { app.player.enqueue(song, next = false) }, color = C.Muted, size = 16.dp)
            IconButton(
                if (liked) Glyph.Heart else Glyph.HeartOff,
                onClick = { app.toggleLike(song) },
                color = if (liked) C.Danger else C.Muted,
                size = 18.dp,
            )
        }
    }
}

/**
 * Меню правой кнопки. Если строка входит в выделение, действия относятся ко всем выделенным;
 * иначе — к одному треку. Строки читаются здесь (в композиции), а пункты собираются при открытии меню.
 */
@Composable
internal fun rowMenu(app: AppState, song: SongItem, songs: List<SongItem>?, onPlay: () -> Unit): (Boolean) -> List<androidx.compose.foundation.ContextMenuItem> {
    val play = S("d_menu_play")
    val next = S("d_menu_play_next")
    val queue = S("d_menu_queue_add")
    val like = S("d_menu_like")
    val unlike = S("d_menu_unlike")
    val download = S("d_menu_download")
    val artist = S("d_menu_artist")
    val album = S("d_menu_album")
    val copy = S("d_menu_copy_link")
    val editFile = S("lm_edit")
    return { inSelection ->
        val targets = if (inSelection && songs != null) app.selection.of(songs).ifEmpty { listOf(song) } else listOf(song)
        val single = targets.size == 1
        buildList {
            if (single) add(androidx.compose.foundation.ContextMenuItem(play, onPlay))
            add(androidx.compose.foundation.ContextMenuItem(next) { targets.asReversed().forEach { app.player.enqueue(it, next = true) } })
            add(androidx.compose.foundation.ContextMenuItem(queue) { targets.forEach { app.player.enqueue(it, next = false) } })
            val allLiked = targets.all { it.id in app.likedIds }
            add(androidx.compose.foundation.ContextMenuItem(if (allLiked) unlike else like) {
                targets.filter { (it.id in app.likedIds) == allLiked }.forEach { app.toggleLike(it) }
            })
            if (targets.any { !LocalFiles.isLocal(it.id) }) add(androidx.compose.foundation.ContextMenuItem(download) { targets.forEach { app.downloads.download(it) } })
            if (single && LocalFiles.isLocal(song.id)) {
                add(androidx.compose.foundation.ContextMenuItem(editFile) { app.open(Screen.LocalFiles); app.localEditing = song.id })
            }
            if (single && !LocalFiles.isLocal(song.id)) {
                song.artistId?.let { id -> add(androidx.compose.foundation.ContextMenuItem(artist) { app.open(Screen.Artist(id, song.artist, song.thumbnailUrl)) }) }
                song.albumId?.let { id -> add(androidx.compose.foundation.ContextMenuItem(album) { app.open(Screen.Album(id, song.album ?: song.title, song.thumbnailUrl)) }) }
                add(androidx.compose.foundation.ContextMenuItem(copy) {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection("https://music.youtube.com/watch?v=${song.id}"), null)
                })
            }
        }
    }
}

@Composable
fun SongList(app: AppState, songs: List<SongItem>, state: LazyListState = rememberLazyListState(), header: @Composable () -> Unit = {}, extra: @Composable (SongItem) -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom)) {
            item { header() }
            itemsIndexed(songs, key = { i, s -> "${s.id}#$i" }) { i, song ->
                SongRow(app, song, onPlay = { app.player.play(songs, i) }, index = i, songs = songs) { extra(song) }
            }
            item { Gap(h = 24) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
fun CardTile(title: String, subtitle: String?, thumb: String?, circle: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier.width(150.dp).hairEnter(0).hoverLift(source).hoverable(source).clip(RoundedCornerShape(Look.row)).background(hoverBackground(source))
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Cover(thumb, 134.dp, circle = circle)
        Gap(h = 8)
        Txt(title, size = 13)
        subtitle?.let { Txt(it, color = C.Muted, size = 11) }
    }
}

/** Плашка «доступна новая версия» на главной: «Обновить» или «Позже» (после «Позже» остаётся кнопка в настройках). */
@Composable
internal fun UpdateBanner(app: AppState) {
    val info = (app.updater.state as? com.texfi.w0y.data.UpdateState.Available)?.info ?: return
    PixelCard(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        RowCenter {
            Column(Modifier.weight(1f)) {
                Txt(S("d_update_banner", info.label), size = 15, weight = androidx.compose.ui.text.font.FontWeight.Medium)
                Txt(S("update_dialog_hint"), color = C.Muted, size = 12, maxLines = 2, modifier = Modifier.padding(top = 3.dp))
            }
            Gap(w = 14)
            PixelButton(S("update_dialog_later"), onClick = { app.updater.later() }, primary = false)
            Gap(w = 8)
            PixelButton(if (Platform.isWindows) S("update_btn_update") else S("d_set_update_open"), onClick = { app.updater.update() })
        }
    }
}

fun AppState.openCard(card: PlaylistCard) {
    if (card.isAlbum) {
        open(Screen.Album(card.browseId, card.title, card.thumbnailUrl))
    } else {
        open(Screen.Remote(card.browseId, card.title, card.thumbnailUrl))
    }
}

@Composable
fun SearchScreen(app: AppState) {
    ScreenFrame(S("tab_search"), app, canBack = false) {
        val state = rememberLazyListState()
        val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Actions.searchFocusTick) { if (Actions.searchFocusTick > 0) runCatching { searchFocus.requestFocus() } }
        Column(Modifier.fillMaxSize()) {
            RowCenter(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(if (Look.smooth) 50.dp else 6.dp)).background(if (Look.smooth) C.SurfaceHigh.copy(alpha = 0.7f) else C.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(Glyph.Search, C.Muted, 18.dp)
                Gap(w = 10)
                Box(Modifier.weight(1f)) {
                    if (app.searchQuery.isEmpty()) Txt(S("search_placeholder"), color = C.Muted, size = 15)
                    BasicTextField(
                        value = app.searchQuery,
                        onValueChange = { app.searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = C.Text, fontSize = 15.sp),
                        cursorBrush = SolidColor(C.Blue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { app.search(app.searchQuery) }),
                        modifier = Modifier.fillMaxWidth().textFieldFocus().focusRequester(searchFocus).onKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                                app.search(app.searchQuery)
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
                if (app.searchQuery.isNotEmpty()) IconButton(Glyph.Close, onClick = { app.searchQuery = ""; app.searchResult = null }, color = C.Muted, size = 14.dp, box = 28.dp)
            }
            Gap(h = 12)
            val result = app.searchResult
            val suggestions by produceState<List<String>>(emptyList(), app.searchQuery, app.st.searchSuggestions) {
                value = emptyList()
                val q = app.searchQuery.trim()
                if (app.st.searchSuggestions && q.isNotEmpty() && q != app.lastSearched) {
                    kotlinx.coroutines.delay(250)
                    value = runCatching { app.yt.suggestions(q) }.getOrDefault(emptyList())
                }
            }
            val typing = app.searchQuery.isNotBlank() && app.searchQuery.trim() != app.lastSearched
            when {
                typing && suggestions.isNotEmpty() ->
                    Column(Modifier.fillMaxWidth()) {
                        SectionLabel(S("d_search_suggestions"), Modifier.padding(bottom = 6.dp))
                        suggestions.forEach { sug ->
                            Txt(sug, size = 15, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { app.searchQuery = sug; app.search(sug) }.padding(10.dp))
                        }
                    }
                app.searchQuery.isEmpty() && result == null && app.lib.searchHistory.isNotEmpty() && app.st.saveSearchHistory ->
                    Column(Modifier.fillMaxWidth()) {
                        SectionLabel(S("search_recent"), Modifier.padding(bottom = 6.dp))
                        app.lib.searchHistory.forEach { h ->
                            RowCenter(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { app.searchQuery = h; app.search(h) }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Txt(h, size = 15, modifier = Modifier.weight(1f))
                                IconButton(Glyph.Close, onClick = { app.forgetSearch(h) }, color = C.Muted, size = 12.dp, box = 26.dp)
                            }
                        }
                    }
                app.searching -> Empty(S("d_searching"))
                app.searchError != null -> Empty(app.searchError!!)
                result == null -> Empty(S("d_search_enter"))
                else ->
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom)) {
                            // Свои файлы, подходящие под запрос, — сверху: искать их в YouTube незачем.
                            val words = app.lastSearched.lowercase().split(' ').filter { it.isNotBlank() }
                            val mine = if (words.isEmpty()) emptyList() else app.localSongs.filter { f ->
                                val hay = "${f.title} ${f.artist} ${f.album.orEmpty()}".lowercase()
                                words.all { it in hay }
                            }
                            if (mine.isNotEmpty()) {
                                item { SectionLabel(S("lm_card"), Modifier.padding(vertical = 8.dp)) }
                                itemsIndexed(mine) { i, song -> SongRow(app, song, onPlay = { app.player.play(mine, i) }) }
                            }
                            if (result.songs.isNotEmpty()) item { SectionLabel(S("search_filter_songs"), Modifier.padding(vertical = 8.dp)) }
                            itemsIndexed(result.songs) { i, song -> SongRow(app, song, onPlay = { app.player.play(result.songs, i) }) }
                            if (result.albums.isNotEmpty()) {
                                item {
                                    SectionLabel(S("search_filter_albums"), Modifier.padding(top = 16.dp, bottom = 8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(result.albums) { c -> CardTile(c.title, c.subtitle, c.thumbnailUrl) { app.openCard(c) } }
                                    }
                                }
                            }
                            if (result.artists.isNotEmpty()) {
                                item {
                                    SectionLabel(S("search_filter_artists"), Modifier.padding(top = 16.dp, bottom = 8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(result.artists) { a ->
                                            CardTile(a.name, a.subtitle, a.thumbnailUrl, circle = true) { app.open(Screen.Artist(a.browseId, a.name, a.thumbnailUrl)) }
                                        }
                                    }
                                }
                            }
                            item { Gap(h = 24) }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
            }
        }
    }
}

@Composable
private fun DetailHeader(app: AppState, title: String, subtitle: String?, thumb: String?, songs: List<SongItem>, circle: Boolean = false) {
    RowCenter(Modifier.padding(bottom = 16.dp)) {
        Cover(thumb, 120.dp, circle = circle)
        Gap(w = 18)
        Column(Modifier.weight(1f)) {
            Txt(title, size = 22, weight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 2)
            subtitle?.let { Txt(it, color = C.Muted, size = 13) }
            Gap(h = 12)
            RowCenter {
                PixelButton(S("album_play"), onClick = { if (songs.isNotEmpty()) app.player.play(songs, 0) }, enabled = songs.isNotEmpty())
                Gap(w = 10)
                PixelButton(S("album_shuffle"), onClick = { if (songs.isNotEmpty()) app.player.play(songs.shuffled(), 0) }, primary = false, enabled = songs.isNotEmpty())
            }
        }
    }
}

@Composable
fun AlbumScreen(app: AppState, s: Screen.Album) {
    val page by produceState<com.texfi.w0y.data.AlbumPage?>(null, s.browseId) {
        value = runCatching { app.yt.album(s.browseId) }.getOrNull()
    }
    ScreenFrame(s.title, app, canBack = true) {
        val songs = page?.songs.orEmpty()
        if (page == null) Empty(S("common_loading")) else SongList(app, songs, header = { DetailHeader(app, s.title, page?.subtitle, page?.thumbnailUrl ?: s.thumb, songs) })
    }
}

@Composable
fun ArtistScreen(app: AppState, s: Screen.Artist) {
    val page by produceState<com.texfi.w0y.data.ArtistPage?>(null, s.browseId) {
        value = runCatching { app.yt.artist(s.browseId) }.getOrNull()
    }
    var allSongs by remember(s.browseId) { mutableStateOf<List<SongItem>?>(null) }
    var loadingAll by remember(s.browseId) { mutableStateOf(false) }
    ScreenFrame(s.name, app, canBack = true) {
        val pg = page
        if (pg == null) {
            Empty(S("common_loading"))
            return@ScreenFrame
        }
        val songs = allSongs ?: pg.songs
        val state = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom)) {
                item {
                    RowCenter(Modifier.padding(bottom = 20.dp)) {
                        Cover(pg.thumbnailUrl ?: s.thumb, 160.dp, circle = true)
                        Gap(w = 22)
                        Column(Modifier.weight(1f)) {
                            Txt(pg.name.ifBlank { s.name }, size = 28, weight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 2)
                            pg.subtitle?.let { Txt(it, color = C.Muted, size = 13) }
                            Gap(h = 14)
                            RowCenter {
                                PixelButton(S("album_play"), onClick = { if (songs.isNotEmpty()) app.player.play(songs, 0) }, enabled = songs.isNotEmpty())
                                Gap(w = 10)
                                PixelButton(S("album_shuffle"), onClick = { if (songs.isNotEmpty()) app.player.play(songs.shuffled(), 0) }, primary = false, enabled = songs.isNotEmpty())
                                if (pg.allSongsBrowseId != null && allSongs == null) {
                                    Gap(w = 10)
                                    PixelButton(if (loadingAll) S("d_loading") else S("artist_all_songs"), onClick = {
                                        if (!loadingAll) {
                                            loadingAll = true
                                            app.scope.launch {
                                                allSongs = runCatching { app.yt.playlistSongs(pg.allSongsBrowseId!!, maxPages = 3, params = pg.allSongsParams) }.getOrNull()?.takeIf { it.isNotEmpty() }
                                                loadingAll = false
                                            }
                                        }
                                    }, primary = false, enabled = !loadingAll)
                                }
                            }
                        }
                    }
                    if (songs.isNotEmpty()) SectionLabel(if (allSongs != null) S("artist_all_songs") else S("d_popular"), Modifier.padding(bottom = 6.dp))
                }
                itemsIndexed(songs, key = { i, sg -> "${sg.id}#$i" }) { i, song ->
                    SongRow(app, song, onPlay = { app.player.play(songs, i) })
                }
                if (pg.releases.isNotEmpty()) {
                    item {
                        SectionLabel(S("artist_releases"), Modifier.padding(top = 20.dp, bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(pg.releases) { c -> CardTile(c.title, c.subtitle, c.thumbnailUrl) { app.openCard(c) } }
                        }
                    }
                }
                if (pg.similar.isNotEmpty()) {
                    item {
                        SectionLabel(S("artist_similar"), Modifier.padding(top = 20.dp, bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(pg.similar) { a -> CardTile(a.name, a.subtitle, a.thumbnailUrl, circle = true) { app.open(Screen.Artist(a.browseId, a.name, a.thumbnailUrl)) } }
                        }
                    }
                }
                item { Gap(h = 24) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
fun RemoteScreen(app: AppState, s: Screen.Remote) {
    val songs by produceState<List<SongItem>?>(null, s.browseId) {
        value = runCatching { app.yt.playlistSongs(s.browseId) }.getOrNull() ?: emptyList()
    }
    ScreenFrame(s.title, app, canBack = true) {
        val list = songs
        if (list == null) Empty(S("common_loading")) else if (list.isEmpty()) Empty(S("d_playlist_empty_or_failed")) else SongList(app, list, header = { DetailHeader(app, s.title, "${list.size} треков", s.thumb, list) })
    }
}

@Composable
fun LibraryScreen(app: AppState) {
    ScreenFrame(S("tab_library"), app, canBack = false) {
        val state = rememberLazyListState()
        var newName by remember { mutableStateOf<String?>(null) }
        var cookie by remember { mutableStateOf("") }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    PixelCard(Modifier.fillMaxWidth()) {
                        Column {
                            SectionLabel(S("library_account"))
                            Gap(h = 10)
                            if (app.signedIn) {
                                RowCenter {
                                    Cover(app.lib.accountAvatar, 40.dp, circle = true)
                                    Gap(w = 12)
                                    Txt(app.lib.accountName ?: S("library_signed_in"), size = 16, modifier = Modifier.weight(1f))
                                    PixelButton(if (app.sync.running) S("library_syncing") else S("library_sync"), onClick = { app.syncAccount(force = true) }, enabled = !app.sync.running)
                                    Gap(w = 10)
                                    PixelButton(S("library_sign_out"), onClick = app::signOut, primary = false)
                                }
                            } else {
                                Txt(
                                    S("d_login_google_note"),
                                    color = C.Muted, size = 12, maxLines = 3,
                                )
                                Gap(h = 10)
                                PixelButton(if (app.loggingIn) S("d_waiting_login") else S("d_sign_in_google"), onClick = app::signInWithGoogle, enabled = !app.loggingIn)
                                Gap(h = 14)
                                Txt(S("d_login_cookie_hint"), color = C.Muted, size = 12, maxLines = 3)
                                Gap(h = 8)
                                RowCenter {
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(C.SurfaceHigh).padding(10.dp)) {
                                        if (cookie.isEmpty()) Txt("Cookie", color = C.Muted, size = 13)
                                        BasicTextField(cookie, { cookie = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 13.sp), cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth().textFieldFocus())
                                    }
                                    Gap(w = 10)
                                    PixelButton(S("login_submit"), onClick = { app.signIn(cookie); cookie = "" })
                                }
                            }
                            app.sync.error?.let { Txt(it, color = C.Sand, size = 12, modifier = Modifier.padding(top = 8.dp), maxLines = 3) }
                            app.accountStatus?.let { Txt(it, color = C.Sand, size = 12, modifier = Modifier.padding(top = 8.dp), maxLines = 3) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Shortcut(S("library_likes"), "${app.lib.liked.size}", Modifier.weight(1f), 1, Glyph.Heart) { app.open(Screen.Liked) }
                        Shortcut(S("library_history"), "${app.lib.history.size}", Modifier.weight(1f), 2, Glyph.Timer) { app.open(Screen.History) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Shortcut(S("library_downloaded"), "${app.lib.downloads.size}", Modifier.weight(1f), 3, Glyph.Download) { app.open(Screen.Downloaded) }
                        Shortcut(S("lm_card"), "${app.lib.localFiles.size}", Modifier.weight(1f), 6, Glyph.Note) { app.open(Screen.LocalFiles) }
                        Shortcut(S("library_stats"), "${app.lib.playLog.size}", Modifier.weight(1f), 4, Glyph.Stats) { app.open(Screen.Stats) }
                        Shortcut(S("player_queue"), "${app.player.queue.size}", Modifier.weight(1f), 5, Glyph.Queue) { app.open(Screen.Player) }
                    }
                }
                item {
                    RowCenter {
                        SectionLabel(S("library_playlists"), Modifier.weight(1f))
                        IconButton(Glyph.Plus, onClick = { newName = "" }, color = C.Blue)
                    }
                }
                newName?.let { value ->
                    item {
                        PixelCard(Modifier.fillMaxWidth()) {
                            RowCenter {
                                Box(Modifier.weight(1f)) {
                                    if (value.isEmpty()) Txt(S("library_playlist_name"), color = C.Muted, size = 14)
                                    BasicTextField(value, { newName = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 14.sp), cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth().textFieldFocus())
                                }
                                Gap(w = 10)
                                PixelButton(S("library_create"), onClick = { app.createPlaylist(value); newName = null })
                                Gap(w = 8)
                                PixelButton(S("library_cancel"), onClick = { newName = null }, primary = false)
                            }
                        }
                    }
                }
                if (app.lib.playlists.isEmpty() && newName == null) item { Empty(S("library_no_playlists")) }
                items(app.lib.playlists, key = { it.id }) { pl ->
                    val where =
                        when {
                            pl.remoteId != null && !pl.editable -> S("d_pl_readonly")
                            pl.remoteId != null -> S("d_pl_tracks_yt", pl.songs.size)
                            else -> S("d_pl_tracks_local", pl.songs.size)
                        }
                    PlaylistLine(pl, where) { app.open(Screen.Local(pl.id)) }
                }
                if (app.accountAlbums.isNotEmpty()) {
                    item { SectionLabel(S("library_albums"), Modifier.padding(top = 8.dp)) }
                    items(app.accountAlbums, key = { it.browseId }) { c ->
                        ListLine(c.title, c.subtitle ?: S("card_album"), c.thumbnailUrl) { app.openCard(c) }
                    }
                }
                item { Gap(h = 24) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun Shortcut(label: String, value: String, modifier: Modifier, index: Int, glyph: Glyph, onClick: () -> Unit) {
    PixelCard(modifier.hairEnter(index).hoverLift(remember { MutableInteractionSource() }, 0f).clickable(onClick = onClick)) {
        if (Look.hair) {
            // Как на телефоне: номер и значок сверху, крупное число и подпись снизу.
            Column(Modifier.fillMaxWidth()) {
                RowCenter(Modifier.fillMaxWidth()) {
                    Txt("%02d".format(index), color = C.Muted.copy(alpha = 0.7f), size = 11, pixel = true)
                    Box(Modifier.weight(1f))
                    Icon(glyph, C.Muted, 18.dp)
                }
                Gap(h = 14)
                Txt(value, size = 30, pixel = true)
                Gap(h = 4)
                SectionLabel(label)
            }
        } else {
            Column {
                SectionLabel(label)
                Gap(h = 10)
                Txt(value, size = 22, weight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
    }
}

/** Строка плейлиста в библиотеке: плитка с обложкой или значком, цвет рамкой. */
@Composable
private fun PlaylistLine(pl: StoredPlaylist, subtitle: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    RowCenter(
        Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(Look.row)).background(hoverBackground(source))
            .clickable(interactionSource = source, indication = null, onClick = onClick).padding(8.dp),
    ) {
        PlaylistTile(pl, 48.dp)
        Gap(w = 12)
        Column(Modifier.weight(1f)) {
            Txt(pl.name, size = 15)
            Txt(subtitle, color = C.Muted, size = 12)
        }
    }
}

@Composable
private fun ListLine(title: String, subtitle: String, thumb: String?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    RowCenter(
        Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(Look.row)).background(hoverBackground(source))
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Cover(thumb, 48.dp)
        Gap(w = 12)
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15)
            Txt(subtitle, color = C.Muted, size = 12)
        }
    }
}

@Composable
fun LikedScreen(app: AppState) {
    ScreenFrame(S("library_likes"), app, canBack = true) {
        val songs = app.lib.liked.map { it.toItem() }
        if (songs.isEmpty()) Empty(S("library_empty_text")) else SongList(app, songs)
    }
}

@Composable
fun HistoryScreen(app: AppState) {
    ScreenFrame(S("library_history"), app, canBack = true) {
        val songs = app.lib.history.map { it.toItem() }
        if (songs.isEmpty()) Empty(S("library_empty_text")) else SongList(app, songs)
    }
}

/** «3:47» или «1:02:10» → секунды; не разобралось — 0. */
private fun parseDuration(text: String?): Int {
    val parts = text?.split(':')?.mapNotNull { it.trim().toIntOrNull() } ?: return 0
    return parts.fold(0) { acc, n -> acc * 60 + n }
}

/**
 * Плейлист: шапка с обложкой, именем, описанием и счётчиками, «Играть» и «Вперемешку», кнопка настройки.
 * Настройка (обложка, имя, описание, цвет, значок, удаление) открывается тут же, как на телефоне.
 */
@Composable
fun LocalScreen(app: AppState, s: Screen.Local) {
    val pl = app.playlist(s.id)
    // Состав сверяется с аккаунтом при открытии: правили его там — увидим сразу.
    LaunchedEffect(s.id) { app.sync.refreshPlaylist(s.id) }
    ScreenFrame(S("playlist_title"), app, canBack = true) {
        if (pl == null) return@ScreenFrame
        androidx.compose.animation.AnimatedContent(
            targetState = app.playlistEditing,
            transitionSpec = { androidx.compose.animation.fadeIn(tween(Motion.ms(240))) togetherWith androidx.compose.animation.fadeOut(tween(Motion.ms(140))) },
            label = "playlist-editor",
        ) { editing ->
            if (editing) {
                PlaylistEditor(
                    pl,
                    onSave = { name, note, accent, glyph, picked, clear ->
                        app.savePlaylistLook(pl.id, name, note, accent, glyph, picked, clear)
                        app.playlistEditing = false
                    },
                    onDelete = { app.deletePlaylist(pl.id) },
                    onClose = { app.playlistEditing = false },
                )
            } else {
                PlaylistView(app, pl)
            }
        }
    }
}

@Composable
private fun PlaylistView(app: AppState, pl: StoredPlaylist) {
    val songs = pl.songs.map { it.toItem() }
    val seconds = pl.songs.sumOf { parseDuration(it.duration) }
    val where =
        when {
            pl.remoteId != null && !pl.editable -> S("d_pl_readonly")
            pl.remoteId != null -> S("d_pl_in_youtube")
            else -> S("d_pl_only_here")
        }
    val meta = buildList {
        add(S("d_pl_tracks", songs.size))
        if (seconds >= 60) add(S("d_pl_minutes", (seconds + 30) / 60))
        add(where)
    }.joinToString(" · ")
    val accent = pl.accent?.let { androidx.compose.ui.graphics.Color(it) }
    Column(Modifier.fillMaxSize()) {
        SongList(app, songs, header = {
            Column {
                RowCenter(Modifier.padding(bottom = 18.dp)) {
                    PlaylistTile(pl, 168.dp, Modifier.hairEnter(0))
                    Gap(w = 24)
                    Column(Modifier.weight(1f)) {
                        SectionLabel(meta)
                        Gap(h = 8)
                        Txt(pl.name, size = 28, pixel = true, maxLines = 2)
                        if (accent != null) Box(Modifier.padding(top = 10.dp).width(40.dp).height(3.dp).background(accent))
                        pl.note?.let { Txt(it, color = C.Muted, size = 13, maxLines = 3, modifier = Modifier.padding(top = 10.dp)) }
                        if (!pl.editable) Txt(S("playlist_readonly"), color = C.Muted, size = 12, maxLines = 3, modifier = Modifier.padding(top = 8.dp))
                        Gap(h = 16)
                        RowCenter {
                            PixelButton(S("album_play"), onClick = { if (songs.isNotEmpty()) app.player.play(songs, 0) }, enabled = songs.isNotEmpty())
                            Gap(w = 10)
                            PixelButton(S("album_shuffle"), onClick = { if (songs.isNotEmpty()) app.player.play(songs.shuffled(), 0) }, primary = false, enabled = songs.isNotEmpty())
                            Gap(w = 8)
                            IconButton(Glyph.Sliders, onClick = { app.playlistEditing = true }, color = C.Muted, size = 20.dp, box = 40.dp)
                        }
                    }
                }
                if (songs.isEmpty()) Empty(S("playlist_empty"))
            }
        }, extra = { song ->
            if (pl.editable) IconButton(Glyph.Trash, onClick = { app.removeFromPlaylist(pl.id, song.id) }, color = C.Muted, size = 16.dp)
        })
    }
}

/** Стрелка «скачать»: ждёт, показывает проценты, превращается в галочку. */
@Composable
fun DownloadButton(app: AppState, song: SongItem) {
    // Свой файл уже на диске: скачивать нечего.
    if (LocalFiles.isLocal(song.id)) {
        Box(Modifier.width(34.dp).height(34.dp))
        return
    }
    val d = app.downloads
    val done = app.downloadedPath(song.id) != null
    val p = d.progress[song.id]
    when {
        done -> Box(Modifier.width(34.dp).height(34.dp), contentAlignment = Alignment.Center) { Icon(Glyph.Check, C.Sand, 16.dp) }
        p != null -> Box(Modifier.width(34.dp).height(34.dp), contentAlignment = Alignment.Center) {
            Txt(if (p < 0) "…" else "${(p * 100).toInt()}%", color = C.Blue, size = 10)
        }
        else -> IconButton(Glyph.Download, onClick = { d.download(song) }, color = if (song.id in d.failed) C.Danger else C.Muted, size = 16.dp)
    }
}

@Composable
fun DownloadedScreen(app: AppState) {
    val songs = app.lib.downloads.values.map { it.song.toItem() }
    ScreenFrame(S("library_downloaded"), app, canBack = true) {
        Column(Modifier.fillMaxSize()) {
            Txt(S("d_downloads_folder", app.downloads.folder.absolutePath), color = C.Muted, size = 12, maxLines = 3)
            Gap(h = 10)
            app.downloads.notice?.let { Txt(it, color = C.Sand, size = 12, maxLines = 2); Gap(h = 6) }
            if (songs.isEmpty()) {
                Empty(S("downloads_empty_text"))
            } else {
                SongList(app, songs, extra = { song ->
                    IconButton(Glyph.Trash, onClick = { app.downloads.remove(song.id) }, color = C.Muted, size = 16.dp)
                })
            }
        }
    }
}

/** «Быстрый набор»: закреплённое и самое играемое; на плитке — кнопки «закрепить» и «убрать». */
@Composable
fun DialSection(app: AppState, header: Boolean = true) {
    val entries = app.dialEntries()
    if (header) {
        SectionLabel(S("home_dial"), Modifier.padding(top = 8.dp, bottom = 4.dp))
        Txt(S("home_dial_hint"), color = C.Muted, size = 11, modifier = Modifier.padding(bottom = 8.dp))
    }
    if (entries.isEmpty()) {
        Txt(S("home_dial_empty"), color = C.Muted, size = 13, maxLines = 3, modifier = Modifier.padding(bottom = 14.dp))
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(entries, key = { it.key }) { e -> DialTile(app, e, entries) }
    }
    Gap(h = 14)
}

@Composable
private fun DialTile(app: AppState, e: DialEntry, all: List<DialEntry>) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier.width(150.dp).hairEnter(0).hoverLift(source).hoverable(source).clip(RoundedCornerShape(Look.row)).background(if (hovered) C.Surface else Color.Transparent)
            .clickable {
                val song = e.song
                if (song != null) app.player.play(all.mapNotNull { it.song }, all.mapNotNull { it.song }.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                else e.artistId?.let { app.open(Screen.Artist(it, e.title, e.thumb)) }
            }.padding(8.dp),
    ) {
        Column {
            Cover(e.thumb, 134.dp, circle = e.artistId != null)
            Gap(h = 8)
            Txt(e.title, size = 13)
            e.subtitle?.let { Txt(it, color = C.Muted, size = 11) }
        }
        if (hovered || e.pinned) {
            RowCenter(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(Glyph.Pin, onClick = { app.togglePin(e.key) }, color = if (e.pinned) C.Sand else C.Text, size = 14.dp, box = 28.dp)
                if (hovered) IconButton(Glyph.Close, onClick = { app.hideFromDial(e.key) }, color = C.Text, size = 12.dp, box = 28.dp)
            }
        }
    }
}
