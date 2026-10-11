package com.texfi.w0y.ui.screens

import com.texfi.w0y.ui.theme.styledClip
import com.texfi.w0y.ui.theme.styledBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.SkeletonRow
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.screenBackground
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.PixelTitle

/** Страница альбома: обложка, подпись и треклист с номерами. */
@Composable
fun AlbumScreen(
    route: BrowseRoute.Album,
    onBack: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(key = "album-${route.browseId}"),
) {
    val colors = LocalW0yColors.current
    val page by viewModel.album.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pinned by viewModel.pinnedKeys.collectAsStateWithLifecycle()

    LaunchedEffect(route.browseId) { viewModel.loadAlbum(route.browseId) }

    val songs = page?.songs.orEmpty()
    val title = page?.title?.takeIf { it.isNotBlank() } ?: route.title
    val cover = page?.thumbnailUrl ?: route.thumbnailUrl
    val isPinned = PinEntity.key(PinEntity.KIND_ALBUM, route.browseId) in pinned

    LazyColumn(
        Modifier
            .fillMaxSize()
            .screenBackground(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = com.texfi.w0y.ui.components.LocalBottomInset.current),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpriteButton(Sprites.chevronLeft, onClick = onBack, size = 24)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (page == null) stringResource(R.string.common_loading_caps) else stringResource(R.string.album_title),
                    style = PixelSectionLabel,
                    color = colors.textMuted,
                )
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Обложка со смещённой тенью — тот же приём, что у карточек:
                // резкий прямоугольник вместо размытой Material-подложки.
                Box(Modifier.size(208.dp)) {
                    if (!com.texfi.w0y.ui.theme.isSmooth) Box(
                        Modifier
                            .matchParentSize()
                            .offset(5.dp, 5.dp)
                            .styledClip(8)
                            .background(colors.shadow),
                    )
                    CoverImage(
                        url = cover,
                        px = Thumbnails.HERO,
                        corner = 8,
                        modifier =
                            Modifier
                                .matchParentSize()
                                .styledBorder(8),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(title, style = PixelTitle, color = colors.text)
                page?.subtitle?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PixelButton(
                        text = stringResource(R.string.album_play),
                        onClick = { viewModel.playCollection(songs, 0) },
                        enabled = songs.isNotEmpty(),
                    )
                    PixelButton(
                        text = stringResource(R.string.queue_mode_shuffle),
                        onClick = { viewModel.shuffle(songs) },
                        enabled = songs.isNotEmpty(),
                        fill = colors.surfaceHigh,
                    )
                    Spacer(Modifier.weight(1f))
                    SpriteButton(
                        rows = Sprites.download,
                        size = 22,
                        onClick = { viewModel.downloadAll(songs) },
                    )
                    SpriteButton(
                        rows = Sprites.pin,
                        active = isPinned,
                        size = 22,
                        onClick = {
                            viewModel.togglePin(
                                kind = PinEntity.KIND_ALBUM,
                                id = route.browseId,
                                title = title,
                                subtitle = page?.subtitle,
                                thumbnailUrl = cover,
                            )
                        },
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (error != null) {
            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
                    Spacer(Modifier.height(10.dp))
                    PixelButton(text = stringResource(R.string.common_retry), onClick = { viewModel.retry(route.browseId, isArtist = false) })
                }
            }
        } else if (page == null) {
            items(8) { SkeletonRow(Modifier.padding(horizontal = 18.dp)) }
        }

        itemsIndexed(songs) { index, song ->
            Row(
                Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    style = PixelSectionLabel,
                    color = colors.textMuted,
                    modifier = Modifier.width(26.dp),
                )
                SongRow(
                    song = song,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.playCollection(songs, index) },
                    actions = { DownloadButton(song.id, onDownload = { viewModel.download(song) }) },
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}
