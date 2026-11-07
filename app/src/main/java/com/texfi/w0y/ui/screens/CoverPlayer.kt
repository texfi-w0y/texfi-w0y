package com.texfi.w0y.ui.screens

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.widthIn

import com.texfi.w0y.ui.theme.asLabel

import androidx.compose.ui.draw.drawBehind

import com.texfi.w0y.ui.theme.screenBackdrop

import com.texfi.w0y.ui.theme.matte
import com.texfi.w0y.ui.theme.drawGrain

import com.texfi.w0y.ui.components.coverFlightHide
import com.texfi.w0y.ui.components.coverFlightPlayer
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.HairMath
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.hairLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Палитра поверх размытой обложки: фон тёмный при любой теме, текст светлый, линии заметнее
 * обычного — на цветном пятне тонкий контур иначе теряется. Акцент остаётся акцентом:
 * включённое (повтор, перемешивание, лайк) видно цветом, а не только толщиной.
 */
@Composable
internal fun overBlurColors() =
    LocalW0yColors.current.let { base ->
        base.copy(
            background = Color(0xFF0B0B0E),
            surface = Color(0xFF15151B).copy(alpha = 0.72f),
            surfaceHigh = Color.White.copy(alpha = 0.12f),
            border = Color.White.copy(alpha = 0.22f),
            lineStrong = Color.White.copy(alpha = 0.5f),
            text = Color.White,
            textMuted = Color.White.copy(alpha = 0.74f),
        )
    }

/**
 * Размытая обложка на весь экран. Цвета трека растекаются по фону сами и при смене трека
 * перетекают в новые; пока играет — пятно медленно дышит (масштаб и сдвиг, цикл 18 с).
 * С Android 12 размытие настоящее, на старых версиях — мелкая картинка, растянутая в мягкое пятно.
 */
@Composable
fun PlayerBlurBackdrop(url: String?, playing: Boolean, modifier: Modifier = Modifier) {
    if (com.texfi.w0y.ui.theme.isIsland) {
        IslandFlowBackdrop(url, playing, modifier)
        return
    }
    val context = LocalContext.current
    val moves = hairLook.motion.moves && playing
    val drift = rememberInfiniteTransition(label = "blurDrift")
    val phase by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse), label = "blurPhase")
    Box(modifier.fillMaxSize().background(Color(0xFF0B0B0E))) {
        Crossfade(targetState = url, animationSpec = tween(700), label = "blurBackdrop") { current ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(Thumbnails.sized(current, 96)).crossfade(false).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = if (moves) phase else 0.5f
                            val scale = 1.4f + 0.12f * p
                            scaleX = scale
                            scaleY = scale
                            translationX = (p - 0.5f) * size.width * 0.08f
                            translationY = (0.5f - p) * size.height * 0.05f
                        }
                        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(64.dp) else Modifier),
            )
        }
        // Затемнение сильнее к низу: управление стоит на спокойном тёмном поле.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.45f to Color.Black.copy(alpha = 0.45f), 1f to Color.Black.copy(alpha = 0.82f))),
        )
    }
}

/**
 * Фон постера Hairline New: цвета обложки переливаются. Три копии обложки, крупно и сильно
 * размытые, медленно вращаются вокруг разных точек в разные стороны (24, 31 и 38 с на оборот) —
 * цветовые пятна обложки текут друг через друга, как жидкость. Пока трек на паузе, течение
 * замирает; без анимаций — неподвижная смесь. Смена трека — перетекание за 900 мс.
 */
