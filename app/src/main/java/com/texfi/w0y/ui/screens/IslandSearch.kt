package com.texfi.w0y.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.components.rememberTapHaptic
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.GeologicaFamily
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.matte

/**
 * Фильтры выдачи в Hairline New: пилюли с тонкой рамкой, выбранная — светлее, с точкой акцента
 * перед подписью. Ряд листается вбок, если не влез.
 */
@Composable
internal fun IslandChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val tap = rememberTapHaptic()
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            val interaction = remember { MutableInteractionSource() }
            val edge by animateColorAsState(colors.text.copy(alpha = if (on) 0.34f else 0.12f), hairSpec(HairTokens.SHORT_MS), label = "chipEdge")
            val ink by animateColorAsState(if (on) colors.text else colors.textMuted, hairSpec(HairTokens.SHORT_MS), label = "chipInk")
            // Точка не появляется скачком: место под неё раскрывается вместе с выбором.
            val dot by animateDpAsState(if (on) 10.dp else 0.dp, hairSpec(HairTokens.SHORT_MS), label = "chipDot")
            Row(
                Modifier
                    .pressScale(interaction, pressed = 0.94f)
                    .border(1.dp, edge, RoundedCornerShape(50))
                    .clickable(interactionSource = interaction, indication = null) { tap(); onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(dot), contentAlignment = Alignment.CenterStart) {
                    if (dot > 1.dp) Box(Modifier.size(4.dp).background(tone, CircleShape))
                }
                Text(label.asLabel(), style = HairLabelStyle, color = ink, maxLines = 1)
            }
        }
    }
}

/**
 * Лучший результат: матовая карточка — круглое фото артиста в уголках кадра, над именем моно
 * «артист», под ним подпись выдачи (слушатели, подписчики), справа кружок со стрелкой.
 */
@Composable
internal fun IslandBestResult(name: String, subtitle: String?, thumbnailUrl: String?, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val tick = colors.text.copy(alpha = 0.38f)
    // Уголки при показе съезжаются к фото, как видоискатель, ловящий кадр.
    val moves = com.texfi.w0y.ui.theme.hairLook.motion.moves
    val frame = remember(name) { androidx.compose.animation.core.Animatable(if (moves) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(name) { frame.animateTo(1f, androidx.compose.animation.core.tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut)) }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressed = 0.98f)
            .matte(RoundedCornerShape(18.dp), alpha = 0.6f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .drawBehind {
                    // Уголки кадра, как метки обреза на печатном листе.
                    val l = 9.dp.toPx()
                    val w = 1.dp.toPx()
                    val s = size
                    val out = 8.dp.toPx() * (1f - frame.value)
                    listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(s.width, 0f) to Offset(-1f, 1f), Offset(0f, s.height) to Offset(1f, -1f), Offset(s.width, s.height) to Offset(-1f, -1f))
                        .forEach { (corner, dir) ->
                            val at = Offset(corner.x - dir.x * out, corner.y - dir.y * out)
                            drawLine(tick, at, Offset(at.x + l * dir.x, at.y), w)
                            drawLine(tick, at, Offset(at.x, at.y + l * dir.y), w)
                        }
                }
                .padding(8.dp),
        ) {
            CoverImage(url = thumbnailUrl, px = Thumbnails.TILE, circle = true, modifier = Modifier.size(68.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.island_kind_artist).asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1)
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.6).sp, lineHeight = 30.sp),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(36.dp).border(1.dp, colors.text.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            HairIcon(HairGlyph.CHEVRON_RIGHT, colors.text, Modifier.size(16.dp))
        }
    }
}

/** Метка «у тебя» у трека, который уже лежит в библиотеке: рамка и подпись акцентом. */
@Composable
internal fun IslandOwnBadge() {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    Text(
        stringResource(R.string.island_yours).asLabel(),
        style = HairLabelStyle.copy(fontSize = 10.sp),
        color = tone,
        maxLines = 1,
        modifier = Modifier.border(1.dp, tone.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
