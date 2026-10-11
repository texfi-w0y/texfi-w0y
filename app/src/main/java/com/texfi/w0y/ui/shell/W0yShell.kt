package com.texfi.w0y.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.texfi.w0y.ui.theme.screenBackdrop
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.data.StartTab
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.ui.components.LocalCompactRows
import com.texfi.w0y.ui.components.Buzz
import com.texfi.w0y.ui.components.LocalHaptics
import com.texfi.w0y.ui.components.rememberHaptics
import com.texfi.w0y.ui.components.LocalPlayingSongId
import com.texfi.w0y.ui.components.AddToPlaylistPanel
import com.texfi.w0y.ui.components.LocalSongActions
import com.texfi.w0y.ui.components.MiniPlayer
import com.texfi.w0y.ui.components.FloatingNavBar
import com.texfi.w0y.ui.components.NavItem
import androidx.compose.foundation.layout.widthIn
import com.texfi.w0y.ui.components.SongActions
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.components.softEnter
import com.texfi.w0y.ui.components.W0yMotion
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.components.popWhenActivated
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.DownloadToast
import com.texfi.w0y.ui.components.LocalDownloadProgress
import com.texfi.w0y.ui.components.Starfield
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.nav.BrowseNavigator
import com.texfi.w0y.ui.nav.BrowseRoute
import com.texfi.w0y.ui.nav.LocalBrowseNavigator
import com.texfi.w0y.ui.screens.AlbumScreen
import com.texfi.w0y.ui.screens.ArtistScreen
import com.texfi.w0y.ui.screens.HomeScreen
import com.texfi.w0y.ui.screens.LibraryScreen
import com.texfi.w0y.ui.screens.LibraryRoute
import com.texfi.w0y.ui.screens.LibraryViewModel
import com.texfi.w0y.ui.screens.LoginScreen
import com.texfi.w0y.ui.screens.PlayerScreen
import com.texfi.w0y.ui.screens.SearchScreen
import com.texfi.w0y.ui.screens.SettingsScreen
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.screenBackground

private enum class Tab(val labelRes: Int, val sprite: List<String>) {
    HOME(R.string.tab_home, Sprites.home),
    SEARCH(R.string.tab_search, Sprites.search),
    LIBRARY(R.string.tab_library, Sprites.library),
}

