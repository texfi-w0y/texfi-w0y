package com.texfi.w0y.ui.screens

import com.texfi.w0y.ui.theme.styledSurface
import com.texfi.w0y.ui.theme.styledClip
import com.texfi.w0y.ui.theme.styledBorder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.SectionHeader
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.AddToPlaylistPanel
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.EmptyState
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSegmented
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SkeletonRow
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlin.random.Random

/**
 * Поиск.
 *
 * Порядок на экране — порядок ожидания: то, что уже есть на телефоне,
 * появляется сразу, подсказки YouTube — через сто пятьдесят миллисекунд
 * набора, сама выдача — когда придёт. Так экран никогда не бывает пустым
 * в ответ на нажатие.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val colors = LocalW0yColors.current
    val navigator = LocalBrowseNavigator.current
    val keyboard = LocalSoftwareKeyboardController.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val recentQueries by viewModel.recentQueries.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val localResults by viewModel.localResults.collectAsStateWithLifecycle()
    val hideExplicit by viewModel.hideExplicit.collectAsStateWithLifecycle()
    val diagnosis by viewModel.diagnosis.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var pickPlaylistFor by remember { mutableStateOf<SongItem?>(null) }
    var newPlaylistName by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = stringResource(R.string.tab_search), horizontalPadding = 0.dp)
        SearchField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            onClear = { viewModel.onQueryChange("") },
            onSubmit = {
                viewModel.submit()
                keyboard?.hide()
                focused = false
            },
            onFocusChange = { focused = it },
        )

        // Подсказки лежат поверх результатов, а не сдвигают их: список,
        // прыгающий вниз на каждую букву, невозможно читать.
        AnimatedVisibility(
            visible = focused && suggestions.isNotEmpty(),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100)),
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                suggestions.forEach { suggestion ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.submit(suggestion)
                                keyboard?.hide()
                                focused = false
                            }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelSprite(
                            rows = Sprites.search,
                            color = colors.textMuted,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.text,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PixelSegmented(
            options = SearchFilter.entries.map { stringResource(it.label) },
            selectedIndex = SearchFilter.entries.indexOf(filter),
            onSelect = { viewModel.onFilterChange(SearchFilter.entries[it]) },
        )
        Spacer(Modifier.height(14.dp))

        when (val current = state) {
            SearchState.Idle ->
                Column {
                    Hint(stringResource(R.string.search_hint))
                    if (recentQueries.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.search_recent),
                                style = PixelSectionLabel,
                                color = colors.accent,
                                modifier = Modifier.weight(1f),
                            )
                            SpriteButton(Sprites.trash, onClick = viewModel::clearHistory)
                        }
                        Spacer(Modifier.height(4.dp))
                        recentQueries.forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.submit(entry) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = entry,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.text,
                                    modifier = Modifier.weight(1f),
                                )
                                SpriteButton(Sprites.close, onClick = { viewModel.forgetQuery(entry) }, size = 14)
                            }
                        }
                    }
                }

            SearchState.Loading ->
                // Пока идёт запрос, уже видно то, что нашлось на телефоне:
                // ожидание перестаёт быть пустым экраном.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (localResults.isNotEmpty()) {
                        LocalBlock(
                            songs = localResults,
                            onPlay = { index -> viewModel.playFrom(localResults, index) },
                        )
                    }
                    repeat(if (localResults.isEmpty()) 6 else 3) { SkeletonRow() }
                }

            is SearchState.Failed ->
                Column {
                    if (localResults.isNotEmpty()) {
                        LocalBlock(
                            songs = localResults,
                            onPlay = { index -> viewModel.playFrom(localResults, index) },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    Hint(current.message)
                    current.detail?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        PixelButton(
                            text = stringResource(R.string.common_retry),
                            onClick = viewModel::retry,
                        )
                        Spacer(Modifier.width(12.dp))
                        PixelButton(
                            text = stringResource(R.string.search_diagnostics),
                            onClick = viewModel::diagnose,
                            fill = colors.surfaceHigh,
                        )
                    }
                    diagnosis?.let { text ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }

            is SearchState.Results ->
                // Фильтр здесь, а не в запросе: YouTube не умеет отдавать
                // выдачу без «E», а перезапрашивать при переключении
                // настройки — лишний поход в сеть.
                if (current.isEmpty) {
                    EmptyState(
                        sprite = Sprites.search,
                        title = stringResource(R.string.search_nothing_title),
                        text = stringResource(R.string.search_nothing_text),
                    )
                } else {
                    val songs = current.songs.filterNot { hideExplicit && it.explicit }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = com.texfi.w0y.ui.components.LocalBottomInset.current)) {
                        // Своё — выше чужого: то, что человек уже слушал,
                        // он ищет чаще, чем что-то новое.
                        val mine = localResults.filterNot { own -> songs.any { it.id == own.id } }
                        if (mine.isNotEmpty()) {
                            item {
                                LocalBlock(
                                    songs = mine,
                                    onPlay = { index -> viewModel.playFrom(mine, index) },
                                )
                            }
                        }
                        // В смешанной выдаче артист стоит выше треков: по
                        // имени исполнителя ищут его самого, а не одну песню.
                        if (current.artists.isNotEmpty() && current.songs.isNotEmpty()) {
                            item { SectionLabel(stringResource(R.string.search_filter_artists)) }
                        }
                        items(current.artists.take(ARTISTS_IN_MIX), key = { "artist-${it.browseId}" }) { card ->
                            CardRow(
                                title = card.name,
                                subtitle = card.subtitle ?: stringResource(R.string.card_artist),
                                thumbnailUrl = card.thumbnailUrl,
                                round = true,
                                onClick = {
                                    navigator.open(
                                        BrowseRoute.Artist(card.browseId, card.name, card.thumbnailUrl),
                                    )
                                },
                            )
                        }
                        if (songs.isNotEmpty() && current.artists.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(10.dp))
                                SectionLabel(stringResource(R.string.search_filter_songs))
                            }
                        }
                        // Ключ по id: без него Compose пересобирает строки
                        // при каждом обновлении списка, и прокрутка дёргается.
                        items(songs, key = { it.id }) { song ->
                            SongRow(
                                song = song,
                                onClick = { viewModel.playFrom(songs, songs.indexOf(song)) },
                                wideCover = current.videos,
                                actions = {
                                    DownloadButton(song.id, onDownload = { viewModel.download(song) })
                                    Spacer(Modifier.width(12.dp))
                                    SpriteButton(
                                        rows = Sprites.plus,
                                        onClick = { pickPlaylistFor = song },
                                    )
                                },
                            )
                        }
                        if (current.albums.isNotEmpty() && songs.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(10.dp))
                                SectionLabel(stringResource(R.string.search_filter_albums))
                            }
                        }
                        items(current.albums, key = { "album-${it.browseId}" }) { card ->
                            CardRow(
                                title = card.title,
                                subtitle =
                                    card.subtitle
                                        ?: if (card.isAlbum) {
                                            stringResource(R.string.card_album)
                                        } else {
                                            stringResource(R.string.card_playlist)
                                        },
                                thumbnailUrl = card.thumbnailUrl,
                                round = false,
                                onClick = {
                                    navigator.open(
                                        BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl),
                                    )
                                },
                            )
                        }
                        // Награда тому, кто долистал до конца: выдача
                        // кончилась, выбирать больше не из чего — пусть
                        // выберет само.
                        if (songs.size >= RANDOM_MIN) {
                            item {
                                PixelButton(
                                    text = stringResource(R.string.search_random),
                                    onClick = { viewModel.playFrom(songs, Random.nextInt(songs.size)) },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
        }
    }

    pickPlaylistFor?.let { song ->
        SearchPlaylistPanel(
            song = song,
            playlists = playlists.map { it.id to it.name },
            newName = newPlaylistName,
            onNewNameChange = { newPlaylistName = it },
            onPick = {
                viewModel.addToPlaylist(it, song)
                pickPlaylistFor = null
            },
            onCreate = {
                viewModel.createPlaylistWith(it, song)
                newPlaylistName = null
                pickPlaylistFor = null
            },
            onDismiss = {
                pickPlaylistFor = null
                newPlaylistName = null
            },
            onPlayNext = {
                viewModel.playNext(song)
                pickPlaylistFor = null
            },
            onEnqueue = {
                viewModel.enqueue(song)
                pickPlaylistFor = null
            },
        )
    }
}

/** Найденное в своей библиотеке — отдельным блоком, чтобы не путать с выдачей. */
@Composable
private fun LocalBlock(songs: List<SongItem>, onPlay: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.search_in_library))
        Spacer(Modifier.height(4.dp))
        songs.forEachIndexed { index, song ->
            SongRow(song = song, onClick = { onPlay(index) })
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Строка альбома или артиста в выдаче: та же высота, что у трека. */
@Composable
private fun CardRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    round: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = thumbnailUrl,
            px = Thumbnails.ROW,
            corner = if (round) 24 else 4,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
        }
        PixelSprite(
            rows = Sprites.chevronRight,
            color = colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .styledSurface(8)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // «Искать» на клавиатуре отправляет запрос сразу, минуя
                // паузу набора: человек уже сказал, что закончил.
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChange(it.isFocused) },
            )
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                )
            }
        }
        // Стереть набранное одним нажатием: держать backspace до конца
        // строки — то, за что ругают чужие клиенты.
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            SpriteButton(Sprites.close, onClick = onClear, size = 16)
        }
    }
}

@Composable
private fun Hint(text: String) {
    val colors = LocalW0yColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMuted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Заголовок блока внутри экрана — тот же, что у разделов главной. */
@Composable
internal fun SectionLabel(text: String) {
    SectionHeader(label = text, modifier = Modifier.padding(bottom = 8.dp))
}

/** Панель выбора плейлиста живёт поверх экрана поиска. */
@Composable
private fun SearchPlaylistPanel(
    song: SongItem,
    playlists: List<Pair<Long, String>>,
    newName: String?,
    onNewNameChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
) = AddToPlaylistPanel(
    song = song,
    playlists = playlists,
    newName = newName,
    onNewNameChange = onNewNameChange,
    onPick = onPick,
    onCreate = onCreate,
    onDismiss = onDismiss,
    onPlayNext = onPlayNext,
    onEnqueue = onEnqueue,
)

/** Меньше трёх треков — «случайный» перестаёт быть случайным. */
private const val RANDOM_MIN = 3

/** Сколько артистов пускать в смешанную выдачу перед треками. */
private const val ARTISTS_IN_MIX = 3
