package com.texfi.w0y.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.texfi.w0y.data.Reverb
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Эхо, посчитанное самим приложением, — прямо в аудиоцепочке плеера.
 *
 * Раньше эхо было системным эффектом (PresetReverb) на аудиосессии. На части
 * телефонов, включая Pixel, система такой эффект не создаёт вовсе (ошибка -38),
 * и ручка «эхо» молча ничего не делала. Здесь своё: схема Freeverb — восемь
 * гребёнчатых фильтров с затуханием и четыре фазовых на канал, правый канал
 * чуть сдвинут для ширины. Работает на любом устройстве и не зависит от сессии.
 *
 * [reverb] меняют с главного потока; поток воспроизведения читает его раз на
 * буфер. Выключенное эхо — просто копия звука.
 */
@OptIn(UnstableApi::class)
class ReverbProcessor : BaseAudioProcessor() {
    @Volatile
    var reverb: Reverb = Reverb.OFF

    private var channels = 0
    private var combs: Array<Array<Comb>> = emptyArray()
    private var allpasses: Array<Array<Allpass>> = emptyArray()
    private var applied: Reverb? = null
    private var wet = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        val k = inputAudioFormat.sampleRate / 44_100f
        val lanes = minOf(channels, 2)
        combs = Array(lanes) { lane -> Array(COMB_TUNING.size) { Comb(((COMB_TUNING[it] + lane * SPREAD) * k).toInt().coerceAtLeast(1)) } }
        allpasses = Array(lanes) { lane -> Array(ALLPASS_TUNING.size) { Allpass(((ALLPASS_TUNING[it] + lane * SPREAD) * k).toInt().coerceAtLeast(1)) } }
        applied = null
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val target = reverb
        if (target != applied) tune(target)
        if (target == Reverb.OFF || combs.isEmpty()) {
            out.put(inputBuffer).flip()
            return
        }
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytes = if (float) 4 else 2
        val frames = remaining / bytes / channels
        val lanes = combs.size
        val frame = FloatArray(channels)
        repeat(frames) {
            for (c in 0 until channels) frame[c] = if (float) input.float else input.short / 32768f
            // Вход в эхо — моно-смесь, как у Freeverb: эхо одно, разное только на выходе.
            val mono = (if (channels > 1) (frame[0] + frame[1]) * 0.5f else frame[0]) * FIXED_GAIN
            for (lane in 0 until lanes) {
                var acc = 0f
                for (comb in combs[lane]) acc += comb.process(mono)
                for (ap in allpasses[lane]) acc = ap.process(acc)
                frame[lane] = (frame[lane] * DRY + acc * wet).coerceIn(-1f, 1f)
            }
            if (lanes == 1 && channels > 1) frame[1] = frame[0]
            for (c in 0 until channels) {
                if (float) out.putFloat(frame[c]) else out.putShort((frame[c] * 32767f).toInt().toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    /** Размер комнаты, глушение высоких и доля эха — по выбранному эху. */
    private fun tune(target: Reverb) {
        val (room, damp, mix) =
            when (target) {
                Reverb.ROOM -> Triple(0.55f, 0.5f, 0.4f)
                Reverb.HALL -> Triple(0.82f, 0.3f, 0.6f)
                Reverb.CAVE -> Triple(0.94f, 0.12f, 0.8f)
                Reverb.OFF -> Triple(0f, 0f, 0f)
            }
        val feedback = room * 0.28f + 0.7f
        for (lane in combs) for (comb in lane) {
            comb.feedback = feedback
            comb.damp = damp * 0.4f
        }
        // Из тишины в эхо — хвосты прошлого раза не нужны.
        if (applied == Reverb.OFF || applied == null) clear()
        wet = mix
        applied = target
    }

    private fun clear() {
        for (lane in combs) for (comb in lane) comb.clear()
        for (lane in allpasses) for (ap in lane) ap.clear()
    }

    override fun onFlush() = clear()

    override fun onReset() {
        combs = emptyArray()
        allpasses = emptyArray()
        applied = null
    }

    private class Comb(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var store = 0f
        var feedback = 0.84f
        var damp = 0.2f

        fun process(input: Float): Float {
            val output = buffer[index]
            store = output * (1f - damp) + store * damp
            buffer[index] = input + store * feedback
            if (++index >= buffer.size) index = 0
            return output
        }

        fun clear() {
            buffer.fill(0f)
            store = 0f
        }
    }

    private class Allpass(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(input: Float): Float {
            val stored = buffer[index]
            val output = stored - input
            buffer[index] = input + stored * 0.5f
            if (++index >= buffer.size) index = 0
            return output
        }

        fun clear() = buffer.fill(0f)
    }

    private companion object {
        /** Длины линий задержки Freeverb для 44,1 кГц. */
        val COMB_TUNING = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASS_TUNING = intArrayOf(556, 441, 341, 225)
        const val SPREAD = 23
        const val FIXED_GAIN = 0.015f
        const val DRY = 0.8f
    }
}
