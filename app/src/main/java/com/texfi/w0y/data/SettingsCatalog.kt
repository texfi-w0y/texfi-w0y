package com.texfi.w0y.data

/**
 * Десять категорий настроек — порядок и номера те, что на главной странице.
 * Каждая настройка живёт ровно в одной из них; в «Избранном» и поиске это
 * ссылки на ту же строку, а не копии.
 */
enum class SettingsCategory(val number: Int, val docTitle: String) {
    PLAYBACK(1, "ВОСПРОИЗВЕДЕНИЕ"),
    SOUND(2, "ЗВУК"),
    TONE(3, "ТОН И СКОРОСТЬ"),
    TEXT_VIZ(4, "ТЕКСТ И ВИЗУАЛИЗАТОР"),
    STAND(5, "РЕЖИМ ПОДСТАВКИ"),
    STORAGE(6, "ЗАГРУЗКИ И ПАМЯТЬ"),
    SEARCH_FEEDS(7, "ПОИСК И ЛЕНТЫ"),
    LOOK(8, "ВИД"),
    ACCOUNT(9, "АККАУНТ"),
    DATA(10, "ДАННЫЕ И ПРИЛОЖЕНИЕ"),
}

/** Подзаголовки внутри категории: моно-заголовок над группой строк. */
enum class SettingsGroup(@androidx.annotation.StringRes val title: Int) {
    QUEUE(com.texfi.w0y.R.string.grp_queue),
    CONTROL(com.texfi.w0y.R.string.grp_control),
    QUALITY(com.texfi.w0y.R.string.grp_quality),
    OUTPUT(com.texfi.w0y.R.string.grp_output),
    LYRICS(com.texfi.w0y.R.string.grp_lyrics),
    VIZ(com.texfi.w0y.R.string.grp_viz),
    STAND_LOOK(com.texfi.w0y.R.string.grp_stand_look),
    STAND_RULES(com.texfi.w0y.R.string.grp_stand_rules),
    DOWNLOADS(com.texfi.w0y.R.string.grp_downloads),
    MEMORY(com.texfi.w0y.R.string.grp_memory),
    RECS(com.texfi.w0y.R.string.grp_recs),
    SEARCH(com.texfi.w0y.R.string.grp_search),
    CLEAN(com.texfi.w0y.R.string.grp_clean),
    STYLE(com.texfi.w0y.R.string.grp_style),
    PANEL(com.texfi.w0y.R.string.grp_panel),
    PLAYER_LOOK(com.texfi.w0y.R.string.grp_player_look),
    FEEL(com.texfi.w0y.R.string.grp_feel),
    GENERAL(com.texfi.w0y.R.string.grp_general),
    BACKUP(com.texfi.w0y.R.string.grp_backup),
    DIAG(com.texfi.w0y.R.string.grp_diag),
    ABOUT(com.texfi.w0y.R.string.grp_about),
}

/**
 * Одна запись каталога: идентификатор (имя строки с названием), категория,
 * ключ в DataStore (у действий и справочных строк его нет) и умолчание.
 */
class CatalogEntry(
    val id: String,
    val category: SettingsCategory,
    val key: String?,
    val name: String,
    val default: (W0ySettings) -> Any? = { null },
    /** На Linux не показывается: иконка приложения, виджет и прочее, чего на десктопе нет. */
    val androidOnly: Boolean = false,
    /** Подзаголовок внутри категории; у категорий в одну группу его нет. */
    val group: SettingsGroup? = null,
)

/** Единая таблица «настройка → категория → ключ → умолчание». По ней строятся экран и `docs/SETTINGS_MAP.md`. */
object SettingsCatalog {
    private fun e(
        id: String,
        c: SettingsCategory,
        key: String?,
        name: String,
        group: SettingsGroup? = null,
        androidOnly: Boolean = false,
        d: (W0ySettings) -> Any? = { null },
    ) = CatalogEntry(id, c, key, name, d, androidOnly, group)

    private val P = SettingsCategory.PLAYBACK
    private val S = SettingsCategory.SOUND
    private val T = SettingsCategory.TONE
    private val V = SettingsCategory.TEXT_VIZ
    private val D = SettingsCategory.STAND
    private val M = SettingsCategory.STORAGE
    private val F = SettingsCategory.SEARCH_FEEDS
    private val L = SettingsCategory.LOOK
    private val A = SettingsCategory.ACCOUNT
    private val X = SettingsCategory.DATA

