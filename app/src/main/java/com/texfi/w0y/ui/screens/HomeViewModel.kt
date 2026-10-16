package com.texfi.w0y.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.Shelf
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.StartupMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Рекомендации на главной.
 *
 * Отдельно от локальной библиотеки: это единственное на главном экране,
 * что ходит в сеть, и падать вместе с ней остальная главная не должна.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val youtube: YouTubeRepository,
    private val playback: PlaybackStarter,
    private val settings: SettingsRepository,
    private val recs: com.texfi.w0y.recs.RecEngine,
    private val library: com.texfi.w0y.data.LibraryRepository,
    startupMetrics: StartupMetrics,
) : ViewModel() {
    /**
     * Среднее время «нажал → пошёл звук».
     *
     * Показывается прямо под названием на главной: главное обещание
     * приложения стоит держать на виду, а не прятать в настройках —
     * и его всегда можно проверить.
     */
    val startupAverage: StateFlow<Long?> = startupMetrics.average

    /** Ленты можно выключить совсем — кому-то нужна только своя библиотека. */
    val showRecommendations: StateFlow<Boolean> =
        settings.settings
            .map { it.showRecommendations }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val seasonMode: StateFlow<com.texfi.w0y.data.SeasonMode> =
        settings.settings
            .map { it.seasonMode }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.texfi.w0y.data.SeasonMode.AUTO)

    private val _shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelves: StateFlow<List<Shelf>> = _shelves.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private val _blocks = MutableStateFlow(com.texfi.w0y.recs.HomeBlocks())

    /** Блоки подборок: открытия, релизы твоих исполнителей, забытое, время суток. */
    val blocks: StateFlow<com.texfi.w0y.recs.HomeBlocks> = _blocks.asStateFlow()

    init {
        refresh()
        loadBlocks(force = false)
    }

    fun loadBlocks(force: Boolean) {
        viewModelScope.launch {
            val loaded = runCatching { recs.homeBlocks(force) }.onFailure { Timber.w(it, "Блоки подборок не собрались") }.getOrNull() ?: return@launch
            _blocks.value = loaded
            weeklyPlaylist(loaded.discoveries)
        }
    }

    /** Раз в неделю, если включено: свои «Открытия» складываются в обычный локальный плейлист. */
    private suspend fun weeklyPlaylist(found: List<com.texfi.w0y.recs.Rec>) {
        if (found.size < MIN_WEEKLY || !settings.settings.first().weeklyDiscovery) return
        val cal = java.util.Calendar.getInstance()
        val week = "%d-W%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.WEEK_OF_YEAR))
        if (settings.lastDiscoveryWeek() == week) return
        val id = library.createPlaylist("OPEN · $week")
        found.forEach { library.addToPlaylist(id, it.song) }
        settings.setLastDiscoveryWeek(week)
    }

    fun playRecs(items: List<com.texfi.w0y.recs.Rec>, index: Int) = playback.play(items.map { it.song }, index)

    fun refresh() {
        if (_loading.value) return
        loadBlocks(force = true)
        viewModelScope.launch {
            _loading.value = true
            _failed.value = false
            runCatching { youtube.home() }
                .onSuccess { loaded ->
                    val hide = settings.settings.first().hideExplicit
                    val shelves =
                        if (hide) {
                            loaded.map { shelf -> shelf.copy(songs = shelf.songs.filterNot { it.explicit }) }
                        } else {
                            loaded
                        }
                    // Лент на главной у YouTube бывает под два десятка —
                    // это ровно та перегруженность, от которой уходили.
                    _shelves.value =
                        shelves
                            .filterNot { isSeasonalPromo(it.title) }
                            .filter { it.songs.size + it.cards.size + it.artists.size >= 2 }
                            .take(SHELVES)
                }.onFailure {
                    Timber.w(it, "Рекомендации не пришли")
                    _failed.value = true
                }
            _loading.value = false
        }
    }

    fun play(songs: List<SongItem>, index: Int) = playback.play(songs, index)

    private companion object {
        const val SHELVES = 5
        const val MIN_WEEKLY = 4

        /**
         * Сезонные промо-ленты YouTube («The sound of autumn» и подобные) —
         * реклама подборок, а не рекомендации по вкусу; на главной их нет.
         */
        val SEASONAL = Regex("^the sounds? of (autumn|fall|winter|spring|summer)\\b", RegexOption.IGNORE_CASE)

        fun isSeasonalPromo(title: String) = SEASONAL.containsMatchIn(title.trim())
    }
}
