package com.texfi.w0y.data

import java.util.Locale

/**
 * Числа в интерфейсе: одинаково на всех экранах и в любой локали.
 *
 * Раньше «1.00×» на одном экране выходило «1,00×» на другом (системная
 * локаль), а на метке трека — «1×». Теперь формат один, и десятичный
 * разделитель всегда точка: так же, как в названиях версий («0.85×»).
 */
object NumberText {
    /** Скорость: «1.00×», «0.85×». */
    fun speed(value: Float): String = String.format(Locale.ROOT, "%.2f\u00d7", value)

    /** Тон: «1.05». */
    fun pitch(value: Float): String = String.format(Locale.ROOT, "%.2f", value)
}
