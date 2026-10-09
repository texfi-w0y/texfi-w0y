package com.texfi.w0y.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeymapTest {
    @Test
    fun defaultsDoNotCollide() {
        val seen = HashMap<String, Action>()
        for (a in Action.entries) for (combo in a.defaults) {
            val other = seen.put(combo, a)
            assertTrue(other == null, "«$combo» занято дважды: ${other?.id} и ${a.id}")
        }
    }

    @Test
    fun customOverridesDefaults() {
        val custom = mapOf(Action.PLAY_PAUSE.id to listOf("p"))
        assertEquals(listOf("p"), Keymap.bindings(Action.PLAY_PAUSE, custom))
        assertEquals(Action.PLAY_PAUSE, Keymap.find("p", custom))
        assertNull(Keymap.find("space", custom))
    }

    @Test
    fun ownerIgnoresTheActionItself() {
        assertNull(Keymap.owner("space", emptyMap(), except = Action.PLAY_PAUSE))
        assertEquals(Action.PLAY_PAUSE, Keymap.owner("space", emptyMap(), except = Action.NEXT))
    }

    @Test
    fun everyActionHasUniqueId() {
        assertEquals(Action.entries.size, Action.entries.map { it.id }.toSet().size)
    }
}
