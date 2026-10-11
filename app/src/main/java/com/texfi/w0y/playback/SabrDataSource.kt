package com.texfi.w0y.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.metrolist.innertubex.sabr.ExperimentalSabrApi
import com.metrolist.innertubex.sabr.SabrAudioStream
import com.metrolist.innertubex.sabr.SabrBootstrap
import io.ktor.client.HttpClient
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Адрес SABR-потока для плеера: ключ потока в кэше извлечения ([com.texfi.w0y.data.YouTubeRepository]). */
fun sabrUri(streamKey: String): Uri = Uri.Builder().scheme(SABR_SCHEME).authority(streamKey).build()

const val SABR_SCHEME = "sabr"

/**
 * Звук, который YouTube отдаёт только по SABR — своему протоколу вместо
 * обычной ссылки на файл.
 *
 * Библиотека присылает куски того же файла, что лежал бы по ссылке, с
 * местом каждого куска в файле: сначала заголовок, потом сегменты по
 * порядку. Отсюда они выдаются плееру и загрузчику подряд, как обычный
 * файл, — дальше всё работает как с прямой ссылкой: кэш, загрузки, теги,
 * перекодирование в MP3.
 *
 * Чтение с середины (перемотка в ещё не скачанное) начинается с примерной
 * секунды чуть раньше нужного байта; если поток пришёл уже дальше этого
 * байта — заново с начала, чтобы ничего не потерять. Куски приходят на
 * скорости канала: сколько читать вперёд, решает сам плеер.
 */
