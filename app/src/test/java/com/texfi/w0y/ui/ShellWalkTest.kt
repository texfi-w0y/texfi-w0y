package com.texfi.w0y.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.texfi.w0y.HiltTestActivity
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.db.HistoryEntity
import com.texfi.w0y.data.db.PlaylistEntity
import com.texfi.w0y.data.db.PlaylistSongEntity
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDatabase
import com.texfi.w0y.ui.shell.ShellViewModel
import com.texfi.w0y.ui.shell.W0yShell
import com.texfi.w0y.ui.theme.W0yTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Обход настоящей оболочки: вкладки, «Моё» и его разделы на 60 записях.
 * Последняя строка каждого списка обязана оказаться над островом.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ShellWalkTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<HiltTestActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject lateinit var db: W0yDatabase

    @Inject lateinit var settings: SettingsRepository

    @Before
    fun seed() = runBlocking {
        hilt.inject()
        settings.setWelcomeSeen(true)
        settings.setStylePicked(true)
        val dao = db.dao()
        val now = System.currentTimeMillis()
        val playlist = dao.createPlaylist(PlaylistEntity(name = "Stress", createdAt = now))
        repeat(60) { i ->
            val id = "song$i"
            dao.saveSongMeta(SongEntity(id = id, title = "Track $i", artist = "Artist ${i % 7}", durationMs = 200_000))
            dao.setLiked(id, true, now - i)
            dao.setDownloadState(id, SongEntity.DOWNLOAD_DONE)
            dao.addHistory(HistoryEntity(songId = id, playedAt = now - i * 60_000L, listenedMs = 90_000, counted = true, legacy = false))
            dao.addToPlaylist(PlaylistSongEntity(playlistId = playlist, songId = id, position = i, addedAt = now))
        }
    }

    private val verticalList =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            SemanticsMatcher("не вкладки настроек") { it.config.getOrNull(SemanticsProperties.TestTag) != "settings-tabs" }

    private fun start() {
        compose.setContent {
            val shell: ShellViewModel = hiltViewModel()
            W0yTheme { W0yShell(shell) }
        }
        compose.waitForIdle()
    }

    private fun islandTop(): Float = compose.onNodeWithTag("island").fetchSemanticsNode().boundsInRoot.top

    /** Прокручивает главный список вниз до конца и проверяет, что низ последней строки над островом. */
    private fun assertEndsAboveIsland(label: String, cut: Boolean = true) {
        compose.waitForIdle()
        // Данные читаются из базы на другом потоке: ждём, пока список появится.
        runCatching { compose.waitUntil(8_000) { compose.onAllNodes(verticalList).fetchSemanticsNodes().isNotEmpty() } }
        val lists = compose.onAllNodes(verticalList)
        if (lists.fetchSemanticsNodes().isEmpty()) {
            throw AssertionError("$label: нет прокручиваемого списка\n" + compose.onRoot().printToString(maxDepth = 10).take(2500))
        }
        val list = lists.onFirst()
        val node = list.fetchSemanticsNode()
        val rows = node.config.getOrNull(SemanticsProperties.CollectionInfo)?.rowCount ?: 0
        if (rows > 0) list.performScrollToIndex(rows - 1)
        compose.waitForIdle()
        val listNode = compose.onAllNodes(verticalList).onFirst().fetchSemanticsNode()
        val lowest = listNode.children.maxOfOrNull { it.boundsInRoot.bottom } ?: 0f
        val top = islandTop()
        assertTrue("$label: низ последней строки $lowest заходит под остров $top", lowest <= top + 1f)
        val listBottom = listNode.boundsInRoot.bottom
        if (cut) {
            assertTrue("$label: список обрезан по острову ($listBottom <= $top)", listBottom <= top + 1f)
        } else {
            assertTrue("$label: экран идёт под островом ($listBottom > $top)", listBottom > top + 1f)
        }
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun libraryRoutesEndAboveTheIsland() {
        start()
        compose.onNode(hasContentDescription("yours")).performClick()
        compose.waitForIdle()
        assertEndsAboveIsland("Моё", cut = false)
        listOf("LIKES", "HISTORY", "DOWNLOADED", "MY FILES", "STATS").forEach { label ->
            compose.onAllNodes(hasText(label, substring = true)).onFirst().performClick()
            // «Мои файлы» без добавленных файлов — пустое состояние без списка.
            if (label == "MY FILES") compose.waitForIdle() else assertEndsAboveIsland(label, cut = label != "STATS")
            back()
        }
    }

    @Test
    fun homeAndSearchRenderWithoutCrash() {
        start()
        compose.onNode(hasContentDescription("search")).performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("home")).performClick()
        compose.waitForIdle()
        assertEndsAboveIsland("Главная", cut = false)
    }

    @Test
    fun statsShowTheSeededListening() {
        start()
        compose.onNode(hasContentDescription("yours")).performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("STATS", substring = true)).onFirst().performClick()
        compose.waitUntil(8_000) { compose.onAllNodes(hasText("60", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // 60 прослушиваний по 90 секунд — 90 минут: ни нулей, ни «пусто».
        assertTrue(compose.onAllNodes(hasText("90", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("No listening", substring = true, ignoreCase = true)).fetchSemanticsNodes().isEmpty())
    }
}
