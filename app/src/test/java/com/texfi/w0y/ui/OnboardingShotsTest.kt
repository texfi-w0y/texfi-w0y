package com.texfi.w0y.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.texfi.w0y.HiltTestActivity
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.ui.shell.GestureTour
import com.texfi.w0y.ui.shell.Onboarding
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.W0yTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Кадры первого запуска и знакомства с жестами — посмотреть глазами без телефона (build/onboarding-shots). */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "ru-w411dp-h891dp-xxhdpi")
class OnboardingShotsTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<HiltTestActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Before
    fun inject() {
        hilt.inject()
    }

    private fun frames(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        Thread.sleep(200)
        compose.mainClock.advanceTimeBy(32)
    }

    private fun shot(name: String) {
        val dir = File("build/onboarding-shots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(text: String) {
        compose.onNode(hasText(text, ignoreCase = true)).performClick()
        frames(700)
    }

    @Test
    fun shots() {
        compose.setContent {
            W0yTheme(style = UiStyle.ISLAND) {
                var done by remember { mutableStateOf(false) }
                Box(Modifier.background(LocalW0yColors.current.background)) {
                    if (!done) Onboarding(signedIn = false, accountName = null, onSignIn = {}, onDone = { done = true }) else GestureTour(onDone = {})
                }
            }
        }
        compose.mainClock.autoAdvance = false
        frames(2200)
        shot("1-hello")
        tap("Начать")
        shot("2-account")
        tap("Без входа")
        shot("3-color")
        tap("Дальше")
        tap("Переливы")
        frames(600)
        shot("4-backdrop")
        tap("Готово")
        frames(800)
        shot("5-tour-intro")
        tap("Показать")
        frames(1000)
        shot("6-tour-mini-up")
        compose.onRoot().performTouchInput {
            down(Offset(centerX, centerY + 80f))
            repeat(12) { moveBy(Offset(0f, -40f)) }
            up()
        }
        frames(1400)
        frames(800)
        shot("7-tour-mini-side")
        tap("Дальше")
        frames(1300)
        shot("8-tour-hold")
        tap("Дальше")
        frames(1300)
        shot("9-tour-flow")
    }
}
