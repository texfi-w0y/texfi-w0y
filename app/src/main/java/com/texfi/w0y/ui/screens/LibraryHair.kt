package com.texfi.w0y.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.data.db.FolderEntity
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.HairIconButton
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SectionHeader
import com.texfi.w0y.ui.components.hairSurface
import com.texfi.w0y.ui.components.hairStagger
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook

/**
 * «Моё» в Hairline: аккаунт строкой, библиотека списком с тонкими линиями
 * вместо плиток-кнопок, плейлисты сеткой обложек и папки для них.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HairLibraryRoot(viewModel: LibraryViewModel, onOpenLogin: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val navigator = LocalBrowseNavigator.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val weekPlays by viewModel.weekPlays.collectAsStateWithLifecycle()
    // Новое прослушивание попадает в историю — счётчик на плитке «Итоги» обновляется вместе с ней.
    LaunchedEffect(recent.firstOrNull()?.id, recent.size) { viewModel.refreshWeek() }
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

    var prompt by remember { mutableStateOf<Prompt?>(null) }
    var moving by remember { mutableStateOf<PlaylistEntity?>(null) }
    var folderMenu by remember { mutableStateOf<FolderEntity?>(null) }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    val openFolder = folders.firstOrNull { it.id == openFolderId }
    if (openFolderId != null && openFolder == null) openFolderId = null
    BackHandler(enabled = openFolder != null) { openFolderId = null }

    LaunchedEffect(signedIn) { if (signedIn) viewModel.sync(force = false) }

    val shown = if (openFolder != null) playlists.filter { it.folderId == openFolder.id } else playlists.filter { it.folderId == null }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = com.texfi.w0y.ui.components.bottomContentPadding(),
    ) {
        if (openFolder != null) {
            item(key = "folder-title") {
                ScreenTitle(title = openFolder.name, onBack = { openFolderId = null }, horizontalPadding = 0.dp)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            item(key = "title") { ScreenTitle(title = stringResource(R.string.library_title), horizontalPadding = 0.dp) }

            // Аккаунт: имя и две кнопки, без рамок-карточек.
            item(key = "account") {
                Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 20.dp)) {
                    if (signedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverImage(url = accountAvatar, px = Thumbnails.ROW, modifier = Modifier.size(44.dp), circle = true)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = accountName ?: stringResource(R.string.library_signed_in),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.text,
                                    maxLines = 1,
                                )
                                Text(
                                    text = stringResource(R.string.library_account).asLabel(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PixelButton(
                                text = if (syncing) stringResource(R.string.library_syncing) else stringResource(R.string.library_sync),
                                onClick = viewModel::sync,
                                enabled = !syncing,
                            )
                            PixelButton(text = stringResource(R.string.library_sign_out), onClick = viewModel::signOut, fill = colors.surfaceHigh)
                        }
                    } else {
                        Text(stringResource(R.string.library_sign_in_hint), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                        Spacer(Modifier.height(12.dp))
                        PixelButton(text = stringResource(R.string.library_sign_in), onClick = onOpenLogin)
                    }
                    syncError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                    }
                    mirrorFailure?.let {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.library_mirror_failed, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondary,
                                modifier = Modifier.weight(1f),
                            )
                            HairIconButton(HairGlyph.CLOSE, stringResource(R.string.library_cancel), viewModel::clearMirrorFailure, size = 16.dp)
                        }
                    }
                }
            }

            item(key = "lib-header") { SectionHeader(stringResource(R.string.library_section_library), index = 1) }
            item(key = "lib-rows") {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BentoCard(0, HairGlyph.HEART, stringResource(R.string.library_likes), liked.size, { viewModel.open(LibraryRoute.Liked) }, Modifier.fillMaxWidth(), tall = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BentoCard(1, HairGlyph.TIMER, stringResource(R.string.library_history), recent.size, { viewModel.open(LibraryRoute.History) }, Modifier.weight(1f))
                        BentoCard(2, HairGlyph.DOWNLOAD, stringResource(R.string.library_downloaded), downloaded.size, { viewModel.open(LibraryRoute.Downloads) }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BentoCard(3, HairGlyph.NOTE, stringResource(R.string.lm_card), files.size, { viewModel.open(LibraryRoute.Files) }, Modifier.weight(1f))
                        BentoCard(4, HairGlyph.STATS, stringResource(R.string.library_stats), weekPlays?.takeIf { it > 0 }, { viewModel.open(LibraryRoute.Stats) }, Modifier.weight(1f))
                    }
                }
            }
            item(key = "gap1") { Spacer(Modifier.height(26.dp)) }

            // Папки.
            item(key = "folders-header") {
                SectionHeader(
                    label = stringResource(R.string.folders_title),
                    index = 2,
                    action = { HairIconButton(HairGlyph.PLUS, stringResource(R.string.folder_new), { prompt = Prompt.NewFolder(null) }, size = 18.dp) },
                )
            }
            if (folders.isNotEmpty()) {
                items(folders, key = { "f-" + it.id }) { folder ->
                    val count = playlists.count { it.folderId == folder.id }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { openFolderId = folder.id }, onLongClick = { folderMenu = folder }),
                    ) {
                        PanelRow(HairGlyph.FOLDER, folder.name, stringResource(R.string.folder_count, count))
                    }
                }
            }
            item(key = "gap2") { Spacer(Modifier.height(26.dp)) }
        }

        item(key = "pl-header") {
            SectionHeader(
                label = stringResource(R.string.library_playlists),
                index = if (openFolder != null) null else 3,
                action = { HairIconButton(HairGlyph.PLUS, stringResource(R.string.library_create), { prompt = Prompt.NewPlaylist }, size = 18.dp) },
            )
            Spacer(Modifier.height(12.dp))
        }

        prompt?.let { p ->
            item(key = "prompt") {
                NameField(
                    label = stringResource(if (p is Prompt.NewPlaylist) R.string.library_playlist_name else R.string.folder_name),
                    initial = "",
                    onSave = { name ->
                        if (name.isNotBlank()) {
                            when (p) {
                                Prompt.NewPlaylist -> viewModel.createPlaylist(name)
                                is Prompt.NewFolder -> viewModel.createFolder(name, p.move)
                                is Prompt.RenameFolder -> viewModel.renameFolder(p.id, name)
                            }
                        }
                        prompt = null
                    },
                    onCancel = { prompt = null },
                )
            }
        }

        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(if (openFolder != null) R.string.folder_empty else R.string.library_no_playlists),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        } else {
            items(shown.chunked(COLUMNS), key = { row -> "hp-" + row.first().id }) { row ->
                Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { playlist ->
                        val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val press = rememberPress(source)
                        Column(
                            Modifier
                                .weight(1f)
                                .hairStagger(shown.indexOf(playlist))
                                .graphicsLayer {
                                    val k = 1f - 0.04f * press
                                    scaleX = k
                                    scaleY = k
                                }
                                .combinedClickable(
                                    interactionSource = source,
                                    indication = null,
                                    onClick = { viewModel.open(LibraryRoute.Local(playlist.id)) },
                                    onLongClick = { moving = playlist },
                                ),
                        ) {
                            val tint = playlist.accent?.let { androidx.compose.ui.graphics.Color(it) }
                            val tileShape = RoundedCornerShape(hairLook.cardRadius.coerceAtMost(12.dp))
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                CoverImage(
                                    url = covers[playlist.id],
                                    px = Thumbnails.TILE,
                                    modifier = Modifier.fillMaxSize().then(if (tint != null) Modifier.border(hairLook.line * 2, tint, tileShape) else Modifier),
                                )
                                glyphOf(playlist.glyph)?.let { g ->
                                    Box(
                                        Modifier.align(Alignment.BottomStart).padding(6.dp).size(26.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(colors.background.copy(alpha = 0.85f)),
                                        contentAlignment = Alignment.Center,
                                    ) { HairIcon(g, tint ?: colors.text, Modifier.size(14.dp)) }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(playlist.name, style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 1)
                            Text(
                                text =
                                    when {
                                        // Под узкой плиткой — короткая подпись: длинная обрезалась на «· в».
                                        playlist.remoteId != null && !playlist.remoteEditable -> stringResource(R.string.library_tile_saved)
                                        playlist.remoteId != null -> stringResource(R.string.library_tile_mirrored)
                                        else -> stringResource(R.string.library_tile_own)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (openFolder == null && accountAlbums.isNotEmpty()) {
            item(key = "albums-header") {
                Spacer(Modifier.height(10.dp))
                SectionHeader(stringResource(R.string.library_albums), index = 4)
                Spacer(Modifier.height(10.dp))
            }
            items(accountAlbums, key = { "a-" + it.browseId }) { card ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { navigator.open(BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl)) },
                ) {
                    PanelRow(HairGlyph.RELEASE, card.title, card.subtitle)
                }
            }
        }
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }

    moving?.let { playlist ->
        MoveDialog(
            playlist = playlist,
            folders = folders,
            onMove = { folderId ->
                viewModel.movePlaylist(playlist.id, folderId)
                moving = null
            },
            onNewFolder = { name ->
                viewModel.createFolder(name, playlist.id)
                moving = null
            },
            onDismiss = { moving = null },
        )
    }
    folderMenu?.let { folder ->
        FolderDialog(
            folder = folder,
            onRename = {
                prompt = Prompt.RenameFolder(folder.id)
                folderMenu = null
            },
            onDelete = {
                if (openFolderId == folder.id) openFolderId = null
                viewModel.deleteFolder(folder.id)
                folderMenu = null
            },
            onDismiss = { folderMenu = null },
        )
    }
}

private sealed interface Prompt {
    data object NewPlaylist : Prompt

    data class NewFolder(val move: Long?) : Prompt

    data class RenameFolder(val id: Long) : Prompt
}

private const val COLUMNS = 3

@Composable
private fun LinkRow(glyph: HairGlyph, label: String, value: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) { LinkRowBody(glyph, label, value) }
}

/** Панель-строка: тонкая рамка, глиф, название, значение и шеврон; между панелями воздух. */
@Composable
private fun LinkRowBody(glyph: HairGlyph, label: String, value: String?) {
    val colors = LocalW0yColors.current
    val shape = RoundedCornerShape(hairLook.cardRadius.coerceAtMost(12.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(colors.surface)
            .border(hairLook.line, colors.border, shape)
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HairIcon(glyph, colors.text, Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = colors.text, maxLines = 1, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value.asLabel(), style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1)
            Spacer(Modifier.width(10.dp))
        }
        HairIcon(HairGlyph.CHEVRON_RIGHT, colors.textMuted, Modifier.size(16.dp))
    }
}

