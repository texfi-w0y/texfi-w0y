package com.texfi.w0y

import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.screens.HistoryDays
import com.texfi.w0y.ui.screens.durationSeconds
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Разбивка истории по дням и разбор длительности для «Моих файлов». */
class HistoryDaysTest {
    private val zone = ZoneOffset.UTC
    private fun song(id: String) = SongItem(id = id, title = id, artist = "a")
    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(zone).toEpochMilli()

    @Test
    fun groupsConsecutiveDaysAndKeepsOrder() {
        val d1 = LocalDate.of(2026, 10, 24)
        val d0 = d1.minusDays(1)
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))
        val times = mapOf("a" to at(d1, 20), "b" to at(d1, 9), "c" to at(d0, 23))
        val days = HistoryDays.group(songs, times, zone)
        assertEquals(listOf(d1, d0, null), days.map { it.day })
        assertEquals(listOf("a", "b"), days[0].songs.map { it.id })
        assertEquals(listOf("c"), days[1].songs.map { it.id })
        assertEquals(listOf("d"), days[2].songs.map { it.id })
    }

    @Test
    fun emptyHistoryHasNoDays() {
        assertEquals(0, HistoryDays.group(emptyList(), emptyMap(), zone).size)
    }

    @Test
    fun parsesDurations() {
        assertEquals(199, durationSeconds("3:19"))
        assertEquals(3731, durationSeconds("1:02:11"))
        assertEquals(0, durationSeconds(null))
        assertEquals(0, durationSeconds("live"))
    }
}
