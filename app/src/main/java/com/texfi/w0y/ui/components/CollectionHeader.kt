package com.texfi.w0y.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.StatsRepository
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelTitle
import com.texfi.w0y.ui.theme.styleTokens

/**
 * Шапка плейлиста как у альбома: обложка (своя или мозаика из первых
 * треков), название, владелец, число треков, общая длительность и три
 * действия. Длительность — сумма тех треков, у которых она известна;
 * если не известна ни у одного, её не показываем, а не пишем ноль.
 */
@Composable
fun CollectionHeader(
    title: String,
    owner: String?,
    coverUrl: String?,
    songs: List<SongItem>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalW0yColors.current
    val seconds = songs.sumOf { StatsRepository.durationSeconds(it.durationText) }
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(coverUrl, songs)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = PixelTitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                owner?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                val count = stringResource(R.string.collection_tracks, songs.size)
                Text(
                    if (seconds > 0) "$count · ${formatLength(seconds)}" else count,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelButton(stringResource(R.string.album_play), onClick = onPlay, enabled = songs.isNotEmpty())
            PixelButton(stringResource(R.string.album_shuffle), onClick = onShuffle, enabled = songs.isNotEmpty(), fill = colors.secondary)
            Spacer(Modifier.weight(1f))
            if (songs.isNotEmpty()) SpriteButton(Sprites.download, onClick = onDownload, size = 22)
        }
    }
}

@Composable
private fun Cover(url: String?, songs: List<SongItem>) {
    val size = 104.dp
    val tiles = songs.mapNotNull { it.thumbnailUrl }.distinct().take(4)
    if (url != null || tiles.size < 4) {
        CoverImage(url ?: tiles.firstOrNull(), Thumbnails.TILE, Modifier.size(size), corner = 6)
        return
    }
    // Мозаика 2×2 из первых обложек — у своего плейлиста своей картинки нет.
    Column(Modifier.size(size).clip(styleTokens.cover)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.weight(1f)) {
                row.forEach { tile ->
                    Box(Modifier.weight(1f)) {
                        CoverImage(tile, Thumbnails.ROW, Modifier.size(size / 2), corner = 0)
                    }
                }
            }
        }
    }
}

internal fun formatLength(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}