@Composable
private fun IslandFlowBackdrop(url: String?, playing: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val moves = hairLook.motion.moves
    // Свои часы течения: идут, пока трек играет, и стоят на паузе — продолжение без рывка.
    val clock = remember { androidx.compose.runtime.mutableFloatStateOf(7f) }
    androidx.compose.runtime.LaunchedEffect(playing, moves) {
        if (!playing || !moves) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) clock.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    val periods = floatArrayOf(24f, 31f, 38f)
    Box(modifier.fillMaxSize().background(Color(0xFF0B0B0E))) {
        Crossfade(targetState = url, animationSpec = tween(900), label = "flowBackdrop") { current ->
            Box(Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(72.dp) else Modifier)) {
                val layers = listOf(Triple(0.0f, 0.0f, 1f), Triple(-0.28f, 0.22f, 0.75f), Triple(0.3f, -0.18f, 0.6f))
                layers.forEachIndexed { i, (dx, dy, a) ->
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(Thumbnails.sized(current, 96)).crossfade(false).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val angle = clock.floatValue / periods[i] * 360f * (if (i == 1) -1f else 1f)
                                    alpha = a
                                    val k = 1.9f - i * 0.25f
                                    scaleX = k
                                    scaleY = k
                                    rotationZ = angle
                                    translationX = dx * size.width
                                    translationY = dy * size.height
                                },
                    )
                }
            }
        }
        // Поле темнее к низу: кнопки и название стоят на спокойном цвете; сверху цвет виден в полную силу.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.5f to Color.Black.copy(alpha = 0.32f), 1f to Color.Black.copy(alpha = 0.66f))),
        )
    }
}

/** Обложка без рамки и формы: заполняет отведённое место целиком. */
@Composable
internal fun PlainCover(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(Thumbnails.sized(url, Thumbnails.HERO)).crossfade(200).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/**
 * Появление блока при открытии плеера: снизу на 24 dp и из прозрачности, с задержкой по номеру
 * блока — экран собирается ступенью сверху вниз. В Motion Off — сразу на месте.
 */
@Composable
internal fun Modifier.hairEnter(index: Int): Modifier {
    val moves = hairLook.motion.moves
    val anim = remember { Animatable(if (moves) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (moves) {
            delay(60L * index)
            anim.animateTo(1f, tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut))
        }
    }
    return graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 24.dp.toPx()
    }
}

/**
 * Тяга вниз по любому месту страницы сворачивает плеер, как в остальных раскладках: страница не
 * прокручивается, поэтому сама отдаёт жест шторке через вложенную прокрутку.
 */
@Composable
private fun Modifier.dragToCollapse(): Modifier {
    val dispatcher = remember { NestedScrollDispatcher() }
    val scope = rememberCoroutineScope()
    val noop = remember { object : NestedScrollConnection {} }
    return nestedScroll(noop, dispatcher).pointerInput(Unit) {
        val tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = {
                val v = Velocity(0f, tracker.calculateVelocity().y)
                scope.launch {
                    val used = dispatcher.dispatchPreFling(v)
                    dispatcher.dispatchPostFling(used, v - used)
                }
            },
            onDragCancel = { scope.launch { dispatcher.dispatchPostFling(Velocity.Zero, Velocity.Zero) } },
        ) { change, dy ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            val delta = Offset(0f, dy)
            val pre = dispatcher.dispatchPreScroll(delta, NestedScrollSource.UserInput)
            dispatcher.dispatchPostScroll(Offset.Zero, delta - pre, NestedScrollSource.UserInput)
        }
    }
}

/**
 * Рамка обложки Hairline: тонкий контур с зазором, по которому бежит прогресс трека — та же
 * мысль, что кольцо диска, только по скруглённому квадрату. По углам — короткие засечки,
 * как на чертёжном листе.
 */
private fun Modifier.progressFrame(progress: () -> Float, line: Color, tone: Color, width: Float, radius: Float): Modifier =
    drawWithContent {
        drawContent()
        val inset = width / 2f
        val path = Path().apply { addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(radius))) }
        drawPath(path, line, style = Stroke(width))
        val p = progress().coerceIn(0f, 1f)
        if (p > 0f) {
            val measure = PathMeasure().apply { setPath(path, false) }
            val seg = Path()
            measure.getSegment(0f, measure.length * p, seg, true)
            drawPath(seg, tone, style = Stroke(width * 2.2f, cap = StrokeCap.Round))
        }
        val tick = 10.dp.toPx()
        val out = -6.dp.toPx()
        listOf(Offset(out, out) to Offset(1f, 1f), Offset(size.width - out, out) to Offset(-1f, 1f), Offset(out, size.height - out) to Offset(1f, -1f), Offset(size.width - out, size.height - out) to Offset(-1f, -1f))
            .forEach { (c, d) ->
                drawLine(line, c, c + Offset(tick * d.x, 0f), width)
                drawLine(line, c, c + Offset(0f, tick * d.y), width)
            }
    }

