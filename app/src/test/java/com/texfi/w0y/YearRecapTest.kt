package com.texfi.w0y

import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YearRecap
import com.texfi.w0y.data.YearRecapMath
import com.texfi.w0y.data.db.ArtistPlays
import com.texfi.w0y.data.db.ListenRow
import com.texfi.w0y.share.RecapCardRenderer
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Итоги года: серия дней, рекордный день и месяц, границы года, карточка рисуется. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YearRecapTest {
    private val zone = ZoneId.of("Europe/Warsaw")

    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun row(date: LocalDate, hour: Int, minutes: Int) =
        ListenRow(at(date, hour), minutes * 60_000L, legacy = false, counted = true, durationMs = null, durationText = null)

    @Test
    fun streakFindsLongestChain() {
        val d = LocalDate.of(2026, 3, 1)
        val days = setOf(d, d.plusDays(1), d.plusDays(2), d.plusDays(5), d.plusDays(6))
        assertEquals(3, YearRecapMath.streak(days))
        assertEquals(0, YearRecapMath.streak(emptySet()))
    }

    @Test
    fun computeCountsOnlyThisYear() {
        val rows =
            listOf(
                row(LocalDate.of(2025, 12, 31), 23, 50), // прошлый год — мимо
                row(LocalDate.of(2026, 2, 10), 21, 30),
                row(LocalDate.of(2026, 2, 11), 21, 40),
                row(LocalDate.of(2026, 2, 12), 9, 10),
                row(LocalDate.of(2026, 7, 1), 21, 25),
            )
        val r = YearRecapMath.compute(2026, rows, plays = 4, topSongs = emptyList(), topArtists = emptyList(), newArtists = 2, zone = zone)
        assertEquals(105, r.minutes)
        assertEquals(3, r.streakDays)
        assertEquals(4, r.activeDays)
        assertEquals(2, r.bestMonth)
        assertEquals(80, r.bestMonthMinutes)
        assertEquals(LocalDate.of(2026, 2, 11), r.bestDay)
        assertEquals(21, r.peakHour)
    }

    @Test
    fun emptyYearIsEmpty() {
        val r = YearRecapMath.compute(2026, emptyList(), 0, emptyList(), emptyList(), 0, zone)
        assertTrue(r.isEmpty)
        assertNull(r.peakHour)
    }

    @Test
    fun cardRendersWithoutCovers() = runBlocking {
        val recap =
            YearRecap(
                year = 2026, minutes = 12_345, plays = 900,
                topSongs = (1..5).map { SongItem(id = "s$it", title = "Очень длинное название трека номер $it", artist = "VILLIAN") to (50 - it) },
                topArtists = listOf(ArtistPlays("VILLIAN", 120), ArtistPlays("madk1d", 80)),
                peakHour = 22, streakDays = 12, activeDays = 200,
            )
        val file = RecapCardRenderer(ApplicationProvider.getApplicationContext()).render(recap)
        assertTrue(file.exists() && file.length() > 10_000)
    }
}
