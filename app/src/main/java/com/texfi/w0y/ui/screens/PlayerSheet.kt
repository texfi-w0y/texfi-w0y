package com.texfi.w0y.ui.screens

import androidx.compose.ui.draw.drawBehind

import com.texfi.w0y.ui.theme.matte

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
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
import com.texfi.w0y.ui.theme.HairTokens
import androidx.compose.animation.core.VisibilityThreshold
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
            // Hairline New: плашку уже держит матовый остров вокруг, своя рамка была бы второй.
            .then(if (com.texfi.w0y.ui.theme.isIsland) Modifier else Modifier.border(look.line, colors.outline, RoundedCornerShape(look.cardRadius.coerceAtMost(12.dp))))
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
 * Затемнение под нижней панелью, которое оболочка дорисовывает поверх островка. Панель живёт в
 * области экрана над островком (иначе он закрывал бы её низ), поэтому её собственная вуаль
 * обрывалась на его верхнем крае; оболочка продолжает её до самого низа с той же яркостью.
 */
class ModalDim {
    var alpha by androidx.compose.runtime.mutableFloatStateOf(0f)
    var dismiss: (() -> Unit)? by androidx.compose.runtime.mutableStateOf(null)
    internal var owner: Any? = null
}

val LocalModalDim = androidx.compose.runtime.staticCompositionLocalOf<ModalDim?> { null }

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
    // dragPx — сколько протянул палец; панель едет не за ним, а через «магнит» [magnetPx]:
    // до порога её держит (идёт втрое медленнее пальца), за порогом отпускает с щелчком.
    var dragPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var heightPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val haptic = com.texfi.w0y.ui.components.rememberHaptics()
    var pastMagnet by remember { mutableStateOf(false) }
    val magnet = LocalSheetMagnet.current
    val sheetPx = if (magnet) magnetPx(dragPx, dismissAt) else dragPx
    androidx.compose.runtime.SideEffect {
        val past = dragPx > dismissAt
        if (magnet && past != pastMagnet) {
            pastMagnet = past
            haptic(if (past) com.texfi.w0y.ui.components.Buzz.TAP else com.texfi.w0y.ui.components.Buzz.OFF)
        }
    }
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
    val shown by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, hairSpec(), label = "sheetDim")
    val dim = LocalModalDim.current
    val dimKey = remember { Any() }
    val dimAlpha = 0.55f * shown * (1f - (sheetPx / heightPx).coerceIn(0f, 1f))
    androidx.compose.runtime.SideEffect {
        if (dim != null) {
            if (dimAlpha > 0.001f) {
                dim.owner = dimKey
                dim.alpha = dimAlpha
                dim.dismiss = { currentDismiss() }
            } else if (dim.owner === dimKey) {
                dim.owner = null
                dim.alpha = 0f
                dim.dismiss = null
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(dim) {
        onDispose {
            if (dim?.owner === dimKey) {
                dim.owner = null
                dim.alpha = 0f
                dim.dismiss = null
            }
        }
    }
    fun settle(velocityY: Float = 0f) {
        if (dragPx > dismissAt || velocityY > 1600f) {
            // Сорвали с магнита: панель доезжает вниз той же рукой, а не пропадает рывком.
            scope.launch {
                val target = heightPx + dismissAt
                androidx.compose.animation.core.animate(dragPx.coerceAtLeast(dismissAt), target, initialVelocity = velocityY.coerceAtLeast(0f), animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { v, _ -> dragPx = v }
                currentDismiss()
            }
        } else {
            // Не дотянули: магнит возвращает панель на место с лёгкой отдачей.
            scope.launch {
                androidx.compose.animation.core.animate(
                    dragPx,
                    0f,
                    initialVelocity = velocityY,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 700f),
                ) { v, _ -> dragPx = v }
            }
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
                    // Бросок списка (не палец) панель не тянет — иначе она дёргалась бы в конце прокрутки.
                    if (available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
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
    val moves = look.motion.moves
    val bottomInset = com.texfi.w0y.ui.components.LocalBottomInset.current
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(hairSpec()), exit = fadeOut(hairSpec())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f * (1f - (sheetPx / heightPx).coerceIn(0f, 1f))))
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
        // Появление: панель поднимается на пружине и одновременно «раскрывается» из чуть меньшей
        // карточки; содержимое догоняет её на полшага позже. Уход — быстрее и без пружины.
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter =
                if (moves) {
                    slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)) { it / 2 } +
                        androidx.compose.animation.scaleIn(
                            androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 420f),
                            initialScale = 0.9f,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                        ) +
                        fadeIn(hairSpec(HairTokens.SHORT_MS))
                } else {
                    fadeIn(hairSpec())
                },
            exit =
                if (moves) {
                    slideOutVertically(hairSpec<IntOffset>(HairTokens.MID_MS)) { it } + fadeOut(hairSpec(HairTokens.MID_MS))
                } else {
                    fadeOut(hairSpec())
                },
        ) {
            val island = com.texfi.w0y.ui.theme.isIsland
            val shape = RoundedCornerShape(topStart = if (island) 26.dp else 18.dp, topEnd = if (island) 26.dp else 18.dp)
            // Содержимое въезжает следом за листом: снизу на 18 dp и из прозрачности.
            val inner = remember { androidx.compose.animation.core.Animatable(if (moves) 0f else 1f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (inner.value < 1f) {
                    kotlinx.coroutines.delay(70)
                    inner.animateTo(1f, androidx.compose.animation.core.tween(HairTokens.MID_MS, easing = HairTokens.EaseOut))
                }
            }
            Column(
                Modifier
                    .offset { IntOffset(0, sheetPx.toInt()) }
                    .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .nestedScroll(connection)
                    .fillMaxWidth()
                    .then(if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier)
                    .then(
                        // Hairline New: панель — матовый лист с зерном и светлой кромкой сверху.
                        if (island) {
                            Modifier.matte(shape, fill = colors.surface.copy(alpha = 1f), alpha = 0.94f)
                        } else {
                            Modifier.clip(shape).background(colors.surface).sheetOutline(colors.outline, look.line, 18.dp)
                        },
                    )
                    .testTag("panel")
                    // Во вкладках низ экрана занят островом: содержимое панели кончается над ним,
                    // а сама панель уходит под затемнённый остров. В плеере острова нет — только жестовая полоса.
                    .then(if (bottomInset > 0.dp) Modifier.padding(bottom = bottomInset) else Modifier.navigationBarsPadding()),
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
                    // Ручка тянется вслед за пальцем, пока панель держит магнит, — видно, что её держат.
                    val stretch = (dragPx / dismissAt).coerceIn(0f, 1f)
                    Box(Modifier.width(36.dp + 20.dp * stretch).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (pastMagnet) colors.text.copy(alpha = 0.6f) else colors.outline))
                }
                Column(
                    Modifier.graphicsLayer {
                        alpha = inner.value
                        translationY = (1f - inner.value) * 18.dp.toPx()
                    },
                ) { content() }
            }
        }
    }
}

