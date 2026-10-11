package com.texfi.w0y.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.texfi.w0y.data.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Что показывает интерфейс о текущем воспроизведении. */
data class PlayerUiState(
    val song: SongItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val queue: List<SongItem> = emptyList(),
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
)

/**
 * Мост между интерфейсом и медиа-сессией сервиса.
 *
 * Состояние собирается из самого плеера, а не из того, что когда-то
 * отправил экран: сервис живёт дольше экрана, и после сворачивания или
 * перезапуска активности интерфейс должен видеть правду, а не свою копию.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val queueStore: QueueStore,
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sleepJob: Job? = null

    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()

    private val _sleepAfterTrack = MutableStateFlow(false)

    /** Заведён ли таймер «до конца трека» — у него нет обратного отсчёта. */
    val sleepAfterTrack: StateFlow<Boolean> = _sleepAfterTrack.asStateFlow()

    private val listener =
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                push(player)
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                    )
                ) {
                    scheduleSave(player)
                }
            }

            /**
             * «До конца трека» — засыпают именно так, а не по круглым
             * минутам. Ставим паузу на переходе к следующему: доиграл —
             * и тишина, очередь остаётся на месте.
             */
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                if (!_sleepAfterTrack.value) return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    _sleepAfterTrack.value = false
                    controller?.pause()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.value = _state.value.copy(error = error.errorCodeName, isBuffering = false)
            }
        }

    // ── Скрытие мини-плеера жестом ───────────────────────────────────────

    private val dismisser =
        PlayerDismisser(
            object : DismissPort {
                override fun snapshot(): QueueSnapshot? {
                    val media = controller ?: return null
                    if (media.mediaItemCount == 0) return null
                    val songs = (0 until media.mediaItemCount).map { media.getMediaItemAt(it).toSong() }
                    return QueueSnapshot(songs, media.currentMediaItemIndex, media.currentPosition, media.isPlaying)
                }

                override fun pause() {
                    controller?.pause()
                }

                override fun stopAndClear() {
                    controller?.run {
                        stop()
                        clearMediaItems()
                    }
                }

                override fun restore(snapshot: QueueSnapshot, play: Boolean) {
                    val media = controller ?: return
                    media.setMediaItems(snapshot.songs.map(::toMediaItem), snapshot.index, snapshot.positionMs)
                    media.prepare()
                    if (play) media.play()
                }
            },
        )

    /** Остановить и убрать плеер; очередь и позиция остаются для [undoDismiss]. */
    fun dismissPlayer(): Boolean {
        saveJob?.cancel()
        return dismisser.dismiss()
    }

    fun undoDismiss(): Boolean = dismisser.undo()

    /** Окно «Отменить» закрыто: очередь больше не нужна ни в памяти, ни на диске. */
    fun commitDismiss() {
        if (dismisser.pending == null) return
        dismisser.commit()
        scope.launch { runCatching { queueStore.save(emptyList(), 0, 0L) } }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, W0yPlayerService::class.java))
        val future =
            MediaController.Builder(context, token)
                // Разрыв связи с сервисом надо ловить: без этого ссылка на
                // контроллер остаётся живой, события больше не приходят, и
                // экран навсегда застывает на последнем состоянии.
                .setListener(
                    object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            if (this@PlayerConnection.controller === controller) {
                                this@PlayerConnection.controller = null
                            }
                        }
                    },
                )
                .buildAsync()
        future.addListener({
            val media = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = media
            media.addListener(listener)
            push(media)
            restoreQueue(media)
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Перечитать состояние у сервиса.
     *
     * Нужно при возвращении в приложение. Пока экран не виден, связь с
     * сервисом может оборваться — например, система останавливает сервис
     * после того, как другое приложение забрало звук и воспроизведение
     * встало. События паузы в этот момент прийти уже некому, и кнопка
     * остаётся в положении «играет», хотя музыка давно остановлена.
     * Здесь состояние берётся заново у самого плеера, а если контроллер
     * отвалился — соединение поднимается с нуля.
     */
    fun refresh() {
        val media = controller
        if (media == null || !media.isConnected) {
            controller = null
            connect()
            return
        }
        push(media)
    }

    fun play(songs: List<SongItem>, startIndex: Int) {
        val media = controller ?: return
        media.setMediaItems(songs.map(::toMediaItem), startIndex, 0L)
        media.prepare()
        media.play()
    }

    /**
     * Заменяет всё, что идёт после текущего трека.
     *
     * Так работает и переключение режима очереди на ходу: играющий трек
     * не трогаем — обрывать его ради смены режима нельзя.
     */
    fun replaceUpcoming(songs: List<SongItem>) {
        val media = controller ?: return
        val from = media.currentMediaItemIndex + 1
        if (media.mediaItemCount > from) media.removeMediaItems(from, media.mediaItemCount)
        if (songs.isNotEmpty()) media.addMediaItems(songs.map(::toMediaItem))
    }

    /**
     * Ставит трек сразу после играющего.
     *
     * Дублировать его в очереди незачем: если он в ней уже есть, просто
     * переставляем — иначе «следующим» превращается в способ набить
     * очередь копиями одного трека.
     */
    fun playNext(song: SongItem) {
        val media = controller ?: return
        if (media.mediaItemCount == 0) {
            play(listOf(song), 0)
            return
        }
        val target = media.currentMediaItemIndex + 1
        val existing = indexOf(media, song.id)
        if (existing != null) {
            if (existing != target) media.moveMediaItem(existing, target)
            return
        }
        media.addMediaItem(target, toMediaItem(song))
    }

    /** Добавляет трек в конец очереди. */
    fun enqueue(song: SongItem) {
        val media = controller ?: return
        if (media.mediaItemCount == 0) {
            play(listOf(song), 0)
            return
        }
        if (indexOf(media, song.id) != null) return
        media.addMediaItem(toMediaItem(song))
    }

    /**
     * Убирает трек из очереди.
     *
     * Играющий не трогаем: удаление того, что звучит прямо сейчас, — это
     * не «убрать из очереди», а «выключить», и делать это одной кнопкой в
     * списке было бы неожиданно.
     */
    fun removeFromQueue(index: Int) {
        val media = controller ?: return
        if (index == media.currentMediaItemIndex) return
        if (index !in 0 until media.mediaItemCount) return
        media.removeMediaItem(index)
    }

    /** Поднимает трек в очереди на одну позицию. */
    fun moveUp(index: Int) {
        val media = controller ?: return
        if (index <= 0 || index >= media.mediaItemCount) return
        media.moveMediaItem(index, index - 1)
    }

    private fun indexOf(media: MediaController, songId: String): Int? =
        (0 until media.mediaItemCount).firstOrNull { media.getMediaItemAt(it).mediaId == songId }

    /** Очередь после текущего трека. */
    fun upcoming(): List<SongItem> {
        val media = controller ?: return emptyList()
        return ((media.currentMediaItemIndex + 1) until media.mediaItemCount)
            .map { media.getMediaItemAt(it).toSong() }
    }

    fun togglePlayPause() {
        val media = controller ?: return
        if (media.isPlaying) {
            media.pause()
        } else {
            // После восстановления очереди плеер стоит в IDLE: поток ещё не
            // запрашивался, и «играть» без prepare() не делает ничего.
            if (media.playbackState == Player.STATE_IDLE && media.mediaItemCount > 0) media.prepare()
            media.play()
        }
    }

    /** Переставляет трек очереди на новое место; играющий остаётся на месте. */
    fun moveInQueue(from: Int, to: Int) {
        val media = controller ?: return
        val count = media.mediaItemCount
        if (from !in 0 until count || to !in 0 until count || from == to) return
        if (from == media.currentMediaItemIndex || to <= media.currentMediaItemIndex) return
        media.moveMediaItem(from, to)
    }

    private var saveJob: Job? = null
    private var restored = false

    /**
     * Сохраняет очередь, но не на каждое событие подряд: смена трека,
     * пауза и правка очереди часто приходят пачкой. Пока играет, позиция
     * дописывается раз в [SAVE_TICK_MS] — хватит, чтобы после закрытия
     * приложения вернуться на то же место с точностью до секунд.
     */
    private fun scheduleSave(player: Player) {
        if (!restored) return
        // Плеер скрыт жестом: пустая очередь не должна стереть сохранённую,
        // пока можно нажать «Отменить».
        if (player.mediaItemCount == 0 && dismisser.pending != null) return
        saveJob?.cancel()
        saveJob =
            scope.launch {
                do {
                    delay(SAVE_DEBOUNCE_MS)
                    val songs = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() }
                    runCatching { queueStore.save(songs, player.currentMediaItemIndex, player.currentPosition) }
                    if (player.isPlaying) delay(SAVE_TICK_MS)
                } while (player.isPlaying)
            }
    }

    /** Возвращает очередь после перезапуска: трек на паузе, поток не запрашивается. */
    private fun restoreQueue(media: MediaController) {
        scope.launch {
            // Сервис мог пережить приложение: тогда очередь уже у него.
            if (media.mediaItemCount == 0) {
                queueStore.load()?.let { saved ->
                    media.setMediaItems(saved.songs.map(::toMediaItem), saved.index, saved.positionMs)
                }
            }
            restored = true
            push(media)
        }
    }

    fun skipNext() = controller?.seekToNextMediaItem()

    fun skipPrevious() {
        val media = controller ?: return
        // Как у всех плееров: первое нажатие возвращает к началу трека,
        // и только в первые секунды — к предыдущему.
        if (media.currentPosition > 4_000) media.seekTo(0) else media.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit

    /**
     * Перемотка на шаг от текущей позиции.
     *
     * Границы считаем сами: `seekTo` за пределы трека media3 обрабатывает
     * по-разному в зависимости от источника, а «перемотал назад в начале
     * трека — и он начался заново» выглядит как сбой.
     */
    fun seekBy(deltaMs: Long) {
        val media = controller ?: return
        val duration = media.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        media.seekTo((media.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    fun playAt(index: Int) {
        val media = controller ?: return
        media.seekTo(index, 0L)
        media.play()
    }

    fun toggleShuffle() {
        val media = controller ?: return
        media.shuffleModeEnabled = !media.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val media = controller ?: return
        media.repeatMode =
            when (media.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    /** Таймер сна: по истечении ставит паузу, а не глушит приложение. */
    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        _sleepAfterTrack.value = false
        val totalMs = minutes * 60_000L
        sleepJob =
            scope.launch {
                var left = totalMs
                while (left > 0) {
                    _sleepRemainingMs.value = left
                    delay(1_000)
                    left -= 1_000
                }
                _sleepRemainingMs.value = null
                controller?.pause()
            }
    }

    /** Пауза после текущего трека — без обратного отсчёта. */
    fun sleepAfterCurrentTrack() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = null
        _sleepAfterTrack.value = true
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = null
        _sleepAfterTrack.value = false
    }

    private fun push(player: Player) {
        val queue =
            (0 until player.mediaItemCount).map { index ->
                player.getMediaItemAt(index).toSong()
            }
        // Запущен другой трек — скрытый плеер уже не вернуть, «Отменить» гаснет.
        if (queue.isNotEmpty() && dismisser.pending != null) dismisser.commit()
        val index = player.currentMediaItemIndex
        _state.value =
            PlayerUiState(
                song = queue.getOrNull(index),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                durationMs = player.duration.takeIf { it > 0 } ?: 0L,
                queue = queue,
                currentIndex = index,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            )
    }

    private fun MediaItem.toSong(): SongItem =
        SongItem(
            id = mediaId,
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString().orEmpty(),
            album = mediaMetadata.albumTitle?.toString(),
            thumbnailUrl = mediaMetadata.artworkUri?.toString(),
            // Ссылки на артиста и альбом едут в extras: без них плеер не
            // смог бы предложить «к артисту», хотя из списка их знали.
            artistId = mediaMetadata.extras?.getString(EXTRA_ARTIST_ID),
            albumId = mediaMetadata.extras?.getString(EXTRA_ALBUM_ID),
            artists = com.texfi.w0y.data.ArtistLink.decode(mediaMetadata.extras?.getString(EXTRA_ARTISTS)),
        )

    // setCustomCacheKey помечен в media3 как нестабильный: без него кэш
    // ключуется по URL, а URL потока YouTube живёт несколько часов — один
    // и тот же трек лёг бы в кэш заново после каждого истечения ссылки.
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun toMediaItem(song: SongItem): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(song.id)
            // Схему разворачивает StreamResolver уже во время загрузки:
            // очередь собирается мгновенно, без сетевых запросов.
            .setUri(w0yUri(song))
            .apply { if (!com.texfi.w0y.data.LocalMusic.isLocal(song.id)) setCustomCacheKey(song.id) }
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.thumbnailUrl?.toUri())
                    .setExtras(
                        Bundle().apply {
                            putString(EXTRA_ARTIST_ID, song.artistId)
                            putString(EXTRA_ALBUM_ID, song.albumId)
                            putString(EXTRA_ARTISTS, com.texfi.w0y.data.ArtistLink.encode(song.artists))
                        },
                    ).build(),
            ).build()

    private companion object {
        const val SAVE_DEBOUNCE_MS = 600L
        const val SAVE_TICK_MS = 15_000L
        const val EXTRA_ARTIST_ID = "w0y.artistId"
        const val EXTRA_ARTISTS = "w0y.artists"
        const val EXTRA_ALBUM_ID = "w0y.albumId"
    }
}
