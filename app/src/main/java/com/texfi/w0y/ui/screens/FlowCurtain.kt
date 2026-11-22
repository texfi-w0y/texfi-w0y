package com.texfi.w0y.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.recs.FlowGenre
import com.texfi.w0y.recs.FlowMood
import com.texfi.w0y.ui.components.Buzz
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.LocalBottomInset
import com.texfi.w0y.ui.components.LocalSheetMagnet
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSlider
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.components.magnetPx
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.components.rememberHaptics
import com.texfi.w0y.ui.components.rememberTapHaptic
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.GeologicaFamily
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalLiteGraphics
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.drawGrain
import com.texfi.w0y.ui.theme.hairLook
import com.texfi.w0y.ui.theme.matte
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Что показывает раскрытая шторка: после первого прорыва — вылет и приветствие, дальше сразу настройки. */
private enum class FlowStage { BURST, WELCOME, PANEL }

/**
 * fl0w — шторка сверху в поиске Hairline New. Тянешь поиск вниз — сверху съезжает fl0w; тянешь
 * её край вверх — она уходит обратно. Тяга магнитная, как у панелей: держит, щёлкает, отрывается.
 *
 * Первый раз вместо ручки висит «потяни меня»: тянется туго, как плёнка, натяжение растёт, у
 * предела дрожит — и рвётся. Шторка вырывается сама, свои обложки летят на зрителя, за ними
 * приветствие. Дальше вступления нет, шторка открывается сразу.
 *
 * Поиск под раскрытой шторкой размыт и притушен — это тот же матовый слой, что у островов.
 */
