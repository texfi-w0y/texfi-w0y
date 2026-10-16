package com.texfi.w0y.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.components.IslandLayout
import com.texfi.w0y.ui.components.bottomContentPadding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Остров стоит в потоке разметки: последняя строка любого списка обязана остаться над ним
 * и принимать нажатия. Проверяется и с мини-плеером (высокий остров), и без него.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private var islandTop = 0f
    private var tapped = -1

    private fun host(count: Int, islandHeight: () -> Int) {
        rule.setContent {
            IslandLayout(
                Modifier.fillMaxSize(),
                island = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(islandHeight().dp)
                            .border(1.dp, androidx.compose.ui.graphics.Color.Gray)
                            .testTag("island")
                            .onGloballyPositioned { islandTop = it.boundsInRoot().top },
                    )
                },
            ) {
                LazyColumn(Modifier.testTag("list"), contentPadding = bottomContentPadding()) {
                    items((0 until count).toList(), key = { it }) { i ->
                        Text("row $i", Modifier.fillMaxWidth().height(56.dp).testTag("row$i").clickable { tapped = i })
                    }
                }
            }
        }
    }

    private fun assertLastAboveIsland(count: Int) {
        rule.onNodeWithTag("list").performScrollToIndex(count - 1)
        rule.waitForIdle()
        val last = rule.onNodeWithTag("row${count - 1}")
        last.assertIsDisplayed()
        val bounds = last.fetchSemanticsNode().boundsInRoot
        assertTrue("низ последней строки ${bounds.bottom} должен быть над островом $islandTop", bounds.bottom <= islandTop + 0.5f)
        tapped = -1
        last.performClick()
        assertEquals(count - 1, tapped)
    }

    @Test
    fun lastRowAboveTallIsland_miniPlayerOn() {
        host(60) { 132 }
        assertLastAboveIsland(60)
    }

    @Test
    fun lastRowAboveLowIsland_miniPlayerOff() {
        host(60) { 64 }
        assertLastAboveIsland(60)
    }

    @Test
    fun islandHeightChangeKeepsLastRowVisible() {
        var h by mutableStateOf(64)
        host(80) { h }
        assertLastAboveIsland(80)
        h = 132 // мини-плеер появился
        rule.waitForIdle()
        assertLastAboveIsland(80)
        h = 64
        rule.waitForIdle()
        assertLastAboveIsland(80)
    }

    /** Старая схема: остров поверх контента. Список без своего отступа уходит под него. */
    @Test
    fun overlayIslandCoversListThatForgotInset_theOldCause() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.testTag("list")) {
                    items((0 until 60).toList(), key = { it }) { i ->
                        Text("row $i", Modifier.fillMaxWidth().height(56.dp).testTag("row$i"))
                    }
                }
                Box(
                    Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(132.dp)
                        .onGloballyPositioned { islandTop = it.boundsInRoot().top },
                )
            }
        }
        rule.onNodeWithTag("list").performScrollToIndex(59)
        rule.waitForIdle()
        val bottom = rule.onNodeWithTag("row59").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("при наложении последняя строка заходит под остров", bottom > islandTop)
    }
}
