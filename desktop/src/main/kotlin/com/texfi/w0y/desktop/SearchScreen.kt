package com.texfi.w0y.desktop

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.zIndex
import com.texfi.w0y.data.ArtistCard
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.desktop.i18n.S
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Раздел выдачи, как на телефоне: «Все» — один запрос без фильтра, сразу артисты, треки и альбомы; остальные
 * разделы — отдельные запросы YouTube Music.
 */
enum class SearchFilter(val key: String) {
    ALL("search_filter_all"),
    SONGS("search_filter_songs"),
    VIDEOS("search_filter_videos"),
    ALBUMS("search_filter_albums"),
    ARTISTS("search_filter_artists"),
}

/** Выдача одного запроса. [videos] — строки рисуются с широким кадром; [corrected] — запрос исправлен (раскладка). */
class SearchResults(
    val songs: List<SongItem> = emptyList(),
    val albums: List<PlaylistCard> = emptyList(),
    val artists: List<ArtistCard> = emptyList(),
    val videos: Boolean = false,
    val corrected: String? = null,
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

sealed interface SearchState {
    data object Idle : SearchState

    data object Loading : SearchState

    data class Done(val results: SearchResults) : SearchState

    /** Запрос прошёл, но ничего не нашёл: это не ошибка. */
    data object Empty : SearchState

    data class Failed(val message: String) : SearchState
}

/**
 * Умный поиск ПК, те же правила, что у телефона: выдача по паузе в наборе (а Enter, подсказка и запись из истории —
 * сразу), готовые ответы по паре «запрос + раздел» не запрашиваются повторно, своё из библиотеки стоит выше чужого.
 * Сверх телефона: запрос, набранный не в той раскладке («руддщ» вместо «hello»), если по нему пусто, ищется в
 * исправленном виде.
 */
class SearchModel(private val app: AppState) {
    var query by mutableStateOf("")
    var filter by mutableStateOf(SearchFilter.ALL)
    var state by mutableStateOf<SearchState>(SearchState.Idle)
        private set

    /** Последний запрос, ушедший в выдачу: пока он совпадает с полем, подсказки не нужны. */
    var searched by mutableStateOf("")
        private set

    /** Счётчик ручных повторов: тот же запрос обходит кэш и ключ эффекта. */
    var retryTick by mutableIntStateOf(0)
        private set

    /** Явная отправка идёт мимо паузы в наборе. */
    var immediate = false

    private val cache = object : LinkedHashMap<String, SearchResults>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SearchResults>?) = size > CACHE_SIZE
    }

    private fun key(q: String, f: SearchFilter) = "${f.name}|${q.trim().lowercase()}"

    /** Для проверок без сети: готовый ответ на пару «запрос + раздел». */
    internal fun seed(q: String, f: SearchFilter, results: SearchResults) {
        cache[key(q, f)] = results
    }

    /** Искать немедленно: Enter, выбор подсказки или записи из истории. */
    fun submit(value: String = query) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        query = clean
        immediate = true
        app.rememberSearch(clean)
        retryTick++
    }

    fun retry() {
        cache.remove(key(query, filter))
        immediate = true
        retryTick++
    }

    /** «Искать как набрано»: исправление раскладки для этого запроса больше не применяется. */
    fun searchOriginal() {
        noFixFor = query.trim()
        cache.remove(key(query, filter))
        immediate = true
        retryTick++
    }

    private var noFixFor: String? = null

    fun clear() {
        query = ""
        searched = ""
        state = SearchState.Idle
    }

    /** Один поиск до конца; отмена пропускается дальше, любая другая беда превращается в [SearchState.Failed]. */
    suspend fun run() {
        val q = query.trim()
        if (q.isEmpty()) {
            state = SearchState.Idle
            searched = ""
            return
        }
        val f = filter
        searched = q
        cache[key(q, f)]?.let {
            state = SearchState.Done(it)
            return
        }
        state = SearchState.Loading
        state = try {
            var results = fetch(q, f)
            if (results.isEmpty) {
                // Пусто: возможно, раскладка. Исправленный вариант ищем сами и честно говорим об этом.
                val fixed = KeyboardLayout.fix(q)
                if (fixed != null && fixed != q && noFixFor != q) {
                    val second = fetch(fixed, f)
                    if (!second.isEmpty) results = SearchResults(second.songs, second.albums, second.artists, second.videos, corrected = fixed)
                }
            }
            if (results.isEmpty) {
                SearchState.Empty
            } else {
                cache[key(q, f)] = results
                SearchState.Done(results)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SearchState.Failed(explain(e))
        }
    }

    private suspend fun fetch(q: String, f: SearchFilter): SearchResults {
        val yt = app.yt
        return when (f) {
            SearchFilter.ALL -> yt.searchEverything(q).let { SearchResults(app.visible(it.songs), it.albums, it.artists.sortedByDescending { a -> artistMatch(a.name, q) }) }
            SearchFilter.SONGS -> SearchResults(songs = app.visible(yt.songsOnly(q)))
            SearchFilter.VIDEOS -> SearchResults(songs = app.visible(yt.searchVideos(q)), videos = true)
            SearchFilter.ALBUMS -> SearchResults(albums = yt.searchAlbums(q))
            SearchFilter.ARTISTS -> SearchResults(artists = yt.searchArtists(q))
        }
    }

    private fun explain(e: Throwable): String =
        when {
            e is java.net.UnknownHostException -> S("search_error_dns")
            e is java.net.SocketTimeoutException || e is kotlinx.coroutines.TimeoutCancellationException -> S("search_error_timeout")
            e is java.net.ConnectException || e is java.io.IOException -> S("search_error_offline")
            else -> S("search_error_unknown")
        }

    private companion object {
        const val CACHE_SIZE = 40
    }
}

