package com.texfi.w0y.data

import com.texfi.w0y.data.db.PlaylistEntity

/** Свой порядок плейлистов поверх списка из базы. */
object PlaylistOrder {
    /**
     * Плейлисты из [order] — в его порядке; остальных (созданы или пришли из аккаунта уже
     * после перестановки) он не знает — они встают первыми, как и раньше: новые сверху.
     */
    fun apply(list: List<PlaylistEntity>, order: List<Long>): List<PlaylistEntity> {
        if (order.isEmpty()) return list
        val rank = order.withIndex().associate { (i, id) -> id to i }
        val (known, fresh) = list.partition { it.id in rank }
        return fresh + known.sortedBy { rank.getValue(it.id) }
    }

    /** Переставить [from] на место [to] в видимом порядке. */
    fun move(ids: List<Long>, from: Long, to: Long): List<Long> {
        val a = ids.indexOf(from)
        val b = ids.indexOf(to)
        if (a < 0 || b < 0 || a == b) return ids
        return ids.toMutableList().apply { add(b, removeAt(a)) }
    }

    /** Полный порядок [all], в котором места видимых [shown] заняты ими в новом порядке. */
    fun merge(all: List<Long>, shown: List<Long>): List<Long> {
        val set = shown.toSet()
        val next = shown.iterator()
        val merged = all.map { if (it in set && next.hasNext()) next.next() else it }
        return merged + shown.filterNot { it in merged }
    }
}
