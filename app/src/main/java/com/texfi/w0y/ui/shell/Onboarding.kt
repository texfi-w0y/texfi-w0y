package com.texfi.w0y.ui.shell

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.BackdropMode
import com.texfi.w0y.ui.components.Buzz
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.components.rememberHaptics
import com.texfi.w0y.ui.components.rememberTapHaptic
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.screens.SettingsViewModel
import com.texfi.w0y.ui.screens.hairEnter
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.GeologicaFamily
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import com.texfi.w0y.ui.theme.matte
import com.texfi.w0y.ui.theme.screenBackground

/** Шаги первого запуска. Стиля среди них нет: по умолчанию Hairline New. */
private enum class Step { HELLO, ACCOUNT, COLOR, BACKDROP }

/**
 * Первый запуск в Hairline New: привет, аккаунт, цвет, фон. Всё на фоне, который выбирается
 * на последнем шаге, — поэтому выбор фона виден сразу, а не в маленьком превью. Сверху тонкая
 * шкала шагов, справа «пропустить»; назад — системным жестом.
 *
 * Цвет и фон пишутся в настройки сразу: приложение перекрашивается под пальцем.
 */
@Composable
fun Onboarding(
    signedIn: Boolean,
    accountName: String?,
    onSignIn: () -> Unit,
    onDone: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val haptic = rememberHaptics()
    var step by rememberSaveable { mutableStateOf(Step.HELLO) }
    var forward by remember { mutableStateOf(true) }
    fun go(to: Step) {
        forward = to.ordinal > step.ordinal
        haptic(Buzz.TAP)
        step = to
    }
    fun next() {
        val all = Step.entries
        if (step == all.last()) {
            haptic(Buzz.DONE)
            onDone()
        } else {
            go(all[step.ordinal + 1])
        }
    }
    BackHandler(enabled = step != Step.HELLO) { go(Step.entries[step.ordinal - 1]) }

    Box(
        Modifier
            .fillMaxSize()
            .screenBackground()
            // Касания не проходят к приложению под первым запуском.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                StepScale(step.ordinal, Step.entries.size, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.welcome_skip).asLabel(),
                    style = HairLabelStyle,
                    color = colors.textMuted,
                    modifier = Modifier.clickable(onClick = onDone).padding(12.dp),
                )
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut)) { it * dir / 4 } + fadeIn(tween(HairTokens.LONG_MS)))
                        .togetherWith(slideOutHorizontally(tween(HairTokens.MID_MS, easing = HairTokens.EaseOut)) { -it * dir / 5 } + fadeOut(tween(HairTokens.SHORT_MS)))
                },
                label = "onboardingStep",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { s ->
                when (s) {
                    Step.HELLO -> Hello(onNext = ::next)
                    Step.ACCOUNT -> Account(signedIn, accountName, onSignIn, onNext = ::next)
                    Step.COLOR -> ColorStep(settingsViewModel, onNext = ::next)
                    Step.BACKDROP -> BackdropStep(settingsViewModel, onNext = ::next)
                }
            }
        }
    }
}

/** Шкала шагов: отрезки в линию, пройденные светлее; текущий заполняется слева направо. */
@Composable
private fun StepScale(current: Int, total: Int, modifier: Modifier) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val shown by animateFloatAsState(current.toFloat(), hairSpec(HairTokens.LONG_MS), label = "stepScale")
    Canvas(modifier.height(12.dp)) {
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (total - 1)) / total
        val y = size.height / 2f
        val line = 1.dp.toPx()
        for (i in 0 until total) {
            val x = i * (w + gap)
            drawLine(colors.text.copy(alpha = 0.16f), Offset(x, y), Offset(x + w, y), line)
            val fill = (shown - i + 1f).coerceIn(0f, 1f)
            if (fill > 0f) drawLine(if (i.toFloat() <= shown - 0.5f && i < current) colors.text.copy(alpha = 0.7f) else tone, Offset(x, y), Offset(x + w * fill, y), line * 1.6f)
        }
    }
}

