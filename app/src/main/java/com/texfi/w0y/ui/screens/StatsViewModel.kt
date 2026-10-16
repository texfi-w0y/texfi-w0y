package com.texfi.w0y.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.ListeningStats
import com.texfi.w0y.data.StatsPeriod
import com.texfi.w0y.data.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: StatsRepository,
    private val recapCards: com.texfi.w0y.share.RecapCardRenderer,
) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.WEEK)

    /** Пересчитывается сама: при смене периода и при каждой записи в историю. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val stats: StateFlow<ListeningStats> =
        period
            .flatMapLatest { repository.observe(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeningStats())

    private val _recap = MutableStateFlow<com.texfi.w0y.data.YearRecap?>(null)

    /** Итоги года: считаются, когда их открыли, а не при каждом входе в статистику. */
    val recap: StateFlow<com.texfi.w0y.data.YearRecap?> = _recap

    fun loadRecap() {
        viewModelScope.launch { _recap.value = repository.yearRecap() }
    }

    private val _recapShare = MutableStateFlow<java.io.File?>(null)
    val recapShare: StateFlow<java.io.File?> = _recapShare

    fun shareRecap() {
        val recap = _recap.value ?: return
        viewModelScope.launch {
            _recapShare.value =
                runCatching { recapCards.render(recap) }
                    .onFailure { timber.log.Timber.w(it, "Карточка итогов не нарисовалась") }
                    .getOrNull()
        }
    }

    fun consumeRecapShare() {
        _recapShare.value = null
    }

    fun setPeriod(value: StatsPeriod) {
        period.value = value
    }

    /** Стирает счётчики и пересчитывает экран; записи «недавнего» остаются. */
    fun reset() {
        viewModelScope.launch { repository.reset() }
    }
}
