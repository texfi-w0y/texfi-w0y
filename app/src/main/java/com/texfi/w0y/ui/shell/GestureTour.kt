package com.texfi.w0y.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R
import com.texfi.w0y.ui.components.Buzz
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.rememberHaptics
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.GeologicaFamily
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Шаги знакомства с жестами. [INTRO] — «показать или пропустить». */
internal enum class TourStep { INTRO, MINI_UP, MINI_SIDE, LONG_PRESS, FLOW_PULL, PLAYER_DOWN }

/** Какой жест ждёт шаг: направление тяги или долгое нажатие. */
private enum class Gesture { UP, SIDE, HOLD, DOWN }

private val TourStep.gesture: Gesture
    get() =
        when (this) {
            TourStep.INTRO, TourStep.MINI_UP -> Gesture.UP
            TourStep.MINI_SIDE -> Gesture.SIDE
            TourStep.LONG_PRESS -> Gesture.HOLD
            TourStep.FLOW_PULL, TourStep.PLAYER_DOWN -> Gesture.DOWN
        }

/**
 * Знакомство с жестами — на главной, сразу после первого запуска (и один раз тем, кто обновился).
 * Сначала карточка: пропустить или показать. Дальше по жесту на шаг: на макете палец сам
 * показывает движение, а повторить его можно прямо на макете — получилось, галочка и следующий
 * шаг. «Дальше» и «пропустить» есть всегда: учить силой нельзя.
 */
