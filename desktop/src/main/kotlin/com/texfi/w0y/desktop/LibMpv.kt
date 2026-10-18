package com.texfi.w0y.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.texfi.w0y.data.SoundProfile
import java.io.File
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Куски C API libmpv, которые нужны плееру. Имена и сигнатуры — из mpv/client.h. */
@Suppress("FunctionName")
interface MpvApi : Library {
    fun mpv_create(): Pointer?

    fun mpv_initialize(ctx: Pointer): Int

    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int

    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int

    fun mpv_command(ctx: Pointer, args: Array<String>): Int

    fun mpv_observe_property(ctx: Pointer, userdata: Long, name: String, format: Int): Int

    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer

    fun mpv_terminate_destroy(ctx: Pointer)

    fun mpv_error_string(error: Int): String

    fun mpv_client_api_version(): Long
}

/** libc: нужен один вызов setlocale. */
@Suppress("FunctionName")
private interface LibC : Library {
    fun setlocale(category: Int, locale: String): String?
}

/**
 * Звук через libmpv, вызванную напрямую: без процесса, сокета и именованных каналов, поэтому
 * одинаково работает на Windows и Linux. Все команды идут через один поток-исполнитель: mpv_command
 * синхронна, и интерфейс не должен ждать, пока mpv, например, перестраивает цепочку фильтров.
 * События читает отдельный поток.
 */