@OptIn(UnstableApi::class, ExperimentalSabrApi::class)
class SabrDataSource(
    private val http: HttpClient,
    private val bootstrapOf: (String) -> SabrBootstrap?,
) : BaseDataSource(true) {
    private var spec: DataSpec? = null
    private var scope: CoroutineScope? = null
    private var queue = LinkedBlockingQueue<Any>(QUEUE)

    /** Кусок, который сейчас отдаётся, и сколько из него уже отдано. */
    private var chunk: ByteArray? = null
    private var chunkOffset = 0
    private var remaining = C.LENGTH_UNSET.toLong()
    private var ended = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val key = dataSpec.uri.authority ?: throw IOException("SABR без ключа потока")
        val bootstrap = bootstrapOf(key) ?: throw IOException("SABR: поток $key уже не в кэше")
        spec = dataSpec
        // Одна строка в системный лог: по ней видно, что трек пошёл по SABR.
        android.util.Log.i("w0y-sabr", "open $key с байта ${dataSpec.position}")
        start(bootstrap, dataSpec.position)
        val total = bootstrap.contentLengthBytes
        remaining =
            when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                total != null -> (total - dataSpec.position).coerceAtLeast(0)
                else -> C.LENGTH_UNSET.toLong()
            }
        transferStarted(dataSpec)
        return remaining
    }

    private fun start(bootstrap: SabrBootstrap, position: Long) {
        val q = LinkedBlockingQueue<Any>(QUEUE)
        queue = q
        chunk = null
        chunkOffset = 0
        ended = false
        val total = bootstrap.contentLengthBytes
        // С середины — с примерной секунды на запас раньше: байты ложатся
        // по времени неровно, а пропустить нужный кусок нельзя.
        val fromMs =
            if (position > 0 && total != null && total > 0) {
                (position.toDouble() / total * bootstrap.durationMs - SEEK_MARGIN_MS).toLong().coerceAtLeast(0)
            } else {
                0L
            }
        val job = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + job).also { s ->
            s.launch { produce(bootstrap, position, fromMs, q, job) }
        }
    }

    private suspend fun produce(
        bootstrap: SabrBootstrap,
        position: Long,
        fromMs: Long,
        q: LinkedBlockingQueue<Any>,
        job: Job,
    ) {
        try {
            // Следующий байт, который нужен плееру; общий для попыток, так что
            // повтор с начала пропускает уже отданное, а не дублирует его.
            val next = longArrayOf(position)
            if (!stream(bootstrap, fromMs, next, q)) {
                // С примерной секунды поток начался дальше нужного — с начала.
                check(stream(bootstrap, 0L, next, q)) { "разрыв в потоке на байте ${next[0]}" }
            }
            push(q, END)
        } catch (error: Throwable) {
            if (job.isActive) q.offer(error as? IOException ?: IOException("SABR: ${error.message}", error))
        }
    }

    /**
     * В очередь с ожиданием, но без вечной блокировки: если плеер закрыл
     * источник, корутина отменена, и производитель выходит, а не висит на
     * полной очереди.
     */
    private suspend fun push(q: LinkedBlockingQueue<Any>, item: Any) {
        while (!q.offer(item, PUSH_WAIT_MS, TimeUnit.MILLISECONDS)) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
        }
    }

    /** false — первый нужный кусок оказался дальше [next]: с этой секунды начинать нельзя. */
    private suspend fun stream(
        bootstrap: SabrBootstrap,
        fromMs: Long,
        next: LongArray,
        q: LinkedBlockingQueue<Any>,
    ): Boolean {
        try {
            SabrAudioStream(http, bootstrap, initialPlayerTimeMs = fromMs).chunks().collect { piece ->
                val from = piece.startRange
                val to = from + piece.data.size
                when {
                    // Уже отдано или раньше нужного места.
                    to <= next[0] -> Unit
                    from > next[0] -> throw TooFar()
                    else -> {
                        val skip = (next[0] - from).toInt()
                        push(q, if (skip == 0) piece.data else piece.data.copyOfRange(skip, piece.data.size))
                        next[0] = to
                    }
                }
            }
            return true
        } catch (tooFar: TooFar) {
            return false
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L || ended) return C.RESULT_END_OF_INPUT
        var current = chunk
        while (current == null || chunkOffset >= current.size) {
            val item =
                try {
                    queue.poll(READ_TIMEOUT_S, TimeUnit.SECONDS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException()
                } ?: throw IOException("SABR: поток молчит дольше $READ_TIMEOUT_S с")
            when (item) {
                END -> {
                    ended = true
                    return C.RESULT_END_OF_INPUT
                }
                is IOException -> throw item
                is ByteArray -> {
                    current = item
                    chunk = item
                    chunkOffset = 0
                }
            }
        }
        var n = minOf(length, current.size - chunkOffset)
        if (remaining != C.LENGTH_UNSET.toLong()) n = minOf(n.toLong(), remaining).toInt()
        System.arraycopy(current, chunkOffset, buffer, offset, n)
        chunkOffset += n
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = spec?.uri

    override fun close() {
        scope?.cancel()
        scope = null
        queue.clear()
        chunk = null
        if (spec != null) {
            spec = null
            transferEnded()
        }
    }

    private class TooFar : RuntimeException()

    class Factory(
        private val http: HttpClient,
        private val bootstrapOf: (String) -> SabrBootstrap?,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SabrDataSource(http, bootstrapOf)
    }

    private companion object {
        val END = Any()

        /** Кусков в очереди наперёд: несколько секунд звука, без лишней памяти. */
        const val QUEUE = 64
        const val SEEK_MARGIN_MS = 4_000L
        const val READ_TIMEOUT_S = 30L
        const val PUSH_WAIT_MS = 200L
    }
}

/**
 * Обычная ссылка — по HTTP, SABR — через [SabrDataSource]. Выбор при каждом
 * открытии: какой поток даст YouTube, известно только после извлечения.
 */
@OptIn(UnstableApi::class)
class SchemeSwitchDataSource(
    private val http: DataSource,
    private val sabr: DataSource,
) : DataSource {
    private var current: DataSource = http

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        http.addTransferListener(transferListener)
        sabr.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        current = if (dataSpec.uri.scheme == SABR_SCHEME) sabr else http
        return current.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current.read(buffer, offset, length)

    override fun getUri(): Uri? = current.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current.responseHeaders

    override fun close() = current.close()
}

/** Схема `file` — прямо с диска, всё остальное — в [rest] (кэши и сеть). */
class FileFirstDataSource(
    private val file: DataSource,
    private val rest: DataSource,
) : DataSource {
    private var current: DataSource = rest

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        file.addTransferListener(transferListener)
        rest.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        current = if (dataSpec.uri.scheme == "file") file else rest
        return current.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current.read(buffer, offset, length)

    override fun getUri(): Uri? = current.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current.responseHeaders

    override fun close() = current.close()
}
