package com.texfi.w0y.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Свои файлы (mp3, wav и т. д.): копируются в папку приложения и дальше
 * живут как обычные треки — плейлисты, быстрый набор, история.
 *
 * Идентификатор `local:<имя файла>` говорит сам за себя: по нему же
 * находится файл на диске, а YouTube-запросы для таких треков не делаются.
 */
object LocalMusic {
    const val PREFIX = "local:"

    fun isLocal(id: String?): Boolean = id?.startsWith(PREFIX) == true

    fun dir(context: Context): File = File(context.filesDir, "local").apply { mkdirs() }

    fun file(context: Context, id: String): File = File(dir(context), id.removePrefix(PREFIX))

    /** «3:47» из миллисекунд. */
    fun durationText(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Название из имени файла: без расширения и с пробелами вместо подчёркиваний. */
    fun titleFromName(name: String): String =
        name.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { name }
}

@Singleton
class LocalMusicRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: W0yDao,
) {
    val songs: Flow<List<SongItem>> = dao.localSongs().map { list -> list.map(SongEntity::toItem) }

    /** Копирует выбранные файлы и заводит треки. Возвращает добавленные; [onEach] зовётся после каждого. */
    suspend fun import(uris: List<Uri>, onEach: (done: Int, total: Int) -> Unit = { _, _ -> }): List<SongItem> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<SongItem>()
            uris.forEachIndexed { index, uri ->
                runCatching { importOne(uri) }
                    .onSuccess { out += it }
                    .onFailure { Timber.w(it, "Файл %s не добавился", uri) }
                onEach(index + 1, uris.size)
            }
            out
        }

    private suspend fun importOne(uri: Uri): SongItem {
        val displayName = displayName(uri)
        val ext = displayName.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 } ?: "mp3"
        val key = UUID.randomUUID().toString().take(12)
        val id = "${LocalMusic.PREFIX}$key.$ext"
        val target = LocalMusic.file(context, id)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "нет доступа к файлу" }
            target.outputStream().use { input.copyTo(it) }
        }
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration = 0L
        var cover: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(target.path)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.embeddedPicture?.let { bytes ->
                val image = File(LocalMusic.dir(context), "$key.jpg")
                image.writeBytes(bytes)
                cover = image.toUri().toString()
            }
        } catch (error: Exception) {
            Timber.w(error, "Теги файла %s не прочитались", displayName)
        } finally {
            runCatching { retriever.release() }
        }
        val song =
            SongItem(
                id = id,
                title = title?.takeIf { it.isNotBlank() } ?: LocalMusic.titleFromName(displayName),
                artist = artist?.takeIf { it.isNotBlank() }.orEmpty(),
                album = album?.takeIf { it.isNotBlank() },
                durationText = duration.takeIf { it > 0 }?.let(LocalMusic::durationText),
                thumbnailUrl = cover,
            )
        dao.upsertSong(SongEntity.from(song).copy(durationMs = duration.takeIf { it > 0 }))
        return song
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "track.mp3"

    /**
     * Сохраняет правку. [newCover] — выбранная картинка (копируется в папку приложения);
     * null оставляет обложку как есть, [clearCover] убирает её.
     */
    suspend fun edit(song: SongItem, title: String, artist: String, album: String, newCover: Uri?, clearCover: Boolean = false): SongItem =
        withContext(Dispatchers.IO) {
            val key = song.id.removePrefix(LocalMusic.PREFIX).substringBeforeLast('.')
            var cover = song.thumbnailUrl
            if (clearCover) {
                File(LocalMusic.dir(context), "$key.jpg").delete()
                cover = null
            }
            if (newCover != null) {
                // Новое имя на каждую правку: иначе кэш картинок покажет старую обложку.
                LocalMusic.dir(context).listFiles { f -> f.name.startsWith("$key.") && f.name.endsWith(".jpg") }?.forEach { it.delete() }
                val image = File(LocalMusic.dir(context), "$key.${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(newCover).use { input ->
                    requireNotNull(input) { "нет доступа к картинке" }
                    image.outputStream().use { input.copyTo(it) }
                }
                cover = image.toUri().toString()
            }
            val clean = title.trim().ifBlank { song.title }
            dao.updateLocal(song.id, clean, artist.trim(), album.trim().ifBlank { null }, cover)
            dao.updatePinnedSong(song.id, clean, artist.trim(), cover)
            song.copy(title = clean, artist = artist.trim(), album = album.trim().ifBlank { null }, thumbnailUrl = cover, artists = emptyList(), artistId = null)
        }

    suspend fun remove(id: String) =
        withContext(Dispatchers.IO) {
            dao.deleteSongFromPlaylists(id)
            dao.deleteSongFromLocalItems(id)
            dao.deleteSongHistory(id)
            dao.deleteSongPins(id)
            dao.deleteSongRow(id)
            val key = id.removePrefix(LocalMusic.PREFIX).substringBeforeLast('.')
            LocalMusic.file(context, id).delete()
            LocalMusic.dir(context).listFiles { f -> f.name.startsWith("$key.") }?.forEach { it.delete() }
        }
}
