package com.texfi.w0y.desktop

import com.texfi.w0y.desktop.i18n.S
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SoundProfile
import java.util.Locale

internal enum class PlayerTab(val key: String) {
    Queue("player_queue"),
    Similar("d_tab_similar"),
    Lyrics("player_lyrics"),
    Sound("player_sound"),
    View("d_tab_view"),
    ;

    val label: String get() = S(key)
}

/**
 * Полноэкранный плеер: большая обложка слева, справа очередь, текст, звук (с эквалайзером) и вид.
 * Раскладка Hairline берётся из настроек: «Обложка в рамке» (с панелью), «Минимум» (только диск),
 * «Постер» (размытая обложка на весь экран).
 */
@Composable
fun PlayerScreen(app: AppState) {
    val p = app.player
    var tab by remember { mutableStateOf(if (Look.hair && app.st.hlLayout == PlayerLayout.COVER && app.st.showLyrics) PlayerTab.Lyrics else PlayerTab.Queue) }
    val song = p.current
    val layout = if (Look.hair) app.st.hlLayout else PlayerLayout.COVER
    val glow = if (!Look.hair && app.st.playerCoverGlow) rememberCoverColor(song?.thumbnailUrl) else null
    val tabs = PlayerTab.entries.filter { it != PlayerTab.Lyrics || app.st.showLyrics }
    if (tab !in tabs) tab = PlayerTab.Queue
    val glowBrush = glow?.let { androidx.compose.ui.graphics.Brush.verticalGradient(listOf(it.copy(alpha = 0.30f), androidx.compose.ui.graphics.Color.Transparent)) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // Hairline: диск растёт вместе с окном, но не выше половины высоты и не шире 40% ширины.
        if (Look.hair && layout == PlayerLayout.COVER) {
            NowPlayingLayout(app, tab, tabs, { tab = it }, maxWidth, maxHeight)
            return@BoxWithConstraints
        }
        val coverSize = if (Look.hair) minOf(maxWidth * (if (layout == PlayerLayout.MINIMAL) 0.7f else ThemeState.coverSize.share), maxHeight - 330.dp).coerceIn(220.dp, 640.dp) else 340.dp
        if (Look.hair) {
            if (layout == PlayerLayout.POSTER) {
                PosterBackdrop(song?.thumbnailUrl)
            } else {
                // Свечение растёт из центра обложки: колонка с диском стоит слева (или по центру в «Минимализме»), под шапкой.
                val cx = if (layout == PlayerLayout.MINIMAL) maxWidth / 2 else 28.dp + coverSize / 2
                PlayerBackdrop(app, DpOffset(cx, 28.dp + 44.dp + coverSize / 2), coverSize / 2)
            }
        }
        if (layout == PlayerLayout.MINIMAL) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.TopCenter) {
                PlayerMain(app, coverSize) { tab = it }
            }
        } else {
            Row(Modifier.fillMaxSize().then(if (glowBrush != null) Modifier.background(glowBrush) else Modifier).padding(28.dp)) {
                PlayerMain(app, coverSize) { tab = it }
                Gap(w = 32)
                Column(Modifier.weight(1f).fillMaxHeight()) { PlayerPanel(app, tab, tabs) { tab = it } }
            }
        }
    }
}

/** Правая часть плеера: вкладки (текст, очередь, похожее, звук, вид) и выбранная панель. */
@Composable
internal fun ColumnScope.PlayerPanel(app: AppState, tab: PlayerTab, tabs: List<PlayerTab>, onTab: (PlayerTab) -> Unit) {
    RowCenter {
        tabs.forEach { t ->
            val active = t == tab
            // Hairline: подпись заглавными и тонкая линия акцента под активной вкладкой, без «таблеток».
            val underline by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, tween(Motion.ms(200)))
            Box(
                Modifier.clip(RoundedCornerShape(if (Look.hair) Look.control.coerceAtMost(12.dp) else if (Look.smooth) 50.dp else 4.dp))
                    .background(if (active && !Look.hair) (if (Look.smooth) C.Blue.copy(alpha = 0.22f) else C.SurfaceHigh) else androidx.compose.ui.graphics.Color.Transparent)
                    .drawBehind { if (Look.hair && underline > 0f) drawRect(C.Blue, Offset(0f, size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Size(size.width * underline, 1.dp.toPx())) }
                    .clickable { onTab(t) }.padding(horizontal = 10.dp, vertical = 10.dp),
            ) { Txt(if (Look.smooth && !Look.hair) t.label else Look.label(t.label), pixel = true, size = if (Look.hair) 11 else if (Look.smooth) 14 else 9, color = if (active) (if (Look.hair) C.Text else C.Blue) else C.Muted) }
            Gap(w = 2)
        }
    }
    Gap(h = 12)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        when (tab) {
            PlayerTab.Queue -> QueueList(app)
            PlayerTab.Similar -> SimilarPane(app)
            PlayerTab.Lyrics -> LyricsPane(app)
            PlayerTab.Sound -> SoundPane(app)
            PlayerTab.View -> ViewPane(app)
        }
    }
}

