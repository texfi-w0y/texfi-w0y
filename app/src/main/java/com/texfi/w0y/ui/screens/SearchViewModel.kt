package com.texfi.w0y.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.texfi.w0y.R
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.data.DebugInfoCollector
import com.texfi.w0y.data.Diagnostics
import com.texfi.w0y.data.NetworkInfo
import com.texfi.w0y.data.SearchError
import com.texfi.w0y.data.SearchErrors
import com.texfi.w0y.data.ArtistCard
import com.texfi.w0y.data.LibraryRepository
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.SearchHistoryLogic
import com.texfi.w0y.data.SearchHistoryRepository
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.PlaybackStarter
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Раздел выдачи.
 *
 * «Всё» — один запрос без фильтра, в ответе сразу треки, альбомы и
 * артисты. Именно он и стоит первым: три отдельные вкладки означали три
 * ожидания там, где человек просто хотел что-то найти.
 */
enum class SearchFilter(@StringRes val label: Int) {
    ALL(R.string.search_filter_all),
    SONGS(R.string.search_filter_songs),
    VIDEOS(R.string.search_filter_videos),
    ALBUMS(R.string.search_filter_albums),
    ARTISTS(R.string.search_filter_artists),
}

sealed interface SearchState {
    data object Idle : SearchState

    data object Loading : SearchState

    data class Results(
        val songs: List<SongItem> = emptyList(),
        val albums: List<PlaylistCard> = emptyList(),
        val artists: List<ArtistCard> = emptyList(),
        /** Строки — видео: рисуются с широким кадром, как в YouTube Music. */
        val videos: Boolean = false,
    ) : SearchState {
        val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
    }

    /** Запрос прошёл, но ничего не нашёл: не ошибка. */
    data object Empty : SearchState

