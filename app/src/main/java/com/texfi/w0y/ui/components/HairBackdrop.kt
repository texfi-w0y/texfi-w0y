package com.texfi.w0y.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.drawBackdrop

/**
 * Фон Hairline: не пустая заливка, а «чертёжный лист». Холодный отсвет
 * сверху, едва заметная сетка 48 dp (как у PhotoCraft) и тёплое пятно цвета
 * играющей обложки в верхнем углу. Всё — градиенты и одна линия-путь: ни
 * размытия, ни растра, а анимируемые значения читаются только при
 * рисовании.
 */
@Composable
fun HairBackdrop(coverUrl: String?, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val spec = com.texfi.w0y.ui.theme.LocalBackdrop.current
    Box(modifier.drawBehind { drawBackdrop(spec, colors, 0f) })
}

private val GRID = 48.dp
