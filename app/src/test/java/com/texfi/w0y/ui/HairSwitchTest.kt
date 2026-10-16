package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.texfi.w0y.ui.components.PixelSwitch
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Бегунок переключателя стоит по центру пилюли: сверху и снизу, слева и справа зазоры одинаковые. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HairSwitchTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var checked by mutableStateOf(false)

    private fun gaps(): FloatArray {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        val pill = rule.onNodeWithTag("hair-switch", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val thumb = rule.onNodeWithTag("hair-switch-thumb", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return floatArrayOf(thumb.top - pill.top, pill.bottom - thumb.bottom, thumb.left - pill.left, pill.right - thumb.right)
    }

    @Test
    fun thumbIsCenteredOffAndOn() {
        rule.setContent { W0yTheme { PixelSwitch(checked = checked, onCheckedChange = { checked = it }) } }
        val off = gaps()
        assertEquals("выключен: сверху и снизу поровну", off[0], off[1], 1.5f)
        checked = true
        val on = gaps()
        assertEquals("включён: сверху и снизу поровну", on[0], on[1], 1.5f)
        assertEquals("выключен: левый зазор = верхнему", off[2], off[0], 1.5f)
        assertEquals("включён: правый зазор = верхнему", on[3], on[0], 1.5f)
    }
}