/** Лист шага: заголовок крупно, одна строка пояснения, своё содержимое, кнопки внизу. */
@Composable
private fun StepPage(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    val colors = LocalW0yColors.current
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, letterSpacing = (-1).sp, lineHeight = 40.sp),
            color = colors.text,
            modifier = Modifier.hairEnter(0),
        )
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.textMuted, modifier = Modifier.hairEnter(1))
        Box(Modifier.weight(1f).fillMaxWidth().hairEnter(2), contentAlignment = Alignment.Center) { content() }
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).hairEnter(3), verticalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
    }
}

/**
 * Привет: знак w0y рисуется одной линией — «W», потом кольцо-ноль, — затем проявляется имя.
 * Знак тот же, что на иконке приложения, только тонкой линией.
 */
@Composable
private fun Hello(onNext: () -> Unit) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val moves = hairLook.motion.moves
    val draw = remember { Animatable(if (moves) 0f else 1f) }
    val name = remember { Animatable(if (moves) 0f else 1f) }
    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(LOGO_MS, easing = HairTokens.EaseOut))
        name.animateTo(1f, tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(168.dp)) {
            val k = size.minDimension / 108f
            val w =
                Path().apply {
                    moveTo(28f * k, 46f * k)
                    lineTo(41f * k, 80f * k)
                    lineTo(54f * k, 56f * k)
                    lineTo(67f * k, 80f * k)
                    lineTo(80f * k, 46f * k)
                }
            val ring = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(54f * k, 34f * k), 7.5f * k)) }
            val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, size.minDimension / 2f - 1.dp.toPx())) }
            fun part(path: Path, from: Float, to: Float): Path {
                val t = ((draw.value - from) / (to - from)).coerceIn(0f, 1f)
                val m = PathMeasure().apply { setPath(path, false) }
                return Path().also { m.getSegment(0f, m.length * t, it, true) }
            }
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(part(outer, 0f, 0.55f), colors.text.copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))
            drawPath(part(w, 0.15f, 0.8f), colors.text, style = stroke)
            drawPath(part(ring, 0.7f, 1f), tone, style = Stroke(width = 2.dp.toPx()))
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "w0y",
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, letterSpacing = (-2).sp),
            color = colors.text,
            modifier = Modifier.graphicsLayer { alpha = name.value; translationY = (1f - name.value) * 16.dp.toPx() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboard_hello_text),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = name.value },
        )
        Spacer(Modifier.weight(1.2f))
        PixelButton(
            stringResource(R.string.onboard_start),
            onClick = onNext,
            fill = colors.accent,
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp).graphicsLayer { alpha = name.value },
        )
    }
}

