package com.texfi.w0y.data

import androidx.annotation.StringRes
import com.texfi.w0y.R

/**
 * Новинка версии, от которой можно отказаться: окно после обновления показывает её
 * переключателем. Это обычная настройка — та же строка есть в Настройках, и решение
 * можно поменять потом.
 */
class ReleaseChoice(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val get: (W0ySettings) -> Boolean,
    val set: suspend (SettingsRepository, Boolean) -> Unit,
)

/** Что из новинок каждой версии можно выключить. Ключ — код сборки (versionCode). */
object ReleaseChoices {
    /** v0.0.4 beta-4 «rethink». */
    private const val BETA_4 = 17

    private val byVersion: Map<Int, List<ReleaseChoice>> =
        mapOf(
            BETA_4 to
                listOf(
                    ReleaseChoice("set_intro", R.string.set_intro, R.string.set_intro_desc, { it.introAnimation }) { r, v -> r.setIntroAnimation(v) },
                    ReleaseChoice("set_playlist_hero", R.string.set_playlist_hero, R.string.set_playlist_hero_desc, { it.playlistHero }) { r, v -> r.setPlaylistHero(v) },
                    ReleaseChoice("set_dial_artist", R.string.set_dial_artist, R.string.set_dial_artist_desc, { it.dialArtist }) { r, v -> r.setDialArtist(v) },
                    ReleaseChoice("set_home_my_playlists", R.string.set_home_my_playlists, R.string.set_home_my_playlists_desc, { it.homeMyPlaylists }) { r, v -> r.setHomeMyPlaylists(v) },
                    ReleaseChoice("set_sheet_magnet", R.string.set_sheet_magnet, R.string.set_sheet_magnet_desc, { it.sheetMagnet }) { r, v -> r.setSheetMagnet(v) },
                    ReleaseChoice("set_sleep_fade", R.string.set_sleep_fade, R.string.set_sleep_fade_desc, { it.sleepFade }) { r, v -> r.setSleepFade(v) },
                ),
        )

    fun of(versionCode: Int): List<ReleaseChoice> = byVersion[versionCode].orEmpty()
}
