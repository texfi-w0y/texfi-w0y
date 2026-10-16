package com.texfi.w0y.data

import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber

/**
 * Двусторонняя сверка библиотеки с аккаунтом YouTube Music.
 *
 * Плейлисты и лайки сверяются по трём точкам: «что здесь», «что в
 * аккаунте» и «что было у обоих при прошлой сверке». Последнее и делает
 * сверку честной: трек, которого нет в аккаунте, — это либо новый
 * локальный, либо удалённый в аккаунте, и отличить одно от другого можно
 * только по снимку. Без него приходилось бы выбирать, что терять.
 *
 * Правила безопасности:
 *  - неполно прочитанный список (обрыв страницы) никогда не считается
 *    основанием что-то удалять;
 *  - пустой ответ вместо непустого списка — тоже: так выглядит сбой
 *    разбора, а не «пользователь стёр всё»;
 *  - плейлист, пропавший из аккаунта, стирается локально только если
 *    и список библиотеки его не содержит, и прямое обращение к нему
 *    подтверждает, что его нет.
 */
@Singleton
class AccountSync @Inject constructor(
    private val dao: W0yDao,
    private val account: AccountRepository,
    private val remote: YtPlaylistSync,
) {
    private val lock = Mutex()
    private val _running = MutableStateFlow(false)
    private val _albums = MutableStateFlow<List<PlaylistCard>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)

    @Volatile
    private var lastRunAt = 0L

    /** Идёт ли сверка сейчас. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Сохранённые альбомы аккаунта: локальных плейлистов из них не делаем. */
    val albums: StateFlow<List<PlaylistCard>> = _albums.asStateFlow()

    /** Почему последняя сверка не прошла — для строки под аккаунтом. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Сверяет всё. Без [force] повторный вход в раздел в течение минуты
     * не делает ничего: данные уже свежие, а запросов десятки.
     */
    suspend fun run(force: Boolean) {
        if (!account.isSignedIn.first()) return
        if (!force && now() - lastRunAt < MIN_GAP_MS) return
        if (!lock.tryLock()) return
        _running.value = true
        try {
            _error.value = null
            val writeBack = remote.enabled()
            val cards = account.allPlaylists()
            val playlistCards = cards.items.filterNot { it.isAlbum || it.remoteId() in SYSTEM }
            _albums.value = cards.items.filter { it.isAlbum }
            Timber.d("Аккаунт: плейлистов %d, альбомов %d, полный %s", playlistCards.size, _albums.value.size, cards.complete)

            dropDeletedInAccount(playlistCards, cards.complete)
            importCards(playlistCards)
            if (writeBack) publishLocalOnly()
            mergeDue(force, writeBack)
            syncLikes(writeBack)
            lastRunAt = now()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Timber.w(error, "Сверка с аккаунтом не удалась")
            _error.value = error.message ?: error::class.simpleName
        } finally {
            _running.value = false
            lock.unlock()
        }
    }

    /** Сверяет один плейлист — при открытии, чтобы не показывать вчерашний состав. */
    suspend fun refreshPlaylist(id: Long) {
        if (!account.isSignedIn.first()) return
        val playlist = dao.playlistOnce(id) ?: return
        if (playlist.remoteId == null) return
        if (!lock.tryLock()) return
        try {
            mergePlaylist(playlist, remote.enabled())
        } finally {
            lock.unlock()
        }
    }

    /** Плейлисты аккаунта становятся локальными: новые заводим, известные обновляем. */
    private suspend fun importCards(cards: List<PlaylistCard>) {
        val known = dao.remotePlaylists().associateBy { it.remoteId }
        cards.forEach { card ->
            val remoteId = card.remoteId()
            val existing = known[remoteId]
            if (existing == null) {
                dao.createPlaylist(
                    PlaylistEntity(
                        name = card.title,
                        createdAt = now(),
                        remoteId = remoteId,
                        coverUrl = card.thumbnailUrl,
                    ),
                )
            } else if (existing.name != card.title || (card.thumbnailUrl != null && existing.coverUrl != card.thumbnailUrl)) {
                dao.updatePlaylistInfo(existing.id, card.title, card.thumbnailUrl)
            }
        }
    }

    /**
     * Плейлист, исчезнувший из аккаунта, исчезает и здесь — но только при
     * двойном подтверждении (см. правила выше).
     */
    private suspend fun dropDeletedInAccount(cards: List<PlaylistCard>, complete: Boolean) {
        if (!complete || cards.isEmpty()) return
        val present = cards.map { it.remoteId() }.toSet()
        dao.remotePlaylists()
            .filter { it.remoteId !in present && it.syncedAt > 0 }
            .forEach { playlist ->
                val pulled = remote.fetch(playlist.remoteId ?: return@forEach, maxPages = 1)
                if (pulled != null && !pulled.exists) {
                    // Свои файлы в плейлисте нельзя терять из-за того, что его убрали в
                    // аккаунте: плейлист остаётся на телефоне и в аккаунт больше не уходит.
                    if (dao.localItemCount(playlist.id) > 0) {
                        Timber.d("Плейлист %s удалён в аккаунте — со своими файлами, оставляю на телефоне", playlist.remoteId)
                        dao.detachFromAccount(playlist.id)
                    } else {
                        Timber.d("Плейлист %s удалён в аккаунте — убираю и здесь", playlist.remoteId)
                        dao.deletePlaylist(playlist.id)
                    }
                }
            }
    }

    /** Свои плейлисты, заведённые здесь, отправляются в аккаунт сами. */
    private suspend fun publishLocalOnly() {
        dao.localOnlyPlaylists().forEach { playlist ->
            val remoteId = remote.create(playlist.name) ?: return@forEach
            dao.setRemoteId(playlist.id, remoteId)
        }
    }

    private suspend fun mergeDue(force: Boolean, writeBack: Boolean) {
        val due =
            dao.remotePlaylists()
                .filter { force || it.syncedAt == 0L || now() - it.syncedAt > PLAYLIST_TTL_MS }
                .sortedBy { it.syncedAt }
        due.forEach { mergePlaylist(it, writeBack) }
    }

    /**
     * Трёхстороннее слияние одного плейлиста (см. описание класса).
     * Порядок в аккаунте сохраняется у только что загруженных: новые
     * треки ложатся в том порядке, в каком их отдал YouTube.
     */
    private suspend fun mergePlaylist(playlist: PlaylistEntity, writeBack: Boolean) {
        val remoteId = playlist.remoteId ?: return
        val pulled = remote.fetch(remoteId, maxPages = if (playlist.remoteEditable) EDITABLE_PAGES else SAVED_PAGES)
        if (pulled == null) return
        if (!pulled.exists) return
        if (cleanOnce(playlist, remoteId, pulled)) return
        val base = playlist.syncBase?.lineSequence()?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        val localIds = dao.playlistSongIds(playlist.id)
        val localSet = localIds.toSet()
        val remoteIds = pulled.songs.map { it.id }
        val remoteSet = remoteIds.toSet()

        val plan = SyncMerge.plan(base, localSet, remoteSet, pulled.complete)

        val failedAdds = mutableSetOf<String>()
        val failedRemoves = mutableSetOf<String>()
        val canWrite = writeBack && pulled.editable
        if (canWrite) {
            plan.pushAdd.forEach { id ->
                if (!remote.add(remoteId, id)) failedAdds += id
            }
            plan.pushRemove.forEach { id ->
                val place = pulled.setVideoIds[id]
                if (place == null || !remote.removeKnown(remoteId, id, place)) failedRemoves += id
            }
        }

        val stamp = now()
        pulled.songs
            .filter { it.id in plan.pullAdd }
            .forEach { dao.addSongToPlaylist(playlist.id, SongEntity.from(it), stamp) }
        if (writeBack) {
            plan.pullRemove.forEach { dao.removeFromPlaylist(playlist.id, it) }
        }

        if (!writeBack) return
        val finalIds = dao.playlistSongIds(playlist.id).toSet()
        val newBase = (finalIds - failedAdds) + failedRemoves
        dao.markSynced(playlist.id, pulled.editable, newBase.joinToString("\n"), stamp, pulled.cover)
    }

    /**
     * Разовая чистка после ошибки разбора: сверка брала вторую страницу по
     * токену рекомендаций, и в плейлисты попадали чужие треки — каждый раз
     * другие. Слиянием это не убрать: такой трек выглядит как добавленный
     * здесь и ушёл бы в аккаунт. Поэтому состав один раз приводится ровно к
     * аккаунту, в его порядке, — и только по полностью прочитанному списку.
     */
    private suspend fun cleanOnce(playlist: PlaylistEntity, remoteId: String, pulled: YtPlaylistSync.RemotePlaylist): Boolean {
        if (!pulled.complete || remoteId in account.cleanedPlaylists()) return false
        // Пустой ответ вместо непустого плейлиста — скорее сбой, чем правда.
        if (pulled.songs.isEmpty() && dao.playlistSongIds(playlist.id).isNotEmpty()) return false
        val stamp = now()
        dao.replacePlaylistSongs(playlist.id, pulled.songs.map(SongEntity::from), stamp)
        dao.markSynced(playlist.id, pulled.editable, pulled.songs.joinToString("\n") { it.id }, stamp, pulled.cover)
        account.markPlaylistCleaned(remoteId)
        Timber.d("Плейлист %s приведён к аккаунту: %d треков", remoteId, pulled.songs.size)
        return true
    }

    /** Лайки — тот же способ, что и у плейлистов, по одному списку на весь аккаунт. */
    private suspend fun syncLikes(writeBack: Boolean) {
        val pulled = account.allLikedSongs()
        val base = account.likesBase()
        val baseSet = base ?: emptySet()
        val localSet = dao.likedIds().toSet()
        val remoteIds = pulled.items.map { it.id }
        val remoteSet = remoteIds.toSet()

        val plan = SyncMerge.plan(baseSet, localSet, remoteSet, pulled.complete)

        val stamp = now()
        // Новые лайки кладём в порядке аккаунта: свежие — выше.
        pulled.items.forEachIndexed { index, song ->
            if (song.id in plan.pullAdd) {
                dao.saveSongMeta(SongEntity.from(song))
                dao.setLiked(song.id, true, stamp - index * 1000L)
            }
        }
        if (writeBack) {
            plan.pullRemove.forEach { dao.setLiked(it, false, null) }
        }

        if (!writeBack) return
        val failed = mutableSetOf<String>()
        val failedRemoves = mutableSetOf<String>()
        // Пачками: первая сверка может принести сотни лайков, остальное уйдёт при следующей.
        val toPush = plan.pushAdd.toList()
        toPush.take(PUSH_LIKES_PER_RUN).forEach { if (!remote.like(it, true)) failed += it }
        toPush.drop(PUSH_LIKES_PER_RUN).forEach { failed += it }
        plan.pushRemove.forEach { if (!remote.like(it, false)) failedRemoves += it }

        val finalLocal = dao.likedIds().toSet()
        account.setLikesBase((finalLocal - failed) + failedRemoves)
    }

    private fun PlaylistCard.remoteId(): String = browseId.removePrefix("VL")

    private fun now() = System.currentTimeMillis()

    private companion object {
        /** Не чаще раза в минуту при входах в «Моё» подряд. */
        const val MIN_GAP_MS = 60_000L

        /** Состав плейлиста считается свежим пять минут. */
        const val PLAYLIST_TTL_MS = 5 * 60_000L
        const val EDITABLE_PAGES = 30
        const val SAVED_PAGES = 10
        const val PUSH_LIKES_PER_RUN = 40

        /** «Понравившиеся» (их ведёт сверка лайков) и «Эпизоды на потом». */
        val SYSTEM = setOf("LM", "SE")
    }
}
