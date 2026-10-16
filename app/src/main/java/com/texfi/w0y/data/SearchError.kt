package com.texfi.w0y.data

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.SerializationException

/**
 * Почему поиск не дал выдачи.
 *
 * Пустая выдача — это не ошибка, а отдельное состояние экрана; сюда попадает
 * только то, что пошло не так: сеть, сервер, ответ неожиданной формы.
 */
sealed interface SearchError {
    data object Offline : SearchError

    data object Timeout : SearchError

    data object Dns : SearchError

    data object Tls : SearchError

    data class Http(val code: Int) : SearchError

    data object RateLimited : SearchError

    /** Ответ пришёл, но разобрать его нельзя; [where] — короткая метка места. */
    data class Parse(val where: String) : SearchError

    data object Auth : SearchError

    /** Отмена устаревшего запроса: пользователю не показывается. */
    data object Cancelled : SearchError

    /** [cause] — только имя класса исключения: текст сообщения может нести лишнее. */
    data class Unknown(val cause: String) : SearchError

    /** Стоит ли повторить один раз: временные сбои, а не «так ответили». */
    val retryable: Boolean
        get() = this is Timeout || this is Dns || this is RateLimited || (this is Http && code >= 500)

    /** Короткая метка для журнала и подписи на экране. */
    val tag: String
        get() =
            when (this) {
                Offline -> "OFFLINE"
                Timeout -> "TIMEOUT"
                Dns -> "DNS"
                Tls -> "TLS"
                is Http -> "HTTP $code"
                RateLimited -> "HTTP 429"
                is Parse -> "PARSE $where"
                Auth -> "AUTH"
                Cancelled -> "CANCELLED"
                is Unknown -> "UNKNOWN $cause"
            }
}

/** Исключение транспорта поиска: несёт уже разобранную причину. */
class SearchException(val error: SearchError, cause: Throwable? = null) : IOException(error.tag, cause)

object SearchErrors {
    /**
     * Сводит любое исключение к [SearchError].
     *
     * [online] — есть ли у телефона сеть вообще: без неё сбой DNS или
     * соединения — это «нет связи», а не «не нашли сервер».
     */
    fun classify(error: Throwable, online: Boolean = true): SearchError {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 6) {
            val found = classifyOne(current, online)
            if (found != null) return found
            current = current.cause
            depth++
        }
        return SearchError.Unknown(error::class.java.simpleName.ifBlank { "Error" })
    }

    private fun classifyOne(error: Throwable, online: Boolean): SearchError? =
        when {
            error is SearchException -> error.error
            error is TimeoutCancellationException -> SearchError.Timeout
            error is java.util.concurrent.CancellationException -> SearchError.Cancelled
            error is SocketTimeoutException || error::class.java.simpleName.contains("Timeout") -> SearchError.Timeout
            error is UnknownHostException -> if (online) SearchError.Dns else SearchError.Offline
            error is ConnectException || error is NoRouteToHostException ->
                if (online) SearchError.Unknown(error::class.java.simpleName) else SearchError.Offline
            error is SSLException -> SearchError.Tls
            error is SerializationException || error::class.java.simpleName.contains("ContentConvert") ->
                SearchError.Parse("json")
            else -> null
        }

    /** Ошибка по коду ответа; 2xx — не ошибка. */
    fun fromStatus(code: Int): SearchError? =
        when {
            code in 200..299 -> null
            code == 429 -> SearchError.RateLimited
            code == 401 -> SearchError.Auth
            else -> SearchError.Http(code)
        }
}
