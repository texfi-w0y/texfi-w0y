package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Что можно сделать с треком: одна панель на поиск, плеер и списки —
 * чтобы действия над треком были одинаковы везде, где он встречается.
 *
 * Очередь здесь же, а не отдельной панелью: «следующим» и «в плейлист» —
 * это одно и то же движение мысли («не сейчас, но скоро»), и разводить
 * их по разным окнам значит заставлять выбирать окно до выбора действия.
 */
@Composable
fun AddToPlaylistPanel(
    song: SongItem,
    playlists: List<Pair<Long, String>>,
    newName: String?,
    onNewNameChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    /** Переход к исполнителю; у фита кнопка на каждого. */
    onArtist: ((com.texfi.w0y.data.ArtistLink) -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .fillMaxSize()
            // Под стеклянной панелью экран размыт ею самой: затемнять его
            // почти до черноты тогда незачем.
            .background(colors.shadow.copy(alpha = if (com.texfi.w0y.ui.theme.LocalPanelBackdrop.current != null) 0.35f else 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PixelCard(
            label = stringResource(R.string.track_panel_title),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            if (onPlayNext != null || onEnqueue != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    onPlayNext?.let {
                        PixelButton(text = stringResource(R.string.queue_play_next), onClick = it)
                    }
                    onEnqueue?.let {
                        PixelButton(
                            text = stringResource(R.string.queue_enqueue),
                            onClick = it,
                            fill = colors.surfaceHigh,
                        )
                    }
                }
                val artists = if (onArtist != null) song.artistLinks() else emptyList()
                if (onDownload != null || artists.isNotEmpty() || onAlbum != null || onCopyLink != null) {
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        onDownload?.let {
                            PixelButton(text = stringResource(R.string.track_download), onClick = it, fill = colors.surfaceHigh)
                        }
                        onCopyLink?.let {
                            PixelButton(text = stringResource(R.string.copy_link), onClick = it, fill = colors.surfaceHigh)
                        }
                        ArtistButtons(artists) { onArtist?.invoke(it) }
                        onAlbum?.let {
                            PixelButton(text = stringResource(R.string.player_to_album), onClick = it, fill = colors.surfaceHigh)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "❯ ${stringResource(R.string.add_to_playlist_title)}",
                    style = com.texfi.w0y.ui.theme.PixelSectionLabel,
                    color = colors.accent,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (playlists.isEmpty() && newName == null) {
                Text(
                    stringResource(R.string.add_to_playlist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(10.dp))
            }
            // Обычная колонка, а не LazyColumn: ленивый список занимает всю
            // разрешённую высоту даже пустым, и панель растягивалась бы.
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                playlists.forEach { (id, name) ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(id) }
                                .padding(vertical = 8.dp),
                    )
                }
            }
            if (newName != null) {
                BasicTextField(
                    value = newName,
                    onValueChange = onNewNameChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                PixelButton(
                    text = stringResource(R.string.add_to_playlist_create),
                    onClick = { if (newName.isNotBlank()) onCreate(newName) },
                )
            } else {
                PixelButton(text = stringResource(R.string.add_to_playlist_new), onClick = { onNewNameChange("") })
            }
        }
    }
}

/**
 * Кнопки перехода к исполнителям: один — «к артисту», у фита — по кнопке
 * с именем каждого, чтобы открыть любого, а не только первого.
 */
@Composable
fun ArtistButtons(
    artists: List<com.texfi.w0y.data.ArtistLink>,
    onOpen: (com.texfi.w0y.data.ArtistLink) -> Unit,
) {
    val colors = LocalW0yColors.current
    if (artists.size == 1) {
        PixelButton(text = stringResource(R.string.player_to_artist), onClick = { onOpen(artists[0]) }, fill = colors.surfaceHigh)
        return
    }
    artists.forEach { artist ->
        PixelButton(text = artist.name, onClick = { onOpen(artist) }, fill = colors.surfaceHigh)
    }
}
