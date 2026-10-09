package com.texfi.w0y.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main(args: Array<String>) {
    // `w0y --do play_pause` — команда уже запущенному приложению (для горячих клавиш окружения).
    if (Control.sendIfRequested(args)) return
    application {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val app = remember { AppState(scope, Yt(), Store()).also { it.start() } }
        val state = rememberWindowState(size = DpSize(1180.dp, 760.dp))
        LaunchedEffect(Unit) {
            Actions.quit = {
                app.shutdown()
                exitApplication()
            }
            Control.start(app, scope) { state.isMinimized = false }
        }
        LaunchedEffect(Actions.fullscreen) {
            state.placement = if (Actions.fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }
        Window(
            onCloseRequest = {
                app.shutdown()
                exitApplication()
            },
            title = "w0y",
            icon = painterResource("icon.png"),
            state = state,
        ) {
            App(app)
        }
    }
}
