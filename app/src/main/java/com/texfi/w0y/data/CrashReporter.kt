package com.texfi.w0y.data

import android.content.Context
import java.io.File

/**
 * Локальная запись вылета: тип, короткое сообщение, обрезанный стек и экран, на котором
 * это случилось. Пишется в маленький файл во внутреннем хранилище, на следующем запуске
 * попадает в [EventLog] и в `Copy debug info`. Наружу ничего не отправляется.
 */
object CrashReporter {
    private const val FILE_NAME = "last-crash.txt"
    private const val MAX_FRAMES = 8
    private const val MAX_MESSAGE = 120

    /** Текущий экран: ставит оболочка приложения; нужен только для отчёта о вылете. */
    @Volatile
    var screen: String = "start"

    fun install(context: Context) {
        val dir = context.applicationContext.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(dir, FILE_NAME).writeText(describe(error, screen, System.currentTimeMillis(), thread.name)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Читает и удаляет запись о вылете прошлого запуска. */
    fun takePending(context: Context): LogEvent? =
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null
            val text = file.readText()
            file.delete()
            parse(text)
        }.getOrNull()

    /** Формат файла: первая строка `время|тип|экран|поток`, дальше сообщение и стек. */
    internal fun describe(error: Throwable, screen: String, nowMs: Long, thread: String): String {
        val root = generateSequence(error) { it.cause?.takeIf { c -> c !== it } }.last()
        val frames = root.stackTrace.take(MAX_FRAMES).joinToString("\n") { "at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        val message = root.message.orEmpty().replace(Regex("""https?://\S+"""), "[url]").replace('\n', ' ').take(MAX_MESSAGE)
        return "$nowMs|${error.javaClass.name}|${screen.replace('|', '/')}|${thread.replace('|', '/')}\n${root.javaClass.name}: $message\n$frames"
    }

    fun describeForTest(error: Throwable, screen: String): String = describe(error, screen, 1_000L, "main")

    fun parseForTest(text: String): LogEvent? = parse(text)

    internal fun parse(text: String): LogEvent? {
        val lines = text.lines()
        val head = lines.firstOrNull()?.split('|') ?: return null
        if (head.size < 4) return null
        val time = head[0].toLongOrNull() ?: return null
        val type = head[1].substringAfterLast('.')
        return LogEvent(
            timeMs = time,
            kind = "CRASH ${head[2]}",
            result = type,
            detail = "thread=${head[3]}\n" + lines.drop(1).joinToString("\n"),
        )
    }
}
