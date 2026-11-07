package com.texfi.w0y.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.texfi.w0y.R
import com.texfi.w0y.appVersionLabel
import com.texfi.w0y.data.UpdateInfo
import com.texfi.w0y.data.UpdateState
import com.texfi.w0y.data.WhatsNew
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairTitleStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import com.texfi.w0y.ui.theme.styleTokens
import java.io.File

/**
 * Окна поверх приложения: «Что нового» после обновления и «Доступна новая версия».
 * Одновременно показывается только одно, «что нового» — первым. «Позже» (и тап мимо, и «назад»)
 * не теряет обновление: оно остаётся в Настройки → Приложение, а окно эту версию больше не
 * показывает.
 */
@Composable
fun UpdateDialogs(
    state: UpdateState,
    deferredTag: String?,
    whatsNew: WhatsNew?,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onDefer: (UpdateInfo) -> Unit,
    onCloseWhatsNew: () -> Unit,
    /** Новинки этой версии, от которых можно отказаться; пусто — окно в один шаг. */
    choices: List<com.texfi.w0y.data.ReleaseChoice> = emptyList(),
    prefs: com.texfi.w0y.data.W0ySettings? = null,
    onChoice: (com.texfi.w0y.data.ReleaseChoice, Boolean) -> Unit = { _, _ -> },
) {
    // Для какой версии окно открыто: найденное обновление открывает его один раз, остальное
    // состояние (скачивание, готово, ошибка) просто меняет содержимое уже открытого окна.
    var openTag by remember { mutableStateOf<String?>(null) }
    val info =
        when (state) {
            is UpdateState.Available -> state.info
            is UpdateState.Downloading -> state.info
            is UpdateState.Ready -> state.info
            is UpdateState.Failed -> state.info
            else -> null
        }
    if (openTag == null && state is UpdateState.Available && state.info.tag != deferredTag) openTag = state.info.tag
    if (whatsNew != null) {
        WhatsNewDialog(whatsNew, choices, prefs, onChoice, onCloseWhatsNew)
        return
    }
    if (info == null || openTag != info.tag) return
    UpdateDialog(
        state = state,
        info = info,
        onUpdate = { onDownload(info) },
        onInstall = onInstall,
        onLater = {
            onDefer(info)
            openTag = null
        },
    )
}

@Composable
private fun DialogFrame(testTag: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalW0yColors.current
    val shape = styleTokens.card
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(hairLook.line, colors.outline, shape)
                .padding(20.dp)
                .testTag(testTag),
        ) {
            content()
        }
    }
}

