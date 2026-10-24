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
import com.texfi.w0y.ui.theme.glassBar
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Настройки панели: форма, чем подкрашена, подписи, свечение, корпус, прогресс, отметка вкладки и высота. */
data class DockConfig(
    val shape: DockShape = DockShape.ISLAND,
    val tint: DockTint = DockTint.COVER,
    val labels: DockLabels = DockLabels.ALL,
    val glow: Boolean = true,
    val surface: com.texfi.w0y.data.DockSurface = com.texfi.w0y.data.DockSurface.GLASS,
    val progress: Boolean = true,
    val indicator: com.texfi.w0y.data.DockIndicator = com.texfi.w0y.data.DockIndicator.LINE,
    val size: com.texfi.w0y.data.DockSize = com.texfi.w0y.data.DockSize.REGULAR,
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
    /** Экран под панелью: есть — корпус стеклянный и размывает его. */
    backdrop: com.kyant.backdrop.Backdrop? = null,
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
        else -> 28.dp
    }
    val shape = RoundedCornerShape(radius)
    val glass = backdrop != null && config.surface == com.texfi.w0y.data.DockSurface.GLASS
    val compact = config.size == com.texfi.w0y.data.DockSize.COMPACT

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
                .then(
                    if (glass) {
                        // Стекло: экран под панелью размыт и чуть преломлён у краёв, сверху — дымка.
                        Modifier.glassBar(shape, backdrop!!).clip(shape)
                    } else {
                        Modifier.clip(shape).background(colors.surface).dockSheen(tint, tinted)
                    },
                )
                .dockFrame(
                    radius,
                    if (config.progress) progress else ({ 0f }),
                    lerp(colors.outline, tint, if (tinted) 0.2f else 0f).copy(alpha = if (glass) 0.7f else 1f),
                    colors.text,
                    colors.text,
                ),
        ) {
            mini()
            DockNav(items, selected, onSelect, tint, tinted, config.labels, radius, config.indicator, compact)
        }
    }
}

/**
 * Плотный корпус не плоский: сверху тонкий отсвет, снизу едва заметный цвет трека — панель
 * читается как предмет над экраном, а не как вырезанный прямоугольник.
 */
private fun Modifier.dockSheen(tint: Color, tinted: Boolean): Modifier =
    drawBehind {
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.06f), 0.45f to Color.Transparent))
        if (tinted) drawRect(Brush.verticalGradient(0.4f to Color.Transparent, 1f to tint.copy(alpha = 0.10f)))
    }

/**
 * Рамка корпуса в одну линию. Прогресс идёт по ней же, по часовой, от левого
 * края через верх, правый бок и низ — линия проходит остров целиком до конца и
 * обводит скругления. Лежит на 1dp внутри корпуса, поэтому не обрезается.
 * Впереди короткий светлый «носик» того же размера.
 */