@Composable
private fun NameField(label: String, initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val colors = LocalW0yColors.current
    var value by remember { mutableStateOf(initial) }
    val shape = RoundedCornerShape(hairLook.cardRadius.coerceAtMost(6.dp))
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(label.asLabel(), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceHigh)
                .border(hairLook.line, colors.border, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(stringResource(R.string.folder_create), onClick = { onSave(value) }, enabled = value.isNotBlank())
            PixelButton(stringResource(R.string.library_cancel), onClick = onCancel, fill = colors.surfaceHigh)
        }
    }
}

/** Куда переложить плейлист: без папки, в одну из папок или в новую. */
@Composable
private fun MoveDialog(
    playlist: PlaylistEntity,
    folders: List<FolderEntity>,
    onMove: (Long?) -> Unit,
    onNewFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalW0yColors.current
    var creating by remember { mutableStateOf(false) }
    HairDialog(onDismiss) {
        Text(stringResource(R.string.folder_move).asLabel(), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        DialogRow(stringResource(R.string.folder_none), playlist.folderId == null) { onMove(null) }
        folders.forEach { f -> DialogRow(f.name, playlist.folderId == f.id) { onMove(f.id) } }
        if (creating) {
            Spacer(Modifier.height(10.dp))
            NameField(stringResource(R.string.folder_name), "", onSave = { if (it.isNotBlank()) onNewFolder(it) }, onCancel = { creating = false })
        } else {
            DialogRow("+ " + stringResource(R.string.folder_new), false) { creating = true }
        }
    }
}

@Composable
private fun FolderDialog(folder: FolderEntity, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalW0yColors.current
    HairDialog(onDismiss) {
        Text(folder.name, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, modifier = Modifier.padding(bottom = 10.dp))
        DialogRow(stringResource(R.string.folder_rename), false, onRename)
        Spacer(Modifier.height(6.dp))
        ConfirmDeleteButton(stringResource(R.string.folder_delete), stringResource(R.string.pl_delete_sure), onDelete)
    }
}

@Composable
private fun HairDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalW0yColors.current
    val shape = RoundedCornerShape(hairLook.cardRadius.coerceAtMost(8.dp))
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .hairSurface(shape)
                .border(hairLook.line, colors.outline, shape)
                .padding(20.dp),
        ) { content() }
    }
}

@Composable
private fun DialogRow(text: String, current: Boolean, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = if (current) colors.accentText else colors.text, maxLines = 1, modifier = Modifier.weight(1f))
            if (current) HairIcon(HairGlyph.CHECK, colors.accentText, Modifier.size(18.dp))
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}