    /** Запрос не удался: причина типизирована, пустой результат сюда не попадает. */
    data class Error(val error: SearchError) : SearchState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: YouTubeRepository,
    private val diagnostics: Diagnostics,
    private val debugInfo: DebugInfoCollector,
    private val networkInfo: NetworkInfo,
    private val library: LibraryRepository,
    private val downloads: DownloadsRepository,
    private val history: SearchHistoryRepository,
    settingsRepository: SettingsRepository,
    private val playback: PlaybackStarter,
    val player: PlayerConnection,
) : ViewModel() {
    private val _query = MutableStateFlow("")

    /**
     * Запросы, отправленные без паузы: нажатие «искать» на клавиатуре и
     * выбор подсказки. Ждать ещё треть секунды после явного действия —
     * ровно та задержка, которую читают как «приложение тормозит».
     */
    private val submitted = MutableSharedFlow<String>(replay = 1)

    /** Прятать ли записи с меткой «E» — решает настройка «без мата». */
    val hideExplicit: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.hideExplicit }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

    /** Недавние запросы: и подсказка под пустым полем, и сигнал рекомендациям. */
    val recentQueries: StateFlow<List<String>> =
        history.recent.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )
    val playlists: StateFlow<List<PlaylistEntity>> =
        library.editablePlaylists.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    fun download(song: SongItem) = downloads.download(song)

    fun playNext(song: SongItem) = player.playNext(song)

    fun enqueue(song: SongItem) = player.enqueue(song)

    fun addToPlaylist(playlistId: Long, song: SongItem) =
        viewModelScope.launch { library.addToPlaylist(playlistId, song) }

    fun createPlaylistWith(name: String, song: SongItem) =
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            library.addToPlaylist(id, song)
        }

    /**
     * Подсказки YouTube по тому, что набрано.
     *
     * Своих не придумываем: угадать название трека по трём буквам может
     * только тот, у кого есть индекс. Пауза короче, чем перед самим
     * поиском: подсказка нужна во время набора, а не после него.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<String>> =
        combine(
            _query.debounce(SUGGEST_DEBOUNCE_MS).distinctUntilChanged(),
            settingsRepository.settings.map { it.searchSuggestions }.distinctUntilChanged(),
        ) { query, enabled -> query to enabled }
            .flatMapLatest { (query, enabled) ->
                flow {
                    if (!enabled || query.trim().length < SUGGEST_MIN_CHARS) {
                        emit(emptyList())
                        return@flow
                    }
                    emit(
                        runCatching { repository.searchSuggestions(query.trim()) }
                            .getOrDefault(emptyList()),
                    )
                }
            }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Совпадения в своей библиотеке.
     *
     * Чаще всего ищут то, что уже слушали, и ждать ради этого ответа
     * YouTube незачем: локальная выборка приходит до сетевой.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val localResults: StateFlow<List<SongItem>> =
        _query
            .debounce(LOCAL_DEBOUNCE_MS)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                flow { emit(library.searchLocal(query)) }
            }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _diagnosis = MutableStateFlow<String?>(null)
    val diagnosis: StateFlow<String?> = _diagnosis.asStateFlow()
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** Счётчик ручных повторов: запрос с теми же словами обходит distinctUntilChanged. */
    private val retryTick = MutableStateFlow(0)

    private val _filter = MutableStateFlow(SearchFilter.SONGS)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    /**
     * Готовые ответы по паре «запрос + раздел».
     *
     * Переключение вкладок туда-обратно не должно ходить в сеть: выдача за
     * секунду не меняется, а ожидание там, где уже всё показывали, — ровно
     * то ощущение медленного поиска, от которого уходили.
     */
    private val cache =
        object : LinkedHashMap<Pair<String, SearchFilter>, SearchState.Results>(
            CACHE_SIZE,
            0.75f,
            true,
        ) {
            // Без предела кэш растёт на каждый набранный запрос и держит
            // в памяти всю выдачу за сессию. Переключение вкладок — это
            // максимум четыре записи на запрос, так что предел щедрый.
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Pair<String, SearchFilter>, SearchState.Results>?,
            ): Boolean = size > CACHE_SIZE
        }

    init {
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        combine(
            // Пауза в наборе, а не запрос на каждую букву; явная отправка
            // идёт мимо паузы.
            merge(_query.debounce(SEARCH_DEBOUNCE_MS), submitted).distinctUntilChanged(),
            _filter,
            retryTick,
        ) { query, filter, _ -> query to filter }
            // flatMapLatest отменяет устаревший запрос: пользователь видит
            // результат по тому, что набрано сейчас, а не три буквы назад.
            .flatMapLatest { (query, filter) ->
                flow {
                    if (query.isBlank()) {
                        emit(SearchState.Idle)
                        return@flow
                    }
                    cache[query to filter]?.let {
                        emit(it)
                        return@flow
                    }
                    emit(SearchState.Loading)
                    emit(runSearch(query, filter))
                }
            }.onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    /**
     * Один поиск до конца: успех, «ничего не найдено» или типизированная ошибка.
     * Отмена не превращается в ошибку: её просто пропускаем дальше.
     */
    private suspend fun runSearch(query: String, filter: SearchFilter): SearchState =
        try {
            val results =
                when (filter) {
                    SearchFilter.ALL ->
                        repository.searchEverything(query).let {
                            SearchState.Results(songs = it.songs, albums = it.albums, artists = it.artists)
                        }

                    SearchFilter.SONGS -> SearchState.Results(songs = repository.searchSongs(query))
                    SearchFilter.VIDEOS -> SearchState.Results(songs = repository.searchVideos(query), videos = true)
                    SearchFilter.ALBUMS -> SearchState.Results(albums = repository.searchAlbums(query))
                    SearchFilter.ARTISTS -> SearchState.Results(artists = repository.searchArtists(query))
                }
            if (results.isEmpty) {
                SearchState.Empty
            } else {
                cache[query to filter] = results
                results
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) SearchState.Error(SearchError.Timeout) else throw e
        } catch (e: Throwable) {
            val error = SearchErrors.classify(e, online = networkInfo.isOnline())
            Timber.w("Поиск не удался: ${error.tag}")
            SearchState.Error(error)
        }

    /** Текст для буфера обмена: версия, устройство, сеть, журнал. Без запросов и аккаунта. */
    suspend fun debugInfoText(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { debugInfo.collect() }

    fun onFilterChange(value: SearchFilter) {
        _filter.value = value
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Искать немедленно — по нажатию «искать» или по выбору подсказки. */
    fun submit(value: String = _query.value) {
        val clean = SearchHistoryLogic.pick(value)
        if (clean.isEmpty()) return
        _query.value = clean
        // История пишется только по явному подтверждению — Enter, подсказка
        // или запись из истории. Раньше запрос запоминался сам, по паузе в
        // наборе, и в историю попадали обрывки вроде «ра», «рад», «радио».
        viewModelScope.launch { history.remember(clean) }
        viewModelScope.launch { submitted.emit(clean) }
    }

    /** Три пробных запроса мимо библиотеки — видно, что именно не нравится YouTube. */
    fun diagnose() {
        viewModelScope.launch {
            _diagnosis.value = context.getString(R.string.search_checking)
            _diagnosis.value = runCatching { diagnostics.run(_query.value.ifBlank { context.getString(R.string.search_query_fallback) }) }
                .getOrElse { context.getString(R.string.search_diagnostics_failed, it.message.orEmpty()) }
        }
    }

    /** Повтор того же запроса: сеть на телефоне отваливается чаще, чем код. */
    fun retry() {
        cache.remove(_query.value to _filter.value)
        retryTick.value += 1
    }

    fun playFrom(songs: List<SongItem>, index: Int) = playback.play(songs, index)

    fun clearHistory() = viewModelScope.launch { history.clear() }

    fun forgetQuery(query: String) = viewModelScope.launch { history.remove(query) }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 280L
        const val SUGGEST_DEBOUNCE_MS = 160L
        const val LOCAL_DEBOUNCE_MS = 100L
        const val SUGGEST_MIN_CHARS = 2
        const val CACHE_SIZE = 40
    }
}
