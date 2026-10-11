package com.texfi.w0y.data

import androidx.compose.runtime.Immutable

/**
 * Своё звучание конкретного трека — «твоя версия».
 *
 * Приложение затевалось вокруг того, что чужие slowed-переделки не нужны:
 * любой оригинал можно замедлить с эхом прямо в плеере. Но пока скорость
 * была одна на всё приложение, этим нельзя было пользоваться так, как
 * люди слушают slowed: замедлил одну песню — замедлились все, включая
 * следующую в очереди. Версия принадлежит треку, а не приложению.
 */
@Immutable
data class SoundProfile(
    val speed: Float,
    val pitch: Float,
    val reverb: Reverb,
) {
    /** Звучит ли трек как все остальные. */
    val isPlain: Boolean get() = speed == 1f && pitch == 1f && reverb == Reverb.OFF

    /** Короткая подпись для метки в списке: «0.85×», «1.25×». */
    val label: String get() = NumberText.speed(speed)

    companion object {
        val Plain = SoundProfile(1f, 1f, Reverb.OFF)

        fun of(settings: W0ySettings) = SoundProfile(settings.speed, settings.pitch, settings.reverb)
    }
}