@Composable
internal fun FlowCurtain(content: @Composable () -> Unit) {
    val vm: FlowViewModel = hiltViewModel()
    val introSeen by vm.introSeen.collectAsStateWithLifecycle()
    val covers by vm.covers.collectAsStateWithLifecycle()
    val russian = LocalConfiguration.current.locales[0].language in RUSSIAN_SEARCH
    val colors = LocalW0yColors.current
    val density = LocalDensity.current
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val magnetOn = LocalSheetMagnet.current
    val moves = hairLook.motion.moves
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalLiteGraphics.current
    val first = introSeen == false
    LaunchedEffect(first) { if (first) vm.loadCovers(russian) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        val pullAt = with(density) { PULL_AT.toPx() }
        val introBreak = with(density) { INTRO_BREAK.toPx() }
        val introHold = with(density) { INTRO_HOLD.toPx() }
        val tick = with(density) { INTRO_TICK.toPx() }

        var open by rememberSaveable { mutableStateOf(false) }
        var stage by rememberSaveable { mutableStateOf(FlowStage.PANEL) }
        val shown = remember { Animatable(if (open) h else 0f) }
        LaunchedEffect(h) { if (open && !shown.isRunning) shown.snapTo(h) }
        // Пока палец на экране, шторка стоит здесь, а не в анимации: без корутины на каждый кадр.
        var live by remember { mutableStateOf<Float?>(null) }
        val drag = remember { floatArrayOf(0f) }
        val past = remember { booleanArrayOf(false) }
        // Вступление уже прорвалось, а палец ещё тянет: остаток жеста ничего не двигает.
        val spent = remember { booleanArrayOf(false) }
        var tension by remember { mutableFloatStateOf(0f) }
        var lastTick by remember { mutableIntStateOf(0) }
        val burst = remember { Animatable(0f) }
        val settle = spring<Float>(HairTokens.SPRING_DAMPING, HairTokens.SPRING_STIFFNESS)
        // Прервали на вылете (поворот, система выгрузила экран) — продолжаем с приветствия.
        LaunchedEffect(Unit) { if (stage == FlowStage.BURST) stage = FlowStage.WELCOME }

        fun breakFree() {
            spent[0] = true
            haptic(Buzz.EXPAND)
            vm.markIntroSeen()
            val from = live ?: shown.value
            live = null
            open = true
            tension = 0f
            stage = if (moves) FlowStage.BURST else FlowStage.WELCOME
            scope.launch {
                shown.snapTo(from)
                // Вырывается не рывком: плавно, чтобы было видно, как шторка уходит вниз.
                shown.animateTo(h, tween(BREAK_OPEN_MS, easing = HairTokens.EaseOut))
            }
            if (moves) {
                scope.launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
                    // Обложки улетели — приветствие поднимается на чистом фоне.
                    stage = FlowStage.WELCOME
                }
            }
        }

        fun pullBy(dy: Float) {
            // Пока не прочитано, было ли вступление, шторку не трогаем: иначе первый раз прошёл бы без него.
            if (spent[0] || introSeen == null) return
            if (open) {
                // Раскрыта — тянут только вверх; тот же магнит, в обратную сторону.
                drag[0] = (drag[0] + dy).coerceAtMost(0f)
                val d = drag[0]
                val isPast = -d >= pullAt
                if (magnetOn && isPast != past[0]) {
                    past[0] = isPast
                    haptic(if (isPast) Buzz.TAP else Buzz.OFF)
                }
                live = (h + if (magnetOn) magnetPx(d, pullAt) else d).coerceIn(0f, h)
                return
            }
            drag[0] = (drag[0] + dy).coerceAtLeast(0f)
            val d = drag[0]
            if (first) {
                // Плёнка: ход растёт всё медленнее, к пределу почти стоит — и рвётся.
                tension = (d / introBreak).coerceIn(0f, 1f)
                live = introHold * (1f - exp(-d / (introBreak * 0.5f)))
                val step = (d / tick).toInt()
                if (step != lastTick) {
                    lastTick = step
                    if (step > 0) haptic(Buzz.TAP)
                }
                if (d >= introBreak) breakFree()
                return
            }
            val isPast = d >= pullAt
            if (magnetOn && isPast != past[0]) {
                past[0] = isPast
                haptic(if (isPast) Buzz.TAP else Buzz.OFF)
            }
            live = (if (magnetOn) magnetPx(d, pullAt) else d).coerceAtMost(h)
        }

        fun close() {
            open = false
            stage = FlowStage.PANEL
            haptic(Buzz.MINIMIZE)
            scope.launch { shown.animateTo(0f, settle) }
        }

        fun release(velocity: Float) {
            val d = drag[0]
            drag[0] = 0f
            past[0] = false
            lastTick = 0
            if (spent[0]) {
                spent[0] = false
                return
            }
            val from = live ?: return
            live = null
            scope.launch {
                shown.snapTo(from)
                if (!open) {
                    if (first) {
                        // Не дотянул — плёнка отскакивает пружиной.
                        tension = 0f
                        shown.animateTo(0f, spring(0.42f, 520f))
                        return@launch
                    }
                    val go = (if (magnetOn) d >= pullAt else d >= pullAt * 0.6f) || velocity > FLING
                    if (go) {
                        open = true
                        stage = FlowStage.PANEL
                        haptic(Buzz.EXPAND)
                        shown.animateTo(h, settle)
                    } else {
                        shown.animateTo(0f, settle)
                    }
                } else {
                    if (-d >= pullAt || velocity < -FLING) close() else shown.animateTo(h, settle)
                }
            }
        }

        /** Нажали «fl0w» в заголовке: открыть; первый раз — только качнуть плёнку, её надо вытянуть. */
        fun tapEntry() {
            if (introSeen == null || open || live != null) return
            if (first) {
                haptic(Buzz.TAP)
                scope.launch {
                    tension = 0.25f
                    shown.animateTo(introHold * 0.35f, tween(HairTokens.MID_MS, easing = HairTokens.EaseOut))
                    shown.animateTo(0f, spring(0.42f, 520f))
                    tension = 0f
                }
                return
            }
            open = true
            stage = FlowStage.PANEL
            haptic(Buzz.EXPAND)
            scope.launch { shown.animateTo(h, settle) }
        }

        BackHandler(enabled = open) { close() }

        // Тяга из прокрутки: выдача упёрлась в верх — дальше тянется шторка; у раскрытой шторки
        // так же уходит вверх то, что упёрлось в низ.
        val pullConnection =
            remember(open) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput || live == null) return Offset.Zero
                        // Шторку уже тянут — обратный ход пальца сперва возвращает её, а не листает.
                        val back = if (open) available.y.coerceAtLeast(0f).coerceAtMost(-drag[0]) else available.y.coerceAtMost(0f).coerceAtLeast(-drag[0])
                        if (back == 0f) return Offset.Zero
                        pullBy(back)
                        return Offset(0f, back)
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput) return Offset.Zero
                        val y = available.y
                        if ((!open && y > 0f) || (open && y < 0f)) {
                            pullBy(y)
                            return Offset(0f, y)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (live == null && !spent[0]) return Velocity.Zero
                        release(available.y)
                        return available
                    }
                }
            }
        val dragState = rememberDraggableState { pullBy(it) }

        val y = live ?: shown.value
        // Один и тот же объект, пока не сменилось «первый раз / тянут»: заголовок поиска не пересобирается на каждый кадр тяги.
        val pulling = first && y > 0.5f
        val entry = remember(first, pulling, h) { FlowEntry(first = first, pulling = pulling, onTap = ::tapEntry) }
        val p = (y / h).coerceIn(0f, 1f)

        // Поиск: под шторкой уходит чуть вниз, тускнеет и размывается.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val q = ((live ?: shown.value) / h).coerceIn(0f, 1f)
                    translationY = q * 0.06f * h
                    // Раскрыта — поиска не видно совсем: под fl0w только свой фон.
                    alpha = 1f - q
                    renderEffect = if (blurs && q > 0.01f) BlurEffect(q * BLUR.toPx(), q * BLUR.toPx(), TileMode.Decal) else null
                }
                .then(if (!open) Modifier.nestedScroll(pullConnection) else Modifier)
                .draggable(dragState, Orientation.Vertical, enabled = !open, onDragStopped = { release(it) }),
        ) {
            CompositionLocalProvider(LocalFlowEntry provides entry) { content() }
        }

        // Плёнка вступления: тянется под пальцем, у кончика — «потяни меня».
        if (first && !open && y > 0.5f) {
            val edge = colors.text.copy(alpha = 0.34f)
            val fill = colors.background
            Canvas(Modifier.fillMaxSize()) {
                val e = y * 0.32f
                val tipX = size.width / 2f
                val path =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, e)
                        quadraticTo(2f * tipX - size.width / 2f, 2f * y - e, 0f, e)
                        close()
                    }
                // Плёнка полупрозрачная, с зерном: сквозь неё виден свой фон.
                drawPath(path, fill.copy(alpha = 0.4f))
                clipPath(path) { drawGrain(0.8f) }
                drawPath(path, edge, style = Stroke(width = 1.dp.toPx()))
            }
            IntroHandle(
                tension = tension,
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { translationY = y - 18.dp.toPx() },
            )
        }


        // Шторка: съезжает сверху, низ — её край с ручкой.
        if (open || (!first && y > 0.5f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = (live ?: shown.value) - h }
                    // Своей заливки нет: под шторкой фон, выбранный в настройках, сверху только зерно.
                    .drawBehind { drawGrain(0.6f) }
                    .nestedScroll(pullConnection)
                    .draggable(dragState, Orientation.Vertical, enabled = open, onDragStopped = { release(it) }),
            ) {
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = { (fadeIn(tween(HairTokens.MID_MS)) + scaleIn(tween(HairTokens.MID_MS, easing = HairTokens.EaseOut), initialScale = 0.96f)) togetherWith fadeOut(tween(HairTokens.SHORT_MS)) },
                    label = "flowStage",
                    modifier = Modifier.fillMaxSize(),
                ) { s ->
                    when (s) {
                        FlowStage.BURST -> Box(Modifier.fillMaxSize())
                        FlowStage.WELCOME -> FlowWelcome(onNext = { stage = FlowStage.PANEL })
                        FlowStage.PANEL -> FlowPanel(vm, russian, onClose = { close() })
                    }
                }
                // Край — своя ручка: под ним прокрутка настроек, и без этого она забирала бы жест себе.
                CurtainEdge(Modifier.align(Alignment.BottomCenter).draggable(dragState, Orientation.Vertical, enabled = open, onDragStopped = { release(it) }))
            }
        }
        // Свои обложки летят на зрителя, когда плёнка прорвалась.
        if (stage == FlowStage.BURST) FlowBurst(covers, t = burst.value)
    }
}

