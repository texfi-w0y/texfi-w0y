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
    val glow: Boolean = true,
)

/**
 * Парящая панель Hairline: мини-плеер и навигация в одном корпусе.
 *
 *  - **цвет обложки** красит корпус сверху вниз, а внутри медленно плывёт
 *    пятно того же цвета — панель «живёт» вместе с треком;
 *  - **контур — прогресс**: линия со свечением и яркой «головкой» обводит
 *    корпус по часовой стрелке;
 *  - **линза** под активной вкладкой пружинит с перелётом, тянется каплей,
 *    а в момент выбора от неё расходится кольцо; значок подпрыгивает;
 *  - **дыхание**: пока играет музыка, свет вокруг панели пульсирует;
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
    val glowOn = animateFloatAsState(if (song != null && config.glow) 1f else 0f, tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut), label = "dockGlowOn")

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

    // Фаза дыхания и плавания пятна: только пока играет и анимации включены.
    val alive = state.isPlaying && look.motion.moves && config.glow
    val phase = rememberAnimationPhase(9_000, 30, enabled = alive)

    // Выезд снизу пружиной — один раз за жизнь панели.
    val rise = remember { Animatable(if (look.motion.moves) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (rise.value > 0f) rise.animateTo(0f, spring(HairTokens.SPRING_DAMPING, HairTokens.SPRING_STIFFNESS * 0.6f))
    }

    val bar = config.shape == DockShape.BAR
    val radius = when {
        bar -> 0.dp
        look.corners == CornerStyle.SHARP -> 10.dp
        else -> look.cardRadius.coerceIn(10.dp, 28.dp)
    }
    val shape = RoundedCornerShape(radius)

    Box(
        modifier
            .fillMaxWidth()
            // Свет вокруг панели: эллипс от нижнего края вверх, дышит в такт фазе.
            .drawBehind {
                val breath = if (alive) 0.82f + 0.18f * sin(phase.floatValue * 2f) else 1f
                val a = glowOn.value * GLOW_ALPHA * breath
                if (a > 0.01f && tinted) {
                    val reach = GLOW_REACH.toPx()
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(tint.copy(alpha = a), tint.copy(alpha = a * 0.35f), Color.Transparent),
                                center = Offset(size.width / 2f, size.height),
                                radius = size.width * 0.7f,
                            ),
                        topLeft = Offset(0f, -reach),
                        size = Size(size.width, size.height + reach),
                    )
                }
            },
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
                // Едва заметная подкраска сверху цветом обложки — без заливок и бликов.
                .background(
                    Brush.verticalGradient(
                        0f to (if (tinted) tint.copy(alpha = 0.10f) else Color.Transparent),
                        0.7f to Color.Transparent,
                    ),
                )
                .dockFrame(radius, progress, lerp(colors.outline, tint, if (tinted) 0.45f else 0f), if (tinted) tint else colors.progress, colors.text, config.glow),
        ) {
            mini()
            DockNav(items, selected, onSelect, tint, tinted, config.labels, radius)
        }
    }
}

/**
 * Рамка корпуса в одну линию и прогресс по верхней кромке: тонкая линия
 * цвета обложки слева направо, под ней мягкий след и точка-«головка».
 * Линия лежит внутри корпуса, поэтому не обрезается скруглением.
 */