/**
 * Раскладка «обложка»: своя, Hairline. Всё на одном экране без прокрутки.
 *
 * Фон — размытая обложка. Сверху та же шапка, что в остальных раскладках (свернуть, номер трека,
 * текст, эффект, ещё). В центре обложка в тонкой рамке, по которой бежит прогресс; при смене трека
 * она уезжает в сторону смахивания, на паузе чуть сжимается. Ниже — название, ряд действий,
 * полоса, управление и плашка очереди. Тяга вниз сворачивает плеер.
 */
@Composable
internal fun CoverPlayerPage(
    song: SongItem,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    stage: @Composable (String?) -> Unit,
    header: @Composable () -> Unit,
    titleBlock: @Composable (Modifier) -> Unit,
    actions: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    plate: @Composable () -> Unit,
    onCoverTap: () -> Unit,
    onSwipe: (Int) -> Unit,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    poster: Boolean = false,
    visualizerOn: Boolean = false,
    /** На сцене сейчас обложка (а не визуализатор, текст или видео): только тогда она летит в мини-плеер. */
    coverShown: Boolean = true,
    /** Hairline New: номер трека в очереди для подписи «№ 11». */
    number: Int? = null,
    /** Hairline New: строка звука под линейкой — «1.00× · тон 1.00»; нажатие открывает скорость. */
    soundLine: String? = null,
    onSoundLine: () -> Unit = {},
) {
    val tapNow = androidx.compose.runtime.rememberUpdatedState(onCoverTap)
    if (poster) {
        PosterPlayerPage(song, isPlaying, positionMs, durationMs, stage, header, titleBlock, actions, transport, plate, { tapNow.value() }, onSwipe, onDrag, onDragEnd, visualizerOn, coverShown, number, soundLine, onSoundLine)
        return
    }
    CompositionLocalProvider(LocalW0yColors provides overBlurColors()) {
        val colors = LocalW0yColors.current
        val look = hairLook
        val tone = Contrast.ensure(colors.accent, colors.background)
        var lastSwipe by remember { mutableIntStateOf(1) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val lineFrame = with(density) { look.line.toPx() }
        val inSlide = hairSpec<androidx.compose.ui.unit.IntOffset>(HairTokens.LONG_MS)
        val outSlide = hairSpec<androidx.compose.ui.unit.IntOffset>(HairTokens.MID_MS)
        val inFade = hairSpec<Float>(HairTokens.LONG_MS)
        val outFade = hairSpec<Float>(HairTokens.MID_MS)
        val progressTarget = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val progress by animateFloatAsState(progressTarget, tween(480, easing = LinearEasing), label = "frameProgress")
        val breathe by animateFloatAsState(if (isPlaying) 1f else 0.94f, spring(dampingRatio = 0.6f, stiffness = 260f), label = "coverBreathe")
        Box(Modifier.fillMaxSize().dragToCollapse()) {
            PlayerBlurBackdrop(song.thumbnailUrl, isPlaying)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Box(Modifier.hairEnter(0)) { header() }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .hairEnter(1),
                    contentAlignment = Alignment.Center,
                ) {
                    val side = minOf(maxWidth, maxHeight) - 16.dp
                    val radius = look.cardRadius.coerceIn(10.dp, 22.dp)
                    val framePx = with(density) { (radius + 6.dp).toPx() }
                    val flight = com.texfi.w0y.ui.components.LocalCoverFlight.current
                    Box(
                        Modifier
                            .size(side)
                            // Место обложки для полёта: рамка 6 dp вокруг картинки не в счёт; «дыхание» ниже по цепочке.
                            .coverFlightPlayer(flight, enabled = true, insetDp = 6.dp, radiusDp = radius, ghost = !coverShown)
                            .graphicsLayer {
                                scaleX = breathe
                                scaleY = breathe
                                flight?.playerScale = breathe
                                flight?.playerAlpha = 1f
                            }
                            .progressFrame({ progress }, colors.outline, tone, lineFrame, framePx)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(radius))
                            .coverFlightHide(flight)
                            .pointerInput(song.id) {
                                var total = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { total = 0f },
                                    onDragEnd = {
                                        when {
                                            total <= -SWIPE_PX -> { lastSwipe = 1; onSwipe(1) }
                                            total >= SWIPE_PX -> { lastSwipe = -1; onSwipe(-1) }
                                        }
                                    },
                                ) { _, dx -> total += dx }
                            }
                            .pointerInput(Unit) { detectTapGestures { tapNow.value() } },
                    ) {
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = {
                                val dir = lastSwipe
                                (slideInHorizontally(inSlide) { it * dir / 3 } + fadeIn(inFade) + scaleIn(inFade, initialScale = 0.92f))
                                    .togetherWith(slideOutHorizontally(outSlide) { -it * dir / 3 } + fadeOut(outFade))
                            },
                            label = "coverTrack",
                        ) { trackId -> androidx.compose.runtime.key(trackId) { Box(Modifier.fillMaxSize()) { stage(song.thumbnailUrl) } } }
                    }
                }
                Box(Modifier.hairEnter(2)) { titleBlock(Modifier.fillMaxWidth()) }
                Box(Modifier.padding(top = 2.dp).hairEnter(3)) { actions() }
                Column(Modifier.hairEnter(4)) {
                    HairSeekLine(positionMs = positionMs, durationMs = durationMs, onDrag = onDrag, onDragEnd = onDragEnd)
                    Row(Modifier.fillMaxWidth()) {
                        Text(HairMath.formatTime(positionMs), style = HairLabelStyle, color = tone)
                        Spacer(Modifier.weight(1f))
                        Text(HairMath.formatTime(durationMs), style = HairLabelStyle, color = colors.textMuted)
                    }
                }
                Box(Modifier.padding(vertical = 4.dp).hairEnter(5)) { transport() }
                Box(Modifier.padding(top = 4.dp, bottom = 8.dp).hairEnter(6)) { plate() }
            }
        }
    }
}

