package com.texfi.w0y.ui.screens

import com.texfi.w0y.ui.theme.styledSurface
import com.texfi.w0y.ui.theme.styledClip
import com.texfi.w0y.ui.theme.styledBorder
import android.app.Activity
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import com.texfi.w0y.ui.theme.asLabel
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.texfi.w0y.ui.components.hairStagger
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import com.texfi.w0y.ui.components.hairSurface
import com.texfi.w0y.ui.theme.asTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.R
import com.texfi.w0y.appVersionLabel
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.ExplicitFallback
import com.texfi.w0y.data.Language
import com.texfi.w0y.data.Quality
import com.texfi.w0y.data.QueueMode
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.data.StartTab
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.playback.AudioOutput
import com.texfi.w0y.ui.components.EmptyState
import com.texfi.w0y.ui.components.forUi
import com.texfi.w0y.ui.components.ConfirmPanel
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelSegmented
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.PixelSwitch
import com.texfi.w0y.ui.components.ScreenTitle
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.liquidGlass
import com.texfi.w0y.ui.theme.screenBackground
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlinx.coroutines.launch

/**
 * Настройки: сначала разделы, потом пункты.
 *
 * Раньше это была полоса из восьми вкладок над плоским списком. Вкладки
 * не вмещались, подписи приходилось резать до одного слова, и найти в них
 * нужный пункт можно было только перебором. Теперь сверху одиннадцать
 * разделов с подписью, что внутри, а над ними — поиск по всем пунктам
 * разом: с сорока настройками это единственный способ попасть в нужную
 * с первого раза.
 *
 * Список пунктов один на оба режима (раздел и поиск) и собирается
 * данными, а не разложен по экранам: два списка одних и тех же настроек
 * разошлись бы на первой же правке.
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val colors = LocalW0yColors.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                val json = viewModel.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
    val currentSettings by viewModel.settings.collectAsStateWithLifecycle()
    val backdropLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.setBackdropPhoto(uri)
        }
    // Предупреждение об авторских правах — один раз, при первом включении
    // сохранения на устройство; действие выполняется после согласия.
    var copyrightPending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var resetPending by remember { mutableStateOf(false) }
    val withCopyrightNotice: (() -> Unit) -> Unit = { action ->
        if (currentSettings.exportWarningSeen) action() else copyrightPending = action
    }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val json =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            if (json != null) viewModel.importJson(json)
        }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.consumeMessage()
        }
    }

    // Полный сброс — только после явного «да»: вернуть стёртое нельзя.
    if (resetPending) {
        ConfirmPanel(
            title = stringResource(R.string.settings_reset_confirm_title),
            text = stringResource(R.string.settings_reset_confirm_text),
            confirm = stringResource(R.string.settings_reset_button),
            dismiss = stringResource(R.string.common_cancel),
            onConfirm = viewModel::fullReset,
            onDismiss = { resetPending = false },
        )
        BackHandler { resetPending = false }
        return
    }

    copyrightPending?.let { action ->
        ConfirmPanel(
            title = stringResource(R.string.export_warning_title),
            text = stringResource(R.string.export_warning_text),
            confirm = stringResource(R.string.export_warning_ok),
            dismiss = stringResource(R.string.common_cancel),
            onConfirm = {
                viewModel.setExportWarningSeen(true)
                copyrightPending = null
                action()
            },
            onDismiss = { copyrightPending = null },
        )
        BackHandler { copyrightPending = null }
        return
    }

    if (aboutOpen) {
        val experiments = viewModel.settings.collectAsStateWithLifecycle().value.experiments
        BackHandler { aboutOpen = false }
        AboutScreen(
            onBack = { aboutOpen = false },
            experiments = experiments,
            onUnlockExperiments = viewModel::unlockExperiments,
        )
        return
    }

    val rows =
        settingsRows(
            viewModel = viewModel,
            onOpenLogin = onOpenLogin,
            onOpenAbout = { aboutOpen = true },
            onFullReset = { resetPending = true },
            onExport = { exportLauncher.launch("w0y-settings.json") },
            onImport = { importLauncher.launch(arrayOf("application/json")) },
            onAutoExport = { on -> if (on) withCopyrightNotice { viewModel.setAutoExport(true) } else viewModel.setAutoExport(false) },
            onPickBackdrop = { backdropLauncher.launch(arrayOf("image/*")) },
        )
    val hair = com.texfi.w0y.ui.theme.isHair
    val current = section?.let { name -> SettingsSection.entries.firstOrNull { it.name == name } }

    // Внутри раздела «назад» возвращает к списку разделов, а не закрывает
    // настройки целиком: закрытие с середины ощущается как сбой.
    if (current != null && !hair) {
        BackHandler { section = null }
    }

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .statusBarsPadding(),
    ) {
        ScreenTitle(
            title = stringResource(if (hair) R.string.settings_title else (current?.title ?: R.string.settings_title)),
            onBack = if (current != null && !hair) ({ section = null }) else onClose,
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }

        if (current == null || hair) {
            SettingsSearchField(value = query, onValueChange = { query = it })
            Spacer(Modifier.height(if (hair) 4.dp else 12.dp))
        }
        if (hair && query.isBlank()) {
            val tabs = listOf<Pair<String?, String>>(null to stringResource(R.string.settings_quick)) +
                SectionGroup.entries.flatMap { g -> g.sections }.map { it.name to stringResource(it.title) }
            HairTabStrip(tabs, section) { section = it }
        }

        val matches =
            if ((current != null && !hair) || query.isBlank()) {
                emptyList()
            } else {
                val needle = query.trim().lowercase()
                rows.filter { row ->
                    needle in row.title.lowercase() ||
                        needle in row.description.lowercase() ||
                        needle in row.keywords.lowercase()
                }
            }

        // Под стеклянными панелями список уходит под них, а отступ снизу
        // докручивает последний пункт над ними; без стекла — над жестами.
        val barsInset = com.texfi.w0y.ui.components.LocalBottomInset.current
        val rowPad = if (com.texfi.w0y.ui.theme.isHair) 0.dp else 16.dp
        val tabOrder = listOf<String?>(null) + SectionGroup.entries.flatMap { g -> g.sections }.map { it.name }
        androidx.compose.animation.AnimatedContent(
            targetState = section,
            transitionSpec = {
                if (!hair) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else {
                    val dir = if (tabOrder.indexOf(targetState) >= tabOrder.indexOf(initialState)) 1 else -1
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 60)) +
                        androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { dir * it / 10 }) togetherWith
                        (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) +
                            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(240)) { -dir * it / 12 })
                }
            },
            label = "settingsTab",
        ) { tabKey ->
        val current = tabKey?.let { name -> SettingsSection.entries.firstOrNull { it.name == name } }
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .then(if (barsInset > 0.dp) Modifier else Modifier.navigationBarsPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, bottom = barsInset),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            when {
                current != null && (!hair || query.isBlank()) -> {
                    item {
                        Text(
                            text = stringResource(current.summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (current == SettingsSection.HAIR) {
                        item { HairLookPreview(currentSettings) }
                    }
                    // Весь раздел — одна группа, строки через черту.
                    item {
                        GlassGroup {
                            if (current == SettingsSection.DEVICES) {
                                Box(Modifier.padding(horizontal = rowPad)) { DevicesBody(viewModel) }
                                GroupDivider(start = 16.dp)
                            }
                            val inSection = rows.filter { it.section == current }
                            inSection.forEachIndexed { index, row ->
                                if (hair) {
                                    Box(Modifier.padding(bottom = 10.dp).hairStagger(index)) { SettingRowView(row) }
                                } else {
                                    Box(Modifier.padding(horizontal = rowPad, vertical = 4.dp)) { SettingRowView(row) }
                                    if (index < inSection.lastIndex) GroupDivider(start = 16.dp)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                query.isNotBlank() ->
                    if (matches.isEmpty()) {
                        item {
                            EmptyState(
                                sprite = Sprites.search,
                                title = stringResource(R.string.settings_search_nothing_title),
                                text = stringResource(R.string.settings_search_nothing_text),
                            )
                        }
                    } else {
                        item {
                            GlassGroup {
                                matches.forEachIndexed { index, row ->
                                    Box(Modifier.padding(horizontal = rowPad, vertical = 4.dp)) {
                                        SettingRowView(row, sectionHint = stringResource(row.section.title))
                                    }
                                    if (index < matches.lastIndex) GroupDivider(start = 16.dp)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }

                else -> {
                    // Самое нужное — сразу наверху, без заходов в разделы.
                    val quick = rows.filter { it.quick }
                    if (quick.isNotEmpty()) {
                        item(key = "quick") {
                            Column {
                                if (!hair) GroupCaption(stringResource(R.string.settings_quick))
                                GlassGroup {
                                    quick.forEachIndexed { index, row ->
                                        if (hair) {
                                            Box(Modifier.padding(bottom = 10.dp).hairStagger(index)) { SettingRowView(row) }
                                        } else {
                                            Box(Modifier.padding(horizontal = rowPad, vertical = 4.dp)) { SettingRowView(row) }
                                            if (index < quick.lastIndex) GroupDivider(start = 16.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Разделы собраны в группы с подписью — как в iOS, в каждом стиле по-своему.
                    if (!hair) SectionGroup.entries.forEach { group ->
                        item(key = group.name) {
                            Column {
                                GroupCaption(stringResource(group.title))
                                GlassGroup {
                                    group.sections.forEachIndexed { index, entry ->
                                        GlassSectionRow(entry) { section = entry.name }
                                        if (index < group.sections.lastIndex) GroupDivider(start = 54.dp)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            text = stringResource(R.string.settings_version, appVersionLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Разделы настроек. У каждого — подпись, что внутри: без неё список
 * названий заставляет открывать разделы по очереди, чтобы понять, где
 * лежит нужное.
 */
