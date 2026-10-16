package com.texfi.w0y

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.StatsPeriod
import com.texfi.w0y.data.StatsRepository
import com.texfi.w0y.data.db.HistoryEntity
import com.texfi.w0y.data.db.ListenRow
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDatabase
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsAggregatesTest {
    private val utc = ZoneId.of("UTC")

    private fun at(hour: Int, daysAgo: Long = 0): Long =
        ZonedDateTime.now(utc).minusDays(daysAgo).withHour(hour).withMinute(30).toInstant().toEpochMilli()

    @Test
    fun hourlyGroupsMinutesByHourOfDay() {
        val rows =
            listOf(
                ListenRow(at(9), 120_000, legacy = false, counted = true, durationMs = 200_000, durationText = null),
                ListenRow(at(9, 1), 60_000, legacy = false, counted = false, durationMs = null, durationText = null),
                ListenRow(at(22), 180_000, legacy = false, counted = true, durationMs = null, durationText = null),
            )
        val hours = StatsRepository.hourly(rows, utc)
        assertEquals(24, hours.size)
        assertEquals(3, hours[9])
        assertEquals(3, hours[22])
        assertEquals(6, hours.sum())
    }

    @Test
    fun emptyIsHonestAndShortListeningStillShowsMinutes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, W0yDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            val repo = StatsRepository(dao)
            assertTrue(repo.stats(StatsPeriod.WEEK).let { it.loaded && it.isEmpty })

            dao.saveSongMeta(SongEntity(id = "s1", title = "t", artist = "A"))
            // Слушал 40 секунд и ушёл: не прослушивание, но минуты не должны пропадать.
            dao.addHistory(HistoryEntity(songId = "s1", playedAt = System.currentTimeMillis(), listenedMs = 40_000, counted = false))
            val short = repo.stats(StatsPeriod.WEEK)
            assertFalse(short.isEmpty)
            assertEquals(0, short.plays)
        } finally {
            db.close()
        }
    }

    @Test
    fun newArtistsAndReset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, W0yDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            val repo = StatsRepository(dao)
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            dao.saveSongMeta(SongEntity(id = "old", title = "o", artist = "Old"))
            dao.saveSongMeta(SongEntity(id = "new", title = "n", artist = "Fresh"))
            // «Old» слушали давно и сегодня; «Fresh» впервые на этой неделе.
            dao.addHistory(HistoryEntity(songId = "old", playedAt = now - 40 * day, listenedMs = 200_000, counted = true))
            dao.addHistory(HistoryEntity(songId = "old", playedAt = now, listenedMs = 200_000, counted = true))
            dao.addHistory(HistoryEntity(songId = "new", playedAt = now, listenedMs = 200_000, counted = true))

            val week = repo.stats(StatsPeriod.WEEK)
            assertEquals(1, week.newArtists)
            assertEquals(2, week.plays)

            repo.reset()
            val after = repo.stats(StatsPeriod.ALL)
            assertTrue(after.isEmpty)
            // Сами записи остались: «недавнее» не пустеет.
            assertEquals(2, dao.recentSongs().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun backfillRestoresListensFromHistoryTimestamps() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, W0yDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            val repo = StatsRepository(dao)
            val now = System.currentTimeMillis()
            listOf("a", "b", "c", "d").forEach { dao.saveSongMeta(SongEntity(id = it, title = it, artist = "Artist $it", durationMs = 200_000)) }
            // Три трека включали подряд с интервалами 3 мин, 20 с и 3 мин; последний — идёт сейчас.
            dao.addHistory(HistoryEntity(songId = "a", playedAt = now - 400_000))
            dao.addHistory(HistoryEntity(songId = "b", playedAt = now - 220_000))
            dao.addHistory(HistoryEntity(songId = "c", playedAt = now - 200_000))
            dao.addHistory(HistoryEntity(songId = "d", playedAt = now - 5_000))
            // До восстановления статистика пуста, хотя история есть.
            assertTrue(repo.stats(StatsPeriod.WEEK).isEmpty)

            val updated = repo.backfill()
            assertEquals(3, updated)
            val after = repo.stats(StatsPeriod.WEEK)
            assertFalse(after.isEmpty)
            assertEquals(2, after.plays) // a: 180 с и c: 195 с засчитаны, b: 20 с — пропущен (порог 30 с)
            assertTrue(after.minutes >= 6)
            // Повторный запуск ничего не меняет.
            assertEquals(0, repo.backfill())
        } finally {
            db.close()
        }
    }

    @Test
    fun observeEmitsAgainWhenListeningIsWritten() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, W0yDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            val repo = StatsRepository(dao)
            dao.saveSongMeta(SongEntity(id = "s1", title = "t", artist = "A"))
            val id = dao.addHistory(HistoryEntity(songId = "s1", playedAt = System.currentTimeMillis()))
            val first = kotlinx.coroutines.withTimeout(5_000) { repo.observe(StatsPeriod.WEEK).first() }
            assertTrue(first.isEmpty)
            val next =
                async(kotlinx.coroutines.Dispatchers.Default) {
                    kotlinx.coroutines.withTimeout(5_000) { repo.observe(StatsPeriod.WEEK).drop(1).first() }
                }
            kotlinx.coroutines.delay(300)
            dao.updateListen(id, 45_000, true)
            val updated = next.await()
            assertEquals(1, updated.plays)
        } finally {
            db.close()
        }
    }
}
