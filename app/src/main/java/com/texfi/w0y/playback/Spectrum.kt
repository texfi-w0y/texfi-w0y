package com.texfi.w0y.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Звук для визуализатора — прямо из цепочки воспроизведения.
 *
 * Отвод стоит в аудиоцепочке плеера ([TeeAudioProcessor]): копия PCM
 * уходит сюда, а сам звук идёт дальше без задержки и без изменений.
 * Микрофон не нужен, разрешения тоже. Пока визуализатор не открыт,
 * отвод ничего не копирует — [active] выключен, и цена ему — одна проверка.
 */
@Singleton
@OptIn(UnstableApi::class)
class SpectrumBus @Inject constructor() : TeeAudioProcessor.AudioBufferSink {
    @Volatile
    var active: Boolean = false

    private val ring = FloatArray(RING)
    private var write = 0
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT

    @Volatile
    var sampleRate: Int = 44_100
        private set

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
    }

    /** Вызывается на потоке воспроизведения: только копирование, никаких расчётов. */
    override fun handleBuffer(buffer: ByteBuffer) {
        if (!active) return
        val data = buffer.duplicate().order(ByteOrder.nativeOrder())
        synchronized(ring) {
            when (encoding) {
                C.ENCODING_PCM_FLOAT -> {
                    val frames = data.remaining() / 4 / channels
                    repeat(frames) {
                        var sum = 0f
                        repeat(channels) { sum += data.float }
                        push(sum / channels)
                    }
                }
                C.ENCODING_PCM_16BIT -> {
                    val frames = data.remaining() / 2 / channels
                    repeat(frames) {
                        var sum = 0f
                        repeat(channels) { sum += data.short / 32768f }
                        push(sum / channels)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun push(value: Float) {
        ring[write] = value
        write = (write + 1) % RING
    }

    /** Последние [out].size отсчётов, по порядку. */
    fun latest(out: FloatArray) {
        synchronized(ring) {
            var at = (write - out.size + RING) % RING
            for (i in out.indices) {
                out[i] = ring[at]
                at = (at + 1) % RING
            }
        }
    }

    private companion object {
        const val RING = 8192
    }
}
