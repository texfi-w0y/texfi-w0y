package com.texfi.w0y.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.texfi.w0y.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/** Новая версия на GitHub: что за версия, что в ней нового и откуда качать APK. */
data class UpdateInfo(
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** SHA-256 файла из API GitHub, если он его отдал. */
    val sha256: String?,
    /** Адрес файла SHA256SUMS.txt того же релиза — запасной источник суммы. */
    val sumsUrl: String?,
) {
    /** «v0.0.4-beta-1» → «0.0.4 beta-1» для людей. */
    val label: String get() = UpdateVersion.label(tag)
}

/** Этап обновления. Экран и окно при запуске показывают именно его. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(val info: UpdateInfo) : UpdateState

    data class Downloading(val info: UpdateInfo, val done: Long, val total: Long) : UpdateState

    data class Ready(val info: UpdateInfo, val file: File) : UpdateState

    data class Failed(val reason: Reason, val info: UpdateInfo? = null) : UpdateState

    enum class Reason { OFFLINE, RATE_LIMIT, SERVER, BAD_FILE, NO_APK, STORAGE }
}

/** Что нового в версии: показывается один раз после обновления и по кнопке в настройках. */
data class WhatsNew(val label: String, val notes: String)

/** Версии вида «v0.0.3-beta-2»: разбор и порядок. Суффиксы сборок (-debug, -bench) отбрасываются. */
object UpdateVersion {
    private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-beta-?(\d+))?""")

    /** Ключ порядка: major, minor, patch, стадия. Релиз без beta старше любой беты той же версии. */
    fun key(version: String): List<Int>? {
        val m = pattern.find(version.trim()) ?: return null
        val (a, b, c, beta) = m.destructured
        return listOf(a.toInt(), b.toInt(), c.toInt(), if (beta.isEmpty()) Int.MAX_VALUE else beta.toInt())
    }

    /** Новее ли [remote], чем [local]. Нечитаемая версия на любой стороне — «не новее»: лучше промолчать. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = key(remote) ?: return false
        val l = key(local) ?: return false
        for (i in r.indices) {
            if (r[i] != l[i]) return r[i] > l[i]
        }
        return false
    }

    fun label(tag: String): String = tag.removePrefix("v").replaceFirst("-beta", " beta")

    /** Тег релиза, которому соответствует эта сборка. */
    fun tagOf(versionName: String): String = "v" + (pattern.find(versionName)?.value?.removePrefix("v") ?: versionName)
}

/** Разбор ответа GitHub и очистка описания релиза. Всё чистое — проверяется тестами. */
object UpdateParsing {
    private const val APK_NAME = ".apk"
    private const val SUMS_NAME = "SHA256SUMS.txt"

    /** Адрес файла из релиза обязан вести на наш репозиторий: подмена ответа не должна увести на чужой сервер. */
    fun isTrustedDownload(url: String): Boolean =
        url.startsWith("https://github.com/$REPO/releases/download/")

    fun parseRelease(body: String): UpdateInfo? =
        runCatching {
            val root = Json.parseToJsonElement(body).jsonObject
            if (root["draft"]?.jsonPrimitive?.contentOrNull == "true") return null
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
            val assets = (root["assets"] as? JsonArray).orEmpty().map { it.jsonObject }
            val apk =
                assets.firstOrNull { it.name().endsWith(APK_NAME, ignoreCase = true) && trusted(it) }
                    ?: return null
            val sums = assets.firstOrNull { it.name().equals(SUMS_NAME, ignoreCase = true) && trusted(it) }
            UpdateInfo(
                tag = tag,
                notes = clean(root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                apkUrl = apk.url(),
                apkSize = (apk["size"] as? JsonPrimitive)?.longOrNull ?: -1L,
                sha256 = (apk["digest"] as? JsonPrimitive)?.contentOrNull?.removePrefix("sha256:")?.lowercase()?.takeIf { it.length == 64 },
                sumsUrl = sums?.url(),
            )
        }.getOrNull()

    /** Название релиза и описание — для «Что нового» (запрос по тегу). */
    fun parseNotes(body: String): String? =
        runCatching { clean(Json.parseToJsonElement(body).jsonObject["body"]?.jsonPrimitive?.contentOrNull.orEmpty()) }.getOrNull()

    private fun JsonObject.name() = this["name"]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.url() = this["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun trusted(asset: JsonObject) = isTrustedDownload(asset.url())

    /** Строка нужной суммы из SHA256SUMS.txt («<hash>  <имя>»). */
    fun sumFor(sums: String, fileName: String): String? =
        sums.lineSequence()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].trimStart('*').substringAfterLast('/') == fileName && it[0].length == 64 }
            ?.get(0)
            ?.lowercase()

    /**
     * Markdown описания релиза → простые строки: заголовки без решёток, списки точками,
     * ссылки без адресов, без HTML-комментариев и разделителей.
     */
    fun clean(markdown: String): String =
        markdown
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .lineSequence()
            .map { it.trim() }
            .filterNot { it.matches(Regex("""[-*_=]{3,}""")) }
            .map { line ->
                line
                    .replace(Regex("""!?\[([^\]]*)]\([^)]*\)"""), "$1")
                    .replace(Regex("""^#{1,6}\s*"""), "")
                    .replace(Regex("""^[-*+]\s+"""), "• ")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .trim()
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .take(MAX_NOTES)

    const val REPO = "texfi-w0y/texfi-w0y"
    private const val MAX_NOTES = 3_000
}

