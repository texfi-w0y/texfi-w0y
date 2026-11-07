package com.texfi.w0y.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R
import com.texfi.w0y.data.Lyrics
import com.texfi.w0y.data.LyricsSize
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.components.HairIconButton
import com.texfi.w0y.ui.components.HairLoader
import com.texfi.w0y.ui.components.HairMath
import com.texfi.w0y.ui.components.HairPlayButton
import com.texfi.w0y.ui.components.LyricsView
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.theme.GeologicaFamily
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalIslandBackdrop
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.matte

/**
 * Страница текста в Hairline New: весь экран — строки поверх размытой обложки. Сверху
 * матовые кружки «свернуть» и «Aa» (размер текста по кругу), между ними название и
 * исполнитель. Строки крупные, слева, звучащая — белая с чертой акцента. Над нижней
 * матовой панелью — откуда текст, в панели — пауза, линейка хода и время.
 */
@Composable
internal fun IslandLyricsPage(
    song: SongItem,
    lyrics: Lyrics?,
    translation: List<String>?,
    positionProvider: () -> Long,
    playing: Boolean,
    onSeekLine: (Long) -> Unit,
    loading: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    size: LyricsSize,
    onCycleSize: () -> Unit,
    positionMs: Long,
    durationMs: Long,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    translate: @Composable () -> Unit,
) {
    val colors = LocalW0yColors.current
    val backdrop = LocalIslandBackdrop.current
    val closeNow = rememberUpdatedState(onClose)
    val closePx = with(LocalDensity.current) { 56.dp.toPx() }
    val sizeLabel = stringResource(R.string.lyrics_size_title)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Шапку можно смахнуть вверх — туда же, откуда текст пришёл.
        Row(
            Modifier
                .fillMaxWidth()
                .hairEnter(0)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }) { _, dy ->
                        total += dy
                        if (total < -closePx) { total = 0f; closeNow.value() }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).matte(CircleShape, backdrop), contentAlignment = Alignment.Center) {
                HairIconButton(HairGlyph.COLLAPSE, stringResource(R.string.player_collapse), onClose, tint = colors.text, size = 20.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(44.dp)
                    .matte(CircleShape, backdrop)
                    .semantics { contentDescription = sizeLabel }
                    .clickable(onClick = onCycleSize),
                contentAlignment = Alignment.Center,
            ) {
                // Буквы растут вместе с выбранным размером — видно, что поменялось.
                val k = when (size) { LyricsSize.SMALL -> 0.86f; LyricsSize.MEDIUM -> 1f; LyricsSize.LARGE -> 1.16f }
                Text("Aa", style = HairLabelStyle.copy(fontSize = 12.sp * k, letterSpacing = 0.5.sp), color = colors.text)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().hairEnter(1).padding(start = 8.dp, end = 4.dp)) {
            when {
                lyrics != null && lyrics.synced.isNotEmpty() ->
                    LyricsView(
                        lines = lyrics.synced,
                        translation = translation,
                        positionProvider = positionProvider,
                        playing = playing,
                        size = size,
                        onSeek = onSeekLine,
                        big = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                lyrics?.plain != null ->
                    Text(
                        text = lyrics.plain.orEmpty(),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
                        color = colors.text,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                loading -> HairLoader(Modifier.size(44.dp).align(Alignment.Center))
                else ->
                    Text(
                        stringResource(R.string.hl_lyrics_none),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = GeologicaFamily, fontWeight = FontWeight.SemiBold),
                        color = colors.textMuted,
                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp),
                    )
            }
        }
        // Откуда текст — тихой подписью, под ней строка перевода во всю ширину: вдвоём на одной
        // строке им было тесно.
        Column(Modifier.fillMaxWidth().hairEnter(2).padding(start = 24.dp, end = 16.dp, top = 4.dp)) {
            val source = lyrics?.source?.let { "${stringResource(R.string.island_text)} · $it" }
            if (source != null) Text(source.asLabel(), style = HairLabelStyle, color = colors.textMuted.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
            translate()
        }
        Row(
            Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
                .hairEnter(3)
                .matte(RoundedCornerShape(24.dp), backdrop)
                .padding(start = 12.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HairPlayButton(playing = playing, onClick = onToggle, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                IslandRuler(positionMs, durationMs, onDrag, onDragEnd)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(HairMath.formatTime(positionMs), style = HairLabelStyle, color = colors.textMuted)
                    Spacer(Modifier.weight(1f))
                    Text(HairMath.formatTime(durationMs), style = HairLabelStyle, color = colors.textMuted)
                }
            }
        }
    }
}
