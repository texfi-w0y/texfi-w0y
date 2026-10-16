package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.texfi.w0y.ui.shell.PlayerSheet
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Шторка плеера не застревает на полпути: либо закрыта целиком, либо вернулась. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PlayerSheetCloseTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var expanded by mutableStateOf(true)
    private var collapses = 0

    private fun show() {
        rule.setContent {
            W0yTheme {
                PlayerSheet(expanded = expanded, onCollapse = { collapses++; expanded = false }) {
                    LazyColumn(Modifier.fillMaxSize().testTag("player")) { items((0 until 30).toList()) { Text("row $it") } }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun fastSwipeDownClosesEverythingNoBlackLeftover() {
        show()
        rule.onNodeWithTag("player").performTouchInput { swipeDown(durationMillis = 120) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(1, collapses)
        rule.onNodeWithTag("player").assertDoesNotExistOrHidden()
    }

    @Test
    fun shortSlowDragReturnsToFullyOpen() {
        show()
        rule.onNodeWithTag("player").performTouchInput { swipeDown(startY = top + 100f, endY = top + 160f, durationMillis = 600) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertTrue("не должен закрыться", expanded)
        rule.onNodeWithTag("player").assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistOrHidden() {
    val nodes = fetchSemanticsNodeOrNull()
    if (nodes != null) assertTrue("плеер должен исчезнуть", false)
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.fetchSemanticsNodeOrNull() = runCatching { fetchSemanticsNode() }.getOrNull()
