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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: StatsRepository,
) : ViewModel() {
    private val _stats = MutableStateFlow(ListeningStats())
    val stats: StateFlow<ListeningStats> = _stats.asStateFlow()

    init {
        setPeriod(StatsPeriod.WEEK)
    }

    fun setPeriod(period: StatsPeriod) {
        viewModelScope.launch { _stats.value = repository.stats(period) }
    }

    /** Стирает счётчики и пересчитывает экран; записи «недавнего» остаются. */
    fun reset() {
        val period = _stats.value.period
        viewModelScope.launch {
            repository.reset()
            _stats.value = repository.stats(period)
        }
    }
}
