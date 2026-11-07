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
internal fun IslandPlaylistRow(cover: String?, name: String, subtitle: String, tint: Color?, index: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().hairStagger(index)) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction, pressed = 0.975f)
                .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 10.dp),
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
