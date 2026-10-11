package com.texfi.w0y.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.ArtistTile
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.ReleaseTile
import com.texfi.w0y.ui.components.SkeletonRow
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.screenBackground
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.PixelTitle

/** Страница артиста: шапка с портретом, треки, релизы, похожие. */
@Composable
fun ArtistScreen(
    route: BrowseRoute.Artist,
    onBack: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(key = "artist-${route.browseId}"),
) {
    val colors = LocalW0yColors.current
    val navigator = LocalBrowseNavigator.current
    val page by viewModel.artist.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pinned by viewModel.pinnedKeys.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val loadingAll by viewModel.loadingAllSongs.collectAsStateWithLifecycle()

    LaunchedEffect(route.browseId) { viewModel.loadArtist(route.browseId) }

    // Пока полный список не запрошен, играет и показывается верхушка со
    // страницы; как только он пришёл — всё дальше идёт по нему, включая
    // «включить» и «перемешать».
    val songs = allSongs.ifEmpty { page?.songs.orEmpty() }
    val hasMore = page?.allSongsBrowseId != null && allSongs.isEmpty()
    val name = page?.name?.takeIf { it.isNotBlank() } ?: route.name
    val isPinned = PinEntity.key(PinEntity.KIND_ARTIST, route.browseId) in pinned

    LazyColumn(
        Modifier
            .fillMaxSize()
            .screenBackground(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = com.texfi.w0y.ui.components.LocalBottomInset.current),
    ) {
        item {
            Hero(
                thumbnailUrl = page?.thumbnailUrl ?: route.thumbnailUrl,
                title = name,
                subtitle = page?.subtitle,
                onBack = onBack,
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelButton(
                    text = stringResource(R.string.album_play),
                    onClick = { viewModel.play(songs, 0) },
                    enabled = songs.isNotEmpty(),
                )
                Spacer(Modifier.width(10.dp))
                PixelButton(
                    text = stringResource(R.string.queue_mode_shuffle),
                    onClick = { viewModel.shuffle(songs) },
                    enabled = songs.isNotEmpty(),
                    fill = colors.surfaceHigh,
                )
                Spacer(Modifier.weight(1f))
                SpriteButton(
                    rows = Sprites.pin,
                    active = isPinned,
                    size = 24,
                    onClick = {
                        viewModel.togglePin(
                            kind = PinEntity.KIND_ARTIST,
                            id = route.browseId,
                            title = name,
                            subtitle = null,
                            thumbnailUrl = page?.thumbnailUrl ?: route.thumbnailUrl,
                        )
                    },
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        if (error != null) {
            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
                    Spacer(Modifier.height(10.dp))
                    PixelButton(text = stringResource(R.string.common_retry), onClick = { viewModel.retry(route.browseId, isArtist = true) })
                }
            }
        } else if (page == null) {
            items(6) { SkeletonRow(Modifier.padding(horizontal = 18.dp)) }
        }

        if (songs.isNotEmpty()) {
            item {
                BrowseSection(
                    // Когда список полный, число в заголовке — это ответ на
                    // вопрос «сколько их всего», а не украшение.
                    text =
                        if (allSongs.isEmpty()) {
                            stringResource(R.string.artist_songs)
                        } else {
                            stringResource(R.string.artist_songs_count, songs.size)
                        },
                )
            }
            items(
                items = if (allSongs.isEmpty()) songs.take(PREVIEW_SONGS) else songs,
                key = { it.id },
            ) { song ->
                SongRow(
                    song = song,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    onClick = { viewModel.play(songs, songs.indexOf(song)) },
                    actions = { DownloadButton(song.id, onDownload = { viewModel.download(song) }) },
                )
            }
            if (hasMore) {
                item {
                    PixelButton(
                        text =
                            if (loadingAll) {
                                stringResource(R.string.artist_all_songs_loading)
                            } else {
                                stringResource(R.string.artist_all_songs)
                            },
                        onClick = viewModel::loadAllSongs,
                        enabled = !loadingAll,
                        fill = colors.surfaceHigh,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }

        page?.releases?.takeIf { it.isNotEmpty() }?.let { releases ->
            item {
                BrowseSection(stringResource(R.string.artist_releases))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(releases, key = { it.browseId }) { card ->
                        ReleaseTile(card) {
                            navigator.open(
                                BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl),
                            )
                        }
                    }
                }
            }
        }

        page?.similar?.takeIf { it.isNotEmpty() }?.let { similar ->
            item {
                BrowseSection(stringResource(R.string.artist_similar))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(similar, key = { it.browseId }) { card ->
                        ArtistTile(card) {
                            navigator.open(
                                BrowseRoute.Artist(card.browseId, card.name, card.thumbnailUrl),
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

/**
 * Шапка страницы: портрет во всю ширину, к низу растворяющийся в фон.
 * Название лежит поверх картинки, поэтому градиент здесь не украшение —
 * без него текст не читается на светлых портретах.
 */
@Composable
private fun Hero(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        CoverImage(
            url = thumbnailUrl,
            px = Thumbnails.HERO,
            corner = 0,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.4f to Color.Transparent,
                        1f to colors.background,
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            SpriteButton(Sprites.chevronLeft, onClick = onBack, size = 24)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(title, style = PixelTitle, color = colors.text)
            subtitle?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun BrowseSection(text: String) {
    val colors = LocalW0yColors.current
    Column {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "❯ $text",
            style = PixelSectionLabel,
            color = colors.accent,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

/** Сколько треков показывать до нажатия «все треки». */
private const val PREVIEW_SONGS = 12
