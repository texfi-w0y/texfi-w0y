package com.texfi.w0y

import com.texfi.w0y.widget.WidgetColors
import com.texfi.w0y.widget.WidgetKind
import com.texfi.w0y.widget.WidgetSize
import com.texfi.w0y.widget.WidgetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    @Test
    fun sizeFollowsHeight() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.forHeightDp(70))
        assertEquals(WidgetSize.COMPACT, WidgetSize.forHeightDp(109))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.forHeightDp(110))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.forHeightDp(199))
        assertEquals(WidgetSize.LARGE, WidgetSize.forHeightDp(200))
    }

    @Test
    fun fourStates() {
        val s = WidgetState(title = "t", playing = true)
        assertEquals(WidgetKind.PLAYING, s.kind(alive = true))
        assertEquals(WidgetKind.PAUSED, s.copy(playing = false).kind(alive = true))
        assertEquals(WidgetKind.ERROR, s.copy(error = true).kind(alive = true))
        // Записавший процесс умер — кнопки нечем обслужить, это «тишина».
        assertEquals(WidgetKind.IDLE, s.kind(alive = false))
    }

    @Test
    fun accentStaysReadableOnBothCards() {
        val night = 0xFF121116.toInt()
        val day = 0xFFF8F7FA.toInt()
        listOf(0xFFE0A860.toInt(), 0xFFF0B49A.toInt(), 0xFF7FB5FF.toInt(), 0xFF4A7CFB.toInt()).forEach { accent ->
            assertTrue(WidgetColors.contrast(WidgetColors.accentOn(accent, night, 0xFFF4F2F7.toInt()), night) >= 3.0)
            assertTrue("светлая карточка, акцент ${accent.toString(16)}", WidgetColors.contrast(WidgetColors.accentOn(accent, day, 0xFF16141B.toInt()), day) >= 3.0)
        }
    }
}