private enum class SettingsSection(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val sprite: List<String>,
    val glyph: com.texfi.w0y.ui.icons.HairGlyph,
) {
    SOUND(R.string.settings_section_sound, R.string.settings_section_sound_sum, Sprites.wave, com.texfi.w0y.ui.icons.HairGlyph.WAVE),
    TONE(R.string.settings_section_tone, R.string.settings_section_tone_sum, Sprites.sliders, com.texfi.w0y.ui.icons.HairGlyph.SLIDERS),
    PLAYER(R.string.settings_section_player, R.string.settings_section_player_sum, Sprites.play, com.texfi.w0y.ui.icons.HairGlyph.PLAY),
    SPEED(R.string.settings_section_speed, R.string.settings_section_speed_sum, Sprites.rocket, com.texfi.w0y.ui.icons.HairGlyph.ROCKET),
    STORAGE(R.string.settings_section_storage, R.string.settings_section_storage_sum, Sprites.box, com.texfi.w0y.ui.icons.HairGlyph.BOX),
    DOWNLOADS(R.string.settings_section_downloads, R.string.settings_section_downloads_sum, Sprites.download, com.texfi.w0y.ui.icons.HairGlyph.DOWNLOAD),
    SEARCH(R.string.settings_section_search, R.string.settings_section_search_sum, Sprites.search, com.texfi.w0y.ui.icons.HairGlyph.SEARCH),
    ACCOUNT(R.string.settings_section_account, R.string.settings_section_account_sum, Sprites.sync, com.texfi.w0y.ui.icons.HairGlyph.SYNC),
    CLEAN(R.string.settings_section_clean, R.string.settings_section_clean_sum, Sprites.shield, com.texfi.w0y.ui.icons.HairGlyph.SHIELD),
    LOOK(R.string.settings_section_look, R.string.settings_section_look_sum, Sprites.palette, com.texfi.w0y.ui.icons.HairGlyph.PALETTE),
    HAIR(R.string.hl_sec_title, R.string.hl_sec_sum, Sprites.sliders, com.texfi.w0y.ui.icons.HairGlyph.SLIDERS),
    DEVICES(R.string.settings_section_devices, R.string.settings_section_devices_sum, Sprites.headphones, com.texfi.w0y.ui.icons.HairGlyph.HEADPHONES),
    DATA(R.string.settings_section_data, R.string.settings_section_data_sum, Sprites.license, com.texfi.w0y.ui.icons.HairGlyph.LICENSE),
}

/** Чем управляет пункт. Вид строки выбирается по этому, а не задаётся руками. */
private sealed interface SettingControl {
    data class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : SettingControl

    data class Choice<T>(
        val options: List<T>,
        val selected: T,
        val label: @Composable (T) -> String,
        val onSelect: (T) -> Unit,
    ) : SettingControl

    data class Action(val button: String, val onClick: () -> Unit) : SettingControl

    data class Info(val value: String) : SettingControl

    /** Свой виджет под названием пункта: образцы цвета и подобное. */
    class Custom(val content: @Composable () -> Unit) : SettingControl
}

/**
 * Пункт настроек.
 *
 * Подписи уже разрешены в строки: по ним идёт поиск, и искать нужно по
 * тому языку, который человек видит на экране, а не по именам ресурсов.
 */
private class SettingRow(
    val section: SettingsSection,
    val title: String,
    val description: String,
    val control: SettingControl,
    /** Слова, по которым пункт тоже должен находиться. */
    val keywords: String = "",
    /** Показывается блоком «Быстро» наверху настроек. */
    val quick: Boolean = false,
)

/** Группы разделов на главной странице настроек. */
private enum class SectionGroup(@StringRes val title: Int, val sections: List<SettingsSection>) {
    SOUND(R.string.settings_group_sound, listOf(SettingsSection.SOUND, SettingsSection.TONE, SettingsSection.PLAYER)),
    SPEED(R.string.settings_group_speed, listOf(SettingsSection.SPEED, SettingsSection.STORAGE, SettingsSection.DOWNLOADS)),
    MUSIC(R.string.settings_group_music, listOf(SettingsSection.SEARCH, SettingsSection.ACCOUNT, SettingsSection.CLEAN)),
    APP(R.string.settings_group_app, listOf(SettingsSection.LOOK, SettingsSection.HAIR, SettingsSection.DEVICES, SettingsSection.DATA)),
}

/**
 * Группа строк: одна поверхность, строки внутри — без своих подложек.
 * Smooth — серое стекло с крупным скруглением, Pixel — блок TexFi с
 * рамкой 2 dp и жёсткой тенью без размытия.
 */
@Composable
private fun GlassGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = LocalW0yColors.current
    if (com.texfi.w0y.ui.theme.isHair) {
        // Hairline: без рамки и поверхности — строки идут прямо по фону, между ними тонкая линия.
        Column(Modifier.fillMaxWidth(), content = content)
        return
    }
    if (!com.texfi.w0y.ui.theme.isSmooth) {
        Box(Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp)) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(4.dp, 4.dp)
                    .background(colors.shadow),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(2.dp, colors.border)
                    .padding(vertical = 2.dp),
                content = content,
            )
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            // В тёмной теме группа — серое стекло, светлее фона, как в iOS:
            // почти чёрная на чёрном она не читалась бы группой.
            .liquidGlass(
                androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                tint = if (colors.background.luminance() > 0.5f) null else Color(0xFF2E2E33),
            ).padding(vertical = 4.dp),
        content = content,
    )
}

