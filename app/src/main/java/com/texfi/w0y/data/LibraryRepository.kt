package com.texfi.w0y.data

import com.texfi.w0y.data.db.HistoryEntity
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Всё, что приложение хранит у себя: плейлисты, лайки, история. */
@Singleton
class LibraryRepository @Inject constructor(
    private val dao: W0yDao,
    private val sync: YtPlaylistSync,
    private val dialHidden: DialHiddenRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val playlists: Flow<List<PlaylistEntity>> = dao.playlists()

    val folders: Flow<List<com.texfi.w0y.data.db.FolderEntity>> = dao.folders()

    suspend fun createFolder(name: String): Long? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        return dao.createFolder(com.texfi.w0y.data.db.FolderEntity(name = clean, createdAt = now()))
    }

    suspend fun renameFolder(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) dao.renameFolder(id, clean)
    }

    suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)

    suspend fun movePlaylist(playlistId: Long, folderId: Long?) = dao.setPlaylistFolder(playlistId, folderId)

    /** Куда можно добавлять треки: чужие плейлисты из библиотеки аккаунта YouTube править не даёт. */
    val editablePlaylists: Flow<List<PlaylistEntity>> =
        playlists.map { list -> list.filter { it.remoteId == null || it.remoteEditable } }

    /**
     * Обложки плейлистов: своя из аккаунта, а если её нет — картинка
     * первого трека. Так плейлист не остаётся серым квадратом.
     */
    val playlistCovers: Flow<Map<Long, String>> =
        combine(playlists, dao.playlistFirstCovers()) { lists, firsts ->
            val first = firsts.associate { it.playlistId to it.url }
            lists.mapNotNull { p -> (p.customCover ?: p.coverUrl ?: first[p.id])?.let { p.id to it } }.toMap()
        }
    val liked: Flow<List<SongItem>> = dao.likedSongs().map { list -> list.map(SongEntity::toItem) }
    val recent: Flow<List<SongItem>> = dao.recentSongs().map { list -> list.map(SongEntity::toItem) }
    val downloaded: Flow<List<SongItem>> =
        dao.songsWithDownloadState().map { list -> list.map(SongEntity::toItem) }

    /** Что слушается чаще всего — с числом засчитанных прослушиваний. */
    val mostPlayed: Flow<List<SongItem>> =
        dao.mostPlayed().map { list -> list.map { it.song.toItem() } }

    /**
     * Кандидаты быстрого набора: треки, запущенные не меньше [minPlays]
     * раз. Выше то, что дослушивают чаще, при равенстве — что свежее.
     */
    fun dialCandidates(minPlays: Int): Flow<List<SongItem>> =
        dao.dialSongs(minPlays = minPlays.coerceAtLeast(1)).map { list -> list.map { it.song.toItem() } }

    val pins: Flow<List<PinEntity>> = dao.pins()

    fun playlist(id: Long) = dao.playlist(id)

    fun playlistSongs(id: Long): Flow<List<SongItem>> =
        dao.playlistSongs(id).map { list -> list.map(SongEntity::toItem) }

    fun playlistSize(id: Long) = dao.playlistSize(id)

    fun isLiked(id: String): Flow<Boolean> = dao.likedFlow(id).map { it == true }

    /**
     * Создаёт плейлист и, если включено зеркалирование, заводит такой же в
     * аккаунте. Сначала запись на телефон: если YouTube откажет, плейлист
     * всё равно останется, просто без отражения.
     */
    suspend fun createPlaylist(name: String): Long {
        val clean = name.trim()
        val id = dao.createPlaylist(PlaylistEntity(name = clean, createdAt = now()))
        if (sync.enabled()) dao.setRemoteId(id, sync.create(clean))
        return id
    }

    suspend fun renamePlaylist(id: Long, name: String) {
        val clean = name.trim()
        dao.renamePlaylist(id, clean)
        remoteId(id)?.let { sync.rename(it, clean) }
    }

    /** Всё, что пользователь настраивает в плейлисте; имя уходит и в аккаунт, остальное — только здесь. */
    suspend fun customizePlaylist(id: Long, name: String, note: String?, accent: Int?, glyph: String?, cover: String?) {
        val old = dao.playlistOnce(id)
        val clean = name.trim().ifBlank { old?.name.orEmpty() }
        dao.updatePlaylistLook(id, clean, note?.trim()?.ifBlank { null }, accent, glyph, cover)
        if (old != null && old.name != clean) remoteId(id)?.let { sync.rename(it, clean) }
    }

    suspend fun deletePlaylist(id: Long) {
        // Ссылку читаем до удаления: после него строки уже нет.
        val playlist = dao.playlistOnce(id)
        val remote = remoteId(id)
        dao.deletePlaylist(id)
        // Чужой плейлист из библиотеки не удаляют, а убирают из неё.
        remote?.let { if (playlist?.remoteEditable == false) sync.unsave(it) else sync.delete(it) }
    }

    suspend fun addToPlaylist(playlistId: Long, song: SongItem) {
        dao.addSongToPlaylist(playlistId, SongEntity.from(song), now())
        if (!LocalMusic.isLocal(song.id)) remoteId(playlistId)?.let { sync.add(it, song.id) }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) {
        dao.removeFromPlaylist(playlistId, songId)
        remoteId(playlistId)?.let { sync.remove(it, songId) }
    }

    /**
     * Ручная догрузка плейлиста в аккаунт: для тех, что появились до входа
     * или когда YouTube не принял часть треков. Возвращает, сколько
     * добавилось, или null — если не получилось.
     */
    suspend fun pushPlaylist(id: Long): Int? {
        if (!sync.enabled()) return null
        val playlist = dao.playlistOnce(id) ?: return null
        val remote = playlist.remoteId ?: sync.create(playlist.name)?.also { dao.setRemoteId(id, it) }
        remote ?: return null
        return sync.push(remote, dao.playlistSongIds(id))
    }

    /** Ищет по тому, что уже есть на телефоне. */
    suspend fun searchLocal(query: String): List<SongItem> =
        if (query.isBlank()) emptyList() else dao.searchLocal(query.trim()).map(SongEntity::toItem)

    private suspend fun remoteId(playlistId: Long): String? =
        if (!sync.enabled()) null else dao.playlistOnce(playlistId)?.remoteId

    suspend fun toggleLike(song: SongItem): Boolean {
        val stored = dao.song(song.id)
        if (stored == null) dao.saveSongMeta(SongEntity.from(song))
        val liked = stored?.liked != true
        dao.setLiked(song.id, liked, if (liked) now() else null)
        // Лайк уходит в аккаунт в фоне: сердце уже переключилось, ждать сети ни к чему.
        // Не дошёл — сверка подхватит: снимок лайков не содержит этого трека.
        scope.launch { if (sync.enabled() && !LocalMusic.isLocal(song.id)) sync.like(song.id, liked) }
        return liked
    }

    /**
     * Строка истории заводится на старте трека — «недавнее» показывает всё,
     * что включали. Прослушиванием она станет позже, по правилу
     * [ListenRule], через [updateListen]. Возвращает id строки.
     */
    suspend fun remember(song: SongItem): Long {
        // Слияние со старой записью — внутри saveSongMeta: из плеера трек
        // приходит без альбома и длительности, а лайк и загрузка вообще не
        // его дело.
        dao.saveSongMeta(SongEntity.from(song))
        // Убранный из быстрого набора трек включили снова — он может вернуться.
        dialHidden.restore(PinEntity.KIND_SONG, song.id)
        return dao.addHistory(HistoryEntity(songId = song.id, playedAt = now()))
    }

    suspend fun updateListen(historyId: Long, listenedMs: Long, counted: Boolean) =
        dao.updateListen(historyId, listenedMs, counted)

    /** Длительность, которую узнал плеер, — если своей у трека нет. */
    suspend fun rememberDuration(songId: String, durationMs: Long) {
        if (durationMs > 0) dao.setDurationMs(songId, durationMs)
    }

    /** Новый порядок своего плейлиста. Аккаунтный порядок меняет [YtPlaylistSync]. */
    suspend fun reorderPlaylist(playlistId: Long, songIds: List<String>) {
        dao.reorderPlaylist(playlistId, songIds)
        remoteId(playlistId)?.let { remote -> sync.reorder(remote, songIds) }
    }

    suspend fun clearHistory() = dao.clearHistory()

    /**
     * Закрепляет или снимает плитку быстрого набора.
     *
     * Снимок хранится целиком: закрепить можно артиста или альбом, которых
     * в локальной библиотеке нет, а плитка должна рисоваться и без сети.
     */
    suspend fun togglePin(
        kind: String,
        targetId: String,
        title: String,
        subtitle: String? = null,
        thumbnailUrl: String? = null,
    ): Boolean {
        val key = PinEntity.key(kind, targetId)
        if (dao.isPinned(key) > 0) {
            dao.unpin(key)
            return false
        }
        // Закрепил снова — значит, плитка снова нужна.
        dialHidden.restore(kind, targetId)
        dao.pin(
            PinEntity(
                key = key,
                kind = kind,
                targetId = targetId,
                title = title,
                subtitle = subtitle,
                thumbnailUrl = thumbnailUrl,
                pinnedAt = now(),
            ),
        )
        return true
    }

    /** «Твоя версия» этого трека, как её видит плеер. */
    fun sound(songId: String): Flow<SoundProfile?> = dao.soundFlow(songId).map { it?.toProfile() }

    suspend fun soundOnce(songId: String): SoundProfile? = dao.sound(songId)?.toProfile()

    /** Сколько треков слушаются по-своему. */
    val soundProfiles: Flow<Int> = dao.soundProfileCount()

    /**
     * Запоминает версию трека или снимает её (`null` — «играть как все»).
     *
     * Трек может ещё не лежать в базе: версию задают из плеера, а туда он
     * попадает из выдачи. Поэтому сначала убеждаемся, что строка есть, —
     * иначе UPDATE молча не сделал бы ничего.
     */
    suspend fun setSound(song: SongItem, profile: SoundProfile?) {
        dao.saveSongMeta(SongEntity.from(song))
        dao.setSound(song.id, profile?.speed, profile?.pitch, profile?.reverb?.name)
    }

    suspend fun markDownload(songId: String, state: Int) = dao.setDownloadState(songId, state)

    suspend fun markDownloadError(songId: String, error: String?) = dao.setDownloadError(songId, error)

    /** Треки, которые не скачались, — с причиной в [downloadErrors]. */
    val failedDownloads: Flow<List<SongItem>> =
        dao.songsWithDownloadError().map { list -> list.map(SongEntity::toItem) }

    val downloadErrors: Flow<Map<String, String>> =
        dao.downloadErrors().map { rows -> rows.associate { it.id to it.downloadError } }

    suspend fun saveSong(song: SongItem) = dao.saveSongMeta(SongEntity.from(song))

    suspend fun songOnce(id: String): SongItem? = dao.song(id)?.toItem()

    suspend fun downloadedIds(): List<String> = dao.downloadedIds()

    /**
     * Лайки из аккаунта в локальную библиотеку.
     *
     * Локальные лайки при этом не снимаются: синхронизация добавляет то,
     * что есть в аккаунте, а не заменяет собой то, что человек отметил на
     * телефоне. Иначе один вход в аккаунт стирал бы всё, что накопилось
     * без него.
     */
    suspend fun importLikes(songs: List<SongItem>) {
        val now = now()
        songs.forEach { song ->
            dao.saveSongMeta(SongEntity.from(song))
            dao.setLiked(song.id, true, now)
        }
    }

    private fun now() = System.currentTimeMillis()
}
