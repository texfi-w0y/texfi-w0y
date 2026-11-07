package com.texfi.w0y.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Загрузчик Hairline: кольцо из тонких делений и яркая «комета», которая
 * бежит по нему с шлейфом. Угол читается при рисовании — перекомпоновок нет.
 * Без анимаций (режим «Без движения») — неподвижное кольцо с головой наверху.
 */
@Composable
fun HairLoader(modifier: Modifier = Modifier, color: Color? = null) {
    val colors = LocalW0yColors.current
    val tone = color ?: Contrast.ensure(colors.accent, colors.background)
    val moves = hairLook.motion.moves
    val spin = rememberInfiniteTransition(label = "hairLoader").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOADER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "loaderAngle",
    )
    val line = hairLook.line
    Canvas(modifier.size(40.dp)) {
        val head = if (moves) spin.value else 0f
        val r = size.minDimension / 2f - 2.dp.toPx()
        val inner = r - 7.dp.toPx()
        val w = line.toPx() * 1.4f
        repeat(TICKS) { i ->
            val f = i.toFloat() / TICKS
            // Расстояние позади головы по ходу вращения: 0 — сама голова.
            val behind = ((head - f) % 1f + 1f) % 1f
            val a = (1f - behind * 2.2f).coerceAtLeast(0.14f)
            val ang = (f * 2f * PI - PI / 2).toFloat()
            val c = center
            drawLine(
                color = (if (behind < 0.06f) tone else colors.text).copy(alpha = a),
                start = Offset(c.x + cos(ang) * inner, c.y + sin(ang) * inner),
                end = Offset(c.x + cos(ang) * r, c.y + sin(ang) * r),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Заставка Hairline: от центра расходится тонкая линия, по ней одной линией рисуется «W»,
 * замыкается кольцо-ноль и проявляется подпись. Потом знак уходит на зрителя, а в фоне
 * раскрывается круглое окно с тонким кольцом по краю — сквозь него уже видно приложение.
 * Заставка не держит касания и не ждёт сети: экран под ней живой с первого кадра.
 * Без анимаций заставки нет вовсе.
 */
@Composable
fun HairIntro(onFinished: () -> Unit) {
    if (com.texfi.w0y.ui.theme.isIsland) {
        IslandIntro(onFinished)
        return
    }
    val colors = LocalW0yColors.current
    val look = hairLook
    val tone = Contrast.ensure(colors.accent, colors.background)
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!look.motion.draws) {
            onFinished()
            return@LaunchedEffect
        }
        t.animateTo(1f, tween(INTRO_MS, easing = LinearEasing))
        onFinished()
    }
    // Доли общей шкалы: каждая часть со своей кривой Hairline.
    fun part(from: Float, to: Float): Float = HairTokens.EaseOut.transform(((t.value - from) / (to - from)).coerceIn(0f, 1f))
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawBehind {
                // Фон с круглым окном: окно растёт из центра до углов экрана, по кромке — кольцо.
                drawRect(colors.background)
                val open = part(0.62f, 1f)
                if (open > 0f) {
                    val maxR = kotlin.math.hypot(size.width / 2f, size.height / 2f)
                    val r = maxR * open
                    drawCircle(Color.Black, r, center, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                    drawCircle(tone.copy(alpha = (1f - open) * 0.9f), r, center, style = Stroke(1.5.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Знак уходит на зрителя и гаснет, пока раскрывается окно.
                    val out = part(0.6f, 0.92f)
                    val s = 1f + 0.22f * out
                    scaleX = s
                    scaleY = s
                    alpha = 1f - out
                },
        ) {
            val side = 180.dp.toPx()
            val k = side / 108f
            val ox = center.x - side / 2f
            val oy = center.y - side / 2f
            // Горизонт: линия расходится от центра и гаснет, когда знак готов.
            val line = part(0f, 0.22f)
            val lineFade = 1f - part(0.4f, 0.6f)
            if (line > 0f && lineFade > 0f) {
                val half = size.width * 0.32f * line
                val y = oy + 54f * k
                drawLine(colors.text.copy(alpha = 0.35f * lineFade), Offset(center.x - half, y), Offset(center.x + half, y), 1.dp.toPx())
            }
            val wPath = Path().apply {
                moveTo(ox + 28f * k, oy + 40f * k)
                lineTo(ox + 41f * k, oy + 74f * k)
                lineTo(ox + 54f * k, oy + 50f * k)
                lineTo(ox + 67f * k, oy + 74f * k)
                lineTo(ox + 80f * k, oy + 40f * k)
            }
            val seg = Path()
            val m = PathMeasure().also { it.setPath(wPath, false) }
            m.getSegment(0f, m.length * part(0.08f, 0.42f), seg, true)
            drawPath(seg, colors.text, style = Stroke(7f * k * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val circle = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(ox + 54f * k - 7.5f * k, oy + 33f * k - 7.5f * k), androidx.compose.ui.geometry.Size(15f * k, 15f * k)))
            }
            val cm = PathMeasure().also { it.setPath(circle, false) }
            val cp = Path()
            cm.getSegment(0f, cm.length * part(0.3f, 0.52f), cp, true)
            drawPath(cp, tone, style = Stroke(3.6f * k * 0.5f, cap = StrokeCap.Round))
            // Засечки по углам знака — как на чертёжном листе; сходятся к нему.
            val ticks = part(0.18f, 0.48f)
            if (ticks > 0f) {
                val pad = 14.dp.toPx() * (2f - ticks)
                val len = 10.dp.toPx() * ticks
                val a = colors.text.copy(alpha = 0.5f * ticks)
                val l = ox + 18f * k - pad
                val r = ox + 90f * k + pad
                val tp = oy + 18f * k - pad
                val bt = oy + 90f * k + pad
                listOf(Offset(l, tp) to Offset(1f, 1f), Offset(r, tp) to Offset(-1f, 1f), Offset(l, bt) to Offset(1f, -1f), Offset(r, bt) to Offset(-1f, -1f)).forEach { (c, d) ->
                    drawLine(a, c, c + Offset(len * d.x, 0f), 1.dp.toPx())
                    drawLine(a, c, c + Offset(0f, len * d.y), 1.dp.toPx())
                }
            }
        }
        // Подпись под знаком: моно, с разрядкой, проявляется снизу.
        val label = part(0.36f, 0.56f)
        androidx.compose.material3.Text(
            text = "W0Y",
            style = com.texfi.w0y.ui.theme.HairLabelStyle.copy(letterSpacing = 6.sp),
            color = colors.textMuted,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(top = 150.dp)
                    .graphicsLayer {
                        alpha = label * (1f - part(0.6f, 0.8f))
                        translationY = (1f - label) * 10.dp.toPx()
                    },
        )
    }
}

private const val LOADER_MS = 1_100
private const val TICKS = 28
private const val INTRO_MS = 1_250

/**
 * Заставка Hairline New по иконке-наброску: пером, одной линией, рисуется «W», над ним по
 * чёрточкам собирается котик — уши, макушка, глаза, нос — и моргает. Снизу печатается «w0y»
 * с квадратиком-курсором. Потом лист сжимается в остров нижней панели, а знак улетает в него
 * и тает; под ним уже живое приложение. Около полутора секунд, только линии, без свечений.
 */
@Composable
private fun IslandIntro(onFinished: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val tone = Contrast.ensure(colors.accent, colors.background)
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!look.motion.draws) {
            onFinished()
            return@LaunchedEffect
        }
        t.animateTo(1f, tween(ISLAND_INTRO_MS, easing = LinearEasing))
        onFinished()
    }
    fun part(from: Float, to: Float): Float = HairTokens.EaseOut.transform(((t.value - from) / (to - from)).coerceIn(0f, 1f))
    val nav = androidx.compose.foundation.layout.WindowInsets.navigationBars
    val density = androidx.compose.ui.platform.LocalDensity.current
    val navPx = nav.getBottom(density).toFloat()
    val fold = { androidx.compose.animation.core.FastOutSlowInEasing.transform(((t.value - 0.6f) / 0.32f).coerceIn(0f, 1f)) }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // Лист → остров: прямоугольник экрана сходится к плашке нижней панели.
                val k = fold()
                val side = 16.dp.toPx()
                val islandH = 84.dp.toPx()
                val bottom = size.height - navPx - 12.dp.toPx()
                val left = side * k
                val right = size.width - side * k
                val top = (bottom - islandH) * k
                val bot = size.height + (bottom - size.height) * k
                val r = 28.dp.toPx() * k
                val fade = 1f - part(0.88f, 1f)
                drawRoundRect(
                    colors.background.copy(alpha = fade),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bot - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
                if (k > 0f) {
                    // Кромка острова — тонкая светлая линия, как у настоящей панели.
                    drawRoundRect(
                        colors.text.copy(alpha = 0.12f * fade * k),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bot - top),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                        style = Stroke(1.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val screenH = with(density) { maxHeight.toPx() }
        // Куда улетает знак: в середину острова.
        val islandCenter = screenH - navPx - with(density) { (12.dp + 42.dp).toPx() }
        androidx.compose.foundation.layout.Column(
            Modifier.graphicsLayer {
                val k = fold()
                translationY = (islandCenter - screenH / 2f) * k
                val sc = 1f - 0.72f * k
                scaleX = sc
                scaleY = sc
                alpha = 1f - part(0.8f, 0.95f)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(MARK_DP.dp)) {
                val u = size.width / MARK_UNITS
                fun pt(x: Float, y: Float) = Offset((x - MARK_CX) * u + size.width / 2f, (y - MARK_CY) * u + size.height / 2f)
                fun pen(points: List<Offset>, progress: Float, width: Float, color: Color) {
                    if (progress <= 0f) return
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val drawn = Path()
                    measure.getSegment(0f, measure.length * progress, drawn, true)
                    drawPath(drawn, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // «W» пером: быстрый росчерк с разгоном и торможением.
                val w = androidx.compose.animation.core.FastOutSlowInEasing.transform(((t.value - 0.02f) / 0.34f).coerceIn(0f, 1f))
                pen(INTRO_W.map { pt(it.x, it.y) }, w, 4.4f * u, colors.text)
                // Котик собирается чёрточками одна за другой.
                CAT_STROKES.forEachIndexed { i, stroke ->
                    val start = 0.3f + i * 0.035f
                    var pts = stroke.map { pt(it.x, it.y) }
                    if (i == 3 || i == 4) {
                        // Глаза: моргание — короткий сплющ к середине и обратно.
                        val blink = ((t.value - 0.5f) / 0.08f).coerceIn(0f, 1f)
                        val squash = 1f - 0.85f * sin(blink * PI.toFloat())
                        val mid = (pts[0].y + pts[1].y) / 2f
                        pts = pts.map { Offset(it.x, mid + (it.y - mid) * squash) }
                    }
                    pen(pts, part(start, start + 0.07f), 1.4f * u, colors.text)
                }
            }
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom) {
                "w0y".forEachIndexed { i, ch ->
                    val p = part(0.36f + i * 0.05f, 0.5f + i * 0.05f)
                    androidx.compose.material3.Text(
                        text = ch.toString(),
                        style =
                            androidx.compose.ui.text.TextStyle(
                                fontFamily = com.texfi.w0y.ui.theme.GeologicaFamily,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                                fontSize = 30.sp,
                                letterSpacing = (-1).sp,
                            ),
                        color = if (ch == '0') tone else colors.text,
                        modifier =
                            Modifier.graphicsLayer {
                                alpha = p
                                translationY = (1f - p) * 10.dp.toPx()
                            },
                    )
                }
                // Квадратик-курсор: встаёт после букв и раз мигает.
                val sq = part(0.5f, 0.56f)
                val blinkOff = t.value in 0.62f..0.68f
                Box(
                    Modifier
                        .padding(start = 4.dp, bottom = 8.dp)
                        .size(6.dp)
                        .graphicsLayer {
                            scaleX = sq
                            scaleY = sq
                            alpha = if (blinkOff) 0f else 1f
                        }
                        .background(colors.text),
                )
            }
        }
    }
}

/** Знак с иконки-наброска в её сетке 108: «W» и котик из чёрточек. */
private val INTRO_W = listOf(Offset(33.5f, 46f), Offset(45.3f, 68.3f), Offset(52.9f, 49f), Offset(59.9f, 62.4f), Offset(76.9f, 39.6f))
private val CAT_STROKES =
    listOf(
        listOf(Offset(38.6f, 46.2f), Offset(40.2f, 41.4f), Offset(42.4f, 44.6f)),
        listOf(Offset(43.4f, 44.3f), Offset(45.4f, 44.3f)),
        listOf(Offset(46.4f, 44.6f), Offset(48.4f, 41.4f), Offset(49.6f, 46.2f)),
        listOf(Offset(41.8f, 47.2f), Offset(41.8f, 48.6f)),
        listOf(Offset(46.6f, 47.2f), Offset(46.6f, 48.6f)),
        listOf(Offset(44.2f, 49.6f), Offset(44.2f, 50.8f)),
        listOf(Offset(43.2f, 51.2f), Offset(45.2f, 51.2f)),
    )
private const val MARK_DP = 168
private const val MARK_UNITS = 50f
private const val MARK_CX = 55.2f
private const val MARK_CY = 54f
private const val ISLAND_INTRO_MS = 1_450
