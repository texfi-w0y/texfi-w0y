package com.texfi.w0y.desktop

import com.texfi.w0y.data.SongItem
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Свои файлы (mp3, flac, wav…): копируются в папку приложения и дальше живут как обычные треки —
 * очередь, плейлисты, «быстрый набор», рекомендации. Как на телефоне, идентификатор `local:<имя файла>`
 * говорит сам за себя: по нему находится файл на диске, а запросов в YouTube для такого трека нет.
 */
object LocalFiles {
    const val PREFIX = "local:"

    val extensions = setOf("mp3", "flac", "wav", "ogg", "opus", "m4a", "aac", "wma", "mka", "webm", "mp4", "aiff", "aif", "ape", "wv")

    fun isLocal(id: String?): Boolean = id?.startsWith(PREFIX) == true

    fun dir(): File = Platform.dataDir().resolve("local").toFile().apply { mkdirs() }

    fun file(id: String): File = File(dir(), id.removePrefix(PREFIX))

    fun keyOf(id: String): String = id.removePrefix(PREFIX).substringBeforeLast('.')

    /** «3:47» из миллисекунд. */
    fun durationText(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Название из имени файла: без расширения и с пробелами вместо подчёркиваний. */
    fun titleFromName(name: String): String = name.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { name }

    fun isAudio(file: File) = file.isFile && file.extension.lowercase() in extensions

    fun uri(file: File): String = file.toURI().toString()

    /** Копирует файл в папку приложения и заводит трек. Бросает исключение, если файл не прочитался. */
    fun import(src: File): SongItem {
        require(src.isFile && src.canRead()) { "нет доступа к файлу" }
        val ext = src.extension.lowercase().takeIf { it.length in 2..5 } ?: "mp3"
        val key = UUID.randomUUID().toString().replace("-", "").take(12)
        val id = "$PREFIX$key.$ext"
        val target = file(id)
        Files.copy(src.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val tags = AudioTags.read(target)
        var cover: String? = null
        tags.cover?.let { bytes -> cover = writeCover(key, bytes)?.let(::uri) }
        return SongItem(
            id = id,
            title = tags.title?.takeIf { it.isNotBlank() } ?: titleFromName(src.name),
            artist = tags.artist?.takeIf { it.isNotBlank() }.orEmpty(),
            album = tags.album?.takeIf { it.isNotBlank() },
            durationText = tags.durationMs.takeIf { it > 0 }?.let(::durationText),
            thumbnailUrl = cover,
        )
    }

    /** Картинка обложки трека. Имя каждый раз новое: иначе кэш картинок показал бы старую обложку. */
    fun writeCover(key: String, bytes: ByteArray): File? =
        runCatching {
            dir().listFiles { f -> coverOf(key, f) }?.forEach { it.delete() }
            val ext = when {
                bytes.size > 3 && bytes[0] == 0x89.toByte() -> "png"
                else -> "jpg"
            }
            File(dir(), "$key.${System.currentTimeMillis()}.$ext").also { it.writeBytes(bytes) }
        }.getOrNull()

    /** Выбранную пользователем картинку приводим к разумному размеру: полноразмерные фото тормозили бы список. */
    fun importCover(key: String, picked: File): File? =
        runCatching {
            val image = ImageIO.read(picked) ?: return null
            val scale = minOf(1.0, 900.0 / maxOf(image.width, image.height))
            val w = (image.width * scale).toInt().coerceAtLeast(1)
            val h = (image.height * scale).toInt().coerceAtLeast(1)
            val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
            out.createGraphics().apply {
                setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                color = java.awt.Color.BLACK
                fillRect(0, 0, w, h)
                drawImage(image, 0, 0, w, h, null)
                dispose()
            }
            dir().listFiles { f -> coverOf(key, f) }?.forEach { it.delete() }
            File(dir(), "$key.${System.currentTimeMillis()}.jpg").also { ImageIO.write(out, "jpg", it) }
        }.getOrNull()

    fun clearCover(key: String) {
        dir().listFiles { f -> coverOf(key, f) }?.forEach { it.delete() }
    }

    /** Файл обложки этого трека: `<ключ>.<число>.jpg|png`. */
    private fun coverOf(key: String, f: File) = Regex("^${Regex.escape(key)}\\.\\d+\\.(jpg|png)$").matches(f.name)

    /** Стирает всё, что относится к треку: звук и обложку. */
    fun delete(id: String) {
        val key = keyOf(id)
        dir().listFiles { f -> f.name.startsWith("$key.") }?.forEach { it.delete() }
    }
}

/** Папка для своих обложек плейлистов. */
object PlaylistCovers {
    fun dir(): File = Platform.dataDir().resolve("covers").toFile().apply { mkdirs() }

    /** Копирует выбранную картинку, уменьшив до 900 px; новое имя на каждую правку. */
    fun import(playlistId: String, picked: File): String? =
        runCatching {
            val image = ImageIO.read(picked) ?: return null
            val side = minOf(image.width, image.height)
            // Квадрат из середины: на плитках плейлистов обложка квадратная.
            val square = image.getSubimage((image.width - side) / 2, (image.height - side) / 2, side, side)
            val size = minOf(side, 900)
            val out = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB)
            out.createGraphics().apply {
                setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                drawImage(square, 0, 0, size, size, null)
                dispose()
            }
            remove(playlistId)
            File(dir(), "$playlistId.${System.currentTimeMillis()}.jpg").also { ImageIO.write(out, "jpg", it) }.toURI().toString()
        }.getOrNull()

    fun remove(playlistId: String) {
        dir().listFiles { f -> f.name.startsWith("$playlistId.") }?.forEach { it.delete() }
    }
}
