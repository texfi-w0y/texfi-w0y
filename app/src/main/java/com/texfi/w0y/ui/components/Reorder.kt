package com.texfi.w0y.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Перетаскивание строк в ленивом списке — без сторонней библиотеки.
 *
 * Тянется строка за ручку; когда её середина заходит на соседнюю строку,
 * они меняются местами ([onMove] с ключами), и сдвиг пересчитывается от
 * нового места — палец и строка не расходятся. У края списка он сам
 * прокручивается. Порядок сохраняется один раз — когда строку отпустили.
 */
@Stable
class ReorderState(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (from: Any, to: Any) -> Unit,
    private val onSwap: () -> Unit,
    /** Ряд вместо столбца: тянется по горизонтали (плейлисты на «Главной»). */
    val horizontal: Boolean = false,
) {
    var dragging by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(key: Any) {
        dragging = key
        offset = 0f
    }

    fun drag(delta: Float) {
        val key = dragging ?: return
        offset += delta
        val items = list.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        val center = current.offset + offset + current.size / 2f
        val target =
            items.firstOrNull { item ->
                item.key != key && item.contentType == REORDER_TYPE && center > item.offset && center < item.offset + item.size
            }
        if (target != null) {
            onMove(key, target.key)
            offset -= (target.offset - current.offset)
            onSwap()
        }
        // У края — подкрутить список, иначе длинный плейлист не перетащить.
        val info = list.layoutInfo
        val top = current.offset + offset
        val bottom = top + current.size
        val edge = EDGE_PX
        when {
            top < info.viewportStartOffset + edge && delta < 0 -> scope.launch { list.scrollBy(-STEP_PX) }
            bottom > info.viewportEndOffset - edge && delta > 0 -> scope.launch { list.scrollBy(STEP_PX) }
        }
    }

    fun end(): Boolean {
        val was = dragging != null
        dragging = null
        offset = 0f
        return was
    }

    /** Модификатор строки: перетаскиваемая — поверх остальных и сдвинута за пальцем. */
    fun Modifier.reorderable(key: Any): Modifier =
        if (dragging == key) {
            zIndex(1f).graphicsLayer {
                if (horizontal) translationX = offset else translationY = offset
                scaleX = 1.04f
                scaleY = 1.04f
            }
        } else {
            this
        }

    companion object {
        /** Тип строк, которые можно переставлять, — шапка и подписи не участвуют. */
        const val REORDER_TYPE = "reorder-row"
        private const val EDGE_PX = 96
        private const val STEP_PX = 24f
    }
}

@Composable
fun rememberReorderState(list: LazyListState, horizontal: Boolean = false, onMove: (Any, Any) -> Unit): ReorderState {
    val scope = rememberCoroutineScope()
    val tap = rememberTapHaptic()
    // Свежая лямбда на каждой перекомпоновке: она меняет список, который
    // мог пересоздаться, а состояние перетаскивания живёт дольше.
    val move by androidx.compose.runtime.rememberUpdatedState(onMove)
    return androidx.compose.runtime.remember(list) { ReorderState(list, scope, { a, b -> move(a, b) }, tap, horizontal) }
}

/** Ручка строки: 48 dp касания; перенос — только за неё, прокрутка списка и касание строки его не начинают. */
@Composable
fun ReorderHandle(state: ReorderState, key: Any, onDrop: () -> Unit) {
    val colors = LocalW0yColors.current
    val haptic = rememberHaptics()
    Box(
        Modifier
            .size(48.dp)
            .holdToDrag(
                key = key,
                onStart = {
                    haptic(Buzz.ON)
                    state.start(key)
                },
                onDrag = { dy -> state.drag(dy) },
                onEnd = { if (state.end()) onDrop() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        PixelSprite(Sprites.grip, if (state.dragging == key) colors.accent else colors.textMuted, Modifier.size(18.dp))
    }
}

/**
 * Перенос за ручку: ведёшь пальцем по ручке — строка берётся, как только палец прошёл порог касания.
 *
 * Раньше требовалось ещё и удержание 250 мс: пока палец стоял, малейший сдвиг уже подхватывал
 * прокрутка списка, и перенос не начинался вовсе — «музыку в плейлисте не переместить». Защита от
 * случайного переноса осталась: тянуть можно только за ручку, а не за строку.
 */
fun Modifier.holdToDrag(key: Any, onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(key) {
        detectDragGestures(
            onDragStart = { onStart() },
            onDragEnd = { onEnd() },
            onDragCancel = { onEnd() },
        ) { change, amount ->
            change.consume()
            onDrag(amount.y)
        }
    }

/**
 * Перенос без ручки: удержать элемент и вести пальцем. Отпустили, не сдвинув, — это просто
 * долгое нажатие ([onHold], например меню). Короткое касание не трогается — его ловит
 * обычный clickable; поэтому модификатор ставится в цепочке после него: так он первым видит
 * отпускание после переноса и гасит его, и касание не срабатывает следом.
 */
@Composable
fun Modifier.holdToReorder(state: ReorderState, key: Any, onHold: (() -> Unit)? = null, onDrop: () -> Unit): Modifier {
    val haptic = rememberHaptics()
    val hold by androidx.compose.runtime.rememberUpdatedState(onHold)
    val drop by androidx.compose.runtime.rememberUpdatedState(onDrop)
    return pointerInput(key, state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            haptic(Buzz.ON)
            state.start(key)
            var moved = 0f
            drag(long.id) { change ->
                val delta = change.positionChange()
                val along = if (state.horizontal) delta.x else delta.y
                moved += kotlin.math.abs(along)
                state.drag(along)
                change.consume()
            }
            currentEvent.changes.forEach { it.consume() }
            val was = state.end()
            if (moved < viewConfiguration.touchSlop) hold?.invoke() else if (was) drop()
        }
    }
}

