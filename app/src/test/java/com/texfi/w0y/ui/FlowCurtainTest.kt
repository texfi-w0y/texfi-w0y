package com.texfi.w0y.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.texfi.w0y.HiltTestActivity
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDatabase
import com.texfi.w0y.ui.screens.SearchScreen
import com.texfi.w0y.ui.theme.W0yTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * fl0w в поиске Hairline New: первый раз «потяни меня» не открывается лёгкой тягой, рвётся за
 * пределом, дальше приветствие и настройки; вверх — обратно в поиск, и вступления больше нет.
 * Кадры каждого шага кладутся в build/flow-shots — посмотреть глазами без телефона.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "ru-w411dp-h891dp-xxhdpi")
class FlowCurtainTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<HiltTestActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject lateinit var db: W0yDatabase

    @Inject lateinit var settings: SettingsRepository

    @Before
    fun seed() = runBlocking {
        hilt.inject()
        val dao = db.dao()
        repeat(12) { i ->
            dao.saveSongMeta(SongEntity(id = "song$i", title = "Track $i", artist = "Artist ${i % 4}", thumbnailUrl = "https://example.invalid/$i.jpg"))
            dao.setLiked("song$i", true, 1000L + i)
        }
    }

    private fun frames(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        Thread.sleep(150)
        compose.mainClock.advanceTimeBy(32)
    }

    private fun shot(name: String) {
        val dir = File("build/flow-shots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun firstPullBreaksFreeThenWelcomePanelAndBack() {
        compose.setContent {
            W0yTheme(style = UiStyle.ISLAND) {
                // Фон, как в оболочке: без него полупрозрачная шторка ляжет на белое окно теста.
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(com.texfi.w0y.ui.theme.LocalW0yColors.current.background)) { SearchScreen() }
            }
        }
        compose.mainClock.autoAdvance = false
        frames(600)
        assertTrue("висит «потяни меня»", has("ПОТЯНИ МЕНЯ"))
        shot("1-pull-me")

        val root = compose.onRoot()
        val down = root.fetchSemanticsNode().boundsInRoot
        root.performTouchInput { down(androidx.compose.ui.geometry.Offset(centerX, 300f)) }
        // Тянем до половины предела: плёнка тянется, но не рвётся.
        repeat(10) {
            root.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, 34f)) }
            frames(16)
        }
        shot("2-tension")
        assertFalse("от половины тяги не открылось", has("Дальше"))
        // Дальше предела — рвётся сама, ещё до того, как отпустили.
        repeat(16) {
            root.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, 34f)) }
            frames(16)
        }
        frames(1000)
        shot("3-burst")
        root.performTouchInput { up() }
        frames(2400)
        assertTrue("приветствие", has("Дальше"))
        shot("4-welcome")
        assertTrue("вступление запомнено", runBlocking { settings.settings.first().flowIntroSeen })

        compose.onNode(hasText("Дальше", ignoreCase = true)).performClick()
        frames(600)
        assertTrue("выбор настроения", has("Что угодно"))
        shot("5-panel")

        // Настройки fl0w: зачеркнуть «Зло» — из выбора оно пропадает.
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Настройки fl0w")).performClick()
        frames(600)
        assertTrue("страница настроек", has("Никогда не показывать настроения"))
        compose.onNode(hasText("Зло")).performClick()
        frames(400)
        shot("5b-settings")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Назад")).performClick()
        frames(600)
        assertTrue(has("Что угодно"))
        assertFalse("скрытое настроение пропало", has("Зло"))

        // Вверх за край шторки — обратно в поиск.
        val bottom = down.bottom
        root.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, bottom - 40f))
            repeat(12) { moveBy(androidx.compose.ui.geometry.Offset(0f, -40f)) }
            up()
        }
        frames(900)
        shot("6-back")
        assertFalse("шторка ушла", has("Что угодно"))
        assertFalse("вступления больше нет", has("ПОТЯНИ МЕНЯ"))

        // Со второго раза «fl0w» в заголовке открывает шторку нажатием.
        compose.onNode(hasText("fl0w")).performClick()
        frames(900)
        assertTrue("открылась нажатием", has("Что угодно"))
    }
}
