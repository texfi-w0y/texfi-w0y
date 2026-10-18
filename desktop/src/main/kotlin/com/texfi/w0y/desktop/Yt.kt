package com.texfi.w0y.desktop

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.YouTubeLocale
import com.texfi.w0y.data.AlbumPage
import com.texfi.w0y.data.ArtistPage
import com.texfi.w0y.data.MixedResults
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.Shelf
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YtJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Доступ к YouTube Music для десктопа. Те же запросы и тот же разбор
 * (YtJson общий с телефоном), без Android: ни Hilt, ни кэша плеера.
 */
class Yt {
    private val http =
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    val innerTube: InnerTube =
        InnerTube(httpClient = http).apply {
            val lang = Locale.getDefault().language.ifBlank { "en" }
            val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
            locale = YouTubeLocale(gl = country, hl = lang)
        }
    private val extractor: InnerTubeExtractor =
        run {
            val store = RemotePlayerConfigStore(httpClient = http, repository = PlayerConfigRepository.disabled())
            InnerTubeExtractor(
                configParser = YtConfigParserImpl(http, innerTube, store),
                cipherService = YouTubeCipherService(http, store),
                innerTube = innerTube,
            )
        }

    private val streams = ConcurrentHashMap<String, ExtractedStream>()

    /** Подставляет cookie аккаунта (или снимает, если null). */
    fun applySession(cookie: String?) {
        innerTube.replaceSession(
            cookie = cookie,
            visitorData = null,
            dataSyncId = null,
            authUser = "0",
            useLoginForBrowse = !cookie.isNullOrBlank(),
        )
    }

