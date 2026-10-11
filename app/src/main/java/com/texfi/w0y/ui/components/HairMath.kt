package com.texfi.w0y.ui.components

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Чистая математика Hairline-плеера: без Compose и Android, чтобы её
 * проверяли обычные тесты, а рисующий код оставался тонким.
 */
object HairMath {
    /** Столько точек в «живой» линии мини-плеера. */
    const val WAVE_POINTS = 56

    /**
     * Амплитуды живой линии: набор заранее, по номеру трека. Это не анализ звука —
     * один и тот же трек всегда рисуется одной линией, а кадр не платит ничем.
     * Соседние точки сглажены, чтобы линия была волной, а не частоколом.
     */
    fun waveAmps(seed: Int, n: Int = WAVE_POINTS): FloatArray {
        var x = seed.toLong() xor 0x5DEECE66DL
        val raw =
            FloatArray(n) {
                x = x * 6364136223846793005L + 1442695040888963407L
                ((x ushr 40) and 0xFFFF).toFloat() / 0xFFFF
            }
        return FloatArray(n) { i ->
            val a = raw[(i - 1).coerceAtLeast(0)]
            val b = raw[i]
            val c = raw[(i + 1).coerceAtMost(n - 1)]
            (0.18f + 0.82f * (0.25f * a + 0.5f * b + 0.25f * c)).coerceIn(0f, 1f)
        }
    }

    /**
     * Сколько делений на окружности диска. На обычной скорости — 60, как
     * секунды на циферблате; быстрее — реже и крупнее, медленнее — чаще: по
     * делениям видно, что пластинка идёт не так, как всегда.
     */
    fun tickCount(speed: Float): Int {
        val s = if (speed.isNaN() || speed <= 0f) 1f else speed
        return (60f / s).roundToInt().coerceIn(24, 120)
    }

    /** Шаг маркеров при точной перемотке: 10 с, а у длинных записей — минута, чтобы не слиплись. */
    fun seekMarkerStepMs(durationMs: Long): Long = if (durationMs > 10 * 60_000L) 60_000L else 10_000L

    /** Доля окружности по точке касания: 0 — вверху, по часовой до 1. */
    fun angleFraction(dx: Float, dy: Float): Float {
        val a = atan2(dx.toDouble(), (-dy).toDouble()) // 0 вверху, + по часовой
        val f = (a / (2.0 * PI)).toFloat()
        return if (f < 0f) f + 1f else f
    }

    /**
     * Доля после касания без перескока через верх: палец идёт по кругу и
     * пересекает «12 часов» — позиция не прыгает с конца в начало, а упирается
     * в край.
     */
    fun unwrapFraction(previous: Float, raw: Float): Float =
        when {
            previous > 0.75f && raw < 0.25f -> 1f
            previous < 0.25f && raw > 0.75f -> 0f
            else -> raw
        }.coerceIn(0f, 1f)

    /** «1:05» или «1:02:03». */
    fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, s) else "%d:%02d".format(java.util.Locale.ROOT, m, s)
    }

    /** Скорость одинаково везде и независимо от локали: «1.00×». */
    fun speedText(speed: Float): String = "%.2f×".format(java.util.Locale.ROOT, speed)
}
