package com.texfi.w0y.ui.screens

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.NumberText
import com.texfi.w0y.data.PlayerLayout
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.SoundProfile
import com.texfi.w0y.ui.components.Buzz
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.ExplicitBadge
import com.texfi.w0y.ui.components.HairIconButton
import com.texfi.w0y.ui.components.HairMath
import com.texfi.w0y.ui.components.HairPlayButton
import com.texfi.w0y.ui.components.VersionBadge
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.components.rememberHaptics
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.HairScreenTitleStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.asTitle
import com.texfi.w0y.ui.theme.hairLook
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Верхняя строка плеера Hairline: свернуть, «играет · N из M», визуализатор, подставка. */
@Composable
internal fun HairPlayerHeader(
    onCollapse: () -> Unit,
    position: String?,
    visualizerOn: Boolean,
    onToggleArt: () -> Unit,
    onStand: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HairIconButton(HairGlyph.COLLAPSE, stringResource(R.string.player_collapse), onCollapse, tint = colors.text)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.player_now_playing).removePrefix("❯ ").asLabel(),
                style = PixelSectionLabel,
                color = Contrast.ensure(colors.accent, colors.background),
            )
            if (position != null) Text(position, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        HairIconButton(
            HairGlyph.WAVE,
            stringResource(R.string.player_visualizer),
            onToggleArt,
            tint = if (visualizerOn) Contrast.ensure(colors.accent, colors.background) else colors.textMuted,
        )
        HairIconButton(HairGlyph.STAND, stringResource(R.string.player_stand), onStand)
    }
}

/**
 * Название и исполнитель. В раскладке «диск» — по центру под диском, в
 * «минимум» — крупно (широкий гротеск, заглавными, до трёх строк), в
 * «компакт» — слева, рядом с маленьким диском.
 */
@Composable
internal fun HairTitleBlock(song: SongItem, layout: PlayerLayout, own: SoundProfile?, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val centered = layout == PlayerLayout.RING
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    Column(modifier.fillMaxWidth(), horizontalAlignment = align) {
        if (song.explicit) {
            ExplicitBadge()
            Spacer(Modifier.height(6.dp))
        }
        if (layout == PlayerLayout.MINIMAL) {
            Text(
                text = song.title.asTitle(),
                style = HairScreenTitleStyle,
                color = colors.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.basicMarquee(),
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            own?.takeIf { !it.isPlain }?.let {
                Spacer(Modifier.width(8.dp))
                VersionBadge(it.label)
            }
        }
    }
}

/** Ряд действий над треком: лайк, закрепить, в плейлист, скачать, поделиться, таймер сна. */
@Composable
internal fun HairActionsRow(
    song: SongItem,
    liked: Boolean,
    pinned: Boolean,
    sharing: Boolean,
    sleepLeftMs: Long?,
    onLike: () -> Unit,
    onPin: () -> Unit,
    onAdd: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onSleep: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val likeFill by animateFloatAsState(if (liked) 1f else 0f, hairSpec(HairTokens.MID_MS), label = "likeFill")
    val pinFill by animateFloatAsState(if (pinned) 1f else 0f, hairSpec(HairTokens.MID_MS), label = "pinFill")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        HairIconButton(HairGlyph.HEART, stringResource(R.string.player_cd_like), onLike, tint = if (liked) tone else colors.textMuted, fill = { likeFill })
        HairIconButton(HairGlyph.PIN, stringResource(R.string.player_cd_pin), onPin, tint = if (pinned) tone else colors.textMuted, fill = { pinFill })
        HairIconButton(HairGlyph.PLUS, stringResource(R.string.player_cd_playlist), onAdd)
        Box(Modifier.size(HairTokens.TOUCH), contentAlignment = Alignment.Center) { DownloadButton(song.id, onDownload = onDownload) }
        HairIconButton(HairGlyph.SHARE, stringResource(R.string.player_cd_share), onShare, tint = if (sharing) tone else colors.textMuted)
        HairSleepButton(sleepLeftMs, onSleep)
    }
}

