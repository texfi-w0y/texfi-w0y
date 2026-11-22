package com.texfi.w0y.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.PlayerConnection
import com.texfi.w0y.playback.SpectrumBus
import com.texfi.w0y.recs.FlowChoice
import com.texfi.w0y.recs.FlowEngine
import com.texfi.w0y.recs.FlowGenre
import com.texfi.w0y.recs.FlowMood
import com.texfi.w0y.recs.FlowSet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Что показывает fl0w под кнопкой. */
sealed interface FlowResult {
    data object Idle : FlowResult

    data object Loading : FlowResult

    data class Ready(val set: FlowSet) : FlowResult

    /** Ничего не собралось: ни сети, ни своего. */
    data object Empty : FlowResult
}

/** Настройки fl0w: что никогда не показывать, волна на фоне, длина очереди. */
data class FlowPrefs(
    val hiddenGenres: Set<FlowGenre> = emptySet(),
    val hiddenMoods: Set<FlowMood> = emptySet(),
    val visualizer: Boolean = true,
    val length: Int = 40,
)

@HiltViewModel
class FlowViewModel @Inject constructor(
    private val engine: FlowEngine,
    private val settings: SettingsRepository,
    private val playback: PlaybackStarter,
    private val player: PlayerConnection,
    val spectrum: SpectrumBus,
) : ViewModel() {
    private val _choice = MutableStateFlow(FlowChoice())
    val choice: StateFlow<FlowChoice> = _choice.asStateFlow()

    /** null — ещё не прочитано: шторка не решает, показывать ли вступление, пока не знает. */
    private val _introSeen = MutableStateFlow<Boolean?>(null)
    val introSeen: StateFlow<Boolean?> = _introSeen.asStateFlow()

    private val _covers = MutableStateFlow<List<String>>(emptyList())
    val covers: StateFlow<List<String>> = _covers.asStateFlow()

    private val _result = MutableStateFlow<FlowResult>(FlowResult.Idle)
    val result: StateFlow<FlowResult> = _result.asStateFlow()

    /** Выбор поменяли после запуска: играет прежнее, а кнопка уже собирает по новому. */
    private val dirty = MutableStateFlow(false)

    val prefs: StateFlow<FlowPrefs> =
        settings.settings
            .map { s ->
                FlowPrefs(
                    hiddenGenres = decode(s.flowHiddenGenres, FlowGenre.entries),
                    hiddenMoods = decode(s.flowHiddenMoods, FlowMood.entries),
                    visualizer = s.flowVisualizer,
                    length = s.flowLength,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, FlowPrefs())

    private var building: Job? = null

    /** Играет очередь fl0w (не важно, по какому выбору): тогда на фоне волна. */
    val sounding: StateFlow<Boolean> =
        combine(playback.flow, player.state) { flow, state -> flow != null && state.isPlaying }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Большая кнопка — пауза и продолжение, а не «собрать заново»: стоит трек fl0w и выбор с тех
     * пор не меняли. Первое — так ли это, второе — играет ли.
     */
    val own: StateFlow<Pair<Boolean, Boolean>> =
        combine(playback.flow, player.state, dirty) { flow, state, changed ->
            val mine = flow != null && !changed
            mine to (mine && state.isPlaying)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false to false)

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            _choice.value = FlowChoice.decode(s.flowMood, s.flowGenres, s.flowNovelty)
            _introSeen.value = s.flowIntroSeen
        }
    }

    fun loadCovers(russian: Boolean) {
        if (_covers.value.isNotEmpty()) return
        viewModelScope.launch { _covers.value = runCatching { engine.introCovers(russian) }.getOrDefault(emptyList()) }
    }

    fun markIntroSeen() {
        _introSeen.value = true
        viewModelScope.launch { settings.setFlowIntroSeen(true) }
    }

    /** null — «что угодно». */
    fun setMood(mood: FlowMood?) {
        _choice.value = _choice.value.copy(mood = mood)
        changed()
    }

    fun toggleGenre(genre: FlowGenre) {
        _choice.value = _choice.value.let { c -> c.copy(genres = if (genre in c.genres) c.genres - genre else c.genres + genre) }
        changed()
    }

    fun setNovelty(value: Int) {
        _choice.value = _choice.value.copy(novelty = value.coerceIn(0, 100))
        changed()
    }

    /** Скрыть жанр или вернуть. Скрытый сразу снимается и с выбора. */
    fun toggleHiddenGenre(genre: FlowGenre) {
        val p = prefs.value
        val hidden = if (genre in p.hiddenGenres) p.hiddenGenres - genre else p.hiddenGenres + genre
        if (genre in hidden && genre in _choice.value.genres) toggleGenre(genre)
        viewModelScope.launch { settings.setFlowHidden(encode(hidden), encode(p.hiddenMoods)) }
    }

    fun toggleHiddenMood(mood: FlowMood) {
        val p = prefs.value
        val hidden = if (mood in p.hiddenMoods) p.hiddenMoods - mood else p.hiddenMoods + mood
        if (mood in hidden && _choice.value.mood == mood) setMood(null)
        viewModelScope.launch { settings.setFlowHidden(encode(p.hiddenGenres), encode(hidden)) }
    }

    fun setVisualizer(on: Boolean) {
        viewModelScope.launch { settings.setFlowVisualizer(on) }
    }

    fun setLength(value: Int) {
        viewModelScope.launch { settings.setFlowLength(value) }
    }

    /** Собрать и сразу включить: ради этого режим и открывают. */
    fun start(russian: Boolean) {
        val c = _choice.value
        building?.cancel()
        building =
            viewModelScope.launch {
                settings.setFlowChoice(c.mood?.name.orEmpty(), c.encodeGenres(), c.novelty)
                _result.value = FlowResult.Loading
                val set = runCatching { engine.build(c, russian, limit = prefs.value.length) }.getOrNull()
                if (set == null || set.items.isEmpty()) {
                    _result.value = FlowResult.Empty
                    return@launch
                }
                _result.value = FlowResult.Ready(set)
                dirty.value = false
                playback.playFlow(set.items.map { it.song }, c, russian)
            }
    }

    /** Большая кнопка: своя очередь стоит — пауза или продолжить; иначе собрать по выбору и включить. */
    fun onPlay(russian: Boolean) {
        if (own.value.first) player.togglePlayPause() else start(russian)
    }

    /** Выбор поменяли — прежняя подборка ему уже не отвечает, кнопка снова собирает. */
    private fun changed() {
        if (_result.value is FlowResult.Ready) _result.value = FlowResult.Idle
        dirty.value = true
    }

    private companion object {
        fun <E : Enum<E>> decode(raw: String, all: List<E>): Set<E> =
            raw.split(',').mapNotNull { n -> all.firstOrNull { it.name == n } }.toSet()

        fun encode(set: Set<Enum<*>>): String = set.joinToString(",") { it.name }
    }
}
