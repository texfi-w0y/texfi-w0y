# Карта настроек

Файл собирается из `SettingsCatalog` тестом `SettingsCatalogTest`: правишь каталог — обновляется и таблица.
Каждая настройка живёт ровно в одной вкладке; поиск ссылается на ту же строку.

| Вкладка | Настройка (id строки) | Ключ DataStore | Умолчание | Только Android |
|---|---|---|---|---|
| 1. ВОСПРОИЗВЕДЕНИЕ | Queue mode (`settings_queue_title`) | `queue_mode` | ORDER |  |
| 1. ВОСПРОИЗВЕДЕНИЕ | When the queue ends (`rec_end_mode`) | `rec_end_mode` | CONTINUE |  |
| 1. ВОСПРОИЗВЕДЕНИЕ | Similar instead of the list (`rec_instead_of_list`) | `rec_instead_of_list` | false |  |
| 1. ВОСПРОИЗВЕДЕНИЕ | Preload next track (`settings_preload_title`) | `preload_next` | true |  |
| 1. ВОСПРОИЗВЕДЕНИЕ | Seek step, s (`settings_seek_step_title`) | `seek_step_sec` | 10 |  |
| 1. ВОСПРОИЗВЕДЕНИЕ | Swipe mini-player down to stop (`hl_set_swipe`) | `hl_swipe_dismiss` | true | да |
| 1. ВОСПРОИЗВЕДЕНИЕ | Accidental delete protection (`delete_protection`) | `delete_protection` | true |  |
| 1. ВОСПРОИЗВЕДЕНИЕ | Sleep timer default, min (`settings_sleep_title`) | `sleep_timer_default` | 30 |  |
| 2. ЗВУК | Stream quality · Wi-Fi (`settings_quality_wifi_title`) | `quality_wifi` | HIGH |  |
| 2. ЗВУК | Stream quality · mobile data (`settings_quality_mobile_title`) | `quality_mobile` | MEDIUM |  |
| 2. ЗВУК | Volume normalization (`settings_normalize_title`) | `normalize_volume` | false |  |
| 2. ЗВУК | Skip silence (`settings_skip_silence_title`) | `skip_silence` | false |  |
| 2. ЗВУК | Equalizer (`settings_equalizer_title`) | — | — | да |
| 2. ЗВУК | Alternative audio source (`settings_fallback_title`) | `fallback_audio` | true |  |
| 2. ЗВУК | Alternative source (`settings_fallback_source_title`) | `fallback_source` | AUTO |  |
| 2. ЗВУК | Pause when headphones are out (`settings_pause_headphones_title`) | `pause_on_unplug` | true |  |
| 2. ЗВУК | Resume when headphones are in (`settings_resume_headphones_title`) | `resume_on_plug` | false |  |
| 2. ЗВУК | Audio outputs (`devices`) | — | — | да |
| 2. ЗВУК | Speed (`settings_speed_title`) | `speed` | 1.0 |  |
| 2. ЗВУК | Pitch (`settings_pitch_title`) | `pitch` | 1.0 |  |
| 2. ЗВУК | Reverb (`settings_reverb_title`) | `reverb` | OFF |  |
| 3. СКАЧИВАНИЕ | Cache limit, MB (`settings_cache_title`) | `cache_limit_mb` | 512 |  |
| 3. СКАЧИВАНИЕ | Clear cache (`settings_clear_cache_title`) | — | — |  |
| 3. СКАЧИВАНИЕ | Download on Wi-Fi only (`settings_wifi_only_title`) | `wifi_only_downloads` | true |  |
| 3. СКАЧИВАНИЕ | Auto-download likes (`settings_autodownload_title`) | `auto_download_liked` | false |  |
| 3. СКАЧИВАНИЕ | Auto-download playlists (`dl_auto_playlists_title`) | `auto_download_playlists` | [] |  |
| 3. СКАЧИВАНИЕ | Download quality · Wi-Fi (`dl_quality_wifi_title`) | `dl_quality_wifi` | HIGH |  |
| 3. СКАЧИВАНИЕ | Download quality · mobile data (`dl_quality_mobile_title`) | `dl_quality_mobile` | HIGH |  |
| 3. СКАЧИВАНИЕ | Download format (`dl_format_title`) | — | — |  |
| 3. СКАЧИВАНИЕ | Parallel downloads (`dl_parallel_title`) | `dl_parallel` | 2 |  |
| 3. СКАЧИВАНИЕ | Speed limit, KB/s (`dl_speed_limit_title`) | `dl_speed_limit_kb` | 0 |  |
| 3. СКАЧИВАНИЕ | Retries (`dl_retries_title`) | `dl_retries` | 3 |  |
| 3. СКАЧИВАНИЕ | Pause on low battery (`dl_low_battery_title`) | `dl_pause_low_battery` | true |  |
| 3. СКАЧИВАНИЕ | Resume automatically (`dl_auto_resume_title`) | `dl_auto_resume` | true |  |
| 3. СКАЧИВАНИЕ | Download notifications (`dl_notifications_title`) | `dl_notifications` | true | да |
| 3. СКАЧИВАНИЕ | Save copies to a folder (`dl_auto_export_title`) | `auto_export` | false |  |
| 3. СКАЧИВАНИЕ | Downloads storage limit, MB (`dl_storage_limit_title`) | `dl_storage_limit_mb` | 0 |  |
| 3. СКАЧИВАНИЕ | Verify downloads (`dl_verify_title`) | — | — |  |
| 3. СКАЧИВАНИЕ | Delete all downloads (`dl_clear_title`) | — | — |  |
| 4. ПОИСК И ЛЕНТЫ | Search suggestions (`settings_suggestions_title`) | `search_suggestions` | true |  |
| 4. ПОИСК И ЛЕНТЫ | Search history (`settings_search_history_title`) | `save_searches` | true |  |
| 4. ПОИСК И ЛЕНТЫ | Listening history (`settings_history_title`) | `keep_history` | true |  |
| 4. ПОИСК И ЛЕНТЫ | Recommendation shelves (`settings_shelves_title`) | `show_recommendations` | true |  |
| 4. ПОИСК И ЛЕНТЫ | Recommendations in playlists (`settings_playlist_recs`) | `playlist_recommendations` | false |  |
| 4. ПОИСК И ЛЕНТЫ | Speed dial threshold (`settings_dial_min_title`) | `dial_min_plays` | 1 |  |
| 4. ПОИСК И ЛЕНТЫ | Picks on Home (`home_rec_blocks`) | `home_rec_blocks` | true |  |
| 4. ПОИСК И ЛЕНТЫ | Weekly Discoveries playlist (`weekly_discovery`) | `weekly_discovery` | false |  |
| 4. ПОИСК И ЛЕНТЫ | Familiar ↔ new, % (`rec_novelty`) | `rec_novelty` | 30 |  |
| 4. ПОИСК И ЛЕНТЫ | Learn my taste (`taste_learning`) | `taste_learning` | true |  |
| 4. ПОИСК И ЛЕНТЫ | My taste (`taste_screen`) | `taste_excluded` | — |  |
| 4. ПОИСК И ЛЕНТЫ | Clean mode (`settings_clean_title`) | `clean_mode` | false |  |
| 4. ПОИСК И ЛЕНТЫ | If no clean version (`settings_clean_fallback_title`) | `explicit_fallback` | PLAY |  |
| 4. ПОИСК И ЛЕНТЫ | Hide explicit (`settings_hide_explicit_title`) | `hide_explicit` | false |  |
| 4. ПОИСК И ЛЕНТЫ | Mute swear lines (`settings_mute_swear_title`) | `mute_swear_lines` | false |  |
| 5. ВИД | Lyrics (`settings_lyrics_title`) | `show_lyrics` | true |  |
| 5. ВИД | Lyrics size (`lyrics_size_title`) | `lyrics_size` | MEDIUM |  |
| 5. ВИД | Lyrics delay, ms (`lyrics_delay_title`) | `lyrics_delay_ms` | 0 |  |
| 5. ВИД | All lyrics sources (`lyrics_sources_title`) | `lyrics_all_sources` | true |  |
| 5. ВИД | Visualizer style (`viz_style_title`) | `visualizer_style` | SEGMENTS |  |
| 5. ВИД | Visualizer sensitivity (`viz_sensitivity_title`) | `visualizer_sensitivity` | 1.0 |  |
| 5. ВИД | Visualizer FPS (`viz_fps_title`) | `visualizer_fps` | 60 |  |
| 5. ВИД | Cover behind visualizer (`viz_backdrop_title`) | `visualizer_backdrop` | true |  |
| 5. ВИД | Look (`settings_style_title`) | `ui_style` | HAIRLINE |  |
| 5. ВИД | Theme (`settings_theme_title`) | `theme` | DARK |  |
| 5. ВИД | Accent (`settings_accent_title`) | `accent` | SAND |  |
| 5. ВИД | Line weight (`hl_set_weight`) | `hl_line_weight` | THIN |  |
| 5. ВИД | Corners (`hl_set_corners`) | `hl_corners` | SHARP |  |
| 5. ВИД | Cover shape (`hl_set_cover`) | `hl_cover_shape` | ROUNDED |  |
| 5. ВИД | Player layout (`hl_set_layout`) | `hl_player_layout` | RING |  |
| 5. ВИД | Capitals (`hl_set_caps`) | `hl_caps` | TITLES_LABELS |  |
| 5. ВИД | Density (`hl_set_density`) | `hl_density` | COMFORTABLE |  |
| 5. ВИД | Motion (`hl_set_motion`) | `hl_motion` | AUTO |  |
| 5. ВИД | Icon style (`hl_set_iconstyle`) | `hl_icon_style` | LINE |  |
| 5. ВИД | App icon (`hl_set_appicon`) | `hl_app_icon` | W | да |
| 5. ВИД | Bottom bar shape (`dock_shape_title`) | `dock_shape` | ISLAND |  |
| 5. ВИД | Bottom bar tint (`dock_tint_title`) | `dock_tint` | COVER |  |
| 5. ВИД | Bottom bar labels (`dock_labels_title`) | `dock_labels` | ACTIVE |  |
| 5. ВИД | Bottom bar glow (`dock_glow_title`) | `dock_glow` | true |  |
| 5. ВИД | Start tab (`settings_start_tab_title`) | `start_tab` | LAST |  |
| 5. ВИД | Language (`settings_language_title`) | `language` | SYSTEM |  |
| 5. ВИД | Compact rows (`settings_compact_title`) | `compact_rows` | false |  |
| 5. ВИД | Live background (`settings_live_background_title`) | `animated_background` | true |  |
| 5. ВИД | Backdrop (`backdrop_title`) | `backdrop_mode` | GRID |  |
| 5. ВИД | Backdrop photo (`backdrop_image_title`) | `backdrop_image` | — |  |
| 5. ВИД | Seasonal mood (`season_title`) | `season_mode` | AUTO |  |
| 5. ВИД | Cover glow in player (`settings_cover_glow_title`) | `player_cover_glow` | true |  |
| 5. ВИД | State badge in player (`player_state_badge`) | `player_state_badge` | true |  |
| 5. ВИД | Vibration (`settings_haptics_title`) | `haptics` | true |  |
| 5. ВИД | Vibration strength (`hl_set_haptics`) | `hl_haptic_level` | FULL |  |
| 5. ВИД | Reset look (`hl_set_reset`) | — | — |  |
| 6. ПОДСТАВКА | Stand view (`stand_view_title`) | `stand_view` | TEXT |  |
| 6. ПОДСТАВКА | Clock (`stand_clock_title`) | `stand_clock` | true |  |
| 6. ПОДСТАВКА | Lyrics on stand (`stand_lyrics_title`) | `stand_lyrics` | false |  |
| 6. ПОДСТАВКА | Visualizer on stand (`stand_viz_title`) | `stand_visualizer` | false |  |
| 6. ПОДСТАВКА | Brightness (`stand_brightness_title`) | `stand_brightness` | -1.0 |  |
| 6. ПОДСТАВКА | Scrolling title (`stand_marquee_title`) | `stand_marquee` | false |  |
| 6. ПОДСТАВКА | Flip screen (`stand_flip_title`) | `stand_flipped` | false |  |
| 6. ПОДСТАВКА | Only while charging (`stand_charging_title`) | `stand_charging_only` | false |  |
| 6. ПОДСТАВКА | Maximum duration, min (`stand_max_title`) | `stand_max_minutes` | 60 |  |
| 6. ПОДСТАВКА | Exit on low battery (`stand_battery_title`) | `stand_low_battery_exit` | true |  |
| 7. АККАУНТ | Account (`settings_account_title`) | — | — |  |
| 7. АККАУНТ | Mirror playlists to the account (`settings_sync_playlists_title`) | `sync_playlists` | true |  |
| 8. ПРИЛОЖЕНИЕ | Export settings (`settings_export_title`) | — | — |  |
| 8. ПРИЛОЖЕНИЕ | Import settings (`settings_import_title`) | — | — |  |
| 8. ПРИЛОЖЕНИЕ | Track start time (`settings_startup_title`) | — | — |  |
| 8. ПРИЛОЖЕНИЕ | Where the time goes (`settings_startup_parts_title`) | — | — |  |
| 8. ПРИЛОЖЕНИЕ | Diagnostics (`diagnostics`) | — | — |  |
| 8. ПРИЛОЖЕНИЕ | About (`settings_about_title`) | — | — |  |
| 8. ПРИЛОЖЕНИЕ | Reset everything (`settings_reset_title`) | — | — |  |
