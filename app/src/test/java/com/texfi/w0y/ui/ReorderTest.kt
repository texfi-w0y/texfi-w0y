package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.components.ReorderHandle
import com.texfi.w0y.ui.components.ReorderState
import com.texfi.w0y.ui.components.rememberReorderState
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Трек в плейлисте переносится только после удержания на ручке (шесть точек). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ReorderTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var order by mutableStateOf(listOf("a", "b", "c", "d", "e"))
    private var drops = 0

    private fun show() {
        rule.setContent {
            W0yTheme {
                val list = rememberLazyListState()
                val reorder =
                    rememberReorderState(list) { from, to ->
                        val a = order.indexOf(from)
                        val b = order.indexOf(to)
                        if (a >= 0 && b >= 0) order = order.toMutableList().apply { add(b, removeAt(a)) }
                    }
                LazyColumn(state = list) {
                    items(order, key = { it }, contentType = { ReorderState.REORDER_TYPE }) { id ->
                        Row(Modifier.fillMaxWidth().height(64.dp)) {
                            Text(id, Modifier.testTag("row-$id"))
                            androidx.compose.foundation.layout.Box(Modifier.testTag("handle-$id")) {
                                ReorderHandle(reorder, id) { drops++ }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun quickSwipeOnHandleDoesNotMove() {
        show()
        rule.onNodeWithTag("handle-a").performTouchInput { swipeDown(startY = center.y, endY = center.y + 150f, durationMillis = 200) }
        rule.waitForIdle()
        assertEquals(listOf("a", "b", "c", "d", "e"), order)
        assertEquals(0, drops)
    }

    @Test
    fun holdThenDragMovesTheRow() {
        show()
        rule.onNodeWithTag("handle-a").performTouchInput {
            down(center)
            advanceEventTime(com.texfi.w0y.ui.components.HOLD_MS + 60)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, 15f), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("b", "a", "c", "d", "e"), order)
        assertEquals(1, drops)
    }
}
