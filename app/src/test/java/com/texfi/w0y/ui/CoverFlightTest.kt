package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.components.CoverFlight
import com.texfi.w0y.ui.components.CoverFlightLayer
import com.texfi.w0y.ui.components.LocalCoverFlight
import com.texfi.w0y.ui.components.coverFlightMini
import com.texfi.w0y.ui.screens.CoverPlayerPage
import com.texfi.w0y.ui.shell.PlayerSheet
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Полёт обложки: место обложки плеера восстанавливается и в покое, и посреди закрытия шторки. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CoverFlightTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var expanded by mutableStateOf(true)
    private val flight = CoverFlight()

    private fun show(poster: Boolean) {
        rule.setContent {
            W0yTheme {
                CompositionLocalProvider(LocalCoverFlight provides flight) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.BottomStart).padding(16.dp).size(44.dp).testTag("mini").coverFlightMini(flight, 44.dp))
                        PlayerSheet(expanded = expanded, onCollapse = { expanded = false }) {
                            CoverPlayerPage(
                                song = SongItem(id = "a", title = "Катюха", artist = "whitek3d"),
                                isPlaying = true, positionMs = 30_000, durationMs = 120_000,
                                stage = { Box(Modifier.testTag("stage").fillMaxSize()) },
                                header = { Box(Modifier.fillMaxWidth().height(56.dp)) },
                                titleBlock = { m -> Text("Катюха", m) },
                                actions = { Box(Modifier.fillMaxWidth().height(48.dp)) },
                                transport = { Box(Modifier.fillMaxWidth().height(88.dp)) },
                                plate = { Box(Modifier.fillMaxWidth().height(52.dp)) },
                                onCoverTap = {}, onSwipe = {}, onDrag = {}, onDragEnd = {},
                                poster = poster,
                            )
                        }
                        CoverFlightLayer(flight, "https://example.test/c.jpg")
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2000)
    }

    private fun restMatchesStage() {
        val stage = rule.onNodeWithTag("stage", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rest = flight.playerRest()
        assertNotNull(rest)
        assertEquals("левый край", stage.left, rest!!.left, 3f)
        assertEquals("верх", stage.top, rest.top, 3f)
        assertEquals("ширина", stage.width, rest.width, 3f)
        assertEquals("высота", stage.height, rest.height, 3f)
    }

    private fun check(poster: Boolean) {
        show(poster)
        assertFalse("открыт — полёта нет", flight.flying)
        val stageRest = rule.onNodeWithTag("stage", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        restMatchesStage()

        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            expanded = false
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        // Кадры идут, пока шторка не окажется в пути (в тесте стиль может быть и ступенчатым).
        var frames = 0
        while (!flight.flying && frames++ < 200) rule.mainClock.advanceTimeByFrame()
        assertTrue("шторка в пути — обложка летит (openness=${flight.openness}, кадров $frames)", flight.flying)
        val rest = flight.playerRest()!!
        assertEquals("место в покое не уплыло: левый край", stageRest.left, rest.left, 4f)
        assertEquals("место в покое не уплыло: верх", stageRest.top, rest.top, 4f)
        assertEquals("место в покое не уплыло: ширина", stageRest.width, rest.width, 4f)

        rule.mainClock.autoAdvance = true
        rule.mainClock.advanceTimeBy(3000)
        assertFalse("закрыт — полёта нет", flight.flying)
        assertEquals(0f, flight.openness, 0.001f)
    }

    @Test
    fun coverLayoutRestIsStableWhileClosing() = check(poster = false)

    @Test
    fun posterLayoutRestIsStableWhileClosing() = check(poster = true)
}
