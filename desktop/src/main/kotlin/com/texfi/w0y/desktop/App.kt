package com.texfi.w0y.desktop

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.texfi.w0y.desktop.i18n.S

@Composable
fun App(app: AppState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val backdrop = if (Look.glass) rememberBackdrop() else null
    val screens: @Composable () -> Unit = {
        when (val s = app.screen) {
            Screen.Home -> HomeScreen(app)
            Screen.Search -> SearchScreen(app)
            Screen.Library -> LibraryScreen(app)
            Screen.Queue, Screen.Player -> PlayerScreen(app)
            Screen.Downloaded -> DownloadedScreen(app)
            Screen.Settings -> SettingsScreen(app)
            Screen.Stats -> StatsScreen(app)
            Screen.Liked -> LikedScreen(app)
            Screen.History -> HistoryScreen(app)
            is Screen.Album -> AlbumScreen(app, s)
            is Screen.Artist -> ArtistScreen(app, s)
            is Screen.Remote -> RemoteScreen(app, s)
            is Screen.Local -> LocalScreen(app, s)
        }
    }
    Box(Modifier.fillMaxSize().background(C.Background).focusRequester(focus).focusable().appKeys(app)) {
        if (Look.smooth) {
            SmoothShell(app, backdrop, screens)
        } else {
            PixelShell(app, screens)
        }
        Overlays(app)
    }
}

@Composable
private fun PixelShell(app: AppState, screens: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Sidebar(app)
        Box(Modifier.width(2.dp).fillMaxHeight().background(C.Border))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (app.st.theme != ThemeMode.LIGHT) Starfield(app.st.animatedBackground, Modifier.fillMaxSize())
                screens()
            }
            PlayerBar(app)
        }
    }
}

private val SidebarWidth = 208.dp
private val BarHeight = 84.dp
private val Gutter = 12.dp

/**
 * Smooth: экран на всё окно, а боковое меню и нижняя панель плывут над ним
 * скруглёнными панелями. Со стеклом они размывают то, что под ними, — как нижние
 * панели на телефоне.
 */
@Composable
private fun SmoothShell(app: AppState, backdrop: Backdrop?, screens: @Composable () -> Unit) {
    val cover = rememberCoverColor(app.player.current?.thumbnailUrl)
    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalBarsInset provides BarsInset(bottom = BarHeight + Gutter * 2, start = SidebarWidth + Gutter * 2),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().let { if (backdrop != null) it.backdropSource(backdrop) else it },
            ) {
                SmoothBackground(cover)
                Box(Modifier.fillMaxSize().padding(start = SidebarWidth + Gutter * 2)) { screens() }
            }
            Sidebar(app, Modifier.padding(Gutter).width(SidebarWidth).fillMaxHeight().glassOrMatte(RoundedCornerShape(28.dp), backdrop))
            PlayerBar(
                app,
                Modifier.align(Alignment.BottomCenter).padding(start = SidebarWidth + Gutter * 2, end = Gutter, bottom = Gutter)
                    .fillMaxWidth().height(BarHeight).glassOrMatte(RoundedCornerShape(28.dp), backdrop),
            )
        }
    }
}

@Composable
private fun Modifier.glassOrMatte(shape: androidx.compose.ui.graphics.Shape, backdrop: Backdrop?): Modifier =
    if (Look.glass) glassBar(shape, backdrop) else this.shadow(10.dp, shape, clip = false).matteCard(shape, C.Surface)

/** Фон Smooth: статичные пятна акцента, фиолетового и цвета обложки — перерисовки на каждый кадр нет. */
@Composable
private fun SmoothBackground(cover: Color?) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(C.Background)
            val r = maxOf(size.width, size.height)
            drawCircle(Brush.radialGradient(listOf(C.Blue.copy(alpha = if (C.isLight) 0.30f else 0.34f), Color.Transparent), Offset(size.width * .18f, size.height * .12f), r * .55f), r * .55f, Offset(size.width * .18f, size.height * .12f))
            drawCircle(Brush.radialGradient(listOf(C.GlassViolet.copy(alpha = if (C.isLight) 0.22f else 0.28f), Color.Transparent), Offset(size.width * .88f, size.height * .92f), r * .6f), r * .6f, Offset(size.width * .88f, size.height * .92f))
            if (cover != null) {
                drawCircle(Brush.radialGradient(listOf(cover.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * .75f, size.height * .15f), r * .45f), r * .45f, Offset(size.width * .75f, size.height * .15f))
            }
        },
    )
}

