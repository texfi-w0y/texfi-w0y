package com.texfi.w0y.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Включена ли защита от случайного удаления. Раздаёт оболочка из настроек:
 * при выключении остаётся подтверждение только у удаления файла с телефона.
 */
val LocalDeleteProtection = androidx.compose.runtime.compositionLocalOf { true }

/**
 * Плашка «Удалено · Вернуть» на ~6 секунд. Чистая логика: тест проверяет,
 * что вернуть можно один раз и что новая плашка заменяет прежнюю.
 */
class UndoController {
    data class Item(val id: Long, val message: String, val onUndo: () -> Unit)

    private val _current = MutableStateFlow<Item?>(null)
    val current: StateFlow<Item?> = _current.asStateFlow()
    private var counter = 0L

    fun post(message: String, onUndo: () -> Unit) {
        _current.value = Item(++counter, message, onUndo)
    }

    /** Возвращает удалённое; повторный вызов ничего не делает. */
    fun undo() {
        val item = _current.value ?: return
        _current.value = null
        item.onUndo()
    }

    fun dismiss(id: Long? = null) {
        val item = _current.value ?: return
        if (id == null || item.id == id) _current.value = null
    }

    companion object {
        const val VISIBLE_MS = 6_000L
    }
}

val LocalUndo = androidx.compose.runtime.staticCompositionLocalOf<UndoController?> { null }

/**
 * Действие над строкой в меню долгого нажатия. [danger] — с подтверждением
 * (пока защита включена); [onUndo] задан — после действия появляется плашка
 * с возвратом, и подтверждение не нужно.
 */
class RowAction(
    val label: String,
    val danger: Boolean = false,
    val confirmTitle: String? = null,
    val confirmText: String? = null,
    val confirmButton: String? = null,
    val undoMessage: String? = null,
    val onUndo: (() -> Unit)? = null,
    /** Удаление файла с устройства: подтверждается всегда, даже при выключенной защите. */
    val alwaysConfirm: Boolean = false,
    val run: () -> Unit,
)

/** Нужно ли подтверждение для действия при текущей настройке защиты. */
fun RowAction.needsConfirm(protection: Boolean): Boolean = (danger && protection) || alwaysConfirm

/**
 * Меню действий над строкой по долгому нажатию: Hairline-карточка с крупными
 * кнопками. Опасное действие сначала спрашивает подтверждение с названием.
 */
@Composable
fun RowActionsMenu(
    title: String,
    actions: List<RowAction>,
    cancel: String,
    onDismiss: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val protection = LocalDeleteProtection.current
    val undo = LocalUndo.current
    var pending by remember { mutableStateOf<RowAction?>(null) }

    fun perform(action: RowAction) {
        action.run()
        val restore = action.onUndo
        val message = action.undoMessage
        if (restore != null && message != null) undo?.post(message, restore)
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val confirm = pending
        if (confirm != null) {
            ConfirmPanel(
                title = confirm.confirmTitle ?: title,
                text = confirm.confirmText ?: confirm.label,
                confirm = confirm.confirmButton ?: confirm.label,
                dismiss = cancel,
                onConfirm = { perform(confirm) },
                onDismiss = { pending = null },
            )
        } else {
            PixelCard(label = title, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    actions.forEach { action ->
                        PixelButton(
                            text = action.label,
                            onClick = { if (action.needsConfirm(protection)) pending = action else perform(action) },
                            fill = if (action.danger) colors.surfaceHigh else null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = action.label },
                        )
                    }
                    PixelButton(
                        text = cancel,
                        onClick = onDismiss,
                        fill = colors.surfaceHigh,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}


/** Подтверждение в отдельном окне: годится там, где панель не может занять весь экран (внутри списка). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ConfirmPanel(title = title, text = text, confirm = confirm, dismiss = dismiss, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}
