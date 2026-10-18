package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texfi.w0y.data.MixedResults
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.Shelf
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.desktop.i18n.S
import com.texfi.w0y.desktop.i18n.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DialEntry(val key: String, val title: String, val subtitle: String?, val thumb: String?, val song: SongItem?, val artistId: String?, val pinned: Boolean = false) {
    val kind: String get() = key.substringBefore(':')
    val id: String get() = key.substringAfter(':')
}

/** Пять страниц по девять плиток, как на телефоне. */
const val DIAL_MAX = 45

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data object Queue : Screen
    data class Album(val browseId: String, val title: String, val thumb: String?) : Screen
    data class Artist(val browseId: String, val name: String, val thumb: String?) : Screen
    data class Remote(val browseId: String, val title: String, val thumb: String?) : Screen
    data class Local(val id: String) : Screen
    data object Liked : Screen
    data object History : Screen
    data object Downloaded : Screen
    data object Player : Screen
    data object Settings : Screen
    data object Stats : Screen
    data object Taste : Screen
    data object LocalFiles : Screen
}

/** Всё состояние приложения в одном месте: экран, библиотека, аккаунт, плеер. */
class AppState(val scope: CoroutineScope, val yt: Yt, private val store: Store) {
    var lib by mutableStateOf(store.load())
        private set
    val player = PlayerCtl(scope, yt, this)
    val downloads = Downloads(scope, yt, this)
    val lyrics = LyricsRepo()
    val recommender = Recommender(this, yt)
    val sync = AccountSync(this, yt)
    val stand = StandMode(this)

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()

    var shelves by mutableStateOf<List<Shelf>>(emptyList())
    var homeLoading by mutableStateOf(false)
    var homeError by mutableStateOf<String?>(null)

    /** Поиск: запрос, раздел, выдача и кэш живут здесь, чтобы возврат с экрана артиста не стирал результаты. */
    val searchModel = SearchModel(this)

    var accountAlbums by mutableStateOf<List<PlaylistCard>>(emptyList())
    var accountStatus by mutableStateOf<String?>(null)
    var loggingIn by mutableStateOf(false)

    val st: DSettings get() = lib.settings

    /** Выделенные строки в списке треков. */
    val selection = Selection()

    /** Проверка обновлений на GitHub (см. [Updater]). */
    val updater = Updater(this, scope)

    fun setSt(block: (DSettings) -> DSettings) {
        val before = st.language
        update { it.copy(settings = block(it.settings)) }
        applyTheme()
        if (st.language != before) {
            yt.setLanguage(Strings.tag)
            loadHome()
        }
    }

    private fun applyTheme() {
        ThemeState.mode = st.theme
        ThemeState.accent = st.accent
        ThemeState.custom = st.customAccent
        ThemeState.style = st.uiStyle
        ThemeState.glass = st.smoothGlass
        lyrics.allSources = st.lyricsAllSources
        ThemeState.weight = st.hlWeight
        ThemeState.corners = st.hlCorners
        ThemeState.coverShape = st.hlCoverShape
        ThemeState.layout = st.hlLayout
        ThemeState.caps = st.hlCaps
        ThemeState.density = st.hlDensity
        ThemeState.backdrop = st.backdrop
        ThemeState.playerBack = st.hlPlayerBack
        if (ThemeState.appIcon != st.appIcon) { ThemeState.appIcon = st.appIcon; AppIcons.applyLauncher(st.appIcon) }
        ThemeState.coverSize = st.hlCoverSize
        ThemeState.coverRing = st.hlCoverRing
        ThemeState.coverSpin = st.hlCoverSpin
        ThemeState.posterDim = st.hlPosterDim
        Motion.mode = st.motion
        Strings.select(st.language.tag)
    }

    val signedIn: Boolean get() = !lib.cookie.isNullOrBlank()
    val likedIds: Set<String> get() = lib.liked.mapTo(HashSet()) { it.id }

