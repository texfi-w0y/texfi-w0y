package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.luminance
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.isSmooth
import com.texfi.w0y.ui.theme.liquidGlass
import com.texfi.w0y.ui.theme.styleTokens

private val ButtonShape = RoundedCornerShape(8.dp)

/**
 * Кнопка вдавливается при нажатии: тень уезжает внутрь, а содержимое
 * сдвигается на её место. Без этого отклика пиксельная кнопка выглядит
 * мёртвой картинкой — проверено на f0kus.
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    enabled: Boolean = true,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tap = rememberTapHaptic()
    // Тень лежит на +4dp; при нажатии содержимое съезжает ровно на неё.
    val sink = if (pressed) 4 else 0
    val body = fill ?: colors.accent

    if (com.texfi.w0y.ui.theme.isHair) {
        HairButton(text, onClick, modifier, body, enabled, interaction, pressed, tap)
        return
    }
    if (colors.glass) {
        GlassButton(text, onClick, modifier, body, enabled, interaction, pressed, tap)
        return
    }
    if (isSmooth) {
        SmoothButton(text, onClick, modifier, body, enabled, interaction, pressed, tap)
        return
    }
    // Минимальный размер снаружи передаётся телу кнопки: иначе при заданной
    // ширине тень растягивалась, а кнопка оставалась по тексту.
    Box(modifier = modifier, propagateMinConstraints = true) {
        Box(
            Modifier
                .matchParentSize()
                .offset(4.dp, 4.dp)
                .clip(ButtonShape)
                // Песочная заливка тенью уходит в тёмный песочный, а не в синий.
                .background(
                    when {
                        !enabled -> colors.shadow
                        body == colors.secondary -> colors.secondaryDeep
                        else -> colors.accentDeep
                    },
                ),
        )
        Box(
            Modifier
                .offset(sink.dp, sink.dp)
                .clip(ButtonShape)
                .background(if (enabled) body else colors.surfaceHigh)
                .border(2.dp, colors.border, ButtonShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) {
                    tap()
                    onClick()
                }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = PixelSectionLabel,
                // Цвет подписи считается от заливки, а не задан раз и навсегда:
                // на синей кнопке нужен тёмный текст, а на тёмной — светлый.
                // Раньше он всегда был тёмным, и «К АРТИСТУ» на сером фоне
                // просто не читалось.
                color = if (enabled) Contrast.on(body, dark = colors.background.takeIf { it.luminance() < 0.2f } ?: Color(0xFF111116)) else colors.textMuted,
            )
        }
    }
}

/**
 * Кнопка стеклянной темы: капсула, при нажатии чуть уменьшается вместо
 * «вдавливания» в тень — тени у стекла нет. Акцентная кнопка остаётся
 * плотной: полупрозрачное главное действие теряется на фоне.
 */
@Composable
private fun GlassButton(
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
    val shape = RoundedCornerShape(50)
    val solid = body == colors.accent
    Box(
        modifier
            .graphicsLayer {
                val k = if (pressed) 0.96f else 1f
                scaleX = k
                scaleY = k
            }.clip(shape)
            .background(
                when {
                    !enabled -> colors.surface
                    solid -> body.copy(alpha = 0.85f)
                    else -> colors.surfaceHigh
                },
            ).border(1.dp, Brush.verticalGradient(listOf(GlassEdge, GlassEdgeFaint)), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                tap()
                onClick()
            }.padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = PixelSectionLabel,
            color = if (enabled) colors.text else colors.textMuted,
        )
    }
}

/** Кнопка плавного стиля: капсула с заливкой, нажатие — лёгкое уменьшение. */
@Composable
private fun SmoothButton(
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
    val tokens = styleTokens
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) tokens.pressScale else 1f,
        androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "smoothPress",
    )
    val fill = if (enabled) body else colors.surfaceHigh
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.then(
                // Главная кнопка плотная — полупрозрачное главное действие
                // теряется; остальные стеклянные.
                if (body == colors.accent || body == colors.secondary || !enabled) {
                    Modifier.clip(tokens.button).background(fill)
                } else {
                    Modifier.liquidGlass(tokens.button, fill)
                },
            ).clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                tap()
                onClick()
            }.padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = PixelSectionLabel,
            color = if (enabled) Contrast.on(fill) else colors.textMuted,
        )
    }
}