private fun Modifier.dockFrame(
    radius: Dp,
    progress: () -> Float,
    border: Color,
    progressColor: Color,
    head: Color,
): Modifier =
    drawWithCache {
        val line = 1.dp.toPx()
        val thick = 2.dp.toPx()
        val inset = 1.dp.toPx()
        val rect = Rect(inset, inset, size.width - inset, size.height - inset)
        val r = radius.toPx().coerceIn(0f, min(rect.width, rect.height) / 2f)
        val frame =
            Path().apply {
                moveTo(rect.left, rect.top + r)
                if (r > 0f) arcTo(Rect(rect.left, rect.top, rect.left + 2 * r, rect.top + 2 * r), 180f, 90f, false)
                lineTo(rect.right - r, rect.top)
                if (r > 0f) arcTo(Rect(rect.right - 2 * r, rect.top, rect.right, rect.top + 2 * r), -90f, 90f, false)
                lineTo(rect.right, rect.bottom - r)
                if (r > 0f) arcTo(Rect(rect.right - 2 * r, rect.bottom - 2 * r, rect.right, rect.bottom), 0f, 90f, false)
                lineTo(rect.left + r, rect.bottom)
                if (r > 0f) arcTo(Rect(rect.left, rect.bottom - 2 * r, rect.left + 2 * r, rect.bottom), 90f, 90f, false)
                lineTo(rect.left, rect.top + r)
            }
        val measure = PathMeasure().also { it.setPath(frame, false) }
        val length = measure.length
        val part = Path()
        val tip = 16.dp.toPx()
        onDrawWithContent {
            drawContent()
            drawPath(frame, border, style = Stroke(line))
            val p = progress()
            if (p > 0f && length > 0f) {
                val end = length * p
                part.rewind()
                measure.getSegment(0f, end, part, true)
                drawPath(part, progressColor, style = Stroke(thick, cap = StrokeCap.Butt))
                part.rewind()
                measure.getSegment(max(0f, end - tip), end, part, true)
                drawPath(part, head, style = Stroke(thick, cap = StrokeCap.Round))
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
    indicator: com.texfi.w0y.data.DockIndicator = com.texfi.w0y.data.DockIndicator.LINE,
    compact: Boolean = false,
) {
    val colors = LocalW0yColors.current
    val itemHeight = if (compact) NAV_ITEM_HEIGHT_COMPACT else NAV_ITEM_HEIGHT
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

    // Кольцо от выбранной вкладки: тонкая окружность расходится от значка и тает.
    val ring = remember { Animatable(1f) }
    var ringAt by remember { androidx.compose.runtime.mutableIntStateOf(selected) }
    var firstSelect by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (firstSelect) {
            firstSelect = false
            return@LaunchedEffect
        }
        if (!look.motion.draws) return@LaunchedEffect
        ringAt = selected
        ring.snapTo(0f)
        ring.animateTo(1f, tween(520, easing = HairTokens.EaseOut))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = if (compact) 3.dp else 6.dp)
            .height(itemHeight)
            .drawBehind {
                val slot = size.width / items.size
                val lo = min(head.value, tail.value)
                val hi = max(head.value, tail.value)
                when (indicator) {
                    com.texfi.w0y.data.DockIndicator.LINE -> {
                        val w = (slot * 0.5f).coerceIn(36.dp.toPx(), 64.dp.toPx())
                        val y = size.height / 2f + (if (compact) 12.dp else 14.dp).toPx()
                        // Как на сайте: тонкая линия под активным пунктом, перетекает между ними.
                        drawLine(lensTone, Offset(lo * slot + (slot - w) / 2f, y), Offset(hi * slot + (slot + w) / 2f, y), 2.dp.toPx(), StrokeCap.Round)
                    }
                    com.texfi.w0y.data.DockIndicator.DOT -> {
                        // Точка под значком; на ходу вытягивается в короткую черту и снова собирается.
                        val y = size.height / 2f + (if (compact) 13.dp else 16.dp).toPx()
                        drawLine(lensTone, Offset(lo * slot + slot / 2f, y), Offset(hi * slot + slot / 2f, y), 5.dp.toPx(), StrokeCap.Round)
                    }
                    com.texfi.w0y.data.DockIndicator.FRAME -> {
                        // Тонкая рамка вокруг вкладки и лёгкая подсветка внутри; на ходу тянется каплей.
                        val inset = 4.dp.toPx()
                        val r = (size.height / 2f - inset).coerceAtMost(16.dp.toPx())
                        val rect = Rect(lo * slot + inset, inset, (hi + 1f) * slot - inset, size.height - inset)
                        drawRoundRect(lensTone.copy(alpha = 0.08f), rect.topLeft, rect.size, CornerRadius(r))
                        drawRoundRect(lensTone.copy(alpha = 0.7f), rect.topLeft, rect.size, CornerRadius(r), style = Stroke(1.dp.toPx()))
                    }
                }
                val rp = ring.value
                if (rp < 1f) {
                    val c = Offset(ringAt * slot + slot / 2f, size.height / 2f)
                    drawCircle(lensTone.copy(alpha = 0.55f * (1f - rp)), 10.dp.toPx() + 22.dp.toPx() * rp, c, style = Stroke(1.dp.toPx()))
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
                        .height(itemHeight)
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
private val NAV_ITEM_HEIGHT_COMPACT = 40.dp
private val LENS_MAX = 104.dp
private val LENS_RADIUS = 10.dp
private const val PROGRESS_POLL_MS = 400L
private const val PAUSED_POLL_MS = 1_000L
private const val GLOW_ALPHA = 0.26f
private val GLOW_REACH = 100.dp
