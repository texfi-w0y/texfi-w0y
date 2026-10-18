package com.texfi.w0y.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.Reverb
import com.texfi.w0y.desktop.i18n.S
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private enum class SSection(val key: String, val glyph: Glyph) {
    SOUND("settings_section_sound", Glyph.Volume),
    TONE("settings_section_tone", Glyph.Wave),
    PLAYER("settings_section_player", Glyph.Play),
    SPEED("settings_section_speed", Glyph.Timer),
    DOWNLOADS("settings_section_downloads", Glyph.Download),
    SEARCH("settings_section_search", Glyph.Search),
    ACCOUNT("settings_section_account", Glyph.Library),
    CLEAN("settings_section_clean", Glyph.Check),
    LOOK("settings_section_look", Glyph.Palette),
    KEYS("d_section_keys", Glyph.Sliders),
    DEVICES("settings_section_devices", Glyph.Headphones),
    DATA("settings_section_data", Glyph.Box),
    ;

    val title: String get() = S(key)
    val summary: String get() = S("${key}_sum")
}

private sealed interface SetControl {
    class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : SetControl

    class Choice(val labels: List<String>, val selected: Int, val onSelect: (Int) -> Unit) : SetControl

    class Act(val button: String, val onClick: () -> Unit) : SetControl

    class Info(val value: String) : SetControl

    class Custom(val content: @Composable () -> Unit) : SetControl
}

private class Setting(val section: SSection, val title: String, val description: String, val control: SetControl, val keywords: String = "")