/** Луна таймера сна; пока идёт отсчёт — вокруг неё дуга, которая убывает. */
@Composable
internal fun HairSleepButton(leftMs: Long?, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val tone = Contrast.ensure(colors.accent, colors.background)
    // Исходная длина — первое значение, с которого пошёл отсчёт.
    var total by remember { mutableStateOf(0L) }
    if (leftMs == null) total = 0L else if (leftMs > total) total = leftMs
    val fraction = if (leftMs != null && total > 0) (leftMs.toFloat() / total).coerceIn(0f, 1f) else 0f
    Box(contentAlignment = Alignment.Center) {
        HairIconButton(
            HairGlyph.MOON,
            stringResource(R.string.player_cd_sleep),
            onClick,
            tint = if (leftMs != null) tone else colors.textMuted,
        )
        if (leftMs != null) {
            Canvas(Modifier.size(HairTokens.TOUCH)) {
                val w = look.line.toPx()
                val inset = 4.dp.toPx() + w
                val rect = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(colors.border, 0f, 360f, false, Offset(inset, inset), rect, style = Stroke(w))
                drawArc(colors.progress, -90f, 360f * fraction, false, Offset(inset, inset), rect, style = Stroke(w * 1.4f, cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * Линия перемотки для раскладок без диска: тонкая дорожка, синий прогресс,
 * бегунок-кольцо, который растёт под пальцем. Позиция пальца показывается,
 * пока тянут, а не то, что у плеера, — иначе бегунок дёргался бы назад.
 */
@Composable
internal fun HairSeekLine(positionMs: Long, durationMs: Long, onDrag: (Long) -> Unit, onDragEnd: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    var dragging by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (dragging) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "seekGrow")
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(HairTokens.TOUCH)
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        dragging = true
                        if (durationMs > 0) onDrag(((at.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                    },
                    onDragEnd = { dragging = false; onDragEnd() },
                    onDragCancel = { dragging = false; onDragEnd() },
                ) { change, _ ->
                    change.consume()
                    if (durationMs > 0) onDrag(((change.position.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectTapGestures { at ->
                    if (durationMs > 0) {
                        onDrag(((at.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                        onDragEnd()
                    }
                }
            },
    ) {
        val w = look.line.toPx() * (1f + 1.2f * grow)
        val pad = 10.dp.toPx()
        val y = size.height / 2f
        drawLine(colors.border, Offset(pad, y), Offset(size.width - pad, y), w, StrokeCap.Round)
        val x = pad + (size.width - pad * 2) * progress
        if (progress > 0f) drawLine(colors.progress, Offset(pad, y), Offset(x, y), w, StrokeCap.Round)
        val r = (6.dp.toPx() + 3.dp.toPx() * grow)
        drawCircle(colors.background, r, Offset(x, y))
        drawCircle(colors.progress, r, Offset(x, y), style = Stroke(look.line.toPx() * (1f + grow)))
    }
}

/** Время слева и справа и шаг перемотки по краям: «0:42  −10  +10  3:15». */
@Composable
internal fun HairTimeRow(
    positionMs: Long,
    durationMs: Long,
    stepSec: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(HairMath.formatTime(positionMs), style = MaterialTheme.typography.labelMedium, color = colors.progress.let { Contrast.ensure(it, colors.background) })
        Spacer(Modifier.weight(1f))
        HairStepChip(stringResource(R.string.player_seek_back, stepSec), onBack)
        Spacer(Modifier.width(10.dp))
        HairStepChip(stringResource(R.string.player_seek_forward, stepSec), onForward)
        Spacer(Modifier.weight(1f))
        Text(HairMath.formatTime(durationMs), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
    }
}

/** Пилюля-контур «−10» / «+10»; нажатие даёт короткий импульс — заполнение на миг. */
@Composable
private fun HairStepChip(text: String, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "stepChip")
    val tone = Contrast.ensure(colors.accent, colors.background)
    val tap = com.texfi.w0y.ui.components.rememberTapHaptic()
    Box(
        Modifier
            .heightInTouch()
            .clickable(interactionSource = interaction, indication = null) {
                tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .border(look.line, androidx.compose.ui.graphics.lerp(colors.outline, tone, press), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) { Text(text, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.lerp(colors.text, tone, press)) }
    }
}

private fun Modifier.heightInTouch(): Modifier = this.then(Modifier.height(HairTokens.TOUCH).padding(horizontal = 0.dp))

/** Транспорт Hairline: shuffle · prev · большая круглая Play · next · repeat. */
@Composable
internal fun HairTransport(
    playing: Boolean,
    shuffle: Boolean,
    repeatActive: Boolean,
    repeatOne: Boolean,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    playSize: Dp = 88.dp,
) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            HairIconButton(HairGlyph.SHUFFLE, stringResource(R.string.player_cd_shuffle), onShuffle, tint = if (shuffle) tone else colors.textMuted)
            HairIconButton(HairGlyph.PREVIOUS, stringResource(R.string.hl_cd_prev), onPrevious, tint = colors.text, size = 28.dp)
            HairPlayButton(playing = playing, onClick = onToggle, size = playSize)
            HairIconButton(HairGlyph.NEXT, stringResource(R.string.hl_cd_next), onNext, tint = colors.text, size = 28.dp)
            HairIconButton(HairGlyph.REPEAT, stringResource(R.string.player_cd_repeat), onRepeat, tint = if (repeatActive) tone else colors.textMuted)
        }
        if (repeatOne) {
            Text(
                text = stringResource(R.string.player_repeat_one),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Круглый регулятор тонкой дугой: шкала 270°, деления, значение в центре.
 * Тянется по кругу (угол пальца — значение) и ставится тапом по дуге.
 * Значение уходит наружу, когда палец поднят; во время движения показывается
 * то, что под пальцем. Для TalkBack — обычный диапазон со шагом.
 */
@Composable
internal fun HairDial(
    label: String,
    valueText: (Float) -> String,
    fraction: Float,
    steps: Int,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val haptic = rememberHaptics()
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: fraction
    val grow by animateFloatAsState(if (drag != null) 1f else 0f, hairSpec(HairTokens.SHORT_MS), label = "dialGrow")
    val tone = Contrast.ensure(colors.accent, colors.background)
    fun snap(f: Float): Float = if (steps > 0) (Math.round(f * steps) / steps.toFloat()) else f
    val description = "$label ${valueText(shown)}"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f, if (steps > 0) steps - 1 else 0)
                    setProgress { target -> onCommit(snap(target.coerceIn(0f, 1f))); true }
                }
                .pointerInput(steps) {
                    detectTapGestures { at ->
                        val f = snap(HairMath.dialFraction(at.x - this.size.width / 2f, at.y - this.size.height / 2f))
                        haptic(Buzz.SELECT)
                        onCommit(f)
                    }
                }
                .pointerInput(steps) {
                    var last = -1f
                    detectDragGestures(
                        onDragStart = { at ->
                            val f = snap(HairMath.dialFraction(at.x - this.size.width / 2f, at.y - this.size.height / 2f))
                            drag = f
                            last = f
                        },
                        onDragEnd = {
                            drag?.let(onCommit)
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    ) { change, _ ->
                        change.consume()
                        val f = snap(HairMath.dialFraction(change.position.x - this.size.width / 2f, change.position.y - this.size.height / 2f))
                        // Не перескакивать с одного края на другой через пустой сектор.
                        if (kotlin.math.abs(f - (drag ?: f)) < 0.6f) {
                            drag = f
                            if (f != last) {
                                last = f
                                haptic(Buzz.TAP)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size)) {
                val w = look.line.toPx()
                val inset = 8.dp.toPx()
                val rect = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(colors.border, 135f, 270f, false, topLeft, rect, style = Stroke(w, cap = StrokeCap.Round))
                if (shown > 0f) {
                    drawArc(tone, 135f, 270f * shown, false, topLeft, rect, style = Stroke(w * (1f + 0.8f * grow), cap = StrokeCap.Round))
                }
                // Метки шкалы: деления по числу шагов (или 11 у плавной шкалы).
                val marks = if (steps > 0) steps + 1 else 11
                val r = rect.width / 2f
                val c = center
                for (i in 0 until marks) {
                    val a = (135f + 270f * i / (marks - 1)) * (PI.toFloat() / 180f)
                    val outer = r + 5.dp.toPx()
                    val inner = r + 1.dp.toPx()
                    drawLine(
                        if (i / (marks - 1f) <= shown + 0.001f) tone else colors.outline,
                        Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                        Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
                        w,
                        StrokeCap.Round,
                    )
                }
                // Бегунок на конце дуги.
                val ta = (135f + 270f * shown) * (PI.toFloat() / 180f)
                val tp = Offset(c.x + cos(ta) * r, c.y + sin(ta) * r)
                drawCircle(colors.background, 5.dp.toPx() + 2.dp.toPx() * grow, tp)
                drawCircle(tone, 5.dp.toPx() + 2.dp.toPx() * grow, tp, style = Stroke(w * (1f + grow)))
            }
            Text(valueText(shown), style = MaterialTheme.typography.labelMedium, color = colors.text, textAlign = TextAlign.Center)
        }
        Text(label.asLabel(), style = PixelSectionLabel, color = colors.textMuted)
    }
}

/** Три регулятора звука трека: скорость, тон, эхо. Запоминаются за треком. */
@Composable
internal fun HairDials(
    sound: SoundProfile,
    reverbLabel: (Reverb) -> String,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onReverb: (Reverb) -> Unit,
) {
    val speedSteps = 36 // 0.2..2.0 шагом 0.05
    val pitchSteps = 4 // 0.9..1.1 шагом 0.05
    val reverbs = Reverb.entries
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        HairDial(
            label = stringResource(R.string.hl_dial_speed),
            valueText = { NumberText.speed(0.2f + it * 1.8f) },
            fraction = ((sound.speed - 0.2f) / 1.8f).coerceIn(0f, 1f),
            steps = speedSteps,
            onCommit = { onSpeed((0.2f + it * 1.8f).let { v -> Math.round(v * 20) / 20f }) },
        )
        HairDial(
            label = stringResource(R.string.hl_dial_pitch),
            valueText = { NumberText.pitch(0.9f + it * 0.2f) },
            fraction = ((sound.pitch - 0.9f) / 0.2f).coerceIn(0f, 1f),
            steps = pitchSteps,
            onCommit = { onPitch((0.9f + it * 0.2f).let { v -> Math.round(v * 20) / 20f }) },
        )
        HairDial(
            label = stringResource(R.string.hl_dial_reverb),
            valueText = { reverbLabel(reverbs[(it * (reverbs.size - 1)).let(Math::round).coerceIn(0, reverbs.lastIndex)]) },
            fraction = reverbs.indexOf(sound.reverb).coerceAtLeast(0).toFloat() / (reverbs.size - 1),
            steps = reverbs.size - 1,
            onCommit = { onReverb(reverbs[(it * (reverbs.size - 1)).let(Math::round).coerceIn(0, reverbs.lastIndex)]) },
        )
    }
}
