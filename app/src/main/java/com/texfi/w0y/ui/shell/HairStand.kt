package com.texfi.w0y.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R
import com.texfi.w0y.data.StandView
import com.texfi.w0y.data.VisualizerStyle
import com.texfi.w0y.data.LyricLine
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.HairIconButton
import com.texfi.w0y.ui.components.HairPlayButton
import com.texfi.w0y.ui.components.HairProgressLine
import com.texfi.w0y.ui.components.LyricsTicker
import com.texfi.w0y.ui.components.Visualizer
import com.texfi.w0y.ui.components.forUi
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.screens.PlayerViewModel
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.UnboundedFamily
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

/**
 * Подставка в Hairline: сверху большие часы, ниже — обложка или визуализатор,
 * название, исполнитель и строка текста; снизу тонкая линия прогресса и ряд
 * кнопок. Кнопки прячутся сами, как раньше; всё остальное — только контент.
 */
@Composable
internal fun HairStandLayer(
    viewModel: PlayerViewModel,
    settings: W0ySettings,
    playing: Boolean,
    song: com.texfi.w0y.data.SongItem?,
    durationMs: Long,
    lyricsLines: List<LyricLine>,
    controls: Boolean,
    brightness: Float,
    onBrightness: (Float) -> Unit,
    lowBattery: Boolean,
    onTouch: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val text = colors.text
    val dim = colors.textMuted
    val accent = Contrast.ensure(colors.accent, Color.Black)

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(10_000)
        }
    }
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, song?.id) {
        position = viewModel.player.positionMs()
        while (playing) {
            delay(1_000)
            position = viewModel.player.positionMs()
        }
    }
    val clock = remember(now) { now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }
    val date = remember(now) { now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(top = if (settings.standClock) 64.dp else 96.dp, bottom = 140.dp)) {
            val boxW = maxWidth
            val boxH = maxHeight
            val wide = boxW > boxH
            val cover = settings.standView == StandView.COVER
            val clockSize = if (wide) 96.sp else 72.sp
            @Composable
            fun Clock(align: TextAlign) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (align == TextAlign.Start) Alignment.Start else Alignment.CenterHorizontally) {
                    Text(
                        clock,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = UnboundedFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = clockSize,
                            letterSpacing = (-2).sp,
                        ),
                        color = text,
                        maxLines = 1,
                        textAlign = align,
                    )
                    Text(date.asLabel(), style = HairLabelStyle, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            @Composable
            fun Info(align: TextAlign) {
                val h = if (align == TextAlign.Start) Alignment.Start else Alignment.CenterHorizontally
                Column(Modifier.fillMaxWidth(), horizontalAlignment = h) {
                    Text(
                        song?.title.orEmpty(),
                        style = androidx.compose.ui.text.TextStyle(fontFamily = UnboundedFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
                        color = text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = align,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(song?.artist.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align)
                    if (settings.standLyrics && lyricsLines.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        LyricsTicker(
                            lines = lyricsLines,
                            positionProvider = viewModel.player::positionMs,
                            playing = playing,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                            color = accent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (lowBattery) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.stand_low_battery).asLabel(), style = HairLabelStyle, color = colors.secondary)
                    }
                }
            }
            @Composable
            fun Art(modifier: Modifier) {
                val shape = RoundedCornerShape(look.cardRadius.coerceAtMost(10.dp))
                if (settings.standVisualizer) {
                    Visualizer(
                        bus = viewModel.spectrum,
                        style = settings.visualizerStyle.forUi(settings.uiStyle),
                        playing = playing,
                        sensitivity = settings.visualizerSensitivity,
                        fps = 30,
                        backdropUrl = null,
                        modifier = modifier.clip(shape),
                    )
                } else if (cover) {
                    CoverImage(song?.thumbnailUrl, px = 720, modifier = modifier.clip(shape).border(look.line, colors.border, shape), corner = 6)
                }
            }
            val hasArt = settings.standVisualizer || cover
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        if (settings.standClock) Clock(TextAlign.Start)
                        Info(TextAlign.Start)
                    }
                    if (hasArt) Art(Modifier.size(boxH * 0.8f))
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (settings.standClock) Clock(TextAlign.Center)
                    if (hasArt) Art(Modifier.size(minOf(boxW, boxH * 0.34f)))
                    Info(TextAlign.Center)
                }
            }
        }

        // Верх: яркость, поворот, выход.
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stand_brightness_title).substringAfter(": ").asLabel(), style = HairLabelStyle, color = dim, maxLines = 1)
                Slider(
                    value = if (brightness < 0f) 0.3f else brightness,
                    onValueChange = onBrightness,
                    valueRange = 0.01f..1f,
                    colors = SliderDefaults.colors(thumbColor = text, activeTrackColor = text, inactiveTrackColor = colors.border),
                    modifier = Modifier.weight(1f).widthIn(max = 260.dp).padding(horizontal = 8.dp),
                )
                Text(
                    stringResource(R.string.stand_brightness_system).asLabel(),
                    style = HairLabelStyle,
                    color = if (brightness < 0f) accent else dim,
                    modifier = Modifier.clickable { onBrightness(com.texfi.w0y.data.STAND_BRIGHTNESS_SYSTEM) }.padding(horizontal = 8.dp, vertical = 14.dp),
                )
                HairIconButton(HairGlyph.CLOSE, stringResource(R.string.player_collapse), onExit, tint = text)
            }
        }

        // Низ: прогресс, транспорт, быстрые переключатели.
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.padding(horizontal = 28.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HairProgressLine({ if (durationMs > 0) position.toFloat() / durationMs else 0f }, Modifier.fillMaxWidth(), 2.dp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    HairIconButton(HairGlyph.PREVIOUS, stringResource(R.string.hl_cd_prev), { viewModel.player.skipPrevious(); onTouch() }, tint = text, size = 26.dp)
                    HairPlayButton(playing, { viewModel.player.togglePlayPause(); onTouch() }, size = 64.dp)
                    HairIconButton(HairGlyph.NEXT, stringResource(R.string.widget_next), { viewModel.player.skipNext(); onTouch() }, tint = text, size = 26.dp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StandChip(stringResource(R.string.stand_view_cover), settings.standView == StandView.COVER && !settings.standVisualizer) {
                        viewModel.setStandView(if (settings.standView == StandView.COVER) StandView.TEXT else StandView.COVER)
                        if (settings.standVisualizer) viewModel.setStandVisualizer(false)
                        onTouch()
                    }
                    StandChip(stringResource(R.string.player_visualizer), settings.standVisualizer) {
                        viewModel.setStandVisualizer(!settings.standVisualizer)
                        onTouch()
                    }
                    if (settings.standVisualizer) {
                        val styles = VisualizerStyle.entries.filter { it.hair }
                        val current = settings.visualizerStyle.forUi(settings.uiStyle)
                        StandChip(stringResource(current.label), false) {
                            viewModel.setVisualizerStyle(styles[(styles.indexOf(current) + 1).mod(styles.size)])
                            onTouch()
                        }
                    }
                    StandChip(stringResource(R.string.stand_clock), settings.standClock) {
                        viewModel.setStandClock(!settings.standClock)
                        onTouch()
                    }
                    StandChip(stringResource(R.string.stand_flip), settings.standFlipped) {
                        viewModel.setStandFlipped(!settings.standFlipped)
                        onTouch()
                    }
                }
            }
        }
    }
}

@Composable
private fun StandChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val look = hairLook
    val shape = RoundedCornerShape(look.pillRadius.coerceAtMost(6.dp))
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (active) colors.text else Color.Transparent)
            .then(if (active) Modifier else Modifier.border(look.line, colors.outline, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.asLabel(), style = HairLabelStyle, color = if (active) colors.background else colors.text, maxLines = 1)
    }
}
