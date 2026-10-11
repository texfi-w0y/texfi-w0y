package com.texfi.w0y.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.R
import com.texfi.w0y.data.AccountRepository
import com.texfi.w0y.data.AccountSync
import com.texfi.w0y.data.DialHiddenRepository
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.YtPlaylistSync
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.MusicExporter
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** Что открыто в разделе «моё»: список или конкретный плейлист. */
sealed interface LibraryRoute {
    data object Root : LibraryRoute

    data class Local(val playlistId: Long) : LibraryRoute

    data class Remote(val card: PlaylistCard) : LibraryRoute

    data object Liked : LibraryRoute

    data object History : LibraryRoute

    data object Downloads : LibraryRoute

    data object Files : LibraryRoute

    data object Stats : LibraryRoute
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val account: AccountRepository,
    private val youtube: YouTubeRepository,
    private val sync: YtPlaylistSync,
    private val accountSync: AccountSync,
    private val exporter: MusicExporter,
    val downloads: DownloadsRepository,
    private val playback: PlaybackStarter,
    val player: PlayerConnection,
    private val dialHidden: DialHiddenRepository,
    private val settings: SettingsRepository,
    private val localMusic: com.texfi.w0y.data.LocalMusicRepository,
) : ViewModel() {
    val files: StateFlow<List<SongItem>> =
        localMusic.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Идёт добавление: (сделано, всего); null — не идёт. */
    private val _importing = MutableStateFlow<Pair<Int, Int>?>(null)
    val importing: StateFlow<Pair<Int, Int>?> = _importing.asStateFlow()

    /** Итог последнего добавления: (добавлено, не вышло). */
    private val _importResult = MutableStateFlow<Pair<Int, Int>?>(null)
    val importResult: StateFlow<Pair<Int, Int>?> = _importResult.asStateFlow()

    fun importFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty() || _importing.value != null) return
        _importResult.value = null
        _importing.value = 0 to uris.size
        viewModelScope.launch {
            val added = localMusic.import(uris) { done, total -> _importing.value = done to total }
            _importing.value = null
            _importResult.value = added.size to (uris.size - added.size)
        }
    }

    fun editFile(song: SongItem, title: String, artist: String, album: String, cover: android.net.Uri?, clearCover: Boolean) {
        viewModelScope.launch {
            runCatching { localMusic.edit(song, title, artist, album, cover, clearCover) }
                .onFailure { Timber.w(it, "Правка файла не сохранилась") }
            // Если трек сейчас в очереди, плеер подхватит правку при следующем запуске.
        }
    }

    fun removeFile(song: SongItem) {
        viewModelScope.launch { runCatching { localMusic.remove(song.id) } }
    }


    val playlists: StateFlow<List<PlaylistEntity>> =
        library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val liked: StateFlow<List<SongItem>> =
        library.liked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent: StateFlow<List<SongItem>> =
        library.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val downloaded: StateFlow<List<SongItem>> =
        library.downloaded.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isSignedIn: StateFlow<Boolean> =
        account.isSignedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val accountName: StateFlow<String?> =
        account.accountName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val accountAvatar: StateFlow<String?> =
        account.accountAvatar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Обложки плейлистов: своя из аккаунта или картинка первого трека. */
    val playlistCovers: StateFlow<Map<Long, String>> =
        library.playlistCovers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Треки, набравшие порог запусков; порог задаётся в настройках. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dialSongs =
        settings.settings
            .map { it.dialMinPlays }
            .distinctUntilChanged()
            .flatMapLatest { library.dialCandidates(it) }

    /**
     * Быстрый набор: сначала закреплённое вручную, следом — то, что
     * слушается чаще всего. Закреплённое не дублируется в хвосте, иначе
     * один и тот же трек занимал бы две плитки из девяти.
     */
    private val rawDial: kotlinx.coroutines.flow.Flow<List<DialItem>> =
        combine(
            library.pins,
            dialSongs,
            library.playlists,
        ) { pins, played, playlists ->
            val pinned =
                pins.map { pin ->
                    DialItem(
                        kind = pin.kind,
                        id = pin.targetId,
                        title = pin.title,
                        subtitle = pin.subtitle,
                        thumbnailUrl = pin.thumbnailUrl,
                        pinned = true,
                        // Закреплённый трек играет из самого снимка: он мог
                        // быть закреплён из поиска и в локальной базе не лежать.
                        song =
                            if (pin.kind == PinEntity.KIND_SONG) {
                                SongItem(
                                    id = pin.targetId,
                                    title = pin.title,
                                    artist = pin.subtitle.orEmpty(),
                                    thumbnailUrl = pin.thumbnailUrl,
                                )
                            } else {
                                null
                            },
                    )
                }
            // Трек попадает в набор с первого запуска (порог в настройках),
            // а порядок учится по тому, что дослушивают. Лайки сюда не
            // подмешиваются.
            val songs =
                played
                    .distinctBy { it.id }
                    .map { song ->
                        DialItem(
                            kind = PinEntity.KIND_SONG,
                            id = song.id,
                            title = song.title,
                            subtitle = song.artist.takeIf { it.isNotBlank() },
                            thumbnailUrl = song.thumbnailUrl,
                            song = song,
                        )
                    }
            val localPlaylists =
                playlists.map { playlist ->
                    DialItem(
                        kind = PinEntity.KIND_PLAYLIST,
                        id = playlist.id.toString(),
                        title = playlist.name,
                        subtitle = context.getString(R.string.library_own_playlist),
                        localPlaylistId = playlist.id,
                    )
                }
            (pinned + songs + localPlaylists)
                .distinctBy { it.kind to it.id }
                // С запасом: убранные плитки вычитаются ниже, и на их место
                // должны встать следующие кандидаты, а не пустые клетки.
                .take(SPEED_DIAL_SIZE + SPEED_DIAL_SPARE)
        }

    /** Быстрый набор без убранных плиток. */
    val speedDial: StateFlow<List<DialItem>> =
        combine(rawDial, dialHidden.hidden) { items, hidden ->
            val gone = hidden.map { it.kind to it.id }.toSet()
            items.filterNot { (it.kind to it.id) in gone }.take(SPEED_DIAL_SIZE)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Адреса первых плиток быстрого набора готовятся заранее: туда
        // нажимают чаще всего, и старт с плитки не должен ждать извлечения.
        // Только адреса, без звука — трафика почти нет. С паузой, чтобы не
        // мешать первому экрану.
        viewModelScope.launch {
            val first = speedDial.first { list -> list.any { it.song != null } }
            kotlinx.coroutines.delay(DIAL_PREFETCH_DELAY_MS)
            youtube.prefetchSequential(first.mapNotNull { it.song?.id }.take(DIAL_PREFETCH))
        }
    }

    /**
     * Убирает плитку из набора на время. Закреплённая сначала открепляется —
     * иначе она вернулась бы в начало набора, как только скрытие кончится.
     */
    fun hideDial(item: DialItem) = viewModelScope.launch {
        if (item.pinned) library.togglePin(item.kind, item.id, item.title, item.subtitle, item.thumbnailUrl)
        dialHidden.hide(item.kind, item.id, item.title)
    }

    /** Отмена «убрать»: плитка возвращается на место, закрепление тоже. */
    fun undoHideDial(item: DialItem) = viewModelScope.launch {
        dialHidden.restore(item.kind, item.id)
        if (item.pinned) library.togglePin(item.kind, item.id, item.title, item.subtitle, item.thumbnailUrl)
    }

    /** Треки быстрого набора подряд — чтобы плитка запускала очередь, а не один трек. */
    fun playDial(item: DialItem) {
        val queue = speedDial.value.mapNotNull { it.song }
        val index = queue.indexOfFirst { it.id == item.id }
        if (index >= 0) playback.play(queue, index) else item.song?.let { playback.play(listOf(it), 0) }
    }

    fun togglePin(item: DialItem) = viewModelScope.launch {
        library.togglePin(item.kind, item.id, item.title, item.subtitle, item.thumbnailUrl)
    }

    /** Закрепляет трек из любого списка — например прямо из плеера. */
    fun pinSong(song: SongItem) = viewModelScope.launch {
        library.togglePin(
            kind = PinEntity.KIND_SONG,
            targetId = song.id,
            title = song.title,
            subtitle = song.artist.takeIf { it.isNotBlank() },
            thumbnailUrl = song.thumbnailUrl,
        )
    }

    private val _route = MutableStateFlow<LibraryRoute>(LibraryRoute.Root)
    val route: StateFlow<LibraryRoute> = _route.asStateFlow()

    private val _remoteSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val remoteSongs: StateFlow<List<SongItem>> = _remoteSongs.asStateFlow()

    private val _remoteSuggestions = MutableStateFlow<List<SongItem>>(emptyList())

    /** Рекомендации под плейлистом аккаунта; пусто, если они выключены. */
    val remoteSuggestions: StateFlow<List<SongItem>> = _remoteSuggestions.asStateFlow()

    /** Сохранённые альбомы аккаунта: плейлисты аккаунта живут среди обычных. */
    val accountAlbums: StateFlow<List<PlaylistCard>> = accountSync.albums

    val syncing: StateFlow<Boolean> = accountSync.running

    val syncError: StateFlow<String?> =
        accountSync.error
            .map { it?.let { message -> context.getString(R.string.library_sync_failed, message) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Последняя неудачная запись плейлиста в аккаунт — её видно в библиотеке. */
    val mirrorFailure: StateFlow<String?> = sync.failure

    private val _pushResult = MutableStateFlow<String?>(null)

    /** Чем кончилась ручная догрузка плейлиста в аккаунт. */
    val pushResult: StateFlow<String?> = _pushResult.asStateFlow()

    fun clearMirrorFailure() = sync.clearFailure()

    private val _currentPlaylistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val currentPlaylistSongs: StateFlow<List<SongItem>> = _currentPlaylistSongs.asStateFlow()

    /** Подписка на состав открытого плейлиста: прежняя снимается, иначе треки соседних плейлистов смешиваются. */
    private var playlistJob: kotlinx.coroutines.Job? = null

    fun open(route: LibraryRoute) {
        _route.value = route
        playlistJob?.cancel()
        playlistJob = null
        when (route) {
            is LibraryRoute.Local -> {
                _currentPlaylistSongs.value = emptyList()
                playlistJob = viewModelScope.launch {
                    library.playlistSongs(route.playlistId).collect { _currentPlaylistSongs.value = it }
                }
                // Состав сверяется с аккаунтом при открытии: правили его там — увидим сразу.
                viewModelScope.launch { accountSync.refreshPlaylist(route.playlistId) }
            }

            is LibraryRoute.Remote ->
                viewModelScope.launch {
                    _remoteSongs.value = emptyList()
                    _remoteSuggestions.value = emptyList()
                    _remoteSongs.value =
                        runCatching { youtube.playlistSongs(route.card.browseId) }
                            .onFailure { Timber.w(it, "Плейлист ${route.card.title} не открылся") }
                            .getOrDefault(emptyList())
                    // Рекомендации YouTube под плейлистом — только по желанию и
                    // отдельным блоком: в сам плейлист и в очередь они не попадают.
                    if (settings.settings.first().showPlaylistRecommendations) {
                        _remoteSuggestions.value =
                            runCatching { youtube.playlistSuggestions(route.card.browseId) }.getOrDefault(emptyList())
                    }
                }

            else -> Unit
        }
    }

    fun back() {
        playlistJob?.cancel()
        playlistJob = null
        _route.value = LibraryRoute.Root
        _currentPlaylistSongs.value = emptyList()
        _remoteSongs.value = emptyList()
        _remoteSuggestions.value = emptyList()
    }

    fun createPlaylist(name: String) = viewModelScope.launch { library.createPlaylist(name) }

    fun deletePlaylist(id: Long) = viewModelScope.launch {
        library.deletePlaylist(id)
        back()
    }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { library.renamePlaylist(id, name) }

    /** Новый порядок плейлиста: в базу и, если плейлист в аккаунте, туда же. */
    fun reorderPlaylist(playlistId: Long, songIds: List<String>) =
        viewModelScope.launch { library.reorderPlaylist(playlistId, songIds) }

    fun removeFromPlaylist(playlistId: Long, songId: String) =
        viewModelScope.launch { library.removeFromPlaylist(playlistId, songId) }

    /**
     * Догрузить плейлист в аккаунт руками.
     *
     * Нужно для тех плейлистов, что появились до входа, и на случай, когда
     * YouTube не принял часть треков: молча оставлять расхождение —
     * то же самое, что врать о синхронизации.
     */
    fun pushPlaylist(id: Long) {
        _pushResult.value = null
        viewModelScope.launch {
            val added = library.pushPlaylist(id)
            _pushResult.value =
                when {
                    added == null -> context.getString(R.string.playlist_push_failed)
                    added == 0 -> context.getString(R.string.playlist_push_same)
                    else -> context.getString(R.string.playlist_push_done, added)
                }
        }
    }

    fun consumePushResult() {
        _pushResult.value = null
    }

    fun play(songs: List<SongItem>, index: Int) = playback.play(songs, index)

    /** Плейлист играет только своими треками — без рекомендаций в очереди. */
    fun playCollection(songs: List<SongItem>, index: Int) = playback.playCollection(songs, index)

    private val _exportStatus = MutableStateFlow<String?>(null)

    /** Что сказать после выгрузки скачанного в «Музыка/w0y music»; null — ничего. */
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    /** Сколько треков из скольких уже сохранено: MP3 кодируется не мгновенно. */
    private val _exportProgress = MutableStateFlow(0 to 0)
    val exportProgress: StateFlow<Pair<Int, Int>> = _exportProgress.asStateFlow()

    /** Папка для сохранения на телефон; [ExportFolder.asked] — вопрос о ней уже был. */
    val exportFolder: StateFlow<ExportFolder?> =
        settings.settings
            .map { ExportFolder(it.exportTreeUri, it.exportFolderAsked || it.exportTreeUri.isNotBlank()) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ответ на вопрос о папке: своя папка или null — «Музыка/w0y music». Доступ к своей сохраняется. */
    fun answerExportFolder(uri: android.net.Uri?) = viewModelScope.launch {
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settings.answerExportFolder(uri?.toString().orEmpty())
    }

    /** Складывает скачанные треки в обычную папку, откуда их видят другие плееры и компьютер. */
    fun exportDownloads(songs: List<SongItem>) {
        if (_exporting.value || songs.isEmpty()) return
        viewModelScope.launch {
            _exporting.value = true
            _exportStatus.value = null
            val r = exporter.export(songs) { done, total -> _exportProgress.value = done to total }
            _exportStatus.value =
                when {
                    r.failed > 0 && r.saved == 0 && r.existed == 0 -> context.getString(R.string.downloads_export_failed)
                    else -> context.getString(R.string.downloads_export_done, r.saved, r.existed, r.failed)
                }
            _exporting.value = false
        }
    }

    fun download(song: SongItem) = downloads.download(song)

    fun downloadAll(songs: List<SongItem>) = downloads.downloadAll(songs)

    fun cancelDownload(songId: String) = downloads.cancel(songId)

    val failedDownloads: StateFlow<List<SongItem>> =
        library.failedDownloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadFailures: StateFlow<Map<String, String>> =
        downloads.failures.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Массовые действия над выбранными скачанными треками. */
    fun deleteDownloads(ids: Collection<String>) = ids.forEach(downloads::cancel)

    fun redownload(ids: Collection<String>) = downloads.redownload(ids.toList())

    /** Убрать трек из списка неудачных, не скачивая. */
    fun dismissFailure(id: String) = viewModelScope.launch { library.markDownloadError(id, null) }

    fun clearHistory() = viewModelScope.launch { library.clearHistory() }

    /**
     * Сверка с аккаунтом. Вход в «Моё» запускает её сам, без [force] и не
     * чаще раза в минуту; кнопка «обновить» — принудительная.
     */
    fun sync(force: Boolean = true) {
        viewModelScope.launch { accountSync.run(force) }
    }

    fun signOut() = viewModelScope.launch {
        account.signOut()
    }

    /**
     * Проверяет cookie и сообщает наверх, чем кончилось: `null` — вход прошёл,
     * иначе текст для пользователя. Экран входа закрывается только при успехе,
     * чтобы не терять уже введённое.
     */
    fun onSignedIn(cookie: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val result = account.signIn(cookie)
        result.fold(
            onSuccess = {
                onResult(null)
                sync()
            },
            onFailure = { onResult(it.message ?: context.getString(R.string.library_login_rejected)) },
        )
    }
}

/** Плитка быстрого набора: трек, альбом, плейлист или артист. */
data class DialItem(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val pinned: Boolean = false,
    /** Заполнено только для треков: по нему плитка сразу играет. */
    val song: SongItem? = null,
    /** Заполнено для своих плейлистов: они открываются локально, а не через YouTube. */
    val localPlaylistId: Long? = null,
)

/** Пять страниц по девять плиток: столько влезает без прокрутки экрана. */
private const val SPEED_DIAL_PAGE = 9
private const val DIAL_PREFETCH = 4
private const val DIAL_PREFETCH_DELAY_MS = 2_500L
private const val SPEED_DIAL_PAGES = 5
private const val SPEED_DIAL_SIZE = SPEED_DIAL_PAGE * SPEED_DIAL_PAGES

/** Запас кандидатов на случай скрытых плиток. */
private const val SPEED_DIAL_SPARE = 40

/** Папка для «сохранить в музыку»: пустой [tree] — Музыка/w0y music. */
data class ExportFolder(val tree: String, val asked: Boolean)
