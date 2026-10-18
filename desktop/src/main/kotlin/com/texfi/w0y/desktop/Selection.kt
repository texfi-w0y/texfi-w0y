package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texfi.w0y.data.SongItem

/** Выделение строк в списках треков: обычный клик, Ctrl — добавить/снять, Shift — диапазон от опорной строки. */
class Selection {
    val ids = mutableStateListOf<String>()
    private var anchor by mutableStateOf(-1)

    fun clear() {
        ids.clear()
        anchor = -1
    }

    fun only(id: String, index: Int) {
        ids.clear()
        ids += id
        anchor = index
    }

    fun toggle(id: String, index: Int) {
        if (!ids.remove(id)) ids += id
        anchor = index
    }

    fun range(songs: List<SongItem>, to: Int) {
        val from = if (anchor in songs.indices) anchor else to
        ids.clear()
        for (i in minOf(from, to)..maxOf(from, to)) songs.getOrNull(i)?.let { ids += it.id }
        if (anchor !in songs.indices) anchor = to
    }

    /** Выделенные треки в порядке списка. */
    fun of(songs: List<SongItem>): List<SongItem> = songs.filter { it.id in ids }
}