/**
 * Раскладка «постер». Обложка во всю ширину от самого верха экрана и растворяется книзу в
 * размытом фоне — без рамки, как разворот журнала. Шапка лежит на тёмной вуали поверх неё.
 * Ниже — короткая акцентная черта, название и блок управления. Высота обложки считается
 * от того, сколько места осталось над управлением, поэтому на маленьком экране она не лезет
 * под кнопки. Смахивание вбок — соседний трек (обложка уезжает в ту же сторону), касание —
 * визуализатор, тяга вниз сворачивает плеер. На паузе обложка тускнеет и чуть приближается.
 */
@Composable
private fun PosterPlayerPage(
    song: SongItem,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    stage: @Composable (String?) -> Unit,
    header: @Composable () -> Unit,
    titleBlock: @Composable (Modifier) -> Unit,
    actions: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    plate: @Composable () -> Unit,
    onCoverTap: () -> Unit,
    onSwipe: (Int) -> Unit,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    visualizerOn: Boolean,
    coverShown: Boolean,
    number: Int?,
    soundLine: String?,
    onSoundLine: () -> Unit,
) {
    CompositionLocalProvider(LocalW0yColors provides overBlurColors()) {
        val colors = LocalW0yColors.current
        val look = hairLook
        val tone = Contrast.ensure(colors.accent, colors.background)
        val density = androidx.compose.ui.platform.LocalDensity.current
        // Визуализатор крупно, по центру видимой части обложки: сверху почти под шапкой, снизу
        // ровно до зоны растворения, которая лежит под кнопками, — на управление он не заходит.
        val stageTop by androidx.compose.animation.core.animateDpAsState(if (visualizerOn) 40.dp else 0.dp, hairSpec(HairTokens.MID_MS), label = "posterStageTop")
        val stageBottom by androidx.compose.animation.core.animateDpAsState(if (visualizerOn) 104.dp else 0.dp, hairSpec(HairTokens.MID_MS), label = "posterStageBottom")
        val coverAlpha by animateFloatAsState(if (visualizerOn) 0.28f else 1f, hairSpec(HairTokens.LONG_MS), label = "posterCoverDim")
        var lastSwipe by remember { mutableIntStateOf(1) }
        var controlsPx by remember { mutableIntStateOf(0) }
        val inSlide = hairSpec<androidx.compose.ui.unit.IntOffset>(HairTokens.LONG_MS)
        val outSlide = hairSpec<androidx.compose.ui.unit.IntOffset>(HairTokens.MID_MS)
        val inFade = hairSpec<Float>(HairTokens.LONG_MS)
        val outFade = hairSpec<Float>(HairTokens.MID_MS)
        val rest by animateFloatAsState(if (isPlaying) 0f else 1f, hairSpec(HairTokens.LONG_MS), label = "posterRest")
        val progressTarget = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val progress by animateFloatAsState(progressTarget, tween(480, easing = LinearEasing), label = "posterProgress")
        // Hairline New: шапка, кнопки и плашка — матовые острова; размывают они то, что под ними, —
        // обложку и её мягкую копию. Для этого всё, что под островами, пишется в одну запись.
        val island = com.texfi.w0y.ui.theme.isIsland
        val posterBackdrop = com.texfi.w0y.ui.theme.rememberScreenBackdrop()
        BoxWithConstraints(Modifier.fillMaxSize().dragToCollapse()) {
            val pageWidth = maxWidth
            val pageHeight = maxHeight
            Box(Modifier.matchParentSize().then(if (island) Modifier.screenBackdrop(posterBackdrop) else Modifier)) {
            PlayerBlurBackdrop(song.thumbnailUrl, isPlaying)
            // Обложка заходит под управление на величину растворения, но не выше квадрата.
            val fade = 96.dp
            val controls = with(density) { controlsPx.toDp() }
            val coverH = if (controlsPx == 0) pageWidth else (pageHeight - controls + fade).coerceIn(pageWidth * 0.6f, pageWidth * 1.15f)
            val flight = com.texfi.w0y.ui.components.LocalCoverFlight.current
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(coverH)
                    // Место постера для полёта: до входной анимации и «паузы» — они идут слоями ниже.
                    .coverFlightPlayer(flight, enabled = true, fadeDp = fade, ghost = !(coverShown && !visualizerOn))
                    .hairEnter(0)
                    .graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        alpha = 1f - 0.28f * rest
                        val s = 1f + 0.03f * rest
                        scaleX = s
                        scaleY = s
                        flight?.playerScale = s
                        flight?.playerAlpha = alpha
                    }
                    .drawWithContent {
                        drawContent()
                        // Растворение по плавной кривой и на полторы высоты зоны: край не читается линией.
                        val f = (fade.toPx() * 1.6f / size.height).coerceIn(0.08f, 0.7f)
                        drawRect(
                            Brush.verticalGradient(*smoothFade(1f - f, 1f)),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                    .pointerInput(song.id) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                when {
                                    total <= -SWIPE_PX -> { lastSwipe = 1; onSwipe(1) }
                                    total >= SWIPE_PX -> { lastSwipe = -1; onSwipe(-1) }
                                }
                            },
                        ) { _, dx -> total += dx }
                    }
                    .pointerInput(Unit) { detectTapGestures { onCoverTap() } },
            ) {
                AnimatedContent(
                    targetState = song.id,
                    transitionSpec = {
                        val dir = lastSwipe
                        (slideInHorizontally(inSlide) { it * dir / 4 } + fadeIn(inFade) + scaleIn(inFade, initialScale = 1.06f))
                            .togetherWith(slideOutHorizontally(outSlide) { -it * dir / 4 } + fadeOut(outFade))
                    },
                    label = "posterTrack",
                ) { trackId ->
                    androidx.compose.runtime.key(trackId) {
                    Box(Modifier.fillMaxSize().coverFlightHide(flight)) {
                        // С визуализатором обложка остаётся на месте, но гаснет до фона, а столбики
                        // встают поверх неё в свободной части между шапкой и управлением.
                        // Размытие нарастает книзу: под резкой обложкой лежит её мягкая копия, а резкая
                        // плавно тает раньше, чем копия. Получается переход «резко → мягко → фон» без шва.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PlainCover(song.thumbnailUrl, Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }.blur(18.dp))
                        }
                        PlainCover(
                            song.thumbnailUrl,
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = coverAlpha
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        // Hairline New: обложка держится резкой дольше — как постер, а не как фон.
                                        val f = (fade.toPx() * (if (island) 1.5f else 3.2f) / size.height).coerceIn(0.15f, 0.85f)
                                        drawRect(Brush.verticalGradient(*smoothFade(1f - f, 1f - f * 0.3f)), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                                    }
                                },
                        )
                        if (visualizerOn) {
                            Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = stageTop, bottom = stageBottom)) {
                                stage(song.thumbnailUrl)
                            }
                        } else {
                            stage(song.thumbnailUrl)
                        }
                    }
                
                    }
                }
                // Вуаль под шапкой: иконки читаются на любой обложке, даже на белой.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Brush.verticalGradient(*smoothFade(0f, 1f, from = 0.55f))),
                )
            }
            // Зерно поверх всего постера — матовая фактура, как у островов.
            if (island) Box(Modifier.matchParentSize().drawBehind { drawGrain(0.7f) })
            }
            // Метки обреза у верхних углов обложки, как на печатном постере. На паузе сходятся к ней.
            if (island && !visualizerOn) {
                val markColor = colors.text.copy(alpha = 0.7f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 70.dp)
                        .height(14.dp)
                        .hairEnter(1)
                        .drawBehind {
                            val inset = 14.dp.toPx() + 4.dp.toPx() * rest
                            val arm = 13.dp.toPx()
                            val w = 1.dp.toPx()
                            drawLine(markColor, Offset(inset, 0f), Offset(inset + arm, 0f), w)
                            drawLine(markColor, Offset(inset, 0f), Offset(inset, arm), w)
                            drawLine(markColor, Offset(size.width - inset, 0f), Offset(size.width - inset - arm, 0f), w)
                            drawLine(markColor, Offset(size.width - inset, 0f), Offset(size.width - inset, arm), w)
                        },
                )
            }
            if (island) {
                CompositionLocalProvider(com.texfi.w0y.ui.theme.LocalIslandBackdrop provides posterBackdrop) {
                    IslandPosterControls(song, number, positionMs, durationMs, soundLine, onSoundLine, header, titleBlock, actions, transport, plate, onDrag, onDragEnd, posterBackdrop, { controlsPx = it })
                }
            } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Box(Modifier.padding(horizontal = 20.dp).hairEnter(1)) { header() }
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { controlsPx = it.height }
                        .padding(horizontal = 20.dp),
                ) {
                    // Короткая черта прогресса над названием — подпись постера; заполняется по треку.
                    Box(Modifier.padding(bottom = 12.dp).width(32.dp).height(2.dp).background(colors.outline).hairEnter(2)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(tone))
                    }
                    Box(Modifier.hairEnter(2)) { titleBlock(Modifier.fillMaxWidth()) }
                    Box(Modifier.padding(top = 6.dp, bottom = 2.dp).hairEnter(3).fillMaxWidth()) { actions() }
                    Column(Modifier.hairEnter(4)) {
                        HairSeekLine(positionMs = positionMs, durationMs = durationMs, onDrag = onDrag, onDragEnd = onDragEnd)
                        Row(Modifier.fillMaxWidth()) {
                            Text(HairMath.formatTime(positionMs), style = HairLabelStyle, color = tone)
                            Spacer(Modifier.weight(1f))
                            Text(HairMath.formatTime(durationMs), style = HairLabelStyle, color = colors.textMuted)
                        }
                    }
                    Box(Modifier.padding(vertical = 4.dp).hairEnter(5)) { transport() }
                    Box(Modifier.padding(top = 4.dp, bottom = 8.dp).hairEnter(6)) { plate() }
                }
            }
            }
        }
    }
}

