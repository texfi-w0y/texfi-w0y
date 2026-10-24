package com.texfi.w0y

import com.texfi.w0y.data.ConnectionTestText
import com.texfi.w0y.data.DebugContext
import com.texfi.w0y.data.DebugInfoFormatter
import com.texfi.w0y.data.EventRing
import com.texfi.w0y.data.LogEvent
import com.texfi.w0y.data.NetworkSnapshot
import com.texfi.w0y.data.RawResponse
import com.texfi.w0y.data.Redact
import com.texfi.w0y.data.SearchEnv
import com.texfi.w0y.data.SearchError
import com.texfi.w0y.data.SearchErrors
import com.texfi.w0y.data.SearchException
import com.texfi.w0y.data.SearchResponse
import com.texfi.w0y.data.SearchRunner
import com.texfi.w0y.data.StepState
import com.texfi.w0y.data.TestStep
import com.texfi.w0y.data.YtJson
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Фикстуры ответов поиска, типизированные ошибки, повтор, журнал и отчёт без секретов. */
class SearchDiagnosticsTest {
    private val row =
        """{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"abc12345678"},"flexColumns":[
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song"}]}}},
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist"},{"text":" • "},{"text":"3:21"}]}}}]}}"""

    private fun response(rows: String) = """{"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[$rows]}}]}}}}]}}}"""

    // ---- разбор ответа

    @Test
    fun fullResponseParses() {
        val root = SearchResponse.parse(response(row), requireContents = true)
        assertTrue(YtJson.songs(root).isNotEmpty())
    }

    @Test
    fun emptyResultIsNotAnError() {
        val root = SearchResponse.parse(response(""), requireContents = true)
        assertTrue(YtJson.songs(root).isEmpty())
    }

    @Test
    fun unknownBlocksAndMissingFieldsAreSkipped() {
        val strange = """{"someNewRenderer":{"x":null},"musicResponsiveListItemRenderer":{"flexColumns":[]}}"""
        val root = SearchResponse.parse(response("$strange,$row"), requireContents = true)
        // Неизвестное и неполное пропускается, остальное читается.
        assertTrue(YtJson.songs(root).isNotEmpty())
    }

    @Test
    fun missingContentsIsParseError() {
        expect(SearchError.Parse("no contents")) { SearchResponse.parse("""{"responseContext":{}}""", true) }
    }

    @Test
    fun truncatedJsonIsParseError() {
        val cut = response(row).take(80)
        expect(SearchError.Parse("json")) { SearchResponse.parse(cut, true) }
    }

    @Test
    fun htmlPageIsParseError() {
        expect(SearchError.Parse("html page")) { SearchResponse.parse("<!doctype html><html>consent</html>", true) }
        expect(SearchError.Parse("empty body")) { SearchResponse.parse("  ", true) }
    }

    @Test
    fun errorObjectBecomesHttpError() {
        expect(SearchError.Http(400)) { SearchResponse.parse("""{"error":{"code":400,"message":"bad"}}""", true) }
        expect(SearchError.RateLimited) { SearchResponse.parse("""{"error":{"code":429}}""", true) }
    }

    // ---- исключения → SearchError

    @Test
    fun exceptionsMapToTypedErrors() {
        assertEquals(SearchError.Timeout, SearchErrors.classify(SocketTimeoutException()))
        assertEquals(SearchError.Dns, SearchErrors.classify(UnknownHostException("music.youtube.com"), online = true))
        assertEquals(SearchError.Offline, SearchErrors.classify(UnknownHostException("x"), online = false))
        assertEquals(SearchError.Offline, SearchErrors.classify(ConnectException(), online = false))
        assertEquals(SearchError.Tls, SearchErrors.classify(SSLHandshakeException("x")))
        assertEquals(SearchError.Parse("json"), SearchErrors.classify(SerializationException("x")))
        assertEquals(SearchError.Cancelled, SearchErrors.classify(CancellationException("x")))
        // Причина внутри обёртки тоже находится.
        assertEquals(SearchError.Timeout, SearchErrors.classify(RuntimeException("wrap", SocketTimeoutException())))
        assertTrue(SearchErrors.classify(IllegalStateException("boom")) is SearchError.Unknown)
    }