/** Что заголовок поиска знает о шторке: первый ли раз и что делать по нажатию на «fl0w». */
internal class FlowEntry(val first: Boolean, val pulling: Boolean, val onTap: () -> Unit)

internal val LocalFlowEntry = compositionLocalOf<FlowEntry?> { null }

/**
 * Вход в fl0w — в заголовке поиска, на виду: матовая пилюля со стрелкой вниз, стрелка мягко
 * покачивается — понятно, что тянуть вниз. Первый раз вместо имени — «потяни меня».
 */
@Composable
internal fun FlowEntryPill() {
    val entry = LocalFlowEntry.current ?: return
    val colors = LocalW0yColors.current
    val moves = hairLook.motion.moves
    val interaction = remember { MutableInteractionSource() }
    val loop = rememberInfiniteTransition(label = "flowEntry")
    val bob by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(BOB_MS, easing = HairTokens.EaseOut), RepeatMode.Reverse), label = "entryBob")
    val label = stringResource(if (entry.first) R.string.flow_pull_me else R.string.flow_name)
    Row(
        Modifier
            // Плёнку тянут — «потяни меня» висит на её кончике, второй в заголовке не нужен.
            .graphicsLayer { alpha = if (entry.pulling) 0f else 1f }
            .pressScale(interaction, pressed = 0.94f)
            .matte(RoundedCornerShape(50), alpha = 0.55f)
            .clickable(interactionSource = interaction, indication = null, onClick = entry.onTap)
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HairIcon(
            HairGlyph.COLLAPSE,
            colors.text,
            Modifier.size(16.dp).graphicsLayer { if (moves) translationY = (bob - 0.5f) * 4.dp.toPx() },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (entry.first) label.asLabel() else label,
            style = if (entry.first) HairLabelStyle else MaterialTheme.typography.titleMedium.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            color = colors.text,
            maxLines = 1,
        )
    }
}

