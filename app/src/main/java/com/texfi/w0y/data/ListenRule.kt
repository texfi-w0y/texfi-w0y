package com.texfi.w0y.data

/**
 * Что считать прослушиванием.
 *
 * Засчитывается включение, в котором реально проиграно 30 секунд или половина
 * трека, смотря что наступит раньше (у коротких треков — половина). Время
 * считается проигранное, а не позиция: перемотка в конец не делает трек
 * «прослушанным», а пропуск в начале не делает ничего.
 */
object ListenRule {
    /** Тридцать секунд — потолок порога: длинные треки не требуют большего. */
    const val THRESHOLD_MS = 30_000L

    /** Сколько нужно проиграть; при неизвестной длительности — тридцать секунд. */
    fun requiredMs(durationMs: Long?): Long =
        if (durationMs == null || durationMs <= 0) THRESHOLD_MS else minOf(durationMs / 2, THRESHOLD_MS)

    fun counts(listenedMs: Long, durationMs: Long?): Boolean = listenedMs >= requiredMs(durationMs)
}

/**
 * Одно включение трека: копит проигранное время по снимкам позиции.
 *
 * Снимок берётся раз в секунду. Шаг вперёд засчитывается, только если он
 * правдоподобен для прошедшего времени с учётом скорости; всё остальное —
 * перемотка, и позиция просто запоминается заново. Логика без Android,
 * чтобы проверяться тестами.
 */
class ListenSession(
    val songId: String,
    var durationMs: Long?,
) {
    var listenedMs: Long = 0
        private set

    /** Перешло ли включение порог — один раз за сессию. */
    var counted: Boolean = false
        private set

    private var lastPosition: Long? = null
    private var lastWall: Long? = null

    /**
     * Снимок плеера. [playing] — идёт ли звук прямо сейчас; на паузе время
     * не копится, но позиция запоминается, чтобы после паузы не насчитать
     * лишнего.
     *
     * Возвращает true, если именно этот снимок перевёл включение в засчитанные.
     */
    fun sample(positionMs: Long, wallMs: Long, playing: Boolean, speed: Float = 1f): Boolean {
        val previous = lastPosition
        val previousWall = lastWall
        lastPosition = positionMs
        lastWall = wallMs
        if (!playing || previous == null || previousWall == null) return false
        val delta = positionMs - previous
        val elapsed = (wallMs - previousWall).coerceAtLeast(0)
        // Правдоподобный шаг: не больше прошедшего времени с учётом скорости
        // и небольшого запаса на неровный таймер.
        val plausible = (elapsed * speed.coerceAtLeast(0.25f) * SLACK).toLong() + JITTER_MS
        if (delta in 1..plausible) listenedMs += delta
        if (!counted && ListenRule.counts(listenedMs, durationMs)) {
            counted = true
            return true
        }
        return false
    }

    /** Перемотка: позиция меняется, но время не копится. */
    fun seeked(positionMs: Long, wallMs: Long) {
        lastPosition = positionMs
        lastWall = wallMs
    }

    private companion object {
        const val SLACK = 1.5
        const val JITTER_MS = 400L
    }
}
