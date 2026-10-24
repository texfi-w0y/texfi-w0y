package com.texfi.w0y.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairScreenTitleStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.MotionLevel
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import kotlinx.coroutines.delay

/**
 * Первый экран после входа в аккаунт. Одна спокойная последовательность, без вспышек:
 *
 *  1. вокруг аватара за 0,7 с вычерчивается кольцо, сам аватар проявляется внутри;
 *  2. подпись «вход выполнен» и приветствие по имени поднимаются на место;
 *  3. три строки — плейлисты, лайки, альбомы. Пока идёт сверка с аккаунтом, по линии
 *     под строкой бегает отрезок; когда она кончилась, линия заливается, число досчитывается,
 *     справа рисуется галочка;
 *  4. кнопка. Ждать сверку не обязательно: «дальше» доступно сразу, сверка идёт в фоне.
 *
 * Сверка запускается чуть позже, чем открывается экран, поэтому «готово» считается только
 * после того, как она хотя бы раз шла, — иначе первые кадры показали бы нули как итог.
 */
@Composable
fun HairWelcome(
    name: String?,
    avatar: String?,
    syncing: Boolean,
    playlists: Int,
    likes: Int,
    albums: Int,
    onDone: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val level = hairLook.motion
    val full = level == MotionLevel.FULL
    var started by remember { mutableStateOf(false) }
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(syncing) { if (syncing) started = true }
    LaunchedEffect(Unit) {
        delay(SYNC_WAIT_MS)
        waited = true
    }
    val done = !syncing && (started || waited)

    // Шаги появления: кольцо, текст, строки, кнопка.
    val ring = remember { Animatable(if (full) 0f else 1f) }
    val text = remember { Animatable(if (level.moves) 0f else 1f) }
    val rows = remember { Animatable(if (level.moves) 0f else 1f) }
    val exit = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (full) ring.animateTo(1f, tween(RING_MS, easing = HairTokens.EaseOut))
        text.animateTo(1f, tween(if (full) HairTokens.LONG_MS else HairTokens.SHORT_MS, easing = HairTokens.EaseOut))
        rows.animateTo(1f, tween(if (full) HairTokens.LONG_MS else HairTokens.SHORT_MS, easing = HairTokens.EaseOut))
    }
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        if (level.moves) exit.animateTo(1f, tween(HairTokens.MID_MS, easing = HairTokens.EaseOut))
        onDone()
    }
    val leave = { leaving = true }
    BackHandler(onBack = leave)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - exit.value
                val k = 1f + 0.02f * exit.value
                scaleX = k
                scaleY = k
            }
            .background(colors.background)
            // Касания мимо кнопки не проходят к экрану под нами.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Аватар и кольцо вокруг него.
            Box(
                Modifier
                    .size(AVATAR + RING_GAP * 2)
                    .drawBehind {
                        val stroke = 1.5.dp.toPx()
                        val inset = stroke / 2f
                        drawCircle(colors.border, size.minDimension / 2f - inset, style = Stroke(1.dp.toPx()))
                        drawArc(
                            tone,
                            startAngle = -90f,
                            sweepAngle = 360f * ring.value,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(AVATAR)
                        .graphicsLayer {
                            alpha = ring.value
                            val k = 0.92f + 0.08f * ring.value
                            scaleX = k
                            scaleY = k
                        }
                        .clip(CircleShape)
                        .background(colors.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatar != null) {
                        CoverImage(url = avatar, px = 256, modifier = Modifier.fillMaxSize())
                    } else {
                        HairIcon(HairGlyph.ARTIST, colors.textMuted, Modifier.size(36.dp))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.graphicsLayer {
                    alpha = text.value
                    translationY = (1f - text.value) * 12.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.welcome_label).asLabel(), style = HairLabelStyle, color = tone)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (name.isNullOrBlank()) stringResource(R.string.welcome_hello_plain) else stringResource(R.string.welcome_hello, name),
                    style = HairScreenTitleStyle.copy(fontSize = 28.sp, lineHeight = 34.sp),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Spacer(Modifier.height(10.dp))
                val fadeInSpec = hairSpec<Float>(HairTokens.MID_MS)
                val fadeOutSpec = hairSpec<Float>(HairTokens.SHORT_MS)
                AnimatedContent(
                    targetState = done,
                    transitionSpec = { fadeIn(fadeInSpec) togetherWith fadeOut(fadeOutSpec) },
                    label = "welcomeState",
                ) { ready ->
                    Text(
                        stringResource(if (ready) R.string.welcome_ready else R.string.welcome_syncing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Column(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = rows.value
                    translationY = (1f - rows.value) * 12.dp.toPx()
                },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WelcomeRow(HairGlyph.QUEUE, stringResource(R.string.welcome_playlists), playlists, done, 0)
                WelcomeRow(HairGlyph.HEART, stringResource(R.string.welcome_likes), likes, done, 1)
                WelcomeRow(HairGlyph.RELEASE, stringResource(R.string.welcome_albums), albums, done, 2)
            }
            Spacer(Modifier.height(36.dp))
            Box(Modifier.graphicsLayer { alpha = rows.value }) {
                PixelButton(
                    stringResource(if (done) R.string.welcome_go else R.string.welcome_later),
                    onClick = leave,
                    fill = if (done) null else colors.surfaceHigh,
                )
            }
        }
    }
}

/** Строка итога: значок, название, число или ход сверки, галочка, когда готово. */
@Composable
private fun WelcomeRow(glyph: HairGlyph, label: String, count: Int, done: Boolean, index: Int) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val check = remember { Animatable(0f) }
    val spec = hairSpec<Float>(HairTokens.LONG_MS)
    LaunchedEffect(done) {
        if (done) {
            delay(index * CHECK_STEP_MS)
            check.animateTo(1f, spec)
        } else {
            check.snapTo(0f)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HairIcon(glyph, colors.textMuted, Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.text, modifier = Modifier.weight(1f))
            if (done) CountUpText(count, HairLabelStyle.copy(fontSize = 13.sp), colors.text)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(16.dp).graphicsLayer { alpha = check.value; val k = 0.6f + 0.4f * check.value; scaleX = k; scaleY = k }) {
                HairIcon(HairGlyph.CHECK, tone, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(10.dp))
        HairProgressLine(if (done) check.value else null)
    }
}

private val AVATAR = 96.dp
private val RING_GAP = 8.dp
private const val RING_MS = 700
private const val CHECK_STEP_MS = 120L

/** Если сверка так и не началась (нет сети, уже шла), через столько итог считается готовым. */
private const val SYNC_WAIT_MS = 4_000L
