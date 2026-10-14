package com.texfi.w0y.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.SeasonMode
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.LocalW0yColors
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

/** Две тонкие линейные фигурки сезона; ничего не рисуется вне праздника. */
@Composable
fun SeasonMark(mode: SeasonMode, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 26.dp) {
    val season = seasonOf(mode)
    if (season == Season.NONE) return
    val colors = LocalW0yColors.current
    val (first, second) = when (season) {
        Season.HALLOWEEN -> HairGlyph.PUMPKIN to HairGlyph.PUMPKIN
        else -> HairGlyph.TREE to HairGlyph.SNOWFLAKE
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        HairIcon(first, colors.accentText, Modifier.size(size))
        Spacer(Modifier.width(6.dp))
        HairIcon(second, colors.textMuted, Modifier.size(size * 0.8f))
    }
}
