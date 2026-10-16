package com.texfi.w0y.data

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * Зеркалирование своих плейлистов в аккаунт YouTube.
 *
 * Правило одно: локальная библиотека — источник истины, аккаунт — её
 * отражение. Поэтому ни одна ошибка записи в YouTube не отменяет того,
 * что человек сделал на телефоне: плейлист остаётся, трек остаётся, а
 * о неудаче говорим прямо и даём кнопку «залить заново». Обратное
 * поведение (откатывать своё, потому что не ответил сервер) в офлайне
 * означало бы, что приложением нельзя пользоваться без сети.
 */
@Singleton
class YtPlaylistSync @Inject constructor(
    private val innerTube: InnerTube,
    private val account: AccountRepository,
    private val settings: SettingsRepository,
) {
    private val _failure = MutableStateFlow<String?>(null)

    /** Последняя неудачная запись в аккаунт — её показывает библиотека. */
    val failure: StateFlow<String?> = _failure.asStateFlow()

    fun clearFailure() {
        _failure.value = null
    }

    /**
     * Зеркалить ли вообще. Без входа в аккаунт записывать некуда, и
     * настройка при этом остаётся включённой: она про желание, а не про
     * возможность.
     */
    suspend fun enabled(): Boolean =
        settings.settings.first().syncPlaylists && account.isSignedIn.first()

    /** Создаёт плейлист в аккаунте и возвращает его идентификатор. */
    suspend fun create(name: String): String? = attempt("create") {
        val response =
            innerTube.createPlaylist(YouTubeClient.WEB_REMIX, name).body<JsonObject>()
        YtJson.createdPlaylistId(response)
    }

    suspend fun add(remoteId: String, videoId: String): Boolean =
        attempt("add") {
            innerTube.addToPlaylist(YouTubeClient.WEB_REMIX, remoteId, videoId)
            true
        } ?: false

    /**
     * Убирает трек из плейлиста аккаунта.
     *
     * Одного videoId мало: один трек может лежать в плейлисте дважды, и
     * YouTube различает вхождения через setVideoId. Его приходится
     * вычитывать самим плейлистом — своего кэша у нас нет, а держать
     * его означало бы хранить чужое состояние и врать, когда плейлист
     * изменили с другого устройства.
     */
    suspend fun remove(remoteId: String, videoId: String): Boolean =
        attempt("remove") {
            val setVideoId = setVideoId(remoteId, videoId) ?: return@attempt false
            innerTube.removePlaylistSong(YouTubeClient.WEB_REMIX, remoteId, videoId, setVideoId)
            true
        } ?: false

    suspend fun rename(remoteId: String, name: String): Boolean =
        attempt("rename") {
            innerTube.renamePlaylist(YouTubeClient.WEB_REMIX, remoteId, name)
            true
        } ?: false

    /**
     * Убирает чужой плейлист из библиотеки аккаунта. Удалить его нельзя —
     * он не наш, — можно только отписаться.
     */
    suspend fun unsave(remoteId: String): Boolean =
        attempt("unsave") {
            innerTube.unlikePlaylist(YouTubeClient.WEB_REMIX, remoteId)
            true
        } ?: false

    suspend fun delete(remoteId: String): Boolean =
        attempt("delete") {
            innerTube.deletePlaylist(YouTubeClient.WEB_REMIX, remoteId)
            true
        } ?: false

    /**
     * Догоняет аккаунт до локального плейлиста: докладывает то, чего в нём
     * нет. Лишнего не убирает — в аккаунте могли добавить трек с другого
     * устройства, и удалять его нашей «синхронизацией» было бы грубо.
     */
    suspend fun push(remoteId: String, songIds: List<String>): Int? = attempt("push") {
        val present = remoteSongIds(remoteId)
        // Свои файлы в аккаунт не уходят никогда: у YouTube такого трека нет.
        val missing = songIds.filterNot { it in present || LocalMusic.isLocal(it) }
        missing.forEach { innerTube.addToPlaylist(YouTubeClient.WEB_REMIX, remoteId, it) }
        missing.size
    }

    /**
     * Переставляет треки в плейлисте аккаунта под локальный порядок.
     *
     * YouTube умеет только «поставить вхождение перед другим», поэтому
     * порядок собирается с конца: каждый трек ставится перед тем, что идёт
     * за ним. На одно перетаскивание это один-два запроса. Если перестановок
     * больше [MAX_MOVES], аккаунт не трогаем — честнее оставить порядок
     * локальным, чем сотней запросов упереться в ограничение YouTube.
     */
    suspend fun reorder(remoteId: String, desired: List<String>): Boolean {
        if (!enabled()) return false
        val remote = fetch(remoteId, maxPages = 10) ?: return false
        if (!remote.editable || !remote.complete) return false
        val moves = ReorderPlan.moves(remote.songs.map { it.id }, desired)
        if (moves.size > MAX_MOVES) return false
        return attempt("reorder") {
            moves.forEach { (item, before) ->
                val set = remote.setVideoIds[item] ?: return@forEach
                val successor = remote.setVideoIds[before] ?: return@forEach
                innerTube.movePlaylistSong(YouTubeClient.WEB_REMIX, remoteId, set, successor)
            }
            true
        } ?: false
    }

    /** Плейлист аккаунта целиком: треки, места в нём, обложка, права. */
    data class RemotePlaylist(
        val songs: List<SongItem>,
        val setVideoIds: Map<String, String>,
        val cover: String?,
        val editable: Boolean,
        /** Все ли страницы прочитаны: по неполному списку нельзя решать, что трек убрали. */
        val complete: Boolean,
        /** false — YouTube ответил, но такого плейлиста нет (удалён в аккаунте). */
        val exists: Boolean,
    )

    /**
     * Читает плейлист со всеми страницами. Неудачу не выносит в общий
     * [failure]: это фоновая сверка, и красная плашка при каждом
     * моргнувшем соединении только пугала бы. Возвращает null, если
     * сеть или ответ не дались.
     */
    suspend fun fetch(remoteId: String, maxPages: Int): RemotePlaylist? =
        runCatching {
            withContext(Dispatchers.IO) {
                val first =
                    innerTube
                        .browse(client = YouTubeClient.WEB_REMIX, browseId = browseId(remoteId), setLogin = true)
                val status = first.status.value
                if (status in 400..499) {
                    return@withContext RemotePlaylist(emptyList(), emptyMap(), null, false, true, exists = false)
                }
                val root = first.body<JsonObject>()
                val header = YtJson.playlistHeader(root)
                // Только треки самого плейлиста и только его продолжение: общий
                // токен страницы у короткого плейлиста ведёт в рекомендации.
                val head = YtJson.playlistPage(root, owned = null)
                val songs = head.songs.toMutableList()
                val videoIds = YtJson.setVideoIds(root).toMutableMap()
                var token = head.continuation
                var page = 1
                var complete = true
                while (token != null) {
                    if (page >= maxPages) {
                        complete = false
                        break
                    }
                    val next =
                        runCatching {
                            innerTube
                                .browse(
                                    client = YouTubeClient.WEB_REMIX,
                                    browseId = null,
                                    continuation = token,
                                    setLogin = true,
                                ).body<JsonObject>()
                        }.getOrNull()
                    if (next == null) {
                        complete = false
                        break
                    }
                    val more = YtJson.playlistPage(next, owned = head.owned)
                    if (more.songs.isEmpty()) break
                    songs += more.songs
                    videoIds += YtJson.setVideoIds(next)
                    token = more.continuation
                    page++
                }
                Timber.d(
                    "Плейлист %s: треков %d, правка %s, обложка %s, полный %s",
                    remoteId,
                    songs.size,
                    header.editable,
                    header.cover != null,
                    complete,
                )
                RemotePlaylist(
                    songs = songs.distinctBy { it.id },
                    setVideoIds = videoIds,
                    cover = header.cover,
                    editable = header.editable,
                    complete = complete,
                    exists = header.found || songs.isNotEmpty(),
                )
            }
        }.onFailure { Timber.w(it, "Плейлист %s не прочитан", remoteId) }.getOrNull()

    /** Убирает трек, место которого уже известно из только что прочитанного плейлиста. */
    suspend fun removeKnown(remoteId: String, videoId: String, setVideoId: String): Boolean =
        attempt("remove") {
            innerTube.removePlaylistSong(YouTubeClient.WEB_REMIX, remoteId, videoId, setVideoId)
            true
        } ?: false

    /** Лайк или снятие лайка в аккаунте. */
    suspend fun like(videoId: String, liked: Boolean): Boolean =
        attempt("like") {
            if (liked) {
                innerTube.likeVideo(YouTubeClient.WEB_REMIX, videoId)
            } else {
                innerTube.unlikeVideo(YouTubeClient.WEB_REMIX, videoId)
            }
            true
        } ?: false

    private suspend fun setVideoId(remoteId: String, videoId: String): String? =
        withContext(Dispatchers.IO) {
            val response =
                innerTube
                    .browse(
                        client = YouTubeClient.WEB_REMIX,
                        browseId = browseId(remoteId),
                        setLogin = true,
                    ).body<JsonObject>()
            YtJson.setVideoIds(response)[videoId]
        }

    private suspend fun remoteSongIds(remoteId: String): Set<String> =
        withContext(Dispatchers.IO) {
            val response =
                innerTube
                    .browse(
                        client = YouTubeClient.WEB_REMIX,
                        browseId = browseId(remoteId),
                        setLogin = true,
                    ).body<JsonObject>()
            YtJson.playlistTracks(response, first = true).map { it.id }.toSet()
        }

    private companion object {
        const val MAX_MOVES = 25
    }

    /** Читать плейлист нужно с приставкой «VL», менять — без неё. */
    private fun browseId(remoteId: String) =
        if (remoteId.startsWith("VL")) remoteId else "VL$remoteId"

    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? =
        runCatching { withContext(Dispatchers.IO) { block() } }
            .onSuccess { _failure.value = null }
            .onFailure { error ->
                Timber.w(error, "Синхронизация плейлиста ($what) не удалась")
                _failure.value = error.message ?: error::class.simpleName
            }.getOrNull()
}