/** Аккаунт: войти через Google или без входа. Вошёл — вместо кнопок имя и галочка. */
@Composable
private fun Account(signedIn: Boolean, accountName: String?, onSignIn: () -> Unit, onNext: () -> Unit) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    StepPage(
        title = stringResource(R.string.onboard_account_title),
        text = stringResource(R.string.onboard_account_text),
        content = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Point(HairGlyph.SYNC, stringResource(R.string.onboard_account_point_sync))
                Point(HairGlyph.HEART, stringResource(R.string.onboard_account_point_likes))
                Point(HairGlyph.SHIELD, stringResource(R.string.onboard_account_point_local))
                if (signedIn) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().matte(RoundedCornerShape(16.dp), alpha = 0.55f).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HairIcon(HairGlyph.CHECK, tone, Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            accountName?.let { stringResource(R.string.onboard_account_done_name, it) } ?: stringResource(R.string.onboard_account_done),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.text,
                        )
                    }
                }
            }
        },
        actions = {
            if (signedIn) {
                PixelButton(stringResource(R.string.onboard_next), onClick = onNext, fill = colors.accent, modifier = Modifier.fillMaxWidth())
            } else {
                PixelButton(stringResource(R.string.onboard_sign_in), onClick = onSignIn, fill = colors.accent, modifier = Modifier.fillMaxWidth())
                PixelButton(stringResource(R.string.onboard_no_account), onClick = onNext, fill = colors.surface, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

@Composable
private fun Point(glyph: HairGlyph, text: String) {
    val colors = LocalW0yColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).border(1.dp, colors.text.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            HairIcon(glyph, colors.text, Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.text)
    }
}

/**
 * Цвет: кружки акцентов, у выбранного — тонкое кольцо вокруг. Последний кружок — свой цвет:
 * под ним радужная линейка, по ней выбирается оттенок. Ниже живой пример: кнопка, метка, линия.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorStep(vm: SettingsViewModel, onNext: () -> Unit) {
    val colors = LocalW0yColors.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tap = rememberTapHaptic()
    StepPage(
        title = stringResource(R.string.onboard_color_title),
        text = stringResource(R.string.onboard_color_text),
        content = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    maxItemsInEachRow = 5,
                ) {
                    Accent.entries.forEach { a ->
                        val fill = if (a == Accent.CUSTOM) Color(settings.customAccent) else Color(a.accent)
                        Dot(fill, selected = settings.accent == a, custom = a == Accent.CUSTOM, label = stringResource(a.label)) {
                            tap()
                            vm.setAccent(a)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                AnimatedContent(targetState = settings.accent, label = "accentName", transitionSpec = { fadeIn(tween(HairTokens.MID_MS)) togetherWith fadeOut(tween(HairTokens.SHORT_MS)) }) { a ->
                    Text(stringResource(a.label).asLabel(), style = HairLabelStyle, color = colors.textMuted)
                }
                if (settings.accent == Accent.CUSTOM) {
                    Spacer(Modifier.height(16.dp))
                    HueRuler(settings.customAccent, onPick = vm::setCustomAccent, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(28.dp))
                AccentSample()
            }
        },
        actions = { PixelButton(stringResource(R.string.onboard_next), onClick = onNext, fill = colors.accent, modifier = Modifier.fillMaxWidth()) },
    )
}

/** Кружок цвета 44 dp; выбранный — кольцо вокруг на расстоянии, вырастает пружиной. */
@Composable
private fun Dot(fill: Color, selected: Boolean, custom: Boolean, label: String, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val ring by animateFloatAsState(if (selected) 1f else 0f, spring(0.55f, 420f), label = "dotRing")
    Box(
        Modifier
            .size(52.dp)
            .pressScale(interaction, pressed = 0.9f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label }
            .drawBehind {
                val r = size.minDimension / 2f
                drawCircle(fill, r - 8.dp.toPx())
                if (custom) {
                    // Свой цвет — радужная кромка, чтобы было видно, что он настраивается.
                    drawCircle(Brush.sweepGradient(RAINBOW), r - 8.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
                if (ring > 0f) drawCircle(colors.text.copy(alpha = 0.8f * ring), r - 2.dp.toPx() + (1f - ring) * 6.dp.toPx(), style = Stroke(1.dp.toPx()))
            },
    )
}

/** Радужная линейка оттенка для своего цвета: тянешь — цвет меняется сразу. */
@Composable
private fun HueRuler(argb: Int, onPick: (Int) -> Unit, modifier: Modifier) {
    val colors = LocalW0yColors.current
    val hsv = remember(argb) { FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) } }
    var width by remember { mutableFloatStateOf(1f) }
    fun pick(x: Float) {
        val h = (x / width).coerceIn(0f, 1f) * 360f
        onPick(android.graphics.Color.HSVToColor(floatArrayOf(h, 0.62f, 0.98f)))
    }
    Box(
        modifier
            .height(36.dp)
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(Unit) { detectTapGestures { pick(it.x) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> pick(change.position.x) } }
            .drawBehind {
                val y = size.height / 2f
                drawLine(Brush.horizontalGradient(RAINBOW), Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), cap = StrokeCap.Round)
                val x = size.width * hsv[0] / 360f
                drawCircle(Color(argb), 9.dp.toPx(), Offset(x, y))
                drawCircle(colors.text, 9.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
            },
    )
}

/** Живой пример акцента: метка с точкой, линия прогресса, круглая кнопка. */
@Composable
private fun AccentSample() {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    Row(
        Modifier.fillMaxWidth().matte(RoundedCornerShape(18.dp), alpha = 0.55f).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(tone, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.onboard_sample_label).asLabel(), style = HairLabelStyle, color = colors.text)
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val y = size.height / 2f
                drawLine(colors.text.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                drawLine(tone, Offset(0f, y), Offset(size.width * 0.42f, y), 2.dp.toPx())
                drawCircle(tone, 3.dp.toPx(), Offset(size.width * 0.42f, y))
            }
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(44.dp).background(tone, CircleShape), contentAlignment = Alignment.Center) {
            HairIcon(HairGlyph.PLAY, colors.background, Modifier.size(18.dp))
        }
    }
}

