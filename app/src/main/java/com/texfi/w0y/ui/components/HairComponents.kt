package com.texfi.w0y.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import com.texfi.w0y.ui.theme.styleTokens
import kotlin.math.roundToInt

/**
 * Примитивы Hairline: контур 1 dp вместо заливки и тени. Каждый — тонкая
 * подстановка в общий компонент приложения (`PixelCard`, `PixelButton`, …),
 * поэтому экраны не знают, какой стиль включён.
 */

/**
 * Поверхность с глубиной без тени: градиент от чуть более светлой кромки
 * к основному тону. Плоская заливка выглядела пустой; здесь тот же 1 dp
 * контур, но карточка «лежит» над фоном.
 */
@Composable
internal fun Modifier.hairSurface(shape: androidx.compose.ui.graphics.Shape, fill: Color = LocalW0yColors.current.surface): Modifier =
    this.background(
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(lerp(fill, Color.White, 0.055f), fill)),
        shape,
    )

/** Карточка: поверхность и контур, который «рисуется» при появлении. Тени нет. */
@Composable
internal fun HairCard(modifier: Modifier, label: String?, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val shape = styleTokens.card
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .hairSurface(shape)
            .hairOutline(shape, colors.outline)
            .padding(if (look.compact) 12.dp else 16.dp),
    ) {
        if (label != null) {
            Text(
                text = label.asLabel(),
                style = PixelSectionLabel,
                color = colors.accentText,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        content()
    }
}

/**
 * Кнопка-контур. Главное действие — контур и подпись цвета акцента, остальные
 * — контур `line-strong`. При нажатии кнопка вдавливается (0.96) и на мгновение
 * заполняется тоном; ни тени, ни сплошной заливки.
 */
@Composable
internal fun HairButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    body: Color,
    enabled: Boolean,
    interaction: MutableInteractionSource,
    pressed: Boolean,
    tap: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val shape = styleTokens.button
    // Как на сайте: главная кнопка — плоская светлая заливка с тёмной подписью,
    // остальные — прозрачные с тонкой рамкой.
    val primary = body == colors.accent || body == colors.secondary
    val press by animateFloatAsState(if (pressed) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "hairBtn")
    val fillColor = if (primary && enabled) colors.text.copy(alpha = 1f - 0.18f * press) else colors.text.copy(alpha = 0.08f * press)
    val labelColor =
        when {
            !enabled -> colors.textMuted
            primary -> colors.background
            else -> colors.text
        }
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(fillColor)
            .then(if (primary && enabled) Modifier else Modifier.border(hairLook.line, if (enabled) colors.outline else colors.border, shape))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button) {
                tap()
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.asLabel(),
            style = PixelSectionLabel,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Переключатель: пилюля-контур и круглый бегунок, который перетекает на 120 мс. */
@Composable
internal fun HairSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, haptic: (Buzz) -> Unit) {
    val colors = LocalW0yColors.current
    val on by animateFloatAsState(if (checked) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "hairSwitch")
    val tone = Contrast.ensure(colors.accent, colors.background)
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(shape)
            .background(tone.copy(alpha = 0.16f * on))
            .border(hairLook.line, lerp(colors.outline, tone, on), shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Switch) {
                haptic(if (checked) Buzz.OFF else Buzz.ON)
                onCheckedChange(!checked)
            }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((18.dp.toPx() * on).roundToInt(), 0) }
                .size(18.dp)
                .clip(CircleShape)
                .background(lerp(colors.textMuted, tone, on)),
        )
    }
}

/** Ползунок: тонкая линия, пройденная часть акцентом, бегунок-кольцо. */
@Composable
internal fun HairSliderVisual(fraction: Float, widthPx: Int, thumbPx: Float, dragging: Boolean, color: Color) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val grow by animateFloatAsState(if (dragging) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "hairThumb")
    Canvas(Modifier.fillMaxSize()) {
        val w = look.line.toPx()
        val y = size.height / 2f
        val tone = Contrast.ensure(color, colors.background)
        drawLine(colors.outline, Offset(0f, y), Offset(size.width, y), w, StrokeCap.Round)
        val x = thumbPx / 2f + (size.width - thumbPx) * fraction
        if (fraction > 0f) drawLine(tone, Offset(0f, y), Offset(x, y), w, StrokeCap.Round)
        val r = thumbPx / 2f * (1f + 0.25f * grow) - w / 2f
        drawCircle(colors.background, r, Offset(x, y))
        drawCircle(tone, r, Offset(x, y), style = Stroke(w))
    }
}