    @Test
    fun timeoutCancellationIsNotUserCancel() {
        val error =
            try {
                runBlocking { withTimeout(1) { kotlinx.coroutines.delay(1_000) } }
                null
            } catch (e: Throwable) {
                SearchErrors.classify(e)
            }
        assertEquals(SearchError.Timeout, error)
    }

    @Test
    fun statusMapping() {
        assertNull(SearchErrors.fromStatus(200))
        assertEquals(SearchError.RateLimited, SearchErrors.fromStatus(429))
        assertEquals(SearchError.Auth, SearchErrors.fromStatus(401))
        assertEquals(SearchError.Http(503), SearchErrors.fromStatus(503))
        assertTrue(SearchError.Http(503).retryable)
        assertFalse(SearchError.Http(404).retryable)
        assertTrue(SearchError.Timeout.retryable && SearchError.Dns.retryable && SearchError.RateLimited.retryable)
        assertFalse(SearchError.Tls.retryable || SearchError.Offline.retryable || SearchError.Parse("x").retryable)
    }

    // ---- повторы

    private class FakeEnv(var signedIn: Boolean = false, var hl: String = "en") : SearchEnv {
        val events = mutableListOf<LogEvent>()
        val pauses = mutableListOf<Long>()
        var localeReset = false
        override fun online() = true
        override fun signedIn() = signedIn
        override fun record(event: LogEvent) { events += event }
        override fun now() = 0L
        override fun hl() = hl
        override fun fallbackLocale() { localeReset = true; hl = "en" }
        override suspend fun pause(ms: Long) { pauses += ms }
    }

    private val ok = RawResponse(200, response(row))

    @Test
    fun retriesOnceOnTimeoutThenSucceeds() = runBlocking {
        val env = FakeEnv()
        var calls = 0
        SearchRunner(env).run("SEARCH SONGS") { calls++; if (calls == 1) throw SocketTimeoutException() else ok }
        assertEquals(2, calls)
        assertEquals(listOf("TIMEOUT", "OK"), env.events.map { it.result })
    }

    @Test
    fun retriesOnlyOnce() = runBlocking {
        val env = FakeEnv()
        var calls = 0
        try {
            SearchRunner(env).run("SEARCH SONGS") { calls++; RawResponse(503, "") }
            fail()
        } catch (e: SearchException) {
            assertEquals(SearchError.Http(503), e.error)
        }
        assertEquals(2, calls)
    }

    @Test
    fun doesNotRetryParseOrOffline() = runBlocking {
        var calls = 0
        try {
            SearchRunner(FakeEnv()).run("X") { calls++; RawResponse(200, "<html>") }
            fail()
        } catch (e: SearchException) {
            assertEquals(SearchError.Parse("html page"), e.error)
        }
        assertEquals(1, calls)
    }

    @Test
    fun rateLimitWaitsLonger() = runBlocking {
        val env = FakeEnv()
        var calls = 0
        SearchRunner(env).run("X") { calls++; if (calls == 1) RawResponse(429, "") else ok }
        assertEquals(listOf(1_500L), env.pauses)
    }

    @Test
    fun staleAccountRetriesWithoutLogin() = runBlocking {
        val env = FakeEnv(signedIn = true)
        val seen = mutableListOf<Boolean?>()
        SearchRunner(env).run("X") { login -> seen += login; if (login == null) RawResponse(401, "") else ok }
        assertEquals(listOf(null, false), seen)
    }

    @Test
    fun rareLocaleFallsBackToEnglish() = runBlocking {
        val env = FakeEnv(hl = "haw")
        var calls = 0
        SearchRunner(env).run("X") { calls++; if (calls == 1) RawResponse(400, """{"error":{"code":400}}""") else ok }
        assertTrue(env.localeReset)
        assertEquals(2, calls)
    }

    @Test
    fun appLanguageIsNotDroppedOnStray400() = runBlocking {
        val env = FakeEnv(hl = "ru")
        try {
            SearchRunner(env).run("X") { RawResponse(400, """{"error":{"code":400}}""") }
            fail()
        } catch (e: SearchException) {
            assertEquals(SearchError.Http(400), e.error)
        }
        assertFalse(env.localeReset)
        assertEquals("ru", env.hl)
    }

    @Test
    fun cancellationPropagatesAndIsNotLogged() = runBlocking {
        val env = FakeEnv()
        try {
            SearchRunner(env).run("X") { throw CancellationException("newer query") }
            fail()
        } catch (e: CancellationException) {
            // ожидаемо
        }
        assertTrue(env.events.isEmpty())
    }