/**
 * «Потяни меня»: висит под верхом и покачивается. Под тягой буквы расходятся, как растянутые, а
 * у самого предела подпись мелко дрожит.
 */
@Composable
private fun IntroHandle(tension: Float, modifier: Modifier) {
    val colors = LocalW0yColors.current
    val moves = hairLook.motion.moves
    val loop = rememberInfiniteTransition(label = "pullMe")
    val bob by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(BOB_MS, easing = HairTokens.EaseOut), RepeatMode.Reverse), label = "bob")
    val shake by loop.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(SHAKE_MS, easing = LinearEasing)), label = "shake")
    val tone = Contrast.ensure(colors.accent, colors.background)
    Row(
        modifier
            .graphicsLayer {
                if (moves) {
                    translationY += if (tension == 0f) bob * 6.dp.toPx() else 0f
                    val tremble = ((tension - 0.65f) / 0.35f).coerceIn(0f, 1f)
                    translationX = sin(shake * 7f) * tremble * 2.5.dp.toPx()
                    scaleX = 1f + tension * 0.12f
                    scaleY = 1f - tension * 0.06f
                }
            }
            .padding(top = 6.dp)
            .matte(RoundedCornerShape(50), alpha = 0.7f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HairIcon(HairGlyph.COLLAPSE, tone, Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.flow_pull_me).asLabel(),
            style = HairLabelStyle.copy(letterSpacing = (1.2f + tension * 3f).sp),
            color = colors.text,
            maxLines = 1,
        )
    }
}

/** Край раскрытой шторки: линия и ручка «к поиску» — за неё и тянут вверх. */
@Composable
private fun CurtainEdge(modifier: Modifier) {
    val colors = LocalW0yColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = LocalBottomInset.current)
            .height(EDGE_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HairIcon(HairGlyph.EXPAND, colors.textMuted, Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.flow_back).asLabel(), style = HairLabelStyle.copy(fontSize = 10.sp), color = colors.textMuted)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(36.dp).height(3.dp).drawBehind { drawRoundRect(colors.text.copy(alpha = 0.28f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height)) })
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(colors.text.copy(alpha = 0.12f)) })
    }
}

/**
 * Вылет обложек на зрителя. [t] 0..1: каждая обложка идёт из глубины у центра к краям и растёт,
 * будто пролетает мимо камеры. Без вспышек и частиц — только сами обложки.
 */
@Composable
private fun FlowBurst(covers: List<String>, t: Float) {
    if (covers.isEmpty()) return
    val tiles = remember(covers) { covers.take(BURST_TILES).mapIndexed { i, url -> BurstTile(url, i) } }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val span = minOf(constraints.maxWidth, constraints.maxHeight).toFloat()
        tiles.forEach { tile ->
            val local = ((t - tile.delay) / (1f - BURST_STAGGER)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            CoverImage(
                url = tile.url,
                px = Thumbnails.TILE,
                modifier =
                    Modifier
                        .size(TILE)
                        .graphicsLayer {
                            val z = local.pow(1.7f)
                            val reach = span * (0.06f + 0.95f * z) * tile.reach
                            translationX = cos(tile.angle) * reach
                            translationY = sin(tile.angle) * reach
                            val s = 0.16f + 2.6f * z
                            scaleX = s
                            scaleY = s
                            rotationY = cos(tile.angle) * 28f * z
                            rotationX = -sin(tile.angle) * 28f * z
                            cameraDistance = 10f * density
                            alpha =
                                when {
                                    local < 0.12f -> local / 0.12f
                                    local > 0.72f -> ((1f - local) / 0.28f).coerceIn(0f, 1f)
                                    else -> 1f
                                }
                        },
            )
        }
    }
}

