package com.texfi.w0y.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.EmptyState
import com.texfi.w0y.ui.components.HairLoader
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.components.hairStagger
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.isHair

/** «Мои файлы»: свои mp3/wav и т. п. — список, добавление и правка. */
@Composable
fun LocalFilesList(viewModel: LibraryViewModel) {
    val colors = LocalW0yColors.current
    val songs by viewModel.files.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val result by viewModel.importResult.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SongItem?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importFiles(uris)
    }
    BackHandler(enabled = editing != null) { editing = null }

    AnimatedContent(
        targetState = editing,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 10 }) togetherWith fadeOut(tween(160))
            } else {
                fadeIn(tween(240)) togetherWith (fadeOut(tween(180)) + slideOutVertically(tween(260)) { it / 10 })
            }
        },
        label = "filesEditor",
    ) { target ->
        if (target != null) {
            // Правка видит актуальную строку: после сохранения название меняется на месте.
            val live = songs.firstOrNull { it.id == target.id } ?: target
            LocalFileEditor(
                song = live,
                onSave = { title, artist, album, cover, clear ->
                    viewModel.editFile(live, title, artist, album, cover, clear)
                    editing = null
                },
                onRemove = {
                    viewModel.removeFile(live)
                    editing = null
                },
                onClose = { editing = null },
            )
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                ScreenTitle(title = stringResource(R.string.lm_card).lowercase(), onBack = viewModel::back, horizontalPadding = 0.dp) {
                    SpriteButton(Sprites.plus, onClick = { if (importing == null) picker.launch(arrayOf("audio/*")) }, active = true, size = 18)
                }
                ImportBanner(importing, result)
                if (songs.isEmpty() && importing == null) {
                    EmptyState(
                        sprite = Sprites.note,
                        title = stringResource(R.string.lm_empty_title),
                        text = stringResource(R.string.lm_empty_text),
                    )
                    Spacer(Modifier.height(8.dp))
                    PixelButton(stringResource(R.string.lm_add), onClick = { picker.launch(arrayOf("audio/*")) })
                } else {
                    LazyColumn {
                        items(songs, key = { it.id }) { song ->
                            Box(Modifier.animateItem().hairStagger(songs.indexOf(song))) {
                                SongRow(
                                    song = song,
                                    // Играет один трек: дальше подборка (рекомендации по этому файлу), а не весь список файлов.
                                    onClick = { viewModel.play(listOf(song), 0) },
                                    onLongClick = { editing = song },
                                    actions = { SpriteButton(Sprites.sliders, onClick = { editing = song }) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Полоса состояния добавления: живое кольцо и счётчик, потом итог. */
@Composable
private fun ImportBanner(progress: Pair<Int, Int>?, result: Pair<Int, Int>?) {
    val colors = LocalW0yColors.current
    AnimatedContent(
        targetState = Triple(progress, result, progress == null && result == null),
        transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 3 }) togetherWith fadeOut(tween(140)) },
        label = "importBanner",
    ) { (p, r, idle) ->
        when {
            p != null -> {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp)) { HairLoader(Modifier.size(28.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.lm_adding, p.first.coerceAtMost(p.second), p.second).asLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }

            r != null && !idle -> {
                Text(
                    buildString {
                        append(stringResource(R.string.lm_added, r.first))
                        if (r.second > 0) append(" · ").append(stringResource(R.string.lm_failed, r.second))
                    }.asLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (r.second > 0) colors.secondary else colors.accentText,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            else -> Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LocalFileEditor(
    song: SongItem,
    onSave: (title: String, artist: String, album: String, cover: Uri?, clearCover: Boolean) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalW0yColors.current
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album.orEmpty()) }
    var cover by remember(song.id) { mutableStateOf<Uri?>(null) }
    var clearCover by remember(song.id) { mutableStateOf(false) }
    var sure by remember(song.id) { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            cover = uri
            clearCover = false
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(title = stringResource(R.string.lm_edit).lowercase(), onBack = onClose, horizontalPadding = 0.dp)
        val shape = RoundedCornerShape(if (isHair) 6.dp else 0.dp)
        Box(
            Modifier
                .size(160.dp)
                .align(Alignment.CenterHorizontally)
                .clip(shape)
                .background(colors.surfaceHigh)
                .border(1.dp, colors.border, shape)
                .clickable { pick.launch(arrayOf("image/*")) },
            contentAlignment = Alignment.Center,
        ) {
            val shown = if (clearCover) null else (cover?.toString() ?: song.thumbnailUrl)
            if (shown != null) {
                CoverImage(url = shown, px = 480, modifier = Modifier.fillMaxSize())
            } else {
                Text(stringResource(R.string.lm_cover_pick).asLabel(), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(stringResource(R.string.lm_cover_pick), onClick = { pick.launch(arrayOf("image/*")) }, fill = colors.surfaceHigh)
            if (cover != null || (song.thumbnailUrl != null && !clearCover)) {
                PixelButton(stringResource(R.string.lm_cover_clear), onClick = { cover = null; clearCover = true }, fill = colors.surfaceHigh)
            }
        }
        EditorField(stringResource(R.string.lm_field_title), title) { title = it }
        EditorField(stringResource(R.string.lm_field_artist), artist) { artist = it }
        EditorField(stringResource(R.string.lm_field_album), album) { album = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(stringResource(R.string.lm_save), onClick = { onSave(title, artist, album, cover, clearCover) }, enabled = title.isNotBlank())
            PixelButton(stringResource(R.string.library_cancel), onClick = onClose, fill = colors.surfaceHigh)
        }
        Text(
            stringResource(if (sure) R.string.lm_remove_sure else R.string.lm_remove).asLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = if (sure) colors.secondary else colors.textMuted,
            modifier = Modifier
                .clickable { if (sure) onRemove() else sure = true }
                .padding(vertical = 14.dp),
        )
        com.texfi.w0y.ui.components.BottomInsetSpacer()
    }
}

@Composable
private fun EditorField(label: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalW0yColors.current
    val shape = RoundedCornerShape(if (isHair) 3.dp else 0.dp)
    Column {
        Text(label.asLabel(), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceHigh)
                .border(1.dp, colors.border, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
