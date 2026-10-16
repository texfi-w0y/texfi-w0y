package com.texfi.w0y.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import kotlinx.coroutines.delay

/** Цвета плейлиста: фирменный синий, песочный и несколько спокойных. */
internal val PLAYLIST_COLORS = listOf(0xFF4A7DFB, 0xFFE0A458, 0xFF5FB38A, 0xFFE5646B, 0xFFB07CE8, 0xFF4FC1D9, 0xFFF0A3C0, 0xFFEDEAE0).map { it.toInt() }

/** Значки плейлиста: что-то музыкальное и пара сезонных. */
internal val PLAYLIST_GLYPHS = listOf(
    HairGlyph.NOTE, HairGlyph.HEART, HairGlyph.MOON, HairGlyph.HEADPHONES, HairGlyph.ROCKET,
    HairGlyph.WAVE, HairGlyph.DISC, HairGlyph.PIN, HairGlyph.SNOWFLAKE, HairGlyph.PUMPKIN,
)

internal fun glyphOf(name: String?): HairGlyph? = name?.let { n -> HairGlyph.entries.firstOrNull { it.name == n } }

/**
 * Полная настройка плейлиста: обложка, имя, описание, цвет и значок. Имя
 * уходит и в аккаунт, остальное — только на этом телефоне. Удаление — в два
 * нажатия: первое только взводит кнопку.
 */
@Composable
internal fun PlaylistEditor(
    entry: PlaylistEntity,
    canRename: Boolean,
    cover: String?,
    onSave: (name: String, note: String, accent: Int?, glyph: String?, picked: Uri?, clearCover: Boolean) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    var name by remember(entry.id) { mutableStateOf(entry.name) }
    var note by remember(entry.id) { mutableStateOf(entry.note.orEmpty()) }
    var accent by remember(entry.id) { mutableStateOf(entry.accent) }
    var glyph by remember(entry.id) { mutableStateOf(entry.glyph) }
    var picked by remember(entry.id) { mutableStateOf<Uri?>(null) }
    var clear by remember(entry.id) { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            picked = uri
            clear = false
        }
    }
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(title = stringResource(R.string.pl_customize).lowercase(), onBack = onClose, horizontalPadding = 0.dp)
        val shape = RoundedCornerShape(look.cardRadius.coerceAtMost(12.dp))
        val shown = if (clear) null else (picked?.toString() ?: cover)
        Box(
            Modifier
                .size(168.dp)
                .align(Alignment.CenterHorizontally)
                .clip(shape)
                .background(colors.surfaceHigh)
                .border(look.line * 2, accent?.let { Color(it) } ?: colors.border, shape)
                .clickable { pick.launch(arrayOf("image/*")) },
            contentAlignment = Alignment.Center,
        ) {
            if (shown != null) CoverImage(url = shown, px = 480, modifier = Modifier.fillMaxSize())
            else glyphOf(glyph)?.let { HairIcon(it, accent?.let { c -> Color(c) } ?: colors.textMuted, Modifier.size(56.dp)) }
                ?: Text(stringResource(R.string.lm_cover_pick).asLabel(), style = HairLabelStyle, color = colors.textMuted)
        }
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(stringResource(R.string.lm_cover_pick), onClick = { pick.launch(arrayOf("image/*")) }, fill = colors.surfaceHigh)
            if (shown != null) PixelButton(stringResource(R.string.lm_cover_clear), onClick = { picked = null; clear = true }, fill = colors.surfaceHigh)
        }

        PlaylistField(stringResource(R.string.lm_field_title), name, singleLine = true, enabled = canRename) { name = it }
        PlaylistField(stringResource(R.string.pl_note), note, singleLine = false, enabled = true) { note = it }

        Text(stringResource(R.string.pl_color).asLabel(), style = HairLabelStyle, color = colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).border(look.line, if (accent == null) colors.text else colors.border, CircleShape)
                    .clickable { accent = null },
                contentAlignment = Alignment.Center,
            ) { HairIcon(HairGlyph.CLOSE, colors.textMuted, Modifier.size(16.dp)) }
            PLAYLIST_COLORS.forEach { c ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                        .border(look.line * 2, if (accent == c) colors.text else Color.Transparent, CircleShape)
                        .clickable { accent = c },
                )
            }
        }

        Text(stringResource(R.string.pl_glyph).asLabel(), style = HairLabelStyle, color = colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val chip = RoundedCornerShape(look.pillRadius.coerceAtMost(8.dp))
            (listOf<HairGlyph?>(null) + PLAYLIST_GLYPHS).forEach { g ->
                val active = glyph == g?.name
                Box(
                    Modifier.size(44.dp).clip(chip)
                        .background(if (active) colors.text else Color.Transparent)
                        .then(if (active) Modifier else Modifier.border(look.line, colors.outline, chip))
                        .clickable { glyph = g?.name },
                    contentAlignment = Alignment.Center,
                ) { HairIcon(g ?: HairGlyph.CLOSE, if (active) colors.background else colors.text, Modifier.size(20.dp)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(
                stringResource(R.string.lm_save),
                onClick = { onSave(name, note, accent, glyph, picked, clear) },
                enabled = name.isNotBlank(),
            )
            PixelButton(stringResource(R.string.library_cancel), onClick = onClose, fill = colors.surfaceHigh)
        }
        ConfirmDeleteButton(stringResource(R.string.pl_delete), stringResource(R.string.pl_delete_sure), onDelete)
        com.texfi.w0y.ui.components.BottomInsetSpacer()
    }
}

/** Удаление в два нажатия: первое взводит кнопку на несколько секунд, второе удаляет. */
@Composable
internal fun ConfirmDeleteButton(label: String, armedLabel: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val look = hairLook
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4_000)
            armed = false
        }
    }
    val shape = RoundedCornerShape(look.pillRadius.coerceAtMost(6.dp))
    val danger = Color(0xFFE5646B)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (armed) danger else Color.Transparent)
            .border(look.line, if (armed) danger else colors.outline, shape)
            .clickable { if (armed) onConfirm() else armed = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (if (armed) armedLabel else label).asLabel(),
            style = HairLabelStyle,
            color = if (armed) colors.background else danger,
        )
    }
}

@Composable
private fun PlaylistField(label: String, value: String, singleLine: Boolean, enabled: Boolean, onChange: (String) -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val shape = RoundedCornerShape(look.pillRadius.coerceAtMost(6.dp))
    Column {
        Text(label.asLabel(), style = HairLabelStyle, color = colors.textMuted)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) colors.text else colors.textMuted),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(look.line, colors.outline, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .heightIn(min = if (singleLine) 0.dp else 64.dp),
        )
    }
}