    val entries: List<CatalogEntry> =
        listOf(
            // 01 ВОСПРОИЗВЕДЕНИЕ
            e("settings_queue_title", P, "queue_mode", "Queue mode", group = SettingsGroup.QUEUE) { it.queueMode },
            e("rec_end_mode", P, "rec_end_mode", "When the queue ends", group = SettingsGroup.QUEUE) { it.recEndMode },
            e("rec_instead_of_list", P, "rec_instead_of_list", "Similar instead of the list", group = SettingsGroup.QUEUE) { it.recInsteadOfList },
            e("settings_preload_title", P, "preload_next", "Preload next track", group = SettingsGroup.QUEUE) { it.preloadNext },
            e("settings_seek_step_title", P, "seek_step_sec", "Seek step, s", group = SettingsGroup.QUEUE) { it.seekStepSec },
            e("hl_set_swipe", P, "hl_swipe_dismiss", "Swipe mini-player down to stop", group = SettingsGroup.CONTROL, androidOnly = true) { it.swipeDismiss },
            e("delete_protection", P, "delete_protection", "Accidental delete protection", group = SettingsGroup.CONTROL) { it.deleteProtection },
            e("settings_sleep_title", P, "sleep_timer_default", "Sleep timer default, min", group = SettingsGroup.CONTROL) { it.sleepTimerDefaultMin },
            // 02 ЗВУК
            e("settings_quality_wifi_title", S, "quality_wifi", "Stream quality · Wi-Fi", group = SettingsGroup.QUALITY) { it.qualityWifi },
            e("settings_quality_mobile_title", S, "quality_mobile", "Stream quality · mobile data", group = SettingsGroup.QUALITY) { it.qualityMobile },
            e("settings_normalize_title", S, "normalize_volume", "Volume normalization", group = SettingsGroup.QUALITY) { it.normalizeVolume },
            e("settings_skip_silence_title", S, "skip_silence", "Skip silence", group = SettingsGroup.QUALITY) { it.skipSilence },
            e("settings_equalizer_title", S, null, "Equalizer", group = SettingsGroup.OUTPUT, androidOnly = true),
            e("settings_fallback_title", S, "fallback_audio", "Alternative audio source", group = SettingsGroup.QUALITY) { it.fallbackAudio },
            e("settings_fallback_source_title", S, "fallback_source", "Alternative source", group = SettingsGroup.QUALITY) { it.fallbackSource },
            e("settings_pause_headphones_title", S, "pause_on_unplug", "Pause when headphones are out", group = SettingsGroup.OUTPUT) { it.pauseOnHeadphonesOut },
            e("settings_resume_headphones_title", S, "resume_on_plug", "Resume when headphones are in", group = SettingsGroup.OUTPUT) { it.resumeOnHeadphonesIn },
            e("devices", S, null, "Audio outputs", group = SettingsGroup.OUTPUT, androidOnly = true),
            // 03 ТОН И СКОРОСТЬ
            e("settings_speed_title", T, "speed", "Speed") { it.speed },
            e("settings_pitch_title", T, "pitch", "Pitch") { it.pitch },
            e("settings_reverb_title", T, "reverb", "Reverb") { it.reverb },
            // 04 ТЕКСТ И ВИЗУАЛИЗАТОР
            e("settings_lyrics_title", V, "show_lyrics", "Lyrics", group = SettingsGroup.LYRICS) { it.showLyrics },
            e("lyrics_size_title", V, "lyrics_size", "Lyrics size", group = SettingsGroup.LYRICS) { it.lyricsSize },
            e("lyrics_delay_title", V, "lyrics_delay_ms", "Lyrics delay, ms", group = SettingsGroup.LYRICS) { it.lyricsDelayMs },
            e("lyrics_sources_title", V, "lyrics_all_sources", "All lyrics sources", group = SettingsGroup.LYRICS) { it.lyricsAllSources },
            e("viz_style_title", V, "visualizer_style", "Visualizer style", group = SettingsGroup.VIZ) { it.visualizerStyle },
            e("viz_sensitivity_title", V, "visualizer_sensitivity", "Visualizer sensitivity", group = SettingsGroup.VIZ) { it.visualizerSensitivity },
            e("viz_fps_title", V, "visualizer_fps", "Visualizer FPS", group = SettingsGroup.VIZ) { it.visualizerFps },
            e("viz_backdrop_title", V, "visualizer_backdrop", "Cover behind visualizer", group = SettingsGroup.VIZ) { it.visualizerCoverBackdrop },
            // 05 РЕЖИМ ПОДСТАВКИ
            e("stand_view_title", D, "stand_view", "Stand view", group = SettingsGroup.STAND_LOOK) { it.standView },
            e("stand_clock_title", D, "stand_clock", "Clock", group = SettingsGroup.STAND_LOOK) { it.standClock },
            e("stand_lyrics_title", D, "stand_lyrics", "Lyrics on stand", group = SettingsGroup.STAND_LOOK) { it.standLyrics },
            e("stand_viz_title", D, "stand_visualizer", "Visualizer on stand", group = SettingsGroup.STAND_LOOK) { it.standVisualizer },
            e("stand_brightness_title", D, "stand_brightness", "Brightness", group = SettingsGroup.STAND_LOOK) { it.standBrightness },
            e("stand_marquee_title", D, "stand_marquee", "Scrolling title", group = SettingsGroup.STAND_LOOK) { it.standMarquee },
            e("stand_flip_title", D, "stand_flipped", "Flip screen", group = SettingsGroup.STAND_LOOK) { it.standFlipped },
            e("stand_charging_title", D, "stand_charging_only", "Only while charging", group = SettingsGroup.STAND_RULES) { it.standChargingOnly },
            e("stand_max_title", D, "stand_max_minutes", "Maximum duration, min", group = SettingsGroup.STAND_RULES) { it.standMaxMinutes },
            e("stand_battery_title", D, "stand_low_battery_exit", "Exit on low battery", group = SettingsGroup.STAND_RULES) { it.standLowBatteryExit },
            // 06 ЗАГРУЗКИ И ПАМЯТЬ
            e("settings_cache_title", M, "cache_limit_mb", "Cache limit, MB", group = SettingsGroup.MEMORY) { it.cacheLimitMb },
            e("settings_clear_cache_title", M, null, "Clear cache", group = SettingsGroup.MEMORY),
            e("settings_wifi_only_title", M, "wifi_only_downloads", "Download on Wi-Fi only", group = SettingsGroup.DOWNLOADS) { it.downloadOnWifiOnly },
            e("settings_autodownload_title", M, "auto_download_liked", "Auto-download likes", group = SettingsGroup.DOWNLOADS) { it.autoDownloadLiked },
            e("dl_auto_playlists_title", M, "auto_download_playlists", "Auto-download playlists", group = SettingsGroup.DOWNLOADS) { it.autoDownloadPlaylists },
            e("dl_quality_wifi_title", M, "dl_quality_wifi", "Download quality · Wi-Fi", group = SettingsGroup.DOWNLOADS) { it.downloadQualityWifi },
            e("dl_quality_mobile_title", M, "dl_quality_mobile", "Download quality · mobile data", group = SettingsGroup.DOWNLOADS) { it.downloadQualityMobile },
            e("dl_format_title", M, null, "Download format", group = SettingsGroup.DOWNLOADS),
            e("dl_parallel_title", M, "dl_parallel", "Parallel downloads", group = SettingsGroup.DOWNLOADS) { it.downloadParallel },
            e("dl_speed_limit_title", M, "dl_speed_limit_kb", "Speed limit, KB/s", group = SettingsGroup.DOWNLOADS) { it.downloadSpeedLimitKb },
            e("dl_retries_title", M, "dl_retries", "Retries", group = SettingsGroup.DOWNLOADS) { it.downloadRetries },
            e("dl_low_battery_title", M, "dl_pause_low_battery", "Pause on low battery", group = SettingsGroup.DOWNLOADS) { it.downloadPauseOnLowBattery },
            e("dl_auto_resume_title", M, "dl_auto_resume", "Resume automatically", group = SettingsGroup.DOWNLOADS) { it.downloadAutoResume },
            e("dl_notifications_title", M, "dl_notifications", "Download notifications", group = SettingsGroup.DOWNLOADS, androidOnly = true) { it.downloadNotifications },
            e("dl_auto_export_title", M, "auto_export", "Save copies to a folder", group = SettingsGroup.DOWNLOADS) { it.autoExport },
            e("dl_storage_limit_title", M, "dl_storage_limit_mb", "Downloads storage limit, MB", group = SettingsGroup.MEMORY) { it.downloadStorageLimitMb },
            e("dl_verify_title", M, null, "Verify downloads", group = SettingsGroup.DOWNLOADS),
            e("dl_clear_title", M, null, "Delete all downloads", group = SettingsGroup.MEMORY),
            // 07 ПОИСК И ЛЕНТЫ
            e("settings_suggestions_title", F, "search_suggestions", "Search suggestions", group = SettingsGroup.SEARCH) { it.searchSuggestions },
            e("settings_search_history_title", F, "save_searches", "Search history", group = SettingsGroup.SEARCH) { it.saveSearchHistory },
            e("settings_history_title", F, "keep_history", "Listening history", group = SettingsGroup.SEARCH) { it.keepHistory },
            e("settings_shelves_title", F, "show_recommendations", "Recommendation shelves", group = SettingsGroup.RECS) { it.showRecommendations },
            e("settings_playlist_recs", F, "playlist_recommendations", "Recommendations in playlists", group = SettingsGroup.RECS) { it.showPlaylistRecommendations },
            e("settings_dial_min_title", F, "dial_min_plays", "Speed dial threshold", group = SettingsGroup.RECS) { it.dialMinPlays },
            e("home_rec_blocks", F, "home_rec_blocks", "Picks on Home", group = SettingsGroup.RECS) { it.homeRecBlocks },
            e("weekly_discovery", F, "weekly_discovery", "Weekly Discoveries playlist", group = SettingsGroup.RECS) { it.weeklyDiscovery },
            e("rec_novelty", F, "rec_novelty", "Familiar ↔ new, %", group = SettingsGroup.RECS) { it.recNovelty },
            e("taste_learning", F, "taste_learning", "Learn my taste", group = SettingsGroup.RECS) { it.tasteLearning },
            e("taste_screen", F, "taste_excluded", "My taste", group = SettingsGroup.RECS) { it.tasteExcluded },
            e("settings_clean_title", F, "clean_mode", "Clean mode", group = SettingsGroup.CLEAN) { it.cleanMode },
            e("settings_clean_fallback_title", F, "explicit_fallback", "If no clean version", group = SettingsGroup.CLEAN) { it.explicitFallback },
            e("settings_hide_explicit_title", F, "hide_explicit", "Hide explicit", group = SettingsGroup.CLEAN) { it.hideExplicit },
            e("settings_mute_swear_title", F, "mute_swear_lines", "Mute swear lines", group = SettingsGroup.CLEAN) { it.muteSwearLines },
            // 08 ВИД
            e("settings_style_title", L, "ui_style", "Look", group = SettingsGroup.STYLE) { it.uiStyle },
            e("settings_theme_title", L, "theme", "Theme", group = SettingsGroup.STYLE) { it.theme },
            e("settings_accent_title", L, "accent", "Accent", group = SettingsGroup.STYLE) { it.accent },
            e("hl_set_weight", L, "hl_line_weight", "Line weight", group = SettingsGroup.STYLE) { it.lineWeight },
            e("hl_set_corners", L, "hl_corners", "Corners", group = SettingsGroup.STYLE) { it.corners },
            e("hl_set_cover", L, "hl_cover_shape", "Cover shape", group = SettingsGroup.STYLE) { it.coverShape },
            e("hl_set_layout", L, "hl_player_layout", "Player layout", group = SettingsGroup.PLAYER_LOOK) { it.playerLayout },
            e("hl_set_caps", L, "hl_caps", "Capitals", group = SettingsGroup.STYLE) { it.caps },
            e("hl_set_density", L, "hl_density", "Density", group = SettingsGroup.STYLE) { it.density },
            e("hl_set_motion", L, "hl_motion", "Motion", group = SettingsGroup.FEEL) { it.motion },
            e("hl_set_iconstyle", L, "hl_icon_style", "Icon style", group = SettingsGroup.STYLE) { it.iconStyle },
            e("hl_set_appicon", L, "hl_app_icon", "App icon", group = SettingsGroup.GENERAL, androidOnly = true) { it.appIcon },
            e("dock_shape_title", L, "dock_shape", "Bottom bar shape", group = SettingsGroup.PANEL) { it.dockShape },
            e("dock_tint_title", L, "dock_tint", "Bottom bar tint", group = SettingsGroup.PANEL) { it.dockTint },
            e("dock_labels_title", L, "dock_labels", "Bottom bar labels", group = SettingsGroup.PANEL) { it.dockLabels },
            e("dock_glow_title", L, "dock_glow", "Bottom bar glow", group = SettingsGroup.PANEL) { it.dockGlow },
            e("settings_start_tab_title", L, "start_tab", "Start tab", group = SettingsGroup.GENERAL) { it.startTab },
            e("settings_language_title", L, "language", "Language", group = SettingsGroup.GENERAL) { it.language },
            e("settings_compact_title", L, "compact_rows", "Compact rows", group = SettingsGroup.STYLE) { it.compactRows },
            e("settings_live_background_title", L, "animated_background", "Live background", group = SettingsGroup.PLAYER_LOOK) { it.animatedBackground },
            e("backdrop_title", L, "backdrop_mode", "Backdrop", group = SettingsGroup.PLAYER_LOOK) { it.backdropMode },
            e("backdrop_image_title", L, "backdrop_image", "Backdrop photo", group = SettingsGroup.PLAYER_LOOK) { it.backdropImage },
            e("season_title", L, "season_mode", "Seasonal mood", group = SettingsGroup.PLAYER_LOOK) { it.seasonMode },
            e("settings_cover_glow_title", L, "player_cover_glow", "Cover glow in player", group = SettingsGroup.PLAYER_LOOK) { it.playerCoverGlow },
            e("player_state_badge", L, "player_state_badge", "State badge in player", group = SettingsGroup.PLAYER_LOOK) { it.playerStateBadge },
            e("settings_haptics_title", L, "haptics", "Vibration", group = SettingsGroup.FEEL) { it.haptics },
            e("hl_set_haptics", L, "hl_haptic_level", "Vibration strength", group = SettingsGroup.FEEL) { it.hapticLevel },
            e("hl_set_reset", L, null, "Reset look", group = SettingsGroup.STYLE),
            // 09 АККАУНТ
            e("settings_account_title", A, null, "Account"),
            e("settings_sync_playlists_title", A, "sync_playlists", "Mirror playlists to the account") { it.syncPlaylists },
            // 10 ДАННЫЕ И ПРИЛОЖЕНИЕ
            e("settings_export_title", X, null, "Export settings", group = SettingsGroup.BACKUP),
            e("settings_import_title", X, null, "Import settings", group = SettingsGroup.BACKUP),
            e("settings_startup_title", X, null, "Track start time", group = SettingsGroup.DIAG),
            e("settings_startup_parts_title", X, null, "Where the time goes", group = SettingsGroup.DIAG),
            e("diagnostics", X, null, "Diagnostics", group = SettingsGroup.DIAG),
            e("settings_about_title", X, null, "About", group = SettingsGroup.ABOUT),
            e("settings_reset_title", X, null, "Reset everything", group = SettingsGroup.ABOUT),
        )

