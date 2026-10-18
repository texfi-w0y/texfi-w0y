package com.texfi.w0y.desktop

import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SoundProfile
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Настоящий движок на настоящем файле, но без звуковой карты (ao=null): так тест идёт и на CI.
 * Нет libmpv на машине — тест пропускается, а не падает.
 */
class AudioEngineTest {
    private fun sine(seconds: Int): File {
        val rate = 44100
        val samples = rate * seconds
        // Стерео, как у настоящей музыки: mpv 0.37 (Ubuntu 24.04) на моно с rubberband + lavfi сыплет «could not pass frame».
        val data = ByteBuffer.allocate(samples * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            val v = (sin(2 * PI * 440 * i / rate) * 8000).toInt().toShort()
            data.putShort(v).putShort(v)
        }
        val out = ByteArrayOutputStream()
        val head = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        head.put("RIFF".toByteArray()).putInt(36 + samples * 4).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(rate).putInt(rate * 4).putShort(4).putShort(16)
            .put("data".toByteArray()).putInt(samples * 4)
        out.write(head.array()); out.write(data.array())
        return File.createTempFile("w0y-sine", ".wav").also { it.writeBytes(out.toByteArray()); it.deleteOnExit() }
    }

    private class Probe {
        @Volatile var time = 0.0
        @Volatile var duration = 0.0
        @Volatile var paused = false
        @Volatile var loaded = 0
        val ends = CopyOnWriteArrayList<String>()
        fun engine() = LibMpv({ time = it }, { duration = it }, { paused = it }, { ends += it }, { loaded++ })
    }

    private fun waitFor(ms: Long = 8000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(25) }
        return cond()
    }

    @Test
    fun playsPausesSeeksAndEnds() {
        assumeTrue(LibMpv.available(), "libmpv не найдена")
        val p = Probe()
        val e = p.engine()
        assertTrue(e.start())
        e.applyAudio(SoundProfile.Plain, null, false)
        e.load(sine(4).absolutePath, emptyMap())
        assertTrue(waitFor { p.loaded == 1 && p.duration > 3.5 }, "файл не загрузился")
        assertTrue(waitFor { p.time > 0.5 }, "позиция не растёт")
        e.setPause(true)
        assertTrue(waitFor { p.paused })
        val frozen = p.time
        Thread.sleep(400)
        assertEquals(frozen, p.time, 0.15)
        e.seek(3.0); e.setPause(false)
        assertTrue(waitFor { p.time >= 3.0 }, "перемотка не сработала")
        assertTrue(waitFor { p.ends.contains("eof") }, "конец трека не пришёл")
        assertEquals(1, p.ends.count { it == "eof" }, "eof должен прийти один раз")
        e.shutdown()
    }

    @Test
    fun speedPitchAndReverbDoNotBreakPlayback() {
        assumeTrue(LibMpv.available(), "libmpv не найдена")
        val p = Probe()
        val e = p.engine()
        assertTrue(e.start())
        e.applyAudio(SoundProfile(1.5f, 1.2f, Reverb.CAVE), -3.0, true, listOf(0, 0, 3, 0, 0, 0, 0, 0, 0, 0))
        e.load(sine(6).absolutePath, emptyMap())
        assertTrue(waitFor { p.loaded == 1 })
        val t0 = System.currentTimeMillis()
        assertTrue(waitFor { p.time >= 2.0 }, "с фильтрами позиция стоит")
        // Скорость 1.5: две секунды звука проходят заметно быстрее двух секунд на часах.
        assertTrue(System.currentTimeMillis() - t0 < 1900, "скорость не применилась")
        e.shutdown()
    }

    @Test
    fun twoHundredTrackSwitchesStayHealthy() {
        assumeTrue(LibMpv.available(), "libmpv не найдена")
        val p = Probe()
        val e = p.engine()
        assertTrue(e.start())
        val file = sine(2).absolutePath
        val before = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        repeat(200) { e.load(file, emptyMap()) }
        assertTrue(waitFor(20000) { p.loaded >= 1 && p.time >= 0.0 })
        Thread.sleep(500)
        System.gc()
        val after = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        // Куча JVM за 200 переключений не должна заметно расти (нативную память mpv смотрим отдельно).
        assertTrue(after - before < 40L * 1024 * 1024, "куча выросла на ${(after - before) / 1024 / 1024} МБ")
        e.shutdown()
    }

    @Test
    fun filterChainHasNoEmptyFilters() {
        val chain = audioFilterChain(SoundProfile.Plain, null, false, listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        assertEquals("", chain)
    }
}
