package com.texfi.w0y.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.hilt.navigation.compose.hiltViewModel
import com.texfi.w0y.HiltTestActivity
import com.texfi.w0y.ui.shell.ShellViewModel
import com.texfi.w0y.ui.shell.W0yShell
import com.texfi.w0y.ui.theme.W0yTheme
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Реальная оболочка приложения на Robolectric: запуск не должен падать. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ShellSmokeTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<HiltTestActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @javax.inject.Inject lateinit var settings: com.texfi.w0y.data.SettingsRepository

    @Test
    fun launches() {
        hilt.inject()
        // Настройки одни на весь прогон тестов: начинаем с чистого первого запуска, а не с того, что оставил сосед.
        kotlinx.coroutines.runBlocking { settings.clearAll() }
        compose.setContent {
            val shell: ShellViewModel = hiltViewModel()
            W0yTheme { W0yShell(shell) }
        }
        compose.waitForIdle()
        val skip = androidx.compose.ui.test.hasText("SKIP")
        compose.waitUntil(5_000) { compose.onAllNodes(skip).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(skip).performClick()
        compose.waitForIdle()
        // После первого запуска на главной — знакомство с жестами; пропускаем и его.
        val tourSkip = androidx.compose.ui.test.hasText("Skip", ignoreCase = true)
        runCatching { compose.waitUntil(3_000) { compose.onAllNodes(tourSkip).fetchSemanticsNodes().isNotEmpty() } }
        compose.onAllNodes(tourSkip).fetchSemanticsNodes().firstOrNull()?.let {
            compose.onAllNodes(tourSkip)[0].performClick()
            compose.waitForIdle()
        }
        compose.onNode(androidx.compose.ui.test.hasContentDescription("yours")).performClick()
        compose.waitForIdle()
        dismissIslandOffer()
        // Лишнее окно поверх оболочки — падаем с его содержимым в тексте ошибки, а не с одним «2 корня».
        val roots = compose.onAllNodes(androidx.compose.ui.test.isRoot())
        val count = roots.fetchSemanticsNodes().size
        if (count > 1) throw AssertionError((1 until count).joinToString("\n") { "Лишнее окно:\n" + roots[it].printToString(maxDepth = 12) })
        println("SHELL>>" + compose.onRoot().printToString(maxDepth = 40))
    }

    /**
     * Обновившемуся (выбор стиля уже пройден) один раз предлагается Hairline New — отдельным окном.
     * Это ожидаемо; закрываем «остаться на старом», чтобы дальше проверять саму оболочку.
     */
    private fun dismissIslandOffer() {
        val stay = compose.onAllNodes(androidx.compose.ui.test.hasTestTag("island-offer-stay"))
        // Окно появляется после чтения настроек с диска — ждём его недолго; не появилось — и не надо.
        runCatching { compose.waitUntil(2_000) { stay.fetchSemanticsNodes().isNotEmpty() } }
        if (stay.fetchSemanticsNodes().isNotEmpty()) {
            stay[0].performClick()
            compose.waitForIdle()
        }
    }
}
