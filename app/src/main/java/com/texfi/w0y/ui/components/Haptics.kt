package com.texfi.w0y.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Включена ли вибрация. Значение приходит из настроек и раздаётся всем
 * кнопкам разом: пробрасывать флаг параметром через каждый список,
 * строку и панель — то же самое, что не иметь настройки.
 */
val LocalHaptics = compositionLocalOf { true }

/** Сила отклика: «лёгкая» вдвое слабее и без длинных рисунков, «полная» — как раньше. */
val LocalHapticLevel = compositionLocalOf { com.texfi.w0y.data.HapticLevel.FULL }

/**
 * События с собственным рисунком вибрации.
 *
 * Характер подобран под утилиту, а не под игру: рисунки короткие и сухие,
 * но у каждого события свой, чтобы вслепую отличать «включил» от
 * «выключил», а «лайк» от «ошибки».
 */
enum class Buzz(
    private val timings: LongArray,
    private val amplitudes: IntArray,
    val fallback: HapticFeedbackType,
) {
    TAP(longArrayOf(0, 9), intArrayOf(0, 110), HapticFeedbackType.TextHandleMove),
    SELECT(longArrayOf(0, 15), intArrayOf(0, 170), HapticFeedbackType.LongPress),
    ON(longArrayOf(0, 8, 30, 14), intArrayOf(0, 80, 0, 190), HapticFeedbackType.LongPress),
    OFF(longArrayOf(0, 14, 30, 8), intArrayOf(0, 150, 0, 60), HapticFeedbackType.TextHandleMove),
    LIKE(longArrayOf(0, 12, 60, 12, 110, 22), intArrayOf(0, 120, 0, 120, 0, 230), HapticFeedbackType.LongPress),
    DONE(longArrayOf(0, 10, 40, 10, 40, 30), intArrayOf(0, 100, 0, 160, 0, 255), HapticFeedbackType.LongPress),
    ERROR(longArrayOf(0, 35, 50, 35, 50, 35), intArrayOf(0, 200, 0, 200, 0, 200), HapticFeedbackType.LongPress),
    TRACK(longArrayOf(0, 6, 25, 16), intArrayOf(0, 90, 0, 200), HapticFeedbackType.LongPress),
    EXPAND(longArrayOf(0, 6, 20, 8, 20, 12, 20, 18), intArrayOf(0, 50, 0, 90, 0, 150, 0, 220), HapticFeedbackType.LongPress),
    MINIMIZE(longArrayOf(0, 18, 20, 12, 20, 8, 20, 6), intArrayOf(0, 220, 0, 150, 0, 90, 0, 50), HapticFeedbackType.LongPress),
    EGG(longArrayOf(0, 10, 40, 10, 40, 10, 40, 60), intArrayOf(0, 120, 0, 160, 0, 200, 0, 255), HapticFeedbackType.LongPress),
    WELCOME(longArrayOf(0, 30, 70, 14), intArrayOf(0, 180, 0, 90), HapticFeedbackType.LongPress),
    ;

    internal fun effect(hasAmplitude: Boolean, light: Boolean = false): VibrationEffect {
        // Лёгкая вибрация: слабее вдвое, и только короткие отклики — длинные
        // рисунки (лайк, готово, ошибка) сворачиваются в одно касание.
        val t = if (light && timings.size > 4) longArrayOf(0, timings[1]) else timings
        val a = if (light) IntArray(minOf(amplitudes.size, t.size)) { amplitudes[it] / 2 } else amplitudes
        return if (hasAmplitude) {
            VibrationEffect.createWaveform(t, a, -1)
        } else {
            VibrationEffect.createWaveform(t, -1)
        }
    }
}

private fun vibratorOf(context: Context): Vibrator? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

private fun buzz(vibrator: Vibrator?, feedback: HapticFeedback, kind: Buzz, light: Boolean) {
    // Цепочка деградации: свой рисунок → системный отклик, если железо
    // не умеет вибрировать по паттерну.
    val played =
        vibrator != null &&
            vibrator.hasVibrator() &&
            runCatching { vibrator.vibrate(kind.effect(vibrator.hasAmplitudeControl(), light)) }.isSuccess
    if (!played) feedback.performHapticFeedback(kind.fallback)
}

/** Проигрыватель рисунков: `val haptic = rememberHaptics(); haptic(Buzz.LIKE)`. */
@Composable
fun rememberHaptics(): (Buzz) -> Unit {
    val enabled = LocalHaptics.current
    val level = LocalHapticLevel.current
    val feedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val vibrator = remember(context) { vibratorOf(context) }
    return remember(enabled, level, feedback, vibrator) {
        { kind -> if (enabled && level != com.texfi.w0y.data.HapticLevel.OFF) buzz(vibrator, feedback, kind, level == com.texfi.w0y.data.HapticLevel.LIGHT) }
    }
}

/** Короткий отклик на нажатие. */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val haptic = rememberHaptics()
    return remember(haptic) { { haptic(Buzz.TAP) } }
}

/** Отклик посильнее: смена экрана, переключение раздела, готовое действие. */
@Composable
fun rememberSelectHaptic(): () -> Unit {
    val haptic = rememberHaptics()
    return remember(haptic) { { haptic(Buzz.SELECT) } }
}
