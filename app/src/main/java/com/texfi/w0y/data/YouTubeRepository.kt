package com.texfi.w0y.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.call.body
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Доступ к YouTube Music: поиск и получение потока.
 *
 * Ссылки на поток кэшируются с оглядкой на их собственный срок жизни
 * (`expiresAt`): повторное извлечение — это лишние сетевые запросы и
 * расшифровка cipher, то есть ровно та задержка между нажатием и звуком,
 * ради устранения которой всё и затевалось.
 */
@Singleton
class YouTubeRepository @Inject constructor(
    private val innerTube: InnerTube,
    private val extractor: InnerTubeExtractor,
    private val settings: SettingsRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    /**
     * Перечитать язык запросов после его смены в настройках.
     *
     * InnerTube живёт синглтоном на весь процесс, а экран настроек только
     * пересоздаёт активити — без этого ленты YouTube оставались бы на
     * старом языке до следующего запуска приложения.
     */
    fun applyAppLocale() {
        innerTube.locale = YouTubeLocaleResolver.forApp(context)
    }

    private val streams = ConcurrentHashMap<String, ExtractedStream>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Порядок, в котором ссылки попадали в кэш.
     *
     * Кэш держит их с оглядкой на срок жизни, но пока он не истёк, запись
     * не уходит сама. За долгий вечер это сотни ссылок в памяти процесса,
     * который обещает быть быстрым, — поэтому старое вытесняется.
     */
    private val streamOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun searchSongs(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .search(
                    client = YouTubeClient.WEB_REMIX,
                    query = query,
                    params = SONGS_FILTER,
                ).body<JsonObject>()
        YtJson.songs(response)
    }

    /**
     * Видео из YouTube Music: клипы, лайвы, переделки и всё, что залито
     * роликом, а не треком. Играют они так же — только звуком.
     */
    suspend fun searchVideos(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .search(
                    client = YouTubeClient.WEB_REMIX,
                    query = query,
                    params = VIDEOS_FILTER,
                ).body<JsonObject>()
        YtJson.songs(response)
    }

    /**
     * Готовая переделка трека — slowed или sped up, залитая кем-то.
     *
     * Ищем и среди треков, и среди видео: официальные slowed-релизы лежат
     * треками, а большинство фанатских — роликами. Что из найденного
     * действительно та же песня, решает [EditMatch], а не первая строка
     * выдачи: поиск охотно отдаёт соседние песни того же артиста.
     */
    suspend fun findEdit(song: SongItem, kind: EditKind): SongItem? = coroutineScope {
        val query = "${song.title} ${song.artist.substringBefore(',')} ${kind.query}"
        val songs = async { runCatching { searchSongs(query) }.getOrDefault(emptyList()) }
        val videos = async { runCatching { searchVideos(query) }.getOrDefault(emptyList()) }
        EditMatch.pick(song, kind, songs.await() + videos.await())
    }

    /**
     * Главная страница YouTube Music: готовые ленты рекомендаций.
     *
     * С аккаунтом они личные, без него — общие по региону. Своего
     * «алгоритма» мы не выдумываем: у YouTube он есть, и он лучше.
     */
    suspend fun home(): List<Shelf> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_home", setLogin = true)
                .body<JsonObject>()
        YtJson.shelves(response)
    }

    /**
     * Радио по треку: чем YouTube продолжил бы этот трек.
     *
     * Идентификатор станции — `RDAMVM` плюс видео; это та же станция,
     * которую их приложение заводит по кнопке «начать радио».
     */
    suspend fun radio(videoId: String): List<SongItem> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .next(
                    client = YouTubeClient.WEB_REMIX,
                    videoId = videoId,
                    playlistId = "RDAMVM$videoId",
                    playlistSetVideoId = null,
                    index = null,
                    params = null,
                    continuation = null,
                ).body<JsonObject>()
        YtJson.queueSongs(response).filterNot { it.id == videoId }
    }

    /**
     * Исполнители и альбом трека, у которого их нет: при первом запуске из
     * поиска или ссылки YouTube отдаёт только название. Берём ту же строку
     * из панели «следующих» — там ссылки на каналы и альбом есть всегда.
     */
    suspend fun songDetails(videoId: String): SongItem? = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .next(
                    client = YouTubeClient.WEB_REMIX,
                    videoId = videoId,
                    playlistId = null,
                    playlistSetVideoId = null,
                    index = null,
                    params = null,
                    continuation = null,
                ).body<JsonObject>()
        YtJson.queueSongs(response).firstOrNull { it.id == videoId }
    }

    /**
     * Чистая версия трека, если она вообще выложена.
     *
     * Вырезать слова из записи нельзя, но у половины таких треков есть
     * официальная clean-версия — её и ищем по названию и исполнителю.
     * Сверяем название, иначе поиск с готовностью подсунет чужой кавер.
     */
    suspend fun cleanVersion(song: SongItem): SongItem? {
        val candidates =
            runCatching { searchSongs("${song.title} ${song.artist} clean") }.getOrDefault(emptyList())
        return CleanMatch.pick(song, candidates)
    }

    /** Альбомы в выдаче поиска. */
    suspend fun searchAlbums(query: String): List<PlaylistCard> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .search(client = YouTubeClient.WEB_REMIX, query = query, params = ALBUMS_FILTER)
                .body<JsonObject>()
        YtJson.playlistCards(response)
    }

    /** Артисты в выдаче поиска. */
    suspend fun searchArtists(query: String): List<ArtistCard> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .search(client = YouTubeClient.WEB_REMIX, query = query, params = ARTISTS_FILTER)
                .body<JsonObject>()
        YtJson.artistCards(response)
    }

    /** Страница артиста: треки, релизы, похожие. */
    suspend fun artist(browseId: String): ArtistPage = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId)
                .body<JsonObject>()
        YtJson.artistPage(response, browseId)
    }

    /** Страница альбома: обложка, подпись, треклист. */
    suspend fun album(browseId: String): AlbumPage = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId)
                .body<JsonObject>()
        YtJson.albumPage(response, browseId)
    }

    /**
     * Треки плейлиста или альбома по его browseId.
     *
     * Длинные списки YouTube отдаёт порциями по сто треков, поэтому дальше
     * идём по токену продолжения. Предел в страницах нужен: «все треки»
     * популярного артиста иначе тянулись бы десятком запросов подряд, и
     * экран стоял бы всё это время.
     */
    suspend fun playlistSongs(
        browseId: String,
        params: String? = null,
        maxPages: Int = 1,
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val first =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId, params = params, setLogin = true)
                .body<JsonObject>()
        // Только треки самого плейлиста: под своим плейлистом YouTube кладёт
        // «рекомендованные» строки того же вида, и раньше они попадали
        // в список и в очередь как будто из плейлиста.
        val head = YtJson.playlistPage(first, owned = null)
        val collected = head.songs.toMutableList()
        var token = head.continuation
        var page = 1
        while (token != null && page < maxPages) {
            val next =
                runCatching {
                    innerTube
                        .browse(
                            client = YouTubeClient.WEB_REMIX,
                            browseId = null,
                            continuation = token,
                            setLogin = true,
                        ).body<JsonObject>()
                }.getOrNull() ?: break
            val more = YtJson.playlistPage(next, owned = head.owned)
            if (more.songs.isEmpty()) break
            collected += more.songs
            token = more.continuation
            page++
        }
        collected.distinctBy { it.id }
    }

    /**
     * Рекомендации под плейлистом — отдельно от его треков. Нужны, только
     * если человек сам включил «показывать рекомендации в плейлистах».
     */
    suspend fun playlistSuggestions(browseId: String): List<SongItem> = withContext(Dispatchers.IO) {
        val first =
            innerTube
                .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId, setLogin = true)
                .body<JsonObject>()
        val own = YtJson.playlistTracks(first, first = true).map { it.id }.toSet()
        YtJson.songs(first).filterNot { it.id in own }
    }

    /**
     * Все треки артиста — тот же плейлист, что за «Показать все» на
     * странице артиста. Если YouTube такой ссылки не дал, у артиста её
     * просто нет: выдумывать список из поиска по имени не будем, туда
     * попадают чужие треки.
     */
    suspend fun artistSongs(page: ArtistPage): List<SongItem> {
        val browseId = page.allSongsBrowseId ?: return emptyList()
        return playlistSongs(browseId, page.allSongsParams, maxPages = ARTIST_SONG_PAGES)
    }

    /**
     * Подсказки поиска — те же, что подсказывает сам YouTube Music.
     *
     * Своих не придумываем: по трём буквам название трека угадывает только
     * тот, у кого есть их индекс.
     */
    suspend fun searchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .getSearchSuggestions(YouTubeClient.WEB_REMIX, query, false)
                .body<JsonObject>()
        YtJson.searchSuggestions(response).take(SUGGESTION_LIMIT)
    }

    /**
     * Выдача без фильтра: треки, альбомы и артисты одним запросом.
     *
     * Раздельные вкладки — это три отдельных запроса к YouTube, и на
     * «просто поискать» уходит три ожидания вместо одного. Здесь ответ
     * приходит смешанным, а разбираем мы его по типам сами.
     */
    suspend fun searchEverything(query: String): MixedResults = withContext(Dispatchers.IO) {
        val response =
            innerTube
                .search(client = YouTubeClient.WEB_REMIX, query = query)
                .body<JsonObject>()
        YtJson.mixed(response)
    }

    /**
     * Поток для трека. Блокировка на videoId нужна, чтобы одновременные
     * запросы (нажатие пользователя и предзагрузка очереди) не запускали
     * две расшифровки одного и того же трека.
     */
    suspend fun stream(videoId: String): ExtractedStream = stream(videoId, quality = null)

    /**
     * Поток с явным качеством — для загрузок: у них свой выбор качества,
     * отдельный от воспроизведения. Кэшируется под своим ключом, чтобы
     * загрузка в «лучшем» не подменила плееру ссылку «экономной».
     */
    suspend fun stream(videoId: String, quality: Quality?): ExtractedStream {
        val key = streamKey(videoId, quality)
        cached(key)?.let { return it }
        val lock = locks.getOrPut(key) { Mutex() }
        return lock.withLock {
            cached(key) ?: withContext(Dispatchers.IO) {
                val started = System.nanoTime()
                val extracted =
                    extractor.extract(
                        videoId = videoId,
                        hints = ContentHints(wantVideo = false),
                        audioQuality = quality?.let(::toAudioQuality) ?: currentQuality(),
                    // Библиотека возвращает null, когда YouTube отказал в
                    // воспроизведении (регион, возрастное ограничение,
                    // удалённое видео). Молчаливое null здесь превратилось бы
                    // в бесконечную «загрузку» в интерфейсе.
                    ) ?: throw NoStreamException(videoId)
                resolveMs[videoId] = (System.nanoTime() - started) / 1_000_000
                remember(key, extracted)
                extracted
            }
        }
    }

    /**
     * Настоящее ли это видео: YouTube Music помечает звуковые дорожки
     * `ATV`, всё остальное (клипы, ролики с YouTube) — видео. Известно
     * только когда поток уже получен для воспроизведения; до этого null.
     */
    fun isVideoTrack(videoId: String): Boolean? =
        streams[videoId]?.mediaMetadata?.musicVideoType?.let { it != "MUSIC_VIDEO_TYPE_ATV" }

    /**
     * Видеопоток трека — отдельным запросом, только когда человек включил
     * видео: обычное воспроизведение за видео не платит ни трафиком, ни
     * временем. Потоки SABR сюда не годятся (их читает только звуковой
     * путь), тогда null и кнопка честно говорит «недоступно».
     */
    suspend fun videoSource(videoId: String): VideoSource? =
        withContext(Dispatchers.IO) {
            val stream =
                runCatching { extractor.extract(videoId = videoId, hints = ContentHints(wantVideo = true, maxVideoHeight = VIDEO_MAX_HEIGHT), audioQuality = currentQuality()) }
                    .getOrNull() ?: return@withContext null
            val url = stream.videoUrl ?: return@withContext null
            if (url.startsWith("http")) return@withContext VideoSource(url, stream.headers, stream.videoWidth, stream.videoHeight)
            // YouTube отдаёт видео по SABR: проигрывается тем же источником, что и звук.
            val bootstrap = stream.sabrVideoBootstrap ?: return@withContext null
            videoBootstraps[videoId] = bootstrap
            VideoSource(com.texfi.w0y.playback.sabrUri(VIDEO_KEY + videoId).toString(), emptyMap(), stream.videoWidth, stream.videoHeight, sabr = true)
        }

    private val videoBootstraps = ConcurrentHashMap<String, com.metrolist.innertubex.sabr.SabrBootstrap>()

    /** Ключ потока в кэше: у загрузок своё качество — свой ключ. */
    fun streamKey(videoId: String, quality: Quality?): String = if (quality == null) videoId else "$videoId#${quality.name}"

    /**
     * Адрес, который можно дать плееру: обычная ссылка или, если YouTube
     * отдал звук только по SABR, `sabr://<ключ>` для [com.texfi.w0y.playback.SabrDataSource].
     */
    fun playable(key: String, stream: ExtractedStream): com.texfi.w0y.playback.ResolvedAudio =
        if (isSabr(stream)) {
            com.texfi.w0y.playback.ResolvedAudio(com.texfi.w0y.playback.sabrUri(key).toString(), emptyMap())
        } else {
            com.texfi.w0y.playback.ResolvedAudio(stream.audioUrl, stream.headers)
        }

    private fun isSabr(stream: ExtractedStream) =
        stream.sabrBootstrap != null && stream.audioUrl.substringBefore(':') == com.texfi.w0y.playback.SABR_SCHEME

    /** Данные SABR-потока из кэша — их читает [com.texfi.w0y.playback.SabrDataSource]. */
    fun sabrBootstrap(key: String): com.metrolist.innertubex.sabr.SabrBootstrap? =
        if (key.startsWith(VIDEO_KEY)) videoBootstraps[key.removePrefix(VIDEO_KEY)] else streams[key]?.sabrBootstrap

    /** Сколько заняло последнее получение адреса — часть разбора «нажал → звук». */
    private val resolveMs = ConcurrentHashMap<String, Long>()

    /** Время получения адреса для трека и сброс записи; null — адрес был в кэше. */
    fun takeResolveMs(videoId: String): Long? = resolveMs.remove(videoId)

    /**
     * Что известно о потоке, кроме адреса: предел диапазона, если YouTube
     * его задал, и полный размер. Только из кэша — сеть здесь не трогаем.
     */
    fun streamHint(key: String): com.texfi.w0y.playback.StreamHint? {
        val stream = streams[key] ?: return null
        val limit = if (stream.useRangeChunks || stream.requireBoundedRange) stream.rangeChunkSizeBytes else null
        return com.texfi.w0y.playback.StreamHint(limit?.takeIf { it > 0 }, stream.contentLengthBytes)
    }

    /** Забыть адрес: он истёк или сервер его отверг. */
    fun invalidate(videoId: String) {
        streams.keys.filter { it == videoId || it.startsWith("$videoId#") }.forEach(streams::remove)
    }

    /**
     * Свежий адрес взамен отвергнутого, синхронно — его ждёт поток
     * загрузчика, посреди файла. Качество то же, что было.
     */
    fun refreshBlocking(videoId: String, quality: Quality?): com.texfi.w0y.playback.ResolvedAudio? {
        invalidate(videoId)
        return runCatching {
            kotlinx.coroutines.runBlocking { stream(videoId, quality) }
        }.getOrNull()
            // Обновление нужно HTTP-загрузке посреди файла; SABR-адрес ей не подходит.
            ?.takeUnless(::isSabr)
            ?.let { com.texfi.w0y.playback.ResolvedAudio(it.audioUrl, it.headers) }
    }

    private val warmed = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Прогрев извлечения: конфигурация плеера и токены готовятся заранее,
     * а не в момент первого нажатия. Один раз за процесс, в фоне.
     */
    fun prewarm() {
        if (!warmed.compareAndSet(false, true)) return
        prefetchScope.launch {
            runCatching { extractor.prewarm() }.onFailure { Timber.w(it, "Прогрев извлечения не удался") }
        }
    }

    /** Греет кэш ссылки заранее — следующий трек должен стартовать мгновенно. */
    fun prefetch(videoId: String) {
        if (cached(videoId) != null) return
        prefetchScope.launch {
            runCatching { stream(videoId) }
                .onFailure { Timber.w(it, "Предзагрузка $videoId не удалась") }
        }
    }

    /** Есть ли живой адрес в кэше. */
    fun hasFreshStream(videoId: String): Boolean = cached(videoId) != null

    /**
     * Адреса для нескольких треков по очереди — для плиток быстрого набора.
     * Последовательно и с паузой: это фоновая подготовка, она не должна
     * спорить за сеть с тем, что играет или грузится на экране.
     */
    fun prefetchSequential(videoIds: List<String>, gapMs: Long = 400) {
        prefetchScope.launch {
            videoIds.filter { cached(it) == null }.forEach { id ->
                runCatching { stream(id) }
                kotlinx.coroutines.delay(gapMs)
            }
        }
    }

    private fun remember(videoId: String, stream: ExtractedStream) {
        streams[videoId] = stream
        synchronized(streamOrder) {
            streamOrder.remove(videoId)
            streamOrder.add(videoId)
            while (streamOrder.size > STREAM_CACHE_SIZE) {
                val oldest = streamOrder.removeAt(0)
                streams.remove(oldest)
                // Замок снимаем только свободный: занятый значит, что этот
                // трек прямо сейчас извлекают, и выкидывать его нельзя.
                locks[oldest]?.takeIf { !it.isLocked }?.let { locks.remove(oldest) }
            }
        }
    }

    /** Громкость трека, измеренная YouTube, — для выравнивания уровня. */
    fun cachedLoudnessDb(videoId: String): Double? = streams[videoId]?.loudnessDb

    /**
     * Качество берётся отдельно для Wi-Fi и мобильной сети: на мобильной
     * важнее не сжечь трафик и быстрее начать, на Wi-Fi — качество.
     */
    private suspend fun currentQuality(): AudioQuality {
        val prefs = settings.settings.first()
        val onWifi =
            runCatching {
                val manager = context.getSystemService(ConnectivityManager::class.java)
                val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            }.getOrDefault(true)
        val quality = if (onWifi) prefs.qualityWifi else prefs.qualityMobile
        return toAudioQuality(quality)
    }

    /** На Wi-Fi ли телефон — от этого зависит выбор качества. */
    fun onWifi(): Boolean =
        runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }.getOrDefault(true)

    private fun toAudioQuality(quality: Quality): AudioQuality =
        when (quality) {
            Quality.LOW -> AudioQuality.LOW
            // Библиотека знает только LOW/HIGH/AUTO: «среднее» честнее
            // отдать её автоматике, чем выдумывать несуществующую ступень.
            Quality.MEDIUM -> AudioQuality.AUTO
            Quality.HIGH -> AudioQuality.HIGH
        }

    private fun cached(videoId: String): ExtractedStream? {
        val stream = streams[videoId] ?: return null
        val expiresAt = stream.expiresAt ?: return stream
        // Небольшой запас: ссылка, истекающая через секунду, бесполезна —
        // плеер успеет получить 403 уже на первом запросе.
        return if (expiresAt.minus(EXPIRY_MARGIN) > Clock.System.now()) {
            stream
        } else {
            streams.remove(videoId)
            null
        }
    }

    /** YouTube не отдал поток: регион, возраст, удалённое видео. */
    class NoStreamException(videoId: String) : IllegalStateException("YouTube не отдал поток для $videoId")

    companion object {
        /**
         * Фильтры выдачи поиска. Это закодированные protobuf-параметры
         * самого YouTube: своего API у них нет, значения подобраны и
         * проверены сообществом и совпадают у всех открытых клиентов.
         */
        const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val ALBUMS_FILTER = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val ARTISTS_FILTER = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
        const val VIDEOS_FILTER = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"

        private val EXPIRY_MARGIN = kotlin.time.Duration.parse("30s")

        /** Три страницы — до трёхсот треков: дальше ждать дольше, чем слушать. */
        private const val ARTIST_SONG_PAGES = 3
        private const val SUGGESTION_LIMIT = 8

        /**
         * Сколько ссылок держим. Хватает на длинную очередь с запасом на
         * возвраты назад, и при этом память не растёт весь вечер.
         */
        private const val STREAM_CACHE_SIZE = 80
    }
}

/** Адрес видео и заголовки запроса; размер — если YouTube его назвал. */
data class VideoSource(val url: String, val headers: Map<String, String>, val width: Int?, val height: Int?, val sabr: Boolean = false)

private const val VIDEO_KEY = "v-"
private const val VIDEO_MAX_HEIGHT = 720
