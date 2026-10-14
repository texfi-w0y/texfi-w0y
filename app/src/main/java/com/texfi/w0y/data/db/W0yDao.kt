package com.texfi.w0y.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface W0yDao {
    /**
     * Запись трека целиком — вместе с лайком и состоянием загрузки.
     *
     * Снаружи вызывать нельзя: у трека из выдачи этих полей нет, и такая
     * запись затрёт то, что человек отметил сам. Для всего, что приходит
     * от YouTube, есть [saveSongMeta]. Метод остаётся потому, что вставку
     * новой строки делать всё равно чем-то надо.
     */
    @Upsert
    suspend fun upsertSong(song: SongEntity)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun song(id: String): SongEntity?

    /** Свои файлы (id начинается с `local:`), новые сверху. */
    @Query("SELECT * FROM songs WHERE id LIKE 'local:%' ORDER BY rowid DESC")
    fun localSongs(): Flow<List<SongEntity>>

    /** Правка своего файла: пустые значения здесь затирают, в отличие от [updateSongMeta]. */
    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album, thumbnailUrl = :thumbnailUrl, artistLinks = NULL, artistId = NULL WHERE id = :id")
    suspend fun updateLocal(id: String, title: String, artist: String, album: String?, thumbnailUrl: String?)

    @Query("UPDATE pins SET title = :title, subtitle = :artist, thumbnailUrl = :thumbnailUrl WHERE kind = 'song' AND targetId = :id")
    suspend fun updatePinnedSong(id: String, title: String, artist: String, thumbnailUrl: String?)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongRow(id: String)

    @Query("DELETE FROM playlist_songs WHERE songId = :id")
    suspend fun deleteSongFromPlaylists(id: String)

    @Query("DELETE FROM history WHERE songId = :id")
    suspend fun deleteSongHistory(id: String)

    @Query("DELETE FROM pins WHERE targetId = :id")
    suspend fun deleteSongPins(id: String)

    /**
     * Обновляет только то, что приходит из выдачи.
     *
     * Лайк, время лайка и состояние загрузки принадлежат пользователю, а не
     * ответу YouTube, и в этом запросе их просто нет. Раньше трек
     * перезаписывался целиком через `@Upsert`, и лайк слетал при первом же
     * повторном прослушивании — история сохраняла тот же трек и обнуляла
     * флаг. Пустые и отсутствующие значения тоже не затирают сохранённое:
     * из истории трек приходит без альбома и длительности.
     */
    @Query(
        """
        UPDATE songs SET
            title = CASE WHEN :title <> '' THEN :title ELSE title END,
            artist = CASE WHEN :artist <> '' THEN :artist ELSE artist END,
            album = COALESCE(:album, album),
            durationText = COALESCE(:durationText, durationText),
            thumbnailUrl = COALESCE(:thumbnailUrl, thumbnailUrl),
            artistId = COALESCE(:artistId, artistId),
            albumId = COALESCE(:albumId, albumId),
            artistLinks = COALESCE(:artistLinks, artistLinks),
            explicit = CASE WHEN :explicit THEN 1 ELSE explicit END
        WHERE id = :id
        """,
    )
    suspend fun updateSongMeta(
        id: String,
        title: String,
        artist: String,
        album: String?,
        durationText: String?,
        thumbnailUrl: String?,
        artistId: String?,
        albumId: String?,
        artistLinks: String?,
        explicit: Boolean,
    )

    /**
     * Единственный способ положить трек в таблицу из выдачи.
     *
     * Новый — вставляется, известный — обновляется по метаданным, и его
     * лайк с загрузкой остаются на месте.
     */
    @Transaction
    suspend fun saveSongMeta(song: SongEntity) {
        if (song(song.id) == null) {
            upsertSong(song)
            return
        }
        updateSongMeta(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationText = song.durationText,
            thumbnailUrl = song.thumbnailUrl,
            artistId = song.artistId,
            albumId = song.albumId,
            artistLinks = song.artistLinks,
            explicit = song.explicit,
        )
    }

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY likedAt DESC")
    fun likedSongs(): Flow<List<SongEntity>>

    @Query("SELECT liked FROM songs WHERE id = :id")
    fun likedFlow(id: String): Flow<Boolean?>

    @Query("UPDATE songs SET liked = :liked, likedAt = :likedAt WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean, likedAt: Long?)

    @Query("UPDATE songs SET downloadState = :state WHERE id = :id")
    suspend fun setDownloadState(id: String, state: Int)

    /**
     * Записывает «твою версию» трека. NULL во всех трёх — «играть как все».
     *
     * В [updateSongMeta] этих столбцов нет намеренно: версия принадлежит
     * пользователю, и повторное прослушивание не должно её сбрасывать —
     * ровно та же история, что когда-то была с лайком.
     */
    @Query("UPDATE songs SET speed = :speed, pitch = :pitch, reverb = :reverb WHERE id = :id")
    suspend fun setSound(id: String, speed: Float?, pitch: Float?, reverb: String?)

    @Query("SELECT speed, pitch, reverb FROM songs WHERE id = :id")
    fun soundFlow(id: String): Flow<SoundRow?>

    @Query("SELECT speed, pitch, reverb FROM songs WHERE id = :id")
    suspend fun sound(id: String): SoundRow?

    /** Сколько треков слушаются со своей версией — строка для «итогов». */
    @Query("SELECT COUNT(*) FROM songs WHERE speed IS NOT NULL")
    fun soundProfileCount(): Flow<Int>

    @Query("SELECT id FROM songs WHERE downloadState = 2")
    suspend fun downloadedIds(): List<String>

    @Query("SELECT * FROM songs WHERE downloadState = :state ORDER BY title")
    fun songsWithDownloadState(state: Int = SongEntity.DOWNLOAD_DONE): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistRow(id: Long)

    /** Плейлист вместе с составом: без внешнего ключа строки состава иначе остаются сиротами. */
    @Transaction
    suspend fun deletePlaylist(id: Long) {
        clearPlaylistSongs(id)
        deletePlaylistRow(id)
    }

    @Query("UPDATE playlists SET name = :name, note = :note, accent = :accent, glyph = :glyph, customCover = :cover WHERE id = :id")
    suspend fun updatePlaylistLook(id: Long, name: String, note: String?, accent: Int?, glyph: String?, cover: String?)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun folders(): Flow<List<FolderEntity>>

    @Insert
    suspend fun createFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE playlists SET folderId = :folderId WHERE id = :playlistId")
    suspend fun setPlaylistFolder(playlistId: Long, folderId: Long?)

    @Query("UPDATE playlists SET folderId = NULL WHERE folderId = :folderId")
    suspend fun emptyFolder(folderId: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderRow(id: Long)

    /** Папка исчезает, а её плейлисты возвращаются в общий список. */
    @Transaction
    suspend fun deleteFolder(id: Long) {
        emptyFolder(id)
        deleteFolderRow(id)
    }

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlistOnce(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE remoteId IS NOT NULL")
    suspend fun remotePlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE remoteId IS NULL")
    suspend fun localOnlyPlaylists(): List<PlaylistEntity>

    @Query("UPDATE playlists SET name = :name, coverUrl = COALESCE(:cover, coverUrl) WHERE id = :id")
    suspend fun updatePlaylistInfo(id: Long, name: String, cover: String?)

    @Query("UPDATE playlists SET remoteEditable = :editable, syncBase = :base, syncedAt = :at, coverUrl = COALESCE(:cover, coverUrl) WHERE id = :id")
    suspend fun markSynced(id: Long, editable: Boolean, base: String, at: Long, cover: String?)

    /** Обложка по умолчанию — картинка первого трека: у плейлиста без своей она всё же есть. */
    @Query(
        """
        SELECT ps.playlistId AS playlistId, s.thumbnailUrl AS url
        FROM playlist_songs ps JOIN songs s ON s.id = ps.songId
        WHERE s.thumbnailUrl IS NOT NULL AND ps.position = (
            SELECT MIN(p2.position) FROM playlist_songs p2 JOIN songs s2 ON s2.id = p2.songId
            WHERE p2.playlistId = ps.playlistId AND s2.thumbnailUrl IS NOT NULL
        )
        """,
    )
    fun playlistFirstCovers(): Flow<List<PlaylistCoverRow>>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("SELECT id FROM songs WHERE liked = 1")
    suspend fun likedIds(): List<String>

    /** Ссылка на плейлист в аккаунте: появляется, когда он там создан. */
    @Query("UPDATE playlists SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemoteId(id: Long, remoteId: String?)

    @Query(
        """
        SELECT songId FROM playlist_songs
        WHERE playlistId = :playlistId
        ORDER BY position
        """,
    )
    suspend fun playlistSongIds(playlistId: Long): List<String>

    /**
     * Поиск по тому, что уже лежит в библиотеке.
     *
     * Нужен в самом поиске: то, что человек уже слушал или лайкнул, он
     * ищет чаще всего, и ждать ради этого ответа YouTube незачем.
     */
    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'
        ORDER BY liked DESC, title
        LIMIT :limit
        """,
    )
    suspend fun searchLocal(query: String, limit: Int = 6): List<SongEntity>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :id")
    fun playlistSize(id: Long): Flow<Int>

    @Query(
        """
        SELECT songs.* FROM songs
        JOIN playlist_songs ON songs.id = playlist_songs.songId
        WHERE playlist_songs.playlistId = :playlistId
        ORDER BY playlist_songs.position
        """,
    )
    fun playlistSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToPlaylist(entry: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: String)

    @Transaction
    suspend fun addSongToPlaylist(playlistId: Long, song: SongEntity, now: Long) {
        saveSongMeta(song)
        addToPlaylist(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                position = nextPosition(playlistId),
                addedAt = now,
            ),
        )
    }

    @Insert
    suspend fun addHistory(entry: HistoryEntity): Long

    /** Сколько проиграно в этом включении и засчитано ли оно уже. */
    @Query("UPDATE history SET listenedMs = :listenedMs, counted = :counted WHERE id = :id")
    suspend fun updateListen(id: Long, listenedMs: Long, counted: Boolean)

    /** Длительность от плеера — только если своей у трека ещё нет. */
    @Query("UPDATE songs SET durationMs = :durationMs WHERE id = :id AND (durationMs IS NULL OR durationMs <= 0)")
    suspend fun setDurationMs(id: String, durationMs: Long)

    @Query("UPDATE songs SET downloadError = :error WHERE id = :id")
    suspend fun setDownloadError(id: String, error: String?)

    @Query("SELECT * FROM songs WHERE downloadError IS NOT NULL ORDER BY title")
    fun songsWithDownloadError(): Flow<List<SongEntity>>

    @Query("SELECT id, downloadError FROM songs WHERE downloadError IS NOT NULL")
    fun downloadErrors(): Flow<List<DownloadErrorRow>>

    /** Состав плейлиста ровно как в аккаунте и в его порядке. */
    @Transaction
    suspend fun replacePlaylistSongs(playlistId: Long, songs: List<SongEntity>, now: Long) {
        clearPlaylistSongs(playlistId)
        songs.forEachIndexed { index, song ->
            saveSongMeta(song)
            addToPlaylist(PlaylistSongEntity(playlistId = playlistId, songId = song.id, position = index, addedAt = now))
        }
    }

    /** Порядок своего плейлиста целиком: позиции переписываются подряд. */
    @Transaction
    suspend fun reorderPlaylist(playlistId: Long, songIds: List<String>) {
        songIds.forEachIndexed { index, songId -> setPosition(playlistId, songId, index) }
    }

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun setPosition(playlistId: Long, songId: String, position: Int)

    /** Включения за период — сырьё для графиков. */
    @Query(
        """
        SELECT history.playedAt AS playedAt, history.listenedMs AS listenedMs,
               history.legacy AS legacy, history.counted AS counted,
               songs.durationMs AS durationMs, songs.durationText AS durationText
        FROM history JOIN songs ON songs.id = history.songId
        WHERE history.playedAt >= :since
        """,
    )
    suspend fun listensSince(since: Long): List<ListenRow>

    @Query(
        """
        SELECT songs.* FROM songs
        JOIN history ON songs.id = history.songId
        GROUP BY songs.id
        ORDER BY MAX(history.playedAt) DESC
        LIMIT :limit
        """,
    )
    fun recentSongs(limit: Int = 50): Flow<List<SongEntity>>

    /**
     * Что слушается чаще всего. Сортировка вторым ключом по свежести:
     * иначе два трека с равным счётом менялись бы местами при каждом
     * запросе, и плитки на главной прыгали бы без причины.
     */
    @Query(
        """
        SELECT songs.*, COUNT(history.id) AS plays FROM songs
        JOIN history ON songs.id = history.songId
        WHERE history.counted = 1
        GROUP BY songs.id
        HAVING plays >= :minPlays
        ORDER BY plays DESC, MAX(history.playedAt) DESC
        LIMIT :limit
        """,
    )
    fun mostPlayed(minPlays: Int = 1, limit: Int = 60): Flow<List<SongPlays>>

    /**
     * Кандидаты быстрого набора. Считается каждый запуск, а не только
     * засчитанное прослушивание: трек появляется в наборе с первого раза,
     * и порядок сразу учится — сначала по засчитанным, потом по всем
     * запускам, при равенстве свежее выше.
     */
    @Query(
        """
        SELECT songs.*, COUNT(history.id) AS plays FROM songs
        JOIN history ON songs.id = history.songId
        GROUP BY songs.id
        HAVING plays >= :minPlays
        ORDER BY SUM(history.counted) DESC, plays DESC, MAX(history.playedAt) DESC
        LIMIT :limit
        """,
    )
    fun dialSongs(minPlays: Int = 1, limit: Int = 60): Flow<List<SongPlays>>

    /**
     * Кого слушают чаще всего. По этому списку рекомендации понимают вкус:
     * выдачу YouTube мы потом пересортировываем под него.
     */
    @Query(
        """
        SELECT songs.artist AS artist, COUNT(history.id) AS plays FROM songs
        JOIN history ON songs.id = history.songId
        WHERE songs.artist != '' AND history.counted = 1
        GROUP BY songs.artist
        ORDER BY plays DESC
        LIMIT :limit
        """,
    )
    suspend fun topArtists(limit: Int = 40): List<ArtistPlays>

    @Query("SELECT COUNT(*) FROM history WHERE playedAt >= :since AND counted = 1")
    suspend fun playsSince(since: Long): Int

    /** Есть ли за период записи, посчитанные по старым правилам. */
    @Query("SELECT COUNT(*) FROM history WHERE playedAt >= :since AND legacy = 1")
    suspend fun legacySince(since: Long): Int

    @Query(
        """
        SELECT songs.*, COUNT(history.id) AS plays FROM songs
        JOIN history ON songs.id = history.songId
        WHERE history.playedAt >= :since AND history.counted = 1
        GROUP BY songs.id
        ORDER BY plays DESC, MAX(history.playedAt) DESC
        LIMIT :limit
        """,
    )
    suspend fun topSongsSince(since: Long, limit: Int = 10): List<SongPlays>

    @Query(
        """
        SELECT songs.artist AS artist, COUNT(history.id) AS plays FROM songs
        JOIN history ON songs.id = history.songId
        WHERE history.playedAt >= :since AND songs.artist != '' AND history.counted = 1
        GROUP BY songs.artist
        ORDER BY plays DESC
        LIMIT :limit
        """,
    )
    suspend fun topArtistsSince(since: Long, limit: Int = 10): List<ArtistPlays>

    /** Длительности прослушанного — из них считаются минуты за период. */
    @Query(
        """
        SELECT songs.durationText AS durationText, COUNT(history.id) AS plays FROM songs
        JOIN history ON songs.id = history.songId
        WHERE history.playedAt >= :since
        GROUP BY songs.id
        """,
    )
    suspend fun playedDurations(since: Long): List<DurationPlays>

    @Query("SELECT MIN(playedAt) FROM history")
    suspend fun firstPlayAt(): Long?

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT * FROM pins ORDER BY pinnedAt")
    fun pins(): Flow<List<PinEntity>>

    @Upsert
    suspend fun pin(pin: PinEntity)

    @Query("DELETE FROM pins WHERE key = :key")
    suspend fun unpin(key: String)

    @Query("SELECT COUNT(*) FROM pins WHERE key = :key")
    suspend fun isPinned(key: String): Int
}

/** Первая картинка плейлиста — обложка, когда своей нет. */
data class PlaylistCoverRow(val playlistId: Long, val url: String)

/** Трек и причина, по которой он не скачался. */
data class DownloadErrorRow(val id: String, val downloadError: String)
