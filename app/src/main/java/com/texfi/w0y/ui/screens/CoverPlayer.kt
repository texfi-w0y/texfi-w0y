package com.texfi.w0y.ui.screens

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
) {
    val tapNow = androidx.compose.runtime.rememberUpdatedState(onCoverTap)
    if (poster) {
        PosterPlayerPage(song, isPlaying, positionMs, durationMs, stage, header, titleBlock, actions, transport, plate, { tapNow.value() }, onSwipe, onDrag, onDragEnd, visualizerOn, coverShown)
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
                            .coverFlightPlayer(flight, enabled = coverShown, insetDp = 6.dp, radiusDp = radius)
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
        BoxWithConstraints(Modifier.fillMaxSize().dragToCollapse()) {
            PlayerBlurBackdrop(song.thumbnailUrl, isPlaying)
            // Обложка заходит под управление на величину растворения, но не выше квадрата.
            val fade = 96.dp
            val controls = with(density) { controlsPx.toDp() }
            val coverH = if (controlsPx == 0) maxWidth else (maxHeight - controls + fade).coerceIn(maxWidth * 0.6f, maxWidth * 1.15f)
            val flight = com.texfi.w0y.ui.components.LocalCoverFlight.current
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(coverH)
                    // Место постера для полёта: до входной анимации и «паузы» — они идут слоями ниже.
                    .coverFlightPlayer(flight, enabled = coverShown && !visualizerOn, fadeDp = fade)
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
                        val f = (fade.toPx() / size.height).coerceIn(0.05f, 0.6f)
                        drawRect(
                            Brush.verticalGradient(0f to Color.Black, (1f - f) to Color.Black, 1f to Color.Transparent),
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
                            PlainCover(song.thumbnailUrl, Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha })
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
                        .height(140.dp)
                        .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent)),
                )
            }
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

private const val SWIPE_PX = 120f