    suspend fun accountInfo(): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val menu = innerTube.accountMenu(YouTubeClient.WEB_REMIX).body<JsonObject>()
        val name = with(YtJson) { menu.findAll("accountName").firstOrNull()?.firstString("text") }
        name to YtJson.accountAvatar(menu)
    }

    /**
     * Поиск тремя запросами с фильтрами, как на телефоне: в общей выдаче
     * в подписи трека первым стоит тип («Композиция», «Видео»), а не исполнитель.
     */
    suspend fun search(query: String): MixedResults = coroutineScope {
        suspend fun filtered(params: String) =
            innerTube.search(client = YouTubeClient.WEB_REMIX, query = query, params = params).body<JsonObject>()
        val songs = async(Dispatchers.IO) { YtJson.songs(filtered(SONGS_FILTER)) }
        val albums = async(Dispatchers.IO) { runCatching { YtJson.playlistCards(filtered(ALBUMS_FILTER)) }.getOrDefault(emptyList()) }
        val artists = async(Dispatchers.IO) { runCatching { YtJson.artistCards(filtered(ARTISTS_FILTER)) }.getOrDefault(emptyList()) }
        MixedResults(songs.await(), albums.await(), artists.await())
    }

    /** Только треки, как на вкладке «Треки» телефона. */
    suspend fun songsOnly(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        YtJson.songs(innerTube.search(client = YouTubeClient.WEB_REMIX, query = query, params = SONGS_FILTER).body<JsonObject>())
    }

    /** Официальная clean-версия трека, если выложена. Название сверяет CleanMatch — чужие каверы не подсунет. */
    suspend fun cleanVersion(song: SongItem): SongItem? =
        com.texfi.w0y.data.CleanMatch.pick(song, runCatching { songsOnly("${song.title} ${song.artist} clean") }.getOrDefault(emptyList()))

    /**
     * Опорный трек для рекомендаций к своему файлу: двойник из каталога или, если точного нет,
     * что-то того же исполнителя, потом просто первая находка. Лучше слегка мимо, чем пусто.
     */
    suspend fun anchorFor(local: SongItem): SongItem? {
        val norm = com.texfi.w0y.data.CleanMatch::normalize
        val core = norm(local.title)
        val artistKey = norm(local.artist).split(' ').firstOrNull().orEmpty()
        val query = "${local.artist} ${local.title}".trim()
        if (core.isNotBlank()) {
            val found = runCatching { songsOnly(query) }.getOrDefault(emptyList())
            found.firstOrNull { c ->
                val t = norm(c.title)
                val same = t.isNotBlank() && (t == core || t.contains(core) || core.contains(t))
                same && (artistKey.isBlank() || norm(c.artist).contains(artistKey))
            }?.let { return it }
        }
        var first: SongItem? = null
        for (q in listOf(query, local.artist.trim(), local.title.trim()).filter { it.isNotBlank() }.distinct()) {
            val found = runCatching { songsOnly(q) }.getOrDefault(emptyList())
            if (first == null) first = found.firstOrNull()
            val sameArtist = if (artistKey.isBlank()) null else found.firstOrNull { norm(it.artist).contains(artistKey) }
            if (sameArtist != null) return sameArtist
        }
        return first
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        YtJson.searchSuggestions(innerTube.getSearchSuggestions(YouTubeClient.WEB_REMIX, query, false).body<JsonObject>()).take(8)
    }

    /** Язык запросов: на нём приходят ленты и подсказки YouTube. */
    fun setLanguage(tag: String?) {
        val lang = tag ?: java.util.Locale.getDefault().language.ifBlank { "en" }
        val country = java.util.Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        innerTube.locale = YouTubeLocale(gl = country, hl = lang)
    }

    suspend fun home(): List<Shelf> = withContext(Dispatchers.IO) {
        YtJson.shelves(
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_home", setLogin = true)
                .body<JsonObject>(),
        )
    }

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

    suspend fun artist(browseId: String): ArtistPage = withContext(Dispatchers.IO) {
        YtJson.artistPage(
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = browseId).body<JsonObject>(),
            browseId,
        )
    }

    suspend fun album(browseId: String): AlbumPage = withContext(Dispatchers.IO) {
        YtJson.albumPage(
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = browseId).body<JsonObject>(),
            browseId,
        )
    }

    /** Треки плейлиста со всеми страницами (до [maxPages]). */
    suspend fun playlistSongs(browseId: String, maxPages: Int = 10, params: String? = null): List<SongItem> = withContext(Dispatchers.IO) {
        val first =
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = browseId, params = params, setLogin = true).body<JsonObject>()
        val all = YtJson.songs(first).toMutableList()
        var token = YtJson.continuation(first)
        var page = 1
        while (token != null && page < maxPages) {
            val next =
                runCatching {
                    innerTube
                        .browse(client = YouTubeClient.WEB_REMIX, browseId = null, continuation = token, setLogin = true)
                        .body<JsonObject>()
                }.getOrNull() ?: break
            val more = YtJson.songs(next)
            if (more.isEmpty()) break
            all += more
            token = YtJson.continuation(next)
            page++
        }
        all.distinctBy { it.id }
    }

    /** Лайкнутые и плейлисты аккаунта. Нужен вход. */
    suspend fun likedSongs(): List<SongItem> = playlistSongs("FEmusic_liked_videos", 20)

    suspend fun accountPlaylists(): List<PlaylistCard> = withContext(Dispatchers.IO) {
        YtJson.playlistCards(
            innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_liked_playlists", setLogin = true)
                .body<JsonObject>(),
        ).filterNot { it.isAlbum || it.browseId == "VLLM" || it.browseId == "VLSE" }
    }

    /** Плейлист аккаунта целиком: треки, места в нём, обложка, права на правку. */
    data class RemotePlaylist(
        val songs: List<SongItem>,
        val setVideoIds: Map<String, String>,
        val cover: String?,
        val editable: Boolean,
        val complete: Boolean,
        val exists: Boolean,
    )

    suspend fun fetchPlaylist(remoteId: String, maxPages: Int): RemotePlaylist? = withContext(Dispatchers.IO) {
        runCatching {
            val first =
                innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = "VL" + remoteId.removePrefix("VL"), setLogin = true)
            if (first.status.value in 400..499) return@runCatching RemotePlaylist(emptyList(), emptyMap(), null, false, true, exists = false)
            val root = first.body<JsonObject>()
            val header = YtJson.playlistHeader(root)
            val songs = YtJson.playlistTracks(root, first = true).toMutableList()
            val ids = YtJson.setVideoIds(root).toMutableMap()
            var token = YtJson.continuation(root)
            var page = 1
            var complete = true
            while (token != null) {
                if (page >= maxPages) { complete = false; break }
                val next =
                    runCatching {
                        innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = null, continuation = token, setLogin = true).body<JsonObject>()
                    }.getOrNull()
                if (next == null) { complete = false; break }
                val more = YtJson.playlistTracks(next, first = false)
                if (more.isEmpty()) break
                songs += more
                ids += YtJson.setVideoIds(next)
                token = YtJson.continuation(next)
                page++
            }
            RemotePlaylist(songs.distinctBy { it.id }, ids, header.cover, header.editable, complete, header.found || songs.isNotEmpty())
        }.getOrNull()
    }

    data class Paged<T>(val items: List<T>, val complete: Boolean)

    private suspend fun <T> paged(browseId: String, maxPages: Int, parse: (JsonObject) -> List<T>): Paged<T> = withContext(Dispatchers.IO) {
        val first = innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = browseId, setLogin = true).body<JsonObject>()
        val all = parse(first).toMutableList()
        var token = YtJson.continuation(first)
        var page = 1
        var complete = true
        while (token != null) {
            if (page >= maxPages) { complete = false; break }
            val next =
                runCatching {
                    innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = null, continuation = token, setLogin = true).body<JsonObject>()
                }.getOrNull()
            if (next == null) { complete = false; break }
            val more = parse(next)
            if (more.isEmpty()) break
            all += more
            token = YtJson.continuation(next)
            page++
        }
        Paged(all, complete)
    }

    suspend fun allPlaylists() = paged("FEmusic_liked_playlists", 6) { YtJson.playlistCards(it) }

    suspend fun allLiked() = paged("FEmusic_liked_videos", 20) { YtJson.songs(it) }

    suspend fun createPlaylist(name: String): String? = withContext(Dispatchers.IO) {
        YtJson.createdPlaylistId(innerTube.createPlaylist(YouTubeClient.WEB_REMIX, name).body<JsonObject>())
    }

    suspend fun addToRemote(remoteId: String, videoId: String) = withContext(Dispatchers.IO) {
        innerTube.addToPlaylist(YouTubeClient.WEB_REMIX, remoteId, videoId); Unit
    }

    suspend fun removeFromRemote(remoteId: String, videoId: String, setVideoId: String) = withContext(Dispatchers.IO) {
        innerTube.removePlaylistSong(YouTubeClient.WEB_REMIX, remoteId, videoId, setVideoId); Unit
    }

    suspend fun renameRemote(remoteId: String, name: String) = withContext(Dispatchers.IO) {
        innerTube.renamePlaylist(YouTubeClient.WEB_REMIX, remoteId, name); Unit
    }

    suspend fun deleteRemote(remoteId: String) = withContext(Dispatchers.IO) {
        innerTube.deletePlaylist(YouTubeClient.WEB_REMIX, remoteId); Unit
    }

    suspend fun unsaveRemote(remoteId: String) = withContext(Dispatchers.IO) {
        innerTube.unlikePlaylist(YouTubeClient.WEB_REMIX, remoteId); Unit
    }

    suspend fun like(videoId: String, liked: Boolean) = withContext(Dispatchers.IO) {
        if (liked) innerTube.likeVideo(YouTubeClient.WEB_REMIX, videoId) else innerTube.unlikeVideo(YouTubeClient.WEB_REMIX, videoId)
        Unit
    }

    /** Ссылка на звук трека; кэшируется до истечения срока. */
    suspend fun stream(videoId: String, quality: Quality = Quality.HIGH): ExtractedStream {
        streams[videoId]?.let { cached ->
            val expires = cached.expiresAt
            if (expires == null || expires.minus(30.seconds) > Clock.System.now()) return cached
            streams.remove(videoId)
        }
        return withContext(Dispatchers.IO) {
            val extracted =
                extractor.extract(
                    videoId = videoId,
                    hints = ContentHints(wantVideo = false),
                    audioQuality = when (quality) { Quality.LOW -> AudioQuality.LOW; Quality.MEDIUM -> AudioQuality.AUTO; Quality.HIGH -> AudioQuality.HIGH },
                ) ?: error("YouTube не отдал поток для $videoId")
            streams[videoId] = extracted
            extracted
        }
    }

    private companion object {
        const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val ALBUMS_FILTER = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val ARTISTS_FILTER = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
    }
}
