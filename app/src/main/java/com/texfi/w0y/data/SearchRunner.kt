package com.texfi.w0y.data

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/** Сырой ответ сети: код и тело; разбор и проверки — дело [SearchRunner]. */
class RawResponse(val code: Int, val text: String)

/** Всё, что раннеру нужно от Android, — через интерфейс, чтобы тесты шли без телефона. */
interface SearchEnv {
    fun online(): Boolean

    fun signedIn(): Boolean

    fun record(event: LogEvent)

    fun now(): Long

    /** Текущий `hl` запроса. */
    fun hl(): String

    /** Сбросить язык запроса на безопасный `en` (страна остаётся). */
    fun fallbackLocale()

    suspend fun pause(ms: Long) = delay(ms)
}

object SearchResponse {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Разбирает тело ответа. Ответ другой формы — [SearchError.Parse], а не
     * пустая выдача: «ничего не нашлось» и «ответили не то» не должны
     * выглядеть одинаково.
     */
    fun parse(text: String, requireContents: Boolean): JsonObject {
        val trimmed = text.trimStart()
        if (trimmed.isEmpty()) throw SearchException(SearchError.Parse("empty body"))
        // HTML вместо JSON: страница согласия, вход в сеть кафе или заглушка провайдера.
        if (trimmed.startsWith("<")) throw SearchException(SearchError.Parse("html page"))
        val root =
            try {
                json.parseToJsonElement(trimmed) as? JsonObject ?: throw SearchException(SearchError.Parse("not an object"))
            } catch (e: SearchException) {
                throw e
            } catch (e: Exception) {
                throw SearchException(SearchError.Parse("json"), e)
            }
        (root["error"] as? JsonObject)?.let { error ->
            val code = (error["code"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 500
            throw SearchException(SearchErrors.fromStatus(code) ?: SearchError.Http(code))
        }
        if (requireContents && "contents" !in root && "continuationContents" !in root) {
            throw SearchException(SearchError.Parse("no contents"))
        }
        return root
    }
}

/**
 * Выполняет запрос с проверкой статуса и формы ответа и одним повтором на
 * временный сбой. Запись в журнал — на каждую попытку.
 */
class SearchRunner(private val env: SearchEnv, private val host: String = HOST) {
    suspend fun run(
        kind: String,
        requireContents: Boolean = true,
        retry: Boolean = true,
        call: suspend (setLogin: Boolean?) -> RawResponse,
    ): JsonObject {
        var login: Boolean? = null
        var retried = false
        var authRetried = false
        var localeRetried = false
        while (true) {
            val started = env.now()
            var code: Int? = null
            var bytes: Int? = null
            try {
                val response = call(login)
                code = response.code
                bytes = response.text.length
                SearchErrors.fromStatus(code)?.let { throw SearchException(it) }
                val root = SearchResponse.parse(response.text, requireContents)
                env.record(LogEvent(env.now(), kind, "OK", code, env.now() - started, bytes, host))
                return root
            } catch (e: Throwable) {
                val error = SearchErrors.classify(e, online = env.online())
                if (error is SearchError.Cancelled) throw e
                env.record(LogEvent(env.now(), kind, error.tag, code, env.now() - started, bytes, host))
                runCatching { Timber.w("Запрос $kind: ${error.tag}") }
                // Просроченная cookie не должна ломать поиск: один раз без аккаунта.
                val authLike = error is SearchError.Auth || (error is SearchError.Http && error.code == 403)
                if (authLike && env.signedIn() && !authRetried) {
                    authRetried = true
                    login = false
                    continue
                }
                // Редкая локаль: на hl, которого YouTube не знает, он отвечает 400. Языки
                // приложения YouTube знает точно — для них случайный 400 не повод навсегда
                // переводить все ленты на английский («Listen again» в русском интерфейсе).
                if (error is SearchError.Http && error.code == 400 && !localeRetried && env.hl() != SAFE_HL && env.hl() !in KNOWN_HL) {
                    localeRetried = true
                    env.fallbackLocale()
                    continue
                }
                if (retry && error.retryable && !retried) {
                    retried = true
                    env.pause(if (error is SearchError.RateLimited) 1_500 else 600)
                    continue
                }
                throw if (e is SearchException) e else SearchException(error, e)
            }
        }
    }

    companion object {
        const val HOST = "music.youtube.com"
        const val SAFE_HL = "en"

        /** Языки интерфейса w0y: YouTube принимает их в hl (проверено LocaleProbeTest). */
        val KNOWN_HL = setOf("en", "ru", "uk", "pl")
    }
}