private fun Modifier.dockFrame(
    radius: Dp,
    progress: () -> Float,
    border: Color,
    progressColor: Color,
    sheen: Color,
    glow: Boolean,
): Modifier =
    drawWithCache {
        val line = 1.dp.toPx()
        val half = line / 2f
        val frame = framePath(Rect(half, half, size.width - half, size.height - half), radius.toPx())
        val edge = max(radius.toPx() * 0.7f, 6.dp.toPx())
        onDrawWithContent {
            drawContent()
            drawPath(frame, border, style = Stroke(line))
            val p = progress()
            if (p > 0f) {
                val y = 1.5.dp.toPx()
                val from = edge
                val to = size.width - edge
                val x = from + (to - from) * p
                if (glow) {
                    drawRect(
                        Brush.verticalGradient(listOf(progressColor.copy(alpha = 0.30f), Color.Transparent), startY = 0f, endY = 12.dp.toPx()),
                        Offset(from, 0f),
                        Size(x - from, 12.dp.toPx()),
                    )
                }
                drawLine(progressColor, Offset(from, y), Offset(x, y), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(sheen, 3.dp.toPx(), Offset(x, y))
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
    // Кольцо от линзы в момент выбора.
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        scope.launch { head.animateTo(selected.toFloat(), headSpec) }
        scope.launch { tail.animateTo(selected.toFloat(), tailSpec) }
        if (look.motion.draws) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(650, easing = HairTokens.EaseOut))
        }
    }
    val tick = rememberTapHaptic()
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val lensTone = if (tinted) tint else colors.text

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .height(NAV_ITEM_HEIGHT)
            .drawBehind {
                val slot = size.width / items.size
                val lensW = (slot - 14.dp.toPx()).coerceAtMost(LENS_MAX.toPx())
                val pad = (slot - lensW) / 2f
                val left = min(head.value, tail.value) * slot + pad
                val right = (max(head.value, tail.value) + 1f) * slot - pad
                val box = Rect(left, 2.dp.toPx(), right, size.height - 2.dp.toPx())
                val corner = CornerRadius(LENS_RADIUS.toPx())
                // Линза-контур: тонкая линия цвета панели, чуть подсвеченная внутри.
                drawRoundRect(lensTone.copy(alpha = 0.09f), box.topLeft, box.size, corner)
                drawRoundRect(lensTone.copy(alpha = 0.7f), box.topLeft, box.size, corner, style = Stroke(1.dp.toPx()))
                val r = ripple.value
                if (r < 1f) {
                    drawRoundRect(
                        lensTone.copy(alpha = 0.5f * (1f - r)),
                        Offset(box.left - r * 14.dp.toPx(), box.top - r * 6.dp.toPx()),
                        Size(box.width + r * 28.dp.toPx(), box.height + r * 12.dp.toPx()),
                        CornerRadius(LENS_RADIUS.toPx() + r * 6.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                }
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
            // Значок подпрыгивает с перелётом: пружина с малым демпфированием.
            val pop by animateFloatAsState(
                if (active) 1f else 0f,
                if (look.motion.moves) spring(0.38f, 520f) else tween(HairTokens.SHORT_MS),
                label = "dockPop",
            )
            val labelAlpha by animateFloatAsState(
                when (labels) {
                    DockLabels.ALL -> if (active) 1f else 0.7f
                    DockLabels.ACTIVE -> if (active) 1f else 0f
                    DockLabels.NONE -> 0f
                },
                tween(HairTokens.MID_MS),
                label = "dockLabelAlpha",
            )
            val glyph = when (index) {
                0 -> HairGlyph.HOME
                1 -> HairGlyph.SEARCH
                2 -> HairGlyph.YOURS
                else -> null
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(NAV_ITEM_HEIGHT)
                        .semantics {
                            contentDescription = item.label
                            role = Role.Tab
                            this.selected = active
                        }
                        .pressScale(interaction, pressed = 0.92f)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) },
            ) {
                val iconModifier =
                    Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            val k = 1f + 0.22f * pop
                            scaleX = k
                            scaleY = k
                            translationY = -(if (labels == DockLabels.NONE) 0f else 2.dp.toPx()) * pop
                        }
                if (glyph != null) {
                    HairIcon(glyph, iconTint, iconModifier)
                } else {
                    PixelSprite(rows = item.sprite, color = iconTint, modifier = iconModifier)
                }
                if (labels != DockLabels.NONE) {
                    Text(
                        text = item.label.asLabel(),
                        style = com.texfi.w0y.ui.theme.HairLabelStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                        color = (if (active) colors.text else colors.textMuted).copy(alpha = labelAlpha),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

private val NAV_ITEM_HEIGHT = 56.dp
private val LENS_MAX = 104.dp
private val LENS_RADIUS = 10.dp
private const val PROGRESS_POLL_MS = 400L
private const val PAUSED_POLL_MS = 1_000L
private const val GLOW_ALPHA = 0.26f
private val GLOW_REACH = 100.dp
