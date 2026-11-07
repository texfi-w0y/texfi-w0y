package com.texfi.w0y.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import com.texfi.w0y.R
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.data.ListeningStats
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.components.hairStagger
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairScreenTitleStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import com.texfi.w0y.ui.theme.matte

/** Шапка «Моё» в Hairline New: заголовок и аватар аккаунта в матовом кружке справа. */
@Composable
internal fun IslandLibraryHeader(title: String, avatar: String?, avatarLabel: String, onAvatar: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = HairScreenTitleStyle, color = colors.text, maxLines = 1, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(44.dp)
                .pressScale(interaction, pressed = 0.92f)
                .matte(CircleShape)
                .semantics { contentDescription = avatarLabel }
                .clickable(interactionSource = interaction, indication = null, onClick = onAvatar),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar != null) {
                CoverImage(url = avatar, px = Thumbnails.ROW, circle = true, modifier = Modifier.size(40.dp))
            } else {
                HairIcon(HairGlyph.ARTIST, colors.text, Modifier.size(19.dp))
            }
        }
    }
}

internal class IslandTile(val glyph: HairGlyph, val label: String, val count: Int?, val onClick: () -> Unit)

/**
 * Библиотека одной матовой панелью: плитки 2×2 через тонкие линии, как таблица, снизу во
 * всю ширину «Итоги» — время за неделю и столбики по дням, сегодняшний акцентом.
 */
@Composable
internal fun IslandLibraryGrid(tiles: List<IslandTile>, statsLabel: String, weekLabel: String?, week: ListeningStats?, onStats: () -> Unit) {
    val colors = LocalW0yColors.current
    val divider = colors.text.copy(alpha = 0.07f)
    Column(Modifier.fillMaxWidth().matte(RoundedCornerShape(18.dp), alpha = 0.6f)) {
        tiles.chunked(2).forEachIndexed { r, row ->
            if (r > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { c, tile ->
                    if (c > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(divider))
                    IslandTileCell(tile, r * 2 + c, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val press by animateFloatAsState(if (pressed) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "statsPress")
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.text.copy(alpha = 0.05f * press))
                .clickable(interactionSource = interaction, indication = null, onClick = onStats)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .hairStagger(tiles.size),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HairIcon(HairGlyph.STATS, colors.text, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(statsLabel, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1, modifier = Modifier.weight(1f))
            if (weekLabel != null) {
                Text(weekLabel.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1)
                Spacer(Modifier.width(12.dp))
            }
            WeekBars(week?.bars?.map { it.minutes } ?: emptyList())
        }
    }
}

@Composable
private fun IslandTileCell(tile: IslandTile, index: Int, modifier: Modifier) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "tilePress")
    Column(
        modifier
            .background(colors.text.copy(alpha = 0.05f * press))
            .clickable(interactionSource = interaction, indication = null, onClick = tile.onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .hairStagger(index),
    ) {
        // Значок чуть приподнимается под пальцем — плитка отвечает, а не просто темнеет.
        HairIcon(tile.glyph, colors.text, Modifier.size(20.dp).padding(bottom = (2 * press).dp))
        Spacer(Modifier.height(20.dp))
        Text(tile.label, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(tile.count?.let { "%,d".format(it).replace(',', ' ') } ?: "—", style = HairLabelStyle, color = colors.textMuted, maxLines = 1)
    }
}

/** Столбики недели: высота — минуты за день, последний (сегодня) — акцентом; вырастают при показе. */
@Composable
private fun WeekBars(minutes: List<Int>) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.surface)
    val days = (List((7 - minutes.size).coerceAtLeast(0)) { 0 } + minutes).takeLast(7)
    val top = days.maxOrNull()?.takeIf { it > 0 } ?: 1
    val moves = hairLook.motion.moves
    val grow = remember { Animatable(if (moves) 0f else 1f) }
    LaunchedEffect(top) { grow.animateTo(1f, tween(HairTokens.LONG_MS * 2, easing = HairTokens.EaseOut)) }
    val dim = colors.text.copy(alpha = 0.7f)
    val empty = colors.text.copy(alpha = 0.16f)
    Canvas(Modifier.width(56.dp).height(22.dp)) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * 6) / 7
        days.forEachIndexed { i, m ->
            val k = (m.toFloat() / top).coerceIn(0f, 1f)
            val h = (size.height * (0.12f + 0.88f * k) * grow.value).coerceAtLeast(2.dp.toPx())
            val color = when {
                i == days.lastIndex -> tone
                m == 0 -> empty
                else -> dim
            }
            drawRoundRect(color, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(1.dp.toPx()))
        }
    }
}

/**
 * Плейлист строкой: обложка, название, моно-подпись (свой, в YouTube, только чтение) и
 * шеврон, под строкой тонкая линия. Долгое нажатие — переложить в папку.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IslandPlaylistRow(
    cover: String?,
    name: String,
    subtitle: String,
    tint: Color?,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Удержание с переносом; без него долгое нажатие — просто [onLongClick]. */
    hold: Modifier? = null,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth().hairStagger(index)) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction, pressed = 0.975f)
                .then(
                    if (hold != null) {
                        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick).then(hold)
                    } else {
                        Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
                    },
                ).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = RoundedCornerShape(8.dp)
            CoverImage(
                url = cover,
                px = Thumbnails.ROW,
                modifier = Modifier.size(52.dp).then(if (tint != null) Modifier.border(1.5.dp, tint, shape) else Modifier),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(subtitle.asLabel(), style = HairLabelStyle.copy(fontSize = 10.sp), color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HairIcon(HairGlyph.CHEVRON_RIGHT, colors.textMuted, Modifier.size(16.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.text.copy(alpha = 0.07f)))
    }
}

