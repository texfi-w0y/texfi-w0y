package com.texfi.w0y.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.size
import com.texfi.w0y.ui.theme.asLabel
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.texfi.w0y.ui.components.ConfirmDialog
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.HourlyLine
import com.texfi.w0y.ui.components.PixelButton
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
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    val recapShare by viewModel.recapShare.collectAsStateWithLifecycle()
    var recapOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Карточка итогов уходит через системное окно выбора — куда, решает человек.
    androidx.compose.runtime.LaunchedEffect(recapShare) {
        val file = recapShare ?: return@LaunchedEffect
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val send =
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(android.content.Intent.createChooser(send, null))
        }
        viewModel.consumeRecapShare()
    }
    if (recapOpen) {
        recap?.let { value ->
            // Во весь экран поверх островка и плеера: итоги — отдельный момент, а не вкладка.
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { recapOpen = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                YearRecapScreen(value, onClose = { recapOpen = false }, onShare = viewModel::shareRecap)
            }
        }
    }

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

        // Пока считается — не пишем «пусто»: это был ложный пустой экран на первом кадре.
        if (!stats.loaded) return
        if (stats.isEmpty) {
            EmptyState(
                sprite = Sprites.stats,
                title = stringResource(R.string.stats_empty_title),
                text = stringResource(R.string.stats_empty_text),
            )
            return
        }
        var confirmReset by remember { mutableStateOf(false) }
        if (confirmReset) {
            ConfirmDialog(
                title = stringResource(R.string.stats_reset_title),
                text = stringResource(R.string.stats_reset_text),
                confirm = stringResource(R.string.stats_reset_confirm),
                dismiss = stringResource(R.string.library_cancel),
                onConfirm = {
                    confirmReset = false
                    viewModel.reset()
                },
                onDismiss = { confirmReset = false },
            )
        }

        LazyColumn(
            Modifier.padding(horizontal = Gutter),
            contentPadding = com.texfi.w0y.ui.components.bottomContentPadding(),
        ) {
            item {
                RecapEntry(onOpen = {
                    viewModel.loadRecap()
                    recapOpen = true
                })
                Spacer(Modifier.height(18.dp))
            }
            item {
                Row(Modifier.fillMaxWidth()) {
                    Metric(stringResource(R.string.stats_minutes), if (stats.minutes == 0 && stats.listenedMs > 0) "<1" else stats.minutes.toString(), Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Metric(stringResource(R.string.stats_plays), stats.plays.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Metric(stringResource(R.string.stats_new_artists), stats.newArtists.toString(), Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    val peak = stats.hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
                    Metric(stringResource(R.string.stats_peak_hour), peak?.let { "%02d:00".format(it) } ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(22.dp))
                SectionHeader(stringResource(R.string.stats_chart_title))
                Spacer(Modifier.height(10.dp))
                ListenChart(bars = stats.bars, bucket = stats.bucket)
                Spacer(Modifier.height(22.dp))
                SectionHeader(stringResource(R.string.stats_hourly_title))
                Spacer(Modifier.height(10.dp))
                HourlyLine(stats.hourly)
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

            item {
                Spacer(Modifier.height(28.dp))
                PixelButton(
                    text = stringResource(R.string.stats_reset),
                    onClick = { confirmReset = true },
                    fill = colors.surfaceHigh,
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.stats_local_note), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Spacer(Modifier.height(28.dp))
            }
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

/** Вход в итоги года: строка в рамке, акцентная метка года и стрелка — видно, что это переход. */
@Composable
private fun RecapEntry(onOpen: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(look.cardRadius)
    val year = java.time.LocalDate.now().year
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(look.line, colors.outline, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)
            .testTag("recap-entry")
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.recap_card_label, year).asLabel(), style = com.texfi.w0y.ui.theme.HairLabelStyle, color = com.texfi.w0y.ui.theme.Contrast.ensure(colors.accent, colors.background))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.recap_entry), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
        com.texfi.w0y.ui.icons.HairIcon(com.texfi.w0y.ui.icons.HairGlyph.CHEVRON_RIGHT, colors.text, Modifier.size(20.dp))
    }
}