/** Магнит нижних панелей включён (Настройки → Вид); выключен — панель едет за пальцем. */
val LocalSheetMagnet = androidx.compose.runtime.compositionLocalOf { true }

/**
 * «Магнит» нижних панелей: до [threshold] панель идёт за пальцем втрое медленнее — её держит;
 * за порогом отрывается и дальше едет вровень с пальцем, догнав его плавно, без скачка.
 */
internal fun magnetPx(drag: Float, threshold: Float): Float {
    if (drag <= 0f) return 0f
    val held = threshold * 0.32f
    if (drag <= threshold) {
        // Сопротивление растёт к порогу: мягкая кривая, а не постоянный коэффициент.
        val t = drag / threshold
        return held * (1f - (1f - t) * (1f - t))
    }
    return held + (drag - threshold) * 1.35f
}

/**
 * Контур панели без нижней кромки: сверху скругление, по бокам линии, которые к низу тают.
 * Раньше рамка шла по всему периметру, и у самого низа экрана под жестовой полосой торчала
 * лишняя горизонтальная черта, а боковые линии упирались в край.
 */
private fun Modifier.sheetOutline(color: Color, width: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp): Modifier =
    drawWithCache {
        val w = width.toPx().coerceAtLeast(1f)
        val half = w / 2f
        val r = radius.toPx()
        val path =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(half, size.height)
                lineTo(half, r)
                arcTo(androidx.compose.ui.geometry.Rect(half, half, half + 2 * r, half + 2 * r), 180f, 90f, false)
                lineTo(size.width - half - r, half)
                arcTo(androidx.compose.ui.geometry.Rect(size.width - half - 2 * r, half, size.width - half, half + 2 * r), 270f, 90f, false)
                lineTo(size.width - half, size.height)
            }
        val brush = androidx.compose.ui.graphics.Brush.verticalGradient(0f to color, 0.6f to color, 1f to Color.Transparent)
        onDrawWithContent {
            drawContent()
            drawPath(path, brush, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        }
    }

/**
 * Полоса вкладок панели: 2–3 вкладки делят ширину поровну, без прокрутки и без усечения;
 * под активной — акцентная линия, под всей полосой — тонкая.
 */