@Composable
fun W0yShell(viewModel: ShellViewModel = hiltViewModel()) {
    val colors = LocalW0yColors.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    val startTab by viewModel.startTab.collectAsStateWithLifecycle()
    val launchTab by viewModel.launchTab.collectAsStateWithLifecycle()
    val compactRows by viewModel.compactRows.collectAsStateWithLifecycle()
    var startTabApplied by remember { mutableStateOf(false) }
    // Стартовый экран применяется один раз за запуск: иначе возврат на
    // «дом» перекидывал бы обратно при каждом чтении настроек.
    LaunchedEffect(launchTab) {
        if (!startTabApplied && launchTab != null) {
            tab = when (launchTab) {
                StartTab.SEARCH -> Tab.SEARCH
                StartTab.LIBRARY -> Tab.LIBRARY
                else -> Tab.HOME
            }
            startTabApplied = true
        }
    }
    // Последняя вкладка запоминается после применения стартовой, иначе
    // начальное «дом» затёрло бы сохранённое значение.
    LaunchedEffect(tab, startTabApplied) {
        if (startTabApplied) {
            viewModel.setLastTab(
                when (tab) {
                    Tab.HOME -> StartTab.HOME
                    Tab.SEARCH -> StartTab.SEARCH
                    Tab.LIBRARY -> StartTab.LIBRARY
                },
            )
        }
    }
    val playerState by viewModel.player.state.collectAsStateWithLifecycle()
    var playerExpanded by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var standOpen by remember { mutableStateOf(false) }
    var loginOpen by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var loginBusy by remember { mutableStateOf(false) }
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val welcomeSeen by viewModel.welcomeSeen.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val stylePicked by viewModel.stylePicked.collectAsStateWithLifecycle()
    val uiStyle by viewModel.uiStyle.collectAsStateWithLifecycle()
    val smoothGlass by viewModel.smoothGlass.collectAsStateWithLifecycle()
    val accent by viewModel.accent.collectAsStateWithLifecycle()
    val customAccent by viewModel.customAccent.collectAsStateWithLifecycle()
    val animatedBackground by viewModel.animatedBackground.collectAsStateWithLifecycle()
    val haptics by viewModel.haptics.collectAsStateWithLifecycle()
    val hapticLevel by viewModel.hapticLevel.collectAsStateWithLifecycle()
    // Заставка играет один раз за запуск, а не при каждом повороте экрана.
    var introDone by rememberSaveable { mutableStateOf(false) }
    val libraryRoute by libraryViewModel.route.collectAsStateWithLifecycle()
    val browseStack = remember { mutableStateListOf<BrowseRoute>() }
    val navigator = remember { BrowseNavigator { route -> browseStack.add(route) } }
    // Верхний экран стека нужен и во время анимации закрытия, когда стек
    // уже пуст, — иначе на последнем кадре рисовать нечего.
    var lastBrowse by remember { mutableStateOf<BrowseRoute?>(null) }
    browseStack.lastOrNull()?.let { lastBrowse = it }

    // Одна точка на весь «назад».
    //
    // Раньше обработчик стоял только на полноэкранных слоях, и на любом
    // другом экране системный жест уходил мимо приложения — прямо на рабочий
    // стол, вместе со всей навигацией. Порядок здесь — это порядок закрытия:
    // от самого верхнего слоя к вкладкам.
    BackHandler(
        enabled =
            playerExpanded || settingsOpen || loginOpen ||
                browseStack.isNotEmpty() || libraryRoute != LibraryRoute.Root || tab != Tab.HOME,
    ) {
        when {
            loginOpen -> loginOpen = false
            settingsOpen -> settingsOpen = false
            playerExpanded -> playerExpanded = false
            browseStack.isNotEmpty() -> browseStack.removeAt(browseStack.lastIndex)
            libraryRoute != LibraryRoute.Root -> libraryViewModel.back()
            else -> tab = Tab.HOME
        }
    }

    val downloadProgress by viewModel.downloads.progress.collectAsStateWithLifecycle()
    val menuViewModel: SongMenuViewModel = hiltViewModel()
    val menuPlaylists by menuViewModel.playlists.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<SongItem?>(null) }
    var menuNewName by remember { mutableStateOf<String?>(null) }
    val songActions =
        remember(menuViewModel) {
            SongActions(
                playNext = menuViewModel.player::playNext,
                enqueue = menuViewModel.player::enqueue,
                openMenu = { menuSong = it },
            )
        }

    CompositionLocalProvider(
        LocalDownloadProgress provides downloadProgress,
        LocalBrowseNavigator provides navigator,
        LocalCompactRows provides (compactRows || com.texfi.w0y.ui.theme.hairLook.compact),
        LocalHaptics provides haptics,
        com.texfi.w0y.ui.components.LocalHapticLevel provides hapticLevel,
        LocalPlayingSongId provides playerState.song?.id,
        com.texfi.w0y.ui.components.LocalIsPlaying provides playerState.isPlaying,
        LocalSongActions provides songActions,
    ) {
    val haptic = rememberHaptics()
    val hair = com.texfi.w0y.ui.theme.isHair
    val swipeDismiss by viewModel.swipeDismiss.collectAsStateWithLifecycle()
    var undoVisible by remember { mutableStateOf(false) }
    val dismissPlayer: () -> Unit = {
        playerExpanded = false
        if (viewModel.player.dismissPlayer()) {
            haptic(Buzz.MINIMIZE)
            undoVisible = true
        }
    }
    // Окно «Отменить» закрывается само: очередь после него больше не хранится.
    LaunchedEffect(undoVisible) {
        if (undoVisible) {
            kotlinx.coroutines.delay(com.texfi.w0y.playback.MiniDismiss.UNDO_MS)
            viewModel.player.commitDismiss()
            undoVisible = false
        }
    }
    // Любой запущенный трек возвращает плеер и убирает «Отменить».
    LaunchedEffect(playerState.song?.id) { if (playerState.song != null) undoVisible = false }
    // Стеклянный Smooth: экран уходит под нижние панели, а они его размывают
    // и преломляют. В остальных стилях панели стоят под экраном, как раньше:
    // список кончается над ними, и под непрозрачной панелью ничего не прячется.
    val glassBars = com.texfi.w0y.ui.theme.isSmooth && com.texfi.w0y.ui.theme.styleTokens.glass
    val barBackdrop = com.texfi.w0y.ui.theme.rememberScreenBackdrop()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barsHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val barsHeight = with(density) { barsHeightPx.toDp() }
    Box(
        Modifier
            .fillMaxSize()
            .screenBackground(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .screenBackdrop(barBackdrop)
                // Фон — внутри записи: без него текст на прозрачном размывался
                // в бледное пятно, и сквозь стекло читался чёткий оригинал.
                .screenBackground(),
        ) {
        // Фон экосистемы: он виден в промежутках между карточками и
        // строками — как на сайте, где чёрный тоже не пустой.
        if (hair) {
            // Hairline: чистый фон без звёзд и без размытия — быстрее и тише.
        } else if (com.texfi.w0y.ui.theme.isSmooth) {
            com.texfi.w0y.ui.components.SmoothBackdrop(playerState.song?.thumbnailUrl, Modifier.fillMaxSize())
        } else {
            Starfield(Modifier.fillMaxSize(), animated = animatedBackground)
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = if (glassBars) 0.dp else barsHeight),
        ) {
            Box(Modifier.weight(1f)) {
            CompositionLocalProvider(com.texfi.w0y.ui.components.LocalBottomInset provides if (glassBars) barsHeight else 0.dp) {
                // Смена вкладки — спокойное ступенчатое проявление: экран стоит
                // на месте с первого кадра, без ожидания и без цветных блоков.
                Box(Modifier.fillMaxSize().softEnter(tab)) {
                when (tab) {
                    Tab.HOME ->
                        HomeScreen(
                            onOpenSettings = { settingsOpen = true },
                            onOpenPlayer = { playerExpanded = true },
                            onOpenLocalPlaylist = { id ->
                                libraryViewModel.open(LibraryRoute.Local(id))
                                tab = Tab.LIBRARY
                            },
                        )
                    Tab.SEARCH -> SearchScreen()
                    Tab.LIBRARY -> LibraryScreen(onOpenLogin = { loginOpen = true })
                }
                }

                // Артист и альбом ложатся поверх вкладки, но не поверх
                // мини-плеера: на этих страницах чаще всего и переключают
                // трек, прятать управление на них было бы издевательством.
                BrowseOverlay(
                    visible = browseStack.isNotEmpty(),
                    route = lastBrowse,
                    onBack = { if (browseStack.isNotEmpty()) browseStack.removeAt(browseStack.lastIndex) },
                )
                // Настройки открываются внутри экрана, под нижними панелями: те
                // остаются на месте (со стеклом — размывают их), вкладка
                // закрывает настройки.
                SettingsLayer(settingsOpen, onClose = { settingsOpen = false }, onOpenLogin = { loginOpen = true })
            }
            }
        }
        }
        CompositionLocalProvider(com.texfi.w0y.ui.theme.LocalBarBackdrop provides if (glassBars) barBackdrop else null) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barsHeightPx = it.height },
        ) {
            DownloadToast(
                notices = viewModel.downloads.notices,
                onOpen = {
                    browseStack.clear()
                    playerExpanded = false
                    settingsOpen = false
                    libraryViewModel.open(LibraryRoute.Downloads)
                    tab = Tab.LIBRARY
                },
            )
            val mini: @Composable () -> Unit = {
                MiniPlayer(
                    state = playerState,
                    positionProvider = viewModel.player::positionMs,
                    onToggle = viewModel.player::togglePlayPause,
                    onNext = { viewModel.player.skipNext() },
                    onPrevious = { viewModel.player.skipPrevious() },
                    onExpand = { playerExpanded = true },
                    onDismiss = if (hair && swipeDismiss) dismissPlayer else null,
                )
            }
            val navItems = Tab.entries.map { NavItem(stringResource(it.labelRes), it.sprite) }
            if (hair) {
                // Hairline: полосы во всю ширину, без плавающих блоков.
                mini()
                Box(Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding()) {
                    FloatingNavBar(
                        items = navItems,
                        selected = tab.ordinal,
                        onSelect = {
                            tab = Tab.entries[it]
                            settingsOpen = false
                        },
                    )
                }
            } else {
                // Мини-плеер и навигация — плавающие блоки в одной колонке с
                // контентом: список заканчивается над ними и ничем не закрыт.
                // На широком экране блоки стоят по центру, не растягиваясь.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = BAR_MAX_WIDTH)) {
                        mini()
                        FloatingNavBar(
                            items = navItems,
                            selected = tab.ordinal,
                            onSelect = {
                                tab = Tab.entries[it]
                                settingsOpen = false
                            },
                            standalone = Tab.SEARCH.ordinal,
                        )
                    }
                }
            }
        }
        }

        // «Остановлено · Отменить» — четыре секунды после того, как плеер убрали жестом.
        AnimatedVisibility(
            visible = undoVisible,
            enter = fadeIn(tween(W0yMotion.FAST_MS)),
            exit = fadeOut(tween(W0yMotion.FAST_MS)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barsHeight + 12.dp),
        ) {
            com.texfi.w0y.ui.components.HairSnack(
                message = stringResource(R.string.hl_mini_stopped),
                action = stringResource(R.string.hl_undo),
                onAction = {
                    undoVisible = false
                    viewModel.player.undoDismiss()
                },
            )
        }

        PlayerSheet(
            expanded = playerExpanded && playerState.song != null,
            onCollapse = { playerExpanded = false },
        ) {
            PlayerScreen(onCollapse = { playerExpanded = false }, onStand = { standOpen = true })
        }

        menuSong?.let { song ->
            val close = {
                menuSong = null
                menuNewName = null
            }
            CompositionLocalProvider(com.texfi.w0y.ui.theme.LocalPanelBackdrop provides if (glassBars) barBackdrop else null) {
            AddToPlaylistPanel(
                song = song,
                playlists = menuPlaylists.map { it.id to it.name },
                newName = menuNewName,
                onNewNameChange = { menuNewName = it },
                onPick = {
                    menuViewModel.addToPlaylist(it, song)
                    close()
                },
                onCreate = {
                    menuViewModel.createPlaylistWith(it, song)
                    close()
                },
                onDismiss = close,
                onPlayNext = {
                    menuViewModel.player.playNext(song)
                    close()
                },
                onEnqueue = {
                    menuViewModel.player.enqueue(song)
                    close()
                },
                onDownload = {
                    menuViewModel.download(song)
                    close()
                },
                onArtist = { artist ->
                    navigator.open(BrowseRoute.Artist(artist.id, artist.name, song.thumbnailUrl))
                    close()
                },
                onAlbum =
                    song.albumId?.let { id ->
                        {
                            navigator.open(BrowseRoute.Album(id, song.album ?: song.title, song.thumbnailUrl))
                            close()
                        }
                    },
            )
            }
        }


        // Приветствие поверх всего: на первом запуске за ним ещё нечего
        // смотреть, а сразу после него — уже настроенное приложение.
        if (welcomeSeen == false) {
            WelcomeScreen(
                theme = theme,
                startTab = startTab ?: StartTab.HOME,
                onTheme = viewModel::setTheme,
                onStartTab = viewModel::setStartTab,
                onSignIn = { loginOpen = true },
                onDone = {
                    viewModel.completeWelcome()
                },
            )
        }

        // Выбор стиля — после приветствия, один раз; обновившимся тоже один раз.
        if (welcomeSeen == true && stylePicked == false) {
            StylePickerScreen(
                current = uiStyle,
                accent = accent,
                customAccent = customAccent,
                theme = theme,
                glass = smoothGlass,
                onGlass = viewModel::setSmoothGlass,
                onAccent = viewModel::setAccent,
                onDone = viewModel::completeStylePick,
                onSkip = {
                    // Пропуск оставляет стиль по умолчанию, даже если по пути успели переключить.
                    viewModel.setUiStyle(UiStyle.HAIRLINE)
                    viewModel.completeStylePick()
                },
            )
        }

        // Подставка — поверх всего, включая плеер и навигацию.
        if (standOpen && playerState.song != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            StandScreen(
                onExit = { reason ->
                    standOpen = false
                    reason.message?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
                },
            )
        }

        if (!introDone) {
            FeatherIntro(onFinished = { introDone = true })
        }

        AnimatedVisibility(
            visible = loginOpen,
            enter = slideInVertically(tween(W0yMotion.MID_MS, easing = W0yMotion.StepBack)) { it / 2 },
            exit = slideOutVertically(tween(W0yMotion.FAST_MS, easing = W0yMotion.Step)) { it / 2 },
        ) {
            LoginScreen(
                busy = loginBusy,
                error = loginError,
                onCookie = { cookie ->
                    if (!loginBusy) {
                        loginBusy = true
                        loginError = null
                        libraryViewModel.onSignedIn(cookie) { message ->
                            loginBusy = false
                            loginError = message
                            haptic(if (message == null) Buzz.DONE else Buzz.ERROR)
                            if (message == null) loginOpen = false
                        }
                    }
                },
                onClose = {
                    loginOpen = false
                    loginError = null
                },
            )
        }
    }
}
}

