package com.texfi.w0y.data

import android.os.SystemClock
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeLocale
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * Единственный путь, которым поиск ходит в сеть.
 *
 * Раньше ответ сразу читался как JSON и отдавался парсеру: HTTP 400/403/429
 * приходили тем же JSON-объектом с полем `error`, парсер не находил в нём
 * треков, и сбой выглядел как «ничего не найдено». Теперь статус и форма
 * ответа проверяются до разбора (см. [SearchRunner]), причина становится
 * [SearchError], а временный сбой повторяется один раз.
 */
@Singleton
class SearchTransport @Inject constructor(
    private val innerTube: InnerTube,
    private val events: EventLog,
    private val network: NetworkInfo,
) {
    /** Язык запроса сброшен на «en» после отказа YouTube; живёт до смены языка в настройках. */
    @Volatile
    private var localeFallback = false

    fun resetLocaleFallback() {
        localeFallback = false
    }

    private val env =
        object : SearchEnv {
            override fun online() = network.isOnline()

            override fun signedIn() = !innerTube.cookie.isNullOrBlank()

            override fun record(event: LogEvent) = events.record(event)

            override fun now() = SystemClock.elapsedRealtime()

            override fun hl() = if (localeFallback) SearchRunner.SAFE_HL else innerTube.locale.hl

            override fun fallbackLocale() {
                localeFallback = true
                innerTube.locale = YouTubeLocale(gl = innerTube.locale.gl, hl = SearchRunner.SAFE_HL)
            }
        }
    private val runner = SearchRunner(env)

    suspend fun execute(
        kind: String,
        requireContents: Boolean = true,
        retry: Boolean = true,
        call: suspend (setLogin: Boolean?) -> HttpResponse,
    ): JsonObject =
        runner.run(kind, requireContents, retry) { login ->
            val response = call(login)
            RawResponse(response.status.value, response.bodyAsText())
        }

    companion object {
        const val HOST = SearchRunner.HOST
    }
}