/** Матовый кружок с плюсом — действие в заголовке раздела. */
@Composable
internal fun IslandPlusCircle(label: String, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(36.dp)
            .pressScale(interaction, pressed = 0.9f)
            .matte(CircleShape)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        HairIcon(HairGlyph.PLUS, colors.text, Modifier.size(16.dp))
    }
}

/**
 * «Создать» в «Моё»: панель с выбором — плейлист или папка, имя и куда положить плейлист:
 * в аккаунт YouTube Music или только в w0y. Папки есть только в w0y.
 */
@Composable
internal fun IslandCreateSheet(
    visible: Boolean,
    signedIn: Boolean,
    toAccountByDefault: Boolean,
    onDismiss: () -> Unit,
    onPlaylist: (String, Boolean) -> Unit,
    onFolder: (String) -> Unit,
) {
    val colors = LocalW0yColors.current
    var kind by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var name by remember { androidx.compose.runtime.mutableStateOf("") }
    var toAccount by remember(toAccountByDefault) { androidx.compose.runtime.mutableStateOf(toAccountByDefault) }
    androidx.compose.runtime.LaunchedEffect(visible) { if (visible) name = "" }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    HairSheetFrame(visible = visible, onDismiss = onDismiss, heightFraction = null) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(260)
            runCatching { focus.requestFocus() }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(stringResource(R.string.create_title), style = MaterialTheme.typography.titleLarge, color = colors.text)
            Spacer(Modifier.height(14.dp))
            com.texfi.w0y.ui.components.IslandSegmented(
                listOf(stringResource(R.string.create_kind_playlist), stringResource(R.string.create_kind_folder)),
                kind,
                { kind = it },
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.text.copy(alpha = 0.06f))
                    .border(1.dp, colors.text.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (name.isEmpty()) {
                    Text(
                        stringResource(if (kind == 0) R.string.library_playlist_name else R.string.folder_name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted,
                    )
                }
            }
            androidx.compose.animation.AnimatedContent(targetState = kind == 0 && signedIn, label = "createWhere") { where ->
                Column(Modifier.padding(top = 14.dp)) {
                    if (where) {
                        WhereOption(stringResource(R.string.create_where_account), stringResource(R.string.create_where_account_desc), toAccount) { toAccount = true }
                        WhereOption(stringResource(R.string.create_where_local), stringResource(R.string.create_where_local_desc), !toAccount) { toAccount = false }
                    } else if (kind == 1) {
                        Text(stringResource(R.string.create_folder_note), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            PixelButton(
                stringResource(R.string.create_button),
                onClick = {
                    val clean = name.trim()
                    if (clean.isNotEmpty()) {
                        if (kind == 0) onPlaylist(clean, toAccount && signedIn) else onFolder(clean)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WhereOption(title: String, text: String, chosen: Boolean, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val tone = com.texfi.w0y.ui.theme.Contrast.ensure(colors.accent, colors.surface)
    val ring by androidx.compose.animation.animateColorAsState(if (chosen) tone else colors.text.copy(alpha = 0.25f), com.texfi.w0y.ui.components.hairSpec(), label = "whereRing")
    val dot by androidx.compose.animation.core.animateFloatAsState(if (chosen) 1f else 0f, com.texfi.w0y.ui.components.hairSpec(), label = "whereDot")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .border(1.5.dp, ring, CircleShape)
                .padding(5.dp)
                .graphicsLayer { scaleX = dot; scaleY = dot }
                .background(tone, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

/**
 * Аккаунт в «Моё»: кто вошёл, сверка сейчас и настройки аккаунта — зеркало плейлистов,
 * куда по умолчанию идут новые плейлисты, видны ли сохранённые альбомы; внизу — выход.
 */
@Composable
internal fun IslandAccountSheet(
    visible: Boolean,
    name: String?,
    avatar: String?,
    syncing: Boolean,
    syncError: String?,
    mirror: Boolean,
    newToAccount: Boolean,
    showAlbums: Boolean,
    onSync: () -> Unit,
    onMirror: (Boolean) -> Unit,
    onNewToAccount: (Boolean) -> Unit,
    onShowAlbums: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalW0yColors.current
    HairSheetFrame(visible = visible, onDismiss = onDismiss, heightFraction = null) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(url = avatar, px = com.texfi.w0y.data.Thumbnails.ROW, modifier = Modifier.size(56.dp), circle = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name ?: stringResource(R.string.library_signed_in), style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1)
                    Text("YouTube Music".asLabel(), style = HairLabelStyle, color = colors.textMuted)
                }
                PixelButton(
                    if (syncing) stringResource(R.string.library_syncing) else stringResource(R.string.library_sync),
                    onClick = onSync,
                    enabled = !syncing,
                )
            }
            syncError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(12.dp))
            AccountToggle(stringResource(R.string.settings_sync_playlists_title), stringResource(R.string.settings_sync_playlists_desc), mirror, onMirror)
            AccountToggle(stringResource(R.string.account_new_to_account), stringResource(R.string.account_new_to_account_desc), newToAccount, onNewToAccount)
            AccountToggle(stringResource(R.string.account_show_albums), stringResource(R.string.account_show_albums_desc), showAlbums, onShowAlbums)
            Text(
                stringResource(R.string.library_sign_out),
                style = MaterialTheme.typography.bodyLarge,
                color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onSignOut)
                        .padding(vertical = 14.dp, horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun AccountToggle(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        com.texfi.w0y.ui.components.PixelSwitch(checked = checked, onCheckedChange = onChange)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.text.copy(alpha = 0.07f)))
}
