package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.desktop.i18n.S
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Почему трек не скачался: ключ строки и подробность для журнала. */
class DownloadFailure(val key: String, detail: String? = null) : Exception(detail ?: key)

/**
 * Скачивание в обычную папку («Музыка/w0y music» или выбранную): один файл на трек,
 * формат тот, что отдал YouTube (m4a или webm/opus), без перекодирования — или MP3
 * через ffmpeg, если так выбрано. Скачанное играет с диска и открывается любым плеером.
 * Все настройки скачивания из «Настроек» действуют здесь: сколько загрузок разом,
 * ограничение скорости, повторы, предел места, уведомление об окончании.
 */
class Downloads(private val scope: CoroutineScope, private val yt: Yt, private val app: AppState) {
    /** Идущие загрузки: id → доля 0..1 (−1 — ждёт очереди). */
    var progress by mutableStateOf<Map<String, Float>>(emptyMap())
        private set
    var failed by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Причина по каждому не скачавшемуся треку — готовая строка на языке интерфейса. */
    var failReason by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var notice by mutableStateOf<String?>(null)

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
    private val pending = ArrayDeque<SongItem>()
    private var active = 0
    private var finishedThisRun = 0
    private var lastTitle = ""

    /** ffmpeg нужен только для MP3: без него переключатель скрывается. */
    val canMp3: Boolean by lazy { (System.getenv("PATH") ?: "").split(':').any { File(it, "ffmpeg").canExecute() } }

    val defaultFolder: File by lazy {
        val xdg =
            runCatching {
                File(System.getProperty("user.home"), ".config/user-dirs.dirs").readLines()
                    .firstOrNull { it.startsWith("XDG_MUSIC_DIR=") }
                    ?.substringAfter('=')?.trim('"')?.replace("\$HOME", System.getProperty("user.home"))
            }.getOrNull()
        File(xdg ?: "${System.getProperty("user.home")}/Music", "w0y music")
    }

    val folder: File get() = app.st.downloadFolder.takeIf { it.isNotBlank() }?.let(::File) ?: defaultFolder

    /** Сколько места занято скачанным, МБ. */
    fun usedMb(): Long = app.lib.downloads.values.sumOf { runCatching { File(it.path).length() }.getOrDefault(0L) } / 1024 / 1024

    fun openFolder() {
        folder.mkdirs()
        runCatching { ProcessBuilder("xdg-open", folder.absolutePath).start() }
    }

    fun isDone(id: String) = app.downloadedPath(id) != null

    fun download(song: SongItem) {
        if (isDone(song.id) || song.id in progress) return
        val limit = app.st.downloadStorageLimitMb
        if (limit > 0 && usedMb() >= limit) {
            notice = S("dl_storage_full", limit)
            failed = failed + song.id
            failReason = failReason + (song.id to S("dl_storage_full", limit))
            return
        }
        progress = progress + (song.id to -1f)
        failed = failed - song.id
        pending.addLast(song)
        pump()
    }

    private fun pump() {
        while (active < app.st.downloadParallel.coerceIn(1, 6) && pending.isNotEmpty()) {
            val song = pending.removeFirst()
            active++
            scope.launch(Dispatchers.IO) {
                try {
                    val file = withRetries { fetch(song) }
                    app.markDownloaded(song, file.absolutePath)
                    finishedThisRun++
                    lastTitle = song.title
                } catch (e: DownloadFailure) {
                    fail(song, S(e.key))
                } catch (e: Exception) {
                    fail(song, S("dl_fail_unknown"))
                }
                withContext(Dispatchers.Main) {
                    progress = progress - song.id
                    active--
                    pump()
                    if (active == 0 && pending.isEmpty()) finishedQueue()
                }
            }
        }
    }

    private fun fail(song: SongItem, reason: String) {
        failed = failed + song.id
        failReason = failReason + (song.id to reason)
    }

    private suspend fun <T> withRetries(block: suspend () -> T): T {
        val tries = app.st.downloadRetries.coerceIn(0, 10) + 1
        var last: Exception? = null
        repeat(tries) { attempt ->
            try {
                return block()
            } catch (e: DownloadFailure) {
                // Причины, которые повтором не лечатся.
                if (e.key in setOf("dl_fail_restricted", "dl_fail_storage", "dl_fail_no_audio")) throw e
                last = e
            } catch (e: Exception) {
                last = e
            }
            delay(1500L * (attempt + 1))
        }
        throw last ?: DownloadFailure("dl_fail_unknown")
    }

    private fun finishedQueue() {
        val n = finishedThisRun
        finishedThisRun = 0
        if (n == 0 || !app.st.downloadNotifications) return
        runCatching {
            ProcessBuilder("notify-send", "-a", "w0y", "-i", "w0y", S("downloads_title"), if (n == 1) S("dl_done_notification", lastTitle) else S("dl_done_notification_many", n))
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        }
    }

    fun downloadAll(songs: List<SongItem>) {
        songs.forEach(::download)
        notice = S("download_queued_many", songs.size)
    }

