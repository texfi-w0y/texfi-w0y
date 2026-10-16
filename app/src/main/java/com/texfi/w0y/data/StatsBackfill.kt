package com.texfi.w0y.data

import com.texfi.w0y.data.db.BackfillRow

/** Что дописать в одну строку истории. */
data class BackfillUpdate(val id: Long, val listenedMs: Long, val counted: Boolean)

/**
 * Восстановление прослушиваний по уже накопленной истории.
 *
 * Строка истории заводится на старте трека, а время и «засчитано» дописываются позже.
 * Если до этого дело не дошло (или статистика только появилась), у строки 0 мс и
 * `counted = false`, хотя трек явно слушали. Свидетельство остаётся в метках времени:
 * до начала следующего трека прошло столько-то. Проигранное не больше этого промежутка,
 * не больше длительности трека и не больше [MAX_MS] (пауза на ночь не в счёт).
 * Старые записи (до правила) не трогаются: они считаются по длительности.
 */
object StatsBackfill {
    /** Меньше этого промежутка между стартами — трек пропустили сразу. */
    const val MIN_EVIDENCE_MS = 5_000L

    /** Потолок на одну запись: дольше «слушать» одну песню без паузы нельзя. */
    const val MAX_MS = 10 * 60_000L

    fun plan(rows: List<BackfillRow>): List<BackfillUpdate> {
        val ordered = rows.sortedWith(compareBy({ it.playedAt }, { it.id }))
        val out = ArrayList<BackfillUpdate>()
        for (i in 0 until ordered.size - 1) {
            val row = ordered[i]
            if (row.legacy || row.counted || row.listenedMs > 0) continue
            val gap = ordered[i + 1].playedAt - row.playedAt
            val evidence = minOf(gap, row.durationMs?.takeIf { it > 0 } ?: MAX_MS, MAX_MS)
            if (evidence < MIN_EVIDENCE_MS) continue
            out += BackfillUpdate(row.id, evidence, ListenRule.counts(evidence, row.durationMs))
        }
        return out
    }
}
