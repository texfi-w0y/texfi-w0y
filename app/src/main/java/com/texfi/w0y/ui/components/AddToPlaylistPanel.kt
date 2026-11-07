package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel

/**
 * Что можно сделать с треком: одна панель на поиск, плеер и списки —
 * чтобы действия над треком были одинаковы везде, где он встречается.
 *
 * Очередь здесь же, а не отдельной панелью: «следующим» и «в плейлист» —
 * это одно и то же движение мысли («не сейчас, но скоро»), и разводить
 * их по разным окнам значит заставлять выбирать окно до выбора действия.
 */
@Composable
fun AddToPlaylistPanel(
    song: SongItem,
    playlists: List<Pair<Long, String>>,
    newName: String?,
    onNewNameChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    /** Переход к исполнителю; у фита кнопка на каждого. */
    onArtist: ((com.texfi.w0y.data.ArtistLink) -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
) {
    val colors = LocalW0yColors.current
    if (com.texfi.w0y.ui.theme.isHair) {
        HairTrackSheet(song, playlists, newName, onNewNameChange, onPick, onCreate, onDismiss, onPlayNext, onEnqueue, onDownload, onArtist, onAlbum, onCopyLink)
        return
    }
    Box(
        Modifier
            .fillMaxSize()
            // Под стеклянной панелью экран размыт ею самой: затемнять его
            // почти до черноты тогда незачем.
            .background(colors.shadow.copy(alpha = if (com.texfi.w0y.ui.theme.LocalPanelBackdrop.current != null) 0.35f else 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PixelCard(
            label = stringResource(R.string.track_panel_title),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            if (onPlayNext != null || onEnqueue != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    onPlayNext?.let {
                        PixelButton(text = stringResource(R.string.queue_play_next), onClick = it)
                    }
                    onEnqueue?.let {
                        PixelButton(
                            text = stringResource(R.string.queue_enqueue),
                            onClick = it,
                            fill = colors.surfaceHigh,
                        )
                    }
                }
                val artists = if (onArtist != null) song.artistLinks() else emptyList()
                if (onDownload != null || artists.isNotEmpty() || onAlbum != null || onCopyLink != null) {
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        onDownload?.let {
                            PixelButton(text = stringResource(R.string.track_download), onClick = it, fill = colors.surfaceHigh)
                        }
                        onCopyLink?.let {
                            PixelButton(text = stringResource(R.string.copy_link), onClick = it, fill = colors.surfaceHigh)
                        }
                        ArtistButtons(artists) { onArtist?.invoke(it) }
                        onAlbum?.let {
                            PixelButton(text = stringResource(R.string.player_to_album), onClick = it, fill = colors.surfaceHigh)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "❯ ${stringResource(R.string.add_to_playlist_title)}",
                    style = com.texfi.w0y.ui.theme.PixelSectionLabel,
                    color = colors.accent,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (playlists.isEmpty() && newName == null) {
                Text(
                    stringResource(R.string.add_to_playlist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(10.dp))
            }
            // Обычная колонка, а не LazyColumn: ленивый список занимает всю
            // разрешённую высоту даже пустым, и панель растягивалась бы.
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                playlists.forEach { (id, name) ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(id) }
                                .padding(vertical = 8.dp),
                    )
                }
            }
            if (newName != null) {
                BasicTextField(
                    value = newName,
                    onValueChange = onNewNameChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                PixelButton(
                    text = stringResource(R.string.add_to_playlist_create),
                    onClick = { if (newName.isNotBlank()) onCreate(newName) },
                )
            } else {
                PixelButton(text = stringResource(R.string.add_to_playlist_new), onClick = { onNewNameChange("") })
            }
        }
    }
}

/**
 * Кнопки перехода к исполнителям: один — «к артисту», у фита — по кнопке
 * с именем каждого, чтобы открыть любого, а не только первого.
 */
@Composable
fun ArtistButtons(
    artists: List<com.texfi.w0y.data.ArtistLink>,
    onOpen: (com.texfi.w0y.data.ArtistLink) -> Unit,
) {
    val colors = LocalW0yColors.current
    if (artists.size == 1) {
        PixelButton(text = stringResource(R.string.player_to_artist), onClick = { onOpen(artists[0]) }, fill = colors.surfaceHigh)
        return
    }
    artists.forEach { artist ->
        PixelButton(text = artist.name, onClick = { onOpen(artist) }, fill = colors.surfaceHigh)
    }
}

/**
 * Меню трека в Hairline: нижняя панель вместо коробки с кнопками. Сверху трек с обложкой,
 * под ним три плитки частых действий, дальше спокойный список: ссылка, артисты, альбом,
 * плейлисты. Появляется и закрывается панелью — с магнитом и анимацией, как все панели.
 */
@Composable
private fun HairTrackSheet(
    song: SongItem,
    playlists: List<Pair<Long, String>>,
    newName: String?,
    onNewNameChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    onPlayNext: (() -> Unit)?,
    onEnqueue: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onArtist: ((com.texfi.w0y.data.ArtistLink) -> Unit)?,
    onAlbum: (() -> Unit)?,
    onCopyLink: (() -> Unit)?,
) {
    val colors = LocalW0yColors.current
    // Панель показывают условием «if (меню открыто)»: чтобы было видно и появление, и уход,
    // она сама включается кадром позже и сама дожидается конца ухода, прежде чем закрыться.
    var shown by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun close(then: () -> Unit = {}) {
        shown = false
        scope.launch {
            kotlinx.coroutines.delay(com.texfi.w0y.ui.theme.HairTokens.MID_MS.toLong())
            then()
            onDismiss()
        }
    }
    // Действие срабатывает сразу, а панель уезжает следом.
    fun act(block: () -> Unit): () -> Unit = { block(); close() }
    com.texfi.w0y.ui.screens.HairSheetFrame(visible = shown, onDismiss = { close() }, heightFraction = null) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(url = song.thumbnailUrl, px = com.texfi.w0y.data.Thumbnails.ROW, corner = 10, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (song.artist.isNotBlank()) {
                        Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
            val tiles =
                buildList {
                    onPlayNext?.let { add(Triple(com.texfi.w0y.ui.icons.HairGlyph.NEXT, R.string.queue_play_next, it)) }
                    onEnqueue?.let { add(Triple(com.texfi.w0y.ui.icons.HairGlyph.QUEUE, R.string.queue_enqueue, it)) }
                    onDownload?.let { add(Triple(com.texfi.w0y.ui.icons.HairGlyph.DOWNLOAD, R.string.track_download, it)) }
                }
            if (tiles.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiles.forEachIndexed { i, (glyph, label, action) ->
                        MenuTile(glyph, stringResource(label), act(action), Modifier.weight(1f).hairStagger(i))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            onCopyLink?.let { MenuLine(com.texfi.w0y.ui.icons.HairGlyph.LINK, stringResource(R.string.copy_link), act(it)) }
            if (onArtist != null) {
                song.artistLinks().forEach { artist ->
                    MenuLine(com.texfi.w0y.ui.icons.HairGlyph.ARTIST, artist.name, act { onArtist(artist) })
                }
            }
            onAlbum?.let { MenuLine(com.texfi.w0y.ui.icons.HairGlyph.DISC, song.album?.takeIf(String::isNotBlank) ?: stringResource(R.string.player_to_album), act(it)) }
            Text(
                stringResource(R.string.add_to_playlist_title).uppercase(),
                style = com.texfi.w0y.ui.theme.HairLabelStyle,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
            )
            if (playlists.isEmpty() && newName == null) {
                Text(stringResource(R.string.add_to_playlist_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(vertical = 6.dp))
            }
            playlists.forEach { (id, name) ->
                MenuLine(com.texfi.w0y.ui.icons.HairGlyph.NOTE, name, act { onPick(id) })
            }
            if (newName != null) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = newName,
                        onValueChange = onNewNameChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.text.copy(alpha = 0.06f))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    PixelButton(text = stringResource(R.string.add_to_playlist_create), onClick = { if (newName.isNotBlank()) act { onCreate(newName) }() })
                }
            } else {
                MenuLine(com.texfi.w0y.ui.icons.HairGlyph.PLUS, stringResource(R.string.add_to_playlist_new), { onNewNameChange("") }, accent = true)
            }
        }
    }
}

/** Плитка частого действия: значок над подписью, мягкая подложка. */
@Composable
private fun MenuTile(glyph: com.texfi.w0y.ui.icons.HairGlyph, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    val tap = rememberTapHaptic()
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction, 0.95f)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.text.copy(alpha = 0.06f))
            .border(1.dp, colors.text.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null) { tap(); onClick() }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.texfi.w0y.ui.icons.HairIcon(glyph, colors.text, Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(label.asLabel(), style = com.texfi.w0y.ui.theme.HairLabelStyle, color = colors.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Строка списка меню: значок, подпись. */
@Composable
private fun MenuLine(glyph: com.texfi.w0y.ui.icons.HairGlyph, label: String, onClick: () -> Unit, accent: Boolean = false) {
    val colors = LocalW0yColors.current
    val tint = if (accent) com.texfi.w0y.ui.theme.Contrast.ensure(colors.accent, colors.surface) else colors.text
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.texfi.w0y.ui.icons.HairIcon(glyph, if (accent) tint else colors.textMuted, Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
