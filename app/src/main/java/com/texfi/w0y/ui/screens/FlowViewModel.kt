package com.texfi.w0y.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.recs.FlowChoice
import com.texfi.w0y.recs.FlowEngine
import com.texfi.w0y.recs.FlowGenre
import com.texfi.w0y.recs.FlowMood
import com.texfi.w0y.recs.FlowPreset
import com.texfi.w0y.recs.FlowSet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Что показывает fl0w под настройками. */
sealed interface FlowResult {
    data object Idle : FlowResult

    data object Loading : FlowResult

    data class Ready(val set: FlowSet) : FlowResult

    /** Ничего не собралось: ни сети, ни своего. */
    data object Empty : FlowResult
}

@HiltViewModel
class FlowViewModel @Inject constructor(
    private val engine: FlowEngine,
    private val settings: SettingsRepository,
    private val playback: PlaybackStarter,
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

    private var building: Job? = null

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

    fun setMood(mood: FlowMood) {
        _choice.value = _choice.value.let { it.copy(mood = if (it.mood == mood) null else mood) }
    }

    fun toggleGenre(genre: FlowGenre) {
        _choice.value = _choice.value.let { c -> c.copy(genres = if (genre in c.genres) c.genres - genre else c.genres + genre) }
    }

    fun setNovelty(value: Int) {
        _choice.value = _choice.value.copy(novelty = value.coerceIn(0, 100))
    }

    fun applyPreset(preset: FlowPreset) {
        _choice.value = preset.choice
    }

    /** Собрать и сразу включить: ради этого режим и открывают. */
    fun start(russian: Boolean) {
        val c = _choice.value
        building?.cancel()
        building =
            viewModelScope.launch {
                settings.setFlowChoice(c.mood?.name.orEmpty(), c.encodeGenres(), c.novelty)
                _result.value = FlowResult.Loading
                val set = runCatching { engine.build(c, russian) }.getOrNull()
                if (set == null || set.items.isEmpty()) {
                    _result.value = FlowResult.Empty
                    return@launch
                }
                _result.value = FlowResult.Ready(set)
                playback.play(set.items.map { it.song }, 0)
            }
    }

    fun playFrom(index: Int) {
        val ready = _result.value as? FlowResult.Ready ?: return
        playback.play(ready.set.items.map { it.song }, index)
    }
}
