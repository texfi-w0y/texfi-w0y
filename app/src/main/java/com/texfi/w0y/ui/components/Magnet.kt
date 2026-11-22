package com.texfi.w0y.ui.components

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Магнит включён (Настройки → Вид); выключен — всё, что тянешь, едет за пальцем.
 * Действует на нижние панели, шторку плеера, смахивание строк и мини-плеера.
 */
val LocalSheetMagnet = androidx.compose.runtime.compositionLocalOf { true }

/**
 * «Магнит» — общий отклик тяги: до [threshold] то, что тянешь, держится и идёт медленнее пальца
 * (сопротивление нарастает к порогу, мягкой кривой), за порогом отрывается — в этот момент щелчок —
 * и дальше идёт быстрее пальца, пока не догонит его, без скачка. Знак [drag] сохраняется: влево
 * тянется так же, как вправо.
 */
fun magnetPx(drag: Float, threshold: Float): Float {
    val d = abs(drag)
    if (d == 0f || threshold <= 0f) return 0f
    val held = threshold * MAGNET_HELD
    val shown =
        if (d <= threshold) {
            val t = d / threshold
            held * (1f - (1f - t) * (1f - t))
        } else {
            held + (d - threshold) * MAGNET_CATCH_UP
        }
    return shown * sign(drag)
}

/**
 * Обратное к [magnetPx]: сколько надо протянуть пальцем, чтобы стоять там, где стоит сейчас.
 * Нужно, когда тягу подхватывают на полпути — во время пружины или из чужого жеста.
 */
fun magnetDrag(shown: Float, threshold: Float): Float {
    val s = abs(shown)
    if (s == 0f || threshold <= 0f) return 0f
    val held = threshold * MAGNET_HELD
    val drag =
        if (s <= held) {
            (1f - sqrt((1f - s / held).coerceAtLeast(0f))) * threshold
        } else {
            threshold + (s - held) / MAGNET_CATCH_UP
        }
    return drag * sign(shown)
}

/** Какую долю порога магнит проходит, пока держит. */
private const val MAGNET_HELD = 0.32f

/** Насколько быстрее пальца идёт оторвавшееся, пока не догонит. */
private const val MAGNET_CATCH_UP = 1.35f
