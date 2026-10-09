package com.texfi.w0y.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Спектр и форма волны из отсчётов. Без Android — проверяется тестом на
 * синусе известной частоты.
 *
 * Окно Ханна, БПФ по основанию 2, полосы на логарифмической шкале частот
 * (как слышит ухо: низы шире, верхи уже), сглаживание «быстро вверх,
 * медленно вниз», чтобы столбики не дрожали.
 */
class SpectrumAnalyzer(
    private val size: Int = 1024,
    val bands: Int = 32,
) {
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val window = FloatArray(size) { (0.5 - 0.5 * cos(2 * PI * it / (size - 1))).toFloat() }
    private val smooth = FloatArray(bands)
    val samples = FloatArray(size)

    /** Магнитуды полос 0..1 после сглаживания. */
    val levels = FloatArray(bands)

    /** Уровень низов 0..1 — для мозаики, реагирующей на бас. */
    var bass = 0f
        private set

    init {
        require(size and (size - 1) == 0) { "размер должен быть степенью двойки" }
    }

    /** Пересчитать по отсчётам в [samples]. [gain] — чувствительность. */
    fun analyze(sampleRate: Int, gain: Float = 1f) {
        for (i in 0 until size) {
            re[i] = samples[i] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val nyquist = sampleRate / 2f
        val minHz = 40f
        val maxHz = minOf(16_000f, nyquist)
        for (b in 0 until bands) {
            val lo = minHz * (maxHz / minHz).pow(b / bands.toFloat())
            val hi = minHz * (maxHz / minHz).pow((b + 1) / bands.toFloat())
            val from = (lo / nyquist * (size / 2)).toInt().coerceIn(1, size / 2 - 1)
            val to = (hi / nyquist * (size / 2)).toInt().coerceIn(from + 1, size / 2)
            var peak = 0f
            val loBin = lo / nyquist * (size / 2)
            val hiBin = hi / nyquist * (size / 2)
            if (hiBin - loBin < 1f) {
                // Полоса уже одного отсчёта БПФ (низы): берём значение в её
                // центре между соседними отсчётами, иначе несколько полос
                // показывали бы один и тот же отсчёт ступенькой.
                val center = sqrt(loBin * hiBin)
                val k = center.toInt().coerceIn(1, size / 2 - 2)
                val f = (center - k).coerceIn(0f, 1f)
                peak = magnitude(k) * (1 - f) + magnitude(k + 1) * f
            } else {
                for (k in from until to) peak = max(peak, magnitude(k))
            }
            // Децибелы, сжатые в 0..1: тихое остаётся видимым, громкое не упирается.
            val db = 20f * ln(peak / (size / 4f) + 1e-6f) / ln(10f)
            val value = ((db + DB_FLOOR) / DB_FLOOR * gain).coerceIn(0f, 1f)
            smooth[b] = if (value > smooth[b]) value else smooth[b] * DECAY + value * (1 - DECAY)
            levels[b] = smooth[b]
        }
        bass = (levels.take(3).average().toFloat())
    }

    private fun magnitude(k: Int): Float = sqrt(re[k] * re[k] + im[k] * im[k])

    /** Номер полосы, в которую попадает частота [hz]. */
    fun bandOf(hz: Float, sampleRate: Int): Int {
        val nyquist = sampleRate / 2f
        val minHz = 40f
        val maxHz = minOf(16_000f, nyquist)
        val f = ((ln(hz / minHz) / ln(maxHz / minHz)) * bands).toInt()
        return f.coerceIn(0, bands - 1)
    }

    /** Спокойная анимация на паузе: всё медленно оседает. */
    fun settle() {
        for (b in 0 until bands) {
            smooth[b] *= DECAY
            levels[b] = smooth[b]
        }
        bass *= DECAY
    }

    private companion object {
        const val DB_FLOOR = 60f
        const val DECAY = 0.82f

        /** БПФ на месте, Кули — Тьюки. */
        fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    var t = re[i]
                    re[i] = re[j]
                    re[j] = t
                    t = im[i]
                    im[i] = im[j]
                    im[j] = t
                }
            }
            var len = 2
            while (len <= n) {
                val ang = -2 * PI / len
                val wr = cos(ang).toFloat()
                val wi = sin(ang).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f
                    var ci = 0f
                    for (k in 0 until len / 2) {
                        val ur = re[i + k]
                        val ui = im[i + k]
                        val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                        val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                        re[i + k] = ur + vr
                        im[i + k] = ui + vi
                        re[i + k + len / 2] = ur - vr
                        im[i + k + len / 2] = ui - vi
                        val nr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = nr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
