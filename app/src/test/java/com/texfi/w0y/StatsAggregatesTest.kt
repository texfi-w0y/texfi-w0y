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
}