/**
 * Слой страниц артиста и альбома.
 *
 * Вынесен в отдельную функцию не для красоты: внутри `Column` компилятор
 * выбирает `ColumnScope.AnimatedVisibility`, а здесь нужен обычный.
 */
@Composable
private fun BrowseOverlay(visible: Boolean, route: BrowseRoute?, onBack: () -> Unit) {
    val colors = LocalW0yColors.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(W0yMotion.FAST_MS, easing = W0yMotion.StepWide)) { it / 3 },
        exit = slideOutHorizontally(tween(W0yMotion.FAST_MS, easing = W0yMotion.StepWide)) { it / 3 },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .screenBackground(),
        ) {
            when (route) {
                is BrowseRoute.Artist -> ArtistScreen(route = route, onBack = onBack)
                is BrowseRoute.Album -> AlbumScreen(route = route, onBack = onBack)
                null -> Unit
            }
        }
    }
}

private val BAR_MAX_WIDTH = 560.dp

/** Настройки с выездом снизу — одна обёртка для обоих мест, где они живут. */
@Composable
private fun SettingsLayer(visible: Boolean, onClose: () -> Unit, onOpenLogin: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(W0yMotion.MID_MS, easing = W0yMotion.StepBack)) { it / 3 },
        exit = slideOutVertically(tween(W0yMotion.FAST_MS, easing = W0yMotion.Step)) { it / 3 },
    ) {
        SettingsScreen(onClose = onClose, onOpenLogin = onOpenLogin)
    }
}
