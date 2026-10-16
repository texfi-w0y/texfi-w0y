package com.texfi.w0y.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.recs.Rec
import com.texfi.w0y.recs.RecSet
import com.texfi.w0y.recs.Why
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.RowAction
import com.texfi.w0y.ui.components.RowActionsMenu
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook

/** Вкладки нижней панели плеера. */
enum class SheetTab(@StringRes val label: Int) {
    QUEUE(R.string.sheet_queue),
    SIMILAR(R.string.sheet_similar),
    SOUND(R.string.sheet_sound),
    SPEED(R.string.sheet_speed),
    TIMER(R.string.sheet_timer),
}

/** Последняя открытая вкладка помнится, пока приложение живо. */
object PlayerSheetMemory {
    var tab: SheetTab = SheetTab.QUEUE
}

/**
 * Полоска внизу плеера: «ОЧЕРЕДЬ · N» с маленьким значком состояния справа.
 * Нажать или потянуть вверх — открыть панель.
 */
@Composable
fun PlayerSheetHandle(label: String, badge: String?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val description = stringResource(R.string.sheet_open_cd)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(look.cardRadius.coerceAtMost(12.dp)))
            .border(look.line, colors.outline, RoundedCornerShape(look.cardRadius.coerceAtMost(12.dp)))
            .semantics { contentDescription = description; role = Role.Button }
            .pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { _, dy -> drag += dy },
                    onDragEnd = { if (drag < -40.dp.toPx()) onOpen() },
                )
            }
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HairIcon(HairGlyph.EXPAND, colors.textMuted, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label.asLabel(), style = HairLabelStyle, color = colors.text, modifier = Modifier.weight(1f))
        if (badge != null) {
            Text(badge.asLabel(), style = HairLabelStyle, color = Contrast.ensure(colors.accent, colors.background))
        }
    }
}

/** Краткий значок состояния: скорость и «EQ» (тон или эхо). Пусто — всё по умолчанию. */
fun stateBadge(speed: Float, pitch: Float, reverbOn: Boolean, eq: String): String? {
    val parts = ArrayList<String>(2)
    if (kotlin.math.abs(speed - 1f) > 0.001f) parts += com.texfi.w0y.data.NumberText.speed(speed)
    if (kotlin.math.abs(pitch - 1f) > 0.001f || reverbOn) parts += eq
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Каркас нижней панели Hairline: затемнение, панель с контуром 1 dp, ручка.
 * Закрывается жестом вниз по ручке, кнопкой «назад» и нажатием по фону.
 */
@Composable
fun HairSheetFrame(
    visible: Boolean,
    onDismiss: () -> Unit,
    heightFraction: Float? = 0.82f,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    if (visible) BackHandler(onBack = onDismiss)
    val close = stringResource(R.string.sheet_close_cd)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(hairSpec()), exit = fadeOut(hairSpec())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(hairSpec<IntOffset>()) { it },
            exit = slideOutVertically(hairSpec<IntOffset>()) { it },
        ) {
            val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier)
                    .clip(shape)
                    .background(colors.surface)
                    .border(look.line, colors.outline, shape)
                    .navigationBarsPadding(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .semantics { contentDescription = close }
                        .pointerInput(Unit) {
                            var drag = 0f
                            detectVerticalDragGestures(
                                onDragStart = { drag = 0f },
                                onVerticalDrag = { _, dy -> drag += dy },
                                onDragEnd = { if (drag > 48.dp.toPx()) onDismiss() },
                            )
                        }
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.outline))
                }
                content()
            }
        }
    }
}

