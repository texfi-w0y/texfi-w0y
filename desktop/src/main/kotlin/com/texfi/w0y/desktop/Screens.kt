package com.texfi.w0y.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
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
            if (Look.smooth) Txt(title.replaceFirstChar(Char::uppercase), size = 28, weight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
            else Txt(title.uppercase(), pixel = true, size = 18, modifier = Modifier.weight(1f))
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
fun SongRow(app: AppState, song: SongItem, onPlay: () -> Unit, extra: @Composable () -> Unit = {}) {
    val source = remember { MutableInteractionSource() }
    val bg = hoverBackground(source)
    val liked = song.id in app.likedIds
    val current = app.player.current?.id == song.id
    RowCenter(
        Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(Look.row)).background(bg)
            .clickable(onClick = onPlay).padding(horizontal = 8.dp, vertical = if (app.st.compactRows) 2.dp else 6.dp),
    ) {
        Cover(song.thumbnailUrl, if (app.st.compactRows) 32.dp else 44.dp)
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

@Composable
fun SongList(app: AppState, songs: List<SongItem>, state: LazyListState = rememberLazyListState(), header: @Composable () -> Unit = {}, extra: @Composable (SongItem) -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom)) {
            item { header() }
            itemsIndexed(songs, key = { i, s -> "${s.id}#$i" }) { i, song ->
                SongRow(app, song, onPlay = { app.player.play(songs, i) }) { extra(song) }
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
        Modifier.width(150.dp).hoverable(source).clip(RoundedCornerShape(Look.row)).background(hoverBackground(source))
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Cover(thumb, 134.dp, circle = circle)
        Gap(h = 8)
        Txt(title, size = 13)
        subtitle?.let { Txt(it, color = C.Muted, size = 11) }
    }
}

@Composable
fun HomeScreen(app: AppState) {
    ScreenFrame(S("tab_home"), app, canBack = false, actions = {
        PixelButton(if (app.homeLoading) S("d_loading") else S("library_sync"), onClick = app::loadHome, primary = false, enabled = !app.homeLoading)
    }) {
        val state = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = state, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom)) {
                item { DialSection(app) }
                if (!app.st.showRecommendations) {
                    item { Empty(S("home_empty")) }
                } else if (app.shelves.isEmpty()) {
                    item { Empty(app.homeError ?: if (app.homeLoading) S("common_loading") else S("home_recommendations_failed")) }
                } else {
                    items(app.shelves) { shelf ->
                        if (shelf.songs.isEmpty() && shelf.cards.isEmpty()) return@items
                        SectionLabel(shelf.title, Modifier.padding(top = 8.dp, bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(shelf.songs) { i, song ->
                                CardTile(song.title, song.artist, song.thumbnailUrl) { app.player.play(shelf.songs, i) }
                            }
                            items(shelf.cards) { card -> CardTile(card.title, card.subtitle, card.thumbnailUrl) { app.openCard(card) } }
                        }
                        Gap(h = 14)
                    }
                }
                item { Gap(h = 24) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
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
        Column(Modifier.fillMaxSize()) {
            RowCenter(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(C.Surface).padding(horizontal = 12.dp, vertical = 10.dp),
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
                        modifier = Modifier.fillMaxWidth().onKeyEvent {
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
                                        BasicTextField(cookie, { cookie = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 13.sp), cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth())
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
                        Shortcut(S("library_likes"), "${app.lib.liked.size}", Modifier.weight(1f)) { app.open(Screen.Liked) }
                        Shortcut(S("library_history"), "${app.lib.history.size}", Modifier.weight(1f)) { app.open(Screen.History) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Shortcut(S("library_downloaded"), "${app.lib.downloads.size}", Modifier.weight(1f)) { app.open(Screen.Downloaded) }
                        Shortcut(S("library_stats"), "${app.lib.playLog.size}", Modifier.weight(1f)) { app.open(Screen.Stats) }
                        Shortcut(S("player_queue"), "${app.player.queue.size}", Modifier.weight(1f)) { app.open(Screen.Player) }
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
                                    BasicTextField(value, { newName = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 14.sp), cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth())
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
                    ListLine(pl.name, where, pl.cover ?: pl.songs.firstOrNull()?.thumb) { app.open(Screen.Local(pl.id)) }
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
private fun Shortcut(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    PixelCard(modifier.clickable(onClick = onClick)) {
        Column {
            SectionLabel(label)
            Gap(h = 10)
            Txt(value, size = 22, weight = androidx.compose.ui.text.font.FontWeight.Medium)
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

@Composable
fun LocalScreen(app: AppState, s: Screen.Local) {
    val pl = app.playlist(s.id)
    var renaming by remember { mutableStateOf<String?>(null) }
    // Состав сверяется с аккаунтом при открытии: правили его там — увидим сразу.
    LaunchedEffect(s.id) { app.sync.refreshPlaylist(s.id) }
    ScreenFrame(pl?.name ?: S("playlist_title"), app, canBack = true, actions = {
        if (pl != null) {
            if (pl.editable) {
                PixelButton(S("playlist_rename"), onClick = { renaming = pl.name }, primary = false)
                Gap(w = 8)
            }
            PixelButton(if (pl.editable) S("d_delete") else S("d_remove_from_library"), onClick = { app.deletePlaylist(s.id) }, primary = false)
        }
    }) {
        val songs = pl?.songs?.map { it.toItem() }.orEmpty()
        Column(Modifier.fillMaxSize()) {
            renaming?.let { value ->
                PixelCard(Modifier.fillMaxWidth()) {
                    RowCenter {
                        Box(Modifier.weight(1f)) {
                            BasicTextField(value, { renaming = it }, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 14.sp), cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth())
                        }
                        Gap(w = 10)
                        PixelButton(S("playlist_rename_save"), onClick = { app.renamePlaylist(s.id, value); renaming = null })
                        Gap(w = 8)
                        PixelButton(S("library_cancel"), onClick = { renaming = null }, primary = false)
                    }
                }
                Gap(h = 10)
            }
            if (pl != null && !pl.editable) {
                Txt(S("playlist_readonly"), color = C.Muted, size = 12, maxLines = 3)
                Gap(h = 8)
            }
            if (pl?.remoteId != null) {
                Txt(if (pl.editable) "Отражён в аккаунте YouTube: правки уходят туда и приходят оттуда." else S("d_pl_from_account"), color = C.Muted, size = 12)
                Gap(h = 8)
            }
            if (songs.isEmpty()) {
                Empty(S("playlist_empty"))
            } else {
                SongList(app, songs, extra = { song ->
                    if (pl?.editable != false) IconButton(Glyph.Trash, onClick = { app.removeFromPlaylist(s.id, song.id) }, color = C.Muted, size = 16.dp)
                })
            }
        }
    }
}

/** Стрелка «скачать»: ждёт, показывает проценты, превращается в галочку. */
@Composable
fun DownloadButton(app: AppState, song: SongItem) {
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
fun DialSection(app: AppState) {
    val entries = app.dialEntries()
    SectionLabel(S("home_dial"), Modifier.padding(top = 8.dp, bottom = 4.dp))
    Txt(S("home_dial_hint"), color = C.Muted, size = 11, modifier = Modifier.padding(bottom = 8.dp))
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
        Modifier.width(150.dp).hoverable(source).clip(RoundedCornerShape(Look.row)).background(if (hovered) C.Surface else Color.Transparent)
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