private class BurstTile(val url: String, index: Int) {
    /** Золотой угол: обложки ложатся по кругу без кучек и без ровного «циферблата». */
    val angle = (index * 2.39996f) % (2f * PI.toFloat())
    val reach = 0.55f + ((index * 37) % 45) / 100f
    val delay = (index % 6) * (BURST_STAGGER / 5f)
}

/** Приветствие после первого прорыва: имя, одна фраза, «дальше». */
@Composable
private fun FlowWelcome(onNext: () -> Unit) {
    val colors = LocalW0yColors.current
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .matte(RoundedCornerShape(24.dp), alpha = 0.62f)
                .padding(24.dp),
        ) {
            Text(
                stringResource(R.string.flow_name),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, letterSpacing = (-1.2).sp),
                color = colors.text,
                modifier = Modifier.hairEnter(0),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.flow_welcome_text),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
                modifier = Modifier.hairEnter(1),
            )
            Spacer(Modifier.height(22.dp))
            PixelButton(
                text = stringResource(R.string.flow_welcome_go),
                onClick = onNext,
                fill = colors.accent,
                modifier = Modifier.fillMaxWidth().hairEnter(2),
            )
        }
    }
}

/**
 * fl0w раскрыт: имя, настроение словами, огромная кнопка «играть», жанры и «своё ↔ новое».
 * Ни плиток, ни карточек: выбранное слово светлее и подчёркнуто линией в 1 dp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowPanel(vm: FlowViewModel, russian: Boolean, onClose: () -> Unit) {
    val colors = LocalW0yColors.current
    val choice by vm.choice.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val own by vm.own.collectAsStateWithLifecycle()
    val tap = rememberTapHaptic()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.padding(top = 18.dp).hairEnter(0), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.flow_name),
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.8).sp),
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                val closeLabel = stringResource(R.string.flow_cd_close)
                Box(
                    Modifier
                        .size(40.dp)
                        .border(1.dp, colors.text.copy(alpha = 0.18f), CircleShape)
                        .clickable { tap(); onClose() }
                        .semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    HairIcon(HairGlyph.EXPAND, colors.text, Modifier.size(16.dp))
                }
            }

            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FlowRow(
                    Modifier.fillMaxWidth().hairEnter(1),
                    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Word(stringResource(R.string.flow_any), choice.mood == null, big = true) { tap(); vm.setMood(null) }
                    FlowMood.entries.forEach { mood ->
                        Word(stringResource(mood.label()), choice.mood == mood, big = true) { tap(); vm.setMood(mood) }
                    }
                }
                Spacer(Modifier.height(36.dp))
                Box(Modifier.hairEnter(2), contentAlignment = Alignment.Center) {
                    if (result == FlowResult.Loading) BuildingRing(Modifier.size(PLAY + 16.dp))
                    com.texfi.w0y.ui.components.HairPlayButton(playing = own.second, onClick = { vm.onPlay(russian) }, size = PLAY)
                }
                Spacer(Modifier.height(36.dp))
                FlowRow(
                    Modifier.fillMaxWidth().hairEnter(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowGenre.entries.forEach { genre ->
                        Word(stringResource(genre.label()), genre in choice.genres, big = false) { tap(); vm.toggleGenre(genre) }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.widthIn(max = 340.dp).fillMaxWidth().hairEnter(4), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.flow_mix_mine).asLabel(), style = HairLabelStyle.copy(fontSize = 10.sp), color = colors.textMuted)
                    Spacer(Modifier.width(12.dp))
                    PixelSlider(value = choice.novelty.toFloat(), range = 0f..100f, step = 5f, onValueChange = { vm.setNovelty(it.toInt()) }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.flow_mix_new).asLabel(), style = HairLabelStyle.copy(fontSize = 10.sp), color = colors.textMuted)
                }
            }

            val status =
                when (val r = result) {
                    FlowResult.Idle -> ""
                    FlowResult.Loading -> stringResource(R.string.flow_building)
                    FlowResult.Empty -> stringResource(R.string.flow_empty)
                    is FlowResult.Ready -> if (r.set.offline) stringResource(R.string.flow_offline) else stringResource(R.string.flow_queued, r.set.items.size)
                }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = LocalBottomInset.current + EDGE_HEIGHT + 8.dp),
            )
        }
    }
}

/** Слово выбора: выбранное — основной цвет и линия под ним, остальные приглушены. */
@Composable
private fun Word(text: String, on: Boolean, big: Boolean, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val ink by animateColorAsState(if (on) colors.text else colors.text.copy(alpha = if (big) 0.36f else 0.42f), hairSpec(HairTokens.SHORT_MS), label = "wordInk")
    val line by animateFloatAsState(if (on) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "wordLine")
    val style =
        if (big) {
            MaterialTheme.typography.headlineSmall.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.3).sp)
        } else {
            HairLabelStyle
        }
    Text(
        if (big) text else text.asLabel(),
        style = style,
        color = ink,
        maxLines = 1,
        modifier =
            Modifier
                .pressScale(interaction, pressed = 0.94f)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(vertical = 4.dp)
                .drawBehind {
                    if (line > 0f) {
                        // Линия растёт от середины слова.
                        val w = size.width * line
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(ink, Offset((size.width - w) / 2f, y), Offset((size.width + w) / 2f, y), 1.dp.toPx())
                    }
                },
    )
}

