package com.texfi.w0y.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Подготовка следующих треков, пока играет текущий.
 *
 * Два уровня:
 *  - адреса потоков для нескольких следующих — дёшево, без байтов звука,
 *    и переход при быстром пролистывании не ждёт извлечения;
 *  - начало ближайшего следующего — на диск, в кэш прослушанного. Когда
 *    очередь до него дойдёт, первые секунды читаются с диска, и звук
 *    начинается без сети.
 *
 * Играющий трек не трогаем: его запись в кэш держит сам плеер, и вторая
 * запись того же ключа ушла бы в сеть впустую. Скачанные — тоже: они и так
 * на диске целиком.
 */
@Singleton
@OptIn(UnstableApi::class)
class ReadAhead @Inject constructor(
    private val repository: YouTubeRepository,
    @param:Named("stream") private val streamCache: SimpleCache,
    @param:Named("download") private val downloadCache: SimpleCache,
    @param:Named("resolving") private val resolving: DataSource.Factory,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var writer: CacheWriter? = null
    private var lastPlan: List<String> = emptyList()

    /**
     * [upcoming] — следующие треки по порядку. Повторный вызов с тем же
     * списком ничего не делает; с другим — отменяет прежнюю подготовку.
     */
    fun prepare(upcoming: List<SongItem>, onWifi: Boolean) {
        // Свои файлы лежат на диске: готовить из сети нечего.
        val upcoming = upcoming.filterNot { com.texfi.w0y.data.LocalMusic.isLocal(it.id) }
        val ids = upcoming.take(URL_LOOKAHEAD).map { it.id }
        if (ids == lastPlan) return
        lastPlan = ids
        job?.cancel()
        writer?.cancel()
        val next = upcoming.firstOrNull() ?: return
        job =
            scope.launch {
                // Небольшая пауза: на старте трека сеть нужнее ему самому.
                delay(SETTLE_MS)
                if (!downloaded(next.id)) {
                    val bytes = if (onWifi) WIFI_HEAD else MOBILE_HEAD
                    runCatching { warmHead(next, bytes) }
                        .onFailure { if (it !is InterruptedException) Timber.d(it, "Начало %s не подготовилось", next.id) }
                }
                upcoming.drop(1).take(URL_LOOKAHEAD - 1).forEach { song ->
                    if (!downloaded(song.id) && !repository.hasFreshStream(song.id)) {
                        runCatching { repository.stream(song.id) }
                        delay(URL_GAP_MS)
                    }
                }
            }
    }

    private fun downloaded(id: String): Boolean = downloadCache.getCachedSpans(id).isNotEmpty()

    private fun warmHead(song: SongItem, bytes: Long) {
        if (streamCache.isCached(song.id, 0, bytes)) return
        val source =
            CacheDataSource
                .Factory()
                .setCache(streamCache)
                .setUpstreamDataSourceFactory(resolving)
                .createDataSource()
        val spec =
            DataSpec
                .Builder()
                .setUri(w0yUri(song))
                .setKey(song.id)
                .setPosition(0)
                .setLength(bytes)
                .build()
        val cacheWriter = CacheWriter(source, spec, null, null)
        writer = cacheWriter
        cacheWriter.cache()
    }

    fun cancel() {
        lastPlan = emptyList()
        job?.cancel()
        writer?.cancel()
    }

    private companion object {
        /** Сколько следующих треков готовим адресами. */
        const val URL_LOOKAHEAD = 3
        const val SETTLE_MS = 1_500L
        const val URL_GAP_MS = 300L

        /** Около полуминуты звука на Wi-Fi и около десяти секунд на мобильной. */
        const val WIFI_HEAD = 1024L * 1024
        const val MOBILE_HEAD = 384L * 1024
    }
}
