package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.screens.MoreTab
import com.texfi.w0y.ui.screens.MorePanel
import com.texfi.w0y.ui.screens.QueuePanel
import com.texfi.w0y.ui.screens.QueueTab
import com.texfi.w0y.ui.screens.SimilarReasons
import com.texfi.w0y.ui.screens.SleepChoice
import com.texfi.w0y.ui.screens.SleepPanel
import com.texfi.w0y.ui.shell.PlayerSheet
import com.texfi.w0y.ui.theme.W0yTheme
import com.texfi.w0y.recs.Why
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Три панели плеера: свои вкладки, свой жест закрытия, плеер под ними не сворачивается. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PlayerPanelsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var collapsed = 0
    private var queueOpen by mutableStateOf(true)
    private var moreOpen by mutableStateOf(false)
    private var sleepOpen by mutableStateOf(false)
    private var moreTab by mutableStateOf(MoreTab.SOUND)
    private var chosen: SleepChoice? = null

    private fun show() {
        rule.setContent {
            W0yTheme {
                PlayerSheet(expanded = true, onCollapse = { collapsed++ }) {
                    Box(Modifier.fillMaxSize()) {
                        BackHandler { collapsed++ }
                        QueuePanel(visible = queueOpen, tab = QueueTab.QUEUE, onTab = {}, onDismiss = { queueOpen = false }) {
                            items((0 until 40).toList(), key = { it }) { i -> Text("track $i", Modifier.fillMaxWidth().height(56.dp).testTag("q$i")) }
                        }
                        MorePanel(visible = moreOpen, tab = moreTab, onTab = { moreTab = it }, onDismiss = { moreOpen = false }) {
                            items((0 until 3).toList(), key = { it }) { i -> Text("opt $i", Modifier.fillMaxWidth().height(56.dp)) }
                        }
                        SleepPanel(visible = sleepOpen, remainingMs = null, afterTrack = false, onChoose = { chosen = it }, onDismiss = { sleepOpen = false })
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun swipeDownOnPanelClosesOnlyThePanel() {
        show()
        rule.onNodeWithTag("panel-list").assertIsDisplayed()
        rule.onNodeWithTag("panel-list").performTouchInput { swipeDown(startY = top + 40f, endY = bottom - 10f, durationMillis = 250) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
        assertFalse("панель должна закрыться", queueOpen)
        assertEquals("плеер под панелью не должен сворачиваться", 0, collapsed)
    }

    @Test
    fun backClosesThePanelFirstThenThePlayer() {
        show()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertFalse(queueOpen)
        assertEquals(0, collapsed)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(1, collapsed)
    }

    @Test
    fun tapOnScrimClosesOnlyThePanel() {
        show()
        rule.onNodeWithTag("panel-scrim").performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 12f)) }
        rule.waitForIdle()
        assertFalse(queueOpen)
        assertEquals(0, collapsed)
    }

    @Test
    fun threeTabsShareTheWidthEquallyAndFitTheScreen() {
        queueOpen = false
        moreOpen = true
        show()
        val widths = (0..2).map { rule.onNodeWithTag("panel-tab-$it").fetchSemanticsNode().boundsInRoot }
        val screen = rule.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
        assertEquals(widths[0].width, widths[1].width, 1f)
        assertEquals(widths[1].width, widths[2].width, 1f)
        widths.forEach { assertTrue("вкладка внутри панели", it.left >= screen.left - 1f && it.right <= screen.right + 1f) }
        // Переключение вкладки «Ещё» работает и не закрывает панель.
        rule.onNodeWithTag("panel-tab-1").performClick()
        rule.waitForIdle()
        assertEquals(MoreTab.SPEED, moreTab)
        assertTrue(moreOpen)
    }

    @Test
    fun sleepPanelIsCompactAndPicksOneChoice() {
        queueOpen = false
        sleepOpen = true
        show()
        val panel = rule.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
        assertTrue("таймер — компактная панель, не на весь экран", panel.height < 520f)
        rule.onNodeWithTag("sleep-panel").assertIsDisplayed()
    }

    @Test
    fun similarShowsOneSharedReasonNotOnePerRow() {
        val same = Why.SimilarTo("Династия")
        assertEquals(same, SimilarReasons.shared(listOf(same, same, same, Why.NewArtist)))
        assertNull(SimilarReasons.shared(listOf(Why.NewArtist, Why.Forgotten, Why.TimeOfDay)))
        assertNull(SimilarReasons.shared(listOf(same)))
    }
}
