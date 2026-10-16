package com.texfi.w0y.data

import android.os.SystemClock
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request

enum class StepState { Pending, Running, Ok, Failed, Skipped }

data class TestStep(val title: String, val state: StepState = StepState.Pending, val detail: String? = null)

/** Итог последней проверки: хранится в памяти, чтобы попасть в debug info. */
@Singleton
class ConnectionTestStore @Inject constructor() {
    @Volatile
    var last: String? = null
}

object ConnectionTestText {
    fun format(steps: List<TestStep>): String =
        steps.mapIndexed { i, step ->
            val mark =
                when (step.state) {
                    StepState.Ok -> "OK"
                    StepState.Failed -> "FAILED"
                    StepState.Skipped -> "SKIPPED"
                    StepState.Running -> "RUNNING"
                    StepState.Pending -> "-"
                }
            "${i + 1}. ${step.title}: $mark" + (step.detail?.let { " ($it)" } ?: "")
        }.joinToString("\n")
}

/**
 * Проверка соединения по шагам, только по нажатию: сеть, DNS, TLS, пробный
 * поиск и разбор ответа. Запрос фиксированный и не содержит ничего личного.
 */
@Singleton
class ConnectionTest @Inject constructor(
    private val okHttp: OkHttpClient,
    private val network: NetworkInfo,
    private val transport: SearchTransport,
    private val innerTube: InnerTube,
    private val store: ConnectionTestStore,
) {
    fun run(): Flow<List<TestStep>> =
        flow {
            val steps =
                mutableListOf(
                    TestStep("Network"),
                    TestStep("DNS lookup of ${SearchTransport.HOST}"),
                    TestStep("HTTPS connection (TLS)"),
                    TestStep("Test search request"),
                    TestStep("Reading the response"),
                )
            emit(steps.toList())
            var failed = false
            var response: kotlinx.serialization.json.JsonObject? = null
            for (i in steps.indices) {
                if (failed) {
                    steps[i] = steps[i].copy(state = StepState.Skipped)
                    continue
                }
                steps[i] = steps[i].copy(state = StepState.Running)
                emit(steps.toList())
                val started = SystemClock.elapsedRealtime()
                val outcome: Result<String?> =
                    runCatching {
                        when (i) {
                            0 -> network.snapshot().let { if (!it.online) throw SearchException(SearchError.Offline) else it.transport }
                            1 -> withTimeout(STEP_TIMEOUT_MS) { dns() }
                            2 -> withTimeout(STEP_TIMEOUT_MS) { tls() }
                            3 -> {
                                response =
                                    transport.execute("TEST", retry = false) { login ->
                                        innerTube.search(YouTubeClient.WEB_REMIX, TEST_QUERY, YouTubeRepository.SONGS_FILTER, setLogin = login)
                                    }
                                null
                            }
                            else -> {
                                val songs = YtJson.songs(response ?: throw SearchException(SearchError.Parse("no response")))
                                if (songs.isEmpty()) throw SearchException(SearchError.Parse("no results parsed"))
                                "${songs.size} results"
                            }
                        }
                    }
                val ms = SystemClock.elapsedRealtime() - started
                steps[i] =
                    outcome.fold(
                        onSuccess = { steps[i].copy(state = StepState.Ok, detail = listOfNotNull(it, "${ms} ms").joinToString(", ")) },
                        onFailure = {
                            failed = true
                            steps[i].copy(state = StepState.Failed, detail = SearchErrors.classify(it, online = network.isOnline()).tag)
                        },
                    )
                emit(steps.toList())
            }
            store.last = ConnectionTestText.format(steps)
        }.flowOn(Dispatchers.IO)

    private suspend fun dns(): String =
        withContext(Dispatchers.IO) {
            val addresses = InetAddress.getAllByName(SearchTransport.HOST)
            "${addresses.size} addresses"
        }

    /** Любой ответ сервера на HEAD значит, что рукопожатие TLS прошло. */
    private suspend fun tls(): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("https://${SearchTransport.HOST}/").head().build()
            okHttp.newBuilder().followRedirects(false).build().newCall(request).execute().use { "HTTP ${it.code}" }
        }

    private companion object {
        const val STEP_TIMEOUT_MS = 8_000L
        const val TEST_QUERY = "test"
    }
}