private fun <T> choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) =
    SetControl.Choice(options.map(label), options.indexOf(selected).coerceAtLeast(0)) { onSelect(options[it]) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: AppState) {
    var section by remember { mutableStateOf<SSection?>(null) }
    var query by remember { mutableStateOf("") }
    var aboutOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    if (aboutOpen) {
        AboutPane(app) { aboutOpen = false }
        return
    }

    val rows = buildRows(app, onAbout = { aboutOpen = true }, onMessage = { message = it }, onReset = { confirmReset = true })
    val q = query.trim().lowercase()
    val shown =
        when {
            q.isNotEmpty() -> rows.filter { "${it.title} ${it.description} ${it.keywords} ${it.section.title}".lowercase().contains(q) }
            section != null -> rows.filter { it.section == section }
            else -> emptyList()
        }

    ScreenFrame(section?.title ?: S("settings_title"), app, canBack = false, actions = {
        if (section != null && q.isEmpty()) IconButton(Glyph.ChevronLeft, onClick = { section = null }, color = C.Muted, size = 20.dp)
    }) {
        Column(Modifier.fillMaxSize()) {
            AppTextField(query, { query = it }, S("settings_search_placeholder"), leading = Glyph.Search, size = 14, onClear = { query = "" })
            message?.let {
                LaunchedEffect(it) { kotlinx.coroutines.delay(3500); message = null }
                Txt(it, color = C.Sand, size = 12, modifier = Modifier.padding(top = 8.dp), maxLines = 2)
            }
            if (confirmReset) {
                Gap(h = 10)
                PixelCard(Modifier.fillMaxWidth()) {
                    Column {
                        Txt(S("settings_reset_confirm_title"), size = 16, weight = FontWeight.SemiBold)
                        Txt(S("settings_reset_confirm_text"), color = C.Muted, size = 13, maxLines = 4, modifier = Modifier.padding(top = 4.dp))
                        Gap(h = 10)
                        RowCenter {
                            PixelButton(S("settings_reset_button"), onClick = { app.eraseEverything() })
                            Gap(w = 10)
                            PixelButton(S("common_cancel"), onClick = { confirmReset = false }, primary = false)
                        }
                    }
                }
            }
            Gap(h = 12)
            val state = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(end = 12.dp),
                    state = state,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = LocalBarsInset.current.bottom),
                ) {
                    if (q.isEmpty() && section == null) {
                        items(SSection.entries) { s -> SectionCard(s) { section = s } }
                    } else if (shown.isEmpty()) {
                        item {
                            Txt(S("settings_search_nothing_title"), pixel = true, size = 12, color = C.Blue, modifier = Modifier.padding(top = 12.dp))
                            Txt(S("settings_search_nothing_text"), color = C.Muted, size = 13, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                        }
                    } else {
                        items(shown) { r -> SettingRowView(r, showSection = q.isNotEmpty()) }
                    }
                    item { Gap(h = 24) }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SectionCard(s: SSection, onClick: () -> Unit) {
    PixelCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(Look.card)).clickable(onClick = onClick)) {
        RowCenter {
            Box(
                Modifier.width(40.dp).height(40.dp).clip(RoundedCornerShape(if (Look.smooth) 14.dp else 4.dp)).background(C.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(s.glyph, C.Blue, 20.dp) }
            Gap(w = 14)
            Column(Modifier.weight(1f)) {
                Txt(s.title, size = 16, weight = FontWeight.Medium)
                Txt(s.summary, color = C.Muted, size = 12)
            }
            Icon(Glyph.ChevronRight, C.Muted, 16.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRowView(r: Setting, showSection: Boolean) {
    PixelCard(Modifier.fillMaxWidth()) {
        Column {
            if (showSection) SectionLabel(r.section.title, Modifier.padding(bottom = 6.dp))
            RowCenter {
                Column(Modifier.weight(1f)) {
                    Txt(r.title, size = 15, weight = FontWeight.Medium, maxLines = 2)
                    Txt(r.description, color = C.Muted, size = 12, maxLines = 8, modifier = Modifier.padding(top = 3.dp))
                }
                when (val c = r.control) {
                    is SetControl.Toggle -> {
                        Gap(w = 14)
                        AppToggle(c.checked, c.onChange)
                    }
                    is SetControl.Act -> {
                        Gap(w = 14)
                        PixelButton(c.button, onClick = c.onClick, primary = false)
                    }
                    is SetControl.Info -> {
                        Gap(w = 14)
                        Txt(c.value, color = C.Blue, size = 14, weight = FontWeight.Medium)
                    }
                    else -> Unit
                }
            }
            when (val c = r.control) {
                is SetControl.Choice -> {
                    Gap(h = 10)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        c.labels.forEachIndexed { i, label -> ChoiceChip(label, i == c.selected) { c.onSelect(i) } }
                    }
                }
                is SetControl.Custom -> {
                    Gap(h = 10)
                    c.content()
                }
                else -> Unit
            }
        }
    }
}

// ---------------------------------------------------------------- пункты

private fun Float.speedLabel() = "${this}×".replace(".0×", "×")

@Composable
private fun buildRows(app: AppState, onAbout: () -> Unit, onMessage: (String) -> Unit, onReset: () -> Unit): List<Setting> {
    val st = app.st
    val set = app::setSt
    val devices by produceState<List<Pair<String, String>>?>(null) { value = withContext(Dispatchers.IO) { MpvDevices.list() } }
    return buildList {
        // ---- Звук
        add(Setting(SSection.SOUND, S("d_set_quality_title"), S("d_set_quality_desc"),
            choice(Quality.entries, st.quality, { it.label }) { v -> set { it.copy(quality = v) } }))
        add(Setting(SSection.SOUND, S("settings_normalize_title"), S("settings_normalize_desc"),
            SetControl.Toggle(st.normalizeVolume) { v -> set { it.copy(normalizeVolume = v) } }))
        add(Setting(SSection.SOUND, S("settings_skip_silence_title"), S("settings_skip_silence_desc"),
            SetControl.Toggle(st.skipSilence) { v -> { set { it.copy(skipSilence = v) }; app.player.reapplyAudio() } }))
        add(Setting(SSection.SOUND, S("settings_equalizer_title"), S("d_set_eq_desc"), SetControl.Custom { EqPanel(app) }, "equalizer eq эквалайзер"))
        // ---- Звучание
        add(Setting(SSection.TONE, S("settings_speed_title"), S("settings_speed_desc"),
            choice(listOf(0.5f, 0.75f, 0.85f, 1f, 1.1f, 1.25f, 1.5f, 2f), st.speed, { it.speedLabel() }) { v -> set { it.copy(speed = v) } }))
        add(Setting(SSection.TONE, S("settings_pitch_title"), S("settings_pitch_desc"),
            choice(listOf(0.8f, 0.92f, 1f, 1.06f, 1.2f), st.pitch, { if (it == 1f) S("settings_pitch_normal") else "%.2f".format(it) }) { v -> set { it.copy(pitch = v) } }))
        add(Setting(SSection.TONE, S("settings_reverb_title"), S("settings_reverb_desc"),
            choice(Reverb.entries, st.reverb, { it.label }) { v -> set { it.copy(reverb = v) } }))
        // ---- Плеер
        add(Setting(SSection.PLAYER, S("settings_queue_title"), st.queueMode.hint,
            choice(QueueMode.entries, st.queueMode, { it.label }) { v -> set { it.copy(queueMode = v) } }, "queue radio shuffle очередь"))
        add(Setting(SSection.PLAYER, S("settings_seek_step_title"), S("settings_seek_step_desc"),
            choice(listOf(5, 10, 15, 30), st.seekStepSec, { S("settings_seek_step_value", it) }) { v -> set { it.copy(seekStepSec = v) } }))
        add(Setting(SSection.PLAYER, S("settings_cover_glow_title"), S("settings_cover_glow_desc"),
            SetControl.Toggle(st.playerCoverGlow) { v -> set { it.copy(playerCoverGlow = v) } }))
        add(Setting(SSection.PLAYER, S("settings_lyrics_title"), S("settings_lyrics_desc"),
            SetControl.Toggle(st.showLyrics) { v -> set { it.copy(showLyrics = v) } }))
        add(Setting(SSection.PLAYER, S("lyrics_size_title"), S("lyrics_size_desc"),
            choice(LyricsSize.entries, st.lyricsSize, { it.label }) { v -> set { it.copy(lyricsSize = v) } }))
        add(Setting(SSection.PLAYER, S("settings_sleep_title"), S("settings_sleep_desc"),
            choice(listOf(10, 15, 30, 45, 60), st.sleepTimerDefaultMin, { S("settings_sleep_minutes", it) }) { v -> set { it.copy(sleepTimerDefaultMin = v) } }))
        add(Setting(SSection.PLAYER, S("settings_playlist_recs"), S("settings_playlist_recs_hint"),
            SetControl.Toggle(st.showPlaylistRecommendations) { v -> set { it.copy(showPlaylistRecommendations = v) } }))
        // визуализатор
        val vizOk = SpectrumCapture.available
        add(Setting(SSection.PLAYER, S("viz_style_title"), if (vizOk) S("d_set_viz_desc") else S("d_set_viz_missing"),
            if (vizOk) {
                val styles = VisualizerStyle.entries.filter { it.smooth == Look.smooth }
                choice(styles, st.visualizerStyle.forUi(st.uiStyle), { it.label }) { v -> set { it.copy(visualizerStyle = v) } }
            } else {
                SetControl.Info("—")
            }, "visualizer визуализатор"))
        if (vizOk) {
            add(Setting(SSection.PLAYER, S("d_set_art_title"), S("d_set_art_desc"),
                SetControl.Toggle(st.playerArt == PlayerArt.VISUALIZER) { v -> set { it.copy(playerArt = if (v) PlayerArt.VISUALIZER else PlayerArt.COVER) } }))
            add(Setting(SSection.PLAYER, S("viz_sensitivity_title"), S("viz_sensitivity_desc"),
                SetControl.Custom { AppSlider(st.visualizerSensitivity, 0.5f..2f, { v -> set { it.copy(visualizerSensitivity = v) } }, Modifier.width(260.dp), step = 0.05f) }))
            add(Setting(SSection.PLAYER, S("viz_fps_title"), S("d_set_viz_fps_desc"),
                choice(listOf(30, 60), st.visualizerFps, { "$it" }) { v -> set { it.copy(visualizerFps = v) } }))
            add(Setting(SSection.PLAYER, S("viz_backdrop_title"), S("d_set_viz_backdrop_desc"),
                SetControl.Toggle(st.visualizerCoverBackdrop) { v -> set { it.copy(visualizerCoverBackdrop = v) } }))
        }
        // подставка
        add(Setting(SSection.PLAYER, S("stand_lyrics_title"), S("stand_lyrics_desc"), SetControl.Toggle(st.standLyrics) { v -> set { it.copy(standLyrics = v) } }, "stand подставка"))
        if (vizOk) add(Setting(SSection.PLAYER, S("stand_viz_title"), S("stand_viz_desc"), SetControl.Toggle(st.standVisualizer) { v -> set { it.copy(standVisualizer = v) } }, "stand подставка"))
        add(Setting(SSection.PLAYER, S("stand_view_title"), S("stand_view_desc"), choice(StandView.entries, st.standView, { it.label }) { v -> set { it.copy(standView = v) } }, "stand подставка"))
        add(Setting(SSection.PLAYER, S("stand_marquee_title"), S("stand_marquee_desc"), SetControl.Toggle(st.standMarquee) { v -> set { it.copy(standMarquee = v) } }, "stand подставка"))
        add(Setting(SSection.PLAYER, S("stand_flip_title"), S("stand_flip_desc"), SetControl.Toggle(st.standFlipped) { v -> set { it.copy(standFlipped = v) } }, "stand подставка"))
        add(Setting(SSection.PLAYER, S("stand_max_title"), S("stand_max_desc"),
            choice(listOf(0, 30, 60, 120, 240), st.standMaxMinutes, { if (it == 0) S("dl_no_limit") else S("settings_sleep_minutes", it) }) { v -> set { it.copy(standMaxMinutes = v) } }, "stand подставка"))
        add(Setting(SSection.PLAYER, S("d_set_stand_awake_title"), S("d_set_stand_awake_desc"),
            SetControl.Toggle(st.standKeepAwake) { v -> set { it.copy(standKeepAwake = v) } }, "stand подставка"))
        add(Setting(SSection.PLAYER, S("d_act_stand"), S("d_set_stand_open_desc"), SetControl.Act(S("download_open")) { app.stand.enter() }, "stand подставка"))
        // ---- Скорость
        add(Setting(SSection.SPEED, S("settings_startup_title"), S("settings_startup_desc", app.player.startupSummary()),
            SetControl.Info(app.player.lastStartupMs?.let { S("settings_startup_value_ms", it) } ?: "—")))
        add(Setting(SSection.SPEED, S("settings_preload_title"), S("settings_preload_desc"),
            SetControl.Toggle(st.preloadNext) { v -> set { it.copy(preloadNext = v) } }))
        add(Setting(SSection.SPEED, S("settings_dial_min_title"), S("settings_dial_min_desc"),
            choice(listOf(1, 2, 3, 5), st.dialMinPlays, { S("settings_dial_min_value", it) }) { v -> set { it.copy(dialMinPlays = v) } }))
        // ---- Загрузки
        add(Setting(SSection.DOWNLOADS, S("dl_folder_current", app.downloads.folder.absolutePath), S("d_set_dl_folder_desc", app.downloads.usedMb()),
            SetControl.Custom {
                RowCenter {
                    PixelButton(S("download_open"), onClick = { app.downloads.openFolder() }, primary = false)
                    Gap(w = 8)
                    PixelButton(S("dl_folder_change"), onClick = {
                        val picked = pickFolder(S("dl_folder_ask_title"))
                        if (picked != null) set { it.copy(downloadFolder = picked) } else onMessage(S("d_set_no_picker"))
                    }, primary = false)
                    if (st.downloadFolder.isNotBlank()) {
                        Gap(w = 8)
                        PixelButton(S("dl_folder_default"), onClick = { set { it.copy(downloadFolder = "") } }, primary = false)
                    }
                }
            }, "folder папка"))
        add(Setting(SSection.DOWNLOADS, S("settings_autodownload_title"), S("settings_autodownload_desc"),
            SetControl.Toggle(st.autoDownloadLiked) { v -> set { it.copy(autoDownloadLiked = v) } }))
        add(Setting(SSection.DOWNLOADS, S("dl_auto_playlists_title"), S("dl_auto_playlists_desc"),
            SetControl.Custom {
                if (app.lib.playlists.isEmpty()) {
                    Txt(S("dl_auto_playlists_none"), color = C.Muted, size = 13)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        app.lib.playlists.forEach { pl ->
                            val on = pl.id in st.autoDownloadPlaylists
                            ChoiceChip(pl.name, on) {
                                set { it.copy(autoDownloadPlaylists = if (on) it.autoDownloadPlaylists - pl.id else it.autoDownloadPlaylists + pl.id) }
                                app.downloads.syncAutoPlaylists()
                            }
                        }
                    }
                }
            }))
        add(Setting(SSection.DOWNLOADS, S("d_set_dl_quality_title"), S("dl_quality_desc"),
            choice(Quality.entries, st.downloadQuality, { it.label }) { v -> set { it.copy(downloadQuality = v) } }))
        if (app.downloads.canMp3) {
            add(Setting(SSection.DOWNLOADS, S("dl_format_title"), S("d_set_mp3_desc"),
                choice(listOf(false, true), st.downloadAsMp3, { if (it) "MP3" else S("dl_format_value") }) { v -> set { it.copy(downloadAsMp3 = v) } }, "mp3 ffmpeg"))
        } else {
            add(Setting(SSection.DOWNLOADS, S("dl_format_title"), S("d_set_mp3_missing"), SetControl.Info(S("dl_format_value")), "mp3 ffmpeg"))
        }
        add(Setting(SSection.DOWNLOADS, S("dl_parallel_title"), S("dl_parallel_desc"),
            choice(listOf(1, 2, 3, 4), st.downloadParallel, { "$it" }) { v -> set { it.copy(downloadParallel = v) } }))
        add(Setting(SSection.DOWNLOADS, S("dl_speed_limit_title"), S("dl_speed_limit_desc"),
            choice(listOf(0, 256, 512, 1024, 4096), st.downloadSpeedLimitKb, { if (it == 0) S("dl_no_limit") else S("dl_kbps", it) }) { v -> set { it.copy(downloadSpeedLimitKb = v) } }))
        add(Setting(SSection.DOWNLOADS, S("dl_retries_title"), S("dl_retries_desc"),
            choice(listOf(0, 1, 3, 5), st.downloadRetries, { "$it" }) { v -> set { it.copy(downloadRetries = v) } }))
        add(Setting(SSection.DOWNLOADS, S("dl_storage_limit_title"), S("dl_storage_limit_desc", app.downloads.usedMb()),
            choice(listOf(0, 1024, 5120, 10240, 51200), st.downloadStorageLimitMb, { if (it == 0) S("dl_no_limit") else if (it >= 1024) S("settings_cache_gb", it / 1024) else S("settings_cache_mb", it) }) { v -> set { it.copy(downloadStorageLimitMb = v) } }))
        add(Setting(SSection.DOWNLOADS, S("dl_notifications_title"), S("dl_notifications_desc"),
            SetControl.Toggle(st.downloadNotifications) { v -> set { it.copy(downloadNotifications = v) } }))
        add(Setting(SSection.DOWNLOADS, S("dl_verify_title"), S("dl_verify_desc"), SetControl.Act(S("dl_verify_button")) { app.downloads.verify(); onMessage(app.downloads.notice.orEmpty()) }))
        add(Setting(SSection.DOWNLOADS, S("dl_clear_title"), S("dl_clear_desc"), SetControl.Act(S("dl_clear_button")) { app.downloads.clearAll(); onMessage(S("dl_cleared")) }))
        // ---- Поиск
        add(Setting(SSection.SEARCH, S("settings_suggestions_title"), S("settings_suggestions_desc"),
            SetControl.Toggle(st.searchSuggestions) { v -> set { it.copy(searchSuggestions = v) } }))
        add(Setting(SSection.SEARCH, S("settings_search_history_title"), S("settings_search_history_desc"),
            SetControl.Toggle(st.saveSearchHistory) { v -> set { it.copy(saveSearchHistory = v) } }))
        // ---- Аккаунт
        add(Setting(SSection.ACCOUNT, S("settings_account_title"),
            if (app.signedIn) S("settings_account_in", app.lib.accountName ?: S("settings_account_unnamed")) else S("settings_account_out"),
            SetControl.Custom {
                RowCenter {
                    if (app.signedIn) {
                        PixelButton(S("settings_account_sign_out"), onClick = app::signOut, primary = false)
                    } else {
                        PixelButton(if (app.loggingIn) S("d_waiting_login") else S("d_sign_in_google"), onClick = app::signInWithGoogle, enabled = !app.loggingIn)
                    }
                    app.accountStatus?.let { Txt("  $it", color = C.Sand, size = 12, maxLines = 2) }
                }
            }, "войти выйти google cookie login"))
        add(Setting(SSection.ACCOUNT, S("settings_sync_playlists_title"), S("settings_sync_playlists_desc"),
            SetControl.Toggle(st.syncPlaylists) { v -> set { it.copy(syncPlaylists = v) } }, "sync синхронизация"))
        add(Setting(SSection.ACCOUNT, S("settings_history_title"), S("settings_history_desc"),
            SetControl.Toggle(st.keepHistory) { v -> set { it.copy(keepHistory = v) } }))
        // ---- Без мата
        add(Setting(SSection.CLEAN, S("settings_clean_title"), S("settings_clean_desc"),
            SetControl.Toggle(st.cleanMode) { v -> set { it.copy(cleanMode = v) } }, "мат explicit"))
        add(Setting(SSection.CLEAN, S("settings_clean_fallback_title"), S("settings_clean_fallback_desc"),
            choice(ExplicitFallback.entries, st.explicitFallback, { it.label }) { v -> set { it.copy(explicitFallback = v) } }))
        add(Setting(SSection.CLEAN, S("settings_hide_explicit_title"), S("settings_hide_explicit_desc"),
            SetControl.Toggle(st.hideExplicit) { v -> set { it.copy(hideExplicit = v) } }))
        add(Setting(SSection.CLEAN, S("settings_mute_swear_title"), S("settings_mute_swear_desc"),
            SetControl.Toggle(st.muteSwearLines) { v -> set { it.copy(muteSwearLines = v) } }))
        // ---- Вид
        add(Setting(SSection.LOOK, S("settings_style_title"), S("settings_style_desc"),
            choice(UiStyle.entries, st.uiStyle, { it.label }) { v -> set { it.copy(uiStyle = v, stylePicked = true) } }, "style pixel smooth glass стиль"))
        if (st.uiStyle == UiStyle.SMOOTH) {
            add(Setting(SSection.LOOK, S("theme_glass"), S("d_set_glass_desc"),
                choice(listOf(true, false), st.smoothGlass, { if (it) S("style_glass") else S("style_matte") }) { v -> set { it.copy(smoothGlass = v) } }, "glass liquid стекло"))
        }
        add(Setting(SSection.LOOK, S("settings_theme_title"), S("settings_theme_desc"),
            choice(ThemeMode.entries, st.theme, { it.label }) { v -> set { it.copy(theme = v) } }, "оформление тема"))
        add(Setting(SSection.LOOK, S("settings_accent_title"), S("settings_accent_desc"), SetControl.Custom { AccentPicker(app) }, "цвет акцент"))
        add(Setting(SSection.LOOK, S("settings_language_title"), S("settings_language_desc"),
            choice(Language.entries, st.language, { it.label }) { v -> set { it.copy(language = v) } }))
        add(Setting(SSection.LOOK, S("settings_start_tab_title"), S("settings_start_tab_desc"),
            choice(StartTab.entries, st.startTab, { it.label }) { v -> set { it.copy(startTab = v) } }))
        add(Setting(SSection.LOOK, S("settings_shelves_title"), S("settings_shelves_desc"),
            SetControl.Toggle(st.showRecommendations) { v -> set { it.copy(showRecommendations = v) } }))
        add(Setting(SSection.LOOK, S("settings_live_background_title"), S("settings_live_background_desc"),
            SetControl.Toggle(st.animatedBackground) { v -> set { it.copy(animatedBackground = v) } }))
        add(Setting(SSection.LOOK, S("settings_compact_title"), S("settings_compact_desc"),
            SetControl.Toggle(st.compactRows) { v -> set { it.copy(compactRows = v) } }))
        // ---- Клавиши
        add(Setting(SSection.KEYS, S("d_keys_title").replaceFirstChar(Char::uppercase), S("d_set_keys_desc"), SetControl.Custom { KeysEditor(app) }, "keys hotkeys binds клавиши бинды"))
        add(Setting(SSection.KEYS, S("d_set_cli_title"), S("d_set_cli_desc"), SetControl.Info("w0y --do …"), "cli command line socket терминал"))
        add(Setting(SSection.KEYS, S("d_set_keys_reset_title"), S("d_set_keys_reset_desc"), SetControl.Act(S("settings_reset_button")) { set { it.copy(keymap = emptyMap()) } }))
        add(Setting(SSection.KEYS, S("d_act_help"), S("d_set_keys_help_desc"), SetControl.Act(S("download_open")) { Actions.helpOpen = true }))
        // ---- Устройства
        val list = devices
        add(Setting(SSection.DEVICES, S("player_output"), when {
            list == null -> S("d_set_asking")
            list.size <= 1 -> S("settings_devices_empty")
            else -> S("d_set_device_desc")
        }, if (list == null || list.size <= 1) SetControl.Info("auto") else
            SetControl.Choice(list.map { it.second.take(48) }, list.indexOfFirst { it.first == st.audioDevice }.coerceAtLeast(0)) { i ->
                val id = list[i].first
                set { it.copy(audioDevice = id) }
                app.player.setAudioDevice(id)
            }, "наушники колонки bluetooth"))
        add(Setting(SSection.DEVICES, S("d_set_notify_title"), S("d_set_notify_desc"),
            SetControl.Toggle(st.notifyTrackChange) { v -> set { it.copy(notifyTrackChange = v) } }, "notification уведомление"))
        // ---- Данные
        add(Setting(SSection.DATA, S("settings_export_title"), S("d_set_export_desc"),
            SetControl.Act(S("settings_export_button")) {
                val dialog = FileDialog(null as Frame?, S("settings_export_title"), FileDialog.SAVE).apply { file = "w0y-settings.json"; isVisible = true }
                val f = dialog.file?.let { File(dialog.directory, it) }
                if (f != null) {
                    f.writeText(Json { prettyPrint = true }.encodeToString(DSettings.serializer(), st))
                    onMessage(f.name)
                }
            }))
        add(Setting(SSection.DATA, S("settings_import_title"), S("settings_import_desc"),
            SetControl.Act(S("settings_import_button")) {
                val dialog = FileDialog(null as Frame?, S("settings_import_title"), FileDialog.LOAD).apply { isVisible = true }
                val f = dialog.file?.let { File(dialog.directory, it) }
                if (f != null) {
                    runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(DSettings.serializer(), f.readText()) }
                        .onSuccess { v -> set { v }; onMessage(S("settings_imported")) }
                        .onFailure { onMessage(S("settings_import_failed", it.message ?: "")) }
                }
            }))
        add(Setting(SSection.DATA, S("d_set_data_title"), S("d_set_data_desc", Store.dirPath()), SetControl.Act(S("download_open")) { Store.openDir() }))
        add(Setting(SSection.DATA, S("settings_reset_title"), S("settings_reset_desc"), SetControl.Act(S("settings_reset_button"), onReset)))
        add(Setting(SSection.DATA, S("settings_about_title"), S("settings_about_desc", APP_VERSION), SetControl.Act(S("settings_about_button"), onAbout)))
    }
}

val APP_VERSION: String = Platform::class.java.getResourceAsStream("/version.txt")?.use { it.readBytes().toString(Charsets.UTF_8).trim() } ?: "dev"

// ---------------------------------------------------------------- эквалайзер

private val EqFreqs = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

@Composable
private fun EqPanel(app: AppState) {
    val st = app.st
    Column {
        RowCenter {
            AppToggle(st.eqEnabled) { v -> app.setSt { it.copy(eqEnabled = v) }; app.player.reapplyAudio() }
            Gap(w = 12)
            Txt(S("d_eq_on"), size = 13)
            Box(Modifier.weight(1f))
            PixelButton(S("d_eq_flat"), onClick = { app.setSt { it.copy(eqBands = List(10) { 0 }) }; app.player.reapplyAudio() }, primary = false)
        }
        Gap(h = 12)
        RowCenter(Modifier.fillMaxWidth(), ) {
            st.eqBands.forEachIndexed { i, gain ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(if (gain > 0) "+$gain" else "$gain", size = 10, color = if (gain == 0) C.Muted else C.Blue)
                    VerticalGain(gain, enabled = st.eqEnabled) { v ->
                        app.setSt { s -> s.copy(eqBands = s.eqBands.toMutableList().also { it[i] = v }) }
                        app.player.reapplyAudio()
                    }
                    Txt(EqFreqs[i], size = 10, color = C.Muted)
                }
            }
        }
    }
}

/** Вертикальный бегунок усиления −12…+12 дБ. */
@Composable
private fun VerticalGain(gain: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    fun at(y: Float, h: Int): Int = (12 - (y / h).coerceIn(0f, 1f) * 24).toInt().coerceIn(-12, 12)
    Box(
        Modifier.width(34.dp).height(130.dp)
            .pointerInput(Unit) { detectTapGestures { onChange(at(it.y, size.height)) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> onChange(at(c.position.y, size.height)) } }
            .drawBehind {
                val trackW = if (Look.smooth) 5.dp.toPx() else 8.dp.toPx()
                val x = (size.width - trackW) / 2
                val color = if (enabled) C.Blue else C.Muted
                drawRoundRect(C.SurfaceHigh, Offset(x, 0f), Size(trackW, size.height), CornerRadius(if (Look.smooth) trackW / 2 else 0f))
                val mid = size.height / 2
                val y = (12 - gain) / 24f * size.height
                drawRect(color, Offset(x, minOf(y, mid)), Size(trackW, kotlin.math.abs(y - mid)))
                drawRect(C.Border, Offset(0f, mid - 0.5.dp.toPx()), Size(size.width, 1.dp.toPx()))
                if (Look.smooth) drawCircle(color, 7.dp.toPx(), Offset(size.width / 2, y)) else drawRect(color, Offset(x - 4.dp.toPx(), y - 3.dp.toPx()), Size(trackW + 8.dp.toPx(), 6.dp.toPx()))
            },
    )
}

// ---------------------------------------------------------------- клавиши

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeysEditor(app: AppState) {
    var capturing by remember { mutableStateOf<Action?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    Column {
        Action.groupKeys.forEachIndexed { g, key ->
            SectionLabel(S(key), Modifier.padding(top = if (g == 0) 0.dp else 14.dp, bottom = 4.dp))
            Action.entries.filter { it.group == g }.forEach { a ->
                val bound = Keymap.bindings(a, app.st.keymap)
                RowCenter(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Txt(a.label, modifier = Modifier.weight(1f), size = 14)
                    bound.forEach { combo ->
                        Box(Modifier.clickable { app.setSt { it.copy(keymap = it.keymap + (a.id to bound.filterNot { b -> b == combo })) } }) { KeyCap(combo) }
                        Gap(w = 6)
                    }
                    if (capturing == a) {
                        CaptureBox(onKey = { combo ->
                            val owner = Keymap.owner(combo, app.st.keymap, except = a)
                            app.setSt { s ->
                                var map = s.keymap
                                if (owner != null) map = map + (owner.id to Keymap.bindings(owner, map).filterNot { it == combo })
                                s.copy(keymap = map + (a.id to (Keymap.bindings(a, map) + combo).distinct()))
                            }
                            note = if (owner != null) S("d_keys_taken", Keymap.pretty(combo), owner.label) else null
                            capturing = null
                        }, onCancel = { capturing = null })
                    } else {
                        IconButton(Glyph.Plus, onClick = { capturing = a }, color = C.Muted, size = 14.dp, box = 28.dp)
                    }
                }
            }
        }
        note?.let { Txt(it, color = C.Sand, size = 12, modifier = Modifier.padding(top = 8.dp), maxLines = 2) }
        Txt(S("d_keys_hint"), color = C.Muted, size = 12, modifier = Modifier.padding(top = 10.dp), maxLines = 3)
    }
}

/** Ждёт нажатия сочетания: Esc отменяет, любое другое записывается. */
@Composable
private fun CaptureBox(onKey: (String) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) {
        KeyContext.capturing = true
        onDispose { KeyContext.capturing = false }
    }
    val shape = RoundedCornerShape(if (Look.smooth) 8.dp else 3.dp)
    Box(
        Modifier.clip(shape).border(2.dp, C.Blue, shape).padding(horizontal = 10.dp, vertical = 4.dp)
            .focusRequester(focus)
            .onFocusChanged { if (!it.isFocused) onCancel() }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                val combo = Keymap.combo(e) ?: return@onPreviewKeyEvent true
                if (combo == "escape") onCancel() else onKey(combo)
                true
            }.focusable(),
    ) { Txt(S("d_keys_press"), size = 12, color = C.Blue) }
}

// ---------------------------------------------------------------- схема цвета

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(app: AppState) {
    val st = app.st
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Accent.entries.forEach { a ->
                val color = if (a == Accent.CUSTOM) Color(st.customAccent) else Color(a.accent)
                val on = a == st.accent
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Swatch(color, on) { app.setSt { it.copy(accent = a) } }
                    Txt(a.label, size = if (Look.smooth) 11 else 8, pixel = true, color = if (on) C.Text else C.Muted, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        if (st.accent == Accent.CUSTOM) {
            Gap(h = 14)
            Txt(S("accent_custom_hue"), color = C.Muted, size = 12)
            Gap(h = 6)
            AppSlider(hueOf(st.customAccent), 0f..360f, { app.setSt { s -> s.copy(customAccent = fromHue(it)) } }, Modifier.fillMaxWidth(), step = 5f, segments = 36)
            Gap(h = 8)
            var hex by remember(st.customAccent) { mutableStateOf("%06X".format(st.customAccent and 0xFFFFFF)) }
            RowCenter {
                Txt("#", color = C.Muted, size = 14)
                Gap(w = 6)
                AppTextField(hex, {
                    hex = it.filter { c -> c.isLetterOrDigit() }.take(6).uppercase()
                    if (hex.length == 6) hex.toIntOrNull(16)?.let { v -> app.setSt { s -> s.copy(customAccent = 0xFF000000.toInt() or v) } }
                }, "A06CFF", Modifier.width(150.dp), size = 14)
            }
        }
        Gap(h = 14)
        RowCenter {
            PixelButton(S("accent_preview"), onClick = {})
            Gap(w = 10)
            PixelButton(S("accent_preview"), onClick = {}, primary = false)
        }
    }
}

private fun hueOf(argb: Int): Float {
    val hsv = FloatArray(3)
    java.awt.Color.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, hsv)
    return hsv[0] * 360f
}

private fun fromHue(hue: Float): Int = java.awt.Color.HSBtoRGB(hue / 360f, 0.62f, 1f)

// ---------------------------------------------------------------- о приложении

@Composable
private fun AboutPane(app: AppState, onBack: () -> Unit) {
    ScreenFrame(S("about_title"), app, canBack = false, actions = { IconButton(Glyph.ChevronLeft, onClick = onBack, color = C.Muted, size = 20.dp) }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PixelCard(Modifier.fillMaxWidth()) {
                Column {
                    Txt("w0y", pixel = true, size = 26, color = C.Blue)
                    Txt(S("about_tagline"), size = 16, modifier = Modifier.padding(top = 8.dp), maxLines = 2)
                    Txt(S("about_blurb"), color = C.Muted, size = 13, maxLines = 6, modifier = Modifier.padding(top = 8.dp))
                    Txt(S("settings_version", APP_VERSION) + " · Linux", color = C.Muted, size = 12, modifier = Modifier.padding(top = 10.dp))
                }
            }
            PixelCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel(S("about_section_open"))
                    Gap(h = 10)
                    Txt(S("about_license_title"), size = 15, weight = FontWeight.Medium)
                    Txt(S("about_license_text"), color = C.Muted, size = 12, maxLines = 2)
                    Gap(h = 10)
                    PixelButton(S("about_source_title"), onClick = { openUrl("https://github.com/texfi-w0y/texfi-w0y") }, primary = false)
                }
            }
            PixelCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel(S("about_section_family"))
                    Gap(h = 10)
                    Txt(S("about_ecosystem_title"), size = 15, weight = FontWeight.Medium)
                    Txt("f0kus — " + S("about_fokus_text"), color = C.Muted, size = 13, maxLines = 2)
                    Txt("m0ney — " + S("about_money_text"), color = C.Muted, size = 13, maxLines = 2)
                    Txt("files — " + S("about_files_text"), color = C.Muted, size = 13, maxLines = 2)
                }
            }
        }
    }
}

fun openUrl(url: String) = Platform.openUrl(url)

/** Выходы звука из самого mpv: `mpv --audio-device=help` печатает их список. */
object MpvDevices {
    fun list(): List<Pair<String, String>> =
        runCatching {
            // Список выходов даёт сам движок звука; на Windows mpv.exe рядом с приложением, на Linux — системный.
            val exe = Platform.which("mpv") ?: return@runCatching emptyList()
            val p = ProcessBuilder(exe.path, "--audio-device=help").redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor()
            Regex("'([^']+)' \\((.*)\\)").findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }.getOrDefault(emptyList())
}
