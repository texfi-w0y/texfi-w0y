package com.texfi.w0y.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.components.BottomInsetSpacer
import com.texfi.w0y.ui.components.ConfirmDialog
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.hairOutline
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.components.hairStagger
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook

/**
 * Пустые «Мои файлы» в Hairline: вместо картинки и отдельной кнопки — одна карточка с
 * пунктирной рамкой, «место, куда положить файлы». Нажатие на любую её часть открывает выбор.
 */
@Composable
internal fun HairDropCard(onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val source = remember { MutableInteractionSource() }
    val press = rememberPress(source)
    val radius = hairLook.cardRadius.coerceAtMost(14.dp)
    Column(
        Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .hairStagger(0)
            .graphicsLayer {
                val k = 1f - 0.02f * press
                scaleX = k
                scaleY = k
            }
            .drawBehind {
                val line = 1.dp.toPx()
                drawRoundRect(
                    lerp(colors.outline, tone, press),
                    topLeft = Offset(line / 2f, line / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width - line, size.height - line),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).hairOutline(CircleShape, tone),
            contentAlignment = Alignment.Center,
        ) { HairIcon(HairGlyph.PLUS, tone, Modifier.size(20.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.lm_empty_title), style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.lm_empty_text),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.lm_add).asLabel(), style = HairLabelStyle, color = tone)
    }
}

/**
 * Правка своего файла в Hairline: крупная обложка, которая обводится линией при появлении,
 * поля с линией снизу — при вводе она заливается акцентом слева направо, — и в самом низу,
 * отдельно от «сохранить», удаление.
 */
@Composable
internal fun HairFileEditor(
    song: SongItem,
    onSave: (title: String, artist: String, album: String, cover: Uri?, clearCover: Boolean) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
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
    val changed = title != song.title || artist != song.artist || album != song.album.orEmpty() || cover != null || clearCover
    val shown = if (clearCover) null else (cover?.toString() ?: song.thumbnailUrl)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        ScreenTitle(title = stringResource(R.string.lm_edit).lowercase(), onBack = onClose, horizontalPadding = 0.dp)
        val shape = RoundedCornerShape(hairLook.cardRadius.coerceAtMost(10.dp))
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(184.dp)
                .align(Alignment.CenterHorizontally)
                .hairStagger(0)
                .clip(shape)
                .background(colors.surfaceHigh)
                .hairOutline(shape, colors.outline)
                .clickable { pick.launch(arrayOf("image/*")) },
            contentAlignment = Alignment.Center,
        ) {
            if (shown != null) {
                CoverImage(url = shown, px = 480, modifier = Modifier.fillMaxSize())
            } else {
                HairIcon(HairGlyph.NOTE, colors.textMuted, Modifier.size(32.dp))
            }
        }
        song.durationText?.let {
            Text(
                it.asLabel(),
                style = HairLabelStyle,
                color = colors.textMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
            )
        }
        Row(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextAction(stringResource(R.string.lm_cover_pick), tone) { pick.launch(arrayOf("image/*")) }
            if (shown != null) {
                TextAction(stringResource(R.string.lm_cover_clear), colors.textMuted) {
                    cover = null
                    clearCover = true
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.hairStagger(1), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HairField(stringResource(R.string.lm_field_title), title) { title = it }
            HairField(stringResource(R.string.lm_field_artist), artist) { artist = it }
            HairField(stringResource(R.string.lm_field_album), album) { album = it }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.hairStagger(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(
                stringResource(R.string.lm_save),
                onClick = { onSave(title, artist, album, cover, clearCover) },
                enabled = title.isNotBlank() && changed,
            )
            PixelButton(stringResource(R.string.library_cancel), onClick = onClose, fill = colors.surfaceHigh)
        }
        Spacer(Modifier.height(36.dp))
        Box(Modifier.fillMaxWidth().height(hairLook.line).background(colors.border))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable { sure = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HairIcon(HairGlyph.TRASH, colors.secondary, Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.lm_remove), style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
        }
        if (sure) {
            // Файл удаляется с телефона насовсем: подтверждение обязательно всегда.
            ConfirmDialog(
                title = stringResource(R.string.rm_remove_file),
                text = stringResource(R.string.rm_remove_file_text, song.title),
                confirm = stringResource(R.string.rm_remove_file_button),
                dismiss = stringResource(R.string.library_cancel),
                onConfirm = { sure = false; onRemove() },
                onDismiss = { sure = false },
            )
        }
        BottomInsetSpacer()
    }
}

/** Действие подписью: моно, без рамки, с запасом под палец. */
@Composable
private fun TextAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label.asLabel(),
        style = HairLabelStyle,
        color = color,
        modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 14.dp),
    )
}

/**
 * Поле Hairline: подпись моно сверху, под текстом линия. В фокусе подпись и линия перетекают
 * в акцент, линия заливается слева направо.
 */
@Composable
private fun HairField(label: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused) 1f else 0f, hairSpec(HairTokens.MID_MS), label = "fieldFocus")
    Column {
        Text(label.asLabel(), style = HairLabelStyle, color = lerp(colors.textMuted, tone, p))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
            cursorBrush = SolidColor(tone),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(colors.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        if (p > 0f) drawLine(tone, Offset(0f, y), Offset(size.width * p, y), 1.5.dp.toPx())
                    }
                    .padding(top = 8.dp, bottom = 10.dp),
        )
    }
}
