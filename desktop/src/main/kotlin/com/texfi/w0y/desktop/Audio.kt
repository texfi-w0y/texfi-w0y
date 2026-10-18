package com.texfi.w0y.desktop

import com.texfi.w0y.data.SoundProfile

/**
 * Движок звука. Всё приложение говорит с ним через этот интерфейс и не знает, что внутри:
 * libmpv, вызванная напрямую ([LibMpv] — основной путь на Windows и Linux), или запасной
 * mpv-процесс с IPC-сокетом ([Mpv] — Linux, если libmpv не нашлась).
 */
interface AudioEngine {
    /** Короткое имя движка для журнала и `Copy debug info`. */
    val name: String

    /** Версия mpv/libmpv, если движок её знает. */
    val version: String?

    fun start(): Boolean

    fun load(url: String, headers: Map<String, String>)

    /** Скорость, тон, реверб, выравнивание, пропуск тишины и эквалайзер одним списком фильтров. */
    fun applyAudio(profile: SoundProfile, gainDb: Double?, skipSilence: Boolean, eqBands: List<Int>? = null)

    fun setMute(muted: Boolean)

    fun setAudioDevice(id: String)

    fun setPause(paused: Boolean)

    fun seek(seconds: Double)

    fun setVolume(percent: Int)

    fun stop()

    fun shutdown()

    companion object {
        /**
         * libmpv, пока она грузится; иначе процесс mpv (только не Windows — там без libmpv звука нет).
         * `W0Y_ENGINE=ipc` принудительно берёт запасной путь, это нужно для проверок.
         */
        fun create(
            onTime: (Double) -> Unit,
            onDuration: (Double) -> Unit,
            onPause: (Boolean) -> Unit,
            onEnd: (reason: String) -> Unit,
            onFileLoaded: () -> Unit,
        ): AudioEngine {
            val forced = System.getenv("W0Y_ENGINE")
            if (forced != "ipc" && LibMpv.available()) return LibMpv(onTime, onDuration, onPause, onEnd, onFileLoaded)
            return Mpv(onTime, onDuration, onPause, onEnd, onFileLoaded)
        }
    }
}

/** Десять полос эквалайзера, Гц: те же, что на телефоне. */
private val EQ_FREQS = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

/**
 * Цепочка фильтров mpv для звучания трека: пропуск тишины, выравнивание громкости (по данным
 * YouTube), тон (rubberband), эквалайзер, реверб. Скорость — не фильтр, а свойство `speed`.
 * Общая для обоих движков, чтобы они не разошлись.
 */
fun audioFilterChain(profile: SoundProfile, gainDb: Double?, skipSilence: Boolean, eqBands: List<Int>?): String {
    val filters = mutableListOf<String>()
    if (skipSilence) filters += "lavfi=[silenceremove=start_periods=1:start_silence=0.1:start_threshold=-50dB]"
    if (gainDb != null) filters += "lavfi=[volume=${"%.2f".format(java.util.Locale.ROOT, gainDb)}dB]"
    if (profile.pitch != 1f) filters += "rubberband=pitch-scale=${profile.pitch}"
    if (eqBands != null) {
        val active = EQ_FREQS.zip(eqBands).filter { it.second != 0 }
        if (active.isNotEmpty()) filters += "lavfi=[" + active.joinToString(",") { (f, g) -> "equalizer=f=$f:t=o:w=1:g=$g" } + "]"
    }
    profile.reverb.filter?.let { filters += "lavfi=[$it]" }
    return filters.joinToString(",")
}
