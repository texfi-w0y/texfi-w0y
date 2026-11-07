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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
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

    if (com.texfi.w0y.ui.theme.isHair) {
        // Hairline: то же меню нижней панелью — с магнитом и анимацией, как все панели. Окно
        // нужно, потому что меню зовут из строки списка; своё затемнение окна выключено.
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            androidx.compose.runtime.SideEffect { window?.setDimAmount(0f) }
            // Окно меню лежит поверх острова — отступ под остров тут не нужен.
            androidx.compose.runtime.CompositionLocalProvider(LocalBottomInset provides 0.dp) {
                HairRowActionsSheet(title, actions, cancel, protection, onPerform = ::perform, onDismiss = onDismiss)
            }
        }
        return
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

/**
 * Меню строки в Hairline: заголовок, действия строками, опасное — с подтверждением прямо
 * в панели, без второго окна.
 */
@Composable
private fun HairRowActionsSheet(
    title: String,
    actions: List<RowAction>,
    cancel: String,
    protection: Boolean,
    onPerform: (RowAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalW0yColors.current
    var shown by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<RowAction?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun close(then: () -> Unit) {
        shown = false
        scope.launch {
            kotlinx.coroutines.delay(com.texfi.w0y.ui.theme.HairTokens.MID_MS.toLong())
            then()
        }
    }
    val danger = androidx.compose.ui.graphics.Color(0xFFFF6B6B)
    com.texfi.w0y.ui.screens.HairSheetFrame(visible = shown, onDismiss = { close(onDismiss) }, heightFraction = null) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 2, modifier = Modifier.padding(bottom = 8.dp))
            androidx.compose.animation.AnimatedContent(targetState = pending, label = "rowMenuConfirm") { confirm ->
                if (confirm != null) {
                    Column {
                        Text(confirm.confirmText ?: confirm.label, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(vertical = 8.dp))
                        androidx.compose.foundation.layout.Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PixelButton(confirm.confirmButton ?: confirm.label, onClick = { close { onPerform(confirm) } })
                            PixelButton(cancel, onClick = { pending = null }, fill = colors.surfaceHigh)
                        }
                    }
                } else {
                    Column {
                        actions.forEachIndexed { i, action ->
                            Text(
                                action.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (action.danger) danger else colors.text,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .hairStagger(i)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                        .clickable { if (action.needsConfirm(protection)) pending = action else close { onPerform(action) } }
                                        .semantics { contentDescription = action.label }
                                        .padding(horizontal = 4.dp, vertical = 14.dp),
                            )
                        }
                        Text(
                            cancel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textMuted,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .clickable { close(onDismiss) }
                                    .padding(horizontal = 4.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}
