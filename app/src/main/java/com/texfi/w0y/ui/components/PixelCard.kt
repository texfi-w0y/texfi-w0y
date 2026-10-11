package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.isSmooth
import com.texfi.w0y.ui.theme.liquidGlass
import com.texfi.w0y.ui.theme.styleTokens

private val CardShape = RoundedCornerShape(8.dp)

/**
 * Карточка TexFi: бордер 2dp, тень — сдвинутый прямоугольник без блюра.
 * Material elevation с размытием ломает резкость пиксельной графики, поэтому
 * тень здесь рисуется вторым слоем, а не эффектом.
 */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    label: String? = null,
    shadowOffset: Int = 4,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalW0yColors.current
    if (com.texfi.w0y.ui.theme.isHair) {
        HairCard(modifier, label, content)
        return
    }
    if (colors.glass) {
        GlassCard(modifier, label, content)
        return
    }
    if (isSmooth) {
        SmoothCard(modifier, label, content)
        return
    }
    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(shadowOffset.dp, shadowOffset.dp)
                .clip(CardShape)
                .background(colors.shadow),
        )
        Column(
            Modifier
                // Ширина как у тени: без этого тело карточки сжимается по
                // содержимому, а тень остаётся во всю ширину — и карточка
                // выглядит сломанной.
                .fillMaxWidth()
                .clip(CardShape)
                .background(colors.surface)
                .border(2.dp, colors.border, CardShape)
                .padding(14.dp),
        ) {
            if (label != null) {
                Text(
                    text = "❯ $label",
                    style = PixelSectionLabel,
                    color = colors.accentText,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            content()
        }
    }
}

private val GlassShape = RoundedCornerShape(20.dp)

/**
 * Карточка стеклянной темы: полупрозрачная заливка, чуть светлее сверху,
 * и рамка-блик — яркая по верхнему краю и почти пропадающая к нижнему.
 * Так читается толщина стекла, а офсетная тень здесь была бы чужой.
 */
@Composable
private fun GlassCard(
    modifier: Modifier,
    label: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalW0yColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(GlassShape)
            .background(Brush.verticalGradient(listOf(colors.surfaceHigh, colors.surface)))
            .border(1.dp, Brush.verticalGradient(listOf(GlassEdge, GlassEdgeFaint)), GlassShape)
            .padding(16.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = PixelSectionLabel,
                color = colors.accent,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        content()
    }
}

/** Карточка плавного стиля: крупное скругление, без рамки и тени — разделяет тон. */
@Composable
private fun SmoothCard(
    modifier: Modifier,
    label: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalW0yColors.current
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(styleTokens.card)
            .padding(16.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(com.texfi.w0y.ui.theme.LocalPanelBackdrop provides null) {
        if (label != null) {
            Text(
                text = label,
                style = PixelSectionLabel,
                color = colors.accentText,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        content()
        }
    }
}

internal val GlassEdge = Color(0x66FFFFFF)
internal val GlassEdgeFaint = Color(0x14FFFFFF)
