package com.texfi.w0y.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.db.HistoryEntity
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDao
import com.texfi.w0y.data.db.W0yDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Наполняет приложение «жилыми» данными: лайки, плейлисты, история, закрепы.
 * Только bench-сборка, только локальная база: через DAO, не через репозиторий,
 * чтобы ничего не уходило в аккаунт YouTube.
 *
 *   adb shell am broadcast -n com.texfi.w0y.bench/com.texfi.w0y.demo.DemoSeedReceiver \
 *       -a com.texfi.w0y.DEMO_SEED [--ez dry true] [--es seed <base64 json>]
 *
 * Описание данных лежит в assets/demo_seed.json (или приходит в extra «seed»).
 * С --ez dry true ничего не пишет, только печатает в logcat (тег DemoSeed), какие
 * треки нашлись по запросам, — чтобы выбрать точные id.
 */
class DemoSeedReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun dao(): W0yDao

        fun database(): W0yDatabase

        fun youtube(): YouTubeRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val dry = intent.getBooleanExtra("dry", false)
        val override = intent.getStringExtra("seed")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = override?.let { String(Base64.decode(it, Base64.DEFAULT)) }
                    ?: app.assets.open("demo_seed.json").bufferedReader().use { it.readText() }
                seed(EntryPointAccessors.fromApplication(app, Deps::class.java), JSONObject(json), dry)
            } catch (e: Throwable) {
                Log.e(TAG, "seed failed", e)
            }
        }
    }

    private suspend fun seed(deps: Deps, spec: JSONObject, dry: Boolean) {
        val dao = deps.dao()
        val yt = deps.youtube()
        val now = System.currentTimeMillis()

        // 1. Треки: по id, если он указан, иначе первый подходящий результат поиска.
        val songs = LinkedHashMap<String, SongItem>()
        val tracks = spec.getJSONArray("tracks")
        for (i in 0 until tracks.length()) {
            val t = tracks.getJSONObject(i)
            val key = t.getString("key")
            val id = t.optString("id")
            val q = t.optString("q")
            val found: SongItem? =
                if (id.isNotBlank()) {
                    yt.songDetails(id)
                } else {
                    val hits = yt.searchSongs(q)
                    if (dry) hits.take(6).forEach { Log.i(TAG, "[$key] candidate ${it.id} | ${it.artist} - ${it.title} | ${it.album} | ${it.durationText}") }
                    val hint = t.optString("artist")
                    hits.firstOrNull { hint.isBlank() || it.artist.contains(hint, ignoreCase = true) } ?: hits.firstOrNull()
                }
            if (found == null) {
                Log.w(TAG, "[$key] not found")
                continue
            }
            Log.i(TAG, "[$key] -> ${found.id} | ${found.artist} - ${found.title} | ${found.durationText}")
            songs[key] = found
        }
        if (dry) return

        // 2. Сброс прежнего демо: история, закрепы, лайки и плейлисты — только локально.
        if (spec.optBoolean("reset", true)) {
            val db = deps.database().openHelper.writableDatabase
            db.execSQL("DELETE FROM history")
            db.execSQL("DELETE FROM pins")
            db.execSQL("DELETE FROM playlist_songs")
            db.execSQL("DELETE FROM playlists")
            db.execSQL("UPDATE songs SET liked = 0, likedAt = NULL")
        }

        for ((_, song) in songs) dao.saveSongMeta(SongEntity.from(song))

        // 3. Лайки: свежие сверху.
        spec.optJSONArray("likes")?.let { likes ->
            for (i in 0 until likes.length()) {
                val song = songs[likes.getString(i)] ?: continue
                dao.setLiked(song.id, true, now - i * 3_600_000L)
            }
        }

        // 4. Плейлисты со своим цветом, значком и описанием.
        spec.optJSONArray("playlists")?.let { lists ->
            for (i in 0 until lists.length()) {
                val p = lists.getJSONObject(i)
                val id = dao.createPlaylist(
                    PlaylistEntity(
                        name = p.getString("name"),
                        createdAt = now - (lists.length() - i) * 86_400_000L,
                        accent = if (p.has("accent")) p.getLong("accent").toInt() else null,
                        glyph = p.optString("glyph").ifBlank { null },
                        note = p.optString("note").ifBlank { null },
                    ),
                )
                val items = p.getJSONArray("tracks")
                for (j in 0 until items.length()) {
                    val song = songs[items.getString(j)] ?: continue
                    dao.addSongToPlaylist(id, SongEntity.from(song), now - (items.length() - j) * 600_000L)
                }
            }
        }

        // 5. История: сколько раз и как давно слушали. Она кормит «Недавнее», быстрый набор и статистику.
        spec.optJSONArray("history")?.let { hist ->
            for (i in 0 until hist.length()) {
                val h = hist.getJSONObject(i)
                val song = songs[h.getString("track")] ?: continue
                val count = h.optInt("count", 1)
                val minutesAgo = h.optLong("minutesAgo", 10L)
                for (k in 0 until count) {
                    dao.addHistory(
                        HistoryEntity(
                            songId = song.id,
                            playedAt = now - minutesAgo * 60_000L - k * 26L * 3_600_000L,
                            listenedMs = h.optLong("listenedMs", 170_000L),
                            counted = true,
                            legacy = false,
                        ),
                    )
                }
            }
        }

        // 6. Закреплённые плитки быстрого набора.
        spec.optJSONArray("pins")?.let { pins ->
            for (i in 0 until pins.length()) {
                val song = songs[pins.getString(i)] ?: continue
                dao.pin(
                    PinEntity(
                        key = PinEntity.key(PinEntity.KIND_SONG, song.id),
                        kind = PinEntity.KIND_SONG,
                        targetId = song.id,
                        title = song.title,
                        subtitle = song.artist,
                        thumbnailUrl = song.thumbnailUrl,
                        pinnedAt = now - i * 60_000L,
                    ),
                )
            }
        }
        Log.i(TAG, "done: ${songs.size} tracks")
    }

    private companion object {
        const val TAG = "DemoSeed"
    }
}
