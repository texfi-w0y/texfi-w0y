package com.texfi.w0y

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.db.RecSeenEntity
import com.texfi.w0y.data.db.W0yDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** База версии 9 с данными проходит обе миграции сразу: локальные файлы переезжают, история и плейлисты на месте. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration9to11Test {
    @Test
    fun upgradeFromNineKeepsEverything() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val path = context.getDatabasePath("migration-9-11.db")
        path.parentFile?.mkdirs()
        path.delete()
        val name = "com.texfi.w0y.data.db.W0yDatabase/9.json"
        val file = listOf(File("schemas/$name"), File("app/schemas/$name")).first { it.exists() }
        val v9 = JSONObject(file.readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = v9.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { ix ->
                    for (j in 0 until ix.length()) db.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = v9.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 9
            db.execSQL("INSERT INTO playlists (id, name, createdAt) VALUES (1, 'p', 1)")
            db.execSQL("INSERT INTO songs (id, title, artist, explicit, liked, downloadState) VALUES ('yt1', 'T', 'A', 0, 0, 0)")
            db.execSQL("INSERT INTO playlist_songs (playlistId, songId, position, addedAt) VALUES (1, 'yt1', 0, 1)")
            db.execSQL("INSERT INTO playlist_songs (playlistId, songId, position, addedAt) VALUES (1, 'local:7', 1, 1)")
            db.execSQL("INSERT INTO history (songId, playedAt) VALUES ('yt1', 1000)")
        }
        val room =
            Room.databaseBuilder(context, W0yDatabase::class.java, path.absolutePath)
                .addMigrations(*W0yDatabase.ALL).allowMainThreadQueries().build()
        try {
            val dao = room.dao()
            assertEquals("p", dao.playlistOnce(1)!!.name)
            dao.markSeen(listOf(RecSeenEntity("x", 5)))
            assertEquals(1, dao.seenCount())
            val sql = room.openHelper.readableDatabase
            // Локальный файл переехал в свою таблицу, трек из аккаунта остался.
            sql.query("SELECT COUNT(*) FROM local_playlist_items WHERE songId = 'local:7'").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            sql.query("SELECT COUNT(*) FROM playlist_songs").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            // История старая: помечена legacy и засчитана, как до обновления.
            sql.query("SELECT legacy, counted FROM history").use { it.moveToFirst(); assertEquals(1, it.getInt(0)); assertEquals(1, it.getInt(1)) }
        } finally {
            room.close()
            path.delete()
        }
    }
}
