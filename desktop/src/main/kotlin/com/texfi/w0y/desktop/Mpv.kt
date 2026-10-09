package com.texfi.w0y.desktop

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Звук играет mpv — его на Linux ставят все, он держит любые форматы YouTube
 * и ходит по сети сам. Приложение управляет им по IPC-сокету: команды
 * строками JSON туда, события и свойства обратно.
 */
class Mpv(
    private val onTime: (Double) -> Unit,
    private val onDuration: (Double) -> Unit,
    private val onPause: (Boolean) -> Unit,
    private val onEnd: (reason: String) -> Unit,
    private val onFileLoaded: () -> Unit,
) {
    private var process: Process? = null
    private var channel: SocketChannel? = null
    private val socket: Path =
        Path.of(System.getenv("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir"), "w0y-mpv-${ProcessHandle.current().pid()}.sock")

    @Volatile
    private var started = false

    fun start(): Boolean {
        runCatching { Files.deleteIfExists(socket) }
        process =
            runCatching {
                ProcessBuilder(
                    "mpv", "--idle=yes", "--no-video", "--no-terminal", "--force-window=no",
                    "--audio-display=no", "--ytdl=no", "--cache=yes", "--input-ipc-server=$socket",
                    "--audio-client-name=w0y", "--title=w0y",
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            }.getOrNull() ?: return false
        // Сокет появляется не сразу.
        var attempts = 0
        while (attempts++ < 100) {
            if (Files.exists(socket)) {
                val ok =
                    runCatching {
                        channel = SocketChannel.open(StandardProtocolFamily.UNIX).also { it.connect(UnixDomainSocketAddress.of(socket)) }
                    }.isSuccess
                if (ok) break
            }
            Thread.sleep(50)
        }
        val ch = channel ?: return false
        started = true
        thread(isDaemon = true, name = "mpv-reader") { read(ch) }
        observe(1, "time-pos")
        observe(2, "duration")
        observe(3, "pause")
        return true
    }

    private fun read(ch: SocketChannel) {
        runCatching {
            Channels.newReader(ch, Charsets.UTF_8).buffered().useLines { lines ->
                lines.forEach { line ->
                    val obj = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                    handle(obj)
                }
            }
        }
        started = false
    }

    private fun handle(obj: JsonObject) {
        // Ответ на команду: ошибку mpv видно в консоли, а не теряется молча.
        (obj["error"] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "success" }?.let { System.err.println("mpv: $it") }
        when ((obj["event"] as? JsonPrimitive)?.contentOrNull) {
            "property-change" -> {
                val value = obj["data"] as? JsonPrimitive
                when ((obj["name"] as? JsonPrimitive)?.contentOrNull) {
                    "time-pos" -> value?.doubleOrNull?.let(onTime)
                    "duration" -> value?.doubleOrNull?.let(onDuration)
                    "pause" -> value?.booleanOrNull?.let(onPause)
                }
            }

            "end-file" -> onEnd((obj["reason"] as? JsonPrimitive)?.contentOrNull ?: "")
            "file-loaded" -> onFileLoaded()
        }
    }

    @Synchronized
    private fun send(vararg command: Any?) {
        val ch = channel ?: return
        if (!started) return
        val array =
            JsonArray(
                command.map {
                    when (it) {
                        null -> JsonPrimitive(null as String?)
                        is Boolean -> JsonPrimitive(it)
                        is Number -> JsonPrimitive(it)
                        else -> JsonPrimitive(it.toString())
                    }
                },
            )
        val line = JsonObject(mapOf("command" to array)).toString() + "\n"
        runCatching { ch.write(ByteBuffer.wrap(line.toByteArray())) }
    }

    private fun observe(id: Int, property: String) = send("observe_property", id, property)

    fun load(url: String, headers: Map<String, String>) {
        // Заголовки из извлечения (user-agent, origin) нужны googlevideo: без них часть ссылок отдаёт 403.
        val ua = headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value
        val rest = headers.filterKeys { !it.equals("user-agent", true) }.map { "${it.key}: ${it.value}" }
        send("set_property", "user-agent", ua ?: "Mozilla/5.0")
        send("set_property", "http-header-fields", rest.joinToString(","))
        send("loadfile", url, "replace")
        send("set_property", "pause", false)
    }

    /**
     * Звучание трека. Скорость — свойство mpv (темп без смены тона делает сам mpv),
     * высота тона — rubberband, эхо — aecho из ffmpeg; выравнивание громкости —
     * volume по данным YouTube, пропуск тишины — silenceremove.
     */
    fun applyAudio(profile: com.texfi.w0y.data.SoundProfile, gainDb: Double?, skipSilence: Boolean, eqBands: List<Int>? = null) {
        send("set_property", "speed", profile.speed.toDouble())
        val filters = mutableListOf<String>()
        if (skipSilence) filters += "lavfi=[silenceremove=start_periods=1:start_silence=0.1:start_threshold=-50dB]"
        if (gainDb != null) filters += "lavfi=[volume=${"%.2f".format(java.util.Locale.ROOT, gainDb)}dB]"
        if (profile.pitch != 1f) filters += "rubberband=pitch-scale=${profile.pitch}"
        if (eqBands != null) {
            val freqs = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
            filters += "lavfi=[" + freqs.zip(eqBands).filter { it.second != 0 }.joinToString(",") { (f, g) -> "equalizer=f=$f:t=o:w=1:g=$g" } + "]"
        }
        profile.reverb.filter?.let { filters += "lavfi=[$it]" }
        send("set_property", "af", filters.joinToString(","))
    }

    /** Заглушение строк с матом: отдельное свойство, громкость пользователя не трогается. */
    fun setMute(muted: Boolean) = send("set_property", "mute", muted)

    fun setAudioDevice(id: String) = send("set_property", "audio-device", id)

    fun setPause(paused: Boolean) = send("set_property", "pause", paused)

    fun seek(seconds: Double) = send("seek", seconds, "absolute")

    fun setVolume(percent: Int) = send("set_property", "volume", percent)

    fun stop() = send("stop")

    fun shutdown() {
        runCatching { send("quit") }
        runCatching { channel?.close() }
        runCatching { process?.destroy() }
        runCatching { Files.deleteIfExists(socket) }
    }
}
