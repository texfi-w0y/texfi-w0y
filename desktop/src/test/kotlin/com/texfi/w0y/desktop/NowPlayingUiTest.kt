package com.texfi.w0y.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.texfi.w0y.data.SongItem
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Большой плеер: текст песни, очередь с историей и подставка. Кадры пишутся при -Dw0y.shots=папка. */
@OptIn(ExperimentalTestApi::class)
class NowPlayingUiTest {
    private fun app(): AppState {
        System.setProperty("w0y.data", Files.createTempDirectory("w0y-test").toString())
        System.setProperty("w0y.ao", "null")
        val app = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Yt(), Store())
        app.setSt { it.copy(uiStyle = UiStyle.HAIRLINE, accent = Accent.BLUE, language = Language.EN, welcomeSeen = true, stylePicked = true, showLyrics = true) }
        return app
    }

    private fun androidx.compose.ui.test.ComposeUiTest.shot(name: String) {
        val dir = System.getProperty("w0y.shots") ?: return
        javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private val words = listOf("Тише, город засыпает", "Свет в окне уже погас", "Я иду по тёмной улице", "Где-то музыка звучит", "Ты была моей весною", "Ты осталась в тишине", "Всё, что было, всё прошло", "Но осталась песня эта", "Пусть она звучит для нас", "До рассвета, до утра")

    /** Цветная картинка вместо обложки: по ней видно, как свечение берёт цвета. */
    private fun art(): String {
        val img = java.awt.image.BufferedImage(400, 400, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(0x7B2FF7), 400f, 400f, java.awt.Color(0xF7A02F))
        g.fillRect(0, 0, 400, 400)
        g.paint = java.awt.Color(0x101018)
        g.fillOval(110, 110, 180, 180)
        g.dispose()
        val f = File.createTempFile("w0y-art", ".png").also { it.deleteOnExit() }
        javax.imageio.ImageIO.write(img, "png", f)
        return f.toURI().toString()
    }

    private fun playing(app: AppState): List<SongItem> {
        val cover = art()
        val songs = (0 until 7).map { SongItem(id = "n$it", title = "Ночной трек $it", artist = "Исполнитель ${it % 3}", durationText = "3:${10 + it}", thumbnailUrl = cover) }
        app.player.start(songs, 2, 80)
        app.lyrics.seed("n2", Lyrics(null, words.mapIndexed { i, w -> LyricLine(i * 8_000L, w) }))
        app.player.duration = 214.0
        app.player.position = 33.0
        return songs
    }

    @Test
    fun bigPlayerShowsLyricsAndOwnControlsWithoutBottomBar() {
        val app = app()
        playing(app)
        app.open(Screen.Player)
        runDesktopComposeUiTest(1280, 800) {
            setContent { App(app) }
            waitForIdle()
            // Звучащая строка (33 с → четвёртая), и соседние тоже в списке.
            onNode(hasText(words[4])).assertExists()
            onNodeWithTag("now-toggle").assertExists()
            shot("now-lyrics")
        }
    }

    @Test
    fun queueShowsNowNextAndPlayedAndClears() {
        val app = app()
        playing(app)
        app.open(Screen.Player)
        runDesktopComposeUiTest(1280, 800) {
            setContent { App(app) }
            waitForIdle()
            onNode(hasText("QUEUE", ignoreCase = true)).performClick()
            waitForIdle()
            onNode(hasText("Ночной трек 3")).assertExists()
            onNode(hasText("Ночной трек 2")).assertExists() // играющий в карточке «Сейчас»
            shot("now-queue")
            // «Уже играло» свёрнуто, пока не раскрыть.
            onNode(hasText("Already played", ignoreCase = true)).performClick()
            waitForIdle()
            onNode(hasText("Ночной трек 0")).assertExists()
            shot("now-queue-played")
            onNode(hasText("Clear")).performClick()
            waitForIdle()
            assertEquals(3, app.player.queue.size) // остались сыгранные и играющий
            assertTrue(app.player.queue.last().id == "n2")
        }
    }

    @Test
    fun standShowsCoverClockAndLyricLines() {
        val app = app()
        playing(app)
        app.setSt { it.copy(standView = StandView.COVER, standLyrics = true) }
        app.stand.enter()
        runDesktopComposeUiTest(1280, 800) {
            setContent { App(app) }
            waitForIdle()
            onAllNodes(hasText("Ночной трек 2")).fetchSemanticsNodes().isNotEmpty().let { assertTrue(it) }
            shot("stand-cover")
        }
        app.setSt { it.copy(standView = StandView.TEXT) }
        runDesktopComposeUiTest(1280, 800) {
            setContent { App(app) }
            waitForIdle()
            shot("stand-text")
        }
        app.stand.off()
    }

    @Test
    fun sidebarAndBottomBarCanBeHidden() {
        val app = app()
        playing(app)
        runDesktopComposeUiTest(1280, 800) {
            setContent { App(app) }
            waitForIdle()
            onNode(hasText("Hide the bar")).assertExists()
            onNode(hasText("0:33 / 3:34")).assertExists()
            shot("panels-shown")
        }
        app.setSt { it.copy(sidebarHidden = true, barHidden = true) }
        runDesktopComposeUiTest(1280, 800) {
            setContent { App(app) }
            waitForIdle()
            assertTrue(onAllNodes(hasText("Hide the bar")).fetchSemanticsNodes().isEmpty(), "боковая панель не спряталась")
            assertTrue(onAllNodes(hasText("0:33 / 3:34")).fetchSemanticsNodes().isEmpty(), "нижняя панель не спряталась")
            shot("panels-hidden")
        }
    }
}
