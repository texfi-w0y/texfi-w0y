package com.texfi.w0y.data

import androidx.compose.runtime.Immutable
import com.texfi.w0y.desktop.i18n.S

/** Эхо: те же четыре ступени, что на телефоне. */
enum class Reverb(val key: String, val filter: String?) {
    OFF("reverb_none", null),
    ROOM("reverb_room", "aecho=0.8:0.85:40|70:0.30|0.20"),
    HALL("reverb_hall", "aecho=0.8:0.88:60|120|180:0.40|0.30|0.20"),
    CAVE("reverb_cave", "aecho=0.8:0.88:100|200|300|450:0.50|0.40|0.30|0.20"),
    ;

    /** Название на языке интерфейса. */
    val label: String get() = S(key)
}

/**
 * «Твоя версия» трека: скорость, высота тона и эхо. Как на телефоне, принадлежит
 * треку, а не приложению: замедлил одну песню — остальные звучат как обычно.
 */
@Immutable
data class SoundProfile(
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val reverb: Reverb = Reverb.OFF,
) {
    val isPlain: Boolean get() = speed == 1f && pitch == 1f && reverb == Reverb.OFF

    /** Метка для списков: «0.85×». */
    val label: String get() = "${speed}×".replace(".0×", "×")

    companion object {
        val Plain = SoundProfile()
        val Slowed = SoundProfile(0.85f, 0.92f, Reverb.HALL)
        val Sped = SoundProfile(1.25f, 1.06f, Reverb.OFF)
    }
}