@Composable
fun PanelTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (com.texfi.w0y.ui.theme.isIsland) {
        com.texfi.w0y.ui.components.IslandSegmented(labels, selected, onSelect, modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        return
    }
    val colors = LocalW0yColors.current
    val look = hairLook
    // Акцентная линия не перепрыгивает, а перетекает: передний край спешит, задний догоняет —
    // на ходу линия вытягивается и у новой вкладки снова собирается.
    val head = remember { androidx.compose.animation.core.Animatable(selected.toFloat()) }
    val tail = remember { androidx.compose.animation.core.Animatable(selected.toFloat()) }
    androidx.compose.runtime.LaunchedEffect(selected) {
        val moves = look.motion.moves
        kotlinx.coroutines.coroutineScope {
            launch { if (moves) head.animateTo(selected.toFloat(), androidx.compose.animation.core.spring(0.7f, 600f)) else head.snapTo(selected.toFloat()) }
            launch { if (moves) tail.animateTo(selected.toFloat(), androidx.compose.animation.core.spring(0.9f, 220f)) else tail.snapTo(selected.toFloat()) }
        }
    }
    val tone = com.texfi.w0y.ui.theme.Contrast.ensure(colors.accent, colors.surface)
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val active = i == selected
                val tone by androidx.compose.animation.animateColorAsState(if (active) colors.text else colors.textMuted, hairSpec(com.texfi.w0y.ui.theme.HairTokens.MID_MS), label = "tabTone")
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("panel-tab-$i")
                        .semantics { role = Role.Tab; this.selected = active }
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label.asLabel(), style = HairLabelStyle, color = tone, maxLines = 1, softWrap = false)
                }
            }
        }
        // Базовая линия и акцент рисуются вместе: акцент — короче вкладки, со скруглёнными концами,
        // и никогда не вылезает за края полосы.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .drawWithCache {
                    val line = look.line.toPx().coerceAtLeast(1f)
                    onDrawBehind {
                        val y = size.height - line / 2f
                        drawLine(colors.outline, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), line)
                        val slot = size.width / labels.size.coerceAtLeast(1)
                        val inset = slot * 0.18f
                        val lo = minOf(head.value, tail.value) * slot + inset
                        val hi = (maxOf(head.value, tail.value) + 1f) * slot - inset
                        val ay = size.height - 1.dp.toPx()
                        drawLine(
                            tone,
                            androidx.compose.ui.geometry.Offset(lo.coerceAtLeast(1.dp.toPx()), ay),
                            androidx.compose.ui.geometry.Offset(hi.coerceAtMost(size.width - 1.dp.toPx()), ay),
                            2.dp.toPx(),
                            androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }
                },
        )
    }
}

/**
 * Панель с вкладками и прокручиваемым списком. [heightFraction] = null — высота по содержимому.
 * Смена вкладки — не скачок: новое содержимое въезжает с той стороны, куда переключили, старое
 * уходит в другую и гаснет; высота панели меняется плавно.
 */
@Composable
private fun TabbedPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    heightFraction: Float?,
    content: LazyListScope.(Int) -> Unit,
) {
    val slideIn = hairSpec<IntOffset>(com.texfi.w0y.ui.theme.HairTokens.LONG_MS)
    val slideOut = hairSpec<IntOffset>(com.texfi.w0y.ui.theme.HairTokens.MID_MS)
    val fadeInSpec = hairSpec<Float>(com.texfi.w0y.ui.theme.HairTokens.LONG_MS)
    val fadeOutSpec = hairSpec<Float>(com.texfi.w0y.ui.theme.HairTokens.SHORT_MS)
    val sizeSpec = hairSpec<androidx.compose.ui.unit.IntSize>(com.texfi.w0y.ui.theme.HairTokens.MID_MS)
    HairSheetFrame(visible = visible, onDismiss = onDismiss, heightFraction = heightFraction) {
        PanelTabs(labels, selected, onSelect)
        Spacer(Modifier.height(10.dp))
        val maxBody = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.62f).dp
        androidx.compose.animation.AnimatedContent(
            targetState = selected,
            modifier = if (heightFraction != null) Modifier.weight(1f) else Modifier,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (androidx.compose.animation.slideInHorizontally(slideIn) { it * dir / 4 } + fadeIn(fadeInSpec))
                    .togetherWith(androidx.compose.animation.slideOutHorizontally(slideOut) { -it * dir / 4 } + fadeOut(fadeOutSpec))
                    .using(androidx.compose.animation.SizeTransform(clip = true) { _, _ -> sizeSpec })
            },
            label = "panelTab",
        ) { tab ->
            LazyColumn(
                (if (heightFraction != null) Modifier.fillMaxSize() else Modifier.heightIn(max = maxBody))
                    .testTag("panel-list")
                    // Список у нижнего края тает в фоне панели, а не обрезается по линейке.
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fade = 28.dp.toPx() / size.height.coerceAtLeast(1f)
                        drawRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black, (1f - fade).coerceIn(0f, 1f) to Color.Black, 1f to Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                content(tab)
            }
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
    ) { i -> content(QueueTab.entries[i]) }
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
    ) { i -> content(MoreTab.entries[i]) }
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
            Text(rec.song.artist, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            // Второстепенное действие — контурная кнопка, а не белая плашка.
            com.texfi.w0y.ui.components.PixelButton(
                text = stringResource(R.string.similar_refresh),
                onClick = onRefresh,
                fill = colors.surfaceHigh,
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
