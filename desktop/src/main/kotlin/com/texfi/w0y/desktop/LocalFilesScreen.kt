package com.texfi.w0y.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.dp
import com.texfi.w0y.desktop.i18n.S
import java.awt.datatransfer.DataFlavor
import java.io.File

/** Окно выбора файлов и добавление: звуковые файлы и папки целиком. */
private fun addFiles(app: AppState) {
    val files = pickFiles(S("lm_add"), multiple = true, extensions = LocalFiles.extensions)
    if (files.isNotEmpty()) app.importLocal(files)
}

/**
 * «Мои файлы»: свои mp3, flac, wav и т. д. Играют, как обычные треки: попадают в плейлисты, историю и
 * «быстрый набор», а рекомендации от файла строятся по его двойнику или исполнителю в каталоге.
 * Файлы можно перетащить в окно.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LocalFilesScreen(app: AppState) {
    val songs = app.localSongs
    val editing = app.localEditing?.let { id -> songs.firstOrNull { it.id == id } }
    val drop = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val transfer = event.awtTransferable
                if (!transfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                @Suppress("UNCHECKED_CAST")
                val files = runCatching { transfer.getTransferData(DataFlavor.javaFileListFlavor) as List<File> }.getOrNull().orEmpty()
                if (files.isEmpty()) return false
                app.importLocal(files)
                return true
            }
        }
    }
    ScreenFrame(S("lm_card"), app, canBack = true, actions = {
        if (editing == null) PixelButton(S("lm_add"), onClick = { addFiles(app) })
    }) {
        Column(
            Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = drop),
        ) {
            AnimatedContent(
                targetState = editing?.id,
                transitionSpec = { fadeIn(tween(Motion.ms(240))) togetherWith fadeOut(tween(Motion.ms(140))) },
                label = "local-files",
            ) { id ->
                val song = songs.firstOrNull { it.id == id }
                if (song != null) {
                    LocalFileEditor(
                        song,
                        onSave = { t, a, al, cover, clear -> app.editLocal(song.id, t, a, al, cover, clear) },
                        onRemove = { app.removeLocal(song.id); app.localEditing = null },
                        onClose = { app.localEditing = null },
                    )
                } else {
                    LocalFilesList(app, songs)
                }
            }
        }
    }
}

@Composable
private fun LocalFilesList(app: AppState, songs: List<com.texfi.w0y.data.SongItem>) {
    val progress = app.localImport
    val result = app.localResult
    Column(Modifier.fillMaxSize()) {
        when {
            progress != null -> Txt(S("lm_adding", progress.first.coerceAtMost(progress.second) + 1, progress.second), color = C.Blue, size = 13, modifier = Modifier.padding(bottom = 10.dp))
            result != null -> Txt(
                buildString {
                    append(S("lm_added", result.first))
                    if (result.second > 0) append(" · ").append(S("lm_failed", result.second))
                },
                color = if (result.second > 0) C.Sand else C.Muted, size = 13, modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        if (songs.isEmpty()) {
            PixelCard(Modifier.fillMaxWidth()) {
                Column {
                    Txt(S("lm_empty_title"), pixel = true, size = 16)
                    Gap(h = 8)
                    Txt(S("lm_empty_text"), color = C.Muted, size = 13, maxLines = 4)
                    Gap(h = 6)
                    Txt(S("d_lm_drop"), color = C.Muted, size = 12, maxLines = 2)
                    Gap(h = 14)
                    PixelButton(S("lm_add"), onClick = { addFiles(app) })
                }
            }
        } else {
            Txt(S("d_lm_drop"), color = C.Muted, size = 12, modifier = Modifier.padding(bottom = 8.dp))
            SongList(app, songs, extra = { song ->
                IconButton(Glyph.Sliders, onClick = { app.localEditing = song.id }, color = C.Muted, size = 16.dp)
            })
        }
    }
}
