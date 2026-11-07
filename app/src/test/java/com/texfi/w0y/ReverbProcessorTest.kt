package com.texfi.w0y

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.playback.ReverbProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Своё эхо: выключенное не трогает звук, включённое оставляет хвост и не хрипит на громком. */
class ReverbProcessorTest {
    private val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)

    private fun processor(reverb: Reverb) =
        ReverbProcessor().apply {
            this.reverb = reverb
            configure(format)
            flush()
        }

    /** Прогоняет кадры (левый = правый) и возвращает выход левого канала. */
    private fun run(p: ReverbProcessor, samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.nativeOrder())
        for (s in samples) input.putShort(s).putShort(s)
        input.flip()
        p.queueInput(input)
        val out = p.output
        return ShortArray(out.remaining() / 4) { out.short.also { out.short } }
    }

    @Test
    fun offIsTransparent() {
        val samples = ShortArray(2_000) { (it * 37 % 20_000 - 10_000).toShort() }
        assertEquals(samples.toList(), run(processor(Reverb.OFF), samples).toList())
    }

    @Test
    fun impulseLeavesTailThatGrowsWithRoom() {
        fun tail(reverb: Reverb): Double {
            val impulse = ShortArray(44_100).also { it[0] = 20_000 }
            val out = run(processor(reverb), impulse)
            // Энергия от 0,1 до 1 с — там, где сухой звук уже давно кончился.
            return (4_410 until out.size).sumOf { out[it].toDouble() * out[it] }
        }
        val room = tail(Reverb.ROOM)
        val cave = tail(Reverb.CAVE)
        assertTrue("эхо есть: $room", room > 0)
        assertTrue("пещера длиннее комнаты: $cave vs $room", cave > room * 2)
    }

    @Test
    fun loudSineDoesNotClipMuch() {
        val sine = ShortArray(44_100) { (Math.sin(it * 2 * Math.PI * 220 / 44_100) * 16_000).toInt().toShort() }
        val out = run(processor(Reverb.CAVE), sine)
        val clipped = out.count { it >= 32_766 || it <= -32_767 }
        assertTrue("клиппинг $clipped из ${out.size}", clipped < out.size / 200)
    }
}