/** Черта между строками группы, с отступом под иконку; в Pixel — сплошная 2 dp. */
@Composable
private fun GroupDivider(start: androidx.compose.ui.unit.Dp) {
    val colors = LocalW0yColors.current
    val smooth = com.texfi.w0y.ui.theme.isSmooth
    val hair = com.texfi.w0y.ui.theme.isHair
    Box(
        Modifier
            .padding(start = if (hair) 0.dp else start)
            .fillMaxWidth()
            .height(if (hair) com.texfi.w0y.ui.theme.hairLook.line else if (smooth) 0.5.dp else 2.dp)
            .background(if (hair) colors.border else if (smooth) colors.text.copy(alpha = 0.1f) else colors.border),
    )
}

/** Подпись группы: в Smooth мелко и приглушённо, в Pixel — пиксельный заголовок секции. */
@Composable
private fun GroupCaption(text: String) {
    val colors = LocalW0yColors.current
    if (com.texfi.w0y.ui.theme.isHair) {
        Text(
            text = "❯ ${text.uppercase()}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(start = 2.dp, top = 24.dp, bottom = 10.dp),
        )
        return
    }
    if (!com.texfi.w0y.ui.theme.isSmooth) {
        Text(
            text = "❯ ${text.uppercase()}",
            style = PixelSectionLabel,
            color = colors.accent,
            modifier = Modifier.padding(start = 2.dp, top = 22.dp, bottom = 10.dp),
        )
        return
    }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
        color = colors.textMuted,
        modifier = Modifier.padding(start = 14.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** Строка раздела внутри группы: иконка, название, подпись, стрелка. */
@Composable
private fun GlassSectionRow(section: SettingsSection, onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    if (com.texfi.w0y.ui.theme.isHair) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.texfi.w0y.ui.icons.HairIcon(section.glyph, colors.textMuted, Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(section.title), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = colors.text, maxLines = 1)
                Text(stringResource(section.summary), style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            com.texfi.w0y.ui.icons.HairIcon(com.texfi.w0y.ui.icons.HairGlyph.CHEVRON_RIGHT, colors.textMuted, Modifier.size(16.dp))
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelSprite(
            rows = section.sprite,
            color = if (com.texfi.w0y.ui.theme.isSmooth) colors.text else colors.accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(section.title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.text,
            )
            Text(
                text = stringResource(section.summary),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        Spacer(Modifier.width(10.dp))
        PixelSprite(rows = Sprites.chevronRight, color = colors.textMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SettingsSearchField(value: String, onValueChange: (String) -> Unit) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .then(
                if (com.texfi.w0y.ui.theme.isHair) {
                    val look = com.texfi.w0y.ui.theme.hairLook
                    val shape = androidx.compose.foundation.shape.RoundedCornerShape(look.pillRadius.coerceAtMost(12.dp))
                    Modifier.clip(shape).border(look.line, colors.outline, shape)
                } else {
                    Modifier.styledSurface(8)
                },
            ).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelSprite(rows = Sprites.search, color = colors.textMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                )
            }
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            SpriteButton(Sprites.close, onClick = { onValueChange("") }, size = 14)
        }
    }
}

/** Одна строка настроек. Вид зависит только от того, чем она управляет. */
@Composable
private fun SettingRowView(row: SettingRow, sectionHint: String? = null) {
    val colors = LocalW0yColors.current
    val control = row.control
    val hair = com.texfi.w0y.ui.theme.isHair
    val look = com.texfi.w0y.ui.theme.hairLook
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(look.cardRadius.coerceAtMost(14.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (hair) {
                    Modifier
                        .clip(cardShape)
                        .background(colors.surface)
                        .border(look.line, colors.border, cardShape)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                } else {
                    Modifier.padding(vertical = 8.dp)
                },
            ),
    ) {
        if (sectionHint != null) {
            Text(
                text = "❯ ${sectionHint.uppercase()}",
                style = PixelSectionLabel,
                color = colors.accent,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        when (control) {
            is SettingControl.Toggle ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { control.onChange(!control.checked) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Labels(row, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    PixelSwitch(checked = control.checked, onCheckedChange = control.onChange)
                }

            is SettingControl.Action ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Labels(row, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    PixelButton(text = control.button, onClick = control.onClick, fill = colors.surfaceHigh)
                }

            is SettingControl.Info ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Labels(row, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(control.value, style = MaterialTheme.typography.bodyLarge, color = colors.accent)
                }

            is SettingControl.Choice<*> -> {
                Labels(row, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                ChoiceControl(control)
            }

            is SettingControl.Custom -> {
                Labels(row, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                control.content()
            }
        }
    }
}

/**
 * Обобщённый выбор. Приведение к `Any?` здесь неизбежно: список
 * настроек хранит варианты разных типов в одном месте, а вернуть выбор
 * обратно всё равно можно только тому, кто его отдал.
 */
@Composable
private fun ChoiceControl(control: SettingControl.Choice<*>) {
    @Suppress("UNCHECKED_CAST")
    val typed = control as SettingControl.Choice<Any?>
    if (com.texfi.w0y.ui.theme.isHair) {
        HairChips(
            options = typed.options.map { typed.label(it) },
            selectedIndex = typed.options.indexOf(typed.selected),
            onSelect = { typed.onSelect(typed.options[it]) },
        )
        return
    }
    PixelSegmented(
        options = typed.options.map { typed.label(it) },
        selectedIndex = typed.options.indexOf(typed.selected),
        onSelect = { typed.onSelect(typed.options[it]) },
    )
}

@Composable
private fun Labels(row: SettingRow, modifier: Modifier) {
    val colors = LocalW0yColors.current
    Column(modifier) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Text(row.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

/**
 * Выходы звука — то, что система действительно видит подключённым.
 *
 * Список приходит от AudioManager и обновляется на подключение и
 * отключение. Ничего «своего» здесь нет: показывать выдуманные
 * устройства в приложении, которое обещает не врать, нельзя.
 */
@Composable
private fun DevicesBody(viewModel: SettingsViewModel) {
    val colors = LocalW0yColors.current
    val outputs by viewModel.outputs.collectAsStateWithLifecycle()
    if (outputs.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_devices_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        return
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        outputs.forEach { output -> DeviceRow(output) }
    }
}

@Composable
private fun DeviceRow(output: AudioOutput) {
    val colors = LocalW0yColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (output.active) colors.accent else colors.border),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = output.name ?: stringResource(output.kind.label),
                style = MaterialTheme.typography.bodyLarge,
                color = if (output.active) colors.text else colors.textMuted,
                maxLines = 1,
            )
            Text(
                text = stringResource(output.kind.label),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        if (output.active) {
            Text(
                text = stringResource(R.string.settings_devices_active),
                style = PixelSectionLabel,
                color = colors.accent,
            )
        }
    }
}

/**
 * Все пункты настроек одним списком.
 *
 * Здесь они и живут: экран только показывает их — в разделе или в выдаче
 * поиска. Пункт, который ничего не меняет, в список не попадает: обещание
 * «у каждой настройки написано, что она делает» держится ровно до первого
 * тумблера-пустышки.
 */
@Composable
private fun settingsRows(
    viewModel: SettingsViewModel,
    onOpenLogin: () -> Unit,
    onOpenAbout: () -> Unit,
    onFullReset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAutoExport: (Boolean) -> Unit,
    onPickBackdrop: () -> Unit,
): List<SettingRow> {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val startupAverage by viewModel.startupAverage.collectAsStateWithLifecycle()
    val startupLast by viewModel.startupLast.collectAsStateWithLifecycle()
    val startupCount by viewModel.startupCount.collectAsStateWithLifecycle()
    val startupBreakdown by viewModel.startupBreakdown.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val downloadBytes by viewModel.downloadBytes.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val context = LocalContext.current

    return buildList {
        // ЗВУК
        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_quality_wifi_title),
                quick = true,
                description = stringResource(R.string.settings_quality_wifi_desc),
                keywords = "wifi wi-fi",
                control =
                    SettingControl.Choice(
                        options = Quality.entries,
                        selected = settings.qualityWifi,
                        label = { qualityLabel(it) },
                        onSelect = viewModel::setQualityWifi,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_quality_mobile_title),
                description = stringResource(R.string.settings_quality_mobile_desc),
                keywords = "lte 4g 5g",
                control =
                    SettingControl.Choice(
                        options = Quality.entries,
                        selected = settings.qualityMobile,
                        label = { qualityLabel(it) },
                        onSelect = viewModel::setQualityMobile,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_normalize_title),
                description = stringResource(R.string.settings_normalize_desc),
                control = SettingControl.Toggle(settings.normalizeVolume, viewModel::setNormalize),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_skip_silence_title),
                description = stringResource(R.string.settings_skip_silence_desc),
                control = SettingControl.Toggle(settings.skipSilence, viewModel::setSkipSilence),
            ),
        )

        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_fallback_title),
                description = stringResource(R.string.settings_fallback_desc),
                keywords = "fallback piped alternative not loading",
                control = SettingControl.Toggle(settings.fallbackAudio, viewModel::setFallbackAudio),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SOUND,
                title = stringResource(R.string.settings_fallback_source_title),
                description = stringResource(R.string.settings_fallback_source_desc),
                keywords = "fallback piped source",
                control =
                    SettingControl.Choice(
                        options = com.texfi.w0y.data.FallbackSource.entries,
                        selected = settings.fallbackSource,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setFallbackSource,
                    ),
            ),
        )

        // ЗВУЧАНИЕ
        add(
            SettingRow(
                section = SettingsSection.TONE,
                title = stringResource(R.string.settings_speed_title),
                description = stringResource(R.string.settings_speed_desc),
                keywords = "slowed sped",
                control =
                    SettingControl.Choice(
                        options = listOf(0.75f, 0.85f, 1f, 1.25f, 1.5f),
                        selected = settings.speed,
                        label = { com.texfi.w0y.data.NumberText.speed(it) },
                        onSelect = viewModel::setSpeed,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.TONE,
                title = stringResource(R.string.settings_pitch_title),
                description = stringResource(R.string.settings_pitch_desc),
                control =
                    SettingControl.Choice(
                        options = listOf(0.9f, 0.95f, 1f, 1.05f, 1.1f),
                        selected = settings.pitch,
                        label = { it.toString().replace("1.0", stringResource(R.string.settings_pitch_normal)) },
                        onSelect = viewModel::setPitch,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.TONE,
                title = stringResource(R.string.settings_reverb_title),
                description = stringResource(R.string.settings_reverb_desc),
                keywords = "reverb",
                control =
                    SettingControl.Choice(
                        options = Reverb.entries,
                        selected = settings.reverb,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setReverb,
                    ),
            ),
        )

        // ПЛЕЕР
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_equalizer_title),
                description = stringResource(R.string.settings_equalizer_desc),
                keywords = "eq",
                control =
                    SettingControl.Action(stringResource(R.string.settings_equalizer_button)) {
                        val intent =
                            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, viewModel.audioSessionId)
                                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                            }
                        // Честно говорим, если эквалайзера в системе нет,
                        // вместо кнопки, которая молча ничего не делает.
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            viewModel.reportNoEqualizer()
                        }
                    },
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_queue_title),
                description = stringResource(R.string.settings_queue_desc),
                keywords = "radio",
                control =
                    SettingControl.Choice(
                        options = QueueMode.entries,
                        selected = settings.queueMode,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setQueueMode,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_seek_step_title),
                description = stringResource(R.string.settings_seek_step_desc),
                control =
                    SettingControl.Choice(
                        options = listOf(5, 10, 15, 30),
                        selected = settings.seekStepSec,
                        label = { stringResource(R.string.settings_seek_step_value, it) },
                        onSelect = viewModel::setSeekStep,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_cover_glow_title),
                description = stringResource(R.string.settings_cover_glow_desc),
                control = SettingControl.Toggle(settings.playerCoverGlow, viewModel::setPlayerCoverGlow),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_lyrics_title),
                quick = true,
                description = stringResource(R.string.settings_lyrics_desc),
                control = SettingControl.Toggle(settings.showLyrics, viewModel::setShowLyrics),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_pause_headphones_title),
                description = stringResource(R.string.settings_pause_headphones_desc),
                control = SettingControl.Toggle(settings.pauseOnHeadphonesOut, viewModel::setPauseOnUnplug),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_resume_headphones_title),
                description = stringResource(R.string.settings_resume_headphones_desc),
                control = SettingControl.Toggle(settings.resumeOnHeadphonesIn, viewModel::setResumeOnPlug),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_playlist_recs),
                description = stringResource(R.string.settings_playlist_recs_hint),
                control = SettingControl.Toggle(settings.showPlaylistRecommendations, viewModel::setShowPlaylistRecommendations),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.viz_style_title),
                quick = true,
                description = stringResource(R.string.viz_style_desc),
                keywords = "visualizer spectrum",
                control = SettingControl.Choice(com.texfi.w0y.data.VisualizerStyle.entries.filter { if (settings.uiStyle == com.texfi.w0y.data.UiStyle.HAIRLINE) it.hair else (!it.hair && it.smooth == (settings.uiStyle != com.texfi.w0y.data.UiStyle.PIXEL)) }, settings.visualizerStyle.forUi(settings.uiStyle), { stringResource(it.label) }, viewModel::setVisualizerStyle),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.viz_sensitivity_title),
                description = stringResource(R.string.viz_sensitivity_desc),
                control = SettingControl.Choice(listOf(0.5f, 1f, 1.5f, 2f), settings.visualizerSensitivity, { com.texfi.w0y.data.NumberText.speed(it) }, viewModel::setVisualizerSensitivity),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.viz_fps_title),
                description = stringResource(R.string.viz_fps_desc),
                control = SettingControl.Choice(listOf(30, 60), settings.visualizerFps, { it.toString() }, viewModel::setVisualizerFps),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.viz_backdrop_title),
                description = stringResource(R.string.viz_backdrop_desc),
                control = SettingControl.Toggle(settings.visualizerCoverBackdrop, viewModel::setVisualizerCoverBackdrop),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.lyrics_size_title),
                description = stringResource(R.string.lyrics_size_desc),
                keywords = "lyrics text",
                control = SettingControl.Choice(com.texfi.w0y.data.LyricsSize.entries, settings.lyricsSize, { stringResource(it.label) }, viewModel::setLyricsSize),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.lyrics_delay_title),
                quick = true,
                description = stringResource(R.string.lyrics_delay_desc),
                keywords = "lyrics sync offset delay",
                control =
                    SettingControl.Choice(
                        listOf(-500, 0, 250, 500, 750, 1000, 1500, 2000),
                        settings.lyricsDelayMs,
                        { ms -> if (ms == 0) stringResource(R.string.lyrics_delay_none) else "%+.2f".format(ms / 1000f).trimEnd('0').trimEnd('.', ',') + " " + stringResource(R.string.unit_sec) },
                        viewModel::setLyricsDelayMs,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.lyrics_sources_title),
                description = stringResource(R.string.lyrics_sources_desc),
                keywords = "lyrics genius netease search",
                control = SettingControl.Toggle(settings.lyricsAllSources, viewModel::setLyricsAllSources),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_clock_title),
                description = stringResource(R.string.stand_clock_desc),
                keywords = "stand clock time",
                control = SettingControl.Toggle(settings.standClock, viewModel::setStandClock),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_lyrics_title),
                description = stringResource(R.string.stand_lyrics_desc),
                keywords = "stand always on",
                control = SettingControl.Toggle(settings.standLyrics, viewModel::setStandLyrics),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_viz_title),
                description = stringResource(R.string.stand_viz_desc),
                keywords = "stand always on",
                control = SettingControl.Toggle(settings.standVisualizer, viewModel::setStandVisualizer),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_brightness_title),
                description = stringResource(R.string.stand_brightness_desc),
                keywords = "stand always on",
                control =
                    SettingControl.Choice(
                        listOf(com.texfi.w0y.data.STAND_BRIGHTNESS_SYSTEM, 0.05f, 0.15f, 0.3f, 0.6f),
                        // Выставленное ползунком в подставке может не совпасть с вариантом — тогда ближайший.
                        if (settings.standBrightness < 0f) {
                            com.texfi.w0y.data.STAND_BRIGHTNESS_SYSTEM
                        } else {
                            listOf(0.05f, 0.15f, 0.3f, 0.6f).minBy { kotlin.math.abs(it - settings.standBrightness) }
                        },
                        { if (it < 0f) stringResource(R.string.stand_brightness_system) else "${(it * 100).toInt()}%" },
                        viewModel::setStandBrightness,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.backdrop_title),
                description = stringResource(R.string.backdrop_desc),
                keywords = "background wallpaper aurora photo grid black",
                quick = true,
                control =
                    SettingControl.Choice(
                        com.texfi.w0y.data.BackdropMode.entries,
                        settings.backdropMode,
                        {
                            stringResource(
                                when (it) {
                                    com.texfi.w0y.data.BackdropMode.GRID -> R.string.backdrop_grid
                                    com.texfi.w0y.data.BackdropMode.PURE -> R.string.backdrop_pure
                                    com.texfi.w0y.data.BackdropMode.COVER -> R.string.backdrop_cover
                                    com.texfi.w0y.data.BackdropMode.AURORA -> R.string.backdrop_aurora
                                    com.texfi.w0y.data.BackdropMode.PHOTO -> R.string.backdrop_photo
                                },
                            )
                        },
                        viewModel::setBackdropMode,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.backdrop_image_title),
                description = stringResource(R.string.backdrop_image_desc),
                keywords = "background photo image gallery",
                control = SettingControl.Action(stringResource(R.string.backdrop_pick), onPickBackdrop),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.season_title),
                description = stringResource(R.string.season_desc),
                keywords = "season events halloween christmas winter new year",
                control =
                    SettingControl.Choice(
                        com.texfi.w0y.data.SeasonMode.entries,
                        settings.seasonMode,
                        {
                            stringResource(
                                when (it) {
                                    com.texfi.w0y.data.SeasonMode.AUTO -> R.string.season_auto
                                    com.texfi.w0y.data.SeasonMode.OFF -> R.string.season_off
                                    com.texfi.w0y.data.SeasonMode.HALLOWEEN -> R.string.season_halloween
                                    com.texfi.w0y.data.SeasonMode.WINTER -> R.string.season_winter
                                },
                            )
                        },
                        viewModel::setSeasonMode,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_view_title),
                description = stringResource(R.string.stand_view_desc),
                keywords = "stand always on cover",
                control =
                    SettingControl.Choice(
                        com.texfi.w0y.data.StandView.entries,
                        settings.standView,
                        { stringResource(if (it == com.texfi.w0y.data.StandView.TEXT) R.string.stand_view_text else R.string.stand_view_cover) },
                        viewModel::setStandView,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_marquee_title),
                description = stringResource(R.string.stand_marquee_desc),
                keywords = "stand always on marquee",
                control = SettingControl.Toggle(settings.standMarquee, viewModel::setStandMarquee),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_flip_title),
                description = stringResource(R.string.stand_flip_desc),
                keywords = "stand always on flip upside down rotate",
                control = SettingControl.Toggle(settings.standFlipped, viewModel::setStandFlipped),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_charging_title),
                description = stringResource(R.string.stand_charging_desc),
                keywords = "stand always on",
                control = SettingControl.Toggle(settings.standChargingOnly, viewModel::setStandChargingOnly),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_max_title),
                description = stringResource(R.string.stand_max_desc),
                keywords = "stand always on",
                control = SettingControl.Choice(listOf(0, 30, 60, 120), settings.standMaxMinutes, { if (it == 0) stringResource(R.string.dl_no_limit) else stringResource(R.string.settings_sleep_minutes, it) }, viewModel::setStandMaxMinutes),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.stand_battery_title),
                description = stringResource(R.string.stand_battery_desc),
                keywords = "stand always on",
                control = SettingControl.Toggle(settings.standLowBatteryExit, viewModel::setStandLowBatteryExit),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.PLAYER,
                title = stringResource(R.string.settings_sleep_title),
                description = stringResource(R.string.settings_sleep_desc),
                control =
                    SettingControl.Choice(
                        options = listOf(15, 30, 45, 60),
                        selected = settings.sleepTimerDefaultMin,
                        label = { stringResource(R.string.settings_sleep_minutes, it) },
                        onSelect = viewModel::setSleepDefault,
                    ),
            ),
        )

        // СКОРОСТЬ
        add(
            SettingRow(
                section = SettingsSection.SPEED,
                title = stringResource(R.string.settings_startup_title),
                description =
                    stringResource(
                        R.string.settings_startup_desc,
                        if (startupCount == 0) {
                            stringResource(R.string.settings_startup_desc_empty)
                        } else {
                            stringResource(
                                R.string.settings_startup_desc_stats,
                                startupCount,
                                (startupLast ?: 0).toInt(),
                            )
                        },
                    ),
                control =
                    SettingControl.Info(
                        startupAverage
                            ?.let { stringResource(R.string.settings_startup_value_ms, it.toInt()) }
                            ?: "—",
                    ),
            ),
        )
        startupBreakdown?.takeIf { it.count > 0 }?.let { b ->
            fun part(stat: com.texfi.w0y.playback.PartStat?) = stat?.let { "${it.median} / ${it.worst}" } ?: "—"
            add(
                SettingRow(
                    section = SettingsSection.SPEED,
                    title = stringResource(R.string.settings_startup_parts_title),
                    description =
                        stringResource(
                            R.string.settings_startup_parts_desc,
                            part(b.resolve),
                            part(b.firstByte),
                            part(b.ready),
                            part(b.total),
                            b.fromDisk,
                            b.count,
                        ),
                    control = SettingControl.Info(stringResource(R.string.settings_startup_parts_value, b.count)),
                ),
            )
        }
        add(
            SettingRow(
                section = SettingsSection.SPEED,
                title = stringResource(R.string.settings_preload_title),
                description = stringResource(R.string.settings_preload_desc),
                control = SettingControl.Toggle(settings.preloadNext, viewModel::setPreload),
            ),
        )

        // ПАМЯТЬ
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.settings_wifi_only_title),
                description = stringResource(R.string.settings_wifi_only_desc),
                control = SettingControl.Toggle(settings.downloadOnWifiOnly, viewModel::setDownloadOnWifiOnly),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.STORAGE,
                title = stringResource(R.string.settings_cache_title),
                description = stringResource(R.string.settings_cache_desc, (cacheBytes / 1024 / 1024).toInt()),
                control =
                    SettingControl.Choice(
                        options = listOf(256, 512, 1024, 2048),
                        selected = settings.cacheLimitMb,
                        label = {
                            if (it >= 1024) {
                                stringResource(R.string.settings_cache_gb, it / 1024)
                            } else {
                                stringResource(R.string.settings_cache_mb, it)
                            }
                        },
                        onSelect = viewModel::setCacheLimit,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.STORAGE,
                title = stringResource(R.string.settings_clear_cache_title),
                description = stringResource(R.string.settings_clear_cache_desc),
                control = SettingControl.Action(stringResource(R.string.settings_clear_cache_button), viewModel::clearCache),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.settings_autodownload_title),
                description = stringResource(R.string.settings_autodownload_desc),
                control = SettingControl.Toggle(settings.autoDownloadLiked, viewModel::setAutoDownload),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_auto_playlists_title),
                description = stringResource(R.string.dl_auto_playlists_desc),
                control =
                    SettingControl.Custom {
                        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                        if (playlists.isEmpty()) {
                            Text(stringResource(R.string.dl_auto_playlists_none), style = MaterialTheme.typography.bodySmall, color = LocalW0yColors.current.textMuted)
                        }
                        Column {
                            playlists.forEach { playlist ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(playlist.name, style = MaterialTheme.typography.bodyMedium, color = LocalW0yColors.current.text, modifier = Modifier.weight(1f))
                                    PixelSwitch(
                                        checked = playlist.id in settings.autoDownloadPlaylists,
                                        onCheckedChange = { viewModel.setAutoDownloadPlaylist(playlist.id, it) },
                                    )
                                }
                            }
                        }
                    },
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_quality_wifi_title),
                description = stringResource(R.string.dl_quality_desc),
                control = SettingControl.Choice(Quality.entries, settings.downloadQualityWifi, { qualityLabel(it) }, viewModel::setDownloadQualityWifi),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_quality_mobile_title),
                description = stringResource(R.string.dl_quality_desc),
                control = SettingControl.Choice(Quality.entries, settings.downloadQualityMobile, { qualityLabel(it) }, viewModel::setDownloadQualityMobile),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_format_title),
                description = stringResource(R.string.dl_format_desc),
                control = SettingControl.Info(stringResource(R.string.dl_format_value)),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_parallel_title),
                description = stringResource(R.string.dl_parallel_desc),
                control = SettingControl.Choice(listOf(1, 2, 3, 4), settings.downloadParallel, { it.toString() }, viewModel::setDownloadParallel),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_speed_limit_title),
                description = stringResource(R.string.dl_speed_limit_desc),
                control =
                    SettingControl.Choice(
                        listOf(0, 256, 512, 1024, 2048, 4096),
                        settings.downloadSpeedLimitKb,
                        { if (it == 0) stringResource(R.string.dl_no_limit) else stringResource(R.string.dl_kbps, it) },
                        viewModel::setDownloadSpeedLimit,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_retries_title),
                description = stringResource(R.string.dl_retries_desc),
                control = SettingControl.Choice(listOf(0, 1, 3, 5), settings.downloadRetries, { it.toString() }, viewModel::setDownloadRetries),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_low_battery_title),
                description = stringResource(R.string.dl_low_battery_desc),
                control = SettingControl.Toggle(settings.downloadPauseOnLowBattery, viewModel::setDownloadPauseOnLowBattery),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_auto_resume_title),
                description = stringResource(R.string.dl_auto_resume_desc),
                control = SettingControl.Toggle(settings.downloadAutoResume, viewModel::setDownloadAutoResume),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_notifications_title),
                description = stringResource(R.string.dl_notifications_desc),
                control = SettingControl.Toggle(settings.downloadNotifications, viewModel::setDownloadNotifications),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_auto_export_title),
                description = stringResource(R.string.dl_auto_export_desc),
                control = SettingControl.Toggle(settings.autoExport, onAutoExport),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_storage_limit_title),
                description = stringResource(R.string.dl_storage_limit_desc, (downloadBytes / 1024 / 1024).toInt()),
                control =
                    SettingControl.Choice(
                        listOf(0, 1024, 2048, 5120, 10240),
                        settings.downloadStorageLimitMb,
                        { if (it == 0) stringResource(R.string.dl_no_limit) else stringResource(R.string.settings_cache_gb, it / 1024) },
                        viewModel::setDownloadStorageLimit,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_verify_title),
                description = stringResource(R.string.dl_verify_desc),
                control = SettingControl.Action(stringResource(R.string.dl_verify_button), viewModel::verifyDownloads),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DOWNLOADS,
                title = stringResource(R.string.dl_clear_title),
                description = stringResource(R.string.dl_clear_desc),
                control = SettingControl.Action(stringResource(R.string.dl_clear_button), viewModel::clearDownloads),
            ),
        )

        // ПОИСК
        add(
            SettingRow(
                section = SettingsSection.SEARCH,
                title = stringResource(R.string.settings_suggestions_title),
                description = stringResource(R.string.settings_suggestions_desc),
                control = SettingControl.Toggle(settings.searchSuggestions, viewModel::setSearchSuggestions),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.SEARCH,
                title = stringResource(R.string.settings_search_history_title),
                description = stringResource(R.string.settings_search_history_desc),
                control = SettingControl.Toggle(settings.saveSearchHistory, viewModel::setSaveSearchHistory),
            ),
        )

        // АККАУНТ
        add(
            SettingRow(
                section = SettingsSection.ACCOUNT,
                title = stringResource(R.string.settings_account_title),
                description =
                    if (signedIn) {
                        stringResource(R.string.settings_account_in, accountName ?: stringResource(R.string.settings_account_unnamed))
                    } else {
                        stringResource(R.string.settings_account_out)
                    },
                keywords = "google youtube",
                control =
                    if (signedIn) {
                        SettingControl.Action(stringResource(R.string.settings_account_sign_out), viewModel::signOut)
                    } else {
                        SettingControl.Action(stringResource(R.string.settings_account_sign_in), onOpenLogin)
                    },
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.ACCOUNT,
                title = stringResource(R.string.settings_sync_playlists_title),
                description =
                    if (signedIn) {
                        stringResource(R.string.settings_sync_playlists_desc)
                    } else {
                        stringResource(R.string.settings_sync_playlists_desc_out)
                    },
                keywords = "sync",
                control = SettingControl.Toggle(settings.syncPlaylists, viewModel::setSyncPlaylists),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.ACCOUNT,
                title = stringResource(R.string.settings_history_title),
                description = stringResource(R.string.settings_history_desc),
                control = SettingControl.Toggle(settings.keepHistory, viewModel::setKeepHistory),
            ),
        )

        // БЕЗ МАТА
        add(
            SettingRow(
                section = SettingsSection.CLEAN,
                title = stringResource(R.string.settings_clean_title),
                description = stringResource(R.string.settings_clean_desc),
                control = SettingControl.Toggle(settings.cleanMode, viewModel::setCleanMode),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.CLEAN,
                title = stringResource(R.string.settings_clean_fallback_title),
                description = stringResource(R.string.settings_clean_fallback_desc),
                control =
                    SettingControl.Choice(
                        options = ExplicitFallback.entries,
                        selected = settings.explicitFallback,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setExplicitFallback,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.CLEAN,
                title = stringResource(R.string.settings_hide_explicit_title),
                description = stringResource(R.string.settings_hide_explicit_desc),
                control = SettingControl.Toggle(settings.hideExplicit, viewModel::setHideExplicit),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.CLEAN,
                title = stringResource(R.string.settings_mute_swear_title),
                description = stringResource(R.string.settings_mute_swear_desc),
                control = SettingControl.Toggle(settings.muteSwearLines, viewModel::setMuteSwearLines),
            ),
        )

        // HAIRLINE
        addHairRows(viewModel, settings, context)

        // ВИД
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_style_title),
                description = stringResource(R.string.settings_style_desc),
                keywords = "pixel smooth",
                control =
                    SettingControl.Custom {
                        // Весь экран и есть живой предпросмотр: стиль меняется
                        // сразу, кругом от середины, а образец ниже показывает
                        // компоненты крупно.
                        val switcher = com.texfi.w0y.ui.shell.LocalStyleSwitcher.current
                        Column {
                            PixelSegmented(
                                options = com.texfi.w0y.data.UiStyle.entries.map { stringResource(it.label) },
                                selectedIndex = settings.uiStyle.ordinal,
                                onSelect = { switcher.switch(com.texfi.w0y.data.UiStyle.entries[it], null) },
                            )
                            if (settings.uiStyle == com.texfi.w0y.data.UiStyle.SMOOTH) {
                                Spacer(Modifier.height(10.dp))
                                PixelSegmented(
                                    options = listOf(stringResource(R.string.style_glass), stringResource(R.string.style_matte)),
                                    selectedIndex = if (settings.smoothGlass) 0 else 1,
                                    onSelect = { viewModel.setSmoothGlass(it == 0) },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            com.texfi.w0y.ui.shell.StyleSample()
                        }
                    },
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_theme_title),
                description = stringResource(R.string.settings_theme_desc),
                keywords = "oled",
                control =
                    SettingControl.Choice(
                        // Стекло видно только тем, кто открыл эксперименты:
                        // в семье TexFi оно чужое, и в общем списке сбивало бы.
                        options = ThemeMode.entries.filter { it != ThemeMode.GLASS || settings.experiments || settings.theme == it },
                        selected = settings.theme,
                        label = { themeLabel(it) },
                        onSelect = viewModel::setTheme,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_accent_title),
                description = stringResource(R.string.settings_accent_desc),
                keywords = "hex custom colour color swatch",
                control =
                    SettingControl.Custom {
                        AccentPicker(
                            selected = settings.accent,
                            customArgb = settings.customAccent,
                            onSelect = viewModel::setAccent,
                            onCustom = viewModel::setCustomAccent,
                        )
                    },
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_language_title),
                description = stringResource(R.string.settings_language_desc),
                control =
                    SettingControl.Choice(
                        options = Language.entries,
                        selected = settings.language,
                        label = { stringResource(it.label) },
                        // Экран пересоздаётся сразу после записи: ресурсы
                        // читаются при создании, и без этого новый язык
                        // появился бы только на части экрана.
                        onSelect = { choice ->
                            viewModel.setLanguage(choice) {
                                (context as? Activity)?.recreate()
                            }
                        },
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_start_tab_title),
                description = stringResource(R.string.settings_start_tab_desc),
                control =
                    SettingControl.Choice(
                        options = StartTab.entries,
                        selected = settings.startTab,
                        label = { stringResource(it.label) },
                        onSelect = viewModel::setStartTab,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_shelves_title),
                description = stringResource(R.string.settings_shelves_desc),
                control = SettingControl.Toggle(settings.showRecommendations, viewModel::setShowRecommendations),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_dial_min_title),
                description = stringResource(R.string.settings_dial_min_desc),
                keywords = "speed dial plays",
                control =
                    SettingControl.Choice(
                        options = listOf(1, 2, 3, 5, 10),
                        selected = settings.dialMinPlays,
                        label = { stringResource(R.string.settings_dial_min_value, it) },
                        onSelect = viewModel::setDialMinPlays,
                    ),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_live_background_title),
                description = stringResource(R.string.settings_live_background_desc),
                control = SettingControl.Toggle(settings.animatedBackground, viewModel::setAnimatedBackground),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_compact_title),
                description = stringResource(R.string.settings_compact_desc),
                control = SettingControl.Toggle(settings.compactRows, viewModel::setCompactRows),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.LOOK,
                title = stringResource(R.string.settings_haptics_title),
                description = stringResource(R.string.settings_haptics_desc),
                keywords = "vibration",
                control = SettingControl.Toggle(settings.haptics, viewModel::setHaptics),
            ),
        )

        // ДАННЫЕ
        add(
            SettingRow(
                section = SettingsSection.DATA,
                title = stringResource(R.string.settings_export_title),
                description = stringResource(R.string.settings_export_desc),
                keywords = "json",
                control = SettingControl.Action(stringResource(R.string.settings_export_button), onExport),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DATA,
                title = stringResource(R.string.settings_import_title),
                description = stringResource(R.string.settings_import_desc),
                keywords = "json",
                control = SettingControl.Action(stringResource(R.string.settings_import_button), onImport),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DATA,
                title = stringResource(R.string.settings_about_title),
                description = stringResource(R.string.settings_about_desc, appVersionLabel),
                keywords = "license agpl",
                control = SettingControl.Action(stringResource(R.string.settings_about_button), onOpenAbout),
            ),
        )
        add(
            SettingRow(
                section = SettingsSection.DATA,
                title = stringResource(R.string.settings_reset_title),
                description = stringResource(R.string.settings_reset_desc),
                keywords = "reset wipe clear factory",
                control = SettingControl.Action(stringResource(R.string.settings_reset_button), onFullReset),
            ),
        )
    }
}

