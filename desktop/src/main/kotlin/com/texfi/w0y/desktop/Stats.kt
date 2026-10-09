package com.texfi.w0y.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.texfi.w0y.desktop.i18n.S
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class Period(val key: String, val days: Int?) {
    WEEK("stats_period_week", 7),
    MONTH("stats_period_month", 30),
    YEAR("stats_period_year", 365),
    ALL("stats_period_all", null),
}

/** Статистика по журналу прослушиваний: минуты, число прослушиваний, любимые треки и исполнители. */
@Composable
fun StatsScreen(app: AppState) {
    var period by remember { mutableStateOf(Period.WEEK) }
    val zone = ZoneId.systemDefault()
    val since = period.days?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L
    val plays = app.lib.playLog.filter { it.at >= since }
    ScreenFrame(S("stats_title"), app, canBack = true) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom + 24.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Period.entries.forEach { p -> ChoiceChip(S(p.key), p == period) { period = p } }
                }
            }
            if (plays.isEmpty()) {
                item {
                    Txt(S("stats_empty_title"), pixel = true, size = 12, color = C.Blue, modifier = Modifier.padding(top = 8.dp))
                    Txt(S("stats_empty_text"), color = C.Muted, size = 13, maxLines = 3, modifier = Modifier.padding(top = 6.dp))
                }
                return@LazyColumn
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Big(S("stats_minutes"), "${plays.sumOf { it.seconds } / 60}", Modifier.weight(1f))
                    Big(S("stats_plays"), "${plays.size}", Modifier.weight(1f))
                    Big(S("stats_songs"), "${plays.map { it.song.id }.distinct().size}", Modifier.weight(1f))
                }
            }
            item {
                PixelCard(Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel(S("stats_chart_title"))
                        Gap(h = 10)
                        val days = (period.days ?: 30).coerceAtMost(30)
                        val today = LocalDate.now(zone)
                        val perDay = (days - 1 downTo 0).map { back ->
                            val day = today.minusDays(back.toLong())
                            plays.filter { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == day }.sumOf { it.seconds } / 60f
                        }
                        val max = (perDay.maxOrNull() ?: 0f).coerceAtLeast(1f)
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth().height(120.dp).drawBehind {
                                val slot = size.width / perDay.size
                                val w = slot * 0.7f
                                perDay.forEachIndexed { i, v ->
                                    val h = (size.height * v / max).coerceAtLeast(if (v > 0) 3.dp.toPx() else 0f)
                                    drawRoundRect(C.Blue, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(if (Look.smooth) w / 2 else 0f))
                                }
                            },
                        )
                    }
                }
            }
            item { SectionLabel(S("stats_songs"), Modifier.padding(top = 4.dp)) }
            val topSongs = plays.groupBy { it.song.id }.entries.sortedByDescending { it.value.size }.take(10)
            items(topSongs.size) { i ->
                val (_, list) = topSongs[i]
                Top(i + 1, list.first().song.title, list.first().song.artist, "${list.size}", list.first().song.thumb)
            }
            item { SectionLabel(S("stats_artists"), Modifier.padding(top = 8.dp)) }
            val topArtists = plays.groupBy { it.song.artist.substringBefore(',').trim() }.entries.sortedByDescending { it.value.size }.take(10)
            items(topArtists.size) { i ->
                val (name, list) = topArtists[i]
                Top(i + 1, name, S("stats_minutes_short", list.sumOf { it.seconds } / 60), "${list.size}", null)
            }
            item { Txt(S("stats_rule_note"), color = C.Muted, size = 11, maxLines = 4) }
        }
    }
}

@Composable
private fun Big(label: String, value: String, modifier: Modifier) {
    PixelCard(modifier) {
        Column {
            SectionLabel(label)
            Gap(h = 8)
            Txt(value, size = 28, weight = FontWeight.Bold, color = C.Blue)
        }
    }
}

@Composable
private fun Top(rank: Int, title: String, subtitle: String, count: String, thumb: String?) {
    RowCenter(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Txt("$rank", color = C.Muted, size = 13, modifier = Modifier.padding(end = 12.dp))
        if (thumb != null) {
            Cover(thumb, 38.dp)
            Gap(w = 12)
        }
        Column(Modifier.weight(1f)) {
            Txt(title, size = 14)
            Txt(subtitle, color = C.Muted, size = 12)
        }
        Txt(count, color = C.Blue, size = 14, weight = FontWeight.Medium)
    }
}
