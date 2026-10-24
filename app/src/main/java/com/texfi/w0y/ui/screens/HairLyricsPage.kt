package com.texfi.w0y.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.Lyrics
import com.texfi.w0y.data.LyricsSize
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.LyricsView
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.components.HairIconButton
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairTitleStyle
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Страница текста: заменяет собой весь плеер. Сверху — маленькая обложка с
 * названием и исполнителем, дальше крупный жирный текст на всю высоту, где
 * звучащая строка яркая, а соседние тускнеют, снизу — перемотка и кнопки.
 * Строка по нажатию перематывает к ней, ссылка на перевод и язык — выше
 * перемотки. Закрывает страницу кнопка справа вверху.
 */
@Composable
internal fun HairLyricsPage(
    song: SongItem,
    lyrics: Lyrics?,
    translation: List<String>?,
    positionProvider: () -> Long,
    playing: Boolean,
    onSeekLine: (Long) -> Unit,
    loading: Boolean,
    onClose: () -> Unit,
    translate: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    onToggle: (() -> Unit)? = null,
    durationMs: Long = 0L,
) {
    val colors = LocalW0yColors.current
    // Только текст: шапка и управление уезжают, строки занимают весь экран. Внизу остаётся
    // тонкая полоса хода, пауза и кнопка, которая возвращает плеер.
    var focus by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val showSpec = com.texfi.w0y.ui.components.hairSpec<androidx.compose.ui.unit.IntSize>(com.texfi.w0y.ui.theme.HairTokens.LONG_MS)
    val fadeSpec = com.texfi.w0y.ui.components.hairSpec<Float>(com.texfi.w0y.ui.theme.HairTokens.MID_MS)
    val tone = Contrast.ensure(colors.accent, colors.background)
    val closeNow = androidx.compose.runtime.rememberUpdatedState(onClose)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val closePx = with(density) { 56.dp.toPx() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        // Ручка сверху: текст пришёл сверху — туда же его можно смахнуть обратно.
        Box(
            Modifier
                .fillMaxWidth()
                .hairEnter(0)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }) { _, dy ->
                        total += dy
                        if (total < -closePx) { total = 0f; closeNow.value() }
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(top = 8.dp)
                    .width(36.dp)
                    .height(2.dp)
                    .background(colors.outline),
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !focus,
            enter = androidx.compose.animation.expandVertically(showSpec) + androidx.compose.animation.fadeIn(fadeSpec),
            exit = androidx.compose.animation.shrinkVertically(showSpec) + androidx.compose.animation.fadeOut(fadeSpec),
        ) {
        Row(
            Modifier
                .fillMaxWidth()
                .hairEnter(1)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }) { _, dy ->
                        total += dy
                        if (total < -closePx) { total = 0f; closeNow.value() }
                    }
                }
                .padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(url = song.thumbnailUrl, px = Thumbnails.ROW, modifier = Modifier.size(60.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = HairTitleStyle,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.copyOnLongPress(song.title),
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.copyOnLongPress(song.artist),
                )
            }
            HairIconButton(HairGlyph.COLLAPSE, stringResource(R.string.hl_lyrics_focus), { focus = true }, tint = colors.textMuted)
            HairIconButton(HairGlyph.LYRICS, stringResource(R.string.player_lyrics), onClose, tint = tone)
        }
        }
        // Края строк растворяются в фоне сверху и снизу, а не обрезаются линейкой.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .hairEnter(2)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.08f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            when {
                lyrics != null && lyrics.synced.isNotEmpty() ->
                    LyricsView(
                        lines = lyrics.synced,
                        translation = translation,
                        positionProvider = positionProvider,
                        playing = playing,
                        size = LyricsSize.LARGE,
                        onSeek = onSeekLine,
                        big = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                lyrics?.plain != null ->
                    Text(
                        text = lyrics.plain.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    )
                loading ->
                    com.texfi.w0y.ui.components.HairLoader(Modifier.size(44.dp).align(Alignment.Center))
                else ->
                    Text(
                        text = stringResource(R.string.hl_lyrics_none),
                        style = HairTitleStyle,
                        color = colors.textMuted,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !focus,
            enter = androidx.compose.animation.expandVertically(showSpec, expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(fadeSpec),
            exit = androidx.compose.animation.shrinkVertically(showSpec, shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(fadeSpec),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().hairEnter(3), horizontalArrangement = Arrangement.Start) { translate() }
                Column(Modifier.hairEnter(4)) { controls() }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = focus,
            enter = androidx.compose.animation.expandVertically(showSpec) + androidx.compose.animation.fadeIn(fadeSpec),
            exit = androidx.compose.animation.shrinkVertically(showSpec) + androidx.compose.animation.fadeOut(fadeSpec),
        ) {
            FocusBar(positionProvider = positionProvider, durationMs = durationMs.takeIf { it > 0 } ?: song.durationMs(), playing = playing, onToggle = onToggle, onShow = { focus = false })
        }
    }
}

/** Длительность трека из подписи «3:24» — для полосы хода в режиме «только текст». */
private fun SongItem.durationMs(): Long =
    durationText?.split(":")?.mapNotNull { it.trim().toLongOrNull() }?.fold(0L) { acc, part -> acc * 60 + part }?.times(1000) ?: 0L

/**
 * Нижняя полоса режима «только текст»: тонкая линия хода во всю ширину, пауза и кнопка, которая
 * возвращает плеер. Позиция читается при рисовании — строка не пересобирается.
 */
@Composable
private fun FocusBar(positionProvider: () -> Long, durationMs: Long, playing: Boolean, onToggle: (() -> Unit)?, onShow: () -> Unit) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(playing) {
        while (true) {
            tick = positionProvider()
            kotlinx.coroutines.delay(if (playing) 250L else 1_000L)
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .drawWithContent {
                    drawRect(colors.outline)
                    val p = if (durationMs > 0) (tick.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    drawRect(tone, size = size.copy(width = size.width * p))
                },
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = com.texfi.w0y.ui.components.HairMath.formatTime(tick),
                style = com.texfi.w0y.ui.theme.HairLabelStyle,
                color = tone,
            )
            Spacer(Modifier.weight(1f))
            if (onToggle != null) {
                HairIconButton(if (playing) HairGlyph.PAUSE else HairGlyph.PLAY, stringResource(if (playing) R.string.hl_cd_pause else R.string.hl_cd_play), onToggle, tint = colors.text)
            }
            HairIconButton(HairGlyph.EXPAND, stringResource(R.string.hl_lyrics_show_player), onShow, tint = colors.textMuted)
        }
    }
}
