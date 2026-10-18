package com.texfi.w0y.desktop

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import kotlin.concurrent.thread

/**
 * Windows: глобальные медиа-клавиши (пауза, дальше, назад, стоп) через RegisterHotKey — работают, пока окно
 * свёрнуто или в трее. Это не панель SMTC с обложкой: её даёт только WinRT, которого у JVM нет без нативной
 * обвязки. На Linux то же делает MPRIS, поэтому здесь только Windows.
 */
object WinMediaKeys {
    private const val WM_HOTKEY = 0x0312
    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3

    @Volatile
    private var started = false

    fun start(app: AppState) {
        if (started || !Platform.isWindows) return
        started = true
        // Горячие клавиши принадлежат потоку, который их зарегистрировал, и приходят в его очередь сообщений.
        thread(isDaemon = true, name = "win-media-keys") {
            val user = runCatching { User32.INSTANCE }.getOrNull() ?: return@thread
            val keys = mapOf(1 to VK_MEDIA_PLAY_PAUSE, 2 to VK_MEDIA_NEXT_TRACK, 3 to VK_MEDIA_PREV_TRACK, 4 to VK_MEDIA_STOP)
            keys.forEach { (id, vk) -> user.RegisterHotKey(null, id, 0, vk) }
            val msg = WinUser.MSG()
            while (user.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WM_HOTKEY) {
                    when (msg.wParam.toInt()) {
                        1 -> Actions.run(app, Action.PLAY_PAUSE)
                        2 -> Actions.run(app, Action.NEXT)
                        3 -> Actions.run(app, Action.PREVIOUS)
                        4 -> if (app.player.playing) Actions.run(app, Action.PLAY_PAUSE)
                    }
                }
            }
        }
    }
}
