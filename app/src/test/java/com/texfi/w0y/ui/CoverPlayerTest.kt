package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.screens.CoverPlayerPage
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Плеер «обложка на весь экран» целиком помещается на экране без прокрутки; значки работают. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CoverPlayerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val clicks = mutableListOf<String>()

    private fun show(heightQualifier: Boolean = false) {
        rule.setContent {
            W0yTheme {
                CoverPlayerPage(
                    song = SongItem(id = "a", title = "MARTINE ROSE", artist = "madk1d, greyrock, tewiq"),
                    isPlaying = false, shuffle = false, repeatActive = false, repeatOne = false,
                    positionMs = 0, durationMs = 200_000, liked = false, lyricsOn = false, positionLabel = "2 of 9",
                    stage = { androidx.compose.foundation.layout.Box(Modifier.testTag("stage").fillMaxSize()) },
                    titleBlock = { m -> androidx.compose.material3.Text("MARTINE ROSE", m) },
                    onCollapse = { clicks += "collapse" }, onCoverTap = {}, onSwipe = {}, onDrag = {}, onDragEnd = {},
                    onShuffle = {}, onPrevious = {}, onToggle = {}, onNext = {}, onRepeat = {},
                    onLike = { clicks += "like" }, onDots = { clicks += "dots" }, onLyrics = { clicks += "lyrics" },
                    onSound = { clicks += "sound" }, onArtist = { clicks += "artist" }, onQueue = { clicks += "queue" },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun everyControlIsOnScreenAndWorks() {
        show()
        val screenHeight = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val queue = rule.onNodeWithContentDescription("Open queue and similar tracks")
        val bounds = queue.fetchSemanticsNode().boundsInRoot
        assertTrue("очередь внутри экрана", bounds.bottom <= screenHeight + 1f)
        val stage = rule.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        assertTrue("обложка занимает заметную часть экрана", stage.height > screenHeight * 0.3f)
        queue.performClick()
        rule.onNodeWithContentDescription("Open speed and sound").performClick()
        rule.onNodeWithContentDescription("LYRICS", ignoreCase = true).performClick()
        assertEquals(listOf("queue", "sound", "lyrics"), clicks)
    }

    /** Маленький экран 360×640: обложка сжимается, управление не уезжает вниз. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun smallScreenStillFitsEverything() {
        show()
        val screenHeight = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val bounds = rule.onNodeWithContentDescription("Open queue and similar tracks").fetchSemanticsNode().boundsInRoot
        assertTrue("очередь внутри экрана ${bounds.bottom} / $screenHeight", bounds.bottom <= screenHeight + 1f)
        val stage = rule.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        assertTrue("обложка не схлопнулась: ${stage.height}", stage.height > 150f)
    }
}
