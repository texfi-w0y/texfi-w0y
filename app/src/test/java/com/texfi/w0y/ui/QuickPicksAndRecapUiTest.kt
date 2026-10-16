package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YearRecap
import com.texfi.w0y.data.db.ArtistPlays
import com.texfi.w0y.ui.components.QuickPicksShelf
import com.texfi.w0y.ui.screens.YearRecapScreen
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Лента «быстрый выбор» строками по четыре и экран итогов года. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class QuickPicksAndRecapUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val songs = (0 until 12).map { SongItem(id = "s$it", title = "Трек $it", artist = "Артист", plays = "1M plays", explicit = it % 2 == 0) }

    @Test
    fun quickPicksRowsInColumnsOfFour() {
        val played = mutableListOf<Int>()
        rule.setContent { W0yTheme { QuickPicksShelf("Quick picks", songs, onPlay = { played += it }) } }
        val first = rule.onNodeWithTag("quick-pick-0").fetchSemanticsNode().boundsInRoot
        val fourth = rule.onNodeWithTag("quick-pick-3").fetchSemanticsNode().boundsInRoot
        val fifth = rule.onNodeWithTag("quick-pick-4").fetchSemanticsNode().boundsInRoot
        assertEquals("в колонке одна под другой", first.left, fourth.left, 1f)
        assertTrue("пятая — в следующей колонке справа", fifth.left > first.right - 1f)
        assertEquals(first.top, fifth.top, 1f)
        rule.onNodeWithTag("quick-pick-2").performClick()
        rule.onNodeWithTag("quick-picks-play-all").performClick()
        rule.waitForIdle()
        assertEquals(listOf(2, 0), played)
    }

    @Test
    fun recapPagesAndShare() {
        var shared = 0
        val recap =
            YearRecap(
                year = 2026, minutes = 4321, plays = 500,
                topSongs = songs.take(5).map { it to 10 },
                topArtists = listOf(ArtistPlays("VILLIAN", 40), ArtistPlays("madk1d", 20)),
                peakHour = 21, streakDays = 7, activeDays = 120,
            )
        rule.setContent { W0yTheme { YearRecapScreen(recap, onClose = {}, onShare = { shared++ }) } }
        rule.mainClock.advanceTimeBy(3000)
        rule.onNodeWithTag("recap-minutes").assertIsDisplayed()
        repeat(4) {
            rule.onNodeWithTag("year-recap").performTouchInput { swipeLeft() }
            rule.mainClock.advanceTimeBy(1500)
        }
        rule.onNodeWithTag("recap-share").performClick()
        rule.waitForIdle()
        assertEquals(1, shared)
    }
}
