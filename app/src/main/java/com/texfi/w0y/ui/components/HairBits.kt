package com.texfi.w0y.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.MotionLevel
import com.texfi.w0y.ui.theme.UnboundedFamily
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook

/**
 * Число, которое досчитывается до значения: с нуля при первом показе, дальше — от прежнего
 * к новому. Без анимаций — сразу итог. Читается только при отрисовке текста, соседей не трогает.
 */
@Composable
fun CountUpText(value: Int, style: TextStyle, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, suffix: String = "") {
    val level = hairLook.motion
    val shown = remember { Animatable(if (level == MotionLevel.OFF) value.toFloat() else 0f) }
    LaunchedEffect(value, level) {
        if (level == MotionLevel.OFF) shown.snapTo(value.toFloat())
        else shown.animateTo(value.toFloat(), tween(if (level == MotionLevel.FULL) COUNT_MS else HairTokens.SHORT_MS, easing = HairTokens.EaseOut))
    }
    Text(shown.value.toInt().toString() + suffix, style = style, color = color, modifier = modifier, maxLines = 1)
}

/** Ячейка строки цифр: число и моно-подпись под ним. */
data class HairStat(val value: Int, val label: String, val suffix: String = "")

/**
 * Строка цифр под заголовком экрана: крупное число и подпись, между ячейками — тонкие
 * вертикальные линии. Числа досчитываются, ячейки проявляются лесенкой.
 */
@Composable
fun HairStatStrip(stats: List<HairStat>, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        stats.forEachIndexed { i, stat ->
            if (i > 0) {
                Box(Modifier.padding(horizontal = 14.dp).width(hairLook.line).fillMaxHeight().background(colors.border))
            }
            Column(Modifier.weight(1f).hairStagger(i)) {
                CountUpText(stat.value, StatNumberStyle, colors.text, suffix = stat.suffix)
                Spacer(Modifier.height(2.dp))
                Text(stat.label.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1)
            }
        }
    }
}

/**
 * Тонкая полоса прогресса: дорожка линией, заполнение — акцентом, едет плавно. [fraction] null —
 * доля неизвестна: по дорожке бегает короткий отрезок.
 */
@Composable
fun HairProgressLine(fraction: Float?, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val level = hairLook.motion
    val shown by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), hairSpec(HairTokens.LONG_MS), label = "hairProgress")
    val run =
        if (fraction == null && level.moves) {
            rememberInfiniteTransition(label = "hairRun").animateFloat(0f, 1f, infiniteRepeatable(tween(RUN_MS, easing = LinearEasing), RepeatMode.Restart), label = "hairRunX").value
        } else {
            0f
        }
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        val y = size.height / 2f
        drawLine(colors.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        if (fraction != null) {
            if (shown > 0f) drawLine(tone, Offset(0f, y), Offset(size.width * shown, y), size.height)
        } else {
            val seg = size.width * 0.22f
            val x = -seg + (size.width + seg) * run
            drawLine(tone, Offset(x.coerceAtLeast(0f), y), Offset((x + seg).coerceAtMost(size.width), y), size.height)
        }
    }
}

/** Заголовок группы внутри списка («Сегодня», «Вчера»): моно-подпись и линия до края. */
@Composable
fun HairGroupHeader(label: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val colors = LocalW0yColors.current
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.asLabel(), style = HairLabelStyle, color = Contrast.ensure(colors.accent, colors.background), maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(hairLook.line).background(colors.border))
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(trailing.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1)
        }
    }
}

/**
 * Справка, которая не занимает экран: строка-вопрос с шевроном, по нажатию под ней раскрывается
 * объяснение. Открыта ли — помнится, пока жив экран.
 */
@Composable
fun HairInfoToggle(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalW0yColors.current
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, hairSpec(HairTokens.MID_MS), label = "infoTurn")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
            HairIcon(HairGlyph.CHEVRON_RIGHT, colors.textMuted, Modifier.size(16.dp).rotate(turn))
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(hairSpec(HairTokens.MID_MS)) + fadeIn(hairSpec(HairTokens.MID_MS)),
            exit = shrinkVertically(hairSpec(HairTokens.MID_MS)) + fadeOut(hairSpec(HairTokens.SHORT_MS)),
        ) {
            Column(Modifier.padding(bottom = 8.dp)) { content() }
        }
    }
}

/** Крупное число строки цифр: тот же гротеск, что у заголовков, но мельче. */
internal val StatNumberStyle =
    TextStyle(fontFamily = UnboundedFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.02).sp)

private const val COUNT_MS = 700
private const val RUN_MS = 1_100