    fun remove(id: String) {
        app.downloadedPath(id)?.let { runCatching { File(it).delete() } }
        app.unmarkDownloaded(id)
    }

    /** Стереть все загрузки и освободить место. */
    fun clearAll() {
        app.lib.downloads.keys.toList().forEach(::remove)
        notice = S("dl_cleared")
    }

    /** Найти потерянные и оборванные файлы и скачать их заново; сколько — число. */
    fun verify(): Int {
        val broken = app.lib.downloads.values.filter { runCatching { File(it.path).length() }.getOrDefault(0L) <= 0L }
        broken.forEach { remove(it.song.id) }
        broken.forEach { download(it.song.toItem()) }
        notice = if (broken.isEmpty()) S("dl_verify_ok") else S("dl_verify_fixing", broken.size)
        return broken.size
    }

    /** Автоскачивание выбранных плейлистов: всё, что в них есть, включая добавленное позже. */
    fun syncAutoPlaylists() {
        val wanted = app.st.autoDownloadPlaylists
        if (wanted.isEmpty()) return
        app.lib.playlists.filter { it.id in wanted }.flatMap { it.songs }.distinctBy { it.id }.forEach { download(it.toItem()) }
    }

    private fun sanitize(song: SongItem) = "${song.artist} - ${song.title}".replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120)

    private suspend fun fetch(song: SongItem): File = withContext(Dispatchers.IO) {
        val stream =
            try {
                yt.stream(song.id, app.st.downloadQuality)
            } catch (e: Exception) {
                throw DownloadFailure("dl_fail_no_stream", e.message)
            }
        val mime = stream.mimeType.orEmpty()
        val ext = if (mime.contains("webm")) "webm" else if (mime.contains("mp4")) "m4a" else "audio"
        folder.mkdirs()
        if (!folder.canWrite()) throw DownloadFailure("dl_fail_storage")
        val name = sanitize(song)
        val target = File(folder, "$name.$ext")
        val part = File(folder, "$name.$ext.part")
        val total = stream.contentLengthBytes
        val chunk = if (stream.useRangeChunks) stream.rangeChunkSizeBytes.coerceAtLeast(256 * 1024L) else 4L * 1024 * 1024
        var done = 0L
        part.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val end = done + chunk - 1
                val request =
                    HttpRequest.newBuilder(URI.create(stream.audioUrl)).apply {
                        stream.headers.forEach { (k, v) -> if (!k.equals("range", true) && !k.equals("host", true)) header(k, v) }
                        header("Range", "bytes=$done-$end")
                        timeout(Duration.ofSeconds(30))
                    }.build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
                val code = response.statusCode()
                if (code == 403 || code == 410) throw DownloadFailure("dl_fail_expired")
                if (code !in listOf(200, 206)) throw DownloadFailure("dl_fail_server", "HTTP $code")
                val range = response.headers().firstValue("Content-Range").orElse(null)
                val size = range?.substringAfter('/')?.toLongOrNull() ?: total
                var got = 0L
                response.body().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        got += n
                        throttle(n)
                    }
                }
                done += got
                if (size != null && size > 0) progress = progress + (song.id to (done.toFloat() / size).coerceIn(0f, 1f))
                // 200 без Range — сервер отдал всё сразу.
                if (code == 200 || got == 0L || (size != null && done >= size)) break
            }
        }
        if (done == 0L || (total != null && total > 0 && done < total)) {
            runCatching { part.delete() }
            throw DownloadFailure("dl_fail_incomplete")
        }
        part.renameTo(target)
        if (app.st.downloadAsMp3 && canMp3) {
            val mp3 = File(folder, "$name.mp3")
            if (toMp3(target, mp3, song)) {
                target.delete()
                return@withContext mp3
            }
        }
        target
    }

    // ---- ограничение скорости: одно на все загрузки, поровну между идущими

    private var windowStart = System.nanoTime()
    private var windowBytes = 0L

    @Synchronized
    private fun throttle(bytes: Int) {
        val limitKb = app.st.downloadSpeedLimitKb
        if (limitKb <= 0) return
        windowBytes += bytes
        val elapsed = (System.nanoTime() - windowStart) / 1e9
        val allowed = limitKb * 1024.0 * elapsed
        if (windowBytes > allowed) Thread.sleep(((windowBytes - allowed) / (limitKb * 1024.0) * 1000).toLong().coerceIn(1, 500))
        if (elapsed > 2) {
            windowStart = System.nanoTime()
            windowBytes = 0
        }
    }

    /** MP3 320 кбит/с с названием, исполнителем и альбомом. */
    private fun toMp3(source: File, target: File, song: SongItem): Boolean =
        runCatching {
            val command = mutableListOf(
                "ffmpeg", "-y", "-loglevel", "error", "-i", source.absolutePath, "-vn",
                "-codec:a", "libmp3lame", "-b:a", "320k",
                "-metadata", "title=${song.title}", "-metadata", "artist=${song.artist}",
            )
            song.album?.let { command += listOf("-metadata", "album=$it") }
            command += target.absolutePath
            val p = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            p.waitFor() == 0 && target.length() > 0
        }.getOrDefault(false)
}