    fun start() {
        applyTheme()
        yt.setLanguage(Strings.tag)
        // Куда попасть при запуске: выбранная вкладка или та, где закрыли в прошлый раз.
        when (if (st.startTab == StartTab.LAST) st.lastTab else st.startTab) {
            StartTab.SEARCH -> { stack.clear(); stack.add(Screen.Search) }
            StartTab.LIBRARY -> { stack.clear(); stack.add(Screen.Library) }
            else -> Unit
        }
        lib.cookie?.let { yt.applySession(it) }
        player.start(lib.queue.map { it.toItem() }, if (lib.queue.isEmpty()) -1 else lib.queueIndex, lib.volume)
        mpris = if (Platform.isLinux) Mpris(this, scope).takeIf { it.start() } else null
        loadHome()
        weeklyIfDue()
        if (signedIn) syncAccount(force = true)
        updater.checkOnStart()
    }

    private var mpris: Mpris? = null

    /** «Полный сброс»: библиотека, настройки, вход и скачанное стираются, приложение закрывается. */
    fun eraseEverything() {
        downloads.clearAll()
        mpris?.stop()
        player.shutdown()
        runCatching {
            java.io.File(Store.dirPath()).listFiles()?.filter { it.name.startsWith("library.json") && !it.name.contains("broken") }?.forEach { it.delete() }
        }
        Actions.quit = {}
        kotlin.system.exitProcess(0)
    }

    fun shutdown() {
        mpris?.stop()
        player.shutdown()
        store.save(lib)
    }

    fun update(block: (Library) -> Library) {
        lib = block(lib)
        store.save(lib)
    }

    // ---- навигация
    /** Куда идём: вперёд (новый экран) или назад — от этого зависит направление анимации перехода. */
    var navForward by mutableStateOf(true)
        private set

    fun open(screen: Screen) {
        selection.clear()
        playlistEditing = false
        localEditing = null
        navForward = true
        Anim.screenAt = System.currentTimeMillis()
        if (stack.last() != screen) stack.add(screen)
    }

    fun tab(screen: Screen) {
        navForward = true
        Anim.screenAt = System.currentTimeMillis()
        stack.clear()
        stack.add(screen)
        playlistEditing = false
        localEditing = null
        when (screen) {
            Screen.Home -> StartTab.HOME
            Screen.Search -> StartTab.SEARCH
            Screen.Library -> StartTab.LIBRARY
            else -> null
        }?.let { t -> if (st.lastTab != t) update { it.copy(settings = it.settings.copy(lastTab = t)) } }
        if (screen == Screen.Library && signedIn) syncAccount(force = false)
    }

