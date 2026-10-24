package com.texfi.w0y.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.DockLabels
import com.texfi.w0y.data.DockShape
import com.texfi.w0y.data.DockTint
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.isSpecified
import kotlin.math.sin
import com.texfi.w0y.data.CornerStyle
import com.texfi.w0y.playback.PlayerUiState
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.MotionLevel
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Настройки панели: форма, чем подкрашена, подписи, свечение. */
data class DockConfig(
    val shape: DockShape = DockShape.ISLAND,
    val tint: DockTint = DockTint.COVER,
    val labels: DockLabels = DockLabels.ALL,
)

/**
 * Парящая панель Hairline: мини-плеер и навигация в одном корпусе.
 *
 *  - **цвет обложки** красит корпус сверху вниз;
 *  - **прогресс** — тонкая линия акцента по верхнему краю корпуса;
 *  - **линза** под активной вкладкой пружинит с перелётом, тянется каплей,
 *    а в момент выбора от неё расходится кольцо; значок подпрыгивает;
 *  - панель выезжает пружиной, мини-плеер раскрывается внутри неё.
 * Позиция и фазы читаются только при рисовании: строки не пересобираются.
 */
@Composable
fun HairDock(
    state: PlayerUiState,
    positionProvider: () -> Long,
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    config: DockConfig = DockConfig(),
    mini: @Composable () -> Unit,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val song = state.song
    val cover by rememberCoverTint(song?.thumbnailUrl, colors.accent)
    val toneAccent = Contrast.ensure(colors.accent, colors.background)
    val tintTarget = when (config.tint) {
        DockTint.COVER -> if (song != null) Contrast.ensure(cover, colors.surface) else toneAccent
        DockTint.ACCENT -> toneAccent
        DockTint.NONE -> colors.text
    }
    val tint by animateColorAsState(tintTarget, tween(600), label = "dockTintColor")
    val tinted = config.tint != DockTint.NONE

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.isPlaying, song?.id) {
        // На паузе тоже опрашиваем, но редко: перемотка на паузе должна сдвинуть контур.
        while (true) {
            position = positionProvider()
            delay(if (state.isPlaying) PROGRESS_POLL_MS else PAUSED_POLL_MS)
        }
    }
    val duration = state.durationMs
    val progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f }

    // Выезд снизу пружиной — один раз за жизнь панели.
    val rise = remember { Animatable(if (look.motion.moves) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (rise.value > 0f) rise.animateTo(0f, spring(HairTokens.SPRING_DAMPING, HairTokens.SPRING_STIFFNESS * 0.6f))
    }

    val bar = config.shape == DockShape.BAR
    val radius = when {
        bar -> 0.dp
        look.corners == CornerStyle.SHARP -> 10.dp
        else -> 28.dp
    }
    val shape = RoundedCornerShape(radius)

    Box(
        modifier
            .fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = if (bar) 0.dp else 12.dp, end = if (bar) 0.dp else 12.dp, top = if (bar) 0.dp else 6.dp, bottom = if (bar) 0.dp else 10.dp)
                .graphicsLayer {
                    translationY = rise.value * 96.dp.toPx()
                    alpha = 1f - rise.value * 0.6f
                }
                .clip(shape)
                .background(colors.surface)
                .dockFrame(radius, progress, lerp(colors.outline, tint, if (tinted) 0.2f else 0f), toneAccent),
        ) {
            mini()
            DockNav(items, selected, onSelect, tint, tinted, config.labels, radius)
        }
    }
}

/**
 * Рамка корпуса в одну линию 1dp. Прогресс трека — линия акцента по прямому участку верхнего
 * края, между скруглениями: читается как полоса прогресса, а не как белая обводка вокруг.
 */
private fun Modifier.dockFrame(
    radius: Dp,
    progress: () -> Float,
    border: Color,
    progressColor: Color,
): Modifier =
    drawWithCache {
        val line = 1.dp.toPx()
        val thick = 1.5.dp.toPx()
        val inset = line / 2f
        val r = radius.toPx().coerceIn(0f, min(size.width, size.height) / 2f)
        val start = r
        val span = (size.width - 2 * r).coerceAtLeast(0f)
        onDrawWithContent {
            drawContent()
            drawRoundRect(
                border,
                topLeft = Offset(inset, inset),
                size = Size(size.width - line, size.height - line),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius((r - inset).coerceAtLeast(0f)),
                style = Stroke(line),
            )
            val p = progress()
            if (p > 0f && span > 0f) {
                drawLine(progressColor, Offset(start, thick / 2f), Offset(start + span * p, thick / 2f), thick, cap = StrokeCap.Butt)
            }
        }
    }

