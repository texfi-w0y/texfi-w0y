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

    @Test
    fun launches() {
        hilt.inject()
        compose.setContent {
            val shell: ShellViewModel = hiltViewModel()
            W0yTheme { W0yShell(shell) }
        }
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasText("SKIP")).performClick()
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("yours")).performClick()
        compose.waitForIdle()
        println("SHELL>>" + compose.onRoot().printToString(maxDepth = 40))
    }
}