/** Нижняя панель плеера: вкладки и список содержимого. */
@Composable
fun PlayerSheet(
    visible: Boolean,
    tab: SheetTab,
    onTab: (SheetTab) -> Unit,
    onDismiss: () -> Unit,
    content: LazyListScope.(SheetTab) -> Unit,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    HairSheetFrame(visible = visible, onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetTab.entries.forEach { t ->
                val active = t == tab
                val chip = RoundedCornerShape(look.pillRadius.coerceAtMost(6.dp))
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(chip)
                        .background(if (active) colors.text else Color.Transparent)
                        .then(if (active) Modifier else Modifier.border(look.line, colors.outline, chip))
                        .clickable { onTab(t) }
                        .semantics { role = Role.Tab }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(t.label).asLabel(),
                        style = HairLabelStyle,
                        color = if (active) colors.background else colors.text,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            content(tab)
        }
    }
}

/** Подпись «почему этот трек» из причины. */
@Composable
fun whyLabel(why: Why): String =
    when (why) {
        is Why.SimilarTo -> stringResource(R.string.why_similar_to, why.title.take(28))
        is Why.YouListen -> stringResource(R.string.why_you_listen, why.artist.take(28))
        Why.NewArtist -> stringResource(R.string.why_new_artist)
        Why.Forgotten -> stringResource(R.string.why_forgotten)
        Why.TimeOfDay -> stringResource(R.string.why_time_of_day)
    }

/** Строка подборки: обложка 52 dp, название, исполнитель, причина, кнопка «играть», меню по долгому нажатию. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecRow(
    rec: Rec,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onNext: () -> Unit,
    onMore: () -> Unit,
    onLess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalW0yColors.current
    var menu by remember { mutableStateOf(false) }
    if (menu) {
        RowActionsMenu(
            title = rec.song.title,
            actions =
                listOf(
                    RowAction(stringResource(R.string.similar_menu_enqueue)) { onEnqueue() },
                    RowAction(stringResource(R.string.similar_menu_next)) { onNext() },
                    RowAction(stringResource(R.string.similar_menu_more)) { onMore() },
                    RowAction(stringResource(R.string.similar_menu_less)) { onLess() },
                ),
            cancel = stringResource(R.string.library_cancel),
            onDismiss = { menu = false },
        )
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(onClick = onPlay, onLongClick = { menu = true })
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(rec.song.thumbnailUrl, com.texfi.w0y.data.Thumbnails.ROW, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(rec.song.title, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rec.song.artist, style = HairLabelStyle, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                whyLabel(rec.why).asLabel(),
                style = HairLabelStyle,
                color = Contrast.ensure(colors.accent, colors.surface),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val play = stringResource(R.string.similar_play_cd, rec.song.title)
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(hairLook.line, colors.outline, RoundedCornerShape(24.dp))
                .semantics { contentDescription = play; role = Role.Button }
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            HairIcon(HairGlyph.PLAY, colors.text, Modifier.size(20.dp))
        }
    }
}

/** Содержимое вкладки «Похожее». */
fun LazyListScope.similarItems(
    set: RecSet?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onPlay: (SongItem) -> Unit,
    onEnqueue: (SongItem) -> Unit,
    onNext: (SongItem) -> Unit,
    onMore: (SongItem) -> Unit,
    onLess: (SongItem) -> Unit,
) {
    item(key = "similar-head") {
        val colors = LocalW0yColors.current
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    when {
                        loading -> stringResource(R.string.similar_loading)
                        set?.offline == true -> stringResource(R.string.similar_offline)
                        else -> ""
                    },
                style = HairLabelStyle,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            com.texfi.w0y.ui.components.PixelButton(
                text = stringResource(R.string.similar_refresh),
                onClick = onRefresh,
                enabled = !loading,
            )
        }
    }
    val rows = set?.items.orEmpty().distinctBy { it.song.id }
    if (rows.isEmpty() && !loading) {
        item(key = "similar-empty") {
            Text(
                stringResource(R.string.similar_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalW0yColors.current.textMuted,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
    items(rows, key = { "rec-" + it.song.id }) { rec ->
        RecRow(
            rec = rec,
            onPlay = { onPlay(rec.song) },
            onEnqueue = { onEnqueue(rec.song) },
            onNext = { onNext(rec.song) },
            onMore = { onMore(rec.song) },
            onLess = { onLess(rec.song) },
        )
    }
}
