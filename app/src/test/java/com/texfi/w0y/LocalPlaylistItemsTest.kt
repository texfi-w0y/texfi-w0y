package com.texfi.w0y

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.PlaylistMerge
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Свои файлы в плейлистах: лежат в отдельной таблице и переживают всё, что
 * синхронизация делает с составом из аккаунта.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalPlaylistItemsTest {
    private fun item(id: String) = SongItem(id = id, title = id, artist = "a")

    private fun song(id: String) = SongEntity(id = id, title = id, artist = "a")

    private fun open(name: String): Pair<W0yDatabase, File> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        path.delete()
        val db =
            Room
                .databaseBuilder(context, W0yDatabase::class.java, path.absolutePath)
                .allowMainThreadQueries()
                .build()
        return db to path
    }

    @Test
    fun mergeKeepsSharedPositionScale() {
        val merged =
            PlaylistMerge.merge(
                remote = listOf(item("r1") to 0, item("r2") to 2),
                local = listOf(item("local:x.mp3") to 1, item("local:y.mp3") to 2),
            )
        // Равные позиции: сначала аккаунт, потом свои.
        assertEquals(listOf("r1", "local:x.mp3", "r2", "local:y.mp3"), merged.map { it.id })
    }

    @Test
    fun resyncDoesNotTouchLocalItems() = runBlocking {
        val (db, path) = open("local-items-resync.db")
        try {
            val dao = db.dao()
            val pl = dao.createPlaylist(PlaylistEntity(name = "p", createdAt = 1, remoteId = "PL1"))
            dao.replacePlaylistSongs(pl, listOf(song("r1"), song("r2")), now = 10)
            dao.addLocalSongToPlaylist(pl, song("local:a.mp3"), now = 11)

            // Сервер прислал совсем другой состав — свой файл остаётся.
            dao.replacePlaylistSongs(pl, listOf(song("r3")), now = 20)
            dao.replacePlaylistSongs(pl, emptyList(), now = 30)
            assertEquals(1, dao.localItemCount(pl))
            assertEquals(listOf("local:a.mp3"), dao.localPlaylistItems(pl).first().map { it.song.id })
        } finally {
            db.close()
            path.delete()
        }
    }

    @Test
    fun detachKeepsLocalItemsAndStopsPushing() = runBlocking {
        val (db, path) = open("local-items-detach.db")
        try {
            val dao = db.dao()
            val pl = dao.createPlaylist(PlaylistEntity(name = "p", createdAt = 1, remoteId = "PL1"))
            dao.addLocalSongToPlaylist(pl, song("local:a.mp3"), now = 11)

            dao.detachFromAccount(pl)

            val after = dao.playlistOnce(pl)!!
            assertEquals(null, after.remoteId)
            assertTrue(after.detached)
            assertEquals(1, dao.localItemCount(pl))
            // Оторванный плейлист не считается «новым локальным» и не уйдёт в аккаунт заново.
            assertFalse(dao.localOnlyPlaylists().any { it.id == pl })
        } finally {
            db.close()
            path.delete()
        }
    }

    @Test
    fun deleteFileRemovesOnlyThatItem() = runBlocking {
        val (db, path) = open("local-items-delete.db")
        try {
            val dao = db.dao()
            val a = dao.createPlaylist(PlaylistEntity(name = "a", createdAt = 1))
            val b = dao.createPlaylist(PlaylistEntity(name = "b", createdAt = 2))
            dao.addLocalSongToPlaylist(a, song("local:1.mp3"), now = 1)
            dao.addLocalSongToPlaylist(a, song("local:2.mp3"), now = 2)
            dao.addLocalSongToPlaylist(b, song("local:1.mp3"), now = 3)

            dao.removeLocalItem(a, "local:1.mp3")

            assertEquals(listOf("local:2.mp3"), dao.localPlaylistItems(a).first().map { it.song.id })
            assertEquals(listOf("local:1.mp3"), dao.localPlaylistItems(b).first().map { it.song.id })
        } finally {
            db.close()
            path.delete()
        }
    }

    @Test
    fun orderSurvivesAppendAfterMixedItems() = runBlocking {
        val (db, path) = open("local-items-order.db")
        try {
            val dao = db.dao()
            val pl = dao.createPlaylist(PlaylistEntity(name = "p", createdAt = 1))
            dao.replacePlaylistSongs(pl, listOf(song("r1"), song("r2")), now = 1)
            dao.addLocalSongToPlaylist(pl, song("local:a.mp3"), now = 2)
            dao.addLocalSongToPlaylist(pl, song("local:b.mp3"), now = 3)
            assertEquals(2, dao.localPlaylistItems(pl).first().first { it.song.id == "local:a.mp3" }.pos)
            assertEquals(3, dao.localPlaylistItems(pl).first().first { it.song.id == "local:b.mp3" }.pos)
        } finally {
            db.close()
            path.delete()
        }
    }

    @Test
    fun migration9to10MovesLocalRowsOut() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val path = context.getDatabasePath("migration-9-10.db")
        path.parentFile?.mkdirs()
        path.delete()

        val name = "com.texfi.w0y.data.db.W0yDatabase/9.json"
        val file = listOf(File("schemas/$name"), File("app/schemas/$name")).first { it.exists() }
        val v9 = JSONObject(file.readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = v9.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = v9.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 9
            for (id in listOf("r1", "local:a.mp3")) {
                db.insert(
                    "songs",
                    null,
                    ContentValues().apply {
                        put("id", id)
                        put("title", id)
                        put("artist", "a")
                        put("explicit", 0)
                        put("liked", 0)
                        put("downloadState", 0)
                    },
                )
            }
            db.insert(
                "playlists",
                null,
                ContentValues().apply {
                    put("id", 1)
                    put("name", "p")
                    put("createdAt", 1)
                },
            )
            listOf("r1" to 0, "local:a.mp3" to 1).forEach { (song, pos) ->
                db.insert(
                    "playlist_songs",
                    null,
                    ContentValues().apply {
                        put("playlistId", 1)
                        put("songId", song)
                        put("position", pos)
                        put("addedAt", 5)
                    },
                )
            }
        }

        val room =
            Room
                .databaseBuilder(context, W0yDatabase::class.java, path.absolutePath)
                .addMigrations(*W0yDatabase.ALL)
                .allowMainThreadQueries()
                .build()
        try {
            val dao = room.dao()
            assertEquals(listOf("r1"), dao.playlistItems(1).first().map { it.song.id })
            val local = dao.localPlaylistItems(1).first()
            assertEquals(listOf("local:a.mp3"), local.map { it.song.id })
            assertEquals(1, local.single().pos)
            assertFalse(dao.playlistOnce(1)!!.detached)
        } finally {
            room.close()
            path.delete()
        }
    }
}
