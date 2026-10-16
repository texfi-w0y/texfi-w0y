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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.launch
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

/** Три отдельные панели плеера: у каждой своя тема, свои вкладки и свой жест закрытия. */
enum class PlayerPanel { SLEEP, QUEUE, MORE }

/** Вкладки панели очереди. */
enum class QueueTab(@StringRes val label: Int) {
    QUEUE(R.string.sheet_queue),
    SIMILAR(R.string.sheet_similar),
}

/** Вкладки панели «Ещё». */
enum class MoreTab(@StringRes val label: Int) {
    SOUND(R.string.sheet_sound),
    SPEED(R.string.sheet_speed),
    LOOK(R.string.sheet_look),
}

/** Выбор таймера сна: без `!!` и без индексов в списке с null. */
enum class SleepChoice(val minutes: Int?) {
    OFF(null),
    TRACK(0),
    M15(15),
    M30(30),
    M60(60),
}

/** Последние открытые вкладки помнятся, пока приложение живо. */
object PlayerPanelMemory {
    var queueTab: QueueTab = QueueTab.QUEUE
    var moreTab: MoreTab = MoreTab.SOUND
}

/**
 * Полоска внизу плеера: «ОЧЕРЕДЬ · N» с маленьким значком состояния справа.
 * Нажать или потянуть вверх — открыть панель.
 */
@Composable
fun PlayerSheetHandle(label: String, badge: String?, onOpen: () -> Unit, modifier: Modifier = Modifier, onBadge: (() -> Unit)? = null) {
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
            val badgeCd = stringResource(R.string.sheet_badge_cd)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = badgeCd; role = Role.Button }
                    .then(if (onBadge != null) Modifier.clickable(onClick = onBadge) else Modifier)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(badge.asLabel(), style = HairLabelStyle, color = Contrast.ensure(colors.accent, colors.background))
            }
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
 *
 * Закрывается жестом вниз (по ручке, по затемнению и тягой вниз за прокрутку, когда список
 * уже наверху), кнопкой «назад» и нажатием по затемнению. Тяга вниз целиком остаётся у панели:
 * остаток жеста не уходит выше, поэтому плеер под панелью не сворачивается.
 * Содержимое панели должно прокручиваться (список или `verticalScroll`) — тогда жест
 * доходит до этого каркаса.
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
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissAt = with(density) { 96.dp.toPx() }
    var dragPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var heightPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    // Сброс смещения — только когда панель уже уехала: иначе она на миг «прыгала» обратно вверх перед закрытием.
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) {
            dragPx = 0f
        } else {
            kotlinx.coroutines.delay(500)
            dragPx = 0f
        }
    }
    val currentDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    fun settle(velocityY: Float = 0f) {
        if (dragPx > dismissAt || velocityY > 1600f) {
            // Смахнули: панель доезжает вниз той же рукой, а не пропадает рывком.
            scope.launch {
                androidx.compose.animation.core.animate(dragPx, heightPx, initialVelocity = velocityY.coerceAtLeast(0f), animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { v, _ -> dragPx = v }
                currentDismiss()
            }
        } else {
            scope.launch { androidx.compose.animation.core.animate(dragPx, 0f) { v, _ -> dragPx = v } }
        }
    }
    val connection =
        remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (dragPx > 0f && available.y < 0f) {
                        val next = (dragPx + available.y).coerceAtLeast(0f)
                        val used = next - dragPx
                        dragPx = next
                        return androidx.compose.ui.geometry.Offset(0f, used)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                override fun onPostScroll(
                    consumed: androidx.compose.ui.geometry.Offset,
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    // Список уже наверху: оставшаяся тяга вниз двигает панель и дальше не уходит.
                    if (available.y > 0f) {
                        dragPx += available.y
                        return androidx.compose.ui.geometry.Offset(0f, available.y)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    if (dragPx <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                    settle(available.y)
                    return available
                }

                override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity =
                    // Остаток броска вниз не передаётся плееру под панелью.
                    available
            }
        }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(hairSpec()), exit = fadeOut(hairSpec())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f * (1f - (dragPx / heightPx).coerceIn(0f, 1f))))
                    .testTag("panel-scrim")
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onVerticalDrag = { _, dy -> drag += dy },
                            onDragEnd = { if (drag > 48.dp.toPx()) currentDismiss() },
                        )
                    }
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
                    .offset { IntOffset(0, dragPx.toInt()) }
                    .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .nestedScroll(connection)
                    .fillMaxWidth()
                    .then(if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier)
                    .clip(shape)
                    .background(colors.surface)
                    .border(look.line, colors.outline, shape)
                    .testTag("panel")
                    .navigationBarsPadding(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .semantics { contentDescription = close }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { },
                                onVerticalDrag = { _, dy -> dragPx = (dragPx + dy).coerceAtLeast(0f) },
                                onDragEnd = { settle() },
                                onDragCancel = { settle() },
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

/**
 * Полоса вкладок панели: 2–3 вкладки делят ширину поровну, без прокрутки и без усечения;
 * под активной — акцентная линия, под всей полосой — тонкая.
 */
@Composable
fun PanelTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val active = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("panel-tab-$i")
                        .semantics { role = Role.Tab; this.selected = active }
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.asLabel(),
                        style = HairLabelStyle,
                        color = if (active) colors.text else colors.textMuted,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (active) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(colors.accent))
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(hairLook.line).background(colors.outline))
    }
}