/**
 * Переключатель разделов: подписи моно заглавными и тонкая линия под
 * активной, которая скользит между пунктами. Контура вокруг всего блока нет.
 */
@Composable
internal fun HairSegmented(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val tap = rememberTapHaptic()
    val position = remember { Animatable(selectedIndex.toFloat()) }
    val spec = hairSpec<Float>(HairTokens.MID_MS)
    LaunchedEffect(selectedIndex) { position.animateTo(selectedIndex.toFloat(), spec) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val slot = maxWidth / options.size.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val active = index == selectedIndex
                val label by animateColorAsState(
                    if (active) colors.text else colors.textMuted,
                    hairSpec(HairTokens.SHORT_MS),
                    label = "hairSeg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = HairTokens.TOUCH)
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab) {
                            tap()
                            onSelect(index)
                        }
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.asLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // Дорожка и бегущая линия: дорожка — разделитель, линия — акцент.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(look.line).background(colors.border))
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset { androidx.compose.ui.unit.IntOffset((slot.toPx() * position.value).roundToInt(), 0) }
                .width(slot)
                .height(maxOf(2.dp, look.line * 2))
                .padding(horizontal = 8.dp)
                .background(Contrast.ensure(colors.accent, colors.background)),
        )
    }
}

/** Линия прогресса: тонкая дорожка и синяя пройденная часть. Прогресс читается при рисовании. */
@Composable
internal fun HairProgressLine(progress: () -> Float, modifier: Modifier, height: androidx.compose.ui.unit.Dp) {
    val colors = LocalW0yColors.current
    Canvas(modifier.height(maxOf(height, hairLook.line))) {
        val w = size.height
        val y = size.height / 2f
        drawLine(colors.border, Offset(w / 2f, y), Offset(size.width - w / 2f, y), w, StrokeCap.Round)
        val p = progress().coerceIn(0f, 1f)
        if (p > 0f) drawLine(colors.progress, Offset(w / 2f, y), Offset(w / 2f + (size.width - w) * p, y), w, StrokeCap.Round)
    }
}

/**
 * Плитка быстрого набора в Hairline: контур, обложка по форме из настроек,
 * подпись под ней. Нажатие — контур становится акцентным, плитка чуть
 * вжимается; закреплённая помечена значком кнопки.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HairDialTile(
    title: String,
    thumbnailUrl: String?,
    modifier: Modifier,
    subtitle: String?,
    pinned: Boolean,
    kindSprite: List<String>?,
    round: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val shape = styleTokens.card
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "hairTile")
    val tone = Contrast.ensure(colors.accent, colors.background)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val k = 1f - 0.03f * press
                scaleX = k
                scaleY = k
            }
            // Без коробки вокруг: обложка и подпись, как в галерее. Рамка — у самой обложки.
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            CoverImage(
                url = thumbnailUrl,
                px = Thumbnails.TILE,
                modifier = Modifier.fillMaxSize(),
                circle = round,
            )
            if (pinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.background.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) { PixelSprite(rows = Sprites.pin, color = tone, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (kindSprite != null) {
                PixelSprite(rows = kindSprite, color = tone, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


/** Короткое сообщение с действием: «ОСТАНОВЛЕНО · ОТМЕНИТЬ». Контур, без заливки и тени. */
@Composable
fun HairSnack(message: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val shape = RoundedCornerShape(50)
    val tone = Contrast.ensure(colors.accent, colors.surface)
    Row(
        modifier
            .clip(shape)
            .background(colors.surface)
            .border(look.line, colors.outline, shape)
            .padding(start = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message.asLabel(), style = PixelSectionLabel, color = colors.textMuted)
        Text(" · ", style = PixelSectionLabel, color = colors.textMuted)
        Box(
            Modifier
                .heightIn(min = HairTokens.TOUCH)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAction)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(action.asLabel(), style = PixelSectionLabel, color = tone) }
    }
}