/**
 * Обновление приложения прямо с GitHub, как у Obtainium: узнаёт о новой версии, качает APK и
 * отдаёт его системному установщику. Запросы — только к api.github.com и github.com, без
 * аккаунта и без каких-либо данных о человеке или телефоне; подпись нового APK проверяет сам
 * Android (с чужим ключом он не поставится). Если у GitHub есть контрольная сумма файла, она
 * сверяется до установки.
 */
@Singleton
class UpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    okHttp: OkHttpClient,
) {
    // Загрузка APK дольше обычного запроса: общий клиент с короткими таймаутами для неё не годится.
    private val http =
        okHttp.newBuilder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("w0y_update", Context.MODE_PRIVATE)
    private var job: Job? = null

    /** Подмена в тестах: имя версии и код сборки. */
    internal var versionName: String = BuildConfig.VERSION_NAME
    internal var versionCode: Int = BuildConfig.VERSION_CODE
    internal var clock: () -> Long = System::currentTimeMillis

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _whatsNew = MutableStateFlow<WhatsNew?>(null)

    /** Не null — пора показать окно «Что нового». */
    val whatsNew: StateFlow<WhatsNew?> = _whatsNew.asStateFlow()

    /** Версия, про которую человек сказал «позже»: окно при запуске её больше не показывает. */
    private val _deferred = MutableStateFlow(prefs.getString(KEY_DEFERRED, null))
    val deferredTag: StateFlow<String?> = _deferred.asStateFlow()

    /** Вызывается при каждом запуске: проверка не чаще раза в [CHECK_EVERY_MS], плюс «что нового» после обновления. */
    fun onLaunch(autoCheck: Boolean) {
        scope.launch { detectVersionChange() }
        if (autoCheck && clock() - prefs.getLong(KEY_LAST_CHECK, 0L) > CHECK_EVERY_MS) check()
    }

    /** Проверка по запросу человека или по расписанию. */
    fun check() {
        if (job?.isActive == true || _state.value is UpdateState.Downloading) return
        job =
            scope.launch {
                _state.value = UpdateState.Checking
                _state.value = runCatching { fetchLatest() }.getOrElse { failure(it) }
            }
    }

    private suspend fun fetchLatest(): UpdateState =
        withContext(Dispatchers.IO) {
            val response = http.newCall(apiRequest("https://api.github.com/repos/${UpdateParsing.REPO}/releases/latest")).execute()
            response.use {
                prefs.edit().putLong(KEY_LAST_CHECK, clock()).apply()
                when {
                    it.code == 404 -> UpdateState.UpToDate
                    it.code == 403 || it.code == 429 -> UpdateState.Failed(UpdateState.Reason.RATE_LIMIT)
                    !it.isSuccessful -> UpdateState.Failed(UpdateState.Reason.SERVER)
                    else -> {
                        val info = UpdateParsing.parseRelease(it.body.string())
                        when {
                            info == null -> UpdateState.Failed(UpdateState.Reason.NO_APK)
                            UpdateVersion.isNewer(info.tag, versionName) -> UpdateState.Available(info)
                            else -> UpdateState.UpToDate
                        }
                    }
                }
            }
        }

    private fun failure(error: Throwable): UpdateState {
        Timber.w(error, "Проверка обновлений не удалась")
        return UpdateState.Failed(if (error is java.io.IOException) UpdateState.Reason.OFFLINE else UpdateState.Reason.SERVER)
    }

    private fun apiRequest(url: String) =
        Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "w0y-updater")
            .build()

    /** «Позже»: окно при запуске эту версию больше не показывает, в настройках она остаётся. */
    fun defer(info: UpdateInfo) {
        prefs.edit().putString(KEY_DEFERRED, info.tag).apply()
        _deferred.value = info.tag
    }

    /** Качает APK во внутренний кэш; по окончании состояние становится [UpdateState.Ready]. */
    fun download(info: UpdateInfo, installAfter: Boolean = true) {
        if (_state.value is UpdateState.Downloading) return
        rememberNotes(info)
        job =
            scope.launch {
                _state.value = UpdateState.Downloading(info, 0L, info.apkSize)
                val result = runCatching { fetchApk(info) }.getOrElse { failure(it).let { s -> if (s is UpdateState.Failed) s.copy(info = info) else s } }
                _state.value = result
                // Человек нажал «Обновить» и ждёт: установщик открывается сам, без второго нажатия.
                if (installAfter && result is UpdateState.Ready) withContext(Dispatchers.Main) { install(result.file) }
            }
    }

    private suspend fun fetchApk(info: UpdateInfo): UpdateState =
        withContext(Dispatchers.IO) {
            if (!UpdateParsing.isTrustedDownload(info.apkUrl)) return@withContext UpdateState.Failed(UpdateState.Reason.BAD_FILE, info)
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, FILE)
            val expected = info.sha256 ?: info.sumsUrl?.let { runCatching { fetchSum(it, info.apkUrl.substringAfterLast('/')) }.getOrNull() }
            val digest = MessageDigest.getInstance("SHA-256")
            http.newCall(Request.Builder().url(info.apkUrl).header("User-Agent", "w0y-updater").build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext UpdateState.Failed(UpdateState.Reason.SERVER, info)
                val total = response.body.contentLength().takeIf { it > 0 } ?: info.apkSize
                var done = 0L
                var lastPublished = 0L
                try {
                    file.outputStream().use { out ->
                        response.body.byteStream().use { input ->
                            val buffer = ByteArray(BUFFER)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                                done += n
                                if (done - lastPublished > PUBLISH_STEP) {
                                    lastPublished = done
                                    _state.value = UpdateState.Downloading(info, done, total)
                                }
                            }
                        }
                    }
                } catch (e: java.io.IOException) {
                    file.delete()
                    throw e
                }
                if (total > 0 && done != total) {
                    file.delete()
                    return@withContext UpdateState.Failed(UpdateState.Reason.BAD_FILE, info)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected != null && !expected.equals(actual, ignoreCase = true)) {
                file.delete()
                return@withContext UpdateState.Failed(UpdateState.Reason.BAD_FILE, info)
            }
            UpdateState.Ready(info, file)
        }

    private fun fetchSum(url: String, fileName: String): String? {
        if (!UpdateParsing.isTrustedDownload(url)) return null
        http.newCall(Request.Builder().url(url).header("User-Agent", "w0y-updater").build()).execute().use { response ->
            if (!response.isSuccessful) return null
            return UpdateParsing.sumFor(response.body.string(), fileName)
        }
    }

    /** Результат попытки запустить установку. */
    enum class InstallResult { STARTED, NEEDS_PERMISSION, FAILED }

    /**
     * Отдаёт скачанный APK системному установщику. Если Android ещё не разрешил этому приложению
     * ставить пакеты, открывает именно его настройку; после разрешения достаточно нажать ещё раз.
     */
    fun install(file: File): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (runCatching { context.startActivity(settings) }.isSuccess) InstallResult.NEEDS_PERMISSION else InstallResult.FAILED
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val view =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(view)
            InstallResult.STARTED
        }.getOrElse {
            Timber.w(it, "Установщик не открылся")
            InstallResult.FAILED
        }
    }

    // ── Что нового ────────────────────────────────────────────────────────

    /**
     * После обновления: запомненный код сборки меньше текущего — значит, приложение обновили.
     * Описание берём у GitHub по тегу своей версии; нет связи — спросим при следующем запуске.
     * Свежая установка (кода ещё нет) окна не получает: рассказывать «что нового» тому, кто
     * ничего старого не видел, незачем.
     */
    private suspend fun detectVersionChange() {
        val seen = prefs.getInt(KEY_SEEN_CODE, 0)
        if (seen == 0) {
            prefs.edit().putInt(KEY_SEEN_CODE, versionCode).apply()
            return
        }
        if (seen >= versionCode) return
        val notes = notesOfCurrent()
        if (notes == null) return
        if (notes.isBlank()) {
            prefs.edit().putInt(KEY_SEEN_CODE, versionCode).apply()
            return
        }
        _whatsNew.value = WhatsNew(UpdateVersion.label(UpdateVersion.tagOf(versionName)), notes)
    }

    /** Описание своей версии: сначала то, что запомнили при загрузке, иначе — запрос по тегу. Null — не удалось. */
    private suspend fun notesOfCurrent(): String? {
        val tag = UpdateVersion.tagOf(versionName)
        if (prefs.getString(KEY_NOTES_TAG, null) == tag) prefs.getString(KEY_NOTES, null)?.let { return it }
        return runCatching {
            withContext(Dispatchers.IO) {
                http.newCall(apiRequest("https://api.github.com/repos/${UpdateParsing.REPO}/releases/tags/$tag")).execute().use {
                    when {
                        it.code == 404 -> ""
                        it.isSuccessful -> UpdateParsing.parseNotes(it.body.string())
                        else -> null
                    }
                }
            }
        }.getOrNull()
    }

    /** Закрыть окно «Что нового»: больше для этой версии не показывается. */
    fun dismissWhatsNew() {
        prefs.edit().putInt(KEY_SEEN_CODE, versionCode).apply()
        _whatsNew.value = null
    }

    /** Строка «Что нового» в настройках: показать описание своей версии ещё раз. Null — не вышло. */
    suspend fun loadWhatsNewNow(): Boolean {
        val notes = notesOfCurrent()?.takeIf { it.isNotBlank() } ?: return false
        _whatsNew.value = WhatsNew(UpdateVersion.label(UpdateVersion.tagOf(versionName)), notes)
        return true
    }

    /** Перед установкой запоминаем описание новой версии: после обновления оно покажется без сети. */
    fun rememberNotes(info: UpdateInfo) {
        prefs.edit().putString(KEY_NOTES_TAG, info.tag).putString(KEY_NOTES, info.notes).apply()
    }

    private companion object {
        const val DIR = "update"
        const val FILE = "w0y-update.apk"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_DEFERRED = "deferred_tag"
        const val KEY_SEEN_CODE = "seen_code"
        const val KEY_NOTES_TAG = "notes_tag"
        const val KEY_NOTES = "notes"
        const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000
        const val BUFFER = 64 * 1024
        const val PUBLISH_STEP = 256 * 1024
    }
}
