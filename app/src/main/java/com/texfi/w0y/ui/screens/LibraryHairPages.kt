package com.texfi.w0y.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.components.ConfirmDialog
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.EmptyState
import com.texfi.w0y.ui.components.HairGroupHeader
import com.texfi.w0y.ui.components.HairStat
import com.texfi.w0y.ui.components.HairStatStrip
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.components.hairStagger
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * История в Hairline: треки по дням — «Сегодня», «Вчера», дальше дата. Справа у строки время,
 * когда трек играл последний раз; сверху — сколько треков в истории и сколько из них сегодня.
 */
@Composable
internal fun HairHistory(viewModel: LibraryViewModel) {
    val songs by viewModel.recent.collectAsStateWithLifecycle()
    val times by viewModel.recentTimes.collectAsStateWithLifecycle()
    val colors = LocalW0yColors.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val days = remember(songs, times) { HistoryDays.group(songs, times, zone) }
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }
    val timeFormat = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val labelToday = stringResource(R.string.day_today)
    val labelYesterday = stringResource(R.string.day_yesterday)
    val labelEarlier = stringResource(R.string.day_earlier)

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenTitle(title = stringResource(R.string.library_history).lowercase(), onBack = viewModel::back, horizontalPadding = 0.dp) {
            if (songs.isNotEmpty()) {
                SpriteButton(Sprites.download, onClick = { viewModel.downloadAll(songs) })
                Spacer(Modifier.width(12.dp))
                var askClear by remember { mutableStateOf(false) }
                SpriteButton(Sprites.trash, onClick = { askClear = true })
                if (askClear) {
                    ConfirmDialog(
                        title = stringResource(R.string.rm_clear_history_title),
                        text = stringResource(R.string.rm_clear_history_text, songs.size),
                        confirm = stringResource(R.string.rm_clear_button),
                        dismiss = stringResource(R.string.library_cancel),
                        onConfirm = { askClear = false; viewModel.clearHistory() },
                        onDismiss = { askClear = false },
                    )
                }
            }
        }
        if (songs.isEmpty()) {
            EmptyState(
                sprite = Sprites.library,
                title = stringResource(R.string.library_empty_title),
                text = stringResource(R.string.library_empty_text),
            )
            return
        }
        HairStatStrip(
            listOf(
                HairStat(songs.size, stringResource(R.string.stat_tracks)),
                HairStat(days.firstOrNull { it.day == today }?.songs?.size ?: 0, labelToday),
            ),
        )
        Spacer(Modifier.height(4.dp))
        LazyColumn {
            var row = 0
            days.forEach { group ->
                val label =
                    when (group.day) {
                        null -> labelEarlier
                        today -> labelToday
                        today.minusDays(1) -> labelYesterday
                        else -> group.day.format(dayFormat)
                    }
                item(key = "day-${group.day?.toEpochDay() ?: "x"}") {
                    HairGroupHeader(label, Modifier.animateItem(), trailing = group.songs.size.toString())
                }
                val start = row
                itemsIndexed(group.songs, key = { _, song -> song.id }) { i, song ->
                    val at = times[song.id]
                    SongRow(
                        song = song,
                        onClick = { viewModel.play(songs, songs.indexOf(song)) },
                        modifier = Modifier.animateItem().hairStagger(start + i),
                        actions = {
                            if (at != null) {
                                Text(Instant.ofEpochMilli(at).atZone(zone).format(timeFormat), style = HairLabelStyle, color = colors.textMuted)
                                Spacer(Modifier.width(10.dp))
                            }
                            DownloadButton(song.id, onDownload = { viewModel.download(song) })
                        },
                    )
                }
                row += group.songs.size
            }
        }
    }
}

/** Разбивка истории по дням: чистая функция, порядок треков сохраняется. */
internal object HistoryDays {
    data class Day(val day: LocalDate?, val songs: List<SongItem>)

    fun group(songs: List<SongItem>, times: Map<String, Long>, zone: ZoneId): List<Day> {
        val out = ArrayList<Day>()
        var current: LocalDate? = null
        var bucket = ArrayList<SongItem>()
        var started = false
        for (song in songs) {
            val day = times[song.id]?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            if (started && day != current) {
                out += Day(current, bucket)
                bucket = ArrayList()
            }
            current = day
            started = true
            bucket += song
        }
        if (started) out += Day(current, bucket)
        return out
    }
}