    private val byId = entries.associateBy { it.id }

    fun categoryOf(id: String): SettingsCategory? = byId[id]?.category

    fun entry(id: String): CatalogEntry? = byId[id]

    private val order = entries.mapIndexed { i, e -> e.id to i }.toMap()

    /** Место в каталоге: по нему строки идут в категории в заданном порядке. */
    fun indexOf(id: String): Int = order[id] ?: Int.MAX_VALUE

    /** Избранное для новых установок: качество, таймер сна, громкость, подставка, тема, защита от удаления. */
    const val DEFAULT_FAVORITES = "settings_quality_wifi_title,settings_sleep_title,settings_normalize_title,stand_view_title,settings_theme_title,delete_protection"

    const val MAX_FAVORITES = 6

    fun parseFavorites(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it in byId }.distinct().take(MAX_FAVORITES)

    fun formatFavorites(ids: List<String>): String = ids.distinct().take(MAX_FAVORITES).joinToString(",")

    /** Таблица для `docs/SETTINGS_MAP.md`. */
    fun markdown(): String =
        buildString {
            val d = W0ySettings()
            appendLine("# Карта настроек")
            appendLine()
            appendLine("Файл собирается из `SettingsCatalog` тестом `SettingsCatalogTest`: правишь каталог — обновляется и таблица.")
            appendLine("Каждая настройка живёт ровно в одной категории; «Избранное» и поиск ссылаются на ту же строку.")
            appendLine()
            appendLine("| Категория | Настройка (id строки) | Ключ DataStore | Умолчание | Только Android |")
            appendLine("|---|---|---|---|---|")
            entries.sortedBy { it.category.number }.forEach { en ->
                val def = en.default(d)?.let { if (it is Enum<*>) it.name else it.toString().ifEmpty { "—" } } ?: "—"
                appendLine("| ${en.category.number}. ${en.category.docTitle} | ${en.name} (`${en.id}`) | ${en.key?.let { "`$it`" } ?: "—"} | $def | ${if (en.androidOnly) "да" else ""} |")
            }
            appendLine()
            appendLine("Избранное по умолчанию: ${DEFAULT_FAVORITES.split(',').joinToString(", ") { "`$it`" }}.")
        }
}
