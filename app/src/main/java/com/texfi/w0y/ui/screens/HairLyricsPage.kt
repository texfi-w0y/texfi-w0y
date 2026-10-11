package com.texfi.w0y.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
            HairIconButton(HairGlyph.LYRICS, stringResource(R.string.player_lyrics), onClose, tint = tone)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { translate() }
        controls()
    }
}
