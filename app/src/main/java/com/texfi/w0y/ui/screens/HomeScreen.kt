package com.texfi.w0y.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import com.texfi.w0y.ui.theme.styledSurface
import com.texfi.w0y.ui.theme.styledClip
import com.texfi.w0y.ui.theme.styledBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.PixelPullRefresh
import com.texfi.w0y.ui.components.Stagger
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.texfi.w0y.ui.theme.asLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.R
import com.texfi.w0y.data.db.PinEntity
import com.texfi.w0y.ui.components.DownloadButton
import com.texfi.w0y.ui.components.ArtistTile
import com.texfi.w0y.ui.components.Gutter
import com.texfi.w0y.ui.components.ReleaseTile
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SectionHeader
import com.texfi.w0y.ui.components.ShelfTitle
import com.texfi.w0y.ui.components.SkeletonRow
import com.texfi.w0y.ui.components.SongRow
import com.texfi.w0y.ui.components.SongTile
import com.texfi.w0y.ui.components.SpeedDialTile
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel

private const val DIAL_COLUMNS = 3
private const val DIAL_ROWS = 3
private const val DIAL_PAGE = DIAL_COLUMNS * DIAL_ROWS
private val TILE_GAP = 10.dp
private const val HAIR_COLUMNS = 3
private const val HAIR_ROWS = 2

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLocalPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    home: HomeViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val hair = com.texfi.w0y.ui.theme.isHair
    val playing by viewModel.player.state.collectAsStateWithLifecycle()
    val navigator = LocalBrowseNavigator.current
    val dial by viewModel.speedDial.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val shelves by home.shelves.collectAsStateWithLifecycle()
    val recBlocks by home.blocks.collectAsStateWithLifecycle()
    val loadingShelves by home.loading.collectAsStateWithLifecycle()
    val recommendationsFailed by home.failed.collectAsStateWithLifecycle()
    val showRecommendations by home.showRecommendations.collectAsStateWithLifecycle()
    val startupAverage by home.startupAverage.collectAsStateWithLifecycle()
    val seasonMode by home.seasonMode.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val signedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    // Меню плитки и плашка «отменить» после того, как плитку убрали на время.
    var menuItem by remember { mutableStateOf<DialItem?>(null) }
    var undoItem by remember { mutableStateOf<DialItem?>(null) }
    LaunchedEffect(undoItem) {
        if (undoItem != null) {
            delay(UNDO_MS)
            undoItem = null
        }
    }

    val openDial: (DialItem) -> Unit = { item ->
        when (item.kind) {
            PinEntity.KIND_ARTIST ->
                navigator.open(BrowseRoute.Artist(item.id, item.title, item.thumbnailUrl))

            // Свой плейлист живёт в базе, а не на YouTube:
            // его открывает библиотека, а не страница альбома.
            PinEntity.KIND_PLAYLIST if item.localPlaylistId != null ->
                onOpenLocalPlaylist(item.localPlaylistId)

            PinEntity.KIND_ALBUM, PinEntity.KIND_PLAYLIST ->
                navigator.open(BrowseRoute.Album(item.id, item.title, item.thumbnailUrl))

            else -> viewModel.playDial(item)
        }
    }
    Box(Modifier.fillMaxSize()) {
    PixelPullRefresh(
        refreshing = loadingShelves || syncing,
        onRefresh = {
            // Обновляется всё, что приходит из сети: рекомендации и, если
            // вошли в аккаунт, плейлисты и лайки. Закреплённое не трогаем.
            home.refresh()
            if (signedIn) viewModel.sync()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = com.texfi.w0y.ui.components.bottomContentPadding()) {
        item {
            ScreenTitle(
                title = stringResource(if (hair) R.string.tab_home else R.string.app_name),
                // Замер старта стоит рядом с названием: обещание «быстро»
                // проверяется на этом же экране, а не на слово. Номер сборки
                // отсюда ушёл — он для отладки и живёт в «О приложении».
                // В Hairline шапка чистая: замер живёт в «О приложении».
                subtitle = if (hair) null else startupAverage?.let { stringResource(R.string.home_meta, it.toInt()) },
                actions = {
                    if (hair) com.texfi.w0y.ui.components.SeasonMark(seasonMode, Modifier.padding(end = 12.dp))
                    SpriteButton(Sprites.gear, onClick = onOpenSettings, size = 22)
                },
            )
        }

        item {
            Column(Modifier.padding(horizontal = Gutter)) {
                SectionHeader(
                    index = 1,
                    label = stringResource(R.string.home_dial),
                    hint = if (dial.isEmpty()) null else stringResource(R.string.home_dial_hint),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (dial.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                        repeat(if (hair) HAIR_COLUMNS else DIAL_COLUMNS) { GhostTile(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.home_dial_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        } else {
            item {
                if (hair) HairDialGrid(dial, onOpen = { item -> openDial(item) }, onLongPress = { menuItem = it }, menuItem = menuItem) else SpeedDialPager(
                    items = dial,
                    onOpen = { item -> openDial(item) },
                    onLongPress = { menuItem = it },
                )
            }
        }

        // Подборки по твоему вкусу: каждый блок появляется, только когда в нём есть что показать.
        // Быстрый выбор — сразу под быстрым набором: своя подборка из радио недавних треков.
        // Пока истории нет (первый запуск), берём первую ленту треков YouTube Music.
        run {
            val own = recBlocks.quickPicks.map { it.song }
            val quick = own.ifEmpty { shelves.firstOrNull { it.songs.isNotEmpty() }?.songs.orEmpty() }
            if (quick.isNotEmpty()) {
                item(key = "quick-picks") {
                    com.texfi.w0y.ui.components.QuickPicksShelf(
                        title = stringResource(R.string.home_quick_picks).asLabel(),
                        songs = quick,
                        onPlay = { index -> home.play(quick, index) },
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
            }
        }
        recBlockRow(R.string.home_block_discoveries, recBlocks.discoveries, home)
        recBlockRow(R.string.home_block_forgotten, recBlocks.forgotten, home)
        recBlockRow(R.string.home_block_time, recBlocks.timeOfDay, home)
        if (recBlocks.releases.isNotEmpty()) {
            item(key = "block-releases") {
                Column(Modifier.padding(top = 16.dp)) {
                    ShelfTitle(stringResource(R.string.home_block_releases).asLabel(), Modifier.padding(horizontal = Gutter))
                    Spacer(Modifier.height(12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recBlocks.releases.distinctBy { it.browseId }, key = { "br-${it.browseId}" }) { card ->
                            ReleaseTile(card) { navigator.open(BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl)) }
                        }
                    }
                }
            }
        }

        if (showRecommendations) {
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Spacer(Modifier.height(22.dp))
                    SectionHeader(
                        index = 2,
                        label = stringResource(R.string.home_recommendations),
                        action = { SpriteButton(Sprites.repeat, onClick = home::refresh, size = 18) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (recommendationsFailed) {
                item {
                    Text(
                        text = stringResource(R.string.home_recommendations_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 10.dp),
                    )
                }
            } else if (shelves.isEmpty() && loadingShelves) {
                items(3) { SkeletonRow(Modifier.padding(horizontal = Gutter)) }
            }

            itemsIndexed(shelves, key = { _, it -> "shelf-${it.title}" }) { shelfIndex, shelf ->
                // Новые ленты после обновления приходят ступенями, по одной,
                // а не мгновенной подменой всего списка.
                Stagger(index = shelfIndex * 3) {
                Column(Modifier.padding(top = 16.dp)) {
                    // Ленты треков — строками по четыре в колонке, как список, а не плитками:
                    // название, исполнитель и прослушивания читаются целиком.
                    if (shelf.songs.isNotEmpty()) {
                        com.texfi.w0y.ui.components.QuickPicksShelf(
                            title = shelf.title,
                            songs = shelf.songs,
                            onPlay = { index -> home.play(shelf.songs, index) },
                        )
                    } else {
                        ShelfTitle(shelf.title, Modifier.padding(horizontal = Gutter))
                    }
                    if (shelf.cards.isNotEmpty() || shelf.artists.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Gutter),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shelf.cards, key = { "c-${it.browseId}" }) { card ->
                            ReleaseTile(card) {
                                navigator.open(BrowseRoute.Album(card.browseId, card.title, card.thumbnailUrl))
                            }
                        }
                        items(shelf.artists, key = { "a-${it.browseId}" }) { card ->
                            ArtistTile(card) {
                                navigator.open(BrowseRoute.Artist(card.browseId, card.name, card.thumbnailUrl))
                            }
                        }
                    }
                    }
                }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Spacer(Modifier.height(26.dp))
                    SectionHeader(stringResource(R.string.home_recent), index = 3)
                    Spacer(Modifier.height(6.dp))
                }
            }
            items(recent.take(20), key = { "recent-${it.id}" }) { song ->
                SongRow(
                    song = song,
                    modifier = Modifier.padding(horizontal = Gutter),
                    onClick = { viewModel.play(recent, recent.indexOf(song)) },
                    actions = { DownloadButton(song.id, onDownload = { viewModel.download(song) }) },
                )
            }
        }

        if (liked.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Spacer(Modifier.height(26.dp))
                    SectionHeader(stringResource(R.string.library_likes), index = 4)
                    Spacer(Modifier.height(6.dp))
                }
            }
            items(liked.take(10), key = { "liked-${it.id}" }) { song ->
                SongRow(
                    song = song,
                    modifier = Modifier.padding(horizontal = Gutter),
                    onClick = { viewModel.play(liked, liked.indexOf(song)) },
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
    }

    menuItem?.let { item ->
        DialMenu(
            item = item,
            onPin = {
                viewModel.togglePin(item)
                menuItem = null
            },
            onHide = {
                viewModel.hideDial(item)
                undoItem = item
                menuItem = null
            },
            onDismiss = { menuItem = null },
        )
    }

    undoItem?.let { item ->
        UndoBar(
            onUndo = {
                viewModel.undoHideDial(item)
                undoItem = null
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }
}

/**
 * Меню плитки быстрого набора: закрепить или убрать на время.
 *
 * Прежнее долгое нажатие «закрепить/открепить» осталось первой кнопкой,
 * новое действие стоит рядом и не мешает ему.
 */
@Composable
private fun DialMenu(
    item: DialItem,
    onPin: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.shadow.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PixelCard(label = stringResource(R.string.track_panel_title), modifier = Modifier.padding(24.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(
                    text = stringResource(if (item.pinned) R.string.dial_unpin else R.string.dial_pin),
                    onClick = onPin,
                )
                PixelButton(text = stringResource(R.string.dial_hide), onClick = onHide, fill = colors.surfaceHigh)
            }
        }
    }
}

/** Плашка после «убрать на время»: можно сразу вернуть. */
@Composable
private fun UndoBar(onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Row(
        modifier
            .padding(16.dp)
            .background(colors.surfaceHigh)
            .styledBorder(0)
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.dial_hidden_toast),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
        )
        Spacer(Modifier.width(12.dp))
        PixelButton(text = stringResource(R.string.dial_undo), onClick = onUndo, fill = colors.secondary)
    }
}

private const val UNDO_MS = 5_000L

/**
 * Пустое место быстрого набора.
 *
 * Пустой экран с одинокой плиткой выглядит как недогруженный; призрачные
 * клетки сразу показывают, сколько их будет и что с ними делать.
 */
@Composable
private fun GhostTile(modifier: Modifier = Modifier, square: Boolean = true) {
    val colors = LocalW0yColors.current
    if (com.texfi.w0y.ui.theme.isHair) {
        HairGhostTile(modifier, square)
        return
    }
    Box(
        modifier
            .aspectRatio(1f)
            .styledSurface(0),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = PixelSectionLabel,
            color = colors.border,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Быстрый набор страницами.
 *
 * Девять плиток на страницу, страницы листаются вбок — так на главной
 * помещается несколько десятков закреплений, не превращая её в
 * бесконечную ленту плиток.
 */
@Composable
private fun SpeedDialPager(
    items: List<DialItem>,
    onOpen: (DialItem) -> Unit,
    onLongPress: (DialItem) -> Unit,
) {
    val colors = LocalW0yColors.current
    // Страница всегда из девяти мест: на неполной странице плитки иначе
    // липнут к верху, и вторая страница выглядит обрубком.
    val pages = items.chunked(DIAL_PAGE).map { page -> page + List(DIAL_PAGE - page.size) { null } }
    val state = rememberPagerState(pageCount = { pages.size })
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Высота считается от ширины: плитка квадратная, а у страницы
        // внутри LazyColumn своей высоты нет.
        val tile = (maxWidth - Gutter * 2 - TILE_GAP * (DIAL_COLUMNS - 1)) / DIAL_COLUMNS
        val pageHeight = tile * DIAL_ROWS + TILE_GAP * (DIAL_ROWS - 1)
        Column {
            HorizontalPager(
                state = state,
                pageSpacing = Gutter * 2,
                contentPadding = PaddingValues(horizontal = Gutter),
                modifier = Modifier.height(pageHeight),
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    pages[page].chunked(DIAL_COLUMNS).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                            row.forEach { item ->
                                if (item == null) {
                                    GhostTile(Modifier.weight(1f))
                                    return@forEach
                                }
                                SpeedDialTile(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    thumbnailUrl = item.thumbnailUrl,
                                    pinned = item.pinned,
                                    kindSprite =
                                        when (item.kind) {
                                            PinEntity.KIND_ARTIST -> Sprites.artist
                                            PinEntity.KIND_ALBUM, PinEntity.KIND_PLAYLIST -> Sprites.release
                                            else -> null
                                        },
                                    round = item.kind == PinEntity.KIND_ARTIST,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpen(item) },
                                    onLongClick = { onLongPress(item) },
                                )
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pages.size) { index ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (index == state.currentPage) 8.dp else 6.dp)
                                .background(if (index == state.currentPage) colors.accent else colors.border),
                        )
                    }
                }
            }
        }
    }
}


/**
 * Пустой слот быстрого набора в Hairline: пунктирный контур с плюсом,
 * который медленно «дышит» прозрачностью — зовёт закрепить что-нибудь.
 */
@Composable
private fun HairGhostTile(modifier: Modifier = Modifier, square: Boolean = true) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val phase = com.texfi.w0y.ui.components.rememberAnimationPhase(3_600, 12, enabled = look.motion.moves)
    val shape = com.texfi.w0y.ui.theme.styleTokens.card
    androidx.compose.foundation.Canvas(if (square) modifier.aspectRatio(1f) else modifier) {
        val w = look.line.toPx()
        val breath = if (look.motion.moves) 0.55f + 0.45f * (0.5f + 0.5f * kotlin.math.sin(phase.floatValue)) else 1f
        val outline = shape.createOutline(androidx.compose.ui.geometry.Size(size.width - w, size.height - w), layoutDirection, this)
        val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
        translate(w / 2f, w / 2f) {
            drawPath(
                path,
                colors.outline.copy(alpha = breath),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    w,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                ),
            )
        }
        val c = center
        val r = 8.dp.toPx()
        drawLine(colors.textMuted.copy(alpha = breath), androidx.compose.ui.geometry.Offset(c.x - r, c.y), androidx.compose.ui.geometry.Offset(c.x + r, c.y), w, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(colors.textMuted.copy(alpha = breath), androidx.compose.ui.geometry.Offset(c.x, c.y - r), androidx.compose.ui.geometry.Offset(c.x, c.y + r), w, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/**
 * Быстрый набор Hairline: сетка контур-плиток 2 в ряд, по три ряда на
 * страницу; листается вбок. Плитка, на которую открыто меню, поднята —
 * выше и с акцентным контуром.
 */
@Composable
private fun HairDialGrid(
    items: List<DialItem>,
    onOpen: (DialItem) -> Unit,
    onLongPress: (DialItem) -> Unit,
    menuItem: DialItem?,
) {
    val colors = LocalW0yColors.current
    val perPage = HAIR_COLUMNS * HAIR_ROWS
    val pages = items.chunked(perPage).map { page -> page + List(perPage - page.size) { null } }
    val state = rememberPagerState(pageCount = { pages.size })
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tile = (maxWidth - Gutter * 2 - TILE_GAP * (HAIR_COLUMNS - 1)) / HAIR_COLUMNS
        // Квадратная обложка + подпись в две строки, без рамки плитки.
        val tileHeight = tile + 6.dp + 38.dp
        val pageHeight = tileHeight * HAIR_ROWS + TILE_GAP * (HAIR_ROWS - 1)
        Column {
            HorizontalPager(
                state = state,
                pageSpacing = Gutter * 2,
                contentPadding = PaddingValues(horizontal = Gutter),
                modifier = Modifier.height(pageHeight),
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    pages[page].chunked(HAIR_COLUMNS).forEach { row ->
                        Row(Modifier.height(tileHeight), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                            row.forEach { item ->
                                if (item == null) {
                                    GhostTile(Modifier.weight(1f).fillMaxHeight(), square = false)
                                    return@forEach
                                }
                                val lifted = menuItem == item
                                SpeedDialTile(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    thumbnailUrl = item.thumbnailUrl,
                                    pinned = item.pinned,
                                    kindSprite =
                                        when (item.kind) {
                                            PinEntity.KIND_ARTIST -> Sprites.artist
                                            PinEntity.KIND_ALBUM, PinEntity.KIND_PLAYLIST -> Sprites.release
                                            else -> null
                                        },
                                    round = item.kind == PinEntity.KIND_ARTIST,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .graphicsLayer {
                                                val k = if (lifted) 1.05f else 1f
                                                scaleX = k
                                                scaleY = k
                                            },
                                    onClick = { onOpen(item) },
                                    onLongClick = { onLongPress(item) },
                                )
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    repeat(pages.size) { index ->
                        val on = index == state.currentPage
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (on) 7.dp else 5.dp)
                                .border(com.texfi.w0y.ui.theme.hairLook.line, if (on) colors.accentText else colors.outline, CircleShape)
                                .background(if (on) colors.accentText else Color.Transparent, CircleShape),
                        )
                    }
                }
            }
        }
    }
}


/** Горизонтальный ряд подборки: плитка трека и под ней причина, почему он здесь. */
private fun androidx.compose.foundation.lazy.LazyListScope.recBlockRow(
    @androidx.annotation.StringRes title: Int,
    rawItems: List<com.texfi.w0y.recs.Rec>,
    home: HomeViewModel,
) {
    // Ключи строк обязаны быть уникальными: повтор одного трека роняет весь список.
    val items = rawItems.distinctBy { it.song.id }
    if (items.isEmpty()) return
    item(key = "block-$title") {
        Column(Modifier.padding(top = 16.dp)) {
            ShelfTitle(stringResource(title).asLabel(), Modifier.padding(horizontal = Gutter))
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { "b-${it.song.id}" }) { rec ->
                    Column(Modifier.width(132.dp)) {
                        SongTile(rec.song) { home.playRecs(items, items.indexOf(rec)) }
                        Text(
                            whyLabel(rec.why).asLabel(),
                            style = com.texfi.w0y.ui.theme.HairLabelStyle,
                            color = LocalW0yColors.current.textMuted,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
