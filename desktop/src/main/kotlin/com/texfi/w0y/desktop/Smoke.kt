package com.texfi.w0y.desktop

import com.texfi.w0y.data.SoundProfile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * `w0y --smoke`: проверка собранного приложения без окна и без звуковой карты. Загружаются строки и
 * версия, поднимается движок звука, играется короткий тон, позиция должна расти, пауза и перемотка —
 * работать. Код возврата 0 — всё в порядке; ему верит CI на Windows и Linux.
 */
object Smoke {
    fun run(): Int {
        val problems = mutableListOf<String>()
        fun check(name: String, ok: Boolean, detail: String = "") {
            println((if (ok) "ok    " else "FAIL  ") + name + if (detail.isNotEmpty()) " ($detail)" else "")
            if (!ok) problems += name
        }
        check("version", APP_VERSION != "dev" && APP_VERSION.isNotBlank(), APP_VERSION)
        com.texfi.w0y.desktop.i18n.Strings.select("ru")
        check("strings", com.texfi.w0y.desktop.i18n.S("tab_home").isNotBlank(), com.texfi.w0y.desktop.i18n.S("tab_home"))
        check("os", true, Platform.describe())
        check("libmpv", LibMpv.available(), LibMpv.Loader.lastError.orEmpty())
        if (LibMpv.available()) {
            val seen = Seen()
            val engine = LibMpv({ seen.time = it }, { seen.duration = it }, { seen.paused = it }, {}, {})
            check("engine start", engine.start(), engine.version.orEmpty())
            engine.applyAudio(SoundProfile.Plain, null, false)
            engine.load(tone(3).absolutePath, emptyMap())
            check("position grows", waitFor { seen.time > 0.4 }, "t=${seen.time}")
            engine.setPause(true)
            check("pause", waitFor { seen.paused })
            engine.seek(2.0)
            engine.setPause(false)
            check("seek", waitFor { seen.time >= 2.0 }, "t=${seen.time}")
            engine.shutdown()
        }
        println(if (problems.isEmpty()) "smoke: OK" else "smoke: FAILED ${problems.joinToString()}")
        return if (problems.isEmpty()) 0 else 1
    }

    private class Seen {
        @Volatile var time = 0.0

        @Volatile var duration = 0.0

        @Volatile var paused = false
    }

    private fun waitFor(ms: Long = 8000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (cond()) return true
            Thread.sleep(25)
        }
        return cond()
    }

    /** Тон 440 Гц нужной длины в WAV. */
    private fun tone(seconds: Int): File {
        val rate = 44100
        val samples = rate * seconds
        val data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        for (i in 0 until samples) data.putShort((sin(2 * PI * 440 * i / rate) * 8000).toInt().toShort())
        return File.createTempFile("w0y-smoke", ".wav").also { it.writeBytes(data.array()); it.deleteOnExit() }
    }
}
