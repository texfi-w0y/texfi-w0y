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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
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
import com.texfi.w0y.ui.components.extendUnderIsland
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.texfi.w0y.data.SettingsCatalog
import com.texfi.w0y.data.SettingsCategory
import com.texfi.w0y.data.SettingsGroup
import com.texfi.w0y.ui.components.RowAction
import com.texfi.w0y.ui.components.RowActionsMenu
import com.texfi.w0y.ui.theme.Contrast

/**
 * Настройки: восемь вкладок сверху и поиск значком в шапке.
 *
 * Вкладки — тонкая полоса с подчёркиванием активной; последняя открытая помнится.
 * Внутри вкладки — строки: слева название и одна строка пояснения, справа значение
 * или переключатель; выбор из вариантов открывается небольшой нижней панелью. Поиск
 * (значок лупы) ищет по названию, пояснению и синонимам, переводит на нужную вкладку
 * и подсвечивает строку.
 *
 * Строки собираются данными; вкладку и группу пункта решает [SettingsCatalog], —
 * поэтому каждая настройка живёт ровно в одном месте.
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

    // Последняя открытая вкладка помнится: возвращаешься туда, где был.
    var tabName by rememberSaveable { mutableStateOf(SettingsTabMemory.last) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var tasteOpen by rememberSaveable { mutableStateOf(false) }
    // Куда прокрутить и что подсветить после перехода из поиска или избранного.
    var target by remember { mutableStateOf<String?>(null) }
    var choiceId by remember { mutableStateOf<String?>(null) }
    var detailsId by remember { mutableStateOf<String?>(null) }

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
        val experiments = currentSettings.experiments
        BackHandler { aboutOpen = false }
        AboutScreen(
            onBack = { aboutOpen = false },
            experiments = experiments,
            onUnlockExperiments = viewModel::unlockExperiments,
        )
        return
    }

    if (tasteOpen) {
        BackHandler { tasteOpen = false }
        TasteScreen(onBack = { tasteOpen = false })
        return
    }

    val rows =
        settingsRows(
            viewModel = viewModel,
            onOpenLogin = onOpenLogin,
            onOpenAbout = { aboutOpen = true },
            onOpenTaste = { tasteOpen = true },
            onFullReset = { resetPending = true },
            onExport = { exportLauncher.launch("w0y-settings.json") },
            onImport = { importLauncher.launch(arrayOf("application/json")) },
            onAutoExport = { on -> if (on) withCopyrightNotice { viewModel.setAutoExport(true) } else viewModel.setAutoExport(false) },
            onPickBackdrop = { backdropLauncher.launch(arrayOf("image/*")) },
        ).filter { SettingsCatalog.entry(it.id) != null }.sortedBy { SettingsCatalog.indexOf(it.id) }
    val category = SettingsCategory.entries.firstOrNull { it.name == tabName } ?: SettingsCategory.PLAYBACK

    // Назад: сначала закрывает поиск, потом настройки целиком.
    BackHandler(enabled = searchOpen || query.isNotBlank()) {
        if (query.isNotBlank()) query = "" else searchOpen = false
    }

    val openRow: (SettingRow) -> Unit = { row ->
        tabName = row.category.name
        SettingsTabMemory.last = tabName
        query = ""
        searchOpen = false
        target = row.id
    }
    val ctx = RowContext(
        onChoose = { choiceId = it },
        onDetails = { detailsId = it },
    )

    Box(Modifier.fillMaxSize()) {
    // Фон листа продолжается за островком до самого низа; список кончается над островком.
    Box(Modifier.fillMaxSize().extendUnderIsland().screenBackground())
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ScreenTitle(
            title = stringResource(R.string.settings_title),
            onBack = onClose,
            actions = {
                val searchCd = stringResource(R.string.settings_search_placeholder)
                Box(
                    Modifier
                        .size(48.dp)
                        .testTag("settings-search")
                        .semantics { contentDescription = searchCd; role = Role.Button }
                        .clickable {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    com.texfi.w0y.ui.icons.HairIcon(
                        com.texfi.w0y.ui.icons.HairGlyph.SEARCH,
                        if (searchOpen) colors.accent else colors.textMuted,
                        Modifier.size(22.dp),
                    )
                }
            },
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        if (searchOpen) {
            SettingsSearchField(value = query, onValueChange = { query = it })
            Spacer(Modifier.height(8.dp))
        }
        SettingsTabStrip(
            tabs = SettingsCategory.entries.map { it.name to stringResource(it.titleRes()) },
            selected = category.name,
            onSelect = {
                tabName = it
                SettingsTabMemory.last = it
            },
        )

        when {
            query.isNotBlank() -> SearchResults(rows, query, onOpen = openRow)
            else -> CategoryList(category, rows, viewModel, currentSettings, ctx, target, onTargetShown = { target = null })
        }
    }

    // Панель остаётся в композиции, чтобы уезжать вниз анимацией; содержимое помнит последнюю строку.
    val choiceRow = rows.firstOrNull { it.id == choiceId }
    var lastChoice by remember { mutableStateOf<SettingRow?>(null) }
    if (choiceRow != null) lastChoice = choiceRow
    ChoiceSheet(visible = choiceRow != null, row = lastChoice, onDismiss = { choiceId = null })
    }

    detailsId?.let { id ->
        rows.firstOrNull { it.id == id }?.let { row ->
            ConfirmPanel(
                title = row.title,
                text = row.description,
                confirm = stringResource(R.string.common_ok),
                dismiss = stringResource(R.string.common_cancel),
                onConfirm = { detailsId = null },
                onDismiss = { detailsId = null },
            )
            BackHandler { detailsId = null }
        }
    }
}

/** Заголовок категории: список в [SettingsCategory] общий, подписи — ресурсами. */
@StringRes
private fun SettingsCategory.titleRes(): Int =
    when (this) {
        SettingsCategory.PLAYBACK -> R.string.cat_playback
        SettingsCategory.SOUND -> R.string.cat_sound
        SettingsCategory.STAND -> R.string.cat_stand
        SettingsCategory.STORAGE -> R.string.cat_storage
        SettingsCategory.SEARCH_FEEDS -> R.string.cat_search_feeds
        SettingsCategory.LOOK -> R.string.cat_look
        SettingsCategory.ACCOUNT -> R.string.cat_account
        SettingsCategory.DATA -> R.string.cat_data
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
 * Категорию и группу пункта задаёт [SettingsCatalog] по [id].
 */
private class SettingRow(
    val id: String,
    val title: String,
    val description: String,
    val control: SettingControl,
    /** Слова, по которым пункт тоже должен находиться. */
    val keywords: String = "",
) {
    val category: SettingsCategory get() = SettingsCatalog.categoryOf(id) ?: SettingsCategory.DATA
    val group: SettingsGroup? get() = SettingsCatalog.entry(id)?.group
}

/** Что нужно строке от экрана: открытие панелей. */
private class RowContext(
    val onChoose: (String) -> Unit,
    val onDetails: (String) -> Unit,
)

/** Текущее значение пункта одной строкой; у действий и своих виджетов его нет. */
@Composable
private fun valueText(row: SettingRow): String? =
    when (val c = row.control) {
        is SettingControl.Toggle -> stringResource(if (c.checked) R.string.val_on else R.string.val_off)
        is SettingControl.Choice<*> -> {
            @Suppress("UNCHECKED_CAST")
            val typed = c as SettingControl.Choice<Any?>
            typed.label(typed.selected)
        }
        is SettingControl.Info -> c.value
        is SettingControl.Action, is SettingControl.Custom -> null
    }

/** Заголовок группы: моно, заглавные, приглушённо. */
@Composable
private fun MonoHeader(text: String) {
    val colors = LocalW0yColors.current
    Text(
        text = text.asLabel(),
        style = com.texfi.w0y.ui.theme.HairLabelStyle,
        color = colors.textMuted,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun RowDivider() {
    val colors = LocalW0yColors.current
    Box(Modifier.fillMaxWidth().height(com.texfi.w0y.ui.theme.hairLook.line).background(colors.border))
}

/** Содержимое категории: группы с моно-заголовками, строки; при переходе из поиска — прокрутка и подсветка. */
@Composable
private fun CategoryList(
    category: SettingsCategory,
    rows: List<SettingRow>,
    viewModel: SettingsViewModel,
    settings: com.texfi.w0y.data.W0ySettings,
    ctx: RowContext,
    target: String?,
    onTargetShown: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val unordered = rows.filter { it.category == category }
    // Группа идёт одним куском, в порядке первого появления в каталоге.
    val groupOrder = unordered.map { it.group }.distinct()
    val inCategory = unordered.sortedBy { groupOrder.indexOf(it.group) }
    val groups = groupOrder
    // Элементы списка: заголовок группы (только если групп больше одной) и строки.
    val entries = ArrayList<Pair<String, SettingRow?>>()
    var prev: SettingsGroup? = null
    inCategory.forEachIndexed { i, row ->
        if (groups.size > 1 && (i == 0 || row.group != prev)) entries += ("g-" + row.group?.name) to null
        entries += row.id to row
        prev = row.group
    }
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    var highlighted by remember { mutableStateOf<String?>(null) }
    val motionFull = com.texfi.w0y.ui.theme.hairLook.motion == com.texfi.w0y.ui.theme.MotionLevel.FULL
    LaunchedEffect(target, category) {
        val id = target ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.first == id }
        if (index >= 0) {
            // Заголовок группы над строкой тоже должен быть виден.
            val top = if (index > 0 && entries[index - 1].second == null) index - 1 else index
            if (motionFull) state.animateScrollToItem(top) else state.scrollToItem(top)
            highlighted = id
            onTargetShown()
            // Контур держится 1,2 секунды; в Motion Lite/Off он появляется и исчезает без анимации.
            kotlinx.coroutines.delay(1200)
            highlighted = null
        } else {
            onTargetShown()
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("settings-list"),
        state = state,
        contentPadding = com.texfi.w0y.ui.components.bottomContentPadding(start = 18.dp, end = 18.dp),
    ) {
        if (category == SettingsCategory.LOOK) {
            item(key = "look-preview") { HairLookPreview(settings) }
        }
        items(entries, key = { it.first }) { (key, row) ->
            if (row == null) {
                val g = SettingsGroup.entries.first { "g-" + it.name == key }
                MonoHeader(stringResource(g.title))
            } else {
                SettingRowItem(row, ctx, highlighted = highlighted == row.id)
                RowDivider()
            }
        }
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }
}

/** Результаты поиска: название, пояснение и категория; нажатие открывает категорию и подсвечивает пункт. */
@Composable
private fun SearchResults(rows: List<SettingRow>, query: String, onOpen: (SettingRow) -> Unit) {
    val colors = LocalW0yColors.current
    val titles = SettingsCategory.entries.associateWith { stringResource(it.titleRes()) }
    val found =
        SettingsSearch.search(
            query,
            rows.map { SearchEntry(it.id, it.title, it.description, it.keywords, titles.getValue(it.category)) },
        ).mapNotNull { e -> rows.firstOrNull { it.id == e.id } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = com.texfi.w0y.ui.components.bottomContentPadding(start = 18.dp, end = 18.dp),
    ) {
        if (found.isEmpty()) {
            item {
                EmptyState(
                    sprite = Sprites.search,
                    title = stringResource(R.string.settings_search_nothing_title),
                    text = stringResource(R.string.settings_search_nothing_text),
                )
            }
        } else {
            items(found, key = { "found-" + it.id }) { row ->
                val open = stringResource(R.string.settings_open_cd, row.title)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = open; role = Role.Button }
                        .clickable { onOpen(row) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        titles.getValue(row.category).asLabel(),
                        style = com.texfi.w0y.ui.theme.HairLabelStyle,
                        color = Contrast.ensure(colors.accent, colors.background),
                    )
                    Text(row.title, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 2)
                    Text(row.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                RowDivider()
            }
        }
    }
}

/** Выбор из вариантов — нижней панелью; выбранное закрывает её. */
@Composable
private fun ChoiceSheet(visible: Boolean, row: SettingRow?, onDismiss: () -> Unit) {
    val colors = LocalW0yColors.current
    HairSheetFrame(visible = visible, onDismiss = onDismiss, heightFraction = null) {
        val control = row?.control as? SettingControl.Choice<*> ?: return@HairSheetFrame
        @Suppress("UNCHECKED_CAST")
        val typed = control as SettingControl.Choice<Any?>
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(row.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            MenuChips(
                title = stringResource(R.string.settings_choose),
                hint = null,
                options = typed.options.map { typed.label(it) },
                selected = typed.options.indexOf(typed.selected),
                onSelect = {
                    typed.onSelect(typed.options[it])
                    onDismiss()
                },
            )
        }
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

/**
 * Одна строка настроек: слева название и одна строка пояснения, справа значение,
 * переключатель или кнопка. Долгое нажатие — меню (избранное, подробности).
 * Выбор из вариантов открывает нижнюю панель. [highlighted] рисует контур акцента.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SettingRowItem(row: SettingRow, ctx: RowContext, highlighted: Boolean) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val control = row.control
    val outlineAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        // Без анимации в Motion Lite/Off: контур просто появляется и пропадает.
        animationSpec = if (look.motion == com.texfi.w0y.ui.theme.MotionLevel.FULL) androidx.compose.animation.core.tween(220) else androidx.compose.animation.core.snap(),
        label = "rowOutline",
    )
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(look.cardRadius.coerceAtMost(12.dp))
    val primary: (() -> Unit)? =
        when (control) {
            is SettingControl.Toggle -> ({ control.onChange(!control.checked) })
            is SettingControl.Choice<*> -> ({ ctx.onChoose(row.id) })
            is SettingControl.Action -> control.onClick
            else -> null
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (outlineAlpha > 0f) Modifier.border(2.dp, Contrast.ensure(colors.accent, colors.background).copy(alpha = outlineAlpha), shape) else Modifier)
            .combinedClickable(
                enabled = primary != null || true,
                onClick = { primary?.invoke() },
                onLongClick = { ctx.onDetails(row.id) },
            )
            .padding(horizontal = if (outlineAlpha > 0f) 8.dp else 0.dp, vertical = 10.dp)
            .heightIn(min = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    row.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            when (control) {
                is SettingControl.Toggle -> PixelSwitch(checked = control.checked, onCheckedChange = control.onChange)

                is SettingControl.Choice<*> -> {
                    Text(
                        valueText(row).orEmpty().asLabel(),
                        style = com.texfi.w0y.ui.theme.HairLabelStyle,
                        color = Contrast.ensure(colors.accent, colors.background),
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.widthIn(max = 150.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    com.texfi.w0y.ui.icons.HairIcon(com.texfi.w0y.ui.icons.HairGlyph.CHEVRON_RIGHT, colors.textMuted, Modifier.size(16.dp))
                }

                is SettingControl.Action -> PixelButton(text = control.button, onClick = control.onClick, fill = colors.surfaceHigh)

                is SettingControl.Info ->
                    Text(control.value.asLabel(), style = com.texfi.w0y.ui.theme.HairLabelStyle, color = Contrast.ensure(colors.accent, colors.background))

                is SettingControl.Custom -> Unit
            }
        }
        if (control is SettingControl.Custom) {
            Spacer(Modifier.height(10.dp))
            control.content()
        }
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
    onOpenTaste: () -> Unit,
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
                id = "settings_quality_wifi_title",
                title = stringResource(R.string.settings_quality_wifi_title),
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
                id = "settings_quality_mobile_title",
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
                id = "settings_normalize_title",
                title = stringResource(R.string.settings_normalize_title),
                description = stringResource(R.string.settings_normalize_desc),
                control = SettingControl.Toggle(settings.normalizeVolume, viewModel::setNormalize),
            ),
        )
        add(
            SettingRow(
                id = "settings_skip_silence_title",
                title = stringResource(R.string.settings_skip_silence_title),
                description = stringResource(R.string.settings_skip_silence_desc),
                control = SettingControl.Toggle(settings.skipSilence, viewModel::setSkipSilence),
            ),
        )

        add(
            SettingRow(
                id = "settings_fallback_title",
                title = stringResource(R.string.settings_fallback_title),
                description = stringResource(R.string.settings_fallback_desc),
                keywords = "fallback piped alternative not loading",
                control = SettingControl.Toggle(settings.fallbackAudio, viewModel::setFallbackAudio),
            ),
        )
        add(
            SettingRow(
                id = "settings_fallback_source_title",
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
                id = "settings_speed_title",
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
                id = "settings_pitch_title",
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
                id = "settings_reverb_title",
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
                id = "settings_equalizer_title",
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
                id = "settings_queue_title",
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
                id = "settings_seek_step_title",
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
                id = "settings_cover_glow_title",
                title = stringResource(R.string.settings_cover_glow_title),
                description = stringResource(R.string.settings_cover_glow_desc),
                control = SettingControl.Toggle(settings.playerCoverGlow, viewModel::setPlayerCoverGlow),
            ),
        )
        add(
            SettingRow(
                id = "settings_lyrics_title",
                title = stringResource(R.string.settings_lyrics_title),
                description = stringResource(R.string.settings_lyrics_desc),
                control = SettingControl.Toggle(settings.showLyrics, viewModel::setShowLyrics),
            ),
        )
        add(
            SettingRow(
                id = "settings_pause_headphones_title",
                title = stringResource(R.string.settings_pause_headphones_title),
                description = stringResource(R.string.settings_pause_headphones_desc),
                control = SettingControl.Toggle(settings.pauseOnHeadphonesOut, viewModel::setPauseOnUnplug),
            ),
        )
        add(
            SettingRow(
                id = "settings_resume_headphones_title",
                title = stringResource(R.string.settings_resume_headphones_title),
                description = stringResource(R.string.settings_resume_headphones_desc),
                control = SettingControl.Toggle(settings.resumeOnHeadphonesIn, viewModel::setResumeOnPlug),
            ),
        )
        add(
            SettingRow(
                id = "settings_playlist_recs",
                title = stringResource(R.string.settings_playlist_recs),
                description = stringResource(R.string.settings_playlist_recs_hint),
                control = SettingControl.Toggle(settings.showPlaylistRecommendations, viewModel::setShowPlaylistRecommendations),
            ),
        )
        add(
            SettingRow(
                id = "viz_style_title",
                title = stringResource(R.string.viz_style_title),
                description = stringResource(R.string.viz_style_desc),
                keywords = "visualizer spectrum",
                control = SettingControl.Choice(com.texfi.w0y.data.VisualizerStyle.entries.filter { if (settings.uiStyle == com.texfi.w0y.data.UiStyle.HAIRLINE) it.hair else (!it.hair && it.smooth == (settings.uiStyle != com.texfi.w0y.data.UiStyle.PIXEL)) }, settings.visualizerStyle.forUi(settings.uiStyle), { stringResource(it.label) }, viewModel::setVisualizerStyle),
            ),
        )
        add(
            SettingRow(
                id = "viz_sensitivity_title",
                title = stringResource(R.string.viz_sensitivity_title),
                description = stringResource(R.string.viz_sensitivity_desc),
                control = SettingControl.Choice(listOf(0.5f, 1f, 1.5f, 2f), settings.visualizerSensitivity, { com.texfi.w0y.data.NumberText.speed(it) }, viewModel::setVisualizerSensitivity),
            ),
        )
        add(
            SettingRow(
                id = "viz_fps_title",
                title = stringResource(R.string.viz_fps_title),
                description = stringResource(R.string.viz_fps_desc),
                control = SettingControl.Choice(listOf(30, 60), settings.visualizerFps, { it.toString() }, viewModel::setVisualizerFps),
            ),
        )
        add(
            SettingRow(
                id = "viz_backdrop_title",
                title = stringResource(R.string.viz_backdrop_title),
                description = stringResource(R.string.viz_backdrop_desc),
                control = SettingControl.Toggle(settings.visualizerCoverBackdrop, viewModel::setVisualizerCoverBackdrop),
            ),
        )
        add(
            SettingRow(
                id = "lyrics_size_title",
                title = stringResource(R.string.lyrics_size_title),
                description = stringResource(R.string.lyrics_size_desc),
                keywords = "lyrics text",
                control = SettingControl.Choice(com.texfi.w0y.data.LyricsSize.entries, settings.lyricsSize, { stringResource(it.label) }, viewModel::setLyricsSize),
            ),
        )
        add(
            SettingRow(
                id = "lyrics_delay_title",
                title = stringResource(R.string.lyrics_delay_title),
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
                id = "lyrics_sources_title",
                title = stringResource(R.string.lyrics_sources_title),
                description = stringResource(R.string.lyrics_sources_desc),
                keywords = "lyrics genius netease search",
                control = SettingControl.Toggle(settings.lyricsAllSources, viewModel::setLyricsAllSources),
            ),
        )
        add(
            SettingRow(
                id = "stand_clock_title",
                title = stringResource(R.string.stand_clock_title),
                description = stringResource(R.string.stand_clock_desc),
                keywords = "stand clock time",
                control = SettingControl.Toggle(settings.standClock, viewModel::setStandClock),
            ),
        )
        add(
            SettingRow(
                id = "stand_lyrics_title",
                title = stringResource(R.string.stand_lyrics_title),
                description = stringResource(R.string.stand_lyrics_desc),
                keywords = "stand always on",
                control = SettingControl.Toggle(settings.standLyrics, viewModel::setStandLyrics),
            ),
        )
        add(
            SettingRow(
                id = "stand_viz_title",
                title = stringResource(R.string.stand_viz_title),
                description = stringResource(R.string.stand_viz_desc),
                keywords = "stand always on",
                control = SettingControl.Toggle(settings.standVisualizer, viewModel::setStandVisualizer),
            ),
        )
        add(
            SettingRow(
                id = "stand_brightness_title",
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
                id = "backdrop_title",
                title = stringResource(R.string.backdrop_title),
                description = stringResource(R.string.backdrop_desc),
                keywords = "background wallpaper aurora photo grid black topo contours lines контуры обои",
                control =
                    SettingControl.Choice(
                        com.texfi.w0y.data.BackdropMode.entries,
                        settings.backdropMode,
                        {
                            stringResource(
                                when (it) {
                                    com.texfi.w0y.data.BackdropMode.TOPO -> R.string.backdrop_topo
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
                id = "backdrop_image_title",
                title = stringResource(R.string.backdrop_image_title),
                description = stringResource(R.string.backdrop_image_desc),
                keywords = "background photo image gallery",
                control = SettingControl.Action(stringResource(R.string.backdrop_pick), onPickBackdrop),
            ),
        )
        add(
            SettingRow(
                id = "season_title",
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
                id = "stand_view_title",
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
                id = "stand_marquee_title",
                title = stringResource(R.string.stand_marquee_title),
                description = stringResource(R.string.stand_marquee_desc),
                keywords = "stand always on marquee",
                control = SettingControl.Toggle(settings.standMarquee, viewModel::setStandMarquee),
            ),
        )
        add(
            SettingRow(
                id = "stand_flip_title",
                title = stringResource(R.string.stand_flip_title),
                description = stringResource(R.string.stand_flip_desc),
                keywords = "stand always on flip upside down rotate",
                control = SettingControl.Toggle(settings.standFlipped, viewModel::setStandFlipped),
            ),
        )
        add(
            SettingRow(
                id = "stand_charging_title",
                title = stringResource(R.string.stand_charging_title),
                description = stringResource(R.string.stand_charging_desc),
                keywords = "stand always on",
                control = SettingControl.Toggle(settings.standChargingOnly, viewModel::setStandChargingOnly),
            ),
        )
        add(
            SettingRow(
                id = "stand_max_title",
                title = stringResource(R.string.stand_max_title),
                description = stringResource(R.string.stand_max_desc),
                keywords = "stand always on",
                control = SettingControl.Choice(listOf(0, 30, 60, 120), settings.standMaxMinutes, { if (it == 0) stringResource(R.string.dl_no_limit) else stringResource(R.string.settings_sleep_minutes, it) }, viewModel::setStandMaxMinutes),
            ),
        )
        add(
            SettingRow(
                id = "stand_battery_title",
                title = stringResource(R.string.stand_battery_title),
                description = stringResource(R.string.stand_battery_desc),
                keywords = "stand always on",
                control = SettingControl.Toggle(settings.standLowBatteryExit, viewModel::setStandLowBatteryExit),
            ),
        )
        add(
            SettingRow(
                id = "settings_sleep_title",
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
                id = "settings_startup_title",
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
                    id = "settings_startup_parts_title",
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
                id = "settings_preload_title",
                title = stringResource(R.string.settings_preload_title),
                description = stringResource(R.string.settings_preload_desc),
                control = SettingControl.Toggle(settings.preloadNext, viewModel::setPreload),
            ),
        )

        // ПАМЯТЬ
        add(
            SettingRow(
                id = "settings_wifi_only_title",
                title = stringResource(R.string.settings_wifi_only_title),
                description = stringResource(R.string.settings_wifi_only_desc),
                control = SettingControl.Toggle(settings.downloadOnWifiOnly, viewModel::setDownloadOnWifiOnly),
            ),
        )
        add(
            SettingRow(
                id = "settings_cache_title",
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
                id = "settings_clear_cache_title",
                title = stringResource(R.string.settings_clear_cache_title),
                description = stringResource(R.string.settings_clear_cache_desc),
                control = SettingControl.Action(stringResource(R.string.settings_clear_cache_button), viewModel::clearCache),
            ),
        )
        add(
            SettingRow(
                id = "settings_autodownload_title",
                title = stringResource(R.string.settings_autodownload_title),
                description = stringResource(R.string.settings_autodownload_desc),
                control = SettingControl.Toggle(settings.autoDownloadLiked, viewModel::setAutoDownload),
            ),
        )
        add(
            SettingRow(
                id = "dl_auto_playlists_title",
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
                id = "dl_quality_wifi_title",
                title = stringResource(R.string.dl_quality_wifi_title),
                description = stringResource(R.string.dl_quality_desc),
                control = SettingControl.Choice(Quality.entries, settings.downloadQualityWifi, { qualityLabel(it) }, viewModel::setDownloadQualityWifi),
            ),
        )
        add(
            SettingRow(
                id = "dl_quality_mobile_title",
                title = stringResource(R.string.dl_quality_mobile_title),
                description = stringResource(R.string.dl_quality_desc),
                control = SettingControl.Choice(Quality.entries, settings.downloadQualityMobile, { qualityLabel(it) }, viewModel::setDownloadQualityMobile),
            ),
        )
        add(
            SettingRow(
                id = "dl_format_title",
                title = stringResource(R.string.dl_format_title),
                description = stringResource(R.string.dl_format_desc),
                control = SettingControl.Info(stringResource(R.string.dl_format_value)),
            ),
        )
        add(
            SettingRow(
                id = "dl_parallel_title",
                title = stringResource(R.string.dl_parallel_title),
                description = stringResource(R.string.dl_parallel_desc),
                control = SettingControl.Choice(listOf(1, 2, 3, 4), settings.downloadParallel, { it.toString() }, viewModel::setDownloadParallel),
            ),
        )
        add(
            SettingRow(
                id = "dl_speed_limit_title",
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
                id = "dl_retries_title",
                title = stringResource(R.string.dl_retries_title),
                description = stringResource(R.string.dl_retries_desc),
                control = SettingControl.Choice(listOf(0, 1, 3, 5), settings.downloadRetries, { it.toString() }, viewModel::setDownloadRetries),
            ),
        )
        add(
            SettingRow(
                id = "dl_low_battery_title",
                title = stringResource(R.string.dl_low_battery_title),
                description = stringResource(R.string.dl_low_battery_desc),
                control = SettingControl.Toggle(settings.downloadPauseOnLowBattery, viewModel::setDownloadPauseOnLowBattery),
            ),
        )
        add(
            SettingRow(
                id = "dl_auto_resume_title",
                title = stringResource(R.string.dl_auto_resume_title),
                description = stringResource(R.string.dl_auto_resume_desc),
                control = SettingControl.Toggle(settings.downloadAutoResume, viewModel::setDownloadAutoResume),
            ),
        )
        add(
            SettingRow(
                id = "dl_notifications_title",
                title = stringResource(R.string.dl_notifications_title),
                description = stringResource(R.string.dl_notifications_desc),
                control = SettingControl.Toggle(settings.downloadNotifications, viewModel::setDownloadNotifications),
            ),
        )
        add(
            SettingRow(
                id = "dl_auto_export_title",
                title = stringResource(R.string.dl_auto_export_title),
                description = stringResource(R.string.dl_auto_export_desc),
                control = SettingControl.Toggle(settings.autoExport, onAutoExport),
            ),
        )
        add(
            SettingRow(
                id = "dl_storage_limit_title",
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
                id = "dl_verify_title",
                title = stringResource(R.string.dl_verify_title),
                description = stringResource(R.string.dl_verify_desc),
                control = SettingControl.Action(stringResource(R.string.dl_verify_button), viewModel::verifyDownloads),
            ),
        )
        add(
            SettingRow(
                id = "dl_clear_title",
                title = stringResource(R.string.dl_clear_title),
                description = stringResource(R.string.dl_clear_desc),
                control = SettingControl.Action(stringResource(R.string.dl_clear_button), viewModel::clearDownloads),
            ),
        )

        // ПОИСК
        add(
            SettingRow(
                id = "settings_suggestions_title",
                title = stringResource(R.string.settings_suggestions_title),
                description = stringResource(R.string.settings_suggestions_desc),
                control = SettingControl.Toggle(settings.searchSuggestions, viewModel::setSearchSuggestions),
            ),
        )
        add(
            SettingRow(
                id = "settings_search_history_title",
                title = stringResource(R.string.settings_search_history_title),
                description = stringResource(R.string.settings_search_history_desc),
                control = SettingControl.Toggle(settings.saveSearchHistory, viewModel::setSaveSearchHistory),
            ),
        )

        // АККАУНТ
        add(
            SettingRow(
                id = "settings_account_title",
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
                id = "settings_sync_playlists_title",
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
                id = "settings_history_title",
                title = stringResource(R.string.settings_history_title),
                description = stringResource(R.string.settings_history_desc),
                control = SettingControl.Toggle(settings.keepHistory, viewModel::setKeepHistory),
            ),
        )

        // БЕЗ МАТА
        add(
            SettingRow(
                id = "settings_clean_title",
                title = stringResource(R.string.settings_clean_title),
                description = stringResource(R.string.settings_clean_desc),
                control = SettingControl.Toggle(settings.cleanMode, viewModel::setCleanMode),
            ),
        )
        add(
            SettingRow(
                id = "settings_clean_fallback_title",
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
                id = "settings_hide_explicit_title",
                title = stringResource(R.string.settings_hide_explicit_title),
                description = stringResource(R.string.settings_hide_explicit_desc),
                control = SettingControl.Toggle(settings.hideExplicit, viewModel::setHideExplicit),
            ),
        )
        add(
            SettingRow(
                id = "settings_mute_swear_title",
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
                id = "settings_style_title",
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
                id = "settings_theme_title",
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
                id = "settings_accent_title",
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
                id = "settings_language_title",
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
                id = "settings_start_tab_title",
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
                id = "settings_shelves_title",
                title = stringResource(R.string.settings_shelves_title),
                description = stringResource(R.string.settings_shelves_desc),
                control = SettingControl.Toggle(settings.showRecommendations, viewModel::setShowRecommendations),
            ),
        )
        add(
            SettingRow(
                id = "settings_dial_min_title",
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
                id = "settings_live_background_title",
                title = stringResource(R.string.settings_live_background_title),
                description = stringResource(R.string.settings_live_background_desc),
                control = SettingControl.Toggle(settings.animatedBackground, viewModel::setAnimatedBackground),
            ),
        )
        add(
            SettingRow(
                id = "settings_compact_title",
                title = stringResource(R.string.settings_compact_title),
                description = stringResource(R.string.settings_compact_desc),
                control = SettingControl.Toggle(settings.compactRows, viewModel::setCompactRows),
            ),
        )
        add(
            SettingRow(
                id = "settings_haptics_title",
                title = stringResource(R.string.settings_haptics_title),
                description = stringResource(R.string.settings_haptics_desc),
                keywords = "vibration",
                control = SettingControl.Toggle(settings.haptics, viewModel::setHaptics),
            ),
        )

        // ДАННЫЕ
        add(
            SettingRow(
                id = "settings_export_title",
                title = stringResource(R.string.settings_export_title),
                description = stringResource(R.string.settings_export_desc),
                keywords = "json",
                control = SettingControl.Action(stringResource(R.string.settings_export_button), onExport),
            ),
        )
        add(
            SettingRow(
                id = "settings_import_title",
                title = stringResource(R.string.settings_import_title),
                description = stringResource(R.string.settings_import_desc),
                keywords = "json",
                control = SettingControl.Action(stringResource(R.string.settings_import_button), onImport),
            ),
        )
        // Обновления с GitHub: автопроверка, ручная проверка и установка, описание своей версии.
        add(
            SettingRow(
                id = "update_check",
                title = stringResource(R.string.update_check_title),
                description = stringResource(R.string.update_check_desc),
                keywords = "update github obtainium version обновление проверка",
                control = SettingControl.Toggle(settings.updateCheck, viewModel::setUpdateCheck),
            ),
        )
        val updateState by viewModel.updateState.collectAsStateWithLifecycle()
        val installed = appVersionLabel
        val updateInfo =
            when (val s = updateState) {
                is com.texfi.w0y.data.UpdateState.Available -> s.info
                is com.texfi.w0y.data.UpdateState.Downloading -> s.info
                is com.texfi.w0y.data.UpdateState.Ready -> s.info
                is com.texfi.w0y.data.UpdateState.Failed -> s.info
                else -> null
            }
        val updateText =
            when (val s = updateState) {
                is com.texfi.w0y.data.UpdateState.Idle -> stringResource(R.string.update_desc_idle, installed)
                is com.texfi.w0y.data.UpdateState.Checking -> stringResource(R.string.update_desc_checking)
                is com.texfi.w0y.data.UpdateState.UpToDate -> stringResource(R.string.update_desc_latest, installed)
                is com.texfi.w0y.data.UpdateState.Available -> stringResource(R.string.update_desc_available, s.info.label, installed)
                is com.texfi.w0y.data.UpdateState.Downloading ->
                    stringResource(R.string.update_desc_downloading, s.info.label, if (s.total > 0) (s.done * 100 / s.total).toInt() else 0)
                is com.texfi.w0y.data.UpdateState.Ready -> stringResource(R.string.update_desc_ready, s.info.label)
                is com.texfi.w0y.data.UpdateState.Failed -> com.texfi.w0y.ui.shell.updateReasonText(s.reason)
            }
        val updateButton =
            when (val s = updateState) {
                is com.texfi.w0y.data.UpdateState.Available -> stringResource(R.string.update_btn_update) to { viewModel.downloadUpdate(s.info) }
                is com.texfi.w0y.data.UpdateState.Ready -> stringResource(R.string.update_btn_install) to { viewModel.installUpdate(s.file); Unit }
                is com.texfi.w0y.data.UpdateState.Downloading -> stringResource(R.string.update_btn_wait) to { }
                is com.texfi.w0y.data.UpdateState.Checking -> stringResource(R.string.update_btn_wait) to { }
                is com.texfi.w0y.data.UpdateState.Failed ->
                    if (updateInfo != null) stringResource(R.string.update_btn_retry) to { viewModel.downloadUpdate(updateInfo) }
                    else stringResource(R.string.update_btn_check) to { viewModel.checkUpdate() }
                else -> stringResource(R.string.update_btn_check) to { viewModel.checkUpdate() }
            }
        add(
            SettingRow(
                id = "update_now",
                title = stringResource(R.string.update_title),
                description = updateText,
                keywords = "update install download github обновить установить версия",
                control = SettingControl.Action(updateButton.first, updateButton.second),
            ),
        )
        val whatsNewScope = androidx.compose.runtime.rememberCoroutineScope()
        val whatsNewContext = androidx.compose.ui.platform.LocalContext.current
        val whatsNewFail = stringResource(R.string.whatsnew_fail)
        add(
            SettingRow(
                id = "update_whatsnew",
                title = stringResource(R.string.whatsnew_title),
                description = stringResource(R.string.whatsnew_desc),
                keywords = "changelog release notes what's new что нового изменения",
                control =
                    SettingControl.Action(stringResource(R.string.whatsnew_btn)) {
                        whatsNewScope.launch {
                            if (!viewModel.showWhatsNew()) android.widget.Toast.makeText(whatsNewContext, whatsNewFail, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
            ),
        )
        add(
            SettingRow(
                id = "settings_about_title",
                title = stringResource(R.string.settings_about_title),
                description = stringResource(R.string.settings_about_desc, appVersionLabel),
                keywords = "license agpl",
                control = SettingControl.Action(stringResource(R.string.settings_about_button), onOpenAbout),
            ),
        )
        add(
            SettingRow(
                id = "settings_reset_title",
                title = stringResource(R.string.settings_reset_title),
                description = stringResource(R.string.settings_reset_desc),
                keywords = "reset wipe clear factory",
                control = SettingControl.Action(stringResource(R.string.settings_reset_button), onFullReset),
            ),
        )

        // ВОСПРОИЗВЕДЕНИЕ, ПОИСК И ЛЕНТЫ, ВИД: настройки подборок и защиты
        add(
            SettingRow(
                id = "rec_end_mode",
                title = stringResource(R.string.rec_end_title),
                description = stringResource(R.string.rec_end_desc),
                keywords = "endless autoplay continue repeat stop radio бесконечно автопродолжение",
                control = SettingControl.Choice(com.texfi.w0y.data.RecEndMode.entries, settings.recEndMode, { stringResource(it.label) }, viewModel::setRecEndMode),
            ),
        )
        add(
            SettingRow(
                id = "rec_instead_of_list",
                title = stringResource(R.string.rec_instead_title),
                description = stringResource(R.string.rec_instead_desc),
                keywords = "radio similar instead playlist",
                control = SettingControl.Toggle(settings.recInsteadOfList, viewModel::setRecInsteadOfList),
            ),
        )
        add(
            SettingRow(
                id = "delete_protection",
                title = stringResource(R.string.delete_protection_title),
                description = stringResource(R.string.delete_protection_desc),
                keywords = "delete undo confirm trash accident",
                control = SettingControl.Toggle(settings.deleteProtection, viewModel::setDeleteProtection),
            ),
        )
        val noveltyOptions = listOf(0, 15, 30, 50, 75, 100)
        add(
            SettingRow(
                id = "rec_novelty",
                title = stringResource(R.string.rec_novelty_title),
                description = stringResource(R.string.rec_novelty_desc),
                keywords = "new familiar discover",
                control =
                    SettingControl.Choice(
                        noveltyOptions,
                        noveltyOptions.minBy { kotlin.math.abs(it - settings.recNovelty) },
                        { "$it%" },
                        viewModel::setRecNovelty,
                    ),
            ),
        )
        add(
            SettingRow(
                id = "taste_learning",
                title = stringResource(R.string.taste_learning_title),
                description = stringResource(R.string.taste_learning_desc),
                keywords = "taste profile learn",
                control = SettingControl.Toggle(settings.tasteLearning, viewModel::setTasteLearning),
            ),
        )
        add(
            SettingRow(
                id = "taste_screen",
                title = stringResource(R.string.taste_row_title),
                description = stringResource(R.string.taste_row_desc),
                keywords = "taste exclude artists reset профиль",
                control = SettingControl.Action(stringResource(R.string.taste_row_button), onOpenTaste),
            ),
        )
        add(
            SettingRow(
                id = "home_rec_blocks",
                title = stringResource(R.string.home_blocks_title),
                description = stringResource(R.string.home_blocks_desc),
                keywords = "home picks discoveries forgotten",
                control = SettingControl.Toggle(settings.homeRecBlocks, viewModel::setHomeRecBlocks),
            ),
        )
        add(
            SettingRow(
                id = "weekly_discovery",
                title = stringResource(R.string.weekly_title),
                description = stringResource(R.string.weekly_desc),
                keywords = "weekly playlist discoveries",
                control = SettingControl.Toggle(settings.weeklyDiscovery, viewModel::setWeeklyDiscovery),
            ),
        )
        add(
            SettingRow(
                id = "player_state_badge",
                title = stringResource(R.string.badge_title),
                description = stringResource(R.string.badge_desc),
                keywords = "badge speed eq",
                control = SettingControl.Toggle(settings.playerStateBadge, viewModel::setPlayerStateBadge),
            ),
        )
        add(
            SettingRow(
                id = "devices",
                title = stringResource(R.string.settings_section_devices),
                description = stringResource(R.string.settings_section_devices_sum),
                keywords = "bluetooth headphones output speaker",
                control = SettingControl.Custom { DevicesBody(viewModel) },
            ),
        )
        add(
            SettingRow(
                id = "diagnostics",
                title = stringResource(R.string.diag_title),
                description = stringResource(R.string.diag_settings_desc),
                keywords = "debug info test connection copy error log",
                control = SettingControl.Custom { DiagnosticsCard() },
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
        id: String,
        title: Int,
        desc: Int,
        options: List<T>,
        selected: T,
        label: @Composable (T) -> String,
        onSelect: (T) -> Unit,
        keywords: String = "",
    ) = add(
        SettingRow(
            id = id,
            title = context.getString(title),
            description = context.getString(desc),
            keywords = keywords,
            control = SettingControl.Choice(options, selected, label, onSelect),
        ),
    )
    choice("hl_set_weight", R.string.hl_set_weight, R.string.hl_set_weight_d, com.texfi.w0y.data.LineWeight.entries, settings.lineWeight, { stringResource(it.label) }, viewModel::setLineWeight, "line thickness hairline")
    choice("hl_set_corners", R.string.hl_set_corners, R.string.hl_set_corners_d, com.texfi.w0y.data.CornerStyle.entries, settings.corners, { stringResource(it.label) }, viewModel::setCorners, "radius round")
    choice("hl_set_cover", R.string.hl_set_cover, R.string.hl_set_cover_d, com.texfi.w0y.data.CoverShape.entries, settings.coverShape, { stringResource(it.label) }, viewModel::setCoverShape, "disc square artwork")
    choice("hl_set_layout", R.string.hl_set_layout, R.string.hl_set_layout_d, com.texfi.w0y.data.PlayerLayout.entries, settings.playerLayout, { stringResource(it.label) }, viewModel::setPlayerLayout, "player ring minimal compact cover full poster big blur постер обложка")
    choice("hl_set_caps", R.string.hl_set_caps, R.string.hl_set_caps_d, com.texfi.w0y.data.CapsMode.entries, settings.caps, { stringResource(it.label) }, viewModel::setCaps, "uppercase capitals")
    choice("hl_set_density", R.string.hl_set_density, R.string.hl_set_density_d, com.texfi.w0y.data.Density.entries, settings.density, { stringResource(it.label) }, viewModel::setDensity, "compact spacing")
    choice("hl_set_motion", R.string.hl_set_motion, R.string.hl_set_motion_d, com.texfi.w0y.data.MotionMode.entries, settings.motion, { stringResource(it.label) }, viewModel::setMotion, "animation reduce")
    choice("hl_set_haptics", R.string.hl_set_haptics, R.string.hl_set_haptics_d, com.texfi.w0y.data.HapticLevel.entries, settings.hapticLevel, { stringResource(it.label) }, viewModel::setHapticLevel, "vibration")
    choice("hl_set_iconstyle", R.string.hl_set_iconstyle, R.string.hl_set_iconstyle_d, com.texfi.w0y.data.IconStyle.entries, settings.iconStyle, { stringResource(it.label) }, viewModel::setIconStyle, "icons line filled dots")
    choice("dock_shape_title", R.string.dock_shape_title, R.string.dock_shape_desc, com.texfi.w0y.data.DockShape.entries, settings.dockShape, { stringResource(if (it == com.texfi.w0y.data.DockShape.ISLAND) R.string.dock_island else R.string.dock_bar) }, viewModel::setDockShape, "panel dock bottom bar floating")
    choice("dock_tint_title", R.string.dock_tint_title, R.string.dock_tint_desc, com.texfi.w0y.data.DockTint.entries, settings.dockTint, { stringResource(when (it) { com.texfi.w0y.data.DockTint.COVER -> R.string.dock_tint_cover; com.texfi.w0y.data.DockTint.ACCENT -> R.string.dock_tint_accent; com.texfi.w0y.data.DockTint.NONE -> R.string.dock_tint_none }) }, viewModel::setDockTint, "panel gradient colour cover")
    choice("dock_labels_title", R.string.dock_labels_title, R.string.dock_labels_desc, com.texfi.w0y.data.DockLabels.entries, settings.dockLabels, { stringResource(when (it) { com.texfi.w0y.data.DockLabels.ALL -> R.string.dock_labels_all; com.texfi.w0y.data.DockLabels.ACTIVE -> R.string.dock_labels_active; com.texfi.w0y.data.DockLabels.NONE -> R.string.dock_labels_none }) }, viewModel::setDockLabels, "panel tabs names")
    add(
        SettingRow(
            id = "dock_glow_title",
            title = context.getString(R.string.dock_glow_title),
            description = context.getString(R.string.dock_glow_desc),
            keywords = "panel glow breathing light pulse",
            control = SettingControl.Toggle(settings.dockGlow, viewModel::setDockGlow),
        ),
    )
    add(
        SettingRow(
            id = "hl_set_swipe",
            title = context.getString(R.string.hl_set_swipe),
            description = context.getString(R.string.hl_set_swipe_d),
            keywords = "mini player dismiss gesture",
            control = SettingControl.Toggle(settings.swipeDismiss, viewModel::setSwipeDismiss),
        ),
    )
    add(
        SettingRow(
            id = "hl_set_appicon",
            title = context.getString(R.string.hl_set_appicon),
            description = context.getString(R.string.hl_set_appicon_d),
            keywords = "launcher icon home screen",
            control = SettingControl.Custom { AppIconPicker(settings.appIcon, viewModel::setAppIcon) },
        ),
    )
    add(
        SettingRow(
            id = "hl_set_reset",
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


/** Последняя открытая вкладка настроек: живёт, пока жив процесс. */
private object SettingsTabMemory {
    @Volatile
    var last: String = SettingsCategory.PLAYBACK.name
}

/**
 * Вкладки настроек: подписи моно, тонкая линия под полосой, под активной — акцентная.
 * Подписи целиком, без усечения: полоса прокручивается, активная сама подъезжает в видимую часть,
 * а у края, где есть продолжение, стоит затемнение.
 */
@Composable
private fun SettingsTabStrip(tabs: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scroll = androidx.compose.foundation.rememberScrollState()
    val spots = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Int, Int>>() }
    val spot = spots[selected]
    val spec = com.texfi.w0y.ui.components.hairSpec<Float>(com.texfi.w0y.ui.theme.HairTokens.MID_MS)
    val x by androidx.compose.animation.core.animateFloatAsState(spot?.first?.toFloat() ?: 0f, spec, label = "tabX")
    val w by androidx.compose.animation.core.animateFloatAsState(spot?.second?.toFloat() ?: 0f, spec, label = "tabW")
    LaunchedEffect(spot, scroll.maxValue) {
        spot ?: return@LaunchedEffect
        val target = (spot.first - 60).coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(target, androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxWidth().testTag("settings-tabs")) {
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Row {
                Spacer(Modifier.width(18.dp))
                tabs.forEach { (key, label) ->
                    val active = key == selected
                    val tone by androidx.compose.animation.animateColorAsState(if (active) colors.text else colors.textMuted, androidx.compose.animation.core.tween(200), label = "tabTone")
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .testTag("tab-$key")
                            .onGloballyPositioned { spots[key] = it.positionInParent().x.toInt() to it.size.width }
                            .semantics { role = Role.Tab; this.selected = active }
                            .clickable { onSelect(key) }
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label.asLabel(), style = com.texfi.w0y.ui.theme.HairLabelStyle, color = tone, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.width(18.dp))
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
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(28.dp)
                    .height(48.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.9f)))),
            )
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(look.line).background(colors.border))
    }
    Spacer(Modifier.height(8.dp))
}
