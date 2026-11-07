package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.texfi.w0y.data.ReleaseChoices
import com.texfi.w0y.data.UpdateState
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.data.WhatsNew
import com.texfi.w0y.ui.shell.UpdateDialogs
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Что нового» → «Продолжить» → переключатели новинок; выключенное уходит в настройки, «Готово» закрывает. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WhatsNewDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun continueLeadsToChoicesAndTogglesSettings() {
        val picked = mutableListOf<Pair<String, Boolean>>()
        var closed = 0
        rule.setContent {
            W0yTheme {
                UpdateDialogs(
                    state = UpdateState.Idle,
                    deferredTag = null,
                    whatsNew = WhatsNew("v0.0.4 beta-4", "## Fixes\n\n- **Reverb** works."),
                    onDownload = {},
                    onInstall = {},
                    onDefer = {},
                    onCloseWhatsNew = { closed++ },
                    choices = ReleaseChoices.of(17),
                    prefs = W0ySettings(),
                    onChoice = { c, on -> picked += c.id to on },
                )
            }
        }
        rule.onNodeWithTag("whatsnew-continue").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("choice-set_intro").performClick()
        rule.waitForIdle()
        assertEquals(listOf("set_intro" to false), picked)
        rule.onNodeWithTag("whatsnew-ok").performClick()
        assertEquals(1, closed)
    }
}
