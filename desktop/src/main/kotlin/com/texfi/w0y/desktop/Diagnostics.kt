package com.texfi.w0y.desktop

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Диагностика без отправки куда-либо: журнал событий в памяти, краткая запись о вылете в файл
 * на диске и текст «Copy debug info», который человек сам копирует и показывает, если захочет.
 * Куки, токены и адреса с параметрами в журнал не попадают.
 */
object Diag {
    class Event(val at: Long, val kind: String, val result: String, val detail: String?)

    private const val MAX_EVENTS = 40
    private const val MAX_CRASH_FILE = 48 * 1024
    private val events = java.util.concurrent.ConcurrentLinkedDeque<Event>()

    @Volatile
    var lastTest: String? = null

    /** Запись о событии (ошибка сети, сбой воспроизведения). Хранятся последние [MAX_EVENTS]. */
    fun event(kind: String, result: String, detail: String? = null) {
        events.addLast(Event(System.currentTimeMillis(), kind, result, detail?.let(::redact)))
        while (events.size > MAX_EVENTS) events.pollFirst()
    }

    /** Время в местной зоне полным форматом: «17 Oct, 19:08». */
    fun stamp(ms: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("dd MMM, HH:mm", Locale.ENGLISH).apply { timeZone = zone }.format(Date(ms))

    private val crashFile get() = Platform.configDir().resolve("crash.log").toFile()

    /** Необработанное исключение любого потока записывается коротко, приложение ведёт себя как обычно. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordCrash(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun recordCrash(threadName: String, error: Throwable) {
        val text = buildString {
            append(stamp(System.currentTimeMillis())).append("  ").append(threadName).append("  ")
            append(error.javaClass.simpleName).append(": ").appendLine(redact(error.message.orEmpty()).take(200))
            error.stackTrace.take(8).forEach { appendLine("    at $it") }
        }
        event("CRASH", error.javaClass.simpleName, error.message)
        val file = crashFile
        file.parentFile.mkdirs()
        // Файл не растёт без границ: когда переполнен, остаётся хвост.
        val old = if (file.exists()) file.readText() else ""
        file.writeText((old + text).takeLast(MAX_CRASH_FILE))
    }

    fun crashTail(lines: Int = 24): String =
        runCatching { crashFile.takeIf { it.exists() }?.readLines()?.takeLast(lines)?.joinToString("\n") }.getOrNull() ?: ""

    /** Вырезает из свободного текста то, что похоже на куки и токены, и параметры в адресах. */
    fun redact(text: String): String =
        text
            .replace(Regex("""(?i)\b(cookie|authorization|token|sapisid|sid|key|password)\b\s*[=:]\s*\S+"""), "$1=…")
            .replace(Regex("""(https?://[^\s?]+)\?\S+"""), "$1?…")

    private fun line(e: Event): String =
        buildString {
            append(stamp(e.at)).append("  ").append(e.kind).append("  ").append(e.result)
            e.detail?.let { append("\n      ").append(it.replace("\n", "\n      ")) }
        }

    /** Весь текст для кнопки «Copy debug info». */
    fun text(app: AppState): String =
        buildString {
            appendLine("w0y debug info")
            appendLine("app: $APP_VERSION (desktop)")
            appendLine("os: ${Platform.describe()}")
            appendLine("engine: ${app.player.engineName}${app.player.engineVersion?.let { " ($it)" }.orEmpty()}")
            appendLine("audio device: ${app.st.audioDevice}")
            appendLine("ui: style=${app.st.uiStyle}, scale=${app.st.uiScale}, language=${app.st.language}, theme=${app.st.theme}")
            appendLine("system locale: ${Locale.getDefault().toLanguageTag()}, zone: ${TimeZone.getDefault().id}")
            appendLine("signed in: ${if (app.signedIn) "yes" else "no"}")
            appendLine("visualizer capture: ${if (SpectrumCapture.available) "yes" else "no"}")
            appendLine()
            appendLine("last ${MAX_EVENTS} events (newest last):")
            val recent = events.toList()
            if (recent.isEmpty()) appendLine("  none") else recent.forEach { appendLine("  " + line(it)) }
            appendLine()
            val crashes = crashTail()
            appendLine("crash log (tail):")
            if (crashes.isBlank()) appendLine("  none") else appendLine(crashes.prependIndent("  "))
            appendLine()
            appendLine("connection test:")
            appendLine(lastTest?.prependIndent("  ") ?: "  not run")
        }

    /** Проверка связи с теми двумя адресами, к которым приложение вообще ходит. Ничего не отправляет, кроме обычного запроса. */
    suspend fun testConnection(): String =
        withContext(Dispatchers.IO) {
            val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build()
            fun probe(name: String, url: String): String {
                val started = System.currentTimeMillis()
                return runCatching {
                    val response = http.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "w0y").timeout(Duration.ofSeconds(8)).build(), HttpResponse.BodyHandlers.discarding())
                    "$name: http=${response.statusCode()} ${System.currentTimeMillis() - started}ms"
                }.getOrElse { "$name: ${it.javaClass.simpleName} ${System.currentTimeMillis() - started}ms" }
            }
            listOf(probe("youtube music", "https://music.youtube.com/"), probe("github", "https://api.github.com/")).joinToString("\n")
                .also { lastTest = "${stamp(System.currentTimeMillis())}\n$it" }
        }
}
