package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook

/**
 * Лента-список с главной («быстрый выбор» и другие ленты треков YouTube Music): треки стоят
 * строками по четыре в колонке, колонки листаются вбок и останавливаются ровно по краю, а край
 * следующей колонки выглядывает справа — видно, что дальше есть ещё. Справа от заголовка —
 * «слушать всё». У строки: обложка, название, метка «E», исполнители и число прослушиваний,
 * справа три точки — общее меню трека. Касание включает ленту с этого трека.
 */
@Composable
fun QuickPicksShelf(
    title: String,
    songs: List<SongItem>,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return
    val colors = LocalW0yColors.current
    val actions = LocalSongActions.current
    val columns = remember(songs) { songs.withIndex().chunked(ROWS) }
    Column(modifier.fillMaxWidth().testTag("quick-picks")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
            ShelfTitle(title, Modifier.weight(1f))
            val shape = com.texfi.w0y.ui.theme.styleTokens.button
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .border(hairLook.line, colors.outline, shape)
                    .clickable(role = Role.Button) { onPlay(0) }
                    .testTag("quick-picks-play-all")
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.home_play_all).asLabel(), style = HairLabelStyle, color = colors.text, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Колонка уже экрана на «выглядывание» следующей; одна колонка — во всю ширину.
            val peek = if (columns.size > 1) 36.dp else 0.dp
            val columnWidth = maxWidth - Gutter * 2 - peek
            val state = rememberLazyListState()
            LazyRow(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(columns, key = { i, _ -> "qp-$i" }) { _, column ->
                    Column(Modifier.width(columnWidth)) {
                        column.forEach { (index, song) ->
                            QuickPickRow(
                                song = song,
                                onClick = { onPlay(index) },
                                onMenu = actions?.let { host -> { host.openMenu(song) } },
                                modifier = Modifier.testTag("quick-pick-$index"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickRow(
    song: SongItem,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val playing = song.id == LocalPlayingSongId.current
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, pressed = 0.975f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onMenu)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            CoverImage(url = song.thumbnailUrl, px = Thumbnails.ROW, modifier = Modifier.size(52.dp))
            if (playing && com.texfi.w0y.ui.theme.isHair) HairPlayingMark(Modifier.size(52.dp), rect = false)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) colors.accent else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.explicit) {
                    ExplicitBadge()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.plays).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onMenu != null) {
            HairIconButton(HairGlyph.DOTS, stringResource(R.string.row_menu_more), onMenu, size = 20.dp)
        }
    }
}

private const val ROWS = 4
