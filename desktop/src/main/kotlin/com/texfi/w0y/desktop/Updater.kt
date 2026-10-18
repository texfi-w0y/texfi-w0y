package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texfi.w0y.data.UpdateInfo
import com.texfi.w0y.data.UpdateParsing
import com.texfi.w0y.data.UpdateState
import com.texfi.w0y.data.UpdateVersion
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Проверка обновлений на GitHub — единственный запрос приложения, кроме YouTube. Не чаще раза в
 * шесть часов, с выключателем, без каких-либо данных о человеке. Windows: скачивает установщик,
 * сверяет SHA-256 и запускает его. Linux: показывает релиз и открывает страницу — AppImage и архив
 * человек ставит сам (так проще и не требует прав на запись в системные каталоги).
 */
class Updater(private val app: AppState, private val scope: CoroutineScope) {
    var state: UpdateState by mutableStateOf(UpdateState.Idle)
        private set

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()

    /** Файл релиза, который нужен этой ОС. Установщик раньше portable-архива. */
    private val suffixes: List<String> get() = if (Platform.isWindows) listOf("-setup.exe", ".exe") else listOf(".appimage", ".tar.gz")

    /** На запуске: если включено и прошло шесть часов. Тихо: «обновлений нет» ничего не показывает. */
    fun checkOnStart() {
        if (!app.st.checkUpdates) return
        if (System.currentTimeMillis() - app.st.lastUpdateCheck < SIX_HOURS) return
        check(manual = false)
    }

    fun check(manual: Boolean = true) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        scope.launch {
            val result = withContext(Dispatchers.IO) { fetchLatest() }
            app.setSt { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            state = when {
                result.isFailure -> if (manual) UpdateState.Failed(reasonOf(result.exceptionOrNull())) else UpdateState.Idle
                result.getOrNull() == null -> if (manual) UpdateState.Failed(UpdateState.Reason.NO_APK) else UpdateState.Idle
                UpdateVersion.isNewer(result.getOrThrow()!!.tag, APP_VERSION) -> UpdateState.Available(result.getOrThrow()!!)
                else -> if (manual) UpdateState.UpToDate else UpdateState.Idle
            }
            (state as? UpdateState.Available)?.let { Diag.event("UPDATE", "available ${it.info.tag}") }
        }
    }

    private class RateLimited : Exception()

    private fun fetchLatest(): Result<UpdateInfo?> =
        runCatching {
            val request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/${UpdateParsing.REPO}/releases/latest"))
                .header("User-Agent", "w0y-updater").header("Accept", "application/vnd.github+json").timeout(Duration.ofSeconds(15)).build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                200 -> UpdateParsing.parseRelease(response.body(), suffixes)
                403, 429 -> throw RateLimited()
                else -> throw IllegalStateException("http ${response.statusCode()}")
            }
        }

    private fun reasonOf(e: Throwable?): UpdateState.Reason =
        when (e) {
            is RateLimited -> UpdateState.Reason.RATE_LIMIT
            is java.io.IOException -> UpdateState.Reason.OFFLINE
            else -> UpdateState.Reason.SERVER
        }

    /** Windows: скачать установщик, проверить сумму и запустить. Linux: открыть страницу релиза. */
    fun update() {
        val info = (state as? UpdateState.Available)?.info ?: (state as? UpdateState.Failed)?.info ?: return
        if (!Platform.isWindows) {
            Platform.openUrl("https://github.com/${UpdateParsing.REPO}/releases/tag/${info.tag}")
            return
        }
        if (!UpdateParsing.isTrustedDownload(info.apkUrl)) {
            state = UpdateState.Failed(UpdateState.Reason.BAD_FILE, info)
            return
        }
        state = UpdateState.Downloading(info, 0, info.apkSize)
        scope.launch {
            val result = withContext(Dispatchers.IO) { download(info) }
            state = result.fold(
                onSuccess = { UpdateState.Ready(info, it) },
                onFailure = { UpdateState.Failed(if (it is BadFile) UpdateState.Reason.BAD_FILE else reasonOf(it), info) },
            )
        }
    }

    private class BadFile : Exception()

    private fun download(info: UpdateInfo): Result<File> =
        runCatching {
            val dir = File(System.getProperty("java.io.tmpdir"), "w0y-update").apply { mkdirs() }
            val target = File(dir, info.apkUrl.substringAfterLast('/'))
            val expected = info.sha256 ?: info.sumsUrl?.let { runCatching { fetchSum(it, target.name) }.getOrNull() }
            val response = http.send(HttpRequest.newBuilder(URI.create(info.apkUrl)).header("User-Agent", "w0y-updater").build(), HttpResponse.BodyHandlers.ofInputStream())
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            response.body().use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        state = UpdateState.Downloading(info, done, info.apkSize)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected != null && expected != actual) {
                target.delete()
                throw BadFile()
            }
            target
        }

    private fun fetchSum(url: String, name: String): String? {
        if (!UpdateParsing.isTrustedDownload(url)) return null
        val body = http.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "w0y-updater").build(), HttpResponse.BodyHandlers.ofString()).body()
        return UpdateParsing.sumFor(body, name)
    }

    /** Запустить скачанный установщик и закрыть приложение, чтобы он мог заменить файлы. */
    fun install() {
        val ready = state as? UpdateState.Ready ?: return
        runCatching { ProcessBuilder(ready.file.absolutePath).start() }
        Actions.quit()
    }

    /** «Позже»: этот релиз больше не всплывает, пока не выйдет следующий; кнопка в настройках остаётся. */
    fun later() {
        (state as? UpdateState.Available)?.let { a -> app.setSt { it.copy(skippedUpdate = a.info.tag) } }
    }

    /** Показывать ли плашку на главной. */
    val bannerVisible: Boolean get() = (state as? UpdateState.Available)?.info?.tag?.let { it != app.st.skippedUpdate } == true

    private companion object {
        const val SIX_HOURS = 6L * 60 * 60 * 1000
    }
}
