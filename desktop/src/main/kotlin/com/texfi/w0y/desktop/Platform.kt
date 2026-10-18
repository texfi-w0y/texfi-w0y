package com.texfi.w0y.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import java.awt.Desktop
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.io.File
import java.net.URI
import java.nio.file.Path

/**
 * Всё, что зависит от операционной системы, собрано здесь. Остальной код не проверяет ОС сам
 * и не знает ни про `xdg-open`, ни про `%APPDATA%`.
 */
object Platform {
    enum class Os { WINDOWS, LINUX, MAC }

    val os: Os =
        System.getProperty("os.name").lowercase().let {
            when {
                it.contains("win") -> Os.WINDOWS
                it.contains("mac") || it.contains("darwin") -> Os.MAC
                else -> Os.LINUX
            }
        }
    val isWindows get() = os == Os.WINDOWS
    val isLinux get() = os == Os.LINUX
    private val home: String get() = System.getProperty("user.home")

    /** `wayland`, `x11` или `windows`: попадает в `Copy debug info`. */
    val sessionType: String
        get() = when {
            isWindows -> "windows"
            System.getenv("XDG_SESSION_TYPE") != null -> System.getenv("XDG_SESSION_TYPE")
            System.getenv("WAYLAND_DISPLAY") != null -> "wayland"
            System.getenv("DISPLAY") != null -> "x11"
            else -> "unknown"
        }

    /** Данные (библиотека, история, загрузки): Windows `%APPDATA%\w0y`, Linux `~/.local/share/w0y`. */
    fun dataDir(): Path =
        if (isWindows) Path.of(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "w0y")
        else Path.of(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "w0y")

    /** Настройки и журналы: Windows `%APPDATA%\w0y`, Linux `~/.config/w0y`. */
    fun configDir(): Path =
        if (isWindows) Path.of(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "w0y")
        else Path.of(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "w0y")

    /** Короткоживущие файлы (сокет управления). */
    fun runtimeDir(): Path = Path.of(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"))

    /** Папка «Музыка» по умолчанию для загрузок. */
    fun musicDir(): File {
        if (isWindows) return File(home, "Music")
        val xdg =
            runCatching {
                File(home, ".config/user-dirs.dirs").readLines().firstOrNull { it.startsWith("XDG_MUSIC_DIR=") }
                    ?.substringAfter('=')?.trim('"')?.replace("\$HOME", home)
            }.getOrNull()
        return File(xdg ?: "$home/Music")
    }

    /** Исполняемый файл из PATH (на Windows с `.exe`), или null. */
    fun which(name: String): File? {
        val names = if (isWindows) listOf("$name.exe", "$name.cmd", name) else listOf(name)
        return (System.getenv("PATH") ?: "").split(File.pathSeparatorChar).filter { it.isNotBlank() }
            .flatMap { dir -> names.map { File(dir, it) } }.firstOrNull { it.isFile && it.canExecute() }
    }

    fun has(name: String) = which(name) != null

    /** Открыть папку или файл в системном файловом менеджере. */
    fun openPath(path: String) {
        runCatching {
            if (isWindows) ProcessBuilder("explorer.exe", path).start()
            else if (has("xdg-open")) ProcessBuilder("xdg-open", path).start()
            else if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(File(path))
        }
    }

    fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(URI(url))
            else if (has("xdg-open")) ProcessBuilder("xdg-open", url).start()
        }
    }

    private var trayForNotices: TrayIcon? = null

    /** Системное уведомление. Linux — notify-send, Windows — пузырь значка в трее. */
    fun notify(title: String, text: String, seconds: Int = 4) {
        runCatching {
            if (isWindows) {
                if (!SystemTray.isSupported()) return
                val tray = trayForNotices ?: TrayIcon(Toolkit.getDefaultToolkit().getImage(Platform::class.java.getResource("/icons/w0y-48.png")), "w0y")
                    .also { it.isImageAutoSize = true; SystemTray.getSystemTray().add(it); trayForNotices = it }
                tray.displayMessage(title, text, TrayIcon.MessageType.NONE)
            } else if (has("notify-send")) {
                ProcessBuilder("notify-send", "-a", "w0y", "-i", "w0y", "-t", "${seconds * 1000}", title, text)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            }
        }
    }

    private interface Kernel32 : Library {
        fun SetThreadExecutionState(flags: Int): Int
    }

    /**
     * Не давать экрану и системе засыпать (Stand mode). Возвращает то, что нужно закрыть, чтобы
     * снова разрешить сон; null — способа нет.
     */
    fun keepAwake(): AutoCloseable? {
        if (isWindows) {
            val gate = java.util.concurrent.CountDownLatch(1)
            // SetThreadExecutionState действует, пока жив вызвавший поток.
            val t = Thread {
                val k = runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull() ?: return@Thread
                k.SetThreadExecutionState(0x80000000.toInt() or 0x00000001 or 0x00000002)
                runCatching { gate.await() }
                k.SetThreadExecutionState(0x80000000.toInt())
            }.apply { isDaemon = true; name = "keep-awake"; start() }
            return AutoCloseable { gate.countDown(); t.interrupt() }
        }
        if (!has("systemd-inhibit")) return null
        val p = runCatching {
            ProcessBuilder("systemd-inhibit", "--what=idle:sleep", "--who=w0y", "--why=stand mode", "--mode=block", "sleep", "infinity")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        }.getOrNull() ?: return null
        return AutoCloseable { p.destroy() }
    }

    /** Автозапуск при входе в систему — только для текущего пользователя, без прав администратора. */
    fun setAutostart(enabled: Boolean) {
        runCatching {
            val exe = ProcessHandle.current().info().command().orElse(null) ?: return
            if (isWindows) {
                val key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
                val args = if (enabled) listOf("reg", "add", key, "/v", "w0y", "/t", "REG_SZ", "/d", "\"$exe\"", "/f") else listOf("reg", "delete", key, "/v", "w0y", "/f")
                ProcessBuilder(args).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor()
            } else {
                val file = Path.of(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "autostart", "w0y.desktop").toFile()
                if (enabled) {
                    file.parentFile.mkdirs()
                    file.writeText("[Desktop Entry]\nType=Application\nName=w0y\nExec=\"$exe\"\nIcon=w0y\nTerminal=false\n")
                } else {
                    file.delete()
                }
            }
        }
    }

    /** Строка для `Copy debug info`. */
    fun describe(): String =
        "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")}), сессия: $sessionType, Java ${System.getProperty("java.version")}"
}
