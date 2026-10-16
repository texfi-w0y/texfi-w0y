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
) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.WEEK)

    /** Пересчитывается сама: при смене периода и при каждой записи в историю. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val stats: StateFlow<ListeningStats> =
        period
            .flatMapLatest { repository.observe(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeningStats())

    fun setPeriod(value: StatsPeriod) {
        period.value = value
    }

    /** Стирает счётчики и пересчитывает экран; записи «недавнего» остаются. */
    fun reset() {
        viewModelScope.launch { repository.reset() }
    }
}
