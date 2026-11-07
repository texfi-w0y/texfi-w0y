package com.texfi.w0y

import com.texfi.w0y.data.PlaylistOrder
import com.texfi.w0y.data.db.PlaylistEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Свой порядок плейлистов: новые сверху, перенос, перестановка внутри папки не двигает остальных. */
class PlaylistOrderTest {
    private fun p(id: Long) = PlaylistEntity(id = id, name = "p$id", createdAt = id)

    @Test
    fun savedOrderWinsAndNewOnesGoFirst() {
        val db = listOf(p(5), p(4), p(3), p(2))
        assertEquals(listOf(5L, 2L, 4L, 3L), PlaylistOrder.apply(db, listOf(2, 4, 3)).map { it.id })
        assertEquals(db, PlaylistOrder.apply(db, emptyList()))
    }

    @Test
    fun moveForwardAndBack() {
        assertEquals(listOf(2L, 3L, 1L, 4L), PlaylistOrder.move(listOf(1, 2, 3, 4), 1, 3))
        assertEquals(listOf(4L, 1L, 2L, 3L), PlaylistOrder.move(listOf(1, 2, 3, 4), 4, 1))
        assertEquals(listOf(1L, 2L), PlaylistOrder.move(listOf(1, 2), 1, 9))
    }

    @Test
    fun mergeKeepsHiddenPlaylistsInPlace() {
        // 2 и 4 видны (папка), их переставили; 1 и 3 стоят где стояли.
        assertEquals(listOf(1L, 4L, 3L, 2L), PlaylistOrder.merge(listOf(1, 2, 3, 4), listOf(4, 2)))
    }
}
