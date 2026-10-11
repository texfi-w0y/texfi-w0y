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
import androidx.compose.ui.unit.dp
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

/**
 * Парящая панель Hairline: мини-плеер и навигация в одном корпусе.
 *
 * Что в ней живёт:
 *  - **контур — это прогресс**: тонкая линия обводит корпус по часовой
 *    стрелке от верхней середины, пока играет трек; отдельной полосы нет;
 *  - **линза** под активной вкладкой: при переключении передний край
 *    бежит быстрее заднего, и она на миг растягивается, как капля;
 *  - **свет от обложки**: цвет играющего трека мягко подсвечивает воздух
 *    над панелью — это градиент, а не размытие, поэтому дёшево;
 *  - панель выезжает пружиной, а мини-плеер раскрывается внутри неё.
 * Позиция читается только при рисовании: строки не пересобираются.
 */
@Composable
fun HairDock(
    state: PlayerUiState,
    positionProvider: () -> Long,
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mini: @Composable () -> Unit,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val song = state.song
    val cover by rememberCoverTint(song?.thumbnailUrl, colors.accent)
    val glowColor = animateColorAsState(cover, tween(500), label = "dockGlow")
    val glowOn = animateFloatAsState(if (song != null) 1f else 0f, tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut), label = "dockGlowOn")
    val tone = Contrast.ensure(colors.accent, colors.background)

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.isPlaying, song?.id) {
        position = positionProvider()
        while (state.isPlaying) {
            delay(PROGRESS_POLL_MS)
            position = positionProvider()
        }
    }
    val duration = state.durationMs
    val progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f }

    // Выезд снизу пружиной — один раз за жизнь панели.
    val rise = remember { Animatable(if (look.motion.moves) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (rise.value > 0f) rise.animateTo(0f, spring(HairTokens.SPRING_DAMPING, HairTokens.SPRING_STIFFNESS * 0.6f))
    }

    val radius = if (look.corners == CornerStyle.SHARP) 10.dp else look.cardRadius.coerceIn(10.dp, 26.dp)
    val shape = RoundedCornerShape(radius)

    Box(
        modifier
            .fillMaxWidth()
            // Свет обложки: эллипс от нижнего края вверх, выходит за границы панели.
            .drawBehind {
                val a = glowOn.value * GLOW_ALPHA
                if (a > 0.01f) {
                    val reach = GLOW_REACH.toPx()
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(glowColor.value.copy(alpha = a), glowColor.value.copy(alpha = a * 0.35f), Color.Transparent),
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
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
                .graphicsLayer {
                    translationY = rise.value * 96.dp.toPx()
                    alpha = 1f - rise.value * 0.6f
                }
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(colors.surface, Color.White, 0.07f).copy(alpha = 0.97f),
                            colors.surface.copy(alpha = 0.97f),
                        ),
                    ),
                )
                .dockFrame(radius, progress, colors.outline, colors.progress, colors.text),
        ) {
            mini()
            DockNav(items, selected, onSelect, tone, radius)
        }
    }
}

/** Рамка корпуса, блик по верхней кромке и контур-прогресс поверх содержимого. */
private fun Modifier.dockFrame(
    radius: androidx.compose.ui.unit.Dp,
    progress: () -> Float,
    border: Color,
    progressColor: Color,
    sheen: Color,
): Modifier =
    drawWithCache {
        val line = 1.dp.toPx()
        val half = line / 2f
        val rect = Rect(half, half, size.width - half, size.height - half)
        val frame = framePath(rect, radius.toPx())
        val measure = PathMeasure().also { it.setPath(frame, false) }
        val part = Path()
        val sheenBrush =
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to sheen.copy(alpha = 0.22f),
                1f to Color.Transparent,
            )
        onDrawWithContent {
            drawContent()
            drawPath(frame, border, style = Stroke(line))
            drawLine(sheenBrush, Offset(radius.toPx(), line * 1.5f), Offset(size.width - radius.toPx(), line * 1.5f), line)
            val p = progress()
            if (p > 0f) {
                part.rewind()
                measure.getSegment(0f, measure.length * p, part, true)
                drawPath(part, progressColor, style = Stroke(line * 2f, cap = StrokeCap.Round))
            }
        }
    }

/** Навигация внутри панели: вкладки и линза, которая перетекает между ними. */
@Composable
private fun DockNav(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tone: Color,
    dockRadius: androidx.compose.ui.unit.Dp,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val head = remember { Animatable(selected.toFloat()) }
    val tail = remember { Animatable(selected.toFloat()) }
    val headSpec: AnimationSpec<Float> =
        when (look.motion) {
            MotionLevel.FULL -> spring(0.72f, 520f)
            MotionLevel.LITE -> tween(HairTokens.SHORT_MS, easing = HairTokens.EaseOut)
            MotionLevel.OFF -> snap()
        }
    val tailSpec: AnimationSpec<Float> =
        when (look.motion) {
            MotionLevel.FULL -> spring(0.86f, 190f)
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
    val tagColor = colors.text

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .height(NAV_ITEM_HEIGHT)
            .drawBehind {
                // Метка-негатив под подписью активной вкладки, как «CRAFT APPS» у PhotoCraft.
                // Передний край бежит быстрее заднего — на переходе метка тянется каплей.
                val slot = size.width / items.size
                val tagW = TAG_WIDTH.toPx().coerceAtMost(slot - 8.dp.toPx())
                val pad = (slot - tagW) / 2f
                val left = min(head.value, tail.value) * slot + pad
                val right = (max(head.value, tail.value) + 1f) * slot - pad
                val bottom = size.height - TAG_BOTTOM.toPx()
                val box = Rect(left, bottom - TAG_HEIGHT.toPx(), right, bottom)
                drawRoundRect(tagColor, box.topLeft, box.size, CornerRadius(2.dp.toPx()))
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
            val tint by animateColorAsState(
                targetValue = if (active) colors.text else colors.textMuted,
                animationSpec = tween(HairTokens.SHORT_MS),
                label = "dockTint",
            )
            // Подпись активной лежит на светлой метке — значит, сама тёмная.
            val labelTint by animateColorAsState(
                targetValue = if (active) colors.background else colors.textMuted,
                animationSpec = tween(HairTokens.MID_MS),
                label = "dockLabel",
            )
            val lift by animateFloatAsState(if (active) 1f else 0f, tween(HairTokens.MID_MS, easing = HairTokens.EaseOut), label = "dockLift")
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
                        .pressScale(interaction, pressed = 0.94f)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) },
            ) {
                PixelSprite(
                    rows = item.sprite,
                    color = tint,
                    modifier =
                        Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                val k = 1f + 0.1f * lift
                                scaleX = k
                                scaleY = k
                                translationY = -2.dp.toPx() * lift
                            },
                )
                Text(
                    text = item.label.asLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelTint,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private val NAV_ITEM_HEIGHT = 52.dp
private val TAG_WIDTH = 76.dp
private val TAG_HEIGHT = 19.dp
private val TAG_BOTTOM = 4.dp
private const val PROGRESS_POLL_MS = 400L
private const val GLOW_ALPHA = 0.34f
private val GLOW_REACH = 90.dp
