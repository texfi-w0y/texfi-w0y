package com.texfi.w0y.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private const val PRESS_MS = 70

/**
 * Отклик на нажатие: элемент вдавливается и возвращается пружиной.
 *
 * У каждого действия свой отклик, а не общая «рябь» поверх всего: нажатие
 * на плитку, на иконку и на кнопку ощущаются по-разному, и по этому
 * ощущению понятно, что именно нажалось.
 */
@Composable
fun Modifier.pressScale(
    interaction: InteractionSource,
    pressed: Float = 0.92f,
): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    // Hairline: отпущенное возвращается пружиной с коротким перелётом — как оторвалось от магнита.
    val springBack = com.texfi.w0y.ui.theme.isHair && com.texfi.w0y.ui.theme.hairLook.motion.moves
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = if (!isPressed && springBack) spring(dampingRatio = 0.45f, stiffness = 900f) else tween(PRESS_MS, easing = W0yMotion.Press),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Толчок в момент включения: лайк, закрепление, повтор.
 *
 * Именно в момент включения, а не выключения — снятие лайка не событие,
 * а отмена, и праздновать там нечего.
 */
@Composable
fun Modifier.popWhenActivated(active: Boolean, peak: Float = 1.35f): Modifier {
    val scale = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active && !seen) {
            scale.animateTo(peak, tween(90, easing = W0yMotion.Press))
            scale.animateTo(1f, tween(W0yMotion.MID_MS, easing = W0yMotion.StepBack))
        }
        seen = active
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