/** Навигация внутри панели: вкладки и линза-капля, которая перетекает между ними. */
@Composable
private fun DockNav(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tint: Color,
    tinted: Boolean,
    labels: DockLabels,
    dockRadius: Dp,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val head = remember { Animatable(selected.toFloat()) }
    val tail = remember { Animatable(selected.toFloat()) }
    // Перелёт: пружина с малым демпфированием, передний край упруго проскакивает.
    val headSpec: AnimationSpec<Float> =
        when (look.motion) {
            MotionLevel.FULL -> spring(0.5f, 420f)
            MotionLevel.LITE -> tween(HairTokens.SHORT_MS, easing = HairTokens.EaseOut)
            MotionLevel.OFF -> snap()
        }
    val tailSpec: AnimationSpec<Float> =
        when (look.motion) {
            MotionLevel.FULL -> spring(0.82f, 170f)
            MotionLevel.LITE -> tween(HairTokens.SHORT_MS, easing = HairTokens.EaseOut)
            MotionLevel.OFF -> snap()
        }
    LaunchedEffect(selected) {
        scope.launch { head.animateTo(selected.toFloat(), headSpec) }
        scope.launch { tail.animateTo(selected.toFloat(), tailSpec) }
    }
    val tick = rememberTapHaptic()
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val lensTone = if (tinted) tint else colors.text

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(NAV_ITEM_HEIGHT)
            .drawBehind {
                val slot = size.width / items.size
                val w = (slot * 0.5f).coerceIn(36.dp.toPx(), 64.dp.toPx())
                val left = min(head.value, tail.value) * slot + (slot - w) / 2f
                val right = max(head.value, tail.value) * slot + (slot + w) / 2f
                val y = size.height / 2f + 14.dp.toPx()
                // Как на сайте: тонкая линия под активным пунктом, перетекает между ними.
                drawLine(lensTone, Offset(left, y), Offset(right, y), 2.dp.toPx(), StrokeCap.Round)
            }
            .pointerInput(items.size) {
                var last = -1
                fun moveTo(x: Float) {
                    val index = (x / (size.width.toFloat() / items.size)).toInt().coerceIn(0, items.lastIndex)
                    if (index != last) {
                        last = index
                        tick()
                        select(index)
                    }
                }
                detectHorizontalDragGestures(onDragStart = { last = currentSelected; moveTo(it.x) }) { change, _ ->
                    change.consume()
                    moveTo(change.position.x)
                }
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEachIndexed { index, item ->
            val active = index == selected
            val interaction = remember { MutableInteractionSource() }
            val iconTint by animateColorAsState(
                targetValue = if (active) colors.text else colors.textMuted,
                animationSpec = tween(HairTokens.SHORT_MS),
                label = "dockTint",
            )
            val pop by animateFloatAsState(
                if (active) 1f else 0f,
                if (look.motion.moves) spring(0.38f, 520f) else tween(HairTokens.SHORT_MS),
                label = "dockPop",
            )
            val showLabel = labels == DockLabels.ALL || (labels == DockLabels.ACTIVE && active)
            val glyph = when (index) {
                0 -> HairGlyph.HOME
                1 -> HairGlyph.SEARCH
                2 -> HairGlyph.YOURS
                else -> null
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(NAV_ITEM_HEIGHT)
                        .semantics {
                            contentDescription = item.label
                            role = Role.Tab
                            this.selected = active
                        }
                        .pressScale(interaction, pressed = 0.94f)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) },
            ) {
                val iconModifier =
                    Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            val k = 1f + 0.14f * pop
                            scaleX = k
                            scaleY = k
                        }
                if (glyph != null) {
                    HairIcon(glyph, iconTint, iconModifier)
                } else {
                    PixelSprite(rows = item.sprite, color = iconTint, modifier = iconModifier)
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLabel,
                    enter = androidx.compose.animation.fadeIn(tween(HairTokens.MID_MS)) + androidx.compose.animation.expandHorizontally(tween(HairTokens.MID_MS)),
                    exit = androidx.compose.animation.fadeOut(tween(HairTokens.SHORT_MS)) + androidx.compose.animation.shrinkHorizontally(tween(HairTokens.SHORT_MS)),
                ) {
                    Text(
                        text = item.label.asLabel(),
                        style = com.texfi.w0y.ui.theme.HairLabelStyle.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                        color = iconTint,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
        }
    }
}

private val NAV_ITEM_HEIGHT = 48.dp
private val LENS_MAX = 104.dp
private val LENS_RADIUS = 10.dp
private const val PROGRESS_POLL_MS = 400L
private const val PAUSED_POLL_MS = 1_000L
