package com.texfi.w0y

import com.texfi.w0y.ui.components.RowAction
import com.texfi.w0y.ui.components.UndoController
import com.texfi.w0y.ui.components.needsConfirm
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Защита от случайного удаления: UNDO возвращает один раз, подтверждения, нет одно-тактных корзин. */
class RemovalTest {
    @Test
    fun undoRestoresOnce() {
        val undo = UndoController()
        var restored = 0
        undo.post("Removed") { restored++ }
        assertEquals("Removed", undo.current.value?.message)
        undo.undo()
        undo.undo()
        assertEquals(1, restored)
        assertNull(undo.current.value)
    }

    @Test
    fun newPlateReplacesOldAndStaleDismissIsIgnored() {
        val undo = UndoController()
        undo.post("first") {}
        val firstId = undo.current.value!!.id
        undo.post("second") {}
        undo.dismiss(firstId) // таймер старой плашки не гасит новую
        assertEquals("second", undo.current.value?.message)
        undo.dismiss(undo.current.value!!.id)
        assertNull(undo.current.value)
    }

    @Test
    fun dangerousActionsAskWhenProtectionIsOn() {
        val danger = RowAction("DELETE", danger = true) {}
        val light = RowAction("REMOVE") {}
        val file = RowAction("DELETE FILE", danger = true, alwaysConfirm = true) {}
        assertTrue(danger.needsConfirm(protection = true))
        assertFalse(danger.needsConfirm(protection = false))
        assertFalse(light.needsConfirm(protection = true))
        // Файл с телефона подтверждается всегда, даже при выключенной защите.
        assertTrue(file.needsConfirm(protection = false))
    }

    /** Корзины и крестики в строках треков убраны: удаление идёт через долгое нажатие. */
    @Test
    fun noOneTapTrashInTrackRows() {
        val ui = File("src/main/java/com/texfi/w0y/ui/screens")
        val library = File(ui, "LibraryScreen.kt").readText()
        // Единственные корзины в библиотеке — заголовок (с подтверждением) и удаление плейлиста.
        assertTrue(Regex("Sprites\\.trash").findAll(library).count() <= 2)
        assertFalse("кнопка «убрать из очереди» вернулась", "SpriteButton(Sprites.close, onClick = it, size = 16)" in File(ui, "PlayerScreen.kt").readText())
    }
}
