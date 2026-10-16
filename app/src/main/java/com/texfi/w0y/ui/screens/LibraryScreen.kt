package com.texfi.w0y.ui.screens

import com.texfi.w0y.ui.theme.styledSurface
import com.texfi.w0y.ui.theme.styledClip
import com.texfi.w0y.ui.theme.styledBorder
import com.texfi.w0y.ui.components.W0yMotion
import com.texfi.w0y.ui.components.SteppedEasing
import com.texfi.w0y.ui.components.ConfirmDialog
import com.texfi.w0y.ui.components.RowAction
import com.texfi.w0y.ui.components.rememberReorderState
import com.texfi.w0y.ui.components.ReorderState
import com.texfi.w0y.ui.components.ReorderHandle
import com.texfi.w0y.ui.components.CollectionHeader
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import com.texfi.w0y.playback.DownloadFailure
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import com.texfi.w0y.ui.components.hairSurface
import com.texfi.w0y.ui.theme.asLabel
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.playback.DownloadState
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.EmptyState
import com.texfi.w0y.ui.components.Gutter
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SectionHeader
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.PixelTitle

@Composable
fun LibraryScreen(
    onOpenLogin: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    when (val current = route) {
        LibraryRoute.Root -> if (com.texfi.w0y.ui.theme.isHair) HairLibraryRoot(viewModel, onOpenLogin) else LibraryRoot(viewModel, onOpenLogin)
        is LibraryRoute.Local -> LocalPlaylist(current.playlistId, viewModel)
        is LibraryRoute.Remote -> RemotePlaylist(current, viewModel)
        LibraryRoute.Liked -> SongList(stringResource(R.string.library_likes), viewModel.liked.collectAsStateWithLifecycle().value, viewModel, collection = true)
        LibraryRoute.History -> SongList(stringResource(R.string.library_history), viewModel.recent.collectAsStateWithLifecycle().value, viewModel, onClear = viewModel::clearHistory)
        LibraryRoute.Downloads -> DownloadsList(viewModel)
        LibraryRoute.Files -> LocalFilesList(viewModel)
        LibraryRoute.Stats -> StatsScreen(onBack = viewModel::back)
    }
}

