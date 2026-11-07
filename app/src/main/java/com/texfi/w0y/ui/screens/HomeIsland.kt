package com.texfi.w0y.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.Gutter
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.components.pressScale
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
 * Шапка главной в Hairline New: слово «w0y» с квадратной точкой акцента — как на сайте — и
 * шестерёнка настроек в матовом кружке.
 */
@Composable
internal fun IslandHomeHeader(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val interaction = remember { MutableInteractionSource() }
    val settingsLabel = stringResource(R.string.settings_title)
    Row(modifier.fillMaxWidth().padding(start = Gutter, end = Gutter - 4.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("w0y", style = MaterialTheme.typography.titleLarge.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.Bold, fontSize = 27.sp, letterSpacing = (-0.8).sp), color = colors.text)
        Box(Modifier.padding(start = 3.dp, top = 9.dp).size(5.dp).background(tone))
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(42.dp)
                .pressScale(interaction, pressed = 0.92f)
                .matte(CircleShape)
                .semantics { contentDescription = settingsLabel }
                .combinedClickable(interactionSource = interaction, indication = null, onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            HairIcon(HairGlyph.GEAR, colors.text, Modifier.size(19.dp))
        }
    }
}

/**
 * Быстрый набор в Hairline New: страница — матовая панель, ячейки 3×2 разделены тонкими
 * линиями, как таблица. Под обложкой — название и что это (трек, альбом, артист), у
 * закреплённого перед подписью точка акцента. Под панелью номер страницы и черты страниц.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IslandDialGrid(
    items: List<DialItem>,
    onOpen: (DialItem) -> Unit,
    onLongPress: (DialItem) -> Unit,
    menuItem: DialItem?,
    columns: Int,
    rows: Int,
    hint: String? = null,
    /** Под треком и альбомом — исполнитель; выключено в настройках — только тип. */
    showArtist: Boolean = true,
) {
    val colors = LocalW0yColors.current
    val perPage = columns * rows
    val pages = items.chunked(perPage).map { page -> page + List(perPage - page.size) { null } }
    val state = rememberPagerState(pageCount = { pages.size })
    val shape = RoundedCornerShape(16.dp)
    val divider = colors.text.copy(alpha = 0.07f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - Gutter * 2) / columns
        // Ячейка: отступ, квадрат обложки, две строки подписи.
        val cellHeight = cell - 22.dp + 12.dp + 11.dp + 46.dp
        Column {
            HorizontalPager(
                state = state,
                pageSpacing = Gutter,
                contentPadding = PaddingValues(horizontal = Gutter),
                modifier = Modifier.height(cellHeight * rows),
            ) { page ->
                Column(Modifier.fillMaxWidth().matte(shape, fill = null, alpha = 0.6f)) {
                    pages[page].chunked(columns).forEachIndexed { r, row ->
                        if (r > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
                        Row(Modifier.weight(1f)) {
                            row.forEachIndexed { c, item ->
                                if (c > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(divider))
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    if (item != null) IslandDialCell(item, lifted = menuItem == item, showArtist = showArtist, onOpen = { onOpen(item) }, onLongPress = { onLongPress(item) })
                                }
                            }
                        }
                    }
                }
            }
            if (pages.size > 1 || hint != null) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
                    if (pages.size > 1) {
                        val number = "%02d / %02d".format(state.currentPage + 1, pages.size)
                        Text(number, style = HairLabelStyle, color = colors.textMuted)
                        Spacer(Modifier.width(12.dp))
                        repeat(pages.size) { index ->
                            val on = index == state.currentPage
                            val w by animateDpAsState(if (on) 22.dp else 12.dp, hairSpec(HairTokens.SHORT_MS), label = "dialPageWidth")
                            val ink by animateColorAsState(if (on) colors.text else colors.border, hairSpec(HairTokens.SHORT_MS), label = "dialPageInk")
                            Box(Modifier.padding(end = 4.dp).width(w).height(1.dp).background(ink))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (hint != null) Text(hint.asLabel(), style = HairLabelStyle.copy(fontSize = 9.5.sp, letterSpacing = 1.sp), color = colors.textMuted.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IslandDialCell(item: DialItem, lifted: Boolean, showArtist: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.surface)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed || lifted) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "dialCellPress")
    val round = item.kind == PinEntity.KIND_ARTIST
    val kind =
        stringResource(
            when (item.kind) {
                PinEntity.KIND_ARTIST -> R.string.island_kind_artist
                PinEntity.KIND_ALBUM -> R.string.island_kind_album
                PinEntity.KIND_PLAYLIST -> R.string.island_kind_playlist
                else -> R.string.island_kind_song
            },
        )
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onOpen, onLongClick = onLongPress)
            .padding(start = 11.dp, end = 11.dp, top = 12.dp, bottom = 11.dp),
    ) {
        CoverImage(
            url = item.thumbnailUrl,
            px = Thumbnails.TILE,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        // Нажатие и меню — обложка чуть отступает внутрь ячейки, ячейка стоит.
                        val k = 1f - 0.05f * press
                        scaleX = k
                        scaleY = k
                    },
            circle = round,
        )
        Spacer(Modifier.height(10.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.pinned) {
                Box(Modifier.size(4.dp).background(tone, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            // У трека и альбома под названием — исполнитель; тип пишем только тем, у кого его нет.
            val artist = item.subtitle?.takeIf { showArtist && it.isNotBlank() && (item.kind == PinEntity.KIND_SONG || item.kind == PinEntity.KIND_ALBUM) }
            if (artist != null) {
                Text(artist, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp), color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(kind.asLabel(), style = HairLabelStyle.copy(fontSize = 9.5.sp, letterSpacing = 1.1.sp), color = colors.textMuted, maxLines = 1)
            }
        }
    }
}
