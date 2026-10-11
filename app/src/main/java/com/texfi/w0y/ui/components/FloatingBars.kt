package com.texfi.w0y.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.isSmooth
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.liquidGlass
import com.texfi.w0y.ui.theme.LocalBarBackdrop
import com.texfi.w0y.ui.theme.glassBar
import com.texfi.w0y.ui.theme.glassSelection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import com.texfi.w0y.ui.theme.styleTokens
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/** Пункт нижней навигации. */
data class NavItem(val label: String, val sprite: List<String>)

/**
 * Плавающая подложка нижних панелей — навигации и мини-плеера.
 *
 * Pixel: квадратный блок с рамкой и жёсткой смещённой тенью без размытия.
 * Smooth: капсула с мягкой тенью. Панели стоят над контентом в общей
 * колонке, а не поверх него: список кончается над ними, и ничего не
 * перекрывается при любой высоте панелей.
 */
@Composable
fun FloatingSurface(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalW0yColors.current
    val tokens = styleTokens
    val backdrop = LocalBarBackdrop.current
    if (com.texfi.w0y.ui.theme.isHair) {
        Box(modifier.background(colors.surface), content = content)
        return
    }
    if (backdrop != null && tokens.glass) {
        Box(modifier.glassBar(shape ?: GlassCapsule, backdrop), content = content)
        return
    }
    if (isSmooth || colors.glass) {
        Box(
            modifier
                .shadow(14.dp, tokens.bar, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
                .liquidGlass(tokens.bar, colors.surfaceHigh),
            content = content,
        )
        return
    }
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(FLOAT_SHADOW, FLOAT_SHADOW)
                .background(colors.shadow),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceHigh)
                .border(2.dp, colors.border),
            content = content,
        )
    }
}

/**
 * Нижняя навигация: один компонент, два вида.
 *
 * Выбранный пункт подсвечен блоком, который переезжает под новый пункт —
 * в Pixel ступенями (5 шагов за 200 мс), в Smooth плавно. Каждый пункт не
 * меньше 48 dp по высоте и по ширине.
 *
 * По панели можно вести пальцем: вкладка под пальцем открывается сразу,
 * без отдельного нажатия на каждую, с лёгким откликом на каждой смене.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    standalone: Int? = null,
) {
    if (com.texfi.w0y.ui.theme.isHair) {
        HairNavBar(items, selected, onSelect, modifier)
        return
    }
    if (standalone != null) {
        SplitNavBar(items, selected, onSelect, modifier, standalone)
        return
    }
    val colors = LocalW0yColors.current
    val smooth = isSmooth
    val tokens = styleTokens
    val position = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected, smooth) {
        position.animateTo(
            selected.toFloat(),
            if (smooth) spring(dampingRatio = 0.8f, stiffness = 500f) else tween(NAV_MS, easing = SteppedEasing(NAV_STEPS)),
        )
    }
    val tick = rememberTapHaptic()
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    FloatingSurface(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(NAV_INSET)
                .pointerInput(items.size) {
                    fun slotAt(x: Float) = (x / (size.width.toFloat() / items.size)).toInt().coerceIn(0, items.lastIndex)
                    // Последняя вкладка этого жеста: выбор доходит до экрана не
                    // мгновенно, и без неё быстрый палец выбрал бы одну вкладку дважды.
                    var last = -1
                    fun moveTo(x: Float) {
                        val index = slotAt(x)
                        if (index != last) {
                            last = index
                            tick()
                            select(index)
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            last = currentSelected
                            moveTo(it.x)
                        },
                    ) { change, _ ->
                        change.consume()
                        moveTo(change.position.x)
                    }
                },
        ) {
            val slot = maxWidth / items.size
            // Блок выбранного пункта: рисуется под иконками, сдвиг читается в слое раскладки.
            Box(
                Modifier
                    .offset { IntOffset((slot.toPx() * position.value).roundToInt(), 0) }
                    .width(slot)
                    .height(NAV_HEIGHT)
                    .clip(if (smooth) tokens.bar else tokens.chip)
                    .background(colors.accent),
            )
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    val active = index == selected
                    val interaction = remember { MutableInteractionSource() }
                    val tint by animateColorAsState(
                        targetValue = if (active) colors.onAccent else colors.textMuted,
                        animationSpec = if (smooth) tween(180) else snap(),
                        label = "navTint",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(NAV_HEIGHT)
                                .pressScale(interaction, pressed = 0.9f)
                                .clickable(interactionSource = interaction, indication = null) { onSelect(index) }
                                .padding(vertical = 6.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        PixelSprite(
                            rows = item.sprite,
                            color = tint,
                            modifier = Modifier.size(22.dp).popWhenActivated(active, peak = 1.18f),
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Навигация, разделённая на два блока: вкладки вместе, поиск — отдельно
 * справа.
 *
 * Smooth (как в iOS 26): капсула и круг, выбранная вкладка — светлая
 * капсула, переезжает плавно. Pixel (TexFi): два квадратных блока с рамкой
 * и жёсткой тенью, выбранная вкладка — блок акцента, переезжает ступенями.
 * По блоку вкладок можно вести пальцем.
 */