@Composable
private fun qualityLabel(quality: Quality): String =
    when (quality) {
        Quality.LOW -> stringResource(R.string.quality_low)
        Quality.MEDIUM -> stringResource(R.string.quality_auto)
        Quality.HIGH -> stringResource(R.string.quality_high)
    }

@Composable
private fun themeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
        ThemeMode.OLED -> stringResource(R.string.theme_oled)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.GLASS -> stringResource(R.string.theme_glass)
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    }

/** Пункты раздела Hairline: у каждого свои подпись и описание, выбор из списка применяется сразу. */
private fun MutableList<SettingRow>.addHairRows(
    viewModel: SettingsViewModel,
    settings: com.texfi.w0y.data.W0ySettings,
    context: android.content.Context,
) {
    fun <T> choice(
        title: Int,
        desc: Int,
        options: List<T>,
        selected: T,
        label: @Composable (T) -> String,
        onSelect: (T) -> Unit,
        keywords: String = "",
    ) = add(
        SettingRow(
            section = SettingsSection.HAIR,
            title = context.getString(title),
            description = context.getString(desc),
            keywords = keywords,
            control = SettingControl.Choice(options, selected, label, onSelect),
        ),
    )
    choice(R.string.hl_set_weight, R.string.hl_set_weight_d, com.texfi.w0y.data.LineWeight.entries, settings.lineWeight, { stringResource(it.label) }, viewModel::setLineWeight, "line thickness hairline")
    choice(R.string.hl_set_corners, R.string.hl_set_corners_d, com.texfi.w0y.data.CornerStyle.entries, settings.corners, { stringResource(it.label) }, viewModel::setCorners, "radius round")
    choice(R.string.hl_set_cover, R.string.hl_set_cover_d, com.texfi.w0y.data.CoverShape.entries, settings.coverShape, { stringResource(it.label) }, viewModel::setCoverShape, "disc square artwork")
    choice(R.string.hl_set_layout, R.string.hl_set_layout_d, com.texfi.w0y.data.PlayerLayout.entries, settings.playerLayout, { stringResource(it.label) }, viewModel::setPlayerLayout, "player ring minimal compact")
    choice(R.string.hl_set_caps, R.string.hl_set_caps_d, com.texfi.w0y.data.CapsMode.entries, settings.caps, { stringResource(it.label) }, viewModel::setCaps, "uppercase capitals")
    choice(R.string.hl_set_density, R.string.hl_set_density_d, com.texfi.w0y.data.Density.entries, settings.density, { stringResource(it.label) }, viewModel::setDensity, "compact spacing")
    choice(R.string.hl_set_motion, R.string.hl_set_motion_d, com.texfi.w0y.data.MotionMode.entries, settings.motion, { stringResource(it.label) }, viewModel::setMotion, "animation reduce")
    choice(R.string.hl_set_haptics, R.string.hl_set_haptics_d, com.texfi.w0y.data.HapticLevel.entries, settings.hapticLevel, { stringResource(it.label) }, viewModel::setHapticLevel, "vibration")
    choice(R.string.hl_set_iconstyle, R.string.hl_set_iconstyle_d, com.texfi.w0y.data.IconStyle.entries, settings.iconStyle, { stringResource(it.label) }, viewModel::setIconStyle, "icons line filled dots")
    add(
        SettingRow(
            section = SettingsSection.HAIR,
            title = context.getString(R.string.hl_set_swipe),
            description = context.getString(R.string.hl_set_swipe_d),
            keywords = "mini player dismiss gesture",
            control = SettingControl.Toggle(settings.swipeDismiss, viewModel::setSwipeDismiss),
        ),
    )
    add(
        SettingRow(
            section = SettingsSection.HAIR,
            title = context.getString(R.string.hl_set_appicon),
            description = context.getString(R.string.hl_set_appicon_d),
            keywords = "launcher icon home screen",
            control = SettingControl.Custom { AppIconPicker(settings.appIcon, viewModel::setAppIcon) },
        ),
    )
    add(
        SettingRow(
            section = SettingsSection.HAIR,
            title = context.getString(R.string.hl_set_reset),
            description = context.getString(R.string.hl_set_reset_d),
            control = SettingControl.Action(context.getString(R.string.hl_reset_btn), viewModel::resetLook),
        ),
    )
}