/** Панель с вкладками и прокручиваемым списком. [heightFraction] = null — высота по содержимому. */
@Composable
private fun TabbedPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    heightFraction: Float?,
    content: LazyListScope.() -> Unit,
) {
    HairSheetFrame(visible = visible, onDismiss = onDismiss, heightFraction = heightFraction) {
        PanelTabs(labels, selected, onSelect)
        Spacer(Modifier.height(10.dp))
        val maxBody = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.62f).dp
        LazyColumn(
            (if (heightFraction != null) Modifier.weight(1f) else Modifier.heightIn(max = maxBody)).testTag("panel-list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            content()
        }
    }
}

/** Панель очереди: вкладки «Очередь» и «Похожее», высокая (~85% экрана). */
@Composable
fun QueuePanel(
    visible: Boolean,
    tab: QueueTab,
    onTab: (QueueTab) -> Unit,
    onDismiss: () -> Unit,
    content: LazyListScope.(QueueTab) -> Unit,
) {
    TabbedPanel(
        visible = visible,
        onDismiss = onDismiss,
        labels = QueueTab.entries.map { stringResource(it.label) },
        selected = tab.ordinal,
        onSelect = { onTab(QueueTab.entries[it]) },
        heightFraction = 0.85f,
    ) { content(tab) }
}

/** Панель «Ещё»: звук, скорость и вид; высота по содержимому. */
@Composable
fun MorePanel(
    visible: Boolean,
    tab: MoreTab,
    onTab: (MoreTab) -> Unit,
    onDismiss: () -> Unit,
    content: LazyListScope.(MoreTab) -> Unit,
) {
    TabbedPanel(
        visible = visible,
        onDismiss = onDismiss,
        labels = MoreTab.entries.map { stringResource(it.label) },
        selected = tab.ordinal,
        onSelect = { onTab(MoreTab.entries[it]) },
        heightFraction = null,
    ) { content(tab) }
}

/** Панель таймера сна: только таймер, компактная. */
@Composable
fun SleepPanel(
    visible: Boolean,
    remainingMs: Long?,
    afterTrack: Boolean,
    onChoose: (SleepChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    HairSheetFrame(visible = visible, onDismiss = onDismiss, heightFraction = null) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .testTag("sleep-panel"),
        ) {
            MenuChips(
                title = stringResource(R.string.player_sleep_title),
                hint = remainingMs?.let { "${it / 60_000}:${"%02d".format(it / 1000 % 60)}" },
                options =
                    listOf(
                        stringResource(R.string.player_sleep_off),
                        stringResource(R.string.player_sleep_track),
                        stringResource(R.string.settings_sleep_minutes, 15),
                        stringResource(R.string.settings_sleep_minutes, 30),
                        stringResource(R.string.settings_sleep_minutes, 60),
                    ),
                selected =
                    when {
                        afterTrack -> SleepChoice.TRACK.ordinal
                        remainingMs != null -> -1
                        else -> SleepChoice.OFF.ordinal
                    },
                onSelect = {
                    onChoose(SleepChoice.entries[it])
                    onDismiss()
                },
            )
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
    showWhy: Boolean = true,
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
            if (showWhy) {
                Text(
                    whyLabel(rec.why).asLabel(),
                    style = HairLabelStyle,
                    color = Contrast.ensure(colors.accent, colors.surface),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    // Общая причина («ПОХОЖЕ НА …») стоит один раз над списком; у строк остаётся только то, что отличается.
    val shared = SimilarReasons.shared(rows.map { it.why })
    if (shared != null) {
        item(key = "similar-reason") {
            Text(
                whyLabel(shared).asLabel(),
                style = HairLabelStyle,
                color = Contrast.ensure(LocalW0yColors.current.accent, LocalW0yColors.current.surface),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
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
            showWhy = shared == null || rec.why != shared,
        )
    }
}

/** Какая причина общая для подборки: одна и та же у большинства строк, иначе её нет. */
object SimilarReasons {
    fun shared(reasons: List<Why>): Why? {
        if (reasons.size < 2) return null
        val top = reasons.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return top.key.takeIf { top.value >= 2 && top.value * 2 > reasons.size }
    }
}
