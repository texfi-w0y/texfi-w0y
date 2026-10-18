package com.texfi.w0y.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.texfi.w0y.desktop.i18n.S
import kotlinx.coroutines.launch

private fun norm(artist: String) = artist.substringBefore(',').trim().lowercase()

/**
 * «Мой вкус»: кого ты слушаешь (по числу прослушиваний на этом компьютере), кого не предлагать.
 * Всё считается локально и никуда не отправляется.
 */
@Composable
fun TasteScreen(app: AppState) {
    ScreenFrame(S("taste_title"), app, canBack = true) {
        val st = app.st
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt(S("taste_intro"), color = C.Muted, size = 13, maxLines = 3)
            if (!st.tasteLearning) Txt(S("taste_off_note"), color = C.Sand, size = 13, maxLines = 3)
            RowCenter {
                PixelButton(if (st.tasteLearning) S("taste_turn_off") else S("taste_turn_on"), onClick = { app.setSt { it.copy(tasteLearning = !it.tasteLearning) } }, primary = false)
                Gap(w = 8)
                var sure by remember { mutableStateOf(false) }
                PixelButton(if (sure) S("stats_reset_confirm") else S("taste_reset"), onClick = {
                    if (sure) { app.update { it.copy(artistPlays = emptyMap()) }; sure = false } else sure = true
                }, primary = false)
            }
            val top = app.lib.artistPlays.entries.filter { it.key !in st.tasteExcluded }.sortedByDescending { it.value }.take(30)
            SectionLabel(S("taste_top"))
            if (top.isEmpty()) Txt(S("taste_empty"), color = C.Muted, size = 13)
            top.forEach { (artist, plays) ->
                RowCenter(Modifier.fillMaxWidth()) {
                    Txt(artist, modifier = Modifier.weight(1f), size = 14)
                    Txt("$plays", color = C.Muted, size = 12)
                    Gap(w = 12)
                    PixelButton(S("taste_exclude"), onClick = { app.setSt { it.copy(tasteExcluded = it.tasteExcluded + artist) } }, primary = false)
                }
            }
            if (st.tasteExcluded.isNotEmpty()) {
                SectionLabel(S("taste_excluded"))
                st.tasteExcluded.sorted().forEach { artist ->
                    RowCenter(Modifier.fillMaxWidth()) {
                        Txt(artist, modifier = Modifier.weight(1f), size = 14)
                        PixelButton(S("taste_return"), onClick = { app.setSt { it.copy(tasteExcluded = it.tasteExcluded - artist) } }, primary = false)
                    }
                }
            }
        }
    }
}

/**
 * «Открытия» раз в неделю: складывает в локальный плейлист новые для слушателя треки — радио по любимым
 * недавним, без того, что уже слушали и лайкали. Запускается при старте, если прошла неделя.
 */
fun AppState.weeklyIfDue(force: Boolean = false) {
    if (!st.weeklyDiscovery) return
    val week = 7L * 24 * 3600 * 1000
    if (!force && System.currentTimeMillis() - st.lastWeekly < week) return
    scope.launch {
        val seeds = lib.history.take(60).groupBy { norm(it.artist) }.entries.sortedByDescending { it.value.size }.take(3).mapNotNull { it.value.firstOrNull()?.toItem() }
        if (seeds.isEmpty()) return@launch
        val known = (lib.liked.map { it.id } + lib.history.map { it.id } + lib.playLog.map { it.song.id }).toSet()
        val picks = LinkedHashMap<String, com.texfi.w0y.data.SongItem>()
        for (seed in seeds) {
            runCatching { recommender.forSeed(seed, exclude = known, limit = 20) }.getOrDefault(emptyList()).forEach { picks.putIfAbsent(it.id, it) }
        }
        val songs = picks.values.take(25).map { it.stored() }
        if (songs.isEmpty()) return@launch
        val name = S("home_block_discoveries")
        update { l ->
            val existing = l.playlists.firstOrNull { it.name == name && it.remoteId == null }
            l.copy(
                playlists = if (existing != null) l.playlists.map { if (it.id == existing.id) it.copy(songs = songs) else it } else l.playlists + StoredPlaylist(name = name, songs = songs),
                settings = l.settings.copy(lastWeekly = System.currentTimeMillis()),
            )
        }
    }
}
