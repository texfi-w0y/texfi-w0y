package com.texfi.w0y.desktop

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Управление снаружи. `w0y --do play_pause` шлёт команду уже запущенному
 * приложению: так на горячие клавиши окружения (KDE, GNOME, sway) вешается
 * что угодно, а не только то, что умеет MPRIS. Список команд — `w0y --do list`.
 * Второй запуск без аргументов не плодит окно, а поднимает первое.
 */
object Control {
    private val path: Path =
        System.getenv("W0Y_CONTROL")?.let { Path.of(it) }
            ?: Path.of(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"), "w0y-control.sock")

    /** Разбор командной строки: true — запуск был командой, окно открывать не нужно. */
    fun sendIfRequested(args: Array<String>): Boolean {
        val i = args.indexOf("--do")
        if (i < 0) {
            // Просто второй экземпляр: просим первый показаться и выходим.
            if (alive()) {
                send("raise")
                return true
            }
            return false
        }
        val command = args.getOrNull(i + 1)
        if (command == null || command == "list") {
            println(Action.entries.joinToString("\n") { it.id } + "\nraise")
            return true
        }
        if (command != "raise" && Action.byId(command) == null) {
            System.err.println("w0y: нет такой команды «$command». Список: w0y --do list")
            return true
        }
        if (!send(command)) System.err.println("w0y: приложение не запущено")
        return true
    }

    private fun alive(): Boolean = Files.exists(path) && runCatching { SocketChannel.open(StandardProtocolFamily.UNIX).use { it.connect(UnixDomainSocketAddress.of(path)); true } }.getOrDefault(false)

    private fun send(command: String): Boolean =
        runCatching {
            SocketChannel.open(StandardProtocolFamily.UNIX).use {
                it.connect(UnixDomainSocketAddress.of(path))
                it.write(ByteBuffer.wrap((command + "\n").toByteArray()))
            }
            true
        }.getOrDefault(false)

    /** Что сделать, чтобы окно показалось: ставит Main. */
    var raiseHook: () -> Unit = {}

    fun start(app: AppState, scope: CoroutineScope, raise: () -> Unit) {
        raiseHook = raise
        runCatching { Files.deleteIfExists(path) }
        val server = runCatching { ServerSocketChannel.open(StandardProtocolFamily.UNIX).also { it.bind(UnixDomainSocketAddress.of(path)) } }.getOrNull() ?: return
        thread(isDaemon = true, name = "w0y-control") {
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    val text = java.io.BufferedReader(java.io.InputStreamReader(java.nio.channels.Channels.newInputStream(client))).readLine()?.trim()
                    client.close()
                    scope.launch(Dispatchers.Main) {
                        when (text) {
                            null, "" -> Unit
                            "raise" -> raise()
                            else -> Action.byId(text)?.let { Actions.run(app, it) }
                        }
                    }
                }
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { Files.deleteIfExists(path) } })
    }
}
