package com.texfi.w0y.desktop

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Единственная кривая Hairline: быстрый старт, мягкая посадка (та же, что на телефоне). */
val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Анимации приложения. Все они спрашивают [Motion]: «Выкл.» — без движения, «Лёгкие» — только появление,
 * «Полные» — появление со сдвигом и лесенкой. Лесенка играет лишь в первые доли секунды после перехода на
 * экран, поэтому прокрутка списка потом не повторяет её.
 */
object Anim {
    /** Когда последний раз открывали экран, мс. */
    @Volatile
    var screenAt = 0L

    /** Только что открытый экран: время для лесенки. */
    val fresh: Boolean get() = System.currentTimeMillis() - screenAt < 900

    /** Переход между экранами: вперёд — въезд справа, назад — слева; плеер раскрывается из чуть меньшего размера. */
    fun screenTransition(scope: AnimatedContentTransitionScope<Screen>, forward: Boolean): ContentTransform {
        if (Motion.off) return EnterTransition.None togetherWith ExitTransition.None
        if (!Motion.full) return fadeIn(tween(Motion.ms(220))) togetherWith fadeOut(tween(Motion.ms(160)))
        val toPlayer = scope.targetState == Screen.Player || scope.targetState == Screen.Queue
        val fromPlayer = scope.initialState == Screen.Player || scope.initialState == Screen.Queue
        val enter: EnterTransition
        val exit: ExitTransition
        if (toPlayer && forward) {
            enter = fadeIn(tween(360, delayMillis = 60, easing = EaseOut)) + scaleIn(tween(420, easing = EaseOut), initialScale = 0.94f)
            exit = fadeOut(tween(200))
        } else if (fromPlayer && !forward) {
            enter = fadeIn(tween(300, delayMillis = 40, easing = EaseOut)) + scaleIn(tween(360, easing = EaseOut), initialScale = 1.04f)
            exit = fadeOut(tween(200)) + scaleOut(tween(260, easing = EaseOut), targetScale = 0.96f)
        } else {
            val dir = if (forward) 1 else -1
            enter = fadeIn(tween(300, delayMillis = 70, easing = EaseOut)) + slideInHorizontally(tween(380, easing = EaseOut)) { dir * 56 }
            exit = fadeOut(tween(170)) + slideOutHorizontally(tween(260, easing = EaseOut)) { -dir * 28 }
        }
        return enter togetherWith exit
    }

    /** Смена обложки на диске: новая въезжает справа, старая уходит влево. */
    fun coverTransition(): ContentTransform =
        when {
            Motion.off -> EnterTransition.None togetherWith ExitTransition.None
            Motion.full -> (fadeIn(tween(420, easing = EaseOut)) + slideInHorizontally(tween(480, easing = EaseOut)) { 40 }) togetherWith
                (fadeOut(tween(260)) + slideOutHorizontally(tween(360, easing = EaseOut)) { -40 })
            else -> fadeIn(tween(Motion.ms(300))) togetherWith fadeOut(tween(Motion.ms(200)))
        }
}

/**
 * Появление блока: прозрачность 0→1 и подъём на 14 dp, у соседей со сдвигом по номеру (до шести).
 * Играет один раз при появлении и только на только что открытом экране.
 */
fun Modifier.hairEnter(index: Int = 0): Modifier =
    composed {
        val play = remember { Motion.mode != MotionMode.OFF && Anim.fresh }
        val progress = remember { Animatable(if (play) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (play) {
                val delay = if (Motion.full) index.coerceIn(0, 6) * 45 else 0
                progress.animateTo(1f, tween(Motion.ms(520), delayMillis = delay, easing = EaseOut))
            }
        }
        graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * (if (Motion.full) 14.dp.toPx() else 0f)
        }
    }

/** Лёгкий подъём при наведении (1 → 1,03): плитки и карточки живее реагируют на мышь. */
fun Modifier.hoverLift(source: InteractionSource, amount: Float = 0.03f): Modifier =
    composed {
        val hovered by source.collectIsHoveredAsState()
        val k by animateFloatAsState(if (hovered && Motion.full) 1f + amount else 1f, tween(180, easing = EaseOut))
        graphicsLayer { scaleX = k; scaleY = k }
    }

/** Вдавливание при нажатии (1 → 0,92): кнопка «отвечает» на клик. */
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.92f): Modifier =
    composed {
        val down by source.collectIsPressedAsState()
        val k by animateFloatAsState(if (down && !Motion.off) pressed else 1f, tween(110, easing = EaseOut))
        graphicsLayer { scaleX = k; scaleY = k; transformOrigin = TransformOrigin.Center }
    }
