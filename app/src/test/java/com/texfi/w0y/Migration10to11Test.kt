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

/** База версии 10 открывается новой схемой: таблица подборок появляется, остальное на месте. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration10to11Test {
    @Test
    fun seenTableAppearsAndOldDataStays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val path = context.getDatabasePath("migration-10-11.db")
        path.parentFile?.mkdirs()
        path.delete()
        val name = "com.texfi.w0y.data.db.W0yDatabase/10.json"
        val file = listOf(File("schemas/$name"), File("app/schemas/$name")).first { it.exists() }
        val v10 = JSONObject(file.readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = v10.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { ix ->
                    for (j in 0 until ix.length()) db.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = v10.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 10
            db.execSQL("INSERT INTO playlists (id, name, createdAt, detached) VALUES (1, 'p', 1, 0)")
        }
        val room =
            Room.databaseBuilder(context, W0yDatabase::class.java, path.absolutePath)
                .addMigrations(*W0yDatabase.ALL).allowMainThreadQueries().build()
        try {
            val dao = room.dao()
            assertEquals("p", dao.playlistOnce(1)!!.name)
            dao.markSeen(listOf(RecSeenEntity("x", 5)))
            assertEquals(1, dao.seenCount())
        } finally {
            room.close()
            path.delete()
        }
    }
}
