package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlin.math.sin

/**
 * Скелетон строки трека: форма будущего списка вместо пустоты со спиннером.
 * Пульсация медленная — быстрая на пиксельной графике читается
 * как мигание ошибки.
 */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    if (com.texfi.w0y.ui.theme.isHair) {
        HairSkeletonRow(modifier)
        return
    }
    // Прозрачность меняется в лямбде graphicsLayer, а не в теле composable:
    // слой обновляется без рекомпозиции и без перерисовки самих прямоугольников.
    val phase = rememberAnimationPhase(PULSE_PERIOD_MS, PULSE_FPS)
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .graphicsLayer { alpha = 0.45f + 0.55f * (0.5f + 0.5f * sin(phase.floatValue)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.surfaceHigh),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.surface),
            )
        }
    }
}

/** Полный цикл пульсации. Медленный: быстрый читается как мигание ошибки. */
private const val PULSE_PERIOD_MS = 1_800

/** Скелетон живёт секунду-две, ему хватает двадцати кадров. */
private const val PULSE_FPS = 20


/**
 * Скелетон Hairline: те же формы, но контуром, а по ним бежит светлая линия —
 * вместо пульсации заливки. Линия двигается в слое рисования, раскладка не
 * пересчитывается; без движения — просто контуры.
 */
@Composable
private fun HairSkeletonRow(modifier: Modifier) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val phase = rememberAnimationPhase(SWEEP_PERIOD_MS, PULSE_FPS, enabled = look.motion.moves)
    val line = colors.border
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .drawWithContent {
                drawContent()
                if (look.motion.moves) {
                    // Светлая вертикальная линия бежит слева направо и обратно не возвращается.
                    val t = (phase.floatValue / (2f * Math.PI.toFloat())).let { it - kotlin.math.floor(it) }
                    val x = size.width * t
                    drawLine(colors.outline, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), look.line.toPx())
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .border(look.line, line, com.texfi.w0y.ui.theme.styleTokens.cover),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.7f).height(12.dp).border(look.line, line, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(10.dp).border(look.line, line, RoundedCornerShape(5.dp)))
        }
    }
}

private const val SWEEP_PERIOD_MS = 1_400
