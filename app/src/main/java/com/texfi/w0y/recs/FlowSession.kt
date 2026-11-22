package com.texfi.w0y.recs

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Что сейчас собрал fl0w: по какому выбору и какие треки — чтобы плеер узнал свою очередь. */
data class FlowNow(val choice: FlowChoice, val russian: Boolean, val ids: Set<String>)

/**
 * Идёт ли fl0w. Ставит запуск fl0w, снимает любой другой запуск. Плеер и мини-плеер смотрят
 * сюда вместе с играющим треком: fl0w — это когда играет трек из [FlowNow.ids].
 */
@Singleton
class FlowSession @Inject constructor() {
    private val _now = MutableStateFlow<FlowNow?>(null)
    val now: StateFlow<FlowNow?> = _now.asStateFlow()

    fun start(now: FlowNow) {
        _now.value = now
    }

    fun extend(ids: Collection<String>) {
        _now.value = _now.value?.let { it.copy(ids = it.ids + ids) }
    }

    fun stop() {
        _now.value = null
    }
}