@Composable
fun GestureTour(onDone: () -> Unit) {
    val colors = LocalW0yColors.current
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(TourStep.INTRO) }
    var solved by remember { mutableStateOf(false) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut)) }
    fun next() {
        val all = TourStep.entries
        solved = false
        if (step == all.last()) {
            haptic(Buzz.DONE)
            onDone()
        } else {
            step = all[step.ordinal + 1]
        }
    }
    BackHandler(onBack = onDone)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .background(colors.background.copy(alpha = 0.9f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut)) { it / 4 } + fadeIn(tween(HairTokens.LONG_MS)))
                    .togetherWith(slideOutHorizontally(tween(HairTokens.MID_MS, easing = HairTokens.EaseOut)) { -it / 5 } + fadeOut(tween(HairTokens.SHORT_MS)))
            },
            label = "tourStep",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                if (s != TourStep.INTRO) {
                    Text(
                        stringResource(R.string.tour_counter, s.ordinal, TourStep.entries.size - 1).asLabel(),
                        style = HairLabelStyle,
                        color = colors.textMuted,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (s == TourStep.INTRO) {
                    IntroMark()
                } else {
                    TourStage(
                        step = s,
                        solved = solved,
                        onSolved = {
                            if (!solved) {
                                solved = true
                                haptic(Buzz.DONE)
                                scope.launch {
                                    delay(SOLVED_PAUSE_MS)
                                    if (step == s) next()
                                }
                            }
                        },
                        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().height(300.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(s.title()),
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.6).sp),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(s.text()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                    PixelButton(stringResource(R.string.tour_skip), onClick = onDone, fill = colors.surface, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    PixelButton(
                        stringResource(if (s == TourStep.INTRO) R.string.tour_show else if (s == TourStep.entries.last()) R.string.tour_finish else R.string.tour_next),
                        onClick = { haptic(Buzz.TAP); next() },
                        fill = colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Знак первой карточки: пять коротких дуг-жестов вокруг точки, медленно вращаются. */
@Composable
private fun IntroMark() {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val loop = rememberInfiniteTransition(label = "tourIntro")
    val turn by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "tourTurn")
    val moves = hairLook.motion.moves
    Canvas(Modifier.size(180.dp)) {
        val r = size.minDimension / 2f - 4.dp.toPx()
        for (i in 0 until 5) {
            val start = (if (moves) turn else 0f) + i * 72f
            drawArc(colors.text.copy(alpha = 0.5f), start, 40f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(1.dp.toPx()))
        }
        drawCircle(tone, 6.dp.toPx())
        drawCircle(colors.text.copy(alpha = 0.3f), 22.dp.toPx(), style = Stroke(1.dp.toPx()))
    }
}

/**
 * Макет шага. Пока его не трогают, палец сам показывает жест по кругу; коснулись — макет
 * идёт за своим пальцем, и жест, доведённый до конца, засчитывается.
 */
@Composable
private fun TourStage(step: TourStep, solved: Boolean, onSolved: () -> Unit, modifier: Modifier) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val loop = rememberInfiniteTransition(label = "tourDemo")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(DEMO_MS, easing = LinearEasing)), label = "tourDemoT")
    var touching by remember { mutableStateOf(false) }
    var userP by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val done by animateFloatAsState(if (solved) 1f else 0f, spring(0.6f, 300f), label = "tourDone")
    val gesture = step.gesture
    val reach = 110f

    // Ход демонстрации: появление, движение, задержка, исчезновение.
    fun demoP(): Float {
        val m = ((t - 0.15f) / 0.5f).coerceIn(0f, 1f)
        val eased = HairTokens.EaseOut.transform(m)
        return if (t > 0.85f) eased * (1f - (t - 0.85f) / 0.15f) else eased
    }
    val fingerAlpha = when {
        touching || solved -> 0f
        t < 0.12f -> t / 0.12f
        t > 0.85f -> 1f - (t - 0.85f) / 0.15f
        else -> 1f
    }

    Box(
        modifier
            .pointerInput(step) {
                if (gesture == Gesture.HOLD) {
                    detectTapGestures(
                        onPress = {
                            touching = true
                            scope.launch { settle.snapTo(0f); settle.animateTo(1f, tween(HOLD_MS, easing = LinearEasing)) }
                            val released = tryAwaitRelease()
                            touching = false
                            if (!released || settle.value < 1f) scope.launch { settle.animateTo(0f, tween(HairTokens.SHORT_MS)) }
                        },
                        onLongPress = { onSolved() },
                    )
                } else {
                    detectDragGestures(
                        onDragStart = { touching = true; userP = 0f },
                        onDragEnd = {
                            touching = false
                            if (userP >= 1f) onSolved()
                            val from = userP
                            scope.launch {
                                settle.snapTo(from)
                                if (!solved) settle.animateTo(0f, spring(0.7f, 400f))
                            }
                            userP = 0f
                        },
                        onDragCancel = { touching = false; userP = 0f },
                    ) { change, drag ->
                        change.consume()
                        val d =
                            when (gesture) {
                                Gesture.UP -> -drag.y
                                Gesture.DOWN -> drag.y
                                Gesture.SIDE -> abs(drag.x)
                                Gesture.HOLD -> 0f
                            }
                        userP = (userP + d / (reach * density)).coerceIn(0f, 1.15f)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p =
                when {
                    solved -> 1f
                    touching && gesture != Gesture.HOLD -> userP.coerceAtMost(1f)
                    gesture == Gesture.HOLD && (touching || settle.value > 0f) -> settle.value
                    settle.value > 0f -> settle.value
                    else -> demoP()
                }
            val line = colors.text.copy(alpha = 0.4f)
            val fill = colors.text.copy(alpha = 0.06f)
            val stroke = Stroke(1.dp.toPx())
            val finger =
                when (step) {
                    TourStep.MINI_UP, TourStep.INTRO -> drawMiniUp(p, line, fill, tone, stroke)
                    TourStep.MINI_SIDE -> drawMiniSide(p, line, fill, tone, stroke)
                    TourStep.LONG_PRESS -> drawHold(p, line, fill, tone, stroke)
                    TourStep.FLOW_PULL -> drawFlowPull(p, line, fill, tone, stroke)
                    TourStep.PLAYER_DOWN -> drawPlayerDown(p, line, fill, tone, stroke)
                }
            if (fingerAlpha > 0f) {
                // Палец: светлый кружок с кольцом; при долгом нажатии кольцо расходится.
                drawCircle(colors.text.copy(alpha = 0.85f * fingerAlpha), 13.dp.toPx(), finger)
                drawCircle(colors.text.copy(alpha = 0.3f * fingerAlpha), 22.dp.toPx() + if (gesture == Gesture.HOLD) 10.dp.toPx() * p else 0f, finger, style = stroke)
            }
            if (done > 0f) {
                // Получилось: галочка в кружке поверх макета.
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(colors.background.copy(alpha = 0.9f * done), 34.dp.toPx() * done, c)
                drawCircle(tone, 34.dp.toPx() * done, c, style = Stroke(1.5.dp.toPx()))
                val k = 12.dp.toPx() * done
                drawLine(tone, Offset(c.x - k, c.y), Offset(c.x - k * 0.25f, c.y + k * 0.7f), 2.dp.toPx())
                drawLine(tone, Offset(c.x - k * 0.25f, c.y + k * 0.7f), Offset(c.x + k, c.y - k * 0.6f), 2.dp.toPx())
            }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Мини-плеер: полоска внизу с обложкой, строками и кругом «играть». */
private fun DrawScope.miniBar(top: Float, height: Float, dx: Float, line: Color, fill: Color, tone: Color, stroke: Stroke) {
    val r = CornerRadius(16.dp.toPx())
    drawRoundRect(fill, Offset(0f, top), Size(size.width, height), r)
    drawRoundRect(line, Offset(0f, top), Size(size.width, height), r, style = stroke)
    val cy = top + 32.dp.toPx()
    val pad = 12.dp.toPx()
    drawRoundRect(line, Offset(pad + dx, cy - 20.dp.toPx()), Size(40.dp.toPx(), 40.dp.toPx()), CornerRadius(6.dp.toPx()), style = stroke)
    drawLine(line, Offset(pad + 52.dp.toPx() + dx, cy - 6.dp.toPx()), Offset(size.width * 0.62f + dx, cy - 6.dp.toPx()), 2.dp.toPx())
    drawLine(line.copy(alpha = line.alpha * 0.6f), Offset(pad + 52.dp.toPx() + dx, cy + 6.dp.toPx()), Offset(size.width * 0.48f + dx, cy + 6.dp.toPx()), 2.dp.toPx())
    drawCircle(tone, 16.dp.toPx(), Offset(size.width - pad - 16.dp.toPx(), cy))
}

/** Вверх по мини-плееру: полоска растёт в плеер, обложка — в большую. */
private fun DrawScope.drawMiniUp(p: Float, line: Color, fill: Color, tone: Color, stroke: Stroke): Offset {
    val barH = 64.dp.toPx()
    val top = lerp(size.height - barH, 0f, p)
    val h = size.height - top
    if (p < 0.5f) {
        miniBar(top, h, 0f, line, fill, tone, stroke)
    } else {
        val r = CornerRadius(16.dp.toPx())
        drawRoundRect(fill, Offset(0f, top), Size(size.width, h), r)
        drawRoundRect(line, Offset(0f, top), Size(size.width, h), r, style = stroke)
        val side = lerp(40.dp.toPx(), size.width * 0.62f, ((p - 0.5f) / 0.5f))
        drawRoundRect(line, Offset((size.width - side) / 2f, top + 24.dp.toPx()), Size(side, side), CornerRadius(10.dp.toPx()), style = stroke)
    }
    return Offset(size.width * 0.4f, lerp(size.height - barH / 2f, size.height - barH / 2f - 110.dp.toPx(), p))
}

/** Вбок по мини-плееру: строки уезжают, справа проступает «следующий». */
private fun DrawScope.drawMiniSide(p: Float, line: Color, fill: Color, tone: Color, stroke: Stroke): Offset {
    val barH = 64.dp.toPx()
    val top = size.height / 2f - barH / 2f
    val dx = -p * 90.dp.toPx()
    miniBar(top, barH, dx, line, fill, tone, stroke)
    if (p > 0.2f) {
        val a = ((p - 0.2f) / 0.8f)
        val x = size.width - 70.dp.toPx() - 20.dp.toPx() * (1f - a)
        val cy = top + barH / 2f
        drawLine(tone.copy(alpha = a), Offset(x, cy - 8.dp.toPx()), Offset(x + 8.dp.toPx(), cy), 2.dp.toPx())
        drawLine(tone.copy(alpha = a), Offset(x + 8.dp.toPx(), cy), Offset(x, cy + 8.dp.toPx()), 2.dp.toPx())
    }
    return Offset(lerp(size.width * 0.7f, size.width * 0.7f - 110.dp.toPx(), p), top + barH / 2f)
}

/** Долгое нажатие на строку: строка вдавливается, рядом раскрывается меню действий. */
private fun DrawScope.drawHold(p: Float, line: Color, fill: Color, tone: Color, stroke: Stroke): Offset {
    val rowH = 56.dp.toPx()
    val gap = 10.dp.toPx()
    val top0 = size.height / 2f - rowH * 1.5f - gap
    for (i in 0 until 3) {
        val top = top0 + i * (rowH + gap)
        val k = if (i == 1) 1f - 0.04f * p else 1f
        val w = size.width * k
        val x = (size.width - w) / 2f
        drawRoundRect(if (i == 1) fill.copy(alpha = fill.alpha + 0.08f * p) else fill, Offset(x, top), Size(w, rowH * k), CornerRadius(12.dp.toPx()))
        drawRoundRect(line, Offset(x + 10.dp.toPx(), top + 8.dp.toPx()), Size(40.dp.toPx(), 40.dp.toPx()), CornerRadius(6.dp.toPx()), style = stroke)
        drawLine(line, Offset(x + 62.dp.toPx(), top + rowH / 2f), Offset(x + w * 0.7f, top + rowH / 2f), 2.dp.toPx())
    }
    if (p >= 1f) {
        val mw = size.width * 0.56f
        val mTop = top0 + rowH + gap + rowH * 0.6f
        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(size.width - mw - 8.dp.toPx(), mTop), Size(mw, 110.dp.toPx()), CornerRadius(14.dp.toPx()))
        drawRoundRect(line, Offset(size.width - mw - 8.dp.toPx(), mTop), Size(mw, 110.dp.toPx()), CornerRadius(14.dp.toPx()), style = stroke)
        for (j in 0 until 3) {
            val y = mTop + 24.dp.toPx() + j * 30.dp.toPx()
            drawLine(if (j == 0) tone else line, Offset(size.width - mw + 10.dp.toPx(), y), Offset(size.width - 30.dp.toPx(), y), 2.dp.toPx())
        }
    }
    return Offset(size.width * 0.55f, top0 + rowH + gap + rowH / 2f)
}

/** Тяга вниз в поиске: сверху съезжает шторка fl0w с большой кнопкой. */
private fun DrawScope.drawFlowPull(p: Float, line: Color, fill: Color, tone: Color, stroke: Stroke): Offset {
    // Поиск: заголовок, поле, пилюля fl0w справа.
    drawLine(line, Offset(0f, 22.dp.toPx()), Offset(size.width * 0.38f, 22.dp.toPx()), 4.dp.toPx())
    drawRoundRect(line, Offset(size.width * 0.66f, 8.dp.toPx()), Size(size.width * 0.34f, 28.dp.toPx()), CornerRadius(14.dp.toPx()), style = stroke)
    drawRoundRect(fill, Offset(0f, 52.dp.toPx()), Size(size.width, 44.dp.toPx()), CornerRadius(22.dp.toPx()))
    for (i in 0 until 3) drawLine(line.copy(alpha = line.alpha * 0.5f), Offset(0f, 130.dp.toPx() + i * 30.dp.toPx()), Offset(size.width * (0.8f - i * 0.15f), 130.dp.toPx() + i * 30.dp.toPx()), 2.dp.toPx())
    // Шторка.
    val h = size.height * p
    if (h > 1f) {
        drawRect(Color.Black.copy(alpha = 0.55f), Offset.Zero, Size(size.width, h))
        drawLine(line, Offset(0f, h), Offset(size.width, h), 1.dp.toPx())
        if (p > 0.4f) {
            val a = (p - 0.4f) / 0.6f
            drawCircle(tone.copy(alpha = a), 34.dp.toPx() * a, Offset(size.width / 2f, h - size.height * 0.45f))
        }
    }
    return Offset(size.width * 0.5f, lerp(40.dp.toPx(), 40.dp.toPx() + 150.dp.toPx(), p))
}

/** Тяга вниз в плеере: плеер съезжает и сжимается в мини-плеер. */
private fun DrawScope.drawPlayerDown(p: Float, line: Color, fill: Color, tone: Color, stroke: Stroke): Offset {
    val barH = 64.dp.toPx()
    val top = lerp(0f, size.height - barH, p)
    val h = size.height - top
    val r = CornerRadius(16.dp.toPx())
    if (p < 0.6f) {
        drawRoundRect(fill, Offset(0f, top), Size(size.width, h), r)
        drawRoundRect(line, Offset(0f, top), Size(size.width, h), r, style = stroke)
        val side = lerp(size.width * 0.62f, 40.dp.toPx(), p / 0.6f)
        drawRoundRect(line, Offset((size.width - side) / 2f, top + 24.dp.toPx()), Size(side, side), CornerRadius(10.dp.toPx()), style = stroke)
        drawCircle(tone, 22.dp.toPx() * (1f - p), Offset(size.width / 2f, size.height - 40.dp.toPx()))
    } else {
        miniBar(top, h, 0f, line, fill, tone, stroke)
    }
    return Offset(size.width * 0.5f, lerp(60.dp.toPx(), 60.dp.toPx() + 150.dp.toPx(), p))
}

private fun TourStep.title(): Int =
    when (this) {
        TourStep.INTRO -> R.string.tour_intro_title
        TourStep.MINI_UP -> R.string.tour_mini_up_title
        TourStep.MINI_SIDE -> R.string.tour_mini_side_title
        TourStep.LONG_PRESS -> R.string.tour_hold_title
        TourStep.FLOW_PULL -> R.string.tour_flow_title
        TourStep.PLAYER_DOWN -> R.string.tour_player_title
    }

private fun TourStep.text(): Int =
    when (this) {
        TourStep.INTRO -> R.string.tour_intro_text
        TourStep.MINI_UP -> R.string.tour_mini_up_text
        TourStep.MINI_SIDE -> R.string.tour_mini_side_text
        TourStep.LONG_PRESS -> R.string.tour_hold_text
        TourStep.FLOW_PULL -> R.string.tour_flow_text
        TourStep.PLAYER_DOWN -> R.string.tour_player_text
    }

private const val DEMO_MS = 2400
private const val HOLD_MS = 500
private const val SOLVED_PAUSE_MS = 650L
