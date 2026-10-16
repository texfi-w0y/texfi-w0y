package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.screens.CoverPlayerPage
import com.texfi.w0y.ui.shell.PlayerSheet
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Раскладка «обложка»: всё на экране без прокрутки, тяга вниз сворачивает плеер. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CoverPlayerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var expanded by mutableStateOf(true)
    private var collapses = 0

    private fun block(tag: String, h: Int) = @androidx.compose.runtime.Composable { Box(Modifier.fillMaxWidth().height(h.dp).testTag(tag)) }

    private fun show() {
        rule.setContent {
            W0yTheme {
                PlayerSheet(expanded = expanded, onCollapse = { collapses++; expanded = false }) {
                    CoverPlayerPage(
                        song = SongItem(id = "a", title = "Катюха", artist = "whitek3d"),
                        isPlaying = true, positionMs = 30_000, durationMs = 120_000,
                        stage = { Box(Modifier.testTag("stage").fillMaxSize()) },
                        header = block("header", 56),
                        titleBlock = { m -> Text("Катюха", m) },
                        actions = block("actions", 48),
                        transport = block("transport", 88),
                        plate = block("plate", 52),
                        onCoverTap = {}, onSwipe = {}, onDrag = {}, onDragEnd = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun everythingFitsWithoutScrolling() {
        show()
        val h = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val plate = rule.onNodeWithTag("plate").fetchSemanticsNode().boundsInRoot
        assertTrue("плашка очереди на экране: ${plate.bottom} / $h", plate.bottom <= h + 1f)
        assertTrue("обложка заметная", rule.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot.height > h * 0.25f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun smallScreenStillFits() {
        show()
        val h = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        assertTrue(rule.onNodeWithTag("plate").fetchSemanticsNode().boundsInRoot.bottom <= h + 1f)
    }

    @Test
    fun swipeDownCollapsesThePlayer() {
        show()
        rule.onNodeWithTag("stage").performTouchInput { swipeDown(durationMillis = 150) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        assertEquals(1, collapses)
    }
}