    fun back() {
        // Открытый редактор (плейлиста или своего файла) закрывается первым «Назад»: экран под ним остаётся.
        if (screen is Screen.LocalFiles && localEditing != null) { localEditing = null; return }
        if (screen is Screen.Local && playlistEditing) { playlistEditing = false; return }
        navForward = false
        Anim.screenAt = System.currentTimeMillis()
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    // ---- главная и поиск
    fun loadHome() {
        if (homeLoading) return
        scope.launch {
            homeLoading = true
            homeError = null
            runCatching { yt.home() }
                .onSuccess { list -> shelves = list.map { it.copy(songs = visible(it.songs)) } }
                .onFailure { homeError = S("d_err_load", it.message ?: "") }
            homeLoading = false
        }
    }

    /** Запрос в историю: повтор поднимается наверх без учёта регистра; выключено в настройках — не пишется. */
    fun rememberSearch(query: String) {
        val q = query.trim()
        if (q.length < 2 || !st.saveSearchHistory) return
        update { it.copy(searchHistory = (listOf(q) + it.searchHistory.filterNot { h -> h.equals(q, true) }).take(20)) }
    }

    fun clearSearchHistory() = update { it.copy(searchHistory = emptyList()) }

    // ---- библиотека
    fun remember(song: SongItem) {
        if (!st.keepHistory) return
        val artist = song.artist.substringBefore(',').trim().lowercase()
        update {
            it.copy(
                history = (listOf(song.stored()) + it.history.filterNot { h -> h.id == song.id }).take(300),
                artistPlays = if (artist.isBlank() || !it.settings.tasteLearning) it.artistPlays else it.artistPlays + (artist to ((it.artistPlays[artist] ?: 0) + 1)),
            )
        }
    }

    /** Прослушивание для статистики и «быстрого набора». */
    fun logPlay(song: SongItem, seconds: Int) = update {
        it.copy(playLog = (it.playLog + StoredPlay(song.stored(), System.currentTimeMillis(), seconds)).takeLast(5000))
    }

    // ---- быстрый набор
    // Ключи плиток: «song:ID», «artist:ID», «album:ID», «remote:ID» (плейлист YouTube), «playlist:ID» (свой плейлист).
    fun dialKey(song: SongItem) = "song:${song.id}"

    fun isPinned(key: String) = key in lib.dialPins

    /** Закрепляет или открепляет плитку. Снимок запоминается: артиста или трек из поиска в журнале может не быть. */
    fun togglePin(key: String, title: String? = null, subtitle: String? = null, thumb: String? = null) = update {
        val pin = key !in it.dialPins
        it.copy(
            dialPins = if (pin) listOf(key) + it.dialPins else it.dialPins - key,
            dialHidden = it.dialHidden - key,
            dialInfo = if (pin && title != null) it.dialInfo + (key to StoredDial(title, subtitle, thumb)) else it.dialInfo,
        )
    }

    fun togglePinSong(song: SongItem) = togglePin(dialKey(song), song.title, song.artist.takeIf { it.isNotBlank() }, song.thumbnailUrl)

    /** Убирает плитку из набора; закреплённая сначала открепляется, иначе она вернулась бы первой. */
    fun hideFromDial(key: String) = update { it.copy(dialPins = it.dialPins - key, dialHidden = (it.dialHidden + key).distinct()) }

    /** «Отменить» после «убрать»: плитка возвращается на место, закрепление тоже. */
    fun restoreDial(key: String, wasPinned: Boolean) = update {
        it.copy(dialHidden = it.dialHidden - key, dialPins = if (wasPinned && key !in it.dialPins) listOf(key) + it.dialPins else it.dialPins)
    }

    /** Плитка, на которую открыто меню (долгое нажатие или правая кнопка), и плитка, которую только что убрали. */
    var dialMenu by mutableStateOf<DialEntry?>(null)
    var dialUndo by mutableStateOf<DialEntry?>(null)

    /**
     * Плитки быстрого набора, как на телефоне: сначала закреплённое вручную (трек, альбом, плейлист, артист),
     * следом то, что слушается чаще всего (от порога из настроек; при равенстве свежее выше), затем свои плейлисты.
     * Убранные плитки вычитаются, на их место встают следующие.
     */
    fun dialEntries(): List<DialEntry> {
        val l = lib
        val songs = HashMap<String, StoredSong>()
        (l.history + l.liked + l.localFiles + l.playlists.flatMap { it.songs }).forEach { songs.putIfAbsent(it.id, it) }
        val counts = HashMap<String, Int>()
        val last = HashMap<String, Long>()
        l.playLog.forEach { p ->
            songs[p.song.id] = p.song
            counts.merge(p.song.id, 1, Int::plus)
            last[p.song.id] = maxOf(last[p.song.id] ?: 0L, p.at)
        }
        fun entry(key: String, pinned: Boolean): DialEntry? {
            val id = key.substringAfter(':')
            val info = l.dialInfo[key]
            return when (key.substringBefore(':')) {
                "song" ->
                    songs[id]?.let { DialEntry(key, it.title, it.artist.takeIf { a -> a.isNotBlank() }, it.thumb, it.toItem(), null, pinned) }
                        ?: info?.let { DialEntry(key, it.title, it.subtitle, it.thumb, SongItem(id = id, title = it.title, artist = it.subtitle.orEmpty(), thumbnailUrl = it.thumb), null, pinned) }
                "artist" ->
                    info?.let { DialEntry(key, it.title, it.subtitle, it.thumb, null, id, pinned) }
                        ?: l.history.firstOrNull { it.artistId == id }?.let { DialEntry(key, it.artist, null, it.thumb, null, id, pinned) }
                "album", "remote" -> info?.let { DialEntry(key, it.title, it.subtitle, it.thumb, null, null, pinned) }
                "playlist" ->
                    l.playlists.firstOrNull { it.id == id }?.let { pl ->
                        DialEntry(key, pl.name, S("library_own_playlist"), pl.customCover ?: pl.cover ?: pl.songs.firstOrNull()?.thumb, null, null, pinned)
                    }
                else -> null
            }
        }
        val out = LinkedHashMap<String, DialEntry>()
        l.dialPins.forEach { k -> entry(k, true)?.let { out.putIfAbsent(k, it) } }
        counts.entries.filter { it.value >= st.dialMinPlays }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { last[it.key] ?: 0L })
            .forEach { (id, _) -> entry("song:$id", false)?.let { out.putIfAbsent(it.key, it) } }
        l.playlists.forEach { pl -> entry("playlist:${pl.id}", false)?.let { out.putIfAbsent(it.key, it) } }
        return out.values.filter { it.key !in l.dialHidden }.take(DIAL_MAX)
    }

