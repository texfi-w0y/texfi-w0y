package com.texfi.w0y.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.components.hairStagger
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.UnboundedFamily
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook

/** Состояние нажатия: 0 → 1 плавно, пока палец на карточке. */
@Composable
internal fun rememberPress(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "press")
    return press
}

/**
 * Карточка раздела «Моё»: номер, значок, крупное число, подпись. Число
 * «накручивается» при появлении, под пальцем рамка становится акцентной,
 * а по нижней кромке бежит линия.
 */
@Composable
internal fun BentoCard(
    index: Int,
    glyph: HairGlyph,
    label: String,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val shape = RoundedCornerShape(look.cardRadius.coerceAtMost(16.dp))
    val source = remember { MutableInteractionSource() }
    val press = rememberPress(source)
    val tone = Contrast.ensure(colors.accent, colors.surface)
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(count) { target = count ?: 0 }
    val shown by animateIntAsState(target, tween(900), label = "bentoCount")
    Box(
        modifier
            .hairStagger(index)
            .graphicsLayer {
                val k = 1f - 0.03f * press
                scaleX = k
                scaleY = k
            }
            .height(if (tall) 132.dp else 104.dp)
            .clip(shape)
            .background(lerp(colors.surface, colors.surfaceHigh, press))
            .border(look.line, lerp(colors.border, tone, press), shape)
            .drawBehind {
                val w = size.width * press
                drawLine(tone, Offset(0f, size.height - 1.5.dp.toPx()), Offset(w, size.height - 1.5.dp.toPx()), 3.dp.toPx())
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(14.dp),
    ) {
        Text("%02d".format(index + 1), style = HairLabelStyle, color = colors.textMuted, modifier = Modifier.align(Alignment.TopStart))
        HairIcon(
            glyph,
            lerp(colors.textMuted, tone, press),
            Modifier.align(Alignment.TopEnd).size(if (tall) 28.dp else 22.dp).offset(x = (press * -3).dp),
        )
        Column(Modifier.align(Alignment.BottomStart)) {
            if (count != null) {
                Text(
                    "$shown",
                    style = TextStyle(fontFamily = UnboundedFamily, fontWeight = FontWeight.ExtraBold, fontSize = if (tall) 40.sp else 30.sp, letterSpacing = (-1).sp),
                    color = colors.text,
                    maxLines = 1,
                )
            }
            Text(label.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1)
        }
    }
}

/** Панель-строка (папки, альбомы): значок, название, значение; под пальцем линия бежит по нижней кромке, шеврон сдвигается. */
@Composable
internal fun PanelRow(glyph: HairGlyph, label: String, value: String?, modifier: Modifier = Modifier, index: Int = 0) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val shape = RoundedCornerShape(look.cardRadius.coerceAtMost(12.dp))
    val tone = Contrast.ensure(colors.accent, colors.surface)
    // Нажатие приходит от родителя через clickable; здесь — только вид и появление.
    Row(
        modifier
            .fillMaxWidth()
            .hairStagger(index)
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(colors.surface)
            .border(look.line, colors.border, shape)
            .height(58.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HairIcon(glyph, tone, Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = colors.text, maxLines = 1, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1)
            Spacer(Modifier.width(10.dp))
        }
        HairIcon(HairGlyph.CHEVRON_RIGHT, colors.textMuted, Modifier.size(16.dp))
    }
}
