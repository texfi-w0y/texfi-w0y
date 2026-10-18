package com.texfi.w0y.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.texfi.w0y.data.ArtistCard
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SongItem
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@OptIn(ExperimentalTestApi::class)
class SearchDialTest {
    private fun app(): AppState {
        System.setProperty("w0y.data", Files.createTempDirectory("w0y-test").toString())
        System.setProperty("w0y.ao", "null")
        val app = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Yt(), Store())
        app.setSt { it.copy(uiStyle = UiStyle.HAIRLINE, accent = Accent.BLUE, language = Language.EN, welcomeSeen = true, stylePicked = true) }
        return app
    }

    private fun song(i: Int, artist: String = "Artist ${i % 3}") = StoredSong("id$i", "Track $i", artist, duration = "3:1$i")

    private fun androidx.compose.ui.test.ComposeUiTest.shot(name: String) {
        val dir = System.getProperty("w0y.shots") ?: return
        javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    @Test
    fun keyboardLayoutIsFixedBothWays() {
        assertEquals("привет", KeyboardLayout.fix("ghbdtn"))
        assertEquals("hello", KeyboardLayout.fix("руддщ"))
        assertEquals("Привет мир", KeyboardLayout.fix("Ghbdtn vbh"))
        assertNull(KeyboardLayout.fix("ab")) // слишком коротко, чтобы гадать
        assertNull(KeyboardLayout.fix("mixедл")) // смешанный алфавит исправлять нечего
    }

    @Test
    fun localMatchesIgnoreCaseYoAndPunctuation() {
        val app = app()
        app.update {
            it.copy(
                liked = listOf(StoredSong("a", "Ёжик в тумане", "Мульт"), StoredSong("b", "Другой", "Артист")),
                history = listOf(StoredSong("c", "Ежевика", "Кто-то")),
                localFiles = listOf(StoredSong("local:z.mp3", "Моя ёлка", "Я")),
            )
        }
        assertEquals(listOf("a"), localMatches(app, "ёжик").map { it.id })
        assertTrue(localMatches(app, "еж").map { it.id }.containsAll(listOf("a", "c")))
        assertEquals(listOf("local:z.mp3"), localMatches(app, "елка").map { it.id })
        assertTrue(localMatches(app, "   ").isEmpty())
        assertEquals(listOf("b"), localMatches(app, "артист другой").map { it.id }) // слова в любом порядке
    }

    @Test
    fun dialPutsPinnedFirstThenMostPlayedThenOwnPlaylistsAndHonoursHidden() {
        val app = app()
        val now = System.currentTimeMillis()
        app.update {
            it.copy(
                playLog = listOf(song(1), song(1), song(1), song(2), song(2), song(3)).mapIndexed { i, s -> StoredPlay(s, now + i, 100) },
                playlists = listOf(StoredPlaylist(id = "pl1", name = "Мой микс", songs = listOf(song(9)))),
            )
        }
        app.togglePin("artist:UC1", "Исполнитель", "Артист", null)
        app.togglePin("album:MP1", "Альбом", "Автор", null)
        val keys = app.dialEntries().map { it.key }
        assertEquals(listOf("album:MP1", "artist:UC1", "song:id1", "song:id2", "song:id3", "playlist:pl1"), keys)
        assertEquals("Артист", app.dialEntries().first { it.key == "artist:UC1" }.subtitle)
        // Убранная плитка уходит, закреплённая при этом открепляется; «отменить» возвращает и закрепление.
        app.hideFromDial("album:MP1")
        assertTrue("album:MP1" !in app.dialEntries().map { it.key })
        app.restoreDial("album:MP1", wasPinned = true)
        assertEquals("album:MP1", app.dialEntries().first().key)
        // Порог запусков из настроек: треки, игранные реже, не попадают.
        app.setSt { it.copy(dialMinPlays = 2) }
        assertTrue("song:id3" !in app.dialEntries().map { it.key })
    }

    @Test
    fun searchAndDialScreensRender() {
        val app = app()
        val now = System.currentTimeMillis()
        app.update {
            it.copy(
                liked = listOf(StoredSong("l1", "Моя любимая", "Исполнитель")),
                playLog = (1..8).flatMap { i -> List(9 - i) { StoredPlay(song(i), now + i, 100) } },
                playlists = listOf(StoredPlaylist(id = "pl1", name = "Ночные поездки", songs = listOf(song(9)), accent = 0xFFB07CE8.toInt(), glyph = "MOON")),
            )
        }
        app.togglePin("artist:UC1", "Исполнитель", "Артист", null)
        // Главная с быстрым набором.
        runDesktopComposeUiTest(1100, 760) {
            setContent { App(app) }
            waitForIdle()
            onNode(hasText("Исполнитель")).assertExists()
            shot("dial-home")
        }
        // Поиск: готовая выдача из кэша, своё над чужим, артист выше треков.
        val results = SearchResults(
            songs = (1..6).map { SongItem("id$it", "Найденный трек $it", "Артист", thumbnailUrl = null) },
            albums = listOf(PlaylistCard("MPREb_1", "Найденный альбом", "Альбом · 2024", null)),
            artists = listOf(ArtistCard("UC9", "Исполнитель", "1,2 млн подписчиков", null)),
        )
        app.searchModel.query = "исполнитель"
        app.searchModel.immediate = true
        app.searchModel.seed("исполнитель", SearchFilter.ALL, results)
        app.open(Screen.Search)
        runDesktopComposeUiTest(1100, 760) {
            setContent { App(app) }
            waitForIdle()
            shot("search-all")
            onNode(hasText("Моя любимая")).assertExists()
            onNode(hasText("Найденный трек 1")).assertExists()
        }
    }
}
