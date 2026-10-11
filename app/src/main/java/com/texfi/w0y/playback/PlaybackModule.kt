package com.texfi.w0y.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.YouTubeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
@OptIn(UnstableApi::class)
object PlaybackModule {
    @Provides
    @Singleton
    fun databaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    /**
     * Кэш прослушанного: вытесняется по мере заполнения, лимит берётся
     * из настроек при первом обращении.
     */
    @Provides
    @Singleton
    @Named("stream")
    fun streamCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        settings: SettingsRepository,
    ): SimpleCache {
        // Лимит читается один раз при создании кэша: у SimpleCache нельзя
        // поменять вытеснитель на живом объекте, поэтому новая величина
        // вступает в силу после перезапуска — так и написано в настройках.
        val limitMb = runBlocking { settings.settings.first().cacheLimitMb }
        return SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(limitMb.toLong() * 1024 * 1024),
            databaseProvider,
        )
    }

    /**
     * Скачанное — отдельное хранилище без вытеснения и во внутренней папке
     * приложения: иначе система однажды вычистит кэш и «скачанная» музыка
     * молча исчезнет.
     */
    @Provides
    @Singleton
    @Named("download")
    fun downloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache =
        SimpleCache(
            File(context.filesDir, "downloads"),
            NoOpCacheEvictor(),
            databaseProvider,
        )

    /**
     * Сеть плюс подстановка настоящей ссылки вместо схемы w0y://.
     *
     * Сам файл читается ограниченными диапазонами ([RangedHttpDataSource]):
     * одним открытым запросом сервер отдаёт его со скоростью
     * воспроизведения, кусками — на скорости канала. Для плеера первый
     * кусок маленький (раньше приходит первый байт), и вперёд качается
     * один кусок: больше на воспроизведение не нужно.
     */
    @Provides
    @Singleton
    @Named("resolving")
    fun resolvingFactory(
        okHttpClient: OkHttpClient,
        httpClient: io.ktor.client.HttpClient,
        repository: YouTubeRepository,
        fallback: FallbackAudio,
        settings: SettingsRepository,
    ): DataSource.Factory =
        ResolvingDataSource.Factory(
            DataSource.Factory {
                FallbackDataSource(
                    withSabr(
                        RangedHttpDataSource.Factory(
                            calls = okHttpClient,
                            plan = { PLAYER_PLAN },
                            hint = repository::streamHint,
                            refresh = { id -> repository.refreshBlocking(id, null) },
                        ),
                        httpClient,
                        repository,
                    ),
                    fallback,
                    settings,
                )
            },
            StreamResolver(repository, fallback, settings),
        )

    /**
     * То же для загрузок: своё качество, крупнее параллельность, повторы и
     * общий лимит скорости из настроек. Настройки читаются из снимка,
     * который держит [DownloadSettingsHolder], — блокировать поток загрузчика
     * чтением DataStore на каждый кусок нельзя.
     */
    @Provides
    @Singleton
    @Named("downloadResolving")
    fun downloadResolvingFactory(
        okHttpClient: OkHttpClient,
        httpClient: io.ktor.client.HttpClient,
        repository: YouTubeRepository,
        fallback: FallbackAudio,
        settings: SettingsRepository,
        holder: DownloadSettingsHolder,
    ): DataSource.Factory {
        val limiter = RateLimiter { holder.current.downloadSpeedLimitKb * 1024L }
        val quality = { holder.downloadQuality(repository.onWifi()) }
        return ResolvingDataSource.Factory(
            DataSource.Factory {
                FallbackDataSource(
                    withSabr(
                        RangedHttpDataSource.Factory(
                            calls = okHttpClient,
                            plan = { RangePlan(RangedHttpDataSource.CHUNK, RangedHttpDataSource.CHUNK, 3, holder.current.downloadRetries) },
                            hint = { id -> repository.streamHint("$id#${quality().name}") ?: repository.streamHint(id) },
                            refresh = { id -> repository.refreshBlocking(id, quality()) },
                            limiter = limiter,
                        ),
                        httpClient,
                        repository,
                    ),
                    fallback,
                    settings,
                )
            },
            StreamResolver(repository, fallback, settings, downloadQuality = quality),
        )
    }

    /** HTTP-источник, который умеет и SABR: YouTube отдаёт его вместо ссылки на части треков. */
    private fun withSabr(
        http: DataSource.Factory,
        httpClient: io.ktor.client.HttpClient,
        repository: YouTubeRepository,
    ): DataSource.Factory {
        val sabr = SabrDataSource.Factory(httpClient, repository::sabrBootstrap)
        return DataSource.Factory { SchemeSwitchDataSource(http.createDataSource(), sabr.createDataSource()) }
    }

    /** Первый кусок 256 КБ: несколько секунд звука, приходит быстро. */
    private val PLAYER_PLAN =
        RangePlan(firstChunk = 256L * 1024, chunk = RangedHttpDataSource.CHUNK, parallel = 1, retries = 2)

    /**
     * Цепочка для воспроизведения: сначала скачанное, потом кэш, и только
     * потом сеть. Скачанный трек играет офлайн и не перекачивается.
     */
    @Provides
    @Singleton
    @Named("player")
    fun playerFactory(
        @Named("download") downloadCache: SimpleCache,
        @Named("stream") streamCache: SimpleCache,
        @Named("resolving") resolving: DataSource.Factory,
    ): DataSource.Factory {
        val cached =
            CacheDataSource
                .Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(
                    CacheDataSource
                        .Factory()
                        .setCache(streamCache)
                        .setUpstreamDataSourceFactory(resolving)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
                ).setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Свои файлы читаются напрямую с диска, мимо кэшей.
        return DataSource.Factory { FileFirstDataSource(androidx.media3.datasource.FileDataSource(), cached.createDataSource()) }
    }

    @Provides
    @Singleton
    fun downloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        @Named("download") downloadCache: SimpleCache,
        @Named("downloadResolving") resolving: DataSource.Factory,
    ): DownloadManager =
        DownloadManager(
            context,
            DefaultDownloadIndex(databaseProvider),
            DefaultDownloaderFactory(
                CacheDataSource
                    .Factory()
                    .setCache(downloadCache)
                    .setUpstreamDataSourceFactory(resolving),
                Executors.newFixedThreadPool(4),
            ),
        ).apply {
            // Сколько треков разом — из настроек, это значение лишь стартовое.
            maxParallelDownloads = 2
            // Повторы самого менеджера — поверх повторов кусков: после них
            // загрузка продолжается с того байта, на котором оборвалась
            // (уже скачанное лежит в хранилище и не перекачивается).
            minRetryCount = 3
        }
}