    // ---- журнал и отчёт

    @Test
    fun ringKeepsLastFifty() {
        val ring = EventRing(50)
        repeat(120) { ring.add(LogEvent(it.toLong(), "SEARCH ALL", "OK")) }
        val snap = ring.snapshot()
        assertEquals(50, snap.size)
        assertEquals(70L, snap.first().timeMs)
        assertEquals(119L, snap.last().timeMs)
    }

    @Test
    fun eventsSurviveEncodeDecode() {
        val event = LogEvent(5, "SEARCH ALL", "HTTP 503", 503, 120, 4096, "music.youtube.com")
        assertEquals(event, EventRing.decode(EventRing.encode(event)))
        assertNull(EventRing.decode("not json"))
    }

    private fun context(lastTest: String? = null) =
        DebugContext(
            appVersion = "0.0.3-beta-2", build = 12, commit = "abc1234", mode = "release",
            device = "Pixel 9a", androidRelease = "16", api = 36, manufacturer = "Google", abi = "arm64-v8a",
            systemLocale = "en-PL", requestHl = "en", requestGl = "PL",
            network = NetworkSnapshot(true, "wifi", vpn = true, privateDns = "on", dataSaver = "off", powerSave = false),
            signedIn = true, lastTest = lastTest,
        )

    @Test
    fun debugInfoHasTheExpectedFacts() {
        val text = DebugInfoFormatter.format(context(), listOf(LogEvent(1_000, "SEARCH ALL", "TIMEOUT", null, 15_000, null, "music.youtube.com")))
        listOf("0.0.3-beta-2", "build 12", "abc1234", "Pixel 9a", "API 36", "hl=en gl=PL", "vpn: yes", "private DNS: on", "signed in: yes", "TIMEOUT").forEach {
            assertTrue("нет «$it» в отчёте:\n$text", it in text)
        }
    }

    @Test
    fun debugInfoLimitsToTwentyEvents() {
        val events = (1..30).map { LogEvent(it * 1000L, "SEARCH ALL", "OK") }
        val lines = DebugInfoFormatter.format(context(), events).lines().count { "SEARCH ALL" in it }
        assertEquals(20, lines)
    }

    @Test
    fun debugInfoNeverContainsSecrets() {
        val cookie = "SAPISID=FAKE_SAPISID_VALUE_123; __Secure-3PSID=FAKE_SECRET_PSID_456"
        val token = "ya29.FAKE_ACCESS_TOKEN_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val mail = "someone.private@example.com"
        // Ошибка с сообщением, в котором секреты: в журнал уходит только метка класса.
        val error = SearchErrors.classify(IllegalStateException("boom $cookie $token $mail query=my secret song"))
        val events = listOf(LogEvent(1, "SEARCH ALL", error.tag, null, 10, 100, "music.youtube.com"))
        // Даже если секрет попал в свободное поле итога проверки, он вырезается.
        val text = DebugInfoFormatter.format(context(lastTest = "1. Network: OK ($cookie $token $mail)"), events)
        listOf("FAKE_SAPISID_VALUE_123", "FAKE_SECRET_PSID_456", "FAKE_ACCESS_TOKEN", "someone.private", "my secret song").forEach {
            assertFalse("в отчёте утёк «$it»:\n$text", it in text)
        }
    }

    @Test
    fun redactRemovesCookieLikeStrings() {
        assertFalse("SAPISID=abc" in Redact.scrub("x SAPISID=abc y"))
        assertFalse("Bearer" in Redact.scrub("Authorization: Bearer abcdefghijkl"))
        assertEquals("plain text 123", Redact.scrub("plain text 123"))
    }

    @Test
    fun connectionTestTextListsSteps() {
        val text = ConnectionTestText.format(
            listOf(TestStep("Network", StepState.Ok, "wifi"), TestStep("DNS", StepState.Failed, "DNS"), TestStep("TLS", StepState.Skipped)),
        )
        assertEquals("1. Network: OK (wifi)\n2. DNS: FAILED (DNS)\n3. TLS: SKIPPED", text)
    }

    private fun expect(expected: SearchError, block: () -> Unit) {
        try {
            block()
            fail("ожидалась ошибка $expected")
        } catch (e: SearchException) {
            assertEquals(expected, e.error)
        }
    }
}