/** Пока собирается: тонкая дуга бежит вокруг кнопки. */
@Composable
private fun BuildingRing(modifier: Modifier) {
    val colors = LocalW0yColors.current
    val loop = rememberInfiniteTransition(label = "flowRing")
    val turn by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(RING_MS, easing = LinearEasing)), label = "ringTurn")
    Canvas(modifier) {
        drawArc(colors.text.copy(alpha = 0.5f), turn, 70f, useCenter = false, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun FlowMood.label(): Int =
    when (this) {
        FlowMood.CALM -> R.string.flow_mood_calm
        FlowMood.SAD -> R.string.flow_mood_sad
        FlowMood.DRIVE -> R.string.flow_mood_drive
        FlowMood.ANGRY -> R.string.flow_mood_angry
        FlowMood.LOVE -> R.string.flow_mood_love
        FlowMood.FOCUS -> R.string.flow_mood_focus
        FlowMood.NIGHT -> R.string.flow_mood_night
        FlowMood.PARTY -> R.string.flow_mood_party
    }

private fun FlowGenre.label(): Int =
    when (this) {
        FlowGenre.RAP -> R.string.flow_genre_rap
        FlowGenre.POP -> R.string.flow_genre_pop
        FlowGenre.ROCK -> R.string.flow_genre_rock
        FlowGenre.ELECTRONIC -> R.string.flow_genre_electronic
        FlowGenre.PHONK -> R.string.flow_genre_phonk
        FlowGenre.INDIE -> R.string.flow_genre_indie
        FlowGenre.RNB -> R.string.flow_genre_rnb
        FlowGenre.LOFI -> R.string.flow_genre_lofi
        FlowGenre.METAL -> R.string.flow_genre_metal
        FlowGenre.JAZZ -> R.string.flow_genre_jazz
    }

/** Языки, на которых настроение ищется по-русски: выдача YouTube у них та же. */
private val RUSSIAN_SEARCH = setOf("ru", "uk", "be")

/** Магнит обычной тяги — как у шторки плеера. */
private val PULL_AT = 96.dp

/** Вступление: где плёнка рвётся, сколько она проходит до разрыва и шаг щелчков натяжения. */
private val INTRO_BREAK = 260.dp
private val INTRO_HOLD = 150.dp
private val INTRO_TICK = 44.dp
/** Бросок, px/с: быстрее — шторка уходит, даже не дотянув до магнита. */
private const val FLING = 1600f
private val BLUR = 18.dp
private val EDGE_HEIGHT = 64.dp
private val TILE = 104.dp
/** Вылет обложек — медленно, чтобы успеть их разглядеть. */
private const val BURST_MS = 2400
private const val BREAK_OPEN_MS = 700
private const val RING_MS = 1100
private val PLAY = 168.dp
private const val BURST_TILES = 18
private const val BURST_STAGGER = 0.36f
private const val BOB_MS = 900
private const val SHAKE_MS = 700
