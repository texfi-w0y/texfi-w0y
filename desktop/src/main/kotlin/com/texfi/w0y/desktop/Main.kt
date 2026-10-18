package com.texfi.w0y.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import com.texfi.w0y.desktop.i18n.S
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main(args: Array<String>) {
    Diag.installCrashHandler()
    // `w0y --smoke` — проверка сборки без окна: CI запускает её на Windows и Linux.
    if ("--smoke" in args) {
        System.setProperty("java.awt.headless", "true")
        if (System.getenv("W0Y_AO").isNullOrBlank()) System.setProperty("w0y.ao", "null")
        kotlin.system.exitProcess(Smoke.run())
    }
    // `w0y --do play_pause` — команда уже запущенному приложению (для горячих клавиш окружения).
    if (Control.sendIfRequested(args)) return
    application {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val app = remember { AppState(scope, Yt(), Store()).also { it.start() } }
        LaunchedEffect(Unit) { if (Platform.isWindows) WinMediaKeys.start(app) }
        val state = rememberWindowState(size = DpSize(1180.dp, 760.dp))
        var visible by remember { mutableStateOf(true) }
        val trayOn = isTraySupported
        LaunchedEffect(Unit) {
            Actions.quit = {
                app.shutdown()
                exitApplication()
            }
            Control.start(app, scope) {
                visible = true
                state.isMinimized = false
            }
        }
        LaunchedEffect(Actions.fullscreen) {
            state.placement = if (Actions.fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }
        // Трей: окно можно убрать, не останавливая музыку; управление — из меню значка.
        if (trayOn) {
            Tray(
                icon = painterResource("icon.png"),
                tooltip = "w0y",
                onAction = { visible = true; state.isMinimized = false },
                menu = {
                    Item(S("d_tray_show"), onClick = { visible = true; state.isMinimized = false })
                    Item(S("d_act_play_pause"), onClick = { app.player.toggle() })
                    Item(S("d_act_next"), onClick = { app.player.nextClick() })
                    Item(S("d_act_previous"), onClick = { app.player.previous() })
                    Item(S("d_act_mini"), onClick = { Actions.mini = true })
                    Separator()
                    Item(S("d_tray_quit"), onClick = { Actions.quit() })
                },
            )
        }
        // Мини-плеер: главное окно прячется, маленькое окно висит поверх остальных.
        val mini = Actions.mini
        LaunchedEffect(mini) { if (mini) visible = false else visible = true }
        if (mini) {
            Window(
                onCloseRequest = { Actions.mini = false },
                title = "w0y",
                icon = painterResource("icon.png"),
                undecorated = true,
                resizable = false,
                alwaysOnTop = true,
                state = rememberWindowState(size = DpSize(400.dp, 128.dp)),
            ) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density * app.st.uiScale.coerceIn(0.7f, 2f), LocalDensity.current.fontScale)) {
                    MiniPlayer(app) { Actions.mini = false }
                }
            }
        }
        Window(
            onCloseRequest = {
                if (trayOn && app.st.closeToTray) {
                    visible = false
                } else {
                    app.shutdown()
                    exitApplication()
                }
            },
            visible = visible,
            title = "w0y",
            icon = painterResource("icon.png"),
            state = state,
        ) {
            // Масштаб интерфейса: всё окно крупнее или мельче, шрифты вместе с размерами.
            val density = LocalDensity.current
            val scale = app.st.uiScale.coerceIn(0.7f, 2f)
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                App(app)
            }
        }
    }
}