@Composable
private fun SplitNavBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    standalone: Int?,
) {
    val colors = LocalW0yColors.current
    val smooth = isSmooth
    val tokens = styleTokens
    val inCapsule = items.indices.filter { it != standalone }
    val capsuleIndex = inCapsule.indexOf(selected)
    val position = remember { Animatable(capsuleIndex.coerceAtLeast(0).toFloat()) }
    LaunchedEffect(capsuleIndex, smooth) {
        if (capsuleIndex >= 0) {
            position.animateTo(
                capsuleIndex.toFloat(),
                if (smooth) spring(dampingRatio = 0.8f, stiffness = 500f) else tween(NAV_MS, easing = SteppedEasing(NAV_STEPS)),
            )
        }
    }
    val pillAlpha by animateFloatAsState(if (capsuleIndex >= 0) 1f else 0f, if (smooth) tween(180) else snap(), label = "pill")
    val tick = rememberTapHaptic()
    val select by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val pill = if (smooth) glassSelection else colors.accent
    val pillShape = if (smooth) GlassCapsule else tokens.chip
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FloatingSurface(Modifier.weight(1f)) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(NAV_INSET)
                    .pointerInput(inCapsule) {
                        fun slotAt(x: Float) = (x / (size.width.toFloat() / inCapsule.size)).toInt().coerceIn(0, inCapsule.lastIndex)
                        var last = -1
                        fun moveTo(x: Float) {
                            val index = inCapsule[slotAt(x)]
                            if (index != last) {
                                last = index
                                tick()
                                select(index)
                            }
                        }
                        detectHorizontalDragGestures(onDragStart = {
                            last = currentSelected
                            moveTo(it.x)
                        }) { change, _ ->
                            change.consume()
                            moveTo(change.position.x)
                        }
                    },
            ) {
                val slot = maxWidth / inCapsule.size
                Box(
                    Modifier
                        .offset { IntOffset((slot.toPx() * position.value).roundToInt(), 0) }
                        .width(slot)
                        .height(NAV_HEIGHT)
                        .graphicsLayer { alpha = pillAlpha }
                        .clip(pillShape)
                        .background(pill),
                )
                Row(Modifier.fillMaxWidth()) {
                    inCapsule.forEach { index ->
                        NavCell(items[index], active = index == selected, label = true, onClick = { onSelect(index) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (standalone != null) {
            val active = selected == standalone
            // Pixel: зазор шире — в нём жёсткая тень блока вкладок.
            Spacer(Modifier.width(if (smooth) 10.dp else 12.dp))
            FloatingSurface(Modifier.size(NAV_HEIGHT + NAV_INSET * 2), shape = CircleShape) {
                Box(
                    Modifier
                        .padding(NAV_INSET)
                        .size(NAV_HEIGHT)
                        .clip(if (smooth) CircleShape else tokens.chip)
                        .background(if (active) pill else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    NavCell(items[standalone], active = active, label = false, onClick = { onSelect(standalone) }, modifier = Modifier.size(NAV_HEIGHT))
                }
            }
        }
    }
}

/**
 * Пункт разделённой навигации: иконка и подпись. Выбранный в Smooth —
 * ярче, в Pixel — цвета поверх акцента, без плавной смены.
 */
@Composable
private fun NavCell(item: NavItem, active: Boolean, label: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalW0yColors.current
    val smooth = isSmooth
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (!active) colors.textMuted else if (smooth) colors.text else colors.onAccent,
        animationSpec = if (smooth) tween(180) else snap(),
        label = "splitNavTint",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        modifier =
            modifier
                .height(NAV_HEIGHT)
                .pressScale(interaction, pressed = 0.9f)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        PixelSprite(
            rows = item.sprite,
            color = tint,
            modifier = Modifier.size(if (label) 22.dp else 24.dp).popWhenActivated(active, peak = 1.18f),
        )
        if (label) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** Капсула стеклянных панелей: скругление на всю высоту. */
private val GlassCapsule = RoundedCornerShape(50)

/** Высота пункта навигации: 48 dp касания плюс подпись. */
val NAV_HEIGHT: Dp = 54.dp
private val NAV_INSET = 5.dp
private val FLOAT_SHADOW = 4.dp
private const val NAV_MS = 200
private const val NAV_STEPS = 5

/**
 * Сколько снизу экрана закрыто стеклянными панелями. Списки добавляют это
 * в отступ снизу: экран виден сквозь стекло, но последний трек всё равно
 * докручивается до места над панелями. Без стекла — ноль.
 */
val LocalBarsInset = androidx.compose.runtime.compositionLocalOf { 0.dp }


/**
 * Навигация Hairline: одна полоса во всю ширину, тонкая линия сверху.
 * Выбранная вкладка — яркая иконка и подпись, а под ней тонкая линия,
 * которая скользит между вкладками (по ней же можно вести пальцем).
 */
@Composable
internal fun HairNavBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val position = remember { Animatable(selected.toFloat()) }
    val spec = hairSpec<Float>(com.texfi.w0y.ui.theme.HairTokens.MID_MS)
    LaunchedEffect(selected) { position.animateTo(selected.toFloat(), spec) }
    val tick = rememberTapHaptic()
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val tone = com.texfi.w0y.ui.theme.Contrast.ensure(colors.accent, colors.background)
    Box(modifier.fillMaxWidth().background(colors.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
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
        ) {
            val slot = maxWidth / items.size
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    val active = index == selected
                    val interaction = remember { MutableInteractionSource() }
                    val tint by animateColorAsState(
                        targetValue = if (active) colors.text else colors.textMuted,
                        animationSpec = hairSpec(com.texfi.w0y.ui.theme.HairTokens.SHORT_MS),
                        label = "hairNavTint",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(NAV_HEIGHT + 8.dp)
                                .semantics {
                                    contentDescription = item.label
                                    role = androidx.compose.ui.semantics.Role.Tab
                                    this.selected = active
                                }
                                .pressScale(interaction, pressed = 0.94f)
                                .clickable(interactionSource = interaction, indication = null) { onSelect(index) },
                    ) {
                        PixelSprite(rows = item.sprite, color = tint, modifier = Modifier.size(22.dp))
                        Text(
                            text = item.label.asLabel(),
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            // Скользящая линия под активной вкладкой.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(((slot.toPx() * position.value) + (slot.toPx() - 28.dp.toPx()) / 2f).roundToInt(), 0) }
                    .width(28.dp)
                    .height(maxOf(2.dp, look.line * 2))
                    .background(tone),
            )
        }
    }
}