    fun toggleLike(song: SongItem) {
        val liked = song.id !in likedIds
        update {
            it.copy(liked = if (liked) listOf(song.stored()) + it.liked else it.liked.filterNot { s -> s.id == song.id })
        }
        if (mirror && !LocalFiles.isLocal(song.id)) scope.launch { runCatching { yt.like(song.id, liked) } }
        if (liked && st.autoDownloadLiked && !LocalFiles.isLocal(song.id)) downloads.download(song)
    }

    private var queueSave: Job? = null

    fun saveQueue(queue: List<SongItem>, index: Int) {
        queueSave?.cancel()
        queueSave =
            scope.launch {
                delay(400)
                update { it.copy(queue = queue.map { s -> s.stored() }, queueIndex = index) }
            }
    }

    fun soundOf(id: String): com.texfi.w0y.data.SoundProfile =
        lib.sound[id]?.let {
            com.texfi.w0y.data.SoundProfile(
                it.speed,
                it.pitch,
                runCatching { com.texfi.w0y.data.Reverb.valueOf(it.reverb) }.getOrDefault(com.texfi.w0y.data.Reverb.OFF),
            )
        } ?: com.texfi.w0y.data.SoundProfile(st.speed, st.pitch, st.reverb)

    fun saveSound(id: String, profile: com.texfi.w0y.data.SoundProfile) = update {
        it.copy(
            sound = if (profile.isPlain) it.sound - id else it.sound + (id to StoredSound(profile.speed, profile.pitch, profile.reverb.name)),
        )
    }

    fun markDownloaded(song: SongItem, path: String) =
        update { it.copy(downloads = it.downloads + (song.id to StoredDownload(path, song.stored()))) }

    fun unmarkDownloaded(id: String) = update { it.copy(downloads = it.downloads - id) }

    fun downloadedPath(id: String): String? =
        lib.downloads[id]?.path?.takeIf { java.io.File(it).isFile }

    /** Прячет помеченные «E» записи, если так выбрано в настройках. */
    fun visible(songs: List<SongItem>): List<SongItem> = if (st.hideExplicit) songs.filterNot { it.explicit } else songs

    val mirror: Boolean get() = signedIn && st.syncPlaylists

    fun forgetSearch(query: String) = update { it.copy(searchHistory = it.searchHistory - query) }

    fun saveVolume(volume: Int) = update { it.copy(volume = volume) }

    fun playlist(id: String): StoredPlaylist? = lib.playlists.firstOrNull { it.id == id }

    private fun mutatePlaylist(id: String, block: (StoredPlaylist) -> StoredPlaylist) =
        update { l -> l.copy(playlists = l.playlists.map { if (it.id == id) block(it) else it }) }

