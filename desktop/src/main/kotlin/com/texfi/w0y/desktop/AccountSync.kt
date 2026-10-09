package com.texfi.w0y.desktop

import com.texfi.w0y.desktop.i18n.S
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texfi.w0y.data.SyncMerge
import java.util.UUID
import kotlinx.coroutines.sync.Mutex

/**
 * Двусторонняя сверка с аккаунтом YouTube Music — то же правило, что на телефоне.
 *
 * Сверка по трём точкам: «что здесь», «что в аккаунте» и «что было у обоих при
 * прошлой сверке» (base). Последнее отличает «удалили» от «ещё не пришло».
 * Логика слияния — общая с телефоном (SyncMerge). Страховки те же: неполно
 * прочитанный список и пустой ответ вместо непустого удалением не считаются;
 * плейлист, пропавший из аккаунта, стирается здесь только после двойной проверки.
 */
class AccountSync(private val app: AppState, private val yt: Yt) {
    var running by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val lock = Mutex()

    @Volatile
    private var lastRun = 0L

    suspend fun run(force: Boolean) {
        if (!app.signedIn) return
        if (!force && now() - lastRun < MIN_GAP_MS) return
        if (!lock.tryLock()) return
        running = true
        error = null
        try {
            val cards = yt.allPlaylists()
            val playlists = cards.items.filterNot { it.isAlbum || it.browseId.removePrefix("VL") in SYSTEM }
            app.accountAlbums = cards.items.filter { it.isAlbum }
            dropDeletedRemotely(playlists.map { it.browseId.removePrefix("VL") }.toSet(), cards.complete && playlists.isNotEmpty())
            importCards(playlists)
            if (app.st.syncPlaylists) publishLocalOnly()
            app.lib.playlists.filter { it.remoteId != null && (force || it.syncedAt == 0L || now() - it.syncedAt > TTL_MS) }
                .sortedBy { it.syncedAt }
                .forEach { mergePlaylist(it.id) }
            syncLikes()
            lastRun = now()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = S("library_sync_failed", e.message ?: "")
        } finally {
            running = false
            lock.unlock()
        }
    }

    /** Сверка одного плейлиста — при открытии, чтобы не показывать вчерашний состав. */
    suspend fun refreshPlaylist(id: String) {
        if (!app.signedIn || app.playlist(id)?.remoteId == null) return
        if (!lock.tryLock()) return
        try {
            mergePlaylist(id)
        } finally {
            lock.unlock()
        }
    }

    private fun importCards(cards: List<com.texfi.w0y.data.PlaylistCard>) {
        cards.forEach { card ->
            val rid = card.browseId.removePrefix("VL")
            val existing = app.lib.playlists.firstOrNull { it.remoteId == rid }
            if (existing == null) {
                app.update { it.copy(playlists = it.playlists + StoredPlaylist(name = card.title, remoteId = rid, cover = card.thumbnailUrl)) }
            } else if (existing.name != card.title || (card.thumbnailUrl != null && existing.cover != card.thumbnailUrl)) {
                app.update { l ->
                    l.copy(playlists = l.playlists.map { if (it.id == existing.id) it.copy(name = card.title, cover = card.thumbnailUrl ?: it.cover) else it })
                }
            }
        }
    }

    private suspend fun dropDeletedRemotely(present: Set<String>, trusted: Boolean) {
        if (!trusted) return
        app.lib.playlists.filter { it.remoteId != null && it.remoteId !in present && it.syncedAt > 0 }.forEach { pl ->
            val pulled = yt.fetchPlaylist(pl.remoteId!!, 1)
            if (pulled != null && !pulled.exists) app.update { l -> l.copy(playlists = l.playlists.filterNot { it.id == pl.id }) }
        }
    }

    private suspend fun publishLocalOnly() {
        app.lib.playlists.filter { it.remoteId == null }.forEach { pl ->
            val rid = runCatching { yt.createPlaylist(pl.name) }.getOrNull() ?: return@forEach
            app.update { l -> l.copy(playlists = l.playlists.map { if (it.id == pl.id) it.copy(remoteId = rid) else it }) }
        }
    }

