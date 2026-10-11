package com.texfi.w0y.ui.screens

import com.texfi.w0y.ui.theme.styledClip
import com.texfi.w0y.ui.theme.styledBorder
import androidx.compose.foundation.gestures.detectTapGestures
import com.texfi.w0y.ui.components.forUi
import com.texfi.w0y.ui.components.Visualizer
import com.texfi.w0y.ui.components.LyricsView
import com.texfi.w0y.data.LyricsSize
import com.texfi.w0y.data.PlayerArt
import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.texfi.w0y.ui.components.SwipeRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.texfi.w0y.R
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.EditKind
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.SoundPreset
import com.texfi.w0y.ui.components.PixelSlider
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.playback.OutputKind
import androidx.compose.ui.graphics.graphicsLayer
import com.texfi.w0y.ui.components.Buzz
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.AddToPlaylistPanel
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.ExplicitBadge
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSegmented
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.PlayPauseButton
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.components.TransportButton
import com.texfi.w0y.ui.components.VersionBadge
import com.texfi.w0y.ui.components.asGlow
import com.texfi.w0y.ui.components.rememberCoverTint
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlinx.coroutines.delay

/** Что показано под управлением. Три вкладки вместо одной длинной простыни. */
private enum class PlayerTab(@StringRes val label: Int) {
    QUEUE(R.string.player_queue),
    LYRICS(R.string.player_lyrics),
    SOUND(R.string.player_sound),
}

