package com.texfi.w0y.playback

import com.texfi.w0y.data.SongItem

/**
 * Жест «потянуть мини-плеер вниз — убрать»: пороги и состояние отката.
 *
 * Без Android и без Compose, чтобы поведение проверял обычный тест:
 * экран только ведёт палец и вызывает отсюда решения.
 */
object MiniDismiss {
    /** Доля высоты, после которой отпускание убирает плеер. */
    const val FRACTION = 0.4f

    /** Скорость броска, px/с, которой хватает и на меньший путь. */
    const val FLING_PX_S = 1_400f

    /** Минимальный путь для броска: случайный щелчок пальцем не считается. */
    private const val FLING_MIN_FRACTION = 0.1f

    /** Сколько после скрытия висит «Отменить», мс. */
    const val UNDO_MS = 4_000L

    fun shouldDismiss(dragPx: Float, heightPx: Float, velocityPxPerSec: Float): Boolean {
        if (heightPx <= 0f || dragPx <= 0f) return false
        return dragPx >= heightPx * FRACTION ||
            (dragPx >= heightPx * FLING_MIN_FRACTION && velocityPxPerSec >= FLING_PX_S)
    }

    /** Прозрачность во время перетаскивания: чем дальше палец, тем бледнее, но не до нуля. */
    fun alpha(dragPx: Float, heightPx: Float): Float {
        if (heightPx <= 0f) return 1f
        return 1f - 0.7f * (dragPx / heightPx).coerceIn(0f, 1f)
    }
}

/** Очередь на момент скрытия: по ней плеер возвращается тем же, что был. */
data class QueueSnapshot(
    val songs: List<SongItem>,
    val index: Int,
    val positionMs: Long,
    val wasPlaying: Boolean,
)

/** То, что скрытию нужно от плеера; в приложении это MediaController, в тесте — заглушка. */
interface DismissPort {
    fun snapshot(): QueueSnapshot?

    fun pause()

    /** Остановить воспроизведение и очистить очередь: уходит уведомление и передний план сервиса. */
    fun stopAndClear()

    fun restore(snapshot: QueueSnapshot, play: Boolean)
}

/**
 * Скрыть плеер и вернуть его. Очередь и позиция лежат в [pending], пока
 * не прошло окно «Отменить» ([MiniDismiss.UNDO_MS]); после [commit] они
 * забываются, и сохранённая очередь на диске тоже снимается.
 */
class PlayerDismisser(private val port: DismissPort) {
    var pending: QueueSnapshot? = null
        private set

    fun dismiss(): Boolean {
        val snap = port.snapshot() ?: return false
        pending = snap
        port.pause()
        port.stopAndClear()
        return true
    }

    fun undo(): Boolean {
        val snap = pending ?: return false
        pending = null
        port.restore(snap, snap.wasPlaying)
        return true
    }

    fun commit() {
        pending = null
    }
}
