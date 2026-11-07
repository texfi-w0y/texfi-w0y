package com.texfi.w0y.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Таймер сна — общий для экрана и сервиса плеера.
 *
 * Отсчёт жил в экране и ставил паузу через связь с сервисом. В фоне эта связь
 * обрывается, и пауза уходила в никуда: таймер «выставлен, а музыка играет».
 * Теперь экран только задаёт срок, а останавливает сам сервис — тот же процесс,
 * тот же плеер. Срок — по часам [android.os.SystemClock.elapsedRealtime], а не
 * накопленными секундами: задержки в фоне его не растягивают.
 */
@Singleton
class SleepClock @Inject constructor() {
    private val _deadline = MutableStateFlow<Long?>(null)

    /** Когда остановить (elapsedRealtime, мс); null — таймера нет. */
    val deadline: StateFlow<Long?> = _deadline.asStateFlow()

    private val _afterTrack = MutableStateFlow(false)

    /** Остановить, когда доиграет текущий трек. */
    val afterTrack: StateFlow<Boolean> = _afterTrack.asStateFlow()

    fun start(minutes: Int, now: Long = android.os.SystemClock.elapsedRealtime()) {
        _afterTrack.value = false
        _deadline.value = now + minutes * 60_000L
    }

    fun startAfterTrack() {
        _deadline.value = null
        _afterTrack.value = true
    }

    fun cancel() {
        _deadline.value = null
        _afterTrack.value = false
    }

    /** Сработал: сервис поставил паузу. */
    fun fired() = cancel()
}
