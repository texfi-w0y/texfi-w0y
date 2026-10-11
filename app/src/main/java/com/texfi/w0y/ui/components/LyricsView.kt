package com.texfi.w0y.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.data.LrcParser
import com.texfi.w0y.data.LyricLine
import com.texfi.w0y.data.LyricsSize
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.isSmooth
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

/**
 * Текст песни крупно — общий для плеера, полноэкранного текста и режима
 * подставки.
 *
 * Звучащая строка яркая и крупнее, соседние тускнеют тем сильнее, чем
 * дальше; края списка растворяются. Список сам плавно держит звучащую
 * строку в верхней трети, но если человек листает сам — не мешает ему
 * несколько секунд. Перевод — под строкой. Нажатие на строку перематывает
 * к ней. Если у строки есть пословные метки, подсвечивается звучащее слово.
 */
@Composable
fun LyricsView(
    lines: List<LyricLine>,
    translation: List<String>?,
    positionProvider: () -> Long,
    playing: Boolean,
    size: LyricsSize,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    /** Hairline-страница текста: строки крупнее и жирнее. */
    big: Boolean = false,
) {
    val colors = LocalW0yColors.current
    val list = rememberLazyListState()
    var position by remember { mutableLongStateOf(positionProvider()) }
    LaunchedEffect(playing, lines) {
        position = positionProvider()
        while (playing) {
            delay(TICK_MS)
            position = positionProvider()
        }
    }
    val active = LrcParser.activeIndex(lines, position)
    // Ручная прокрутка ставит автопрокрутку на паузу.
    var userScrollAt by remember { mutableLongStateOf(0L) }
    // Прокрутку делает сам компонент — её не считаем ручной.
    val auto = remember { booleanArrayOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.drop(1).collect { scrolling ->
            if (scrolling && !auto[0]) userScrollAt = System.currentTimeMillis()
        }
    }
    LaunchedEffect(active) {
        if (active < 0) return@LaunchedEffect
        if (System.currentTimeMillis() - userScrollAt < USER_PAUSE_MS) return@LaunchedEffect
        val viewport = list.layoutInfo.viewportSize.height
        auto[0] = true
        list.animateScrollToItem(active, scrollOffset = -(viewport / 3))
        auto[0] = false
    }

    val base = size.sp.sp * (if (big) 1.2f else 1f)
    val hair = com.texfi.w0y.ui.theme.isHair
    LazyColumn(
        state = list,
        modifier =
            modifier
                // Края растворяются: строки уходят в фон, а не обрезаются линейкой.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            FADE to Color.Black,
                            1f - FADE to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        contentPadding = PaddingValues(vertical = 120.dp),
    ) {
        itemsIndexed(lines, key = { i, line -> "$i-${line.timeMs}" }) { index, line ->
            val distance = if (active < 0) 3 else abs(index - active)
            val isActive = index == active
            val color by animateColorAsState(
                when {
                    isActive -> colors.text
                    big -> colors.text.copy(alpha = if (distance == 1) 0.42f else 0.26f)
                    distance == 1 -> colors.textMuted
                    else -> colors.textMuted.copy(alpha = 0.45f)
                },
                tween(220),
                label = "lyricColor",
            )
            val style =
                TextStyle(
                    fontSize = if (isActive) base * (if (hair) 1.2f else 1.12f) else base,
                    lineHeight = base * 1.35f,
                    fontWeight = if (big) FontWeight.ExtraBold else if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSeek(line.timeMs) }
                    .padding(vertical = 8.dp),
            ) {
                if (isActive && line.words.isNotEmpty()) {
                    Text(
                        buildAnnotatedString {
                            line.words.forEachIndexed { i, word ->
                                val sung = word.timeMs <= position
                                withStyle(SpanStyle(color = if (sung) colors.accentText else colors.text.copy(alpha = 0.55f))) {
                                    append(word.text)
                                }
                                if (i != line.words.lastIndex) append(' ')
                            }
                        },
                        style = style,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(line.text, style = style, color = if (isActive && isSmooth) colors.text else color, modifier = Modifier.fillMaxWidth())
                }
                translation?.getOrNull(index)?.takeIf { it.isNotBlank() && it != line.text }?.let {
                    Text(
                        it,
                        style = style.copy(fontSize = base * 0.68f, lineHeight = base * 0.95f, fontWeight = FontWeight.Normal),
                        color = if (isActive) colors.accentText else color,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Одна звучащая строка — бегущей строкой, для режима подставки. Тот же
 * расчёт текущей строки, что у [LyricsView].
 */
@Composable
fun LyricsTicker(
    lines: List<LyricLine>,
    positionProvider: () -> Long,
    playing: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color,
) {
    var position by remember { mutableLongStateOf(positionProvider()) }
    LaunchedEffect(playing, lines) {
        position = positionProvider()
        while (playing) {
            delay(TICK_MS)
            position = positionProvider()
        }
    }
    val index = LrcParser.activeIndex(lines, position)
    var shown by remember { mutableStateOf("") }
    shown = lines.getOrNull(index)?.text ?: ""
    Text(shown, style = style, color = color, maxLines = 1, modifier = modifier.basicMarquee())
}

private const val TICK_MS = 120L
private const val USER_PAUSE_MS = 3_500L
private const val FADE = 0.12f
