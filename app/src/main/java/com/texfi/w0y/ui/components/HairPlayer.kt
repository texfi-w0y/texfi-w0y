package com.texfi.w0y.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.CoverShape
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.playback.MiniDismiss
import com.texfi.w0y.playback.PlayerUiState
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.hairCoverShape
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Герои Hairline-плеера: диск-обложка с кольцом прогресса, круглая кнопка
 * Play, живая линия и мини-плеер. Всё рисуется контуром; анимируемые
 * значения читаются в слое рисования, раскладка и композиция не страдают.
 */

private val HAIR_PLAY = floatArrayOf(8f, 5f, 13f, 8.3f, 13f, 15.7f, 8f, 19f, 13f, 8.3f, 19f, 12f, 19f, 12f, 13f, 15.7f)
private val HAIR_PAUSE = floatArrayOf(7.5f, 5f, 10.5f, 5f, 10.5f, 19f, 7.5f, 19f, 13.5f, 5f, 16.5f, 5f, 16.5f, 19f, 13.5f, 19f)

/**
 * Круг-контур Play/Pause. Иконка перетекает из треугольника в две полосы
 * внутри того же круга; нажатие вдавливает (0.96), заполняет круг тоном и
 * пускает одно кольцо-вспышку наружу. Вибро — короткий отклик.
 */
@Composable
fun HairPlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val haptic = rememberSelectHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "playPress")
    val morph by animateFloatAsState(if (playing) 1f else 0f, hairSpec(HairTokens.MID_MS), label = "playMorph")
    val tone = Contrast.ensure(colors.accent, colors.background)
    val description = stringResource(if (playing) R.string.hl_cd_pause else R.string.hl_cd_play)
    Box(
        modifier
            .size(maxOf(size, HairTokens.TOUCH))
            .graphicsLayer {
                val k = 1f - 0.04f * press
                scaleX = k
                scaleY = k
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(interactionSource = interaction, indication = null) {
                haptic()
                onClick()
            }
            .drawBehind { 
                val half = this.size.minDimension / 2f
                val r = half * 0.8f
                val c = center
                // Без круга: только сама иконка, она крупнее и под пальцем уходит в тон акцента.
                // Иконка: два четырёхугольника, треугольник ↔ две полосы.
                val k = r * 1.2f / 12f
                val ox = c.x - 12f * k
                val oy = c.y - 12f * k
                val path = Path()
                for (shape in 0..1) {
                    for (i in 0..3) {
                        val a = HAIR_PLAY[shape * 8 + i * 2] + (HAIR_PAUSE[shape * 8 + i * 2] - HAIR_PLAY[shape * 8 + i * 2]) * morph
                        val b = HAIR_PLAY[shape * 8 + i * 2 + 1] + (HAIR_PAUSE[shape * 8 + i * 2 + 1] - HAIR_PLAY[shape * 8 + i * 2 + 1]) * morph
                        if (i == 0) path.moveTo(ox + a * k, oy + b * k) else path.lineTo(ox + a * k, oy + b * k)
                    }
                    path.close()
                }
                drawPath(path, androidx.compose.ui.graphics.lerp(colors.text, tone, press), style = Fill)
            },
    )
}

/** Путь рамки по часовой от верхней середины: круг, скруглённый квадрат или квадрат. */
internal fun framePath(rect: Rect, radius: Float, into: Path = Path()): Path {
    val r = radius.coerceIn(0f, min(rect.width, rect.height) / 2f)
    into.rewind()
    into.moveTo(rect.center.x, rect.top)
    into.lineTo(rect.right - r, rect.top)
    if (r > 0f) into.arcTo(Rect(rect.right - 2 * r, rect.top, rect.right, rect.top + 2 * r), -90f, 90f, false)
    into.lineTo(rect.right, rect.bottom - r)
    if (r > 0f) into.arcTo(Rect(rect.right - 2 * r, rect.bottom - 2 * r, rect.right, rect.bottom), 0f, 90f, false)
    into.lineTo(rect.left + r, rect.bottom)
    if (r > 0f) into.arcTo(Rect(rect.left, rect.bottom - 2 * r, rect.left + 2 * r, rect.bottom), 90f, 90f, false)
    into.lineTo(rect.left, rect.top + r)
    if (r > 0f) into.arcTo(Rect(rect.left, rect.top, rect.left + 2 * r, rect.top + 2 * r), 180f, 90f, false)
    into.lineTo(rect.center.x, rect.top)
    return into
}