class LibMpv(
    private val onTime: (Double) -> Unit,
    private val onDuration: (Double) -> Unit,
    private val onPause: (Boolean) -> Unit,
    private val onEnd: (reason: String) -> Unit,
    private val onFileLoaded: () -> Unit,
) : AudioEngine {
    override val name = "libmpv"
    override val version: String? get() = api?.mpv_client_api_version()?.let { "client API ${it shr 16}.${it and 0xFFFF}" }

    private val api: MpvApi? = Loader.api
    private var ctx: Pointer? = null
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "mpv-commands").apply { isDaemon = true } }

    @Volatile
    private var endFired = false

    private var lastTime = -1.0

    override fun start(): Boolean {
        val lib = api ?: return false
        forceNumericLocaleC()
        val handle = lib.mpv_create() ?: return false
        // Те же настройки, что у процессного движка, плюс «не читать чужой mpv.conf»: поведение не зависит от машины.
        val options =
            mutableListOf(
                "vid" to "no", "audio-display" to "no", "idle" to "yes", "keep-open" to "no", "ytdl" to "no", "cache" to "yes",
                "audio-client-name" to "w0y", "terminal" to "no", "config" to "no", "load-scripts" to "no", "osc" to "no",
                "input-default-bindings" to "no", "input-vo-keyboard" to "no",
            )
        // Для проверок без звуковой карты: W0Y_AO=null.
        (System.getenv("W0Y_AO") ?: System.getProperty("w0y.ao"))?.takeIf { it.isNotBlank() }?.let { options += "ao" to it }
        options.forEach { (k, v) -> lib.mpv_set_option_string(handle, k, v) }
        if (lib.mpv_initialize(handle) < 0) {
            lib.mpv_terminate_destroy(handle)
            return false
        }
        ctx = handle
        observe(handle, 1, "time-pos", FORMAT_DOUBLE)
        observe(handle, 2, "duration", FORMAT_DOUBLE)
        observe(handle, 3, "pause", FORMAT_FLAG)
        observe(handle, 4, "eof-reached", FORMAT_FLAG)
        thread(isDaemon = true, name = "mpv-events") { loop(lib, handle) }
        return true
    }

    /**
     * libmpv отказывается работать, если в процессе числовая локаль не «C» (иначе «0,5» вместо «0.5» ломает
     * разбор настроек), а JVM на Linux берёт локаль пользователя. LC_NUMERIC на glibc — категория 1.
     */
    private fun forceNumericLocaleC() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        runCatching { Native.load("c", LibC::class.java).setlocale(1, "C") }
    }

    private fun observe(handle: Pointer, id: Long, property: String, format: Int) {
        api?.mpv_observe_property(handle, id, property, format)
    }

    /** Читает события, пока mpv не скажет «shutdown»; тогда же и освобождает контекст. */
    private fun loop(lib: MpvApi, handle: Pointer) {
        while (true) {
            val event = lib.mpv_wait_event(handle, 1.0)
            when (event.getInt(0)) {
                EVENT_SHUTDOWN -> {
                    lib.mpv_terminate_destroy(handle)
                    return
                }
                EVENT_PROPERTY_CHANGE -> property(event.getPointer(16))
                EVENT_END_FILE -> {
                    val data = event.getPointer(16)
                    fireEnd(
                        when (data?.getInt(0)) {
                            END_EOF -> "eof"
                            END_STOP -> "stop"
                            END_QUIT -> "quit"
                            END_ERROR -> "error"
                            else -> "redirect"
                        },
                    )
                }
                EVENT_FILE_LOADED -> {
                    endFired = false
                    onFileLoaded()
                }
            }
        }
    }

    /** mpv_event_property: name (0), format (8), data (16). Формат NONE — значения пока нет (например, time-pos до загрузки). */
    private fun property(data: Pointer?) {
        data ?: return
        val name = data.getPointer(0)?.getString(0) ?: return
        val format = data.getInt(8)
        val value = data.getPointer(16)
        if (format == FORMAT_NONE || value == null) return
        when (name) {
            "time-pos" -> if (format == FORMAT_DOUBLE) {
                // mpv шлёт позицию на каждый кусок звука (десятки раз в секунду); интерфейсу хватает четырёх,
                // а лишние обновления перерисовывали шкалу и грели процессор. Перемотка назад проходит сразу.
                val t = value.getDouble(0)
                if (t < lastTime || t - lastTime >= TIME_STEP) {
                    lastTime = t
                    onTime(t)
                }
            }
            "duration" -> if (format == FORMAT_DOUBLE) onDuration(value.getDouble(0))
            "pause" -> if (format == FORMAT_FLAG) onPause(value.getInt(0) != 0)
            // На части потоков mpv встаёт в конце на паузу без end-file: конец трека ловим и по eof-reached.
            "eof-reached" -> if (format == FORMAT_FLAG && value.getInt(0) != 0) fireEnd("eof")
        }
    }

    /** Конец трека сообщается один раз на файл, откуда бы он ни пришёл. */
    private fun fireEnd(reason: String) {
        if (reason == "eof" || reason == "error") {
            if (endFired) return
            endFired = true
        }
        onEnd(reason)
    }

    private fun run(block: (MpvApi, Pointer) -> Unit) {
        val lib = api ?: return
        val handle = ctx ?: return
        runCatching { worker.execute { runCatching { block(lib, handle) }.onFailure { System.err.println("libmpv: ${it.message}") } } }
    }

    private fun command(vararg args: String) = run { lib, h ->
        val code = lib.mpv_command(h, arrayOf(*args))
        if (code < 0) System.err.println("libmpv: ${args.first()}: ${lib.mpv_error_string(code)}")
    }

    private fun set(property: String, value: String) = run { lib, h ->
        val code = lib.mpv_set_property_string(h, property, value)
        if (code < 0) System.err.println("libmpv: $property=$value: ${lib.mpv_error_string(code)}")
    }

    override fun load(url: String, headers: Map<String, String>) {
        // Заголовки из извлечения (user-agent, origin) нужны googlevideo: без них часть ссылок отдаёт 403.
        val ua = headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value
        val rest = headers.filterKeys { !it.equals("user-agent", true) }.map { "${it.key}: ${it.value}" }
        set("user-agent", ua ?: "Mozilla/5.0")
        set("http-header-fields", rest.joinToString(","))
        endFired = false
        lastTime = -1.0
        command("loadfile", url, "replace")
        set("pause", "no")
    }

    override fun applyAudio(profile: SoundProfile, gainDb: Double?, skipSilence: Boolean, eqBands: List<Int>?) {
        set("speed", profile.speed.toString())
        set("af", audioFilterChain(profile, gainDb, skipSilence, eqBands))
    }

    override fun setMute(muted: Boolean) = set("mute", if (muted) "yes" else "no")

    override fun setAudioDevice(id: String) = set("audio-device", id)

    override fun setPause(paused: Boolean) = set("pause", if (paused) "yes" else "no")

    override fun seek(seconds: Double) = command("seek", seconds.toString(), "absolute")

    override fun setVolume(percent: Int) = set("volume", percent.toString())

    override fun stop() = command("stop")

    override fun shutdown() {
        val lib = api
        val handle = ctx
        ctx = null
        if (lib == null || handle == null) return
        // «quit» заканчивает цикл событий; контекст освободит поток событий.
        runCatching { worker.execute { runCatching { lib.mpv_command(handle, arrayOf("quit")) } } }
        worker.shutdown()
    }

    /** Загрузка нативной библиотеки: рядом с приложением (Windows, AppImage), потом системная. */
    object Loader {
        val api: MpvApi? by lazy { load() }
        var lastError: String? = null
            private set

        private fun load(): MpvApi? {
            // QuickJS должен загрузиться раньше libmpv: иначе первая же его среда роняет приложение (см. QuickJsFirst).
            QuickJsFirst.ensure()
            val options = mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")
            val bundled = bundledDirs().flatMap { dir -> libraryFiles().map { File(dir, it) } }.filter { it.isFile }
            val candidates = bundled.map { it.absolutePath } + listOf("mpv", "libmpv.so.2", "libmpv-2", "mpv-2")
            for (name in candidates) {
                val lib = runCatching { Native.load(name, MpvApi::class.java, options) }
                if (lib.isSuccess) return lib.getOrNull()
                lastError = lib.exceptionOrNull()?.message
            }
            return null
        }

        private fun libraryFiles() = listOf("libmpv-2.dll", "mpv-2.dll", "libmpv.dll", "libmpv.so.2", "libmpv.so", "libmpv.dylib")

        /** Где искать встроенную библиотеку: каталог ресурсов Compose, `app/` и `lib/` рядом с jar. */
        private fun bundledDirs(): List<File> {
            val dirs = mutableListOf<File>()
            System.getProperty("w0y.libdir")?.let { dirs += File(it) }
            System.getProperty("compose.application.resources.dir")?.let { dirs += File(it) }
            System.getProperty("user.dir")?.let { dirs += File(it, "lib") }
            runCatching {
                val jar = File(LibMpv::class.java.protectionDomain.codeSource.location.toURI())
                jar.parentFile?.let { dirs += it; dirs += File(it, "native"); it.parentFile?.let { p -> dirs += File(p, "native") } }
            }
            return dirs.filter { it.isDirectory }
        }
    }

    companion object {
        /** Не чаще: позиция уходит в интерфейс раз в 0,25 с. */
        private const val TIME_STEP = 0.25
        private const val FORMAT_NONE = 0
        private const val FORMAT_FLAG = 3
        private const val FORMAT_DOUBLE = 5
        private const val EVENT_SHUTDOWN = 1
        private const val EVENT_END_FILE = 7
        private const val EVENT_FILE_LOADED = 8
        private const val EVENT_PROPERTY_CHANGE = 22
        private const val END_EOF = 0
        private const val END_STOP = 2
        private const val END_QUIT = 3
        private const val END_ERROR = 4

        /** Есть ли libmpv, которую можно загрузить. */
        fun available(): Boolean = Loader.api != null
    }
}