/**
 * Низ постера Hairline New, по макету: шапка из трёх островов; подпись «№ 11 —— альбом»,
 * которая прочерчивается у каждого трека; название с лайком; линейка, время и строка звука;
 * тихий ряд действий; кнопки в матовом острове; «Текст · Очередь · Похожее».
 */
@Composable
private fun IslandPosterControls(
    song: SongItem,
    number: Int?,
    positionMs: Long,
    durationMs: Long,
    soundLine: String?,
    onSoundLine: () -> Unit,
    header: @Composable () -> Unit,
    titleBlock: @Composable (Modifier) -> Unit,
    actions: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    plate: @Composable () -> Unit,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
    onControlsHeight: (Int) -> Unit,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.hairEnter(1)) { header() }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { onControlsHeight(it.height) }
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).hairEnter(2), verticalAlignment = Alignment.CenterVertically) {
                if (number != null) {
                    Text("№ $number", style = HairLabelStyle, color = colors.text, maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                }
                // Линия прочерчивается заново у каждого трека — слева направо, за 360 мс.
                val draw = remember(song.id) { androidx.compose.animation.core.Animatable(if (look.motion.draws) 0f else 1f) }
                androidx.compose.runtime.LaunchedEffect(song.id) { if (draw.value < 1f) draw.animateTo(1f, tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut)) }
                val lineInk = colors.text.copy(alpha = 0.22f)
                Box(Modifier.weight(1f).height(1.dp).drawBehind { drawLine(lineInk, Offset(0f, size.height / 2f), Offset(size.width * draw.value, size.height / 2f), size.height) })
                val album = song.album?.takeIf { al -> al.any(Char::isLetterOrDigit) }
                if (album != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(album.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                }
            }
            Box(Modifier.hairEnter(2)) { titleBlock(Modifier.fillMaxWidth()) }
            Column(Modifier.padding(top = 10.dp).hairEnter(3)) {
                HairSeekLine(positionMs = positionMs, durationMs = durationMs, onDrag = onDrag, onDragEnd = onDragEnd)
                Row(Modifier.fillMaxWidth().offset(y = (-6).dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(HairMath.formatTime(positionMs), style = HairLabelStyle, color = colors.text.copy(alpha = 0.8f))
                    Spacer(Modifier.weight(1f))
                    if (soundLine != null) {
                        Text(soundLine.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1, modifier = Modifier.clickable(onClick = onSoundLine).padding(horizontal = 8.dp, vertical = 4.dp))
                        Spacer(Modifier.weight(1f))
                    }
                    Text(HairMath.formatTime(durationMs), style = HairLabelStyle, color = colors.text.copy(alpha = 0.8f))
                }
            }
            Box(Modifier.fillMaxWidth().hairEnter(4)) { actions() }
            Box(
                Modifier
                    .padding(top = 4.dp, bottom = 6.dp)
                    .hairEnter(5)
                    .matte(androidx.compose.foundation.shape.RoundedCornerShape(50), backdrop)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) { transport() }
            Box(Modifier.hairEnter(6)) { plate() }
        }
    }
}

private const val SWIPE_PX = 120f

/**
 * Остановки градиента «чёрный → прозрачный» по плавной кривой (smoothstep) между [start] и
 * [end]: у линейного перехода видны края зоны, у плавного — нет. [from] — непрозрачность в начале.
 */
internal fun smoothFade(start: Float, end: Float, from: Float = 1f, steps: Int = 10): Array<Pair<Float, Color>> {
    val out = ArrayList<Pair<Float, Color>>(steps + 2)
    if (start > 0f) out += 0f to Color.Black.copy(alpha = from)
    for (i in 0..steps) {
        val k = i.toFloat() / steps
        val eased = k * k * (3f - 2f * k)
        out += (start + (end - start) * k).coerceIn(0f, 1f) to Color.Black.copy(alpha = from * (1f - eased))
    }
    if (end < 1f) out += 1f to Color.Transparent
    return out.toTypedArray()
}
