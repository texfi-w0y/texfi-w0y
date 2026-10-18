package com.texfi.w0y.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Резервная копия одним файлом: настройки, лайки, плейлисты, история, статистика, профиль вкуса.
 * Сессия (cookie) и данные аккаунта в копию не попадают, чтобы файл можно было безопасно хранить и переносить.
 * Формат один на Windows и Linux. С Android он не совместим: там другая база.
 */
object Backup {
    const val FORMAT = "w0y-backup"
    const val VERSION = 1

    @Serializable
    private data class File(val format: String, val version: Int, val platform: String, val app: String, val createdAt: Long, val library: Library)

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun export(lib: Library, now: Long = System.currentTimeMillis()): String =
        json.encodeToString(
            File.serializer(),
            File(FORMAT, VERSION, Platform.os.name.lowercase(), APP_VERSION, now, lib.copy(cookie = null, accountName = null, accountAvatar = null)),
        )

    /** Разбор копии; чужой формат или более новая версия — ошибка с понятной причиной. */
    fun parse(text: String): Result<Library> =
        runCatching {
            val file = json.decodeFromString(File.serializer(), text)
            require(file.format == FORMAT) { "не файл копии w0y" }
            require(file.version <= VERSION) { "копия из более новой версии приложения" }
            file.library
        }

    /** Что остаётся от текущей библиотеки при восстановлении: вход в аккаунт не сбрасывается. */
    fun restore(current: Library, imported: Library): Library =
        imported.copy(cookie = current.cookie, accountName = current.accountName, accountAvatar = current.accountAvatar)
}
