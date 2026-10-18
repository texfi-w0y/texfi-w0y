package com.texfi.w0y.desktop

import com.texfi.w0y.desktop.i18n.S
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Вход через настоящую страницу Google — как окно входа на телефоне.
 *
 * Открывается отдельное окно браузера на пустом временном профиле, в нём живая
 * страница входа Google: пароль человек вводит сам, мы его не видим и не читаем.
 * Приложение лишь ждёт, пока появятся cookie YouTube, забирает их по протоколу
 * отладки браузера (только из этого временного профиля) и закрывает окно.
 * Профиль после входа удаляется, браузер ничего не запоминает.
 *
 * Нужен любой Chromium-браузер (chromium, chrome, brave, vivaldi, edge).
 */
object GoogleLogin {
    private val browsers = listOf("chromium", "chromium-browser", "google-chrome-stable", "google-chrome", "brave", "brave-browser", "vivaldi", "microsoft-edge-stable")

    fun findBrowser(): String? {
        if (Platform.isWindows) {
            // Edge стоит в каждой Windows 10/11; Chrome и Brave — по обычным путям установки.
            val roots = listOfNotNull(System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA"))
            val relative = listOf(
                "Microsoft\\Edge\\Application\\msedge.exe", "Google\\Chrome\\Application\\chrome.exe",
                "BraveSoftware\\Brave-Browser\\Application\\brave.exe", "Vivaldi\\Application\\vivaldi.exe", "Chromium\\Application\\chrome.exe",
            )
            return relative.flatMap { r -> roots.map { File(it, r) } }.firstOrNull { it.isFile }?.path
        }
        return browsers.firstNotNullOfOrNull { Platform.which(it)?.path }
    }

    /** Возвращает строку Cookie для music.youtube.com или null, если окно закрыли раньше. */
    suspend fun signIn(onStatus: (String) -> Unit): Result<String?> = withContext(Dispatchers.IO) {
        val browser = findBrowser() ?: return@withContext Result.failure(IllegalStateException(S("d_login_no_browser")))
        val port = ServerSocket(0).use { it.localPort }
        val profile = Files.createTempDirectory("w0y-login").toFile()
        val process =
            runCatching {
                ProcessBuilder(
                    browser,
                    "--user-data-dir=${profile.absolutePath}",
                    "--remote-debugging-port=$port",
                    "--remote-allow-origins=*",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-features=Translate",
                    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F",
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            }.getOrElse { return@withContext Result.failure(it) }
        try {
            onStatus(S("d_login_waiting_browser"))
            val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
            var wsUrl: String? = null
            var tries = 0
            while (wsUrl == null && tries++ < 60 && process.isAlive) {
                wsUrl =
                    runCatching {
                        val body = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/json/version")).build(), HttpResponse.BodyHandlers.ofString()).body()
                        (Json.parseToJsonElement(body).jsonObject["webSocketDebuggerUrl"] as? JsonPrimitive)?.contentOrNull
                    }.getOrNull()
                if (wsUrl == null) delay(500)
            }
            wsUrl ?: return@withContext Result.failure(IllegalStateException(S("d_login_no_port")))
            var settled = 0
            while (process.isAlive) {
                val cookies = runCatching { cookies(wsUrl) }.getOrNull().orEmpty()
                val relevant = cookies.filter { it.domain.trimStart('.').let { d -> d == "youtube.com" || d == "music.youtube.com" } }
                if (relevant.any { it.name == "SAPISID" }) {
                    // Дать странице доустановить остальные cookie после входа.
                    if (++settled >= 3) {
                        return@withContext Result.success(relevant.distinctBy { it.name }.joinToString("; ") { "${it.name}=${it.value}" })
                    }
                }
                delay(1500)
            }
            Result.success(null)
        } finally {
            runCatching { process.destroy() }
            runCatching { process.waitFor(3, TimeUnit.SECONDS) }
            runCatching { process.destroyForcibly() }
            runCatching { profile.deleteRecursively() }
        }
    }

    data class Cookie(val name: String, val value: String, val domain: String)

    /** Все cookie браузера через CDP (Storage.getCookies на уровне браузера). */
    internal fun cookies(wsUrl: String): List<Cookie> {
        val result = CompletableFuture<String>()
        val buffer = StringBuilder()
        val listener =
            object : WebSocket.Listener {
                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    buffer.append(data)
                    if (last) result.complete(buffer.toString())
                    webSocket.request(1)
                    return null
                }

                override fun onError(webSocket: WebSocket, error: Throwable) {
                    result.completeExceptionally(error)
                }
            }
        val ws = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(wsUrl), listener).get(5, TimeUnit.SECONDS)
        try {
            ws.sendText("""{"id":1,"method":"Storage.getCookies"}""", true).get(5, TimeUnit.SECONDS)
            val response = Json.parseToJsonElement(result.get(8, TimeUnit.SECONDS)).jsonObject
            val array = response["result"]?.jsonObject?.get("cookies") as? JsonArray ?: return emptyList()
            return array.mapNotNull {
                val o = it as? JsonObject ?: return@mapNotNull null
                Cookie(
                    (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                    (o["value"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    (o["domain"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                )
            }
        } finally {
            runCatching { ws.sendClose(WebSocket.NORMAL_CLOSURE, "") }
        }
    }
}
