package com.texfi.w0y.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// Чистая часть обновлений без Android: её же подключает ПК-версия (desktop/build.gradle.kts, sharedSources).

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
    private const val SUMS_NAME = "SHA256SUMS.txt"

    /** Адрес файла из релиза обязан вести на наш репозиторий: подмена ответа не должна увести на чужой сервер. */
    fun isTrustedDownload(url: String): Boolean =
        url.startsWith("https://github.com/$REPO/releases/download/")

    /**
     * [assetSuffixes] — какой файл релиза нужен этой платформе: «.apk» на телефоне, «.exe» или
     * «.appimage» на ПК. Берётся первый подходящий по порядку суффиксов.
     */
    fun parseRelease(body: String, assetSuffixes: List<String> = listOf(".apk")): UpdateInfo? =
        runCatching {
            val root = Json.parseToJsonElement(body).jsonObject
            if (root["draft"]?.jsonPrimitive?.contentOrNull == "true") return null
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
            val assets = (root["assets"] as? JsonArray).orEmpty().map { it.jsonObject }
            val apk =
                assetSuffixes.firstNotNullOfOrNull { suffix -> assets.firstOrNull { it.name().endsWith(suffix, ignoreCase = true) && trusted(it) } }
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
