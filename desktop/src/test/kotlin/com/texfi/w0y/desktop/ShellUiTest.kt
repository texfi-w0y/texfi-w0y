package com.texfi.w0y.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.desktop.i18n.S
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Интерфейс ПК без окна и без сети: каркас Hairline на трёх размерах, восемь вкладок настроек,
 * переключение языка, последняя строка длинного списка, выделение Ctrl/Shift.
 */
@OptIn(ExperimentalTestApi::class)
class ShellUiTest {
    private fun app(): AppState {
        System.setProperty("w0y.data", Files.createTempDirectory("w0y-test").toString())
        System.setProperty("w0y.ao", "null")
        val app = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Yt(), Store())
        app.setSt { it.copy(uiStyle = UiStyle.HAIRLINE, accent = Accent.BLUE, language = Language.EN, welcomeSeen = true, stylePicked = true) }
        return app
    }

    @Test
    fun shellShowsLabelsOnWideAndIconsOnCompactWindow() {
        val app = app()
        // Подпись «texfi» под логотипом есть только в широкой боковой панели; в узкой остаются значки.
        for ((w, h, wide) in listOf(Triple(960, 600, false), Triple(1280, 800, true), Triple(1800, 1000, true))) {
            runDesktopComposeUiTest(w, h) {
                setContent { App(app) }
                waitForIdle()
                assertEquals(if (wide) 1 else 0, onAllNodesWithTextCount("texfi"), "боковая панель в окне $w×$h")
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextCount(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size

    @Test
    fun settingsHasEightTabsAndOpensEachOne() {
        val app = app()
        runDesktopComposeUiTest(1280, 800) {
            setContent { SettingsScreen(app) }
            val tabs = listOf("cat_playback", "cat_sound", "cat_storage", "cat_search_feeds", "cat_look", "cat_stand", "cat_account", "cat_data").map { S(it) }
            assertEquals(8, tabs.toSet().size)
            for (title in tabs) {
                onNodeWithText(title).assertIsDisplayed()
                onNodeWithText(title).performClick()
                waitForIdle()
            }
        }
    }

    @Test
    fun languageSwitchChangesInterfaceText() {
        val app = app()
        runDesktopComposeUiTest(1280, 800) {
            setContent { SettingsScreen(app) }
            val en = S("settings_title")
            onNode(hasText(en, ignoreCase = true)).assertIsDisplayed()
            app.setSt { it.copy(language = Language.RU) }
            waitForIdle()
            val ru = S("settings_title")
            assertTrue(en != ru, "строки ru и en различаются")
            onNode(hasText(ru, ignoreCase = true)).assertIsDisplayed()
        }
    }

    @Test
    fun lastRowOfLongListIsReachable() {
        val app = app()
        val songs = (0 until 600).map { SongItem(id = "id$it", title = "Track number $it", artist = "Artist") }
        runDesktopComposeUiTest(960, 600) {
            setContent { Box(Modifier.fillMaxSize()) { SongList(app, songs) } }
            onNode(hasScrollAction()).performScrollToNode(hasText("Track number 599"))
            onNodeWithText("Track number 599").assertIsDisplayed()
        }
    }

    @Test
    fun selectionSupportsToggleAndRange() {
        val songs = (0 until 10).map { SongItem(id = "s$it", title = "t$it", artist = "a") }
        val sel = Selection()
        sel.only("s2", 2)
        sel.toggle("s5", 5)
        assertEquals(listOf("s2", "s5"), sel.of(songs).map { it.id })
        sel.only("s3", 3)
        sel.range(songs, 6)
        assertEquals(listOf("s3", "s4", "s5", "s6"), sel.of(songs).map { it.id })
        sel.clear()
        assertTrue(sel.ids.isEmpty())
    }
}