@Composable
private fun LibraryRoot(viewModel: LibraryViewModel, onOpenLogin: () -> Unit) {
    val colors = LocalW0yColors.current
    val navigator = LocalBrowseNavigator.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val accountAvatar by viewModel.accountAvatar.collectAsStateWithLifecycle()
    val accountAlbums by viewModel.accountAlbums.collectAsStateWithLifecycle()
    val covers by viewModel.playlistCovers.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val mirrorFailure by viewModel.mirrorFailure.collectAsStateWithLifecycle()
    var newPlaylist by remember { mutableStateOf<String?>(null) }

    // Плейлисты подтягиваются сами при входе в «Моё»: без ручного «обновить».
    // Повторные входы в течение минуты сверку не запускают (см. AccountSync).
    LaunchedEffect(signedIn) { if (signedIn) viewModel.sync(force = false) }

    val hairLook = com.texfi.w0y.ui.theme.isHair
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = com.texfi.w0y.ui.components.bottomContentPadding(),
    ) {
        item { ScreenTitle(title = stringResource(R.string.library_title), horizontalPadding = 0.dp) }

        item {
            PixelCard(label = stringResource(R.string.library_account), modifier = Modifier.fillMaxWidth()) {
                if (signedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Аватарка — как у ника на YouTube: круглая, слева от имени.
                        // Пока она не пришла, под картинкой видна нота-заглушка.
                        CoverImage(
                            url = accountAvatar,
                            px = Thumbnails.ROW,
                            modifier = Modifier.size(40.dp),
                            corner = 20,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = accountName ?: stringResource(R.string.library_signed_in),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.text,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row {
                        PixelButton(
                            text = if (syncing) stringResource(R.string.library_syncing) else stringResource(R.string.library_sync),
                            onClick = viewModel::sync,
                            enabled = !syncing,
                        )
                        Spacer(Modifier.width(10.dp))
                        PixelButton(text = stringResource(R.string.library_sign_out), onClick = viewModel::signOut, fill = colors.surfaceHigh)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.library_sign_in_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    PixelButton(text = stringResource(R.string.library_sign_in), onClick = onOpenLogin)
                }
                syncError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                }
                // О неудачной записи плейлиста в аккаунт говорим прямо: на
                // телефоне изменение осталось, а в YouTube — нет, и молчать
                // об этом значило бы обещать синхронизацию, которой не было.
                mirrorFailure?.let {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.library_mirror_failed, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        SpriteButton(Sprites.close, onClick = viewModel::clearMirrorFailure, size = 14)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutCard(stringResource(R.string.library_likes), "${liked.size}", Sprites.heart, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Liked)
                    }
                    ShortcutCard(stringResource(R.string.library_history), "${recent.size}", Sprites.timer, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.History)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutCard(stringResource(R.string.library_downloaded), "${downloaded.size}", Sprites.download, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Downloads)
                    }
                    ShortcutCard(stringResource(R.string.lm_card), "${files.size}", Sprites.note, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Files)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Итоги — своя статистика по локальной истории, доступная
                    // в любой день, а не раз в год «рекапом».
                    ShortcutCard(stringResource(R.string.library_stats), stringResource(R.string.library_stats_hint), Sprites.stats, Modifier.weight(1f)) {
                        viewModel.open(LibraryRoute.Stats)
                    }
                }
            }
        }

        item {
            SectionHeader(
                label = stringResource(R.string.library_playlists),
                action = { SpriteButton(Sprites.plus, onClick = { newPlaylist = "" }, active = true, size = 18) },
            )
        }

        newPlaylist?.let { value ->
            item {
                PixelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.library_playlist_name), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = value,
                        onValueChange = { newPlaylist = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        PixelButton(
                            text = stringResource(R.string.library_create),
                            onClick = {
                                if (value.isNotBlank()) viewModel.createPlaylist(value)
                                newPlaylist = null
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        PixelButton(text = stringResource(R.string.library_cancel), onClick = { newPlaylist = null }, fill = colors.surfaceHigh)
                    }
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.library_no_playlists),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }

        if (hairLook && playlists.isNotEmpty()) {
            // Hairline: плейлисты сеткой обложек, три в ряд.
            items(playlists.chunked(HAIR_PLAYLIST_COLUMNS), key = { row -> "hp-" + row.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { playlist ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { viewModel.open(LibraryRoute.Local(playlist.id)) },
                        ) {
                            CoverImage(
                                url = covers[playlist.id],
                                px = Thumbnails.TILE,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(playlist.name, style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 1)
                            Text(
                                text =
                                    when {
                                        playlist.remoteId != null && !playlist.remoteEditable -> stringResource(R.string.library_playlist_saved)
                                        playlist.remoteId != null -> stringResource(R.string.library_playlist_mirrored)
                                        else -> stringResource(R.string.library_own_playlist)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(HAIR_PLAYLIST_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else items(playlists, key = { it.id }) { playlist ->
            CollectionRow(
                title = playlist.name,
                // Сразу видно, где плейлист лежит: только на телефоне или
                // ещё и в аккаунте. Без этой строчки «синхронизация» — слово
                // из настроек, которое никак не проверить.
                subtitle =
                    when {
                        playlist.remoteId != null && !playlist.remoteEditable ->
                            stringResource(R.string.library_playlist_saved)
                        playlist.remoteId != null -> stringResource(R.string.library_playlist_mirrored)
                        else -> stringResource(R.string.library_own_playlist)
                    },
                thumbnailUrl = covers[playlist.id],
                sprite = Sprites.library,
                onClick = { viewModel.open(LibraryRoute.Local(playlist.id)) },
            )
        }

        if (accountAlbums.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                SectionHeader(stringResource(R.string.library_albums))
            }
            items(accountAlbums, key = { it.browseId }) { card ->
                CollectionRow(
                    title = card.title,
                    subtitle = card.subtitle ?: stringResource(R.string.card_album),
                    thumbnailUrl = card.thumbnailUrl,
                    sprite = Sprites.release,
                    // У альбома своя страница: обложка, год, треклист.
                    onClick = { navigator.open(BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl)) },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Строка коллекции: плейлист, альбом — с обложкой или пиксельной заглушкой. */
@Composable
private fun CollectionRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    sprite: List<String>,
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
        if (thumbnailUrl != null) {
            CoverImage(url = thumbnailUrl, px = Thumbnails.ROW, modifier = Modifier.size(44.dp))
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .styledClip(4)
                    .background(colors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                PixelSprite(rows = sprite, color = colors.accent, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
        }
        PixelSprite(rows = Sprites.chevronRight, color = colors.textMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ShortcutCard(
    label: String,
    value: String,
    sprite: List<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalW0yColors.current
    if (com.texfi.w0y.ui.theme.isHair) {
        // Hairline: крупное число героем блока, подпись моно над ним.
        val look = com.texfi.w0y.ui.theme.hairLook
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(look.cardRadius)
        val isNumber = value.all { it.isDigit() }
        Column(
            modifier
                .clip(shape)
                .hairSurface(shape)
                .border(look.line, colors.border, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(rows = sprite, color = colors.textMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label.asLabel(), style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            if (isNumber) {
                Text(value, style = com.texfi.w0y.ui.theme.hairBigNumber, color = colors.text, maxLines = 1)
            } else {
                Text(value, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2, minLines = 2)
            }
        }
        return
    }
    Box(
        modifier
            .styledSurface(8)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSprite(rows = sprite, color = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = PixelSectionLabel, color = colors.accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        }
    }
}

@Composable
private fun SongList(
    title: String,
    songs: List<SongItem>,
    viewModel: LibraryViewModel,
    onClear: (() -> Unit)? = null,
    /** Список играется как подборка — без рекомендаций в очереди. */
    collection: Boolean = false,
) {
    val colors = LocalW0yColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = title.lowercase(), onBack = viewModel::back, horizontalPadding = 0.dp) {
            if (songs.isNotEmpty()) {
                SpriteButton(Sprites.download, onClick = { viewModel.downloadAll(songs) })
                onClear?.let {
                    Spacer(Modifier.width(12.dp))
                    var askClear by remember { mutableStateOf(false) }
                    SpriteButton(Sprites.trash, onClick = { askClear = true })
                    if (askClear) {
                        ConfirmDialog(
                            title = stringResource(R.string.rm_clear_history_title),
                            text = stringResource(R.string.rm_clear_history_text, songs.size),
                            confirm = stringResource(R.string.rm_clear_button),
                            dismiss = stringResource(R.string.library_cancel),
                            onConfirm = { askClear = false; it() },
                            onDismiss = { askClear = false },
                        )
                    }
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
        LazyColumn {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { if (collection) viewModel.playCollection(songs, songs.indexOf(song)) else viewModel.play(songs, songs.indexOf(song)) },
                    // Трек уезжает из списка сам: после нажатия «удалить»
                    // должно быть видно, что удалилось именно это, а не
                    // что список перерисовался целиком.
                    modifier = Modifier.animateItem(),
                    actions = { DownloadButton(song.id, onDownload = { viewModel.download(song) }) },
                )
            }
        }
    }
}

@Composable
private fun DownloadsList(viewModel: LibraryViewModel) {
    val colors = LocalW0yColors.current
    val songs by viewModel.downloaded.collectAsStateWithLifecycle()
    val failed by viewModel.failedDownloads.collectAsStateWithLifecycle()
    val reasons by viewModel.downloadFailures.collectAsStateWithLifecycle()
    val progress by viewModel.downloads.progress.collectAsStateWithLifecycle()
    val pending = progress.values.filter { it.state != DownloadState.DONE }
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val folder by viewModel.exportFolder.collectAsStateWithLifecycle()
    // Отмена выбора папки ничего не меняет: вопрос остаётся, папка — прежней.
    val folderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(viewModel::answerExportFolder)
        }
    // Выбор начинается долгим нажатием; пока он идёт, нажатие отмечает трек.
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var selecting by remember { mutableStateOf(false) }
    val selectedSongs = songs.filter { it.id in selected }
    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }
    if (selecting) BackHandler { selecting = false; selected = emptySet() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = stringResource(R.string.downloads_title), onBack = viewModel::back, horizontalPadding = 0.dp)
        Text(
            text =
                stringResource(
                    R.string.downloads_usage,
                    (viewModel.downloads.usedBytes() / 1024 / 1024).toInt(),
                    pending.size,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        // «Где это» — вопрос, который задали первым. Ответ честный: не в
        // папке «Музыка», а внутри приложения.
        Text(
            text = stringResource(R.string.downloads_where),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        folder?.let { current ->
            if (!current.asked) {
                // Первый заход: куда сохранять на телефон — спрашиваем здесь,
                // рядом с кнопкой сохранения, а не в глубине настроек.
                PixelCard(label = stringResource(R.string.dl_folder_ask_title), modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        stringResource(R.string.dl_folder_ask_text, stringResource(R.string.dl_folder_default)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text,
                    )
                    Text(
                        stringResource(R.string.export_warning_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        PixelButton(stringResource(R.string.dl_folder_ask_pick), onClick = { folderLauncher.launch(null) })
                        PixelButton(stringResource(R.string.dl_folder_ask_keep), onClick = { viewModel.answerExportFolder(null) }, fill = colors.surfaceHigh)
                    }
                }
            } else {
                Text(
                    stringResource(R.string.dl_folder_current, folderName(current.tree) ?: stringResource(R.string.dl_folder_default)) +
                        " · " + stringResource(R.string.dl_folder_change),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accentText,
                    modifier = Modifier.clickable { folderLauncher.launch(null) }.padding(vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (selecting) {
            // Панель выбора: сколько отмечено, «все», и что с ними сделать.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.downloads_selected, selected.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(if (selected.size == songs.size) R.string.downloads_select_none else R.string.downloads_select_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accentText,
                    modifier =
                        Modifier
                            .clickable { selected = if (selected.size == songs.size) emptySet() else songs.map { it.id }.toSet() }
                            .padding(12.dp),
                )
                SpriteButton(Sprites.close, onClick = { selecting = false; selected = emptySet() })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                PixelButton(
                    if (exporting) stringResource(R.string.downloads_exporting, exportProgress.first, exportProgress.second) else stringResource(R.string.downloads_export),
                    onClick = { viewModel.exportDownloads(selectedSongs) },
                    enabled = selected.isNotEmpty() && !exporting,
                )
                PixelButton(stringResource(R.string.downloads_redownload), onClick = { viewModel.redownload(selected); selecting = false; selected = emptySet() }, enabled = selected.isNotEmpty(), fill = colors.surfaceHigh)
                PixelButton(stringResource(R.string.dl_clear_button), onClick = { viewModel.deleteDownloads(selected); selecting = false; selected = emptySet() }, enabled = selected.isNotEmpty(), fill = colors.surfaceHigh)
            }
        } else if (songs.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelButton(
                    text =
                        if (exporting) {
                            stringResource(R.string.downloads_exporting, exportProgress.first, exportProgress.second)
                        } else {
                            stringResource(R.string.downloads_export)
                        },
                    onClick = { viewModel.exportDownloads(songs) },
                    enabled = !exporting,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.downloads_select),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accentText,
                    modifier = Modifier.clickable { selecting = true }.padding(12.dp),
                )
            }
        }
        exportStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        if (songs.isEmpty() && pending.isEmpty() && failed.isEmpty()) {
            EmptyState(
                sprite = Sprites.download,
                title = stringResource(R.string.downloads_empty_title),
                text = stringResource(R.string.downloads_empty_text),
            )
            return
        }
        LazyColumn {
            if (failed.isNotEmpty()) {
                item(key = "failed-header") {
                    SectionHeader(stringResource(R.string.downloads_failed), modifier = Modifier.padding(vertical = 8.dp))
                }
                items(failed, key = { "f-" + it.id }) { song ->
                    Column(Modifier.animateItem()) {
                        SongRow(
                            song = song,
                            onClick = { viewModel.redownload(listOf(song.id)) },
                            actions = {
                                SpriteButton(Sprites.sync, onClick = { viewModel.redownload(listOf(song.id)) })
                                Spacer(Modifier.width(14.dp))
                                SpriteButton(Sprites.close, onClick = { viewModel.dismissFailure(song.id) })
                            },
                        )
                        val reason = reasons[song.id]?.let { name -> DownloadFailure.entries.firstOrNull { it.name == name } } ?: DownloadFailure.UNKNOWN
                        Text(
                            stringResource(reason.label),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                    }
                }
                item(key = "done-header") { Spacer(Modifier.height(10.dp)) }
            }
            items(songs, key = { it.id }) { song ->
                val state = progress[song.id]
                val checked = song.id in selected
                Column(Modifier.animateItem()) {
                    SongRow(
                        song = song,
                        onClick = { if (selecting) toggle(song.id) else viewModel.playCollection(songs, songs.indexOf(song)) },
                        rowActions =
                            if (selecting) {
                                emptyList()
                            } else {
                                listOf(
                                    RowAction(
                                        label = stringResource(R.string.rm_remove_download),
                                        danger = true,
                                        confirmTitle = stringResource(R.string.rm_remove_download),
                                        confirmText = stringResource(R.string.rm_remove_download_text, song.title),
                                        confirmButton = stringResource(R.string.rm_delete_button),
                                        run = { viewModel.cancelDownload(song.id) },
                                    ),
                                    RowAction(label = stringResource(R.string.rm_select_several)) {
                                        selecting = true
                                        toggle(song.id)
                                    },
                                )
                            },
                        onLongClick = {
                            selecting = true
                            toggle(song.id)
                        },
                        progressPercent = state?.percent,
                        actions = {
                            if (selecting) SelectMark(checked)
                        },
                    )
                    // Скорость и остаток — только пока качается и есть замер.
                    if (state != null && state.state == DownloadState.RUNNING && state.bytesPerSecond > 0) {
                        Text(
                            stringResource(
                                R.string.dl_speed,
                                Formatter.formatShortFileSize(LocalContext.current, state.bytesPerSecond),
                                state.remainingMs?.let { formatEta(it) } ?: "—",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Отметка выбранного трека: квадрат в Pixel, круг в Smooth — форма из токенов. */
@Composable
private fun SelectMark(checked: Boolean) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .size(22.dp)
            .clip(com.texfi.w0y.ui.theme.styleTokens.switchThumb)
            .background(if (checked) colors.accent else colors.surfaceHigh)
            .border(2.dp, if (checked) colors.accent else colors.border, com.texfi.w0y.ui.theme.styleTokens.switchThumb),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) PixelSprite(Sprites.check, colors.onAccent, Modifier.size(14.dp))
    }
}

private fun formatEta(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(1)
    return if (s >= 60) "${s / 60}:${(s % 60).toString().padStart(2, '0')}" else "0:${s.toString().padStart(2, '0')}"
}

@Composable
private fun LocalPlaylist(playlistId: Long, viewModel: LibraryViewModel) {
    val colors = LocalW0yColors.current
    val songs by viewModel.currentPlaylistSongs.collectAsStateWithLifecycle()
    val playlist by viewModel.playlists.collectAsStateWithLifecycle()
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val entry = playlist.firstOrNull { it.id == playlistId }
    val name = entry?.name ?: stringResource(R.string.playlist_title)
    // Чужой плейлист из библиотеки аккаунта YouTube править не даёт — и мы не обещаем.
    val editable = entry == null || entry.remoteId == null || entry.remoteEditable
    var renaming by remember { mutableStateOf<String?>(null) }
    val hair = com.texfi.w0y.ui.theme.isHair
    var customizing by remember(playlistId) { mutableStateOf(false) }
    val covers by viewModel.playlistCovers.collectAsStateWithLifecycle()
    if (hair && customizing && entry != null) {
        com.texfi.w0y.ui.screens.PlaylistEditor(
            entry = entry,
            canRename = editable,
            cover = covers[playlistId],
            onSave = { newName, note, accent, glyph, picked, clear ->
                viewModel.customizePlaylist(entry, newName, note, accent, glyph, picked, clear)
                customizing = false
            },
            onDelete = { viewModel.deletePlaylist(playlistId) },
            onClose = { customizing = false },
        )
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = name.lowercase(), onBack = viewModel::back, horizontalPadding = 0.dp) {
            if (hair) {
                com.texfi.w0y.ui.components.HairIconButton(
                    com.texfi.w0y.ui.icons.HairGlyph.SLIDERS,
                    stringResource(R.string.pl_customize),
                    { customizing = true },
                    tint = colors.text,
                )
            } else {
                SpriteButton(Sprites.trash, onClick = { viewModel.deletePlaylist(playlistId) })
            }
        }
        renaming?.let { value ->
            PixelCard(modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = value,
                    onValueChange = { renaming = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    PixelButton(
                        text = stringResource(R.string.playlist_rename_save),
                        onClick = {
                            if (value.isNotBlank()) viewModel.renamePlaylist(playlistId, value)
                            renaming = null
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    PixelButton(text = stringResource(R.string.library_cancel), onClick = { renaming = null }, fill = colors.surfaceHigh)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!editable) {
            Text(
                stringResource(R.string.playlist_readonly),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (editable && !hair) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (signedIn) {
                    PixelSprite(
                        rows = Sprites.sync,
                        color = if (entry?.remoteId != null) colors.accent else colors.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text =
                            if (entry?.remoteId != null) {
                                stringResource(R.string.playlist_mirrored)
                            } else {
                                stringResource(R.string.playlist_not_mirrored)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(10.dp))
                PixelButton(
                    text = stringResource(R.string.playlist_rename),
                    onClick = { renaming = name },
                    fill = colors.surfaceHigh,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        // Порядок держим у себя, пока тянут строку; в базу — один раз, когда отпустили.
        var order by remember(songs) { mutableStateOf(songs) }
        val listState = rememberLazyListState()
        val reorder =
            rememberReorderState(listState) { from, to ->
                val a = order.indexOfFirst { it.id == from }
                val b = order.indexOfFirst { it.id == to }
                if (a >= 0 && b >= 0) order = order.toMutableList().apply { add(b, removeAt(a)) }
            }
        LazyColumn(state = listState, contentPadding = com.texfi.w0y.ui.components.bottomContentPadding()) {
            item(key = "header") {
                CollectionHeader(
                    title = name,
                    owner = if (entry?.remoteId != null) accountName ?: stringResource(R.string.library_own_playlist) else stringResource(R.string.library_own_playlist),
                    coverUrl = covers[playlistId] ?: entry?.coverUrl,
                    songs = order,
                    onPlay = { viewModel.playCollection(order, 0) },
                    onShuffle = { viewModel.playCollection(order.shuffled(), 0) },
                    onDownload = { viewModel.downloadAll(order) },
                )
            }
            if (hair && !entry?.note.isNullOrBlank()) {
                item(key = "note") {
                    Text(entry?.note.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
            if (order.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.playlist_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            }
            items(order, key = { it.id }, contentType = { ReorderState.REORDER_TYPE }) { song ->
                val dragged = reorder.dragging == song.id
                with(reorder) {
                    SongRow(
                        song = song,
                        onClick = { viewModel.playCollection(order, order.indexOf(song)) },
                        modifier =
                            if (dragged) {
                                Modifier.reorderable(song.id)
                            } else {
                                // Соседи уступают место ступенями, как всё движение TexFi.
                                Modifier.animateItem(placementSpec = tween(W0yMotion.MID_MS, easing = SteppedEasing(4)))
                            },
                        rowActions =
                            if (editable) {
                                val before = order.map { it.id }
                                listOf(
                                    RowAction(
                                        label = stringResource(R.string.rm_remove_from_playlist),
                                        undoMessage = stringResource(R.string.rm_removed_from_playlist),
                                        onUndo = { viewModel.restoreToPlaylist(playlistId, song, before) },
                                        run = { viewModel.removeFromPlaylist(playlistId, song.id) },
                                    ),
                                )
                            } else {
                                emptyList()
                            },
                        actions = {
                            if (editable) {
                                ReorderHandle(reorder, song.id) {
                                    if (order.map { it.id } != songs.map { it.id }) {
                                        viewModel.reorderPlaylist(playlistId, order.map { it.id })
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RemotePlaylist(route: LibraryRoute.Remote, viewModel: LibraryViewModel) {
    val colors = LocalW0yColors.current
    val songs by viewModel.remoteSongs.collectAsStateWithLifecycle()
    val suggestions by viewModel.remoteSuggestions.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = route.card.title.lowercase(), onBack = viewModel::back, horizontalPadding = 0.dp)
        if (songs.isEmpty()) {
            Text(stringResource(R.string.playlist_loading), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            return
        }
        LazyColumn {
            item(key = "header") {
                CollectionHeader(
                    title = route.card.title,
                    owner = route.card.subtitle,
                    coverUrl = route.card.thumbnailUrl,
                    songs = songs,
                    onPlay = { viewModel.playCollection(songs, 0) },
                    onShuffle = { viewModel.playCollection(songs.shuffled(), 0) },
                    onDownload = { viewModel.downloadAll(songs) },
                )
            }
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.playCollection(songs, songs.indexOf(song)) },
                    // Трек уезжает из списка сам: после нажатия «удалить»
                    // должно быть видно, что удалилось именно это, а не
                    // что список перерисовался целиком.
                    modifier = Modifier.animateItem(),
                    actions = { DownloadButton(song.id, onDownload = { viewModel.download(song) }) },
                )
            }
            if (suggestions.isNotEmpty()) {
                item(key = "suggestions-header") {
                    SectionHeader(
                        label = stringResource(R.string.playlist_suggestions),
                        hint = stringResource(R.string.playlist_suggestions_hint),
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                    )
                }
                // Рекомендация запускается одна, отдельно от плейлиста.
                items(suggestions, key = { "s-" + it.id }) { song ->
                    SongRow(song = song, onClick = { viewModel.play(listOf(song), 0) })
                }
            }
        }
    }
}

/** Имя выбранной папки из адреса SAF: «primary:Music/w0y» → «Music/w0y». */
private fun folderName(tree: String): String? {
    if (tree.isBlank()) return null
    return runCatching {
        android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(tree)).substringAfter(':').ifBlank { "/" }
    }.getOrNull()
}

/** Плейлистов в ряду сетки Hairline. */
private const val HAIR_PLAYLIST_COLUMNS = 3