/** Исправление раскладки: русские буквы, набранные на английской, и наоборот. */
object KeyboardLayout {
    private const val EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,./`"
    private const val RU = "йцукенгшщзхъфывапролджэячсмитьбю.ё"
    private val toRu = EN.zip(RU).toMap()
    private val toEn = RU.zip(EN).toMap()

    /** Запрос в другой раскладке или null, если исправлять нечего (смешанные алфавиты, цифры вместо букв). */
    fun fix(q: String): String? {
        val letters = q.filter { it.isLetter() }
        if (letters.length < 3) return null
        val latin = letters.all { it.lowercaseChar() in 'a'..'z' }
        val cyrillic = letters.all { it.lowercaseChar() in 'а'..'я' || it.lowercaseChar() == 'ё' }
        val map = when {
            latin -> toRu
            cyrillic -> toEn
            else -> return null
        }
        return q.map { c ->
            val m = map[c.lowercaseChar()] ?: return@map c
            if (c.isUpperCase()) m.uppercaseChar() else m
        }.joinToString("")
    }
}

/** Нормализация для сравнения своих треков: регистр, «ё»/«е», без знаков препинания. */
private fun norm(s: String): String = s.lowercase().replace('ё', 'е').map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }.joinToString(" ")

/**
 * То, что уже есть в библиотеке и подходит под запрос: лайки, история, свои файлы, составы плейлистов,
 * скачанное. Каждое слово запроса должно встретиться в названии, исполнителе или альбоме; выше — совпадения с
 * начала названия, лайкнутое и свои файлы.
 */
fun localMatches(app: AppState, query: String, limit: Int = 8): List<SongItem> {
    val words = norm(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val all = LinkedHashMap<String, StoredSong>()
    val lib = app.lib
    (lib.localFiles + lib.liked + lib.history + lib.playlists.flatMap { it.songs } + lib.downloads.values.map { it.song } + lib.playLog.map { it.song }).forEach { all.putIfAbsent(it.id, it) }
    val liked = lib.liked.mapTo(HashSet()) { it.id }
    val whole = norm(query)
    return all.values.mapNotNull { s ->
        val title = norm(s.title)
        val hay = "$title ${norm(s.artist)} ${norm(s.album.orEmpty())}"
        if (!words.all { it in hay }) return@mapNotNull null
        var score = 0
        if (title.startsWith(whole)) score += 4 else if (whole in title) score += 2
        if (s.id in liked) score += 2
        if (LocalFiles.isLocal(s.id)) score += 1
        s to score
    }.sortedWith(compareByDescending<Pair<StoredSong, Int>> { it.second }.thenBy { it.first.title.lowercase() }).take(limit).map { it.first.toItem() }
}

/** Насколько имя артиста совпадает с запросом: точное выше вхождения. Артисты в смешанной выдаче идут не по порядку важности. */
private fun artistMatch(name: String, query: String): Int {
    val n = norm(name)
    val q = norm(query)
    return when {
        q.isEmpty() -> 0
        n == q -> 3
        n.startsWith(q) -> 2
        q in n || n in q -> 1
        else -> 0
    }
}

/** Свои плейлисты, в названии которых есть запрос. */
private fun playlistMatches(app: AppState, query: String): List<StoredPlaylist> {
    val q = norm(query)
    if (q.isEmpty()) return emptyList()
    return app.lib.playlists.filter { q in norm(it.name) }.take(3)
}

@Composable
fun SearchScreen(app: AppState) {
    val model = app.searchModel
    ScreenFrame(S("tab_search"), app, canBack = false) {
        val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Actions.searchFocusTick) { if (Actions.searchFocusTick > 0) runCatching { searchFocus.requestFocus() } }
        // Выдача: пауза в наборе, но Enter и выбор подсказки — без неё; смена раздела тоже сразу.
        LaunchedEffect(model.query, model.filter, model.retryTick) {
            if (model.query.isNotBlank() && !model.immediate) delay(SEARCH_DEBOUNCE_MS)
            model.immediate = false
            model.run()
        }
        var dismissed by remember { mutableStateOf(false) }
        LaunchedEffect(model.query) { dismissed = false }
        val suggestions by produceState<List<String>>(emptyList(), model.query, app.st.searchSuggestions) {
            value = emptyList()
            val q = model.query.trim()
            if (app.st.searchSuggestions && q.length >= 2) {
                delay(SUGGEST_DEBOUNCE_MS)
                value = runCatching { app.yt.suggestions(q) }.getOrDefault(emptyList())
            }
        }
        val showSuggestions = !dismissed && suggestions.isNotEmpty() && model.query.trim() != model.searched
        val local by remember { derivedStateOf { localMatches(app, model.query) } }
        val localPlaylists by remember { derivedStateOf { playlistMatches(app, model.query) } }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                SearchField(app, model, searchFocus, onSubmit = { dismissed = true; model.submit() }, onEscape = {
                    if (showSuggestions) dismissed = true else model.clear()
                })
                Gap(h = 12)
                FilterTabs(model)
                Gap(h = 14)
                SearchBody(app, model, local, localPlaylists)
            }
            if (showSuggestions) {
                // Подсказки лежат поверх выдачи, а не сдвигают её: список, прыгающий на каждую букву, читать нельзя.
                Box(Modifier.fillMaxSize().zIndex(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismissed = true })
                Suggestions(suggestions.take(8), Modifier.zIndex(2f).padding(top = 52.dp)) { s ->
                    dismissed = true
                    model.submit(s)
                }
            }
        }
    }
}

@Composable
private fun SearchField(app: AppState, model: SearchModel, focus: androidx.compose.ui.focus.FocusRequester, onSubmit: () -> Unit, onEscape: () -> Unit) {
    val shape = RoundedCornerShape(if (Look.smooth) 50.dp else 6.dp)
    RowCenter(
        Modifier.fillMaxWidth().clip(shape).background(if (Look.smooth) C.SurfaceHigh.copy(alpha = 0.7f) else C.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(Glyph.Search, C.Muted, 18.dp)
        Gap(w = 10)
        Box(Modifier.weight(1f)) {
            if (model.query.isEmpty()) Txt(S("search_placeholder"), color = C.Muted, size = 15)
            BasicTextField(
                value = model.query,
                onValueChange = { model.query = it },
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(C.Blue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().textFieldFocus().focusRequester(focus).onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (it.key) {
                        Key.Enter, Key.NumPadEnter -> { onSubmit(); true }
                        Key.Escape -> { onEscape(); true }
                        else -> false
                    }
                },
            )
        }
        if (model.query.isNotEmpty()) IconButton(Glyph.Close, onClick = model::clear, color = C.Muted, size = 14.dp, box = 28.dp)
    }
}

/** Разделы выдачи: подпись заглавными, у активного тонкая линия акцента. */
@Composable
private fun FilterTabs(model: SearchModel) {
    RowCenter(Modifier.fillMaxWidth()) {
        SearchFilter.entries.forEach { f ->
            val active = model.filter == f
            Box(
                Modifier.clip(RoundedCornerShape(Look.control.coerceAtMost(12.dp)))
                    .drawBehind { if (Look.hair && active) drawRect(C.Blue, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
                    .background(if (active && !Look.hair) (if (Look.smooth) C.Blue.copy(alpha = 0.22f) else C.SurfaceHigh) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { model.filter = f }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) { Txt(Look.label(S(f.key)), color = if (active) C.Text else C.Muted, size = 11, pixel = true) }
            Gap(w = 4)
        }
    }
}

@Composable
private fun Suggestions(items: List<String>, modifier: Modifier, onPick: (String) -> Unit) {
    val shape = RoundedCornerShape(Look.card.coerceAtMost(14.dp))
    Column(modifier.fillMaxWidth().clip(shape).background(C.Surface, shape).border(Look.line, C.Border, shape).padding(vertical = 6.dp)) {
        items.forEach { s ->
            val source = remember { MutableInteractionSource() }
            RowCenter(
                Modifier.fillMaxWidth().hoverable(source).background(hoverBackground(source)).clickable(interactionSource = source, indication = null) { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Icon(Glyph.Search, C.Muted, 12.dp)
                Gap(w = 10)
                Txt(s, size = 14)
            }
        }
    }
}

@Composable
private fun SearchBody(app: AppState, model: SearchModel, local: List<SongItem>, localPlaylists: List<StoredPlaylist>) {
    val listState = rememberLazyListState()
    when (val st = model.state) {
        SearchState.Idle -> IdleBody(app, model)
        SearchState.Loading -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            localBlock(app, local, localPlaylists)
            item { Skeleton(if (local.isEmpty()) 7 else 4) }
        }
        SearchState.Empty -> Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f, fill = false)) { localBlock(app, local, localPlaylists) }
            NothingFound()
        }
        is SearchState.Failed -> Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f, fill = false)) { localBlock(app, local, localPlaylists) }
            PixelCard(Modifier.fillMaxWidth()) {
                Column {
                    Txt(Look.label(S("search_error_title")), pixel = true, size = 12, color = C.Sand)
                    Gap(h = 8)
                    Txt(st.message, size = 14, maxLines = 3)
                    Gap(h = 6)
                    Txt(S("search_error_network_hint"), color = C.Muted, size = 12, maxLines = 4)
                    Gap(h = 12)
                    PixelButton(S("search_error_retry"), onClick = model::retry)
                }
            }
        }
        is SearchState.Done -> {
            val r = st.results
            val songs = r.songs
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = listState, contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom + 12.dp)) {
                    r.corrected?.let { fixed ->
                        item {
                            RowCenter(Modifier.padding(bottom = 10.dp)) {
                                Txt(S("d_search_corrected", fixed), color = C.Muted, size = 12)
                                Gap(w = 10)
                                Txt(
                                    S("d_search_original", model.query), color = C.Blue, size = 12,
                                    modifier = Modifier.clickable { model.searchOriginal() },
                                )
                            }
                        }
                    }
                    // Своё стоит выше чужого: то, что уже слушал, ищут чаще, чем новое.
                    localBlock(app, local.filterNot { own -> songs.any { it.id == own.id } }, localPlaylists)
                    // В смешанной выдаче артист выше треков: по имени исполнителя ищут его самого, а не одну песню.
                    val artists = if (model.filter == SearchFilter.ALL) r.artists.take(ARTISTS_IN_MIX) else r.artists
                    if (artists.isNotEmpty()) {
                        if (songs.isNotEmpty() || r.albums.isNotEmpty()) item { SectionLabel(S("search_filter_artists"), Modifier.padding(vertical = 8.dp)) }
                        itemsIndexed(artists, key = { _, a -> "artist-${a.browseId}" }) { _, a ->
                            CardLine(a.name, a.subtitle ?: S("card_artist"), a.thumbnailUrl, circle = true) { app.open(Screen.Artist(a.browseId, a.name, a.thumbnailUrl)) }
                        }
                    }
                    if (songs.isNotEmpty()) {
                        if (artists.isNotEmpty() || r.albums.isNotEmpty()) item { SectionLabel(S(if (r.videos) "search_filter_videos" else "search_filter_songs"), Modifier.padding(top = 12.dp, bottom = 8.dp)) }
                        itemsIndexed(songs, key = { i, s -> "${s.id}#$i" }) { i, song ->
                            SongRow(app, song, onPlay = { app.player.play(songs, i) }, index = i, songs = songs, wide = r.videos)
                        }
                    }
                    if (r.albums.isNotEmpty()) {
                        if (songs.isNotEmpty() || artists.isNotEmpty()) item { SectionLabel(S("search_filter_albums"), Modifier.padding(top = 12.dp, bottom = 8.dp)) }
                        itemsIndexed(r.albums, key = { _, c -> "album-${c.browseId}" }) { _, c ->
                            CardLine(c.title, c.subtitle ?: S(if (c.isAlbum) "card_album" else "card_playlist"), c.thumbnailUrl, circle = false) { app.openCard(c) }
                        }
                    }
                    if (songs.size >= RANDOM_MIN) {
                        item {
                            PixelButton(S("search_random"), onClick = { app.player.play(songs, Random.nextInt(songs.size)) }, modifier = Modifier.padding(vertical = 16.dp))
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

/** Блок «уже есть»: свои треки и плейлисты, подошедшие под запрос. */
private fun androidx.compose.foundation.lazy.LazyListScope.localBlock(app: AppState, songs: List<SongItem>, playlists: List<StoredPlaylist>) {
    if (songs.isEmpty() && playlists.isEmpty()) return
    item { SectionLabel(S("search_in_library"), Modifier.padding(top = 4.dp, bottom = 6.dp)) }
    itemsIndexed(playlists, key = { _, p -> "pl-${p.id}" }) { _, p ->
        val tag = if (p.remoteId != null) S("d_pl_in_youtube") else S("library_own_playlist")
        PlaylistLineSmall(p, tag) { app.open(Screen.Local(p.id)) }
    }
    itemsIndexed(songs, key = { i, s -> "own-${s.id}#$i" }) { i, song ->
        SongRow(app, song, onPlay = { app.player.play(songs, i) }, index = i, songs = songs)
    }
    item { Gap(h = 12) }
}

@Composable
private fun IdleBody(app: AppState, model: SearchModel) {
    Column(Modifier.fillMaxSize().padding(end = 12.dp)) {
        Txt(S("search_hint"), color = C.Muted, size = 13, maxLines = 3)
        val history = app.lib.searchHistory
        if (history.isNotEmpty() && app.st.saveSearchHistory) {
            Gap(h = 18)
            RowCenter {
                SectionLabel(S("search_recent"), Modifier.weight(1f))
                IconButton(Glyph.Trash, onClick = app::clearSearchHistory, color = C.Muted, size = 14.dp, box = 28.dp)
            }
            history.forEach { h ->
                val source = remember { MutableInteractionSource() }
                RowCenter(
                    Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(Look.row)).background(hoverBackground(source))
                        .clickable(interactionSource = source, indication = null) { model.submit(h) }.padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Txt(h, size = 15, modifier = Modifier.weight(1f))
                    IconButton(Glyph.Close, onClick = { app.forgetSearch(h) }, color = C.Muted, size = 12.dp, box = 26.dp)
                }
            }
        }
    }
}

@Composable
private fun NothingFound() {
    PixelCard(Modifier.fillMaxWidth()) {
        Column {
            Txt(Look.label(S("search_nothing_title")), pixel = true, size = 12)
            Gap(h = 8)
            Txt(S("search_nothing_text"), color = C.Muted, size = 13, maxLines = 3)
        }
    }
}

/** Серые строки-заглушки на время запроса: экран никогда не бывает пустым в ответ на нажатие. */
@Composable
private fun Skeleton(rows: Int) {
    val pulse = if (Motion.off) 0.5f else rememberInfiniteTransition(label = "skeleton").animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "skeleton-alpha").value
    Column(Modifier.fillMaxWidth().alpha(pulse)) {
        repeat(rows) {
            RowCenter(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(Look.cover)).background(C.SurfaceHigh))
                Gap(w = 12)
                Column {
                    Box(Modifier.width(220.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(C.SurfaceHigh))
                    Gap(h = 6)
                    Box(Modifier.width(120.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(C.SurfaceHigh))
                }
            }
        }
    }
}

/** Строка артиста или альбома: та же высота, что у трека. */
@Composable
private fun CardLine(title: String, subtitle: String, thumb: String?, circle: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    RowCenter(
        Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(Look.row)).background(hoverBackground(source))
            .clickable(interactionSource = source, indication = null, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Cover(thumb, 44.dp, circle = circle)
        Gap(w = 12)
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15)
            Txt(subtitle, color = C.Muted, size = 12)
        }
        Icon(Glyph.ChevronRight, C.Muted, 14.dp)
    }
}

@Composable
private fun PlaylistLineSmall(pl: StoredPlaylist, tag: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    RowCenter(
        Modifier.fillMaxWidth().hoverable(source).clip(RoundedCornerShape(Look.row)).background(hoverBackground(source))
            .clickable(interactionSource = source, indication = null, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        PlaylistTile(pl, 44.dp)
        Gap(w = 12)
        Column(Modifier.weight(1f)) {
            Txt(pl.name, size = 15)
            Txt("$tag · ${S("d_pl_tracks", pl.songs.size)}", color = C.Muted, size = 12)
        }
        Icon(Glyph.ChevronRight, C.Muted, 14.dp)
    }
}

/** Широкая обложка для видео: кадр 16:9, как в YouTube Music. */
@Composable
fun WideCover(url: String?, width: androidx.compose.ui.unit.Dp) {
    val height = width * 9 / 16
    Box(Modifier.size(width, height).clip(RoundedCornerShape(Look.cover.coerceAtMost(8.dp))).background(C.SurfaceHigh), contentAlignment = Alignment.Center) {
        Icon(Glyph.Queue, C.Border, height / 2)
        val model = sizedThumb(url, (width.value * 2).toInt().coerceAtLeast(240))
        if (model != null) {
            coil3.compose.AsyncImage(
                model = model, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(width, height),
            )
        }
    }
}

private const val SEARCH_DEBOUNCE_MS = 280L
private const val SUGGEST_DEBOUNCE_MS = 160L
private const val ARTISTS_IN_MIX = 3
private const val RANDOM_MIN = 8
