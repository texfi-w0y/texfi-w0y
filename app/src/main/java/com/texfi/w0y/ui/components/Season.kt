package com.texfi.w0y.ui.components

import com.texfi.w0y.data.SeasonMode
import java.time.LocalDate
import java.time.Month

/** Праздник, к которому приложение сейчас подмигивает. */
enum class Season { NONE, HALLOWEEN, WINTER }

/** Какой сезон по режиму и дате: Хэллоуин — с 24 октября по 1 ноября, зима — с 15 декабря по 6 января. */
fun seasonOf(mode: SeasonMode, today: LocalDate = LocalDate.now()): Season =
    when (mode) {
        SeasonMode.OFF -> Season.NONE
        SeasonMode.HALLOWEEN -> Season.HALLOWEEN
        SeasonMode.WINTER -> Season.WINTER
        SeasonMode.AUTO -> {
            val m = today.month
            val d = today.dayOfMonth
            when {
                m == Month.OCTOBER && d >= 24 -> Season.HALLOWEEN
                m == Month.NOVEMBER && d == 1 -> Season.HALLOWEEN
                m == Month.DECEMBER && d >= 15 -> Season.WINTER
                m == Month.JANUARY && d <= 6 -> Season.WINTER
                else -> Season.NONE
            }
        }
    }
