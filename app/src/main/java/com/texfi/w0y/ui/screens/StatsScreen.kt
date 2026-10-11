package com.texfi.w0y.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.StatsPeriod
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.FillText
import com.texfi.w0y.ui.components.SegmentedBar
import com.texfi.w0y.ui.components.EmptyState
import com.texfi.w0y.ui.components.Gutter
import com.texfi.w0y.ui.components.ListenChart
import com.texfi.w0y.ui.components.PixelSegmented
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SectionHeader
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.screenBackground
import com.texfi.w0y.ui.theme.PixelBigNumber
import com.texfi.w0y.ui.theme.PixelSectionLabel

/**
 * Итоги прослушивания.
 *
 * Считается прямо на телефоне из локальной истории, поэтому доступно в
 * любой день, а не раз в год, и не зависит от того, что о тебе думает
 * сервис.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground(),
    ) {
        ScreenTitle(title = stringResource(R.string.stats_title), onBack = onBack)
        Column(Modifier.padding(horizontal = Gutter)) {
            PixelSegmented(
                options = StatsPeriod.entries.map { stringResource(it.label) },
                selectedIndex = StatsPeriod.entries.indexOf(stats.period),
                onSelect = { viewModel.setPeriod(StatsPeriod.entries[it]) },
            )
            Spacer(Modifier.height(18.dp))
        }

        if (stats.plays == 0) {
            EmptyState(
                sprite = Sprites.stats,
                title = stringResource(R.string.stats_empty_title),
                text = stringResource(R.string.stats_empty_text),
            )
            return
        }

        LazyColumn(
            Modifier.padding(horizontal = Gutter),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = com.texfi.w0y.ui.components.LocalBottomInset.current),
        ) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    Metric(stringResource(R.string.stats_minutes), stats.minutes.toString(), Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Metric(stringResource(R.string.stats_plays), stats.plays.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(22.dp))
                SectionHeader(stringResource(R.string.stats_chart_title))
                Spacer(Modifier.height(10.dp))
                ListenChart(bars = stats.bars, bucket = stats.bucket)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.stats_rule_note), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                if (stats.hasLegacy) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.stats_legacy_note), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                Spacer(Modifier.height(22.dp))
            }

            if (stats.topArtists.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.stats_artists))
                    Spacer(Modifier.height(10.dp))
                }
                itemsIndexed(stats.topArtists, key = { _, it -> "artist-${it.artist}" }) { index, row ->
                    // Полоса длиной от лидера: видно соотношение, а не только цифру.
                    val share = row.plays.toFloat() / stats.topArtists.first().plays.toFloat()
                    Column(Modifier.padding(vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                style = PixelSectionLabel,
                                color = colors.textMuted,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                row.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${row.plays}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        SegmentedBar(
                            progress = { share },
                            lit = colors.accent,
                            dim = colors.surfaceHigh,
                            modifier = Modifier.padding(start = 24.dp).fillMaxWidth(),
                            height = 5.dp,
                            cell = 8.dp,
                        )
                    }
                }
            }

            if (stats.topSongs.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader(stringResource(R.string.artist_songs))
                    Spacer(Modifier.height(10.dp))
                }
                itemsIndexed(stats.topSongs, key = { _, it -> "song-${it.first.id}" }) { index, (song, plays) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = PixelSectionLabel,
                            color = colors.textMuted,
                            modifier = Modifier.width(24.dp),
                        )
                        CoverImage(song.thumbnailUrl, Thumbnails.ROW, Modifier.width(40.dp).height(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                maxLines = 1,
                            )
                        }
                        Text(
                            "×$plays",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondary,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Column(
        modifier
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Text(label, style = PixelSectionLabel, color = colors.accent)
        Spacer(Modifier.height(10.dp))
        // Число заливается снизу вверх поверх бледной копии — момент, когда
        // цифра «набралась», виден, а не подменяется мгновенным скачком.
        FillText(value, style = PixelBigNumber, ghost = colors.border, fill = colors.text)
    }
}
