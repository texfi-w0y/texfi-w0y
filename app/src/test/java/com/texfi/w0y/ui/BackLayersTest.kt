package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.texfi.w0y.ui.shell.LocalPlayerOpen
import com.texfi.w0y.ui.shell.PlayerSheet
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «Назад» закрывает то, что открыто последним: плеер поверх артиста закрывается первым,
 * текст в плеере возвращает к обложке, а не закрывает плеер.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class BackLayersTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var artistOpen by mutableStateOf(false)
    private var expanded by mutableStateOf(false)
    private var lyricsOn by mutableStateOf(false)

    private fun show() {
        rule.setContent {
            W0yTheme {
                Box(Modifier.fillMaxSize()) {
                    // Как в оболочке: слой под плеером заводит обработчик, когда открывается.
                    if (artistOpen) BackHandler { artistOpen = false }
                    PlayerSheet(expanded = expanded, onCollapse = { expanded = false }) {
                        if (LocalPlayerOpen.current && lyricsOn) BackHandler { lyricsOn = false }
                        Text("player")
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun playerOverArtistClosesFirst() {
        show()
        // Плеер уже был открыт раньше: его обработчик старше, чем у артиста.
        expanded = true
        rule.waitForIdle()
        expanded = false
        rule.waitForIdle()
        artistOpen = true
        rule.waitForIdle()
        expanded = true
        rule.waitForIdle()
        back()
        assertFalse("плеер закрыт", expanded)
        assertTrue("артист на месте", artistOpen)
        back()
        assertFalse("второе «назад» — с артиста", artistOpen)
    }

    @Test
    fun backFromLyricsReturnsToCover() {
        show()
        expanded = true
        rule.waitForIdle()
        lyricsOn = true
        rule.waitForIdle()
        back()
        assertFalse("текст закрыт", lyricsOn)
        assertTrue("плеер остался", expanded)
        back()
        assertFalse(expanded)
    }
}