/**
 * Полноэкранный плеер.
 *
 * Раньше это была одна колонка на пять экранов прокрутки: очередь лежала
 * за звучанием и лирикой, и добраться до неё во время прослушивания было
 * невозможно. Теперь под управлением три вкладки, а их полоса липнет к
 * верху, пока список едет под ней. Обложку можно смахнуть в сторону —
 * это то движение, которым трек переключают, не глядя на экран.
 *
 * Полоса прогресса пиксельная и тянется пальцем; во время перетаскивания
 * показывается позиция пальца, а не то, что сейчас у плеера, — иначе
 * ползунок «дёргается» назад между кадрами.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    onCollapse: () -> Unit,
    onStand: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val haptic = com.texfi.w0y.ui.components.rememberHaptics()
    val navigator = LocalBrowseNavigator.current
    val state by viewModel.player.state.collectAsStateWithLifecycle()
    // Перетаскивание очереди: какая строка поднята и насколько сдвинута.
    // Ключи строк — id трека (с номером повтора), а не позиция: иначе при
    // каждой перестановке строка «менялась» бы на новую и жест обрывался.
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableFloatStateOf(0f) }
    val queueKeys =
        remember(state.queue) {
            val seen = HashMap<String, Int>()
            state.queue.map { song ->
                val n = seen.merge(song.id, 1, Int::plus) ?: 1
                "q-${song.id}-$n"
            }
        }
    val liked by viewModel.isLiked.collectAsStateWithLifecycle()
    val pinned by viewModel.isPinned.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val queueMode by viewModel.queueMode.collectAsStateWithLifecycle()
    val radioLoading by viewModel.loadingRadio.collectAsStateWithLifecycle()
    val findingClean by viewModel.findingClean.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val translation by viewModel.translation.collectAsStateWithLifecycle()
    val lyricsLang by viewModel.lyricsLang.collectAsStateWithLifecycle()
    val translating by viewModel.translating.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val sleepLeft by viewModel.player.sleepRemainingMs.collectAsStateWithLifecycle()
    val output by viewModel.activeOutput.collectAsStateWithLifecycle()
    val trackSound by viewModel.trackSound.collectAsStateWithLifecycle()
    val effectiveSound by viewModel.effectiveSound.collectAsStateWithLifecycle()
    val sleepAfterTrack by viewModel.player.sleepAfterTrack.collectAsStateWithLifecycle()
    val shareFile by viewModel.shareFile.collectAsStateWithLifecycle()
    val sharing by viewModel.sharing.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val song = state.song ?: return

    // Карточка уходит через системное окно выбора: куда именно её отправить,
    // решает человек, а не приложение.
    LaunchedEffect(shareFile) {
        val file = shareFile ?: return@LaunchedEffect
        runCatching {
            val uri =
                FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artist}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(send, null))
        }
        viewModel.consumeShare()
    }

    var position by remember { mutableLongStateOf(0L) }
    var dragPosition by remember { mutableStateOf<Long?>(null) }
    var showPlaylists by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(PlayerTab.QUEUE) }
    var lyricsFull by remember { mutableStateOf(false) }
    val art = settings.playerArt
    val toggleArt: () -> Unit = {
        viewModel.setPlayerArt(if (art == PlayerArt.COVER) PlayerArt.VISUALIZER else PlayerArt.COVER)
    }

    LaunchedEffect(song.id) { viewModel.ensureLyrics(song) }
    LaunchedEffect(song.id, state.isPlaying) {
        // На паузе позиция не меняется, и опрос четыре раза в секунду был
        // просто работой вхолостую: экран перерисовывался, ничего не
        // показывая нового.
        position = viewModel.player.positionMs()
        while (state.isPlaying) {
            delay(POSITION_POLL_MS)
            position = viewModel.player.positionMs()
        }
    }
    // Перемотка сразу двигает показанную позицию: на паузе опроса нет, и
    // раньше экран оставался на старом месте — казалось, что перемотка
    // на паузе не работает.
    // Пока трек после запуска не подготовлен, плеер длительности не знает —
    // берём её из данных трека, иначе ползунок мёртв до первого Play.
    val knownDuration = state.durationMs.takeIf { it > 0 } ?: parseDurationMs(song.durationText)
    val seekNow: (Long) -> Unit = { ms ->
        val target = if (knownDuration > 0) ms.coerceIn(0L, knownDuration) else ms.coerceAtLeast(0L)
        viewModel.player.seekTo(target)
        position = target
    }
    val seekStep: (Long) -> Unit = { delta -> seekNow(position + delta) }

    // Текст во весь экран: тот же компонент, крупнее и по центру.
    val syncedLyrics = lyrics?.synced.orEmpty()
    if (lyricsFull && syncedLyrics.isNotEmpty()) {
        BackHandler { lyricsFull = false }
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            LyricsView(
                lines = syncedLyrics,
                translation = translation,
                positionProvider = viewModel.player::positionMs,
                playing = state.isPlaying,
                size = settings.lyricsSize,
                onSeek = seekNow,
                centered = true,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            )
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SpriteButton(Sprites.collapse, onClick = { lyricsFull = false }, size = 24)
                Spacer(Modifier.weight(1f))
                Text(song.title, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
            }
        }
        return
    }

    val hair = com.texfi.w0y.ui.theme.isHair
    val layout = com.texfi.w0y.ui.theme.hairLook.layout
    val tint by rememberCoverTint(song.thumbnailUrl, colors.accent)
    val glow by animateColorAsState(
        targetValue = if (settings.playerCoverGlow) tint else Color.Transparent,
        animationSpec = tween(420),
        label = "coverGlow",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // Свечение цвета обложки: один мягкий градиент сверху, без
            // блюра. Размытие поверх пиксельной графики читается как
            // чужой эффект, а плоский градиент — как её же фон.
            .drawBehind {
                drawRect(
                    if (hair) {
                        // Hairline: один плавный спуск цвета обложки в фон, без шва.
                        Brush.verticalGradient(
                            0f to glow.asGlow(GLOW_ALPHA),
                            0.5f to glow.asGlow(GLOW_ALPHA * 0.3f),
                            1f to Color.Transparent,
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to glow.asGlow(GLOW_ALPHA),
                            GLOW_STOP to Color.Transparent,
                        )
                    },
                )
            },
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) {
            if (hair) item {
                HairPlayerHeader(
                    onCollapse = onCollapse,
                    position = if (state.queue.size > 1) stringResource(R.string.player_position, state.currentIndex + 1, state.queue.size) else null,
                    visualizerOn = art == PlayerArt.VISUALIZER,
                    onToggleArt = toggleArt,
                    onStand = onStand,
                )
                if (findingClean) {
                    Text(
                        text = stringResource(R.string.player_finding_clean),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent,
                    )
                }
            } else item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpriteButton(Sprites.collapse, onClick = onCollapse)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.player_now_playing),
                            style = PixelSectionLabel,
                            color = colors.accent,
                        )
                        if (state.queue.size > 1) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.player_position,
                                        state.currentIndex + 1,
                                        state.queue.size,
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    SpriteButton(rows = Sprites.wave, onClick = toggleArt, active = art == PlayerArt.VISUALIZER)
                    Spacer(Modifier.width(16.dp))
                    SpriteButton(rows = Sprites.stand, onClick = onStand)
                    Spacer(Modifier.width(16.dp))
                    SpriteButton(
                        rows = Sprites.timer,
                        onClick = { if (sleepLeft == null) viewModel.startSleepTimer() else viewModel.cancelSleepTimer() },
                        active = sleepLeft != null,
                    )
                }
                if (findingClean) {
                    Text(
                        text = stringResource(R.string.player_finding_clean),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent,
                    )
                }
                sleepLeft?.let {
                    Text(
                        text = stringResource(R.string.player_sleep_timer, it / 60_000, (it / 1000) % 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondary,
                    )
                }
            }

            if (hair) item {
                val progressNow = { if (state.durationMs > 0) ((dragPosition ?: position).toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f }
                val visualizer: (@Composable () -> Unit)? =
                    if (art == PlayerArt.VISUALIZER) {
                        {
                            Visualizer(
                                bus = viewModel.spectrum,
                                style = settings.visualizerStyle.forUi(settings.uiStyle),
                                playing = state.isPlaying,
                                sensitivity = settings.visualizerSensitivity,
                                fps = settings.visualizerFps,
                                backdropUrl = song.thumbnailUrl.takeIf { settings.visualizerCoverBackdrop },
                                modifier = Modifier.fillMaxSize().background(colors.surface),
                            )
                        }
                    } else {
                        null
                    }
                when (layout) {
                    com.texfi.w0y.data.PlayerLayout.RING ->
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
                            com.texfi.w0y.ui.components.HairDisc(
                                coverUrl = song.thumbnailUrl,
                                songId = song.id,
                                progress = progressNow,
                                durationMs = state.durationMs,
                                speed = effectiveSound.speed,
                                size = minOf(maxWidth * com.texfi.w0y.ui.theme.HairTokens.DISC_SHARE, 340.dp),
                                onToggle = viewModel.player::togglePlayPause,
                                onNext = { viewModel.player.skipNext() },
                                onPrevious = { viewModel.player.skipPrevious() },
                                onTap = toggleArt,
                                onSeek = seekNow,
                                content = visualizer,
                            )
                        }

                    com.texfi.w0y.data.PlayerLayout.COMPACT ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.texfi.w0y.ui.components.HairDisc(
                                coverUrl = song.thumbnailUrl,
                                songId = song.id,
                                progress = progressNow,
                                durationMs = state.durationMs,
                                speed = effectiveSound.speed,
                                size = 148.dp,
                                onToggle = viewModel.player::togglePlayPause,
                                onNext = { viewModel.player.skipNext() },
                                onPrevious = { viewModel.player.skipPrevious() },
                                onTap = toggleArt,
                                onSeek = seekNow,
                                content = visualizer,
                            )
                            Spacer(Modifier.width(14.dp))
                            HairTitleBlock(song, layout, trackSound, Modifier.weight(1f))
                        }

                    com.texfi.w0y.data.PlayerLayout.MINIMAL -> Spacer(Modifier.height(18.dp))
                }
                if (!settings.gestureHintSeen && layout != com.texfi.w0y.data.PlayerLayout.MINIMAL) {
                    LaunchedEffect(Unit) {
                        delay(HINT_MS)
                        viewModel.markGestureHintSeen()
                    }
                    Text(
                        text = stringResource(R.string.hl_hint_gestures),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    )
                }
            } else item {
                // Обложка «дышит»: на паузе чуть сжимается, при смене трека
                // подпрыгивает пружиной. Оба движения читаются только в слое.
                val pop = remember { androidx.compose.animation.core.Animatable(1f) }
                LaunchedEffect(song.id) {
                    pop.snapTo(0.9f)
                    pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 380f))
                }
                val rest by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (state.isPlaying) 1f else 0.94f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 300f),
                    label = "coverRest",
                )
                // Обложка на весь экран — единственное место, где нужен
                // самый крупный вариант картинки.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val scale = pop.value * rest
                            scaleX = scale
                            scaleY = scale
                        }
                        // Смахивание по обложке переключает трек: на ходу
                        // и в кармане так удобнее, чем попадать в кнопку.
                        .pointerInput(song.id) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    when {
                                        total <= -SWIPE_THRESHOLD_PX -> { haptic(Buzz.TRACK); viewModel.player.skipNext() }
                                        total >= SWIPE_THRESHOLD_PX -> { haptic(Buzz.TRACK); viewModel.player.skipPrevious() }
                                    }
                                },
                            ) { _, dragAmount -> total += dragAmount }
                        },
                ) {
                    if (!com.texfi.w0y.ui.theme.isSmooth) Box(
                        Modifier
                            .matchParentSize()
                            .offset(5.dp, 5.dp)
                            .styledClip(10)
                            .background(colors.shadow),
                    )
                    if (art == PlayerArt.VISUALIZER) {
                        // Визуализатор на месте обложки; нажатие возвращает обложку.
                        Visualizer(
                            bus = viewModel.spectrum,
                            style = settings.visualizerStyle.forUi(settings.uiStyle),
                            playing = state.isPlaying,
                            sensitivity = settings.visualizerSensitivity,
                            fps = settings.visualizerFps,
                            backdropUrl = song.thumbnailUrl.takeIf { settings.visualizerCoverBackdrop },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .styledClip(10)
                                    .background(colors.surface)
                                    .pointerInput(Unit) { detectTapGestures { toggleArt() } },
                        )
                    } else {
                        CoverImage(
                            url = song.thumbnailUrl,
                            px = Thumbnails.HERO,
                            corner = 10,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .styledBorder(10)
                                    .pointerInput(Unit) { detectTapGestures { toggleArt() } },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            item {
                if (hair) {
                    if (layout != com.texfi.w0y.data.PlayerLayout.COMPACT) {
                        HairTitleBlock(song, layout, trackSound)
                        Spacer(Modifier.height(6.dp))
                    }
                    HairActionsRow(
                        song = song,
                        liked = liked,
                        pinned = pinned,
                        sharing = sharing,
                        sleepLeftMs = sleepLeft,
                        onLike = { haptic(if (liked) Buzz.OFF else Buzz.LIKE); viewModel.toggleLike(song) },
                        onPin = { viewModel.togglePin(song) },
                        onAdd = { showPlaylists = true },
                        onDownload = { viewModel.download(song) },
                        onShare = viewModel::shareCard,
                        onSleep = { if (sleepLeft == null) viewModel.startSleepTimer() else viewModel.cancelSleepTimer() },
                    )
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (song.explicit) {
                            ExplicitBadge()
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            text = song.title,
                            style =
                                MaterialTheme.typography.bodyLarge
                                    .copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                            maxLines = 1,
                            // Длинное название едет само, а не обрывается
                            // многоточием: у трека с длинным именем важна
                            // как раз вторая половина.
                            modifier = Modifier.basicMarquee(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            trackSound?.takeIf { !it.isPlain }?.let { own ->
                                Spacer(Modifier.width(8.dp))
                                VersionBadge(own.label)
                            }
                        }
                    }
                    SpriteButton(Sprites.heart, onClick = { haptic(if (liked) Buzz.OFF else Buzz.LIKE); viewModel.toggleLike(song) }, active = liked)
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(Sprites.pin, onClick = { viewModel.togglePin(song) }, active = pinned)
                    Spacer(Modifier.width(14.dp))
                    DownloadButton(song.id, onDownload = { viewModel.download(song) })
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(Sprites.plus, onClick = { showPlaylists = true })
                    Spacer(Modifier.width(14.dp))
                    SpriteButton(
                        rows = Sprites.share,
                        onClick = viewModel::shareCard,
                        active = sharing,
                    )
                }
                // Переход к артисту и альбому прямо из плеера: из него чаще
                // всего и хочется уйти «послушать, что ещё у них есть».
                // У фита — кнопка на каждого исполнителя.
                val artists = song.artistLinks()
                if (hair && (artists.isNotEmpty() || song.albumId != null)) {
                    // Hairline: ссылки строкой моно-подписей, а не стопка кнопок.
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val links = artists.map { artist ->
                            artist.name to {
                                onCollapse()
                                navigator.open(BrowseRoute.Artist(artist.id, artist.name, song.thumbnailUrl))
                            }
                        } + listOfNotNull(
                            song.albumId?.let { id ->
                                stringResource(R.string.player_to_album) to {
                                    onCollapse()
                                    navigator.open(BrowseRoute.Album(id, song.album ?: song.title, song.thumbnailUrl))
                                }
                            },
                        )
                        links.forEachIndexed { i, (label, open) ->
                            if (i > 0) Text(" / ", style = MaterialTheme.typography.labelMedium, color = colors.border, modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = label.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                                modifier = Modifier.clickable(onClick = open).padding(horizontal = 4.dp, vertical = 12.dp),
                            )
                        }
                    }
                } else if (artists.isNotEmpty() || song.albumId != null) {
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        com.texfi.w0y.ui.components.ArtistButtons(artists) { artist ->
                            onCollapse()
                            navigator.open(BrowseRoute.Artist(artist.id, artist.name, song.thumbnailUrl))
                        }
                        song.albumId?.let { id ->
                            PixelButton(
                                text = stringResource(R.string.player_to_album),
                                fill = colors.surfaceHigh,
                                onClick = {
                                    onCollapse()
                                    navigator.open(
                                        BrowseRoute.Album(id, song.album ?: song.title, song.thumbnailUrl),
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (hair) item {
                run {
                    HairSeekLine(
                        positionMs = dragPosition ?: position,
                        durationMs = knownDuration,
                        onDrag = { dragPosition = it },
                        onDragEnd = {
                            dragPosition?.let(seekNow)
                            dragPosition = null
                        },
                    )
                }
                HairTimeRow(
                    positionMs = dragPosition ?: position,
                    durationMs = knownDuration,
                    stepSec = settings.seekStepSec,
                    onBack = { seekStep(-settings.seekStepSec * 1000L) },
                    onForward = { seekStep(settings.seekStepSec * 1000L) },
                )
                Spacer(Modifier.height(8.dp))
            } else item {
                Seekbar(
                    positionMs = dragPosition ?: position,
                    durationMs = state.durationMs,
                    onDrag = { dragPosition = it },
                    onDragEnd = {
                        dragPosition?.let(seekNow)
                        dragPosition = null
                    },
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(dragPosition ?: position),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.weight(1f))
                    // Шаг перемотки берётся из настроек: у подкаста и у
                    // трека на две минуты удобный шаг разный.
                    StepButton(
                        text = stringResource(R.string.player_seek_back, settings.seekStepSec),
                        onClick = { seekStep(-settings.seekStepSec * 1000L) },
                    )
                    Spacer(Modifier.width(8.dp))
                    StepButton(
                        text = stringResource(R.string.player_seek_forward, settings.seekStepSec),
                        onClick = { seekStep(settings.seekStepSec * 1000L) },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTime(state.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (hair) item {
                HairTransport(
                    playing = state.isPlaying,
                    shuffle = state.shuffle,
                    repeatActive = state.repeatMode != Player.REPEAT_MODE_OFF,
                    repeatOne = state.repeatMode == Player.REPEAT_MODE_ONE,
                    onShuffle = viewModel.player::toggleShuffle,
                    onPrevious = viewModel.player::skipPrevious,
                    onToggle = viewModel.player::togglePlayPause,
                    onNext = { viewModel.player.skipNext() },
                    onRepeat = viewModel.player::cycleRepeat,
                )
            } else item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpriteButton(Sprites.shuffle, onClick = viewModel.player::toggleShuffle, active = state.shuffle)
                    TransportButton(Sprites.previous, size = 30, onClick = viewModel.player::skipPrevious)
                    PlayPauseButton(
                        isPlaying = state.isPlaying,
                        size = 46,
                        onClick = viewModel.player::togglePlayPause,
                    )
                    TransportButton(Sprites.next, size = 30, onClick = viewModel.player::skipNext)
                    SpriteButton(
                        rows = Sprites.repeat,
                        onClick = viewModel.player::cycleRepeat,
                        active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    )
                }
                if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    Text(
                        text = stringResource(R.string.player_repeat_one),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                // Куда идёт звук. Показываем только когда это не сам
                // телефон: «играет через динамик» — очевидность, а вот
                // «играет через колонку на кухне» объясняет тишину в
                // наушниках.
                output?.takeIf { it.kind != OutputKind.SPEAKER }?.let { active ->
                    Row(
                        Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelSprite(
                            rows = Sprites.headphones,
                            color = colors.accent,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.player_output,
                                    active.name ?: stringResource(active.kind.label),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            stickyHeader {
                // Полоса вкладок липнет к верху: список под ней едет, а
                // переключиться можно не возвращаясь наверх.
                Box(Modifier.background(colors.background).padding(bottom = 10.dp)) {
                    PixelSegmented(
                        options = PlayerTab.entries.map { stringResource(it.label) },
                        selectedIndex = PlayerTab.entries.indexOf(tab),
                        onSelect = { tab = PlayerTab.entries[it] },
                    )
                }
            }

            when (tab) {
                PlayerTab.QUEUE -> {
                    itemsIndexed(state.queue, key = { index, _ -> queueKeys.getOrElse(index) { "q$index" } }) { index, item ->
                        val removable = index != state.currentIndex
                        // Смахнуть трек вбок — убрать из очереди; играющий не
                        // трогаем: это не «убрать из очереди», а «выключить».
                        SwipeRow(
                            onSwipeRight = if (removable) ({ viewModel.removeFromQueue(index) }) else null,
                            rightLabel = stringResource(R.string.swipe_remove),
                            onSwipeLeft = if (removable) ({ viewModel.removeFromQueue(index) }) else null,
                            leftLabel = stringResource(R.string.swipe_remove),
                        ) {
                            QueueRow(
                                index = index,
                                song = item,
                                active = index == state.currentIndex,
                                lifted = dragFrom == index,
                                liftOffset = { if (dragFrom == index) dragOffset else 0f },
                                onClick = { viewModel.player.playAt(index) },
                                onRemove = if (removable) ({ viewModel.removeFromQueue(index) }) else null,
                                onMeasured = { rowPx = it },
                                // Перетаскивать можно только то, что ещё не играло:
                                // очередь до играющего трека — уже прошлое.
                                onDragStart = if (index > state.currentIndex) ({ dragFrom = index; dragOffset = 0f }) else null,
                                onDrag = { dy ->
                                    dragOffset += dy
                                    val step = rowPx.coerceAtLeast(1f)
                                    while (dragOffset > step / 2 && dragFrom + 1 < state.queue.size) {
                                        viewModel.moveInQueue(dragFrom, dragFrom + 1)
                                        dragFrom += 1
                                        dragOffset -= step
                                    }
                                    while (dragOffset < -step / 2 && dragFrom - 1 > state.currentIndex) {
                                        viewModel.moveInQueue(dragFrom, dragFrom - 1)
                                        dragFrom -= 1
                                        dragOffset += step
                                    }
                                },
                                onDragEnd = {
                                    dragFrom = -1
                                    dragOffset = 0f
                                },
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(14.dp))
                        SectionLabel(stringResource(R.string.player_next_mode))
                        Spacer(Modifier.height(8.dp))
                        PixelSegmented(
                            options = QueueMode.entries.map { stringResource(it.label) },
                            selectedIndex = QueueMode.entries.indexOf(queueMode),
                            onSelect = { viewModel.setQueueMode(QueueMode.entries[it]) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text =
                                if (radioLoading) {
                                    stringResource(R.string.player_finding_similar)
                                } else {
                                    stringResource(queueMode.hint)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (radioLoading) colors.accent else colors.textMuted,
                        )
                        // Таймер сна живёт здесь, а не в углу шапки: это
                        // ответ на вопрос «что будет дальше», а дальше —
                        // тишина. «До конца трека» важнее круглых минут:
                        // засыпают именно так.
                        Spacer(Modifier.height(18.dp))
                        SectionLabel(stringResource(R.string.player_sleep_title))
                        Spacer(Modifier.height(8.dp))
                        val sleepOptions = listOf(null, 0, 15, 30, 60)
                        val selectedSleep =
                            when {
                                sleepAfterTrack -> 1
                                sleepLeft != null -> -1
                                else -> 0
                            }
                        PixelSegmented(
                            options =
                                sleepOptions.map { minutes ->
                                    when (minutes) {
                                        null -> stringResource(R.string.player_sleep_off)
                                        0 -> stringResource(R.string.player_sleep_track)
                                        else -> stringResource(R.string.settings_sleep_minutes, minutes)
                                    }
                                },
                            selectedIndex = selectedSleep,
                            onSelect = { index ->
                                when (val minutes = sleepOptions[index]) {
                                    null -> viewModel.cancelSleepTimer()
                                    0 -> viewModel.sleepAfterTrack()
                                    else -> viewModel.startSleepTimer(minutes)
                                }
                            },
                        )
                    }
                }

                PlayerTab.LYRICS -> {
                    val text = lyrics
                    if (text != null) {
                        item(key = "lang-chips") {
                            LyricsLangRow(
                                selected = lyricsLang,
                                failed = lyricsLang.isNotBlank() && translation == null && !translating,
                                onSelect = viewModel::setLyricsLang,
                            )
                        }
                    }
                    when {
                        text == null ->
                            item {
                                Text(
                                    text = stringResource(R.string.player_lyrics_none),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                )
                            }

                        text.synced.isNotEmpty() -> {
                            item(key = "lyrics-view") {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PixelSegmented(
                                            options = LyricsSize.entries.map { stringResource(it.label) },
                                            selectedIndex = settings.lyricsSize.ordinal,
                                            onSelect = { viewModel.setLyricsSize(LyricsSize.entries[it]) },
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        SpriteButton(Sprites.fullscreen, onClick = { lyricsFull = true }, size = 22)
                                    }
                                    LyricsView(
                                        lines = text.synced,
                                        translation = translation,
                                        positionProvider = viewModel.player::positionMs,
                                        playing = state.isPlaying,
                                        size = settings.lyricsSize,
                                        onSeek = seekNow,
                                        modifier = Modifier.fillMaxWidth().height(LYRICS_HEIGHT),
                                    )
                                }
                            }
                        }

                        else ->
                            item {
                                Text(
                                    text =
                                        translation?.let { tr ->
                                            text.plain.orEmpty().lines().mapIndexed { i, l ->
                                                val t = tr.getOrNull(i).orEmpty()
                                                if (l.isBlank() || t.isBlank() || t == l) l else "$l\n$t"
                                            }.joinToString("\n")
                                        } ?: text.plain.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                )
                            }
                    }
                }

                PlayerTab.SOUND ->
                    item {
                        Text(
                            text = stringResource(R.string.player_version_title),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.player_version_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        PixelSegmented(
                            options = SoundPreset.entries.map { stringResource(it.label) },
                            // −1 значит «ни один»: когда скорость подкручена
                            // руками в настройках, подсвечивать готовый пресет
                            // было бы враньём.
                            selectedIndex = SoundPreset.entries.indexOfFirst { it.matches(effectiveSound) },
                            onSelect = { viewModel.setSoundPreset(SoundPreset.entries[it]) },
                        )
                        Spacer(Modifier.height(12.dp))
                        if (hair) {
                            val reverbNames = Reverb.entries.associateWith { stringResource(it.label) }
                            HairDials(
                                sound = effectiveSound,
                                reverbLabel = { reverbNames.getValue(it) },
                                onSpeed = viewModel::setTrackSpeed,
                                onPitch = viewModel::setTrackPitch,
                                onReverb = viewModel::setTrackReverb,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.player_speed_custom) + "  " + com.texfi.w0y.data.NumberText.speed(effectiveSound.speed),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                            PixelSlider(
                                value = effectiveSound.speed,
                                range = 0.2f..2.0f,
                                step = 0.05f,
                                onValueChange = viewModel::setTrackSpeed,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text =
                                    buildString {
                                        append(
                                            stringResource(
                                                R.string.player_sound_speed,
                                                com.texfi.w0y.data.NumberText.speed(effectiveSound.speed).removeSuffix("\u00d7"),
                                            ),
                                        )
                                        if (effectiveSound.pitch != 1f) {
                                            append(" ")
                                            append(
                                                stringResource(
                                                    R.string.player_sound_pitch,
                                                    effectiveSound.pitch.toString(),
                                                ),
                                            )
                                        }
                                        if (effectiveSound.reverb != Reverb.OFF) {
                                            append(" ")
                                            append(
                                                stringResource(
                                                    R.string.player_sound_reverb,
                                                    stringResource(effectiveSound.reverb.label).lowercase(),
                                                ),
                                            )
                                        }
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                modifier = Modifier.weight(1f),
                            )
                            // Кнопка есть только когда есть что снимать:
                            // «сбросить» при отсутствии версии — пустышка.
                            if (trackSound != null) {
                                Spacer(Modifier.width(10.dp))
                                PixelButton(
                                    text = stringResource(R.string.player_version_clear),
                                    onClick = viewModel::clearTrackSound,
                                    fill = colors.surfaceHigh,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text =
                                if (trackSound == null) {
                                    stringResource(R.string.player_version_global)
                                } else {
                                    stringResource(R.string.player_version_own)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (trackSound == null) colors.textMuted else colors.accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.player_sound_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(20.dp))
                        EditFinder(songId = song.id, edit = edit, onFind = viewModel::findEdit)
                    }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }

        if (showPlaylists) {
            AddToPlaylistPanel(
                song = song,
                playlists = playlists.map { it.id to it.name },
                newName = newPlaylistName,
                onNewNameChange = { newPlaylistName = it },
                onPick = { id ->
                    viewModel.addToPlaylist(id, song)
                    showPlaylists = false
                },
                onCreate = { name ->
                    viewModel.createPlaylistWith(name, song)
                    newPlaylistName = null
                    showPlaylists = false
                },
                onDismiss = {
                    showPlaylists = false
                    newPlaylistName = null
                },
            )
        }
    }
}

@Composable
private fun QueueRow(
    index: Int,
    song: SongItem,
    active: Boolean,
    lifted: Boolean,
    liftOffset: () -> Float,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    onMeasured: (Float) -> Unit,
    onDragStart: (() -> Unit)?,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val hair = com.texfi.w0y.ui.theme.isHair
    val tone = com.texfi.w0y.ui.theme.Contrast.ensure(colors.accent, colors.background)
    Column(Modifier.fillMaxWidth().graphicsLayer { translationY = liftOffset() }) {
    Row(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasured(it.height.toFloat()) }
            // Поднятая строка едет за пальцем и заметно светлее соседей.
            .background(if (lifted) colors.surfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = if (hair) com.texfi.w0y.ui.theme.hairLook.rowPad else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hair) {
            // Номер — моно; у играющего вместо него качаются четыре линии.
            Box(Modifier.width(36.dp).height(24.dp), contentAlignment = Alignment.CenterStart) {
                if (active) {
                    com.texfi.w0y.ui.components.HairPlayingMark(Modifier.size(24.dp), rect = true)
                } else {
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }
        } else Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = if (active) colors.accent else colors.textMuted,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) (if (hair) tone else colors.accent) else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                song.sound?.takeIf { !it.isPlain }?.let {
                    Spacer(Modifier.width(6.dp))
                    VersionBadge(it.label)
                }
            }
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Ручка переноса: зажал и повёл вверх-вниз, трек меняется местами
        // с соседями на ходу. Только у треков после играющего.
        onDragStart?.let { start ->
            Spacer(Modifier.width(6.dp))
            DragGrip(onStart = start, onDrag = onDrag, onEnd = onDragEnd)
        }
        onRemove?.let {
            Spacer(Modifier.width(6.dp))
            SpriteButton(Sprites.close, onClick = it, size = 16)
        }
    }
    if (hair) {
        Box(Modifier.fillMaxWidth().height(com.texfi.w0y.ui.theme.hairLook.line).background(colors.border.copy(alpha = 0.6f)))
    }
    }
}

/** Ручка перетаскивания: два ряда квадратов, область нажатия шире рисунка. */
@Composable
private fun DragGrip(onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    val colors = LocalW0yColors.current
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    Box(
        Modifier
            .size(36.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start() },
                    onDrag = { change, amount ->
                        change.consume()
                        drag(amount.y)
                    },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (com.texfi.w0y.ui.theme.isHair) {
            com.texfi.w0y.ui.icons.HairIcon(com.texfi.w0y.ui.icons.HairGlyph.GRIP, colors.textMuted, Modifier.size(18.dp))
        } else Canvas(Modifier.size(14.dp, 12.dp)) {
            val cell = 4.dp.toPx()
            val gap = 2.dp.toPx()
            for (row in 0 until 3) {
                for (col in 0 until 2) {
                    drawRect(
                        color = colors.textMuted,
                        topLeft = Offset(col * (cell + gap), row * (cell + gap) - if (row == 2) 2.dp.toPx() else 0f),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}

/** Маленькая кнопка перемотки на фиксированный шаг. */
@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .styledClip(6)
            .background(colors.surfaceHigh)
            .styledBorder(6)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.text)
    }
}

@Composable
private fun Seekbar(
    positionMs: Long,
    durationMs: Long,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Своя анимация на перемотку: пока тянут, бегунок и полоса вырастают.
    // Палец закрывает бегунок собой, и без этого непонятно, ведёшь ты
    // перемотку или просто скроллишь экран.
    val thumb by animateDpAsState(
        targetValue = if (dragging) 18.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "seekThumb",
    )
    val track by animateDpAsState(
        targetValue = if (dragging) 8.dp else 6.dp,
        animationSpec = tween(140),
        label = "seekTrack",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            // Ширина берётся из измерения, а не из жеста: иначе бегунок
            // стоит в левом краю, пока по нему не проведут пальцем.
            .onSizeChanged { widthPx = it.width }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        // Первое касание сразу ставит позицию под палец:
                        // иначе перемотка начинается с того места, где
                        // трек и был, и палец «догоняет» бегунок.
                        if (durationMs > 0) {
                            onDrag(((offset.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        onDragEnd()
                    },
                ) { change, _ ->
                    if (durationMs > 0) {
                        val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                        onDrag((ratio * durationMs).toLong())
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(track)
                .background(colors.surfaceHigh),
        )
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(track)
                .background(colors.secondary),
        )
        // Квадратный бегунок вместо круглой Material-ручки: круг здесь
        // читается как чужой элемент поверх пиксельной графики.
        Box(
            Modifier
                .padding(start = with(density) { (widthPx * progress).toDp() })
                .size(thumb)
                .background(colors.text),
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Пока играет — четыре раза в секунду; на паузе опроса нет вовсе. */
private const val POSITION_POLL_MS = 250L

/** Подсказка жестов висит, пока её не прочтут, но не дольше. */
private const val HINT_MS = 9_000L
private val LYRICS_HEIGHT = 440.dp

/** Смахивание короче этого — случайное движение, а не переключение трека. */
private const val SWIPE_THRESHOLD_PX = 120f

private const val GLOW_ALPHA = 0.3f
private const val GLOW_STOP = 0.55f

/**
 * Готовые переделки с YouTube — вторая дорога к slowed рядом со своей.
 *
 * Своя версия замедляет оригинал; здесь — чужой залитый эдит, со своим
 * сведением и эхом. Ответ показывается только для того трека, к которому
 * он относится: «не нашлось» от прошлой песни на новой было бы враньём.
 */
@Composable
private fun EditFinder(
    songId: String,
    edit: EditSearch,
    onFind: (EditKind) -> Unit,
) {
    val colors = LocalW0yColors.current
    SectionLabel(stringResource(R.string.player_edit_title))
    Text(
        text = stringResource(R.string.player_edit_desc),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textMuted,
    )
    Spacer(Modifier.height(10.dp))
    val searching = edit is EditSearch.Searching
    Row {
        PixelButton(
            text = "SLOWED",
            onClick = { onFind(EditKind.SLOWED) },
            enabled = !searching,
            fill = colors.surfaceHigh,
        )
        Spacer(Modifier.width(12.dp))
        PixelButton(
            text = "SPED UP",
            onClick = { onFind(EditKind.SPED_UP) },
            enabled = !searching,
            fill = colors.surfaceHigh,
        )
    }
    val status =
        when (edit) {
            is EditSearch.Searching -> if (edit.songId == songId) stringResource(R.string.player_edit_searching) else null
            is EditSearch.NotFound -> if (edit.songId == songId) stringResource(R.string.player_edit_none) else null
            is EditSearch.Playing -> if (edit.songId == songId) stringResource(R.string.player_edit_playing, edit.title) else null
            EditSearch.Idle -> null
        }
    if (status != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (edit is EditSearch.NotFound) colors.secondary else colors.accent,
        )
    }
}

private val LYRICS_LANGS = listOf("en", "ru", "uk", "pl", "es", "de", "fr", "it", "pt", "tr", "ja", "ko", "zh-CN")

@Composable
private fun LyricsLangRow(selected: String, failed: Boolean, onSelect: (String) -> Unit) {
    val colors = LocalW0yColors.current
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(
            text =
                if (failed) {
                    stringResource(R.string.player_lyrics_translate_fail)
                } else {
                    stringResource(R.string.player_lyrics_translate)
                },
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) colors.accent else colors.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("") + LYRICS_LANGS) { code ->
                val active = code == selected
                Text(
                    text = if (code.isEmpty()) stringResource(R.string.player_lyrics_translate_off) else code.substringBefore('-').uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (active) colors.text else colors.textMuted,
                    modifier =
                        Modifier
                            .background(if (active) colors.accentDeep else colors.surface)
                            .border(2.dp, if (active) colors.accent else colors.border)
                            .clickable { onSelect(code) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** «3:15» или «1:02:03» в миллисекундах; непонятное — ноль. */
private fun parseDurationMs(text: String?): Long {
    val parts = text?.trim()?.split(':')?.map { it.toLongOrNull() ?: return 0L } ?: return 0L
    if (parts.isEmpty() || parts.size > 3) return 0L
    return parts.fold(0L) { acc, v -> acc * 60 + v } * 1000L
}
