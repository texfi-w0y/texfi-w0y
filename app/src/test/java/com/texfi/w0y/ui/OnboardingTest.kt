package com.texfi.w0y.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.hilt.navigation.compose.hiltViewModel
import com.texfi.w0y.HiltTestActivity
import com.texfi.w0y.data.BackdropMode
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.ui.shell.ShellViewModel
import com.texfi.w0y.ui.shell.W0yShell
import com.texfi.w0y.ui.theme.W0yTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Первый запуск на настоящей оболочке: привет → аккаунт (без входа) → цвет → фон, дальше на
 * главной знакомство с жестами. Стиль — Hairline New без выбора. Кадры снимает [OnboardingShotsTest].
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "ru-w411dp-h891dp-xxhdpi")
class OnboardingTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<HiltTestActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject lateinit var settings: SettingsRepository

    @Before
    fun inject() {
        hilt.inject()
    }

    private fun frames(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        // Настройки пишутся на своих потоках по настоящим часам; их продолжения ждут главный поток.
        Thread.sleep(200)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeBy(32)
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()

    private fun tap(text: String) {
        compose.onNode(hasText(text, ignoreCase = true)).performClick()
        frames(700)
    }

    @Test
    fun firstRunWalksThroughAndTeachesGestures() {
        compose.setContent {
            val shell: ShellViewModel = hiltViewModel()
            W0yTheme { W0yShell(shell) }
        }
        compose.mainClock.autoAdvance = false
        frames(2500)
        // Первый запуск ставит Hairline New по-настоящему (постер, точечный фон) — ждём эту запись.
        repeat(40) { if (runBlocking { settings.settings.first().backdropMode } != BackdropMode.GRID) frames(300) }
        assertEquals("Hairline New ставит точечный фон", BackdropMode.GRID, runBlocking { settings.settings.first().backdropMode })
        assertTrue("привет", has("Начать"))
        assertFalse("выбора стиля нет", has("Pixel"))

        tap("Начать")
        assertTrue("аккаунт", has("Войти через Google"))

        tap("Без входа")
        assertTrue("цвет", has("Акцент"))

        tap("Дальше")
        assertTrue("фон", has("Переливы"))
        tap("Переливы")
        // Запись настроек идёт на своём потоке: даём ей дойти.
        var mode = runBlocking { settings.settings.first().backdropMode }
        repeat(10) { if (mode != BackdropMode.AURORA) { frames(300); mode = runBlocking { settings.settings.first().backdropMode } } }
        assertEquals("фон меняется сразу", BackdropMode.AURORA, mode)

        tap("Готово")
        frames(2500)
        repeat(15) { if (!runBlocking { settings.settings.first() }.welcomeSeen) frames(300) }
        val s = runBlocking { settings.settings.first() }
        assertTrue("первый запуск закрыт", s.welcomeSeen)
        assertEquals("стиль по умолчанию — Hairline New", UiStyle.ISLAND, s.uiStyle)
        repeat(10) { if (!has("Что здесь нового")) frames(300) }
        assertTrue("знакомство с жестами", has("Что здесь нового"))

        tap("Показать")
        assertTrue(has("Мини-плеер — вверх"))
        frames(900)
        // Повторить жест на макете: вверх — засчитывается и ведёт дальше.
        compose.onRoot().performTouchInput {
            down(Offset(centerX, centerY + 80f))
            repeat(12) { moveBy(Offset(0f, -40f)) }
            up()
        }
        frames(1400)
        assertTrue("жест засчитан, следующий шаг", has("Мини-плеер — вбок"))

        tap("Пропустить")
        frames(600)
        assertFalse("знакомство закрыто", has("Мини-плеер"))
        repeat(10) { if (!runBlocking { settings.settings.first() }.tourSeen) frames(300) }
        assertTrue(runBlocking { settings.settings.first().tourSeen })
    }
}