/**
 * «Постер»: обложка на весь экран в самом большом размере, под затемнением из настроек и вуалью к низу.
 * При смене трека новая обложка проявляется поверх старой, а пока играет — очень медленно «дышит» (1 → 1,07).
 */
@Composable
private fun PosterBackdrop(url: String?) {
    val dim = ThemeState.posterDim / 100f
    val drift = androidx.compose.animation.core.rememberInfiniteTransition(label = "poster")
    val zoom by drift.animateFloat(1f, 1.07f, androidx.compose.animation.core.infiniteRepeatable(tween(26_000, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "poster-zoom")
    Box(Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape)) {
        androidx.compose.animation.Crossfade(url, animationSpec = tween(Motion.ms(700))) { shown ->
            val model = hiresThumb(shown)
            if (model != null) {
                coil3.compose.AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier.fillMaxSize().graphicsLayer { if (Motion.full) { scaleX = zoom; scaleY = zoom } },
                )
            }
        }
        Box(Modifier.fillMaxSize().background(C.Background.copy(alpha = dim)))
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color.Transparent, 0.55f to androidx.compose.ui.graphics.Color.Transparent, 1f to C.Background.copy(alpha = 0.92f))))
        // Мягкая виньетка по краям.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(0.6f to androidx.compose.ui.graphics.Color.Transparent, 1f to C.Background.copy(alpha = 0.5f))))
    }
}

