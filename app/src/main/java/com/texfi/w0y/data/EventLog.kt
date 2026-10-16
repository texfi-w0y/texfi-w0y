package com.texfi.w0y.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Одна запись журнала. Только служебные поля: ни текста запроса, ни токенов,
 * ни данных аккаунта.
 */
data class LogEvent(
    val timeMs: Long,
    /** Что делали: `SEARCH ALL`, `SUGGEST`, `TEST` и т. п. */
    val kind: String,
    /** Метка [SearchError.tag] или `OK`. */
    val result: String,
    val httpCode: Int? = null,
    val durationMs: Long? = null,
    val bytes: Int? = null,
    /** Хост эндпоинта без пути и параметров. */
    val host: String? = null,
    /** Короткая служебная пометка (для вылетов: экран и обрезанный стек). Без пользовательских данных. */
    val detail: String? = null,
)

/** Кольцо последних событий: чистая логика без Android, чтобы её проверял тест. */
class EventRing(private val capacity: Int = 50) {
    private val items = ArrayDeque<LogEvent>()

    @Synchronized
    fun add(event: LogEvent) {
        items.addLast(event)
        while (items.size > capacity) items.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<LogEvent> = items.toList()

    @Synchronized
    fun clear() = items.clear()

    @Synchronized
    fun replaceAll(events: List<LogEvent>) {
        items.clear()
        events.takeLast(capacity).forEach { items.addLast(it) }
    }

    companion object {
        fun encode(event: LogEvent): String =
            buildJsonObject {
                put("t", event.timeMs)
                put("k", event.kind)
                put("r", event.result)
                event.httpCode?.let { put("c", it) }
                event.durationMs?.let { put("d", it) }
                event.bytes?.let { put("b", it) }
                event.host?.let { put("h", it) }
                event.detail?.let { put("x", it) }
            }.toString()

        fun decode(line: String): LogEvent? =
            runCatching {
                val json = Json.parseToJsonElement(line).jsonObject
                LogEvent(
                    timeMs = json.getValue("t").jsonPrimitive.long,
                    kind = json.getValue("k").jsonPrimitive.content,
                    result = json.getValue("r").jsonPrimitive.content,
                    httpCode = json["c"]?.jsonPrimitive?.int,
                    durationMs = json["d"]?.jsonPrimitive?.long,
                    bytes = json["b"]?.jsonPrimitive?.int,
                    host = json["h"]?.jsonPrimitive?.content,
                    detail = json["x"]?.jsonPrimitive?.content,
                )
            }.getOrNull()
    }
}

/**
 * Журнал последних 50 событий сети: в памяти и в маленьком файле во
 * внутреннем хранилище, чтобы пережить перезапуск. Наружу сам ничего не
 * отправляет — текст показывает «Copy debug info», и куда его отдать,
 * решает человек.
 */
@Singleton
class EventLog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val ring = EventRing(CAPACITY)
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "w0y-eventlog").apply { isDaemon = true } }
    private val file: File get() = File(context.filesDir, FILE_NAME)

    init {
        runCatching {
            if (file.exists()) ring.replaceAll(file.readLines().mapNotNull(EventRing::decode))
        }
        // Вылет прошлого запуска попадает в журнал при первом же старте после него.
        CrashReporter.takePending(context)?.let { ring.add(it); persist() }
    }

    fun record(event: LogEvent) {
        ring.add(event)
        persist()
    }

    fun snapshot(): List<LogEvent> = ring.snapshot()

    fun clear() {
        ring.clear()
        persist()
    }

    private fun persist() {
        val lines = ring.snapshot().map(EventRing::encode)
        writer.execute {
            runCatching { file.writeText(lines.joinToString("\n")) }
        }
    }

    private companion object {
        const val CAPACITY = 50
        const val FILE_NAME = "net-events.jsonl"
    }
}
