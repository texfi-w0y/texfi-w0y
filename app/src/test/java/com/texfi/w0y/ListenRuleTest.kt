package com.texfi.w0y

import com.texfi.w0y.data.ListenRule
import com.texfi.w0y.data.ListenSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Правило прослушивания: 30 секунд или половина трека, и только реально проигранное время. */
class ListenRuleTest {
    private fun play(session: ListenSession, fromMs: Long, toMs: Long, startWall: Long = 0L): Long {
        var wall = startWall
        var pos = fromMs
        session.sample(pos, wall, playing = true)
        while (pos < toMs) {
            pos = minOf(toMs, pos + 1000)
            wall += 1000
            session.sample(pos, wall, playing = true)
        }
        return wall
    }

    @Test
    fun thirtySecondsCountForNormalTrack() {
        assertEquals(30_000L, ListenRule.requiredMs(180_000L))
        val session = ListenSession("a", 180_000L)
        play(session, 0, 29_000)
        assertFalse(session.counted)
        play(session, 29_000, 31_000, startWall = 100_000)
        assertTrue(session.counted)
    }

    @Test
    fun halfOfVeryShortTrackCounts() {
        assertEquals(10_000L, ListenRule.requiredMs(20_000L))
        val session = ListenSession("a", 20_000L)
        play(session, 0, 11_000)
        assertTrue(session.counted)
    }

    @Test
    fun unknownDurationNeedsThirtySeconds() {
        assertEquals(ListenRule.THRESHOLD_MS, ListenRule.requiredMs(null))
        assertEquals(ListenRule.THRESHOLD_MS, ListenRule.requiredMs(20 * 60_000L))
    }

    @Test
    fun seekingToTheEndDoesNotCount() {
        val session = ListenSession("a", 200_000L)
        session.sample(0, 0, playing = true)
        session.sample(1_000, 1_000, playing = true)
        // Скачок позиции на три минуты за секунду — перемотка, не прослушивание.
        session.sample(190_000, 2_000, playing = true)
        session.sample(191_000, 3_000, playing = true)
        assertEquals(2_000L, session.listenedMs)
        assertFalse(session.counted)
    }

    @Test
    fun explicitSeekResetsBaseline() {
        val session = ListenSession("a", 200_000L)
        play(session, 0, 10_000)
        session.seeked(150_000, 10_500)
        session.sample(151_000, 11_500, playing = true)
        assertEquals(11_000L, session.listenedMs)
    }

    @Test
    fun earlySkipDoesNotCount() {
        val session = ListenSession("a", 200_000L)
        play(session, 0, 15_000)
        assertFalse(session.counted)
    }

    @Test
    fun pausedTimeIsNotCounted() {
        val session = ListenSession("a", 200_000L)
        session.sample(0, 0, playing = true)
        session.sample(1_000, 1_000, playing = true)
        session.sample(1_000, 60_000, playing = false)
        session.sample(2_000, 61_000, playing = true)
        assertEquals(2_000L, session.listenedMs)
    }

    @Test
    fun fasterSpeedIsPlausible() {
        val session = ListenSession("a", 200_000L)
        session.sample(0, 0, playing = true, speed = 2f)
        session.sample(2_000, 1_000, playing = true, speed = 2f)
        assertEquals(2_000L, session.listenedMs)
    }

    @Test
    fun repeatIsSeparateSession() {
        // Повтор — новое включение: каждое засчитывается отдельно, по одному разу.
        val first = ListenSession("a", 100_000L)
        var crossed = 0
        var wall = 0L
        var pos = 0L
        first.sample(pos, wall, true)
        while (pos < 100_000) {
            pos += 1000
            wall += 1000
            if (first.sample(pos, wall, true)) crossed++
        }
        val second = ListenSession("a", 100_000L)
        pos = 0
        second.sample(pos, wall, true)
        while (pos < 100_000) {
            pos += 1000
            wall += 1000
            if (second.sample(pos, wall, true)) crossed++
        }
        assertEquals(2, crossed)
    }
}