/** Звёзды на фоне: медленно мерцают, если включён «Живой фон»; иначе рисуются один раз. */
@Composable
fun Starfield(animated: Boolean, modifier: Modifier = Modifier) {
    val phase =
        if (animated) {
            androidx.compose.animation.core.rememberInfiniteTransition().animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = 
                androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(6000, easing = androidx.compose.animation.core.LinearEasing)),
            ).value
        } else {
            0f
        }
    val stars = remember { val r = java.util.Random(7); List(70) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Box(
        modifier.drawBehind {
            stars.forEach { (x, y, seed) ->
                val twinkle = 0.25f + 0.55f * (0.5f + 0.5f * kotlin.math.sin((phase + seed) * 6.2832f))
                drawRect(
                    C.Blue.copy(alpha = if (animated) twinkle * 0.5f else 0.25f),
                    Offset(x * size.width, y * size.height),
                    Size(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        },
    )
}

@Composable
private fun Sidebar(app: AppState, modifier: Modifier = Modifier) {
    val smooth = Look.smooth
    Column(
        (if (smooth) modifier else modifier.width(190.dp).fillMaxHeight().background(C.Surface)).padding(vertical = 22.dp, horizontal = 14.dp),
    ) {
        Txt("w0y", pixel = true, size = if (smooth) 26 else 22, color = C.Blue, weight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        Txt("texfi", pixel = true, size = if (smooth) 12 else 9, color = C.Sand, modifier = Modifier.padding(start = 8.dp, bottom = 24.dp))
        NavItem(S("tab_home"), Glyph.Home, app.screen == Screen.Home) { app.tab(Screen.Home) }
        NavItem(S("tab_search"), Glyph.Search, app.screen == Screen.Search) { app.tab(Screen.Search) }
        NavItem(S("tab_library"), Glyph.Library, app.screen in listOf(Screen.Library, Screen.Liked, Screen.History, Screen.Downloaded, Screen.Stats) || app.screen is Screen.Local) { app.tab(Screen.Library) }
        NavItem(S("d_tab_player"), Glyph.Wave, app.screen == Screen.Player || app.screen == Screen.Queue) { app.tab(Screen.Player) }
        NavItem(S("settings_title"), Glyph.Gear, app.screen == Screen.Settings) { app.tab(Screen.Settings) }
        Box(Modifier.weight(1f))
        app.lib.accountName?.let {
            RowCenter(Modifier.padding(8.dp)) {
                Cover(app.lib.accountAvatar, 28.dp, circle = true)
                Gap(w = 8)
                Txt(it, color = C.Muted, size = 12)
            }
        }
    }
}

@Composable
private fun NavItem(label: String, glyph: Glyph, active: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(if (Look.smooth) 18.dp else 6.dp)
    RowCenter(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).hoverable(source).clip(shape)
            .background(if (active) (if (Look.smooth) C.Blue.copy(alpha = 0.22f) else C.SurfaceHigh) else hoverBackground(source))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Icon(glyph, if (active) C.Blue else C.Muted, 20.dp)
        Gap(w = 12)
        Txt(label, color = if (active) C.Text else C.Muted, size = 15, weight = if (active && Look.smooth) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun fmt(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PlayerBar(app: AppState, modifier: Modifier = Modifier) {
    val p = app.player
    val song = p.current
    val smooth = Look.smooth
    Column(if (smooth) modifier else modifier.fillMaxWidth().background(C.Surface)) {
        if (!smooth) Box(Modifier.fillMaxWidth().height(2.dp).background(C.Border))
        Box(Modifier.fillMaxWidth().padding(horizontal = if (smooth) 26.dp else 18.dp).padding(top = if (smooth) 10.dp else 10.dp)) {
            SegmentedSeek(p.position, p.duration, onSeek = p::seekFraction)
        }
        RowCenter(Modifier.fillMaxWidth().then(if (smooth) Modifier.weight(1f) else Modifier.height(78.dp)).padding(horizontal = if (smooth) 22.dp else 18.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (song != null) {
                    Cover(song.thumbnailUrl, 52.dp, Modifier.clickable { app.open(Screen.Player) })
                    Gap(w = 12)
                    Column(Modifier.weight(1f, fill = false).clickable { app.open(Screen.Player) }) {
                        Txt(song.title, size = 15)
                        Txt(p.error ?: if (p.loading) S("d_loading") else song.artist, color = if (p.error != null) C.Sand else C.Muted, size = 12)
                    }
                    Gap(w = 6)
                    val liked = song.id in app.likedIds
                    IconButton(if (liked) Glyph.Heart else Glyph.HeartOff, onClick = { app.toggleLike(song) }, color = if (liked) C.Danger else C.Muted, size = 18.dp)
                    DownloadButton(app, song)
                    PlaylistMenu(app, song)
                } else {
                    Txt(p.error ?: S("d_idle"), color = if (p.error != null) C.Sand else C.Muted, size = 14)
                }
            }
            RowCenter {
                IconButton(Glyph.Prev, onClick = p::previous, size = 22.dp, box = 40.dp)
                Gap(w = 6)
                Box(
                    (if (smooth) Modifier.size(48.dp).clip(CircleShape).background(C.Blue)
                    else Modifier.size(46.dp).clip(RoundedCornerShape(4.dp)).background(C.Blue).border(2.dp, C.BlueDeep, RoundedCornerShape(4.dp)))
                        .clickable(onClick = p::toggle),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (p.playing) Glyph.Pause else Glyph.Play, C.OnAccent, 24.dp) }
                Gap(w = 6)
                IconButton(Glyph.Next, onClick = { p.nextClick() }, size = 22.dp, box = 40.dp)
            }
            RowCenter(Modifier.weight(1f), ) {
                Box(Modifier.weight(1f))
                Txt("${fmt(p.position)} / ${fmt(p.duration)}", color = C.Muted, size = 12)
                Gap(w = 16)
                SleepMenu(app)
                Gap(w = 10)
                Icon(Glyph.Volume, C.Muted, 18.dp)
                Gap(w = 6)
                VolumeBar(p.volume, onChange = p::changeVolume)
            }
        }
    }
}


/** Полоса перемотки из сегментов, как на телефоне: клик и перетаскивание. */
@Composable
fun SegmentedSeek(position: Double, duration: Double, onSeek: (Double) -> Unit) {
    val fraction = if (duration > 0) (position / duration).coerceIn(0.0, 1.0).toFloat() else 0f
    Box(
        Modifier.fillMaxWidth().height(14.dp)
            .pointerInput(duration) {
                detectTapGestures { onSeek((it.x / size.width).toDouble().coerceIn(0.0, 1.0)) }
            }.pointerInput(duration) {
                detectDragGestures { change, _ -> onSeek((change.position.x / size.width).toDouble().coerceIn(0.0, 1.0)) }
            }.drawBehind {
                if (Look.smooth) {
                    val h = 5.dp.toPx()
                    val y = (size.height - h) / 2
                    drawRoundRect(C.SurfaceHigh, Offset(0f, y), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawRoundRect(C.Blue, Offset(0f, y), Size(size.width * fraction, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawCircle(C.Blue, 6.dp.toPx(), Offset(size.width * fraction, size.height / 2))
                    return@drawBehind
                }
                val segments = 80
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (segments - 1)) / segments
                val filled = (fraction * segments).toInt()
                repeat(segments) { i ->
                    drawRect(
                        if (i < filled) C.BlueLight else C.SurfaceHigh,
                        Offset(i * (w + gap), size.height / 2 - 3.dp.toPx()),
                        Size(w, 6.dp.toPx()),
                    )
                }
            },
    )
}

@Composable
private fun VolumeBar(volume: Int, onChange: (Int) -> Unit) {
    Box(
        Modifier.width(96.dp).height(14.dp)
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width * 100).toInt()) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> onChange((c.position.x / size.width * 100).toInt().coerceIn(0, 100)) } }
            .drawBehind {
                if (Look.smooth) {
                    val h = 5.dp.toPx()
                    val y = (size.height - h) / 2
                    val f = volume / 100f
                    drawRoundRect(C.SurfaceHigh, Offset(0f, y), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawRoundRect(C.Sand, Offset(0f, y), Size(size.width * f, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawCircle(C.Sand, 6.dp.toPx(), Offset(size.width * f, size.height / 2))
                    return@drawBehind
                }
                val segments = 10
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (segments - 1)) / segments
                val filled = volume * segments / 100
                repeat(segments) { i ->
                    drawRect(if (i < filled) C.Sand else C.SurfaceHigh, Offset(i * (w + gap), size.height / 2 - 3.dp.toPx()), Size(w, 6.dp.toPx()))
                }
            },
    )
}

/** «В плейлист»: список своих плейлистов во всплывающем окне над кнопкой. */
@Composable
private fun PlaylistMenu(app: AppState, song: com.texfi.w0y.data.SongItem) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(Glyph.Plus, onClick = { open = !open }, color = C.Muted, size = 18.dp)
        if (open) {
            Popup(alignment = Alignment.TopStart, offset = IntOffset(0, -220), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Column(Modifier.width(230.dp).background(C.SurfaceHigh, RoundedCornerShape(6.dp)).border(2.dp, C.Border, RoundedCornerShape(6.dp)).padding(6.dp)) {
                    Txt(S("d_to_playlist"), pixel = true, size = 9, color = C.Blue, modifier = Modifier.padding(8.dp))
                    if (app.editablePlaylists.isEmpty()) Txt(S("d_make_playlist_first"), color = C.Muted, size = 12, modifier = Modifier.padding(8.dp), maxLines = 3)
                    app.editablePlaylists.forEach { pl ->
                        val has = pl.songs.any { it.id == song.id }
                        Txt(
                            if (has) "✓ ${pl.name}" else pl.name,
                            color = if (has) C.Sand else C.Text,
                            size = 14,
                            modifier = Modifier.fillMaxWidth().clickable { app.addToPlaylist(pl.id, song); open = false }.padding(8.dp),
                        )
                    }
                    Txt(S("queue_play_next"), size = 14, modifier = Modifier.fillMaxWidth().clickable { app.player.enqueue(song, next = true); open = false }.padding(8.dp))
                }
            }
        }
    }
}
