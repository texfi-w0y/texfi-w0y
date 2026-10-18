package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.desktop.i18n.S
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Очередь и воспроизведение. mpv играет, этот класс решает, что играть
 * дальше: следующий в очереди, а когда очередь кончилась — радио по
 * последнему треку, как на телефоне.
 */
class PlayerCtl(
    private val scope: CoroutineScope,
    private val yt: Yt,
    private val app: AppState,
) {
    var queue by mutableStateOf<List<SongItem>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var position by mutableDoubleStateOf(0.0)
        private set
    var duration by mutableDoubleStateOf(0.0)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var volume by mutableIntStateOf(100)
        private set

    val current: SongItem? get() = queue.getOrNull(index)

    /** Сколько секунд осталось до остановки таймером сна; null — таймер не заведён. */
    var sleepRemaining by mutableStateOf<Int?>(null)
        private set

    /** Таймер «до конца трека»: музыка встанет, когда доиграет текущий. */
    var sleepAfterTrack by mutableStateOf(false)
        private set
    private var sleepJob: Job? = null

    /** Есть ли у mpv загруженный трек: после рестарта очередь восстановлена, а звука ещё нет. */
    private var loaded = false
    private var startJob: Job? = null
    private var mpvOk = true

    /** Какой движок звука работает и его версия — для `Copy debug info`. */
    val engineName: String get() = mpv.name
    val engineVersion: String? get() = mpv.version

    private val mpv: AudioEngine =
        AudioEngine.create(
            onTime = { t -> countPlayed(t); position = t },
            onDuration = {
                duration = it
                // Свой файл без длительности в тегах: узнаём её при первом проигрывании и запоминаем.
                current?.takeIf { s -> LocalFiles.isLocal(s.id) && s.durationText == null && it > 0 }?.let { s -> scope.launch { app.updateLocalDuration(s.id, it) } }
            },
            onPause = { playing = !it && loaded },
            onEnd = { reason ->
                when (reason) {
                    "eof" ->
                        scope.launch {
                            if (sleepAfterTrack) {
                                sleepAfterTrack = false
                                playing = false
                                // Позиция остаётся в конце, следующий трек готов к запуску.
                                if (index + 1 < queue.size) index += 1
                                loaded = false
                                app.saveQueue(queue, index)
                            } else if (app.st.repeat == RepeatMode.ONE) {
                                mpv.seek(0.0)
                                mpv.setPause(false)
                            } else {
                                next(auto = true)
                            }
                        }
                    "error" -> {
                        loading = false
                        playing = false
                        error = S("d_err_play")
                        scope.launch { delay(1500); next(auto = true) }
                    }
                }
            },
            onFileLoaded = {
                loading = false
                loaded = true
                playing = true
                if (loadStartedAt > 0) {
                    val ms = ((System.nanoTime() - loadStartedAt) / 1_000_000).toInt()
                    loadStartedAt = 0
                    lastStartupMs = ms
                    startups = (startups + ms).takeLast(20)
                }
            },
        )

    /** Замер «нажал → звук»: показывается в настройках, раздел «Скорость». */
    var lastStartupMs by mutableStateOf<Int?>(null)
        private set
    private var startups = listOf<Int>()
    private var loadStartedAt = 0L

    fun startupSummary(): String =
        if (startups.isEmpty()) S("d_startup_none") else S("d_startup_avg", startups.size, startups.average().toInt())

    /** Окна заглушения строк с матом (мс) для текущего трека. */
    private var swearWindows = listOf<LongRange>()
    private var swearFor: String? = null
    private var swearJob: Job? = null

    private fun watchSwear(song: SongItem) {
        swearJob?.cancel()
        swearWindows = emptyList()
        swearFor = song.id
        if (!app.st.muteSwearLines) return
        swearJob =
            scope.launch {
                val lines = runCatching { app.lyrics.lyrics(song)?.synced.orEmpty() }.getOrDefault(emptyList())
                if (swearFor != song.id) return@launch
                swearWindows =
                    lines.mapIndexedNotNull { i, l ->
                        if (!com.texfi.w0y.data.Profanity.inText(l.text)) return@mapIndexedNotNull null
                        l.timeMs until (lines.getOrNull(i + 1)?.timeMs ?: (l.timeMs + 6000))
                    }
                var muted = false
                while (swearFor == song.id) {
                    val now = (position * 1000).toLong()
                    val shouldMute = app.st.muteSwearLines && swearWindows.any { now in it }
                    if (shouldMute != muted) {
                        muted = shouldMute
                        mpv.setMute(muted)
                    }
                    delay(150)
                }
                if (muted) mpv.setMute(false)
            }
    }

    fun start(restored: List<SongItem>, restoredIndex: Int, volume: Int) {
        mpvOk = mpv.start()
        if (!mpvOk) error = S("d_err_mpv")
        queue = restored
        index = restoredIndex.coerceIn(-1, restored.lastIndex)
        this.volume = volume
        mpv.setVolume(volume)
        if (app.st.audioDevice != "auto") mpv.setAudioDevice(app.st.audioDevice)
    }

    fun shutdown() {
        finishPlay()
        mpv.shutdown()
    }

    fun play(songs: List<SongItem>, at: Int) {
        val first = songs.getOrNull(at.coerceIn(0, songs.lastIndex)) ?: return
        // Режим «что играет дальше»: по очереди, вперемешку или рекомендации.
        val mode = if (app.st.recInsteadOfList) QueueMode.RADIO else app.st.queueMode
        when (mode) {
            QueueMode.ORDER -> {
                queue = songs
                index = at.coerceIn(0, songs.lastIndex)
            }
            QueueMode.SHUFFLE -> {
                queue = listOf(first) + songs.filterIndexed { i, _ -> i != at.coerceIn(0, songs.lastIndex) }.shuffled()
                index = 0
            }
            QueueMode.RADIO -> {
                queue = listOf(first)
                index = 0
                scope.launch {
                    val more = runCatching { app.recommender.forSeed(first, exclude = setOf(first.id)) }.getOrDefault(emptyList())
                    if (current?.id == first.id) queue = queue + more.filter { m -> m.id != first.id }
                    app.saveQueue(queue, index)
                }
            }
        }
        loadCurrent()
        app.saveQueue(queue, index)
    }

    fun playOne(song: SongItem) = play(listOf(song), 0)

    /** Свой файл переименовали или сменили обложку: очередь показывает новые данные, играет тот же файл. */
    fun refreshSong(song: SongItem) {
        if (queue.none { it.id == song.id }) return
        queue = queue.map { if (it.id == song.id) song else it }
    }

    /** Свой файл удалили: из очереди он уходит, кроме играющего (тот доиграет). */
    fun forget(id: String) {
        val now = current?.id
        queue = queue.filterNot { it.id == id && it.id != now }
        index = if (now == null) index else queue.indexOfFirst { it.id == now }
        app.saveQueue(queue, index)
    }

    // ---- журнал прослушиваний: засчитывается, если проиграно хотя бы полтрека (для длинных — 4 минуты)
    private var playedSec = 0.0
    private var lastTick = -1.0
    private var playedSong: SongItem? = null

    private fun countPlayed(t: Double) {
        val d = t - lastTick
        if (lastTick >= 0 && d > 0 && d < 2.5) playedSec += d
        lastTick = t
    }

    /** Закрыть учёт текущего трека: записать прослушивание, если набралась половина. */
    private fun finishPlay() {
        val song = playedSong
        val need = if (duration > 0) minOf(duration / 2, 240.0) else 60.0
        if (song != null && playedSec >= need) app.logPlay(song, playedSec.toInt())
        playedSong = current
        playedSec = 0.0
        lastTick = -1.0
    }

    private fun loadCurrent() {
        finishPlay()
        if (current == null) return
        startJob?.cancel()
        error = null
        loading = true
        playing = false
        loaded = false
        position = 0.0
        duration = 0.0
        loadStartedAt = System.nanoTime()
        startJob =
            scope.launch {
                var song = current ?: return@launch
                // «Без мата»: трек с меткой «E» заменяется чистой версией, а нет её — по выбору играет или пропускается.
                if (app.st.cleanMode && song.explicit) {
                    val clean = runCatching { yt.cleanVersion(song) }.getOrNull()
                    if (clean != null) {
                        queue = queue.toMutableList().also { it[index] = clean }
                        song = clean
                    } else if (app.st.explicitFallback == ExplicitFallback.SKIP) {
                        next(auto = true)
                        return@launch
                    }
                }
                val profile = app.soundOf(song.id).also { soundNow = it }
                if (LocalFiles.isLocal(song.id)) {
                    // Свой файл играет с диска; YouTube про него ничего не знает.
                    val file = LocalFiles.file(song.id)
                    if (!file.isFile) {
                        loading = false
                        error = S("d_err_local_missing")
                        delay(1500)
                        next(auto = true)
                        return@launch
                    }
                    gainNow = null
                    mpv.applyAudio(profile, null, app.st.skipSilence, eqNow())
                    mpv.load(file.absolutePath, emptyMap())
                    app.remember(song)
                    notifyTrack(song)
                    return@launch
                }
                val local = app.downloadedPath(song.id)
                if (local != null) {
                    // Скачанный трек играет с диска: ни сети, ни ссылки, которая истекает.
                    mpv.applyAudio(profile, null, app.st.skipSilence, eqNow())
                    mpv.load(local, emptyMap())
                    app.remember(song)
                    watchSwear(song)
                    return@launch
                }
                runCatching { yt.stream(song.id, app.st.quality) }
                    .onSuccess {
                        val gain = (if (app.st.normalizeVolume) it.loudnessDb?.let { db -> (-db).coerceIn(-10.0, 3.0) } else null).also { g -> gainNow = g }
                        mpv.applyAudio(profile, gain, app.st.skipSilence, eqNow())
                        mpv.load(it.audioUrl, it.headers)
                        app.remember(song)
                        notifyTrack(song)
                        watchSwear(song)
                        // Следующий трек греем заранее: старт без паузы.
                        if (app.st.preloadNext) queue.getOrNull(index + 1)?.takeIf { n -> !LocalFiles.isLocal(n.id) }?.let { n -> launch { runCatching { yt.stream(n.id, app.st.quality) } } }
                    }.onFailure {
                        loading = false
                        error = S("d_err_stream")
                        delay(1500)
                        next(auto = true)
                    }
            }
    }

    fun setAudioDevice(id: String) = mpv.setAudioDevice(id)

    /** Звучание текущего трека: для панели «Звук». */
    var soundNow by mutableStateOf(com.texfi.w0y.data.SoundProfile.Plain)
        private set

    fun setSound(profile: com.texfi.w0y.data.SoundProfile) {
        val song = current ?: return
        soundNow = profile
        app.saveSound(song.id, profile)
        reapplyAudio()
    }

    private fun eqNow(): List<Int>? = app.st.eqBands.takeIf { app.st.eqEnabled && it.any { g -> g != 0 } }

    /** Громкость-выравнивание трека, запомненная при загрузке: нужна, чтобы эквалайзер менялся на лету. */
    private var gainNow: Double? = null

    /** Перенастроить фильтры звука на играющем треке (эквалайзер, пропуск тишины). */
    fun reapplyAudio() {
        if (current == null) return
        mpv.applyAudio(soundNow, gainNow, app.st.skipSilence, eqNow())
    }

    /** Таймер сна: через [minutes] минут пауза. */
    fun startSleep(minutes: Int) {
        cancelSleep()
        sleepRemaining = minutes * 60
        sleepJob =
            scope.launch {
                while ((sleepRemaining ?: 0) > 0) {
                    delay(1000)
                    sleepRemaining = (sleepRemaining ?: 1) - 1
                }
                sleepRemaining = null
                if (playing) mpv.setPause(true)
            }
    }

    fun sleepUntilTrackEnds() {
        cancelSleep()
        sleepAfterTrack = true
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
        sleepRemaining = null
        sleepAfterTrack = false
    }

    fun toggle() {
        if (!mpvOk) return
        if (current == null) return
        if (!loaded) {
            loadCurrent()
            return
        }
        mpv.setPause(playing)
    }

    suspend fun next(auto: Boolean = false) {
        if (queue.isEmpty()) return
        if (index + 1 < queue.size) {
            index += 1
            loadCurrent()
            app.saveQueue(queue, index)
            return
        }
        // Очередь кончилась: повтор списка — сначала; иначе радио по последнему треку (только в режиме рекомендаций).
        if (app.st.repeat == RepeatMode.ALL) {
            index = 0
            loadCurrent()
            app.saveQueue(queue, index)
            return
        }
        // Что дальше, решает «Когда список кончился»: похожее (как радио), список сначала или тишина.
        if (app.st.recEndMode == RecEndMode.REPEAT) {
            index = 0
            loadCurrent()
            app.saveQueue(queue, index)
            return
        }
        if (app.st.recEndMode == RecEndMode.STOP && app.st.queueMode != QueueMode.RADIO && !app.st.recInsteadOfList) {
            playing = false
            return
        }
        val last = current ?: return
        val more = runCatching { app.recommender.forSeed(last, exclude = queue.map { it.id }.toSet()) }.getOrDefault(emptyList())
        if (more.isNotEmpty()) {
            queue = queue + more
            index += 1
            loadCurrent()
            app.saveQueue(queue, index)
        } else if (auto) {
            playing = false
        }
    }

    fun nextClick() = scope.launch { next() }

    fun previous() {
        if (position > 4 || index <= 0) {
            mpv.seek(0.0)
            if (!loaded) loadCurrent()
            return
        }
        index -= 1
        loadCurrent()
        app.saveQueue(queue, index)
    }

    fun seekFraction(fraction: Double) {
        if (duration > 0 && loaded) {
            position = duration * fraction
            mpv.seek(duration * fraction)
        }
    }

    fun seekSeconds(seconds: Double) {
        if (loaded) {
            position = seconds
            mpv.seek(seconds)
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices) return
        val list = queue.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        // Индекс играющего трека едет вместе с ним.
        index =
            when {
                from == index -> to
                from < index && to >= index -> index - 1
                from > index && to <= index -> index + 1
                else -> index
            }
        queue = list
        app.saveQueue(queue, index)
    }

    fun changeVolume(percent: Int) {
        volume = percent.coerceIn(0, 100)
        mpv.setVolume(volume)
        app.saveVolume(volume)
    }

    fun enqueue(song: SongItem, next: Boolean) {
        if (queue.isEmpty()) {
            play(listOf(song), 0)
            return
        }
        queue =
            if (next) {
                queue.toMutableList().apply { add(index + 1, song) }
            } else {
                queue + song
            }
        app.saveQueue(queue, index)
    }

    fun removeFromQueue(position: Int) {
        if (position !in queue.indices) return
        val wasCurrent = position == index
        queue = queue.toMutableList().apply { removeAt(position) }
        if (position < index) index -= 1
        if (wasCurrent) {
            if (queue.isEmpty()) {
                index = -1
                mpv.stop()
                playing = false
                loaded = false
            } else {
                index = index.coerceAtMost(queue.lastIndex)
                loadCurrent()
            }
        }
        app.saveQueue(queue, index)
    }

    fun jump(to: Int) {
        index = to
        loadCurrent()
        app.saveQueue(queue, index)
    }

    // ---- перемешивание, повтор, скорость, перемотка шагом

    /** Перемешать то, что ещё не играло; играющий трек остаётся на месте. */
    fun setShuffle(on: Boolean) {
        app.setSt { it.copy(shuffle = on) }
        if (on && queue.size > 2) {
            val head = queue.take(index + 1)
            queue = head + queue.drop(index + 1).shuffled()
            app.saveQueue(queue, index)
        }
    }

    fun toggleShuffle() = setShuffle(!app.st.shuffle)

    fun cycleRepeat() = app.setSt {
        it.copy(repeat = when (it.repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF })
    }

    fun seekBy(seconds: Double) {
        if (!loaded) return
        seekSeconds((position + seconds).coerceIn(0.0, if (duration > 0) duration else Double.MAX_VALUE))
    }

    private var volumeBeforeMute = -1

    val muted: Boolean get() = volumeBeforeMute >= 0

    fun toggleMute() {
        if (volumeBeforeMute >= 0) {
            val back = volumeBeforeMute.coerceAtLeast(5)
            volumeBeforeMute = -1
            changeVolume(back)
        } else {
            volumeBeforeMute = volume
            changeVolume(0)
        }
    }

    /** Скорость текущего трека шагом 0.05 в пределах 0.5–2.0, запоминается как звук трека. */
    fun nudgeSpeed(delta: Float) {
        val s = soundNow
        val next = (Math.round((s.speed + delta) * 20) / 20f).coerceIn(0.5f, 2f)
        setSound(s.copy(speed = next))
    }

    /** Системное уведомление о смене трека. */
    private fun notifyTrack(song: SongItem) {
        if (!app.st.notifyTrackChange) return
        Platform.notify(song.title, song.artist)
    }
}