    /** Куда можно добавлять: чужие плейлисты из библиотеки аккаунта YouTube править не даёт. */
    val editablePlaylists: List<StoredPlaylist> get() = lib.playlists.filter { it.remoteId == null || it.editable }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val pl = StoredPlaylist(name = clean)
        update { it.copy(playlists = it.playlists + pl) }
        // В аккаунт — следом: плейлист на телефоне не ждёт сети.
        if (mirror) {
            scope.launch {
                runCatching { yt.createPlaylist(clean) }.getOrNull()?.let { rid -> mutatePlaylist(pl.id) { it.copy(remoteId = rid) } }
            }
        }
    }

    fun renamePlaylist(id: String, name: String) {
        val clean = name.trim()
        val pl = playlist(id) ?: return
        if (clean.isEmpty() || !pl.editable) return
        mutatePlaylist(id) { it.copy(name = clean) }
        if (mirror && pl.remoteId != null) scope.launch { runCatching { yt.renameRemote(pl.remoteId, clean) } }
    }

    fun deletePlaylist(id: String) {
        val pl = playlist(id)
        update { it.copy(playlists = it.playlists.filterNot { p -> p.id == id }) }
        back()
        val rid = pl?.remoteId
        if (rid != null && mirror) {
            // Чужой плейлист не удаляют, а убирают из библиотеки.
            scope.launch { runCatching { if (pl.editable) yt.deleteRemote(rid) else yt.unsaveRemote(rid) } }
        }
    }

    fun addToPlaylist(id: String, song: SongItem) {
        val pl = playlist(id) ?: return
        if (!pl.editable || pl.songs.any { it.id == song.id }) return
        mutatePlaylist(id) { it.copy(songs = it.songs + song.stored()) }
        if (mirror && pl.remoteId != null && !LocalFiles.isLocal(song.id)) scope.launch { runCatching { yt.addToRemote(pl.remoteId, song.id) } }
    }

    fun removeFromPlaylist(id: String, songId: String) {
        val pl = playlist(id) ?: return
        if (!pl.editable) return
        mutatePlaylist(id) { it.copy(songs = it.songs.filterNot { s -> s.id == songId }) }
        val rid = pl.remoteId
        if (mirror && rid != null && !LocalFiles.isLocal(songId)) {
            scope.launch {
                // Убрать трек из плейлиста YouTube можно только зная его место в нём.
                val place = yt.fetchPlaylist(rid, 30)?.setVideoIds?.get(songId) ?: return@launch
                runCatching { yt.removeFromRemote(rid, songId, place) }
            }
        }
    }

    // ---- вид и описание плейлиста

    /**
     * Сохраняет настройку плейлиста. Имя уходит и в аккаунт (если плейлист там есть и его можно менять),
     * остальное — описание, цвет, значок и обложка — остаётся только на этом компьютере.
     */
    fun savePlaylistLook(id: String, name: String, note: String, accent: Int?, glyph: String?, pickedCover: java.io.File?, clearCover: Boolean) {
        val pl = playlist(id) ?: return
        scope.launch {
            var cover = pl.customCover
            if (clearCover) {
                PlaylistCovers.remove(id)
                cover = null
            }
            if (pickedCover != null) {
                val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PlaylistCovers.import(id, pickedCover) }
                if (saved != null) cover = saved
            }
            val clean = name.trim()
            val rename = pl.editable && clean.isNotEmpty() && clean != pl.name
            mutatePlaylist(id) { it.copy(name = if (rename) clean else it.name, note = note.trim().ifBlank { null }, accent = accent, glyph = glyph, customCover = cover) }
            if (rename && mirror && pl.remoteId != null) runCatching { yt.renameRemote(pl.remoteId, clean) }
        }
    }

    // ---- свои файлы

    /** Открыт ли редактор плейлиста (экран плейлиста). */
    var playlistEditing by mutableStateOf(false)

    /** Какой файл открыт в редакторе (экран «Мои файлы»); null — виден список. */
    var localEditing by mutableStateOf<String?>(null)

    val localSongs: List<SongItem> get() = lib.localFiles.map { it.toItem() }

    /** Сколько файлов обработано из скольких; null — импорта нет. */
    var localImport by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** Итог прошлого импорта: добавлено и не удалось. */
    var localResult by mutableStateOf<Pair<Int, Int>?>(null)

    /** Добавляет выбранные файлы и папки: папки обходятся целиком, берутся только звуковые файлы. */
    fun importLocal(picked: List<java.io.File>) {
        val files = picked.flatMap { f -> if (f.isDirectory) f.walkTopDown().filter(LocalFiles::isAudio).toList() else listOf(f) }
        if (files.isEmpty() || localImport != null) return
        localResult = null
        scope.launch {
            var failed = 0
            files.forEachIndexed { i, f ->
                localImport = i to files.size
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { LocalFiles.import(f) } }
                result.onSuccess { song -> update { it.copy(localFiles = listOf(song.stored()) + it.localFiles) } }.onFailure { failed++ }
            }
            localImport = null
            localResult = (files.size - failed) to failed
        }
    }

    /** Новое название, исполнитель, альбом и обложка своего файла — везде, где этот трек уже лежит. */
    fun editLocal(id: String, title: String, artist: String, album: String, pickedCover: java.io.File?, clearCover: Boolean) {
        val old = lib.localFiles.firstOrNull { it.id == id } ?: return
        scope.launch {
            val key = LocalFiles.keyOf(id)
            var thumb = old.thumb
            if (clearCover) {
                LocalFiles.clearCover(key)
                thumb = null
            }
            if (pickedCover != null) {
                val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LocalFiles.importCover(key, pickedCover) }
                if (saved != null) thumb = LocalFiles.uri(saved)
            }
            val newTitle = title.trim().ifBlank { old.title }
            val change = { s: StoredSong -> s.copy(title = newTitle, artist = artist.trim(), album = album.trim().ifBlank { null }, thumb = thumb) }
            update { it.rewriteSong(id, change) }
            player.refreshSong(change(old).toItem())
        }
    }

    fun updateLocalDuration(id: String, seconds: Double) {
        val text = LocalFiles.durationText((seconds * 1000).toLong())
        update { it.rewriteSong(id) { s -> s.copy(duration = text) } }
    }

    /** Убирает файл из приложения: из «Моих файлов», плейлистов, лайков, истории и с диска. */
    fun removeLocal(id: String) {
        update { l ->
            l.copy(
                localFiles = l.localFiles.filterNot { it.id == id },
                liked = l.liked.filterNot { it.id == id },
                history = l.history.filterNot { it.id == id },
                queue = l.queue.filterNot { it.id == id },
                playLog = l.playLog.filterNot { it.song.id == id },
                playlists = l.playlists.map { p -> p.copy(songs = p.songs.filterNot { it.id == id }) },
                dialPins = l.dialPins - "song:$id",
                sound = l.sound - id,
            )
        }
        player.forget(id)
        LocalFiles.delete(id)
    }

    /** Меняет данные трека во всех списках сразу: у каждого списка своя копия записи. */
    private fun Library.rewriteSong(id: String, change: (StoredSong) -> StoredSong): Library {
        fun List<StoredSong>.fix() = map { if (it.id == id) change(it) else it }
        return copy(
            localFiles = localFiles.fix(),
            liked = liked.fix(),
            history = history.fix(),
            queue = queue.fix(),
            playLog = playLog.map { if (it.song.id == id) it.copy(song = change(it.song)) else it },
            playlists = playlists.map { it.copy(songs = it.songs.fix()) },
        )
    }

    // ---- аккаунт
    fun signIn(cookie: String) {
        val clean = cookie.trim().removePrefix("Cookie:").trim()
        if (!clean.contains("SAPISID")) {
            accountStatus = S("d_err_cookie")
            return
        }
        scope.launch {
            accountStatus = S("d_checking")
            yt.applySession(clean)
            runCatching { yt.accountInfo() }
                .onSuccess { (name, avatar) ->
                    update { it.copy(cookie = clean, accountName = name, accountAvatar = avatar) }
                    accountStatus = null
                    syncAccount(force = true)
                }.onFailure {
                    yt.applySession(null)
                    accountStatus = S("d_err_cookie_rejected", it.message ?: "")
                }
        }
    }

    /** Вход через окно Google в браузере (см. GoogleLogin). */
    fun signInWithGoogle() {
        if (loggingIn) return
        loggingIn = true
        scope.launch {
            val result = GoogleLogin.signIn { accountStatus = it }
            loggingIn = false
            result.onSuccess { cookie ->
                if (cookie == null) accountStatus = S("d_err_login_closed") else signIn(cookie)
            }.onFailure { accountStatus = it.message ?: S("d_err_login") }
        }
    }

    fun signOut() {
        yt.applySession(null)
        accountAlbums = emptyList()
        update { it.copy(cookie = null, accountName = null, accountAvatar = null, likesBase = null) }
    }

    /** Двусторонняя сверка плейлистов и лайков с аккаунтом (см. AccountSync). */
    fun syncAccount(force: Boolean = true) {
        if (!signedIn) return
        scope.launch { sync.run(force) }
    }
}
