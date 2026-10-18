package com.texfi.w0y.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.graphics.toAwtImage
import com.texfi.w0y.desktop.i18n.S
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Шапка плейлиста, редактор плейлиста и «Мои файлы» рисуются без окна и без сети. Кадры — в папку из `-Dw0y.shots`. */
@OptIn(ExperimentalTestApi::class)
class PlaylistUiTest {
    private fun app(): AppState {
        System.setProperty("w0y.data", Files.createTempDirectory("w0y-test").toString())
        System.setProperty("w0y.ao", "null")
        val app = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Yt(), Store())
        app.setSt { it.copy(uiStyle = UiStyle.HAIRLINE, accent = Accent.BLUE, language = Language.EN, welcomeSeen = true, stylePicked = true) }
        return app
    }

    private fun songs(n: Int) = (1..n).map { StoredSong("id$it", "Track $it", "Artist ${it % 3}", duration = "3:${10 + it}") }

    private fun androidx.compose.ui.test.ComposeUiTest.shot(name: String) {
        val dir = System.getProperty("w0y.shots") ?: return
        val image = onRoot().captureToImage().toAwtImage()
        javax.imageio.ImageIO.write(image, "png", File(dir, "$name.png"))
    }

    @Test
    fun playlistHeaderAndEditorRender() {
        val app = app()
        app.update { it.copy(playlists = listOf(StoredPlaylist(id = "pl1", name = "Ночные поездки", songs = songs(8), note = "Для дороги домой", accent = 0xFFB07CE8.toInt(), glyph = "MOON"))) }
        app.open(Screen.Local("pl1"))
        runDesktopComposeUiTest(1100, 760) {
            setContent { App(app) }
            waitForIdle()
            onNode(hasText("Ночные поездки")).assertExists()
            onNode(hasText("Для дороги домой")).assertExists()
            shot("playlist-header")
            app.playlistEditing = true
            waitForIdle()
            onNode(hasText(S("pl_color").uppercase())).assertExists()
            shot("playlist-editor")
        }
    }

    @Test
    fun localFilesScreenAndEditorRender() {
        val app = app()
        runDesktopComposeUiTest(1100, 760) {
            setContent { App(app) }
            app.open(Screen.LocalFiles)
            waitForIdle()
            onNode(hasText(S("lm_empty_title"))).assertExists()
            shot("local-empty")
            app.update { it.copy(localFiles = listOf(StoredSong("local:a1.mp3", "Моя песня", "Автор", "Альбом", "3:21"), StoredSong("local:b2.flac", "Второй файл", "", null, null))) }
            waitForIdle()
            onNode(hasText("Моя песня")).assertExists()
            shot("local-list")
            app.localEditing = "local:a1.mp3"
            waitForIdle()
            onNode(hasText(S("lm_remove").uppercase())).assertExists()
            shot("local-editor")
        }
        assertTrue(true)
    }
}