@Composable
private fun Notes(text: String) {
    val colors = LocalW0yColors.current
    val blocks = remember(text) { com.texfi.w0y.data.NotesText.blocks(text) }
    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        blocks.forEachIndexed { index, block ->
            when (block.kind) {
                com.texfi.w0y.data.NotesText.Kind.HEADING -> {
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    Text(block.text.replace("**", "").asLabel(), style = HairLabelStyle, color = colors.textMuted)
                    Spacer(Modifier.height(6.dp))
                }
                com.texfi.w0y.data.NotesText.Kind.BULLET ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("·", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(end = 8.dp))
                        Text(bolded(block.text), style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    }
                com.texfi.w0y.data.NotesText.Kind.PARAGRAPH ->
                    Text(bolded(block.text), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

/** `**слово**` → жирное; остальное как есть. */
private fun bolded(text: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        text.split("**").forEachIndexed { i, part ->
            if (i % 2 == 1) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                append(part)
                pop()
            } else {
                append(part)
            }
        }
    }

@Composable
private fun UpdateDialog(
    state: UpdateState,
    info: UpdateInfo,
    onUpdate: () -> Unit,
    onInstall: (File) -> Unit,
    onLater: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    DialogFrame("update-dialog", onLater) {
        Text(stringResource(R.string.update_dialog_title).asLabel(), style = HairLabelStyle, color = tone)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.update_dialog_versions, appVersionLabel, info.label), style = HairTitleStyle, color = colors.text)
        Spacer(Modifier.height(14.dp))
        when (state) {
            is UpdateState.Downloading -> {
                val share = if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                Text(stringResource(R.string.update_dialog_downloading, (share * 100).toInt()), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(colors.outline)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(tone))
                }
            }
            is UpdateState.Ready ->
                Text(stringResource(R.string.update_dialog_ready), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            is UpdateState.Failed ->
                Text(updateReasonText(state.reason), style = MaterialTheme.typography.bodyMedium, color = colors.text)
            else -> if (info.notes.isNotBlank()) Notes(info.notes)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val primary: Pair<String, () -> Unit>? =
                when (state) {
                    is UpdateState.Available -> stringResource(R.string.update_btn_update) to onUpdate
                    is UpdateState.Ready -> stringResource(R.string.update_btn_install) to { onInstall(state.file) }
                    is UpdateState.Failed -> stringResource(R.string.update_btn_retry) to onUpdate
                    else -> null
                }
            primary?.let { (label, click) -> PixelButton(label, click, Modifier.weight(1f).testTag("update-primary")) }
            PixelButton(stringResource(R.string.update_dialog_later), onLater, Modifier.weight(1f).testTag("update-later"), fill = colors.surfaceHigh)
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.update_dialog_hint), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

/**
 * «Что нового» после обновления. Сначала — что изменилось; если в версии есть новинки, от
 * которых можно отказаться, «Продолжить» ведёт ко второму шагу: переключатели, всё включено.
 * Выключенное сохраняется сразу, это обычные настройки. Закрыть окно можно на любом шаге.
 */
@Composable
private fun WhatsNewDialog(
    whatsNew: WhatsNew,
    choices: List<com.texfi.w0y.data.ReleaseChoice>,
    prefs: com.texfi.w0y.data.W0ySettings?,
    onChoice: (com.texfi.w0y.data.ReleaseChoice, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    var step by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val withChoices = choices.isNotEmpty() && prefs != null
    val inSpec = com.texfi.w0y.ui.components.hairSpec<Float>(com.texfi.w0y.ui.theme.HairTokens.MID_MS)
    val outSpec = com.texfi.w0y.ui.components.hairSpec<Float>(com.texfi.w0y.ui.theme.HairTokens.SHORT_MS)
    DialogFrame("whatsnew-dialog", onClose) {
        androidx.compose.animation.AnimatedContent(
            targetState = if (withChoices) step else 0,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(inSpec) + androidx.compose.animation.scaleIn(inSpec, initialScale = 0.97f))
                    .togetherWith(androidx.compose.animation.fadeOut(outSpec))
            },
            label = "whatsNewStep",
        ) { current ->
            Column {
                if (current == 0) {
                    Text(stringResource(R.string.whatsnew_dialog_label).asLabel(), style = HairLabelStyle, color = tone)
                    Spacer(Modifier.height(10.dp))
                    Text(whatsNew.label, style = HairTitleStyle, color = colors.text)
                    Spacer(Modifier.height(14.dp))
                    Notes(whatsNew.notes)
                    Spacer(Modifier.height(20.dp))
                    if (withChoices) {
                        PixelButton(stringResource(R.string.whatsnew_continue), { step = 1 }, Modifier.fillMaxWidth().testTag("whatsnew-continue"))
                    } else {
                        PixelButton(stringResource(R.string.whatsnew_ok), onClose, Modifier.fillMaxWidth().testTag("whatsnew-ok"))
                    }
                } else {
                    Text(stringResource(R.string.whatsnew_choices_label).asLabel(), style = HairLabelStyle, color = tone)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.whatsnew_choices_title), style = HairTitleStyle, color = colors.text)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.whatsnew_choices_hint), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        choices.forEach { choice ->
                            ChoiceRow(choice, prefs?.let(choice.get) ?: true) { onChoice(choice, it) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    PixelButton(stringResource(R.string.whatsnew_done), onClose, Modifier.fillMaxWidth().testTag("whatsnew-ok"))
                }
            }
        }
    }
}

/** Строка выбора: название, пояснение и переключатель; касание по строке — то же, что по переключателю. */
@Composable
private fun ChoiceRow(choice: com.texfi.w0y.data.ReleaseChoice, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalW0yColors.current
    val haptic = com.texfi.w0y.ui.components.rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("choice-" + choice.id)
            .clickable { onChange(!on) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(choice.title), style = MaterialTheme.typography.bodyLarge, color = colors.text)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(choice.description), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        if (com.texfi.w0y.ui.theme.isHair) {
            com.texfi.w0y.ui.components.HairSwitch(on, onChange, haptic)
        } else {
            com.texfi.w0y.ui.components.PixelSwitch(on, onChange)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.text.copy(alpha = 0.07f)))
}

/**
 * Предложение после обновления: попробовать Hairline New (бета) или остаться на своём стиле.
 * Спрашиваем один раз; вернуть любой стиль можно в Настройки → Вид → Стиль.
 */
@Composable
fun IslandOfferDialog(onTry: () -> Unit, onStay: () -> Unit) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    DialogFrame("island-offer", onStay) {
        Text(stringResource(R.string.island_offer_label).asLabel(), style = HairLabelStyle, color = tone)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.island_offer_title), style = HairTitleStyle, color = colors.text)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.island_offer_text), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        Spacer(Modifier.height(20.dp))
        PixelButton(stringResource(R.string.island_offer_try), onTry, Modifier.fillMaxWidth().testTag("island-offer-try"))
        Spacer(Modifier.height(10.dp))
        PixelButton(stringResource(R.string.island_offer_stay), onStay, Modifier.fillMaxWidth().testTag("island-offer-stay"), fill = colors.surfaceHigh)
    }
}

/** Причина сбоя простыми словами — и в окне, и в настройках. */
@Composable
fun updateReasonText(reason: UpdateState.Reason): String =
    stringResource(
        when (reason) {
            UpdateState.Reason.OFFLINE -> R.string.update_fail_offline
            UpdateState.Reason.RATE_LIMIT -> R.string.update_fail_rate
            UpdateState.Reason.SERVER -> R.string.update_fail_server
            UpdateState.Reason.BAD_FILE -> R.string.update_fail_file
            UpdateState.Reason.NO_APK -> R.string.update_fail_noapk
            UpdateState.Reason.STORAGE -> R.string.update_fail_storage
        },
    )