    private suspend fun mergePlaylist(id: String) {
        val pl = app.playlist(id) ?: return
        val rid = pl.remoteId ?: return
        val pulled = yt.fetchPlaylist(rid, if (pl.editable) 30 else 10) ?: return
        if (!pulled.exists) return
        val base = pl.base?.toSet() ?: emptySet()
        val localSet = pl.songs.map { it.id }.toSet()
        val remoteSet = pulled.songs.map { it.id }.toSet()
        val writeBack = app.st.syncPlaylists
        val plan = SyncMerge.plan(base, localSet, remoteSet, pulled.complete)

        val failedAdds = mutableSetOf<String>()
        val failedRemoves = mutableSetOf<String>()
        if (pulled.editable && writeBack) {
            plan.pushAdd.forEach { v -> if (runCatching { yt.addToRemote(rid, v) }.isFailure) failedAdds += v }
            plan.pushRemove.forEach { v ->
                val place = pulled.setVideoIds[v]
                if (place == null || runCatching { yt.removeFromRemote(rid, v, place) }.isFailure) failedRemoves += v
            }
        }
        // Локальные правки, сделанные за время сети, не затираем: применяем разницу к свежему состоянию.
        app.update { l ->
            l.copy(
                playlists =
                    l.playlists.map { cur ->
                        if (cur.id != id) {
                            cur
                        } else {
                            val added = pulled.songs.filter { it.id in plan.pullAdd && cur.songs.none { s -> s.id == it.id } }.map { it.stored() }
                            val kept = if (writeBack) cur.songs.filterNot { it.id in plan.pullRemove } else cur.songs
                            val finalIds = (kept + added).map { it.id }.toSet()
                            cur.copy(
                                songs = kept + added,
                                editable = pulled.editable,
                                // Без записи в аккаунт локальные правки остаются «несверенными»: база не двигается.
                                base = if (writeBack) ((finalIds - failedAdds) + failedRemoves).toList() else cur.base,
                                syncedAt = now(),
                                cover = pulled.cover ?: cur.cover,
                            )
                        }
                    },
            )
        }
    }

    private suspend fun syncLikes() {
        val pulled = yt.allLiked()
        val baseList = app.lib.likesBase
        val base = baseList?.toSet() ?: emptySet()
        val local = app.lib.liked.map { it.id }.toSet()
        val remote = pulled.items.map { it.id }.toSet()
        val plan = SyncMerge.plan(base, local, remote, pulled.complete)

        val writeBack = app.st.syncPlaylists
        val failedPush = mutableSetOf<String>()
        val failedRemove = mutableSetOf<String>()
        val toPush = plan.pushAdd.toList()
        if (writeBack) toPush.take(PUSH_LIKES_PER_RUN).forEach { if (runCatching { yt.like(it, true) }.isFailure) failedPush += it }
        if (writeBack) toPush.drop(PUSH_LIKES_PER_RUN).forEach { failedPush += it }
        if (writeBack) plan.pushRemove.forEach { if (runCatching { yt.like(it, false) }.isFailure) failedRemove += it }

        app.update { l ->
            val stamp = now()
            val fresh = pulled.items.filter { it.id in plan.pullAdd && l.liked.none { s -> s.id == it.id } }.map { it.stored() }
            val kept = if (writeBack) l.liked.filterNot { it.id in plan.pullRemove } else l.liked
            val liked = fresh + kept
            l.copy(liked = liked, likesBase = if (writeBack) ((liked.map { it.id }.toSet() - failedPush) + failedRemove).toList() else l.likesBase)
        }
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val MIN_GAP_MS = 60_000L
        const val TTL_MS = 5 * 60_000L
        const val PUSH_LIKES_PER_RUN = 40
        val SYSTEM = setOf("LM", "SE")
    }
}