/** Образец вида: живая обложка, кнопка и переключатель в текущих настройках. */
@Composable
private fun HairLookPreview(settings: com.texfi.w0y.data.W0ySettings) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(look.cardRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(shape)
            .border(look.line, colors.border, shape)
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(R.string.hl_preview).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(com.texfi.w0y.ui.theme.hairCoverShape(look))
                    .background(colors.surfaceHigh)
                    .border(look.line, colors.outline, com.texfi.w0y.ui.theme.hairCoverShape(look)),
                contentAlignment = Alignment.Center,
            ) { PixelSprite(rows = Sprites.note, color = colors.textMuted, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hl_preview_title).asTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.hl_preview_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                com.texfi.w0y.ui.components.PixelButton(text = stringResource(R.string.hl_preview_button), onClick = {})
            }
        }
    }
}

/** Выбор значка на рабочем столе: три формы маски рядом, предупреждение перед сменой. */
@Composable
private fun AppIconPicker(selected: com.texfi.w0y.data.AppIcon, onPick: (com.texfi.w0y.data.AppIcon) -> Unit) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    var pending by remember { mutableStateOf<com.texfi.w0y.data.AppIcon?>(null) }
    Column {
        Text(
            text = stringResource(R.string.hl_icon_warn),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            com.texfi.w0y.data.AppIcon.entries.forEach { icon ->
                val on = icon == selected
                Column(
                    Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(look.cardRadius.coerceAtMost(16.dp)))
                        .border(
                            look.line,
                            if (on) colors.accentText else colors.border,
                            androidx.compose.foundation.shape.RoundedCornerShape(look.cardRadius.coerceAtMost(16.dp)),
                        ).clickable { if (!on) pending = icon }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            androidx.compose.foundation.shape.CircleShape,
                            androidx.compose.foundation.shape.RoundedCornerShape(38),
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        ).forEach { mask ->
                            Box(Modifier.size(34.dp).clip(mask).background(Color(ICON_BG))) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(icon.foreground),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(icon.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) colors.text else colors.textMuted,
                    )
                }
            }
        }
    }
    pending?.let { icon ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.hl_set_appicon)) },
            text = { Text(stringResource(R.string.hl_icon_warn)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onPick(icon); pending = null }) { Text(stringResource(R.string.hl_icon_apply)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pending = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** Фон значка, как в ресурсе icon_bg: превью рисует маску поверх него. */
private const val ICON_BG = 0xFF0A090CL

/** Много вариантов в ряд не помещаются сегментами — переносим плашками. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HairChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(look.pillRadius.coerceAtMost(6.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(if (active) colors.text else androidx.compose.ui.graphics.Color.Transparent)
                    .then(if (active) Modifier else Modifier.border(look.line, colors.outline, shape))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.asLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) colors.background else colors.text,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Вкладки настроек Hairline: подписи моно, под активной скользит акцентная линия; выбранная вкладка сама подъезжает в видимую часть. */
@Composable
private fun HairTabStrip(tabs: List<Pair<String?, String>>, selected: String?, onSelect: (String?) -> Unit) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scroll = androidx.compose.foundation.rememberScrollState()
    val spots = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Int, Int>>() }
    val key = selected ?: ""
    val spot = spots[key]
    val spec = com.texfi.w0y.ui.components.hairSpec<Float>(com.texfi.w0y.ui.theme.HairTokens.MID_MS)
    val x by androidx.compose.animation.core.animateFloatAsState(spot?.first?.toFloat() ?: 0f, spec, label = "tabX")
    val w by androidx.compose.animation.core.animateFloatAsState(spot?.second?.toFloat() ?: 0f, spec, label = "tabW")
    androidx.compose.runtime.LaunchedEffect(spot, scroll.maxValue) {
        spot ?: return@LaunchedEffect
        val target = (spot.first - 60).coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(target, androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Row(Modifier.padding(horizontal = 18.dp)) {
                tabs.forEach { (tabKey, label) ->
                    val active = tabKey == selected
                    val tone by androidx.compose.animation.animateColorAsState(if (active) colors.text else colors.textMuted, androidx.compose.animation.core.tween(200), label = "tabTone")
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .onGloballyPositioned { spots[tabKey ?: ""] = it.positionInParent().x.toInt() to it.size.width }
                            .clickable { onSelect(tabKey) }
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label.asLabel(), style = com.texfi.w0y.ui.theme.HairLabelStyle, color = tone, maxLines = 1)
                    }
                }
            }
            if (spot != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { androidx.compose.ui.unit.IntOffset(x.toInt(), 0) }
                        .width(with(density) { w.toDp() })
                        .padding(horizontal = 11.dp)
                        .height(2.dp)
                        .background(colors.accent),
                )
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(look.line).background(colors.border))
    }
    Spacer(Modifier.height(14.dp))
}