/** Левая часть: шапка с переключателями, диск (или обложка), название, действия, перемотка и кнопки. */
@Composable
private fun PlayerMain(app: AppState, coverSize: Dp, onTab: (PlayerTab) -> Unit) {
    val p = app.player
    val song = p.current
    val viz = app.st.playerArt == PlayerArt.VISUALIZER && SpectrumCapture.available
    val hair = Look.hair
    val poster = hair && ThemeState.layout == PlayerLayout.POSTER
    val center = if (hair) Alignment.CenterHorizontally else Alignment.Start
    val vizStyle = app.st.visualizerStyle.forUi(ThemeState.style)
    Column(Modifier.width(coverSize).fillMaxHeight(), horizontalAlignment = center) {
        if (hair) {
            RowCenter(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Txt(if (p.queue.isEmpty()) "" else "${p.index + 1} / ${p.queue.size}", color = C.Muted, size = 11, pixel = true)
                Box(Modifier.weight(1f))
                if (app.st.showLyrics) IconButton(Glyph.Mic, onClick = { onTab(PlayerTab.Lyrics) }, color = C.Muted, size = 18.dp)
                if (SpectrumCapture.available) {
                    IconButton(Glyph.Wave, onClick = { app.setSt { it.copy(playerArt = if (viz) PlayerArt.COVER else PlayerArt.VISUALIZER) } }, color = if (viz) C.Blue else C.Muted, size = 18.dp)
                }
                IconButton(Glyph.Stand, onClick = { app.stand.enter() }, color = C.Muted, size = 18.dp)
            }
            if (poster) {
                // «Постер»: обложка уже на весь фон, на её месте только визуализатор (или пусто).
                Box(Modifier.size(coverSize).pointerInput(Unit) { detectTapGestures(onDoubleTap = { p.toggle() }) }) {
                    if (viz) Visualizer(vizStyle, p.playing, app.st.visualizerSensitivity, app.st.visualizerFps, Modifier.fillMaxSize())
                }
            } else {
                CoverArt(app, coverSize)
            }
            Txt(S("d_hint_disc"), color = C.Muted.copy(alpha = 0.7f), size = 11, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
        } else {
            Box(Modifier.clickable(enabled = SpectrumCapture.available) { app.setSt { it.copy(playerArt = if (viz) PlayerArt.COVER else PlayerArt.VISUALIZER) } }) {
                if (viz) {
                    Box(Modifier.width(coverSize).height(coverSize).clip(RoundedCornerShape(Look.cover)).background(C.Surface)) {
                        if (app.st.visualizerCoverBackdrop) Cover(song?.thumbnailUrl, coverSize, Modifier.alpha(0.3f))
                        Visualizer(vizStyle, p.playing, app.st.visualizerSensitivity, app.st.visualizerFps, Modifier.fillMaxSize().padding(12.dp))
                    }
                } else {
                    PixelCover(song?.thumbnailUrl)
                }
            }
        }
        Gap(h = 16)
        if (hair) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Txt(Look.title(song?.title ?: S("d_idle")), pixel = true, size = 22, maxLines = 2) }
        } else {
            Txt(song?.title ?: S("d_idle"), size = 22, weight = FontWeight.Medium, maxLines = 2)
        }
        Gap(h = 4)
        if (song != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = if (hair) Alignment.Center else Alignment.CenterStart) {
                Txt(
                    song.artist,
                    color = if (hair) C.Text else C.Blue,
                    size = 15,
                    modifier = Modifier.clickable(enabled = song.artistId != null) {
                        song.artistId?.let { app.open(Screen.Artist(it, song.artist, null)) }
                    },
                )
            }
            if (!hair) song.album?.let { a ->
                Txt(
                    a, color = C.Muted, size = 13,
                    modifier = Modifier.clickable(enabled = song.albumId != null) {
                        song.albumId?.let { app.open(Screen.Album(it, a, song.thumbnailUrl)) }
                    },
                )
            }
            if (!p.soundNow.isPlain) Txt(S("d_your_version", p.soundNow.label), color = C.Sand, size = 12, modifier = Modifier.padding(top = 6.dp))
        }
        if (hair) {
            // Всё управление (перемотка, кнопки, сердце, закрепить, скачать, ссылка, таймер) — на нижней панели:
            // здесь только обложка, название и исполнитель, а под ними строка текста, если он есть.
            val lyrics = if (app.st.showLyrics) rememberLyricsOf(app, song) else null
            val line = currentLine(lyrics, p.position, app.st.lyricsDelayMs)
            if (line != null) {
                Gap(h = 14)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Txt(line, color = C.Muted, size = 14, maxLines = 2) }
            }
            return@Column
        }
        Gap(h = 14)
        SeekRow(p, app.st.seekStepSec)
        Gap(h = 12)
        RowCenter(Modifier.fillMaxWidth()) {
            IconButton(Glyph.Shuffle, onClick = p::toggleShuffle, color = if (app.st.shuffle) C.Blue else C.Muted, size = 18.dp, box = 38.dp)
            IconButton(Glyph.Prev, onClick = p::previous, size = 24.dp, box = 44.dp)
            Gap(w = 8)
            Box(
                (if (Look.smooth) Modifier.width(54.dp).height(54.dp).clip(androidx.compose.foundation.shape.CircleShape).background(C.Blue)
                else Modifier.width(54.dp).height(54.dp).clip(RoundedCornerShape(4.dp)).background(C.Blue).border(2.dp, C.BlueDeep, RoundedCornerShape(4.dp)))
                    .clickable(onClick = p::toggle),
                contentAlignment = Alignment.Center,
            ) { Icon(if (p.playing) Glyph.Pause else Glyph.Play, C.OnAccent, 28.dp) }
            Gap(w = 8)
            IconButton(Glyph.Next, onClick = { p.nextClick() }, size = 24.dp, box = 44.dp)
            Box {
                IconButton(Glyph.Repeat, onClick = p::cycleRepeat, color = if (app.st.repeat != RepeatMode.OFF) C.Blue else C.Muted, size = 18.dp, box = 38.dp)
                if (app.st.repeat == RepeatMode.ONE) Txt("1", size = 9, color = C.Blue, weight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            }
            Box(Modifier.weight(1f))
            if (song != null) {
                val liked = song.id in app.likedIds
                IconButton(if (liked) Glyph.Heart else Glyph.HeartOff, onClick = { app.toggleLike(song) }, color = if (liked) C.Danger else C.Muted, size = 20.dp)
                DownloadButton(app, song)
            }
            SleepMenu(app)
        }
    }
}

