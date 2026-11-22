package com.texfi.w0y.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * Лёгкий режим включён: без живого размытия (острова и панели плотные), фон плеера стоит,
 * сезонные эффекты и живой фон выключены. Решается в оболочке по настройке и [DeviceClass].
 */
val LocalLiteGraphics = staticCompositionLocalOf { false }

/**
 * Слабый ли телефон — для лёгкого режима «Авто». Живое размытие считает видеокарта, и у
 * недорогих телефонов (часто 4–6 ГБ памяти, слабый Mali) именно оно даёт лаги.
 *
 * Слабым считается: «телефон с малой памятью» по мнению самой системы; меньше ~5 ГБ памяти;
 * 6 ГБ без заявленного класса производительности (его заявляют быстрые телефоны, Pixel — да,
 * бюджетные — обычно нет). Ответ не меняется за время работы, считается один раз.
 */
object DeviceClass {
    @Volatile private var cached: Boolean? = null

    fun isWeak(context: Context): Boolean =
        cached ?: detect(context.applicationContext).also { cached = it }

    private fun detect(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        if (am.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gib = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        if (gib < WEAK_GIB) return true
        // Класс производительности заявляют с Android 12; ноль — не заявлен.
        val declared = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.MEDIA_PERFORMANCE_CLASS > 0
        return !declared && gib < UNDECLARED_GIB
    }

    /** 4 ГБ телефон показывает ~3,6 ГиБ, 6 ГБ — ~5,6 ГиБ. */
    private const val WEAK_GIB = 5.0

    /** 8 ГБ показывает ~7,4 ГиБ: до него без заявленного класса — слабый. */
    private const val UNDECLARED_GIB = 7.0
}

/**
 * Рисует содержимое в 1/[factor] размера и растягивает обратно. Для сильно размытых слоёв: на
 * глаз то же самое, а видеокарте в factor² раз меньше работы. Радиус размытия внутри — тоже
 * делить на [factor].
 */
fun Modifier.lowRes(factor: Int): Modifier =
    layout { measurable, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val small = measurable.measure(Constraints.fixed((w / factor).coerceAtLeast(1), (h / factor).coerceAtLeast(1)))
        layout(w, h) {
            small.placeWithLayer(0, 0) {
                scaleX = w.toFloat() / small.width
                scaleY = h.toFloat() / small.height
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