/**
 * Фон: варианты словами, фон меняется прямо за листом. «Моя картинка» открывает галерею;
 * выбранная картинка сразу становится фоном.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackdropStep(vm: SettingsViewModel, onNext: () -> Unit) {
    val colors = LocalW0yColors.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tap = rememberTapHaptic()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.setBackdropPhoto(uri) }
    val modes = listOf(BackdropMode.GRID, BackdropMode.TOPO, BackdropMode.AURORA, BackdropMode.COVER, BackdropMode.PURE)
    StepPage(
        title = stringResource(R.string.onboard_backdrop_title),
        text = stringResource(R.string.onboard_backdrop_text),
        content = {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                modes.forEach { m ->
                    ChoiceWord(stringResource(m.label()), settings.backdropMode == m) { tap(); vm.setBackdropMode(m) }
                }
                ChoiceWord(stringResource(R.string.backdrop_photo), settings.backdropMode == BackdropMode.PHOTO) {
                    tap()
                    picker.launch(arrayOf("image/*"))
                }
            }
        },
        actions = { PixelButton(stringResource(R.string.onboard_finish), onClick = onNext, fill = colors.accent, modifier = Modifier.fillMaxWidth()) },
    )
}

/** Слово выбора, как в fl0w: выбранное светлое и подчёркнуто линией, растущей от середины. */
@Composable
private fun ChoiceWord(text: String, on: Boolean, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val ink by animateColorAsState(if (on) colors.text else colors.text.copy(alpha = 0.4f), hairSpec(HairTokens.SHORT_MS), label = "choiceInk")
    val line by animateFloatAsState(if (on) 1f else 0f, hairSpec(HairTokens.MID_MS), label = "choiceLine")
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        color = ink,
        modifier =
            Modifier
                .pressScale(interaction, pressed = 0.92f)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(vertical = 4.dp)
                .drawBehind {
                    if (line > 0f) {
                        val w = size.width * line
                        drawLine(ink, Offset((size.width - w) / 2f, size.height), Offset((size.width + w) / 2f, size.height), 1.dp.toPx())
                    }
                },
    )
}

private fun BackdropMode.label(): Int =
    when (this) {
        BackdropMode.TOPO -> R.string.backdrop_topo
        BackdropMode.GRID -> R.string.backdrop_grid
        BackdropMode.PURE -> R.string.backdrop_pure
        BackdropMode.COVER -> R.string.backdrop_cover
        BackdropMode.AURORA -> R.string.backdrop_aurora
        BackdropMode.PHOTO -> R.string.backdrop_photo
    }

private val RAINBOW = listOf(Color(0xFFFF5A5A), Color(0xFFFFC24A), Color(0xFF8BE36A), Color(0xFF4AD8E0), Color(0xFF4A7DFB), Color(0xFFB36BFF), Color(0xFFFF5A5A))
private const val LOGO_MS = 1400