/**
 * Диск-обложка: обложка в форме из настроек, тонкое перекрестие поверх,
 * вокруг — кольцо прогресса и деления. Жесты: двойной тап — пауза,
 * свайп вбок — трек, долгое нажатие и круговое движение — точная перемотка
 * с маркерами каждые 10 с.
 */
@Composable
fun HairDisc(
    coverUrl: String?,
    songId: String,
    progress: () -> Float,
    durationMs: Long,
    speed: Float,
    size: Dp,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTap: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Что показать в диске вместо обложки (визуализатор, текст, видео); получает адрес обложки именно своего трека. */
    content: (@Composable (String?) -> Unit)? = null,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val haptic = rememberHaptics()
    var seeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    val seekAmount by animateFloatAsState(if (seeking) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "seekAmount")
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(songId) {
        if (look.motion.draws) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut))
        } else {
            sweep.snapTo(1f)
        }
    }
    val tone = Contrast.ensure(colors.accent, colors.background)
    val description = stringResource(R.string.hl_cd_disc)
    val gap = RING_GAP
    val shape = hairCoverShape(look)
    val density = LocalDensity.current
    val currentDuration by rememberUpdatedState(durationMs)
    val toggle by rememberUpdatedState(onToggle)
    val tap by rememberUpdatedState(onTap)
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val seek by rememberUpdatedState(onSeek)
    val screenDensity = density.density

    Box(
        modifier
            .size(size)
            .semantics { contentDescription = description }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggle() }, onTap = { tap() }) }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total <= -SWIPE_PX -> { haptic(Buzz.TRACK); next() }
                            total >= SWIPE_PX -> { haptic(Buzz.TRACK); previous() }
                        }
                    },
                ) { _, dx -> total += dx }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { at ->
                        seeking = true
                        haptic(Buzz.SELECT)
                        seekFraction = HairMath.angleFraction(at.x - this.size.width / 2f, at.y - this.size.height / 2f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val raw = HairMath.angleFraction(change.position.x - this.size.width / 2f, change.position.y - this.size.height / 2f)
                        seekFraction = HairMath.unwrapFraction(seekFraction, raw)
                    },
                    onDragEnd = {
                        if (currentDuration > 0) seek((seekFraction * currentDuration).toLong())
                        seeking = false
                    },
                    onDragCancel = { seeking = false },
                )
            }
            .drawWithCache {
                val stroke = look.line.toPx()
                val inset = stroke * 2f
                val rect = Rect(inset, inset, this.size.width - inset, this.size.height - inset)
                val side = min(rect.width, rect.height)
                val radius = when (look.coverShape) {
                    CoverShape.DISC -> side / 2f
                    CoverShape.ROUNDED -> com.texfi.w0y.ui.theme.coverRadiusPx(side, screenDensity)
                    CoverShape.SQUARE -> 0f
                }
                val frame = framePath(rect, radius)
                val measure = PathMeasure().also { it.setPath(frame, false) }
                val length = measure.length
                // Деления по рамке: число зависит от скорости воспроизведения.
                val count = HairMath.tickCount(speed)
                val ticks = FloatArray(count * 4)
                val fractions = FloatArray(count)
                for (i in 0 until count) {
                    val f = i.toFloat() / count
                    fractions[i] = f
                    val pos = measure.getPosition(length * f)
                    val tan = measure.getTangent(length * f)
                    // Внутрь от рамки: путь идёт по часовой, внутрь — вправо от хода.
                    val nx = -tan.y
                    val ny = tan.x
                    val len = if (i % 5 == 0) 7.dp.toPx() else 4.dp.toPx()
                    ticks[i * 4] = pos.x
                    ticks[i * 4 + 1] = pos.y
                    ticks[i * 4 + 2] = pos.x + nx * len
                    ticks[i * 4 + 3] = pos.y + ny * len
                }
                val part = Path()
                val markers = Path()
                onDrawBehind {
                    val widen = 1f + 1.4f * seekAmount
                    val w = stroke * widen
                    val track = sweep.value
                    // Деления.
                    val shown = if (seeking) seekFraction else progress().coerceIn(0f, 1f)
                    // Деления появляются только при перемотке: в покое обложка чистая.
                    if (seekAmount > 0.01f) for (i in 0 until count) {
                        val on = fractions[i] <= shown
                        drawLine(
                            color = (if (on) colors.progress else colors.outline).copy(alpha = seekAmount),
                            start = Offset(ticks[i * 4], ticks[i * 4 + 1]),
                            end = Offset(ticks[i * 4 + 2], ticks[i * 4 + 3]),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                    }
                    // Дорожка рисуется линией-разверткой при смене трека.
                    part.rewind()
                    measure.getSegment(0f, length * track, part, true)
                    drawPath(part, colors.border, style = Stroke(w, cap = StrokeCap.Round))
                    // Прогресс — синим.
                    val p = (if (seeking) seekFraction else progress()).coerceIn(0f, 1f) * track
                    if (p > 0f) {
                        part.rewind()
                        measure.getSegment(0f, length * p, part, true)
                        drawPath(part, colors.progress, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    // Маркеры точной перемотки каждые 10 с.
                    if (seekAmount > 0f && currentDuration > 0) {
                        val step = HairMath.seekMarkerStepMs(currentDuration)
                        var t = step
                        while (t < currentDuration) {
                            val f = t.toFloat() / currentDuration
                            val pos = measure.getPosition(length * f)
                            val tan = measure.getTangent(length * f)
                            val nx = -tan.y
                            val ny = tan.x
                            val l = 10.dp.toPx() * seekAmount
                            drawLine(tone.copy(alpha = seekAmount), pos, Offset(pos.x + nx * l, pos.y + ny * l), stroke, StrokeCap.Round)
                            t += step
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().padding(gap)) {
            AnimatedContent(
                // Состояние несёт и обложку: уходящая обложка остаётся своей, а не меняется на новую.
                targetState = songId to coverUrl,
                transitionSpec = {
                    if (look.motion.draws) {
                        val d = with(density) { 24.dp.roundToPx() }
                        (slideInHorizontally(tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut)) { d } + fadeIn(tween(HairTokens.LONG_MS)))
                            .togetherWith(slideOutHorizontally(tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut)) { -d } + fadeOut(tween(HairTokens.MID_MS)))
                    } else if (look.motion.moves) {
                        fadeIn(tween(HairTokens.SHORT_MS)).togetherWith(fadeOut(tween(HairTokens.SHORT_MS)))
                    } else {
                        ContentTransform(androidx.compose.animation.EnterTransition.None, androidx.compose.animation.ExitTransition.None)
                    }
                },
                label = "discTrack",
                modifier = Modifier.fillMaxSize(),
            ) { (_, shownUrl) ->
                Box(Modifier.fillMaxSize().clip(shape)) {
                    if (content != null) content(shownUrl) else CoverImage(url = shownUrl, px = Thumbnails.HERO, modifier = Modifier.fillMaxSize())
                }
            }
            // Перекрестие — только при перемотке: показывает, куда ведёт палец.
            Canvas(Modifier.fillMaxSize()) {
                val w = look.line.toPx()
                val a = 0.85f * seekAmount
                val line = Color.White.copy(alpha = a)
                val sz = this.size
                if (a > 0.01f) {
                    drawLine(line, Offset(0f, sz.height / 2f), Offset(sz.width, sz.height / 2f), w)
                    drawLine(line, Offset(sz.width / 2f, 0f), Offset(sz.width / 2f, sz.height), w)
                    drawCircle(line, 3.dp.toPx(), center, style = Stroke(w))
                }
            }
            if (seekAmount > 0f) {
                Text(
                    text = HairMath.formatTime((seekFraction * durationMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .graphicsLayer { alpha = seekAmount }
                            .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private val RING_GAP = 7.dp
private const val SWIPE_PX = 120f
/**
 * Живая линия мини-плеера: волна из заранее посчитанных амплитуд. Пройденная
 * часть — синяя, остальная — разделитель. Пока играет, амплитуда «дышит».
 */
@Composable
fun HairWaveLine(seed: Int, progress: () -> Float, playing: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val live = playing && look.motion.moves
    val phase = rememberAnimationPhase(2_400, 20, enabled = live)
    Canvas(
        modifier
            .height(18.dp)
            .drawWithCache {
                val amps = HairMath.waveAmps(seed)
                val w = look.line.toPx()
                val mid = size.height / 2f
                val maxAmp = size.height / 2f - w
                val base = Path()
                val step = size.width / (amps.size - 1)
                var px = 0f
                var py = mid
                base.moveTo(px, py)
                amps.forEachIndexed { i, a ->
                    val x = step * i
                    val y = mid + (if (i % 2 == 0) -1f else 1f) * a * maxAmp
                    base.quadraticTo(px, py, (px + x) / 2f, (py + y) / 2f)
                    px = x
                    py = y
                }
                base.lineTo(px, py)
                val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    val k = if (live) 0.82f + 0.18f * sin(phase.floatValue) else 1f
                    withTransform({ scale(1f, k, Offset(0f, mid)) }) {
                        drawPath(base, colors.border, style = stroke)
                        clipRect(right = size.width * progress().coerceIn(0f, 1f)) {
                            drawPath(base, colors.progress, style = stroke)
                        }
                    }
                }
            },
    ) {}
}

/** Крохотный диск мини-плеера с кольцом прогресса вокруг. */
@Composable
private fun HairMiniDisc(url: String?, progress: () -> Float, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val shape = hairCoverShape(look)
    Box(
        Modifier
            .size(52.dp)
            .clickable(onClick = onClick)
            .drawWithCache {
                val stroke = look.line.toPx()
                val rect = Rect(stroke, stroke, size.width - stroke, size.height - stroke)
                val radius = when (look.coverShape) {
                    CoverShape.DISC -> min(rect.width, rect.height) / 2f
                    CoverShape.ROUNDED -> com.texfi.w0y.ui.theme.coverRadiusPx(rect.width, density)
                    CoverShape.SQUARE -> 0f
                }
                val frame = framePath(rect, radius)
                val measure = PathMeasure().also { it.setPath(frame, false) }
                val part = Path()
                onDrawBehind {
                    drawPath(frame, colors.border, style = Stroke(stroke))
                    val p = progress().coerceIn(0f, 1f)
                    if (p > 0f) {
                        part.rewind()
                        measure.getSegment(0f, measure.length * p, part, true)
                        drawPath(part, colors.progress, style = Stroke(stroke * 1.6f, cap = StrokeCap.Round))
                    }
                }
            }
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            CoverImage(url = url, px = Thumbnails.ROW, modifier = Modifier.fillMaxSize().coverFlightMini(LocalCoverFlight.current, 40.dp))
        }
    }
}

/**
 * Мини-плеер Hairline: живая линия сверху, диск с кольцом, название,
 * круглая Play и «дальше». Тянется вниз — останавливается и убирается
 * (если [onDismiss] задан); до порога следует за пальцем, при недоборе
 * возвращается пружиной. Для TalkBack есть действие «Убрать плеер».
 */
@Composable
fun HairMiniPlayer(
    state: PlayerUiState,
    positionProvider: () -> Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Внутри парящей панели: без своего фона и линии-волны, прогресс рисует контур панели. */
    docked: Boolean = false,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    AnimatedVisibility(
        visible = state.song != null,
        enter = expandVertically(hairSpec(HairTokens.MID_MS)) + fadeIn(hairSpec(HairTokens.MID_MS)),
        exit = shrinkVertically(hairSpec(HairTokens.MID_MS)) + fadeOut(hairSpec(HairTokens.SHORT_MS)),
        modifier = modifier,
    ) {
        val song = state.song ?: return@AnimatedVisibility
        var position by remember { mutableLongStateOf(0L) }
        LaunchedEffect(state.isPlaying, song.id) {
            position = positionProvider()
            while (state.isPlaying) {
                delay(500)
                position = positionProvider()
            }
        }
        val duration = state.durationMs
        val progress = { if (duration > 0) (position.toFloat() / duration) else 0f }
        var offset by remember { mutableFloatStateOf(0f) }
        var heightPx by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val dismissLabel = stringResource(R.string.hl_cd_dismiss)
        val miniDescription = stringResource(R.string.hl_cd_mini, song.title, song.artist)
        val dragState = rememberDraggableState { delta -> offset = (offset + delta).coerceAtLeast(0f) }
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { heightPx = it.height }
                .graphicsLayer {
                    translationY = offset
                    alpha = MiniDismiss.alpha(offset, heightPx.toFloat())
                }
                .semantics(mergeDescendants = false) {
                    contentDescription = miniDescription
                    if (onDismiss != null) {
                        customActions = listOf(CustomAccessibilityAction(dismissLabel) { onDismiss(); true })
                    }
                }
                .then(
                    if (onDismiss == null) {
                        Modifier
                    } else {
                        Modifier.draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                val h = heightPx.toFloat()
                                if (MiniDismiss.shouldDismiss(offset, h, velocity)) {
                                    animate(offset, h * 1.1f, animationSpec = tween(HairTokens.SHORT_MS, easing = HairTokens.EaseOut)) { v, _ -> offset = v }
                                    onDismiss()
                                    offset = 0f
                                } else {
                                    animate(
                                        offset,
                                        0f,
                                        animationSpec = spring(dampingRatio = HairTokens.SPRING_DAMPING, stiffness = HairTokens.SPRING_STIFFNESS),
                                    ) { v, _ -> offset = v }
                                }
                            },
                        )
                    },
                )
                .then(if (docked) Modifier else Modifier.background(colors.surface)),
        ) {
            if (!docked) HairWaveLine(song.id.hashCode(), progress, state.isPlaying, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp))
            SwipeRow(
                onSwipeRight = onPrevious,
                rightLabel = "<<",
                onSwipeLeft = onNext,
                leftLabel = ">>",
                backdrop = colors.surface,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = if (docked) 16.dp else 12.dp, end = 4.dp, top = if (docked) 12.dp else 2.dp, bottom = if (docked) 10.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (docked) {
                        // В панели прогресс рисует её контур — у обложки рамки нет.
                        CoverImage(
                            url = song.thumbnailUrl,
                            px = Thumbnails.ROW,
                            modifier = Modifier.size(44.dp).coverFlightMini(LocalCoverFlight.current, 44.dp).clickable(onClick = onExpand),
                        )
                    } else {
                        HairMiniDisc(song.thumbnailUrl, progress, onExpand)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable(onClick = onExpand)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (state.isBuffering) stringResource(R.string.common_loading) else song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HairPlayButton(playing = state.isPlaying, onClick = onToggle, size = 46.dp)
                    HairIconButton(HairGlyph.NEXT, stringResource(R.string.hl_cd_next), onNext, tint = colors.text)
                }
            }
            if (docked) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(colors.border.copy(alpha = 0.7f)))
            }
        }
    }
}

/** Кнопка-иконка Hairline: касание не меньше 48 dp, подпись для TalkBack, отклик нажатием. */
@Composable
fun HairIconButton(
    glyph: HairGlyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalW0yColors.current.textMuted,
    size: Dp = 22.dp,
    fill: () -> Float = { 0f },
) {
    val interaction = remember { MutableInteractionSource() }
    val tap = rememberTapHaptic()
    Box(
        modifier
            .size(HairTokens.TOUCH)
            .pressScale(interaction, pressed = 0.9f)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(interactionSource = interaction, indication = null) {
                tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        HairIcon(glyph, tint, Modifier.size(size), fill = fill)
    }
}


/**
 * Герой главной: то, что играет, диском с кольцом прогресса. Тап по диску
 * открывает плеер; пауза, смена трека и перемотка — теми же жестами, что в
 * самом плеере.
 */
@Composable
fun HairNowHero(
    state: PlayerUiState,
    positionProvider: () -> Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return
    val colors = LocalW0yColors.current
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.isPlaying, song.id) {
        position = positionProvider()
        while (state.isPlaying) {
            delay(500)
            position = positionProvider()
        }
    }
    val progress = { if (state.durationMs > 0) position.toFloat() / state.durationMs else 0f }
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HairDisc(
            coverUrl = song.thumbnailUrl,
            songId = song.id,
            progress = progress,
            durationMs = state.durationMs,
            speed = 1f,
            size = 232.dp,
            onToggle = onToggle,
            onNext = onNext,
            onPrevious = onPrevious,
            onTap = onOpen,
            onSeek = onSeek,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            HairIconButton(HairGlyph.PREVIOUS, stringResource(R.string.hl_cd_prev), onPrevious, tint = colors.text)
            HairPlayButton(playing = state.isPlaying, onClick = onToggle, size = 60.dp)
            HairIconButton(HairGlyph.NEXT, stringResource(R.string.hl_cd_next), onNext, tint = colors.text)
        }
    }
}