/**
 * Hairline: диск. Обложка в форме из настроек (диск, скруглённый квадрат, квадрат), вокруг тонкая рамка-прогресс.
 * Двойной клик — пауза, перетаскивание вбок — смена трека, нажать, подержать и вести по кольцу — точная перемотка.
 */
@Composable
fun HairDisc(
    url: String?,
    size: Dp,
    progress: () -> Float,
    playing: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Double) -> Unit,
    time: (Float) -> String,
    content: @Composable (Dp) -> Unit,
) {
    val ring = ThemeState.coverRing
    val spinning = ThemeState.coverSpin && Motion.full && playing
    val turn = androidx.compose.animation.core.rememberInfiniteTransition(label = "disc")
    val turning by turn.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(36_000, easing = androidx.compose.animation.core.LinearEasing)), label = "disc-angle")
    val kind = ThemeState.coverShape
    var scrub by remember { mutableStateOf<Float?>(null) }
    val gap = 7.dp
    val stroke = Look.line
    val inner = size - (gap + stroke * 2) * 2
    val radius = when (kind) {
        CoverShape.DISC -> inner / 2
        CoverShape.ROUNDED -> (inner * 0.07f).coerceIn(3.dp, 10.dp)
        CoverShape.SQUARE -> 0.dp
    }
    val shape = RoundedCornerShape(radius)
    val onToggleNow by androidx.compose.runtime.rememberUpdatedState(onToggle)
    val onNextNow by androidx.compose.runtime.rememberUpdatedState(onNext)
    val onPrevNow by androidx.compose.runtime.rememberUpdatedState(onPrevious)
    val onSeekNow by androidx.compose.runtime.rememberUpdatedState(onSeek)
    val line = C.Border
    val tone = C.Blue
    Box(
        Modifier.size(size)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggleNow() }) }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    if (total <= -120f) onNextNow() else if (total >= 120f) onPrevNow()
                }) { _, dx -> total += dx }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { at -> scrub = angleFraction(at.x - this.size.width / 2f, at.y - this.size.height / 2f) },
                    onDrag = { change, _ ->
                        change.consume()
                        val raw = angleFraction(change.position.x - this.size.width / 2f, change.position.y - this.size.height / 2f)
                        val last = scrub ?: raw
                        var delta = raw - (last - Math.floor(last.toDouble()).toFloat())
                        if (delta > 0.5f) delta -= 1f
                        if (delta < -0.5f) delta += 1f
                        scrub = (last + delta).coerceIn(0f, 1f)
                    },
                    onDragEnd = { scrub?.let { onSeekNow(it.toDouble()) }; scrub = null },
                    onDragCancel = { scrub = null },
                )
            }
            .drawWithCache {
                val w = stroke.toPx()
                val inset = w * 2
                val rect = androidx.compose.ui.geometry.Rect(inset, inset, this.size.width - inset, this.size.height - inset)
                val side = minOf(rect.width, rect.height)
                val r = when (kind) {
                    CoverShape.DISC -> side / 2f
                    CoverShape.ROUNDED -> (side * 0.07f).coerceIn(3.dp.toPx(), 10.dp.toPx())
                    CoverShape.SQUARE -> 0f
                }
                val frame = framePath(rect, r)
                val measure = androidx.compose.ui.graphics.PathMeasure().also { it.setPath(frame, false) }
                val length = measure.length
                val part = androidx.compose.ui.graphics.Path()
                onDrawBehind {
                    if (ring) drawPath(frame, line, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                    val p = (scrub ?: progress()).coerceIn(0f, 1f)
                    if (ring && p > 0f) {
                        part.rewind()
                        measure.getSegment(0f, length * p, part, true)
                        drawPath(part, tone, style = androidx.compose.ui.graphics.drawscope.Stroke(w * (if (scrub != null) 2.4f else 1.4f), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(inner).clip(shape).rotate(if (spinning) turning else 0f)) { content(inner) }
        scrub?.let { f ->
            Txt(
                time(f),
                size = 13,
                pixel = true,
                modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

/** Доля окружности по точке касания: 0 — вверху, по часовой до 1. */
private fun angleFraction(dx: Float, dy: Float): Float {
    val a = Math.atan2(dx.toDouble(), (-dy).toDouble())
    return (((a / (2 * Math.PI)) + 1.0) % 1.0).toFloat()
}

/** Путь рамки по часовой от верхней середины: круг, скруглённый квадрат или квадрат. */
private fun framePath(rect: androidx.compose.ui.geometry.Rect, radius: Float): androidx.compose.ui.graphics.Path {
    val r = radius.coerceIn(0f, minOf(rect.width, rect.height) / 2f)
    val into = androidx.compose.ui.graphics.Path()
    into.moveTo(rect.center.x, rect.top)
    into.lineTo(rect.right - r, rect.top)
    if (r > 0f) into.arcTo(androidx.compose.ui.geometry.Rect(rect.right - 2 * r, rect.top, rect.right, rect.top + 2 * r), -90f, 90f, false)
    into.lineTo(rect.right, rect.bottom - r)
    if (r > 0f) into.arcTo(androidx.compose.ui.geometry.Rect(rect.right - 2 * r, rect.bottom - 2 * r, rect.right, rect.bottom), 0f, 90f, false)
    into.lineTo(rect.left + r, rect.bottom)
    if (r > 0f) into.arcTo(androidx.compose.ui.geometry.Rect(rect.left, rect.bottom - 2 * r, rect.left + 2 * r, rect.bottom), 90f, 90f, false)
    into.lineTo(rect.left, rect.top + r)
    if (r > 0f) into.arcTo(androidx.compose.ui.geometry.Rect(rect.left, rect.top, rect.left + 2 * r, rect.top + 2 * r), 180f, 90f, false)
    into.lineTo(rect.center.x, rect.top)
    return into
}

/** Вкладка «Вид»: что показывать вместо обложки, стиль визуализатора, форма обложки, раскладка, свечение, текст. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ViewPane(app: AppState) {
    val st = app.st
    fun chips(content: @Composable () -> Unit): @Composable () -> Unit = {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (Look.hair) {
            SectionLabel(S("hl_set_cover"))
            chips { CoverShape.entries.forEach { v -> ChoiceChip(v.label, st.hlCoverShape == v) { app.setSt { it.copy(hlCoverShape = v) } } } }()
            SectionLabel(S("hl_set_layout"))
            chips { PlayerLayout.entries.forEach { v -> ChoiceChip(v.label, st.hlLayout == v) { app.setSt { it.copy(hlLayout = v) } } } }()
            SectionLabel(S("d_pback_title"))
            chips { PlayerBack.entries.forEach { v -> ChoiceChip(v.label, st.hlPlayerBack == v) { app.setSt { it.copy(hlPlayerBack = v) } } } }()
            SectionLabel(S("d_cover_size_title"))
            chips { CoverSize.entries.forEach { v -> ChoiceChip(v.label, st.hlCoverSize == v) { app.setSt { it.copy(hlCoverSize = v) } } } }()
            RowCenter { AppToggle(st.hlCoverRing) { v -> app.setSt { it.copy(hlCoverRing = v) } }; Gap(w = 12); Txt(S("d_cover_ring_title"), size = 13) }
            RowCenter { AppToggle(st.hlCoverSpin) { v -> app.setSt { it.copy(hlCoverSpin = v) } }; Gap(w = 12); Txt(S("d_cover_spin_title"), size = 13) }
            if (st.hlLayout == PlayerLayout.POSTER) {
                SectionLabel(S("d_poster_dim_title"))
                chips { listOf(20, 35, 45, 60, 75).forEach { v -> ChoiceChip("$v%", st.hlPosterDim == v) { app.setSt { it.copy(hlPosterDim = v) } } } }()
            }
        }
        SectionLabel(S("settings_cover_glow_title"))
        RowCenter { AppToggle(st.playerCoverGlow) { v -> app.setSt { it.copy(playerCoverGlow = v) } }; Gap(w = 12); Txt(S("settings_cover_glow_title"), size = 13) }
        SectionLabel(S("settings_lyrics_title"))
        RowCenter { AppToggle(st.showLyrics) { v -> app.setSt { it.copy(showLyrics = v) } }; Gap(w = 12); Txt(S("settings_lyrics_title"), size = 13) }
        if (SpectrumCapture.available) {
            SectionLabel(S("viz_style_title"))
            chips {
                ChoiceChip(S("d_art_cover"), st.playerArt == PlayerArt.COVER) { app.setSt { it.copy(playerArt = PlayerArt.COVER) } }
                ChoiceChip(S("d_art_visualizer"), st.playerArt == PlayerArt.VISUALIZER) { app.setSt { it.copy(playerArt = PlayerArt.VISUALIZER) } }
                VisualizerStyle.of(ThemeState.style).forEach { v ->
                    ChoiceChip(v.label, st.visualizerStyle.forUi(ThemeState.style) == v) { app.setSt { it.copy(visualizerStyle = v) } }
                }
            }()
            LabeledSlider(S("viz_sensitivity_title"), "%.2f".format(st.visualizerSensitivity), st.visualizerSensitivity, 0.5f..2f) { v -> app.setSt { it.copy(visualizerSensitivity = v) } }
            RowCenter { AppToggle(st.visualizerCoverBackdrop) { v -> app.setSt { it.copy(visualizerCoverBackdrop = v) } }; Gap(w = 12); Txt(S("viz_backdrop_title"), size = 13) }
        } else {
            Txt(S("d_set_viz_missing"), color = C.Muted, size = 12, maxLines = 3)
        }
        PixelButton(S("d_act_stand"), onClick = { app.stand.enter() }, primary = false)
    }
}

@Composable
internal fun PixelCover(url: String?) {
    if (Look.smooth) {
        Box(Modifier.shadow(18.dp, RoundedCornerShape(Look.cover + 6.dp), clip = false)) { Cover(url, 340.dp) }
        return
    }
    Box(Modifier.drawBehind { drawRect(C.Shadow, androidx.compose.ui.geometry.Offset(6.dp.toPx(), 6.dp.toPx()), size) }.border(2.dp, C.Border, RoundedCornerShape(4.dp))) {
        Cover(url, 340.dp)
    }
}

@Composable
internal fun SeekRow(p: PlayerCtl, stepSec: Int) {
    Column {
        Box(Modifier.fillMaxWidth()) { SegmentedSeek(p.position, p.duration, onSeek = p::seekFraction) }
        RowCenter(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Txt(fmtTime(p.position), color = C.Muted, size = 12)
            Box(Modifier.weight(1f))
            val step = stepSec
            Txt("−$step", color = C.Muted, size = 12, modifier = Modifier.clickable { p.seekSeconds((p.position - step).coerceAtLeast(0.0)) }.padding(horizontal = 8.dp, vertical = 2.dp))
            Txt("+$step", color = C.Muted, size = 12, modifier = Modifier.clickable { p.seekSeconds((p.position + step).coerceAtMost(p.duration)) }.padding(horizontal = 8.dp, vertical = 2.dp))
            Box(Modifier.weight(1f))
            Txt(fmtTime(p.duration), color = C.Muted, size = 12)
        }
    }
}

fun fmtTime(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
internal fun SoundPane(app: AppState) {
    val p = app.player
    val song = p.current
    val s = p.soundNow
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (song == null) {
            Empty(S("d_sound_idle"))
        } else {
            Txt(S("player_version_own"), color = C.Muted, size = 13, maxLines = 2)
            RowCenter {
                PixelButton(S("preset_normal"), onClick = { p.setSound(SoundProfile.Plain) }, primary = s.isPlain, modifier = Modifier)
                Gap(w = 8)
                PixelButton(S("preset_slowed"), onClick = { p.setSound(SoundProfile.Slowed) }, primary = s == SoundProfile.Slowed)
                Gap(w = 8)
                PixelButton(S("preset_sped"), onClick = { p.setSound(SoundProfile.Sped) }, primary = s == SoundProfile.Sped)
            }
            LabeledSlider(S("settings_speed_title"), "%.2f×".format(s.speed), s.speed, 0.5f..2f) { p.setSound(s.copy(speed = it)) }
            LabeledSlider(S("settings_pitch_title"), "%.2f".format(s.pitch), s.pitch, 0.7f..1.4f) { p.setSound(s.copy(pitch = it)) }
            Column {
                SectionLabel(S("settings_reverb_title"))
                Gap(h = 8)
                RowCenter {
                    Reverb.entries.forEach { r ->
                        PixelButton(r.label, onClick = { p.setSound(s.copy(reverb = r)) }, primary = s.reverb == r)
                        Gap(w = 8)
                    }
                }
            }
            Txt(S("d_sound_hint"), color = C.Muted, size = 11)
        }
        // Эквалайзер — общий для всех треков, поэтому доступен и без играющего.
        Divider()
        SectionLabel(S("settings_equalizer_title"))
        EqPanel(app)
    }
}

@Composable
private fun LabeledSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        RowCenter { SectionLabel(label, Modifier.weight(1f)); Txt(valueText, size = 14) }
        Gap(h = 8)
        PixelSlider(value, range, onChange, Modifier.fillMaxWidth())
    }
}

/** Таймер сна: через N минут или до конца трека. */
@Composable
fun SleepMenu(app: AppState) {
    val p = app.player
    var open by remember { mutableStateOf(false) }
    val active = p.sleepRemaining != null || p.sleepAfterTrack
    Box {
        RowCenter {
            if (p.sleepRemaining != null) Txt(fmtTime(p.sleepRemaining!!.toDouble()), color = C.Sand, size = 12)
            if (p.sleepAfterTrack) Txt(S("player_sleep_track"), color = C.Sand, size = 12)
            IconButton(Glyph.Timer, onClick = { open = !open }, color = if (active) C.Sand else C.Muted, size = 18.dp)
        }
        if (open) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, -260),
                onDismissRequest = { open = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Column(Modifier.width(220.dp).background(C.SurfaceHigh, RoundedCornerShape(6.dp)).border(2.dp, C.Border, RoundedCornerShape(6.dp)).padding(6.dp)) {
                    Txt(S("player_sleep_title"), pixel = true, size = 9, color = C.Blue, modifier = Modifier.padding(8.dp))
                    listOf(app.st.sleepTimerDefaultMin, 15, 30, 45, 60).distinct().forEach { m ->
                        Txt(if (m == app.st.sleepTimerDefaultMin) S("d_sleep_default", m) else S("settings_sleep_minutes", m), size = 14, modifier = Modifier.fillMaxWidth().clickable { p.startSleep(m); open = false }.padding(8.dp))
                    }
                    Txt(S("player_sleep_track"), size = 14, modifier = Modifier.fillMaxWidth().clickable { p.sleepUntilTrackEnds(); open = false }.padding(8.dp))
                    if (active) Txt(S("player_sleep_off"), color = C.Sand, size = 14, modifier = Modifier.fillMaxWidth().clickable { p.cancelSleep(); open = false }.padding(8.dp))
                }
            }
        }
    }
}

/** «Похожее»: умные рекомендации к текущему треку (см. Recommender). */
@Composable
internal fun SimilarPane(app: AppState) {
    val song = app.player.current
    if (song == null) {
        Empty(S("d_similar_idle"))
        return
    }
    var reload by remember { mutableStateOf(0) }
    val recs by produceState<List<com.texfi.w0y.data.SongItem>?>(null, song.id, reload) {
        value = null
        value = runCatching { app.recommender.forSeed(song, exclude = app.player.queue.map { it.id }.toSet()) }.getOrDefault(emptyList())
    }
    val list = recs
    Column(Modifier.fillMaxSize()) {
        Txt(S("d_similar_note"), color = C.Muted, size = 12, maxLines = 3)
        Gap(h = 10)
        RowCenter {
            PixelButton(S("queue_enqueue"), onClick = { list?.forEach { app.player.enqueue(it, next = false) } }, enabled = !list.isNullOrEmpty())
            Gap(w = 8)
            PixelButton(S("library_sync"), onClick = { reload++ }, primary = false)
        }
        Gap(h = 10)
        when {
            list == null -> Empty(S("player_finding_similar"))
            list.isEmpty() -> Empty(S("d_similar_none"))
            else -> SongList(app, list)
        }
    }
}
