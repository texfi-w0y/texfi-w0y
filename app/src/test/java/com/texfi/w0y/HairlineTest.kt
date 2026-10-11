package com.texfi.w0y

import androidx.compose.ui.graphics.Color
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.AppIcon
import com.texfi.w0y.data.AppIconPlan
import com.texfi.w0y.data.MotionMode
import com.texfi.w0y.data.NumberText
import com.texfi.w0y.data.SearchHistoryLogic
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.playback.DismissPort
import com.texfi.w0y.playback.MiniDismiss
import com.texfi.w0y.playback.PlayerDismisser
import com.texfi.w0y.playback.QueueSnapshot
import com.texfi.w0y.ui.components.HairMath
import com.texfi.w0y.ui.components.resolveMotion
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.MotionLevel
import com.texfi.w0y.ui.theme.resolveColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hairline: токены, жест скрытия плеера, иконка-алиас, история поиска, математика. Без Android. */
class HairlineTest {
    private val modes = listOf(ThemeMode.DARK, ThemeMode.OLED, ThemeMode.LIGHT, ThemeMode.SYSTEM)

    @Test
    fun textIsReadableOnEverySurfaceForEveryAccent() {
        val failures = mutableListOf<String>()
        for (mode in modes) for (accent in Accent.entries) {
            val c = resolveColors(mode, accent, 0xFFA06CFF.toInt(), UiStyle.HAIRLINE)
            for ((name, ground) in listOf("background" to c.background, "surface" to c.surface, "surfaceHigh" to c.surfaceHigh)) {
                for ((label, fg) in listOf("text" to c.text, "muted" to c.textMuted, "accentText" to c.accentText)) {
                    val r = Contrast.ratio(fg, ground)
                    if (r < 4.5f) failures += "$mode/$accent $label on $name = $r"
                }
            }
            val onAccent = Contrast.ratio(c.onAccent, c.accent)
            if (onAccent < 4.5f) failures += "$mode/$accent onAccent = $onAccent"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun interactiveOutlineIsVisibleOnBackgroundAndSurface() {
        val failures = mutableListOf<String>()
        for (mode in modes) {
            val c = resolveColors(mode, Accent.SAND, 0, UiStyle.HAIRLINE)
            listOf("background" to c.background, "surface" to c.surface).forEach { (name, ground) ->
                val r = Contrast.ratio(c.outline, ground)
                if (r < 1.6f) failures += "$mode outline on $name = $r"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── Жест «убрать плеер» ──

    private class FakePort(var snap: QueueSnapshot?) : DismissPort {
        val calls = mutableListOf<String>()
        var restored: QueueSnapshot? = null

        override fun snapshot() = snap

        override fun pause() { calls += "pause" }

        override fun stopAndClear() { calls += "stop"; snap = null }

        override fun restore(snapshot: QueueSnapshot, play: Boolean) { calls += "restore:$play"; restored = snapshot }
    }

    private fun song(id: String) = SongItem(id = id, title = id, artist = "a")

    @Test
    fun dragPastFortyPercentDismisses() {
        assertTrue(MiniDismiss.shouldDismiss(dragPx = 41f, heightPx = 100f, velocityPxPerSec = 0f))
        assertFalse(MiniDismiss.shouldDismiss(dragPx = 39f, heightPx = 100f, velocityPxPerSec = 0f))
        // Короткий бросок быстрым пальцем тоже считается, случайный щелчок — нет.
        assertTrue(MiniDismiss.shouldDismiss(dragPx = 15f, heightPx = 100f, velocityPxPerSec = 2_000f))
        assertFalse(MiniDismiss.shouldDismiss(dragPx = 4f, heightPx = 100f, velocityPxPerSec = 5_000f))
        // Вверх и на нулевой высоте — никогда.
        assertFalse(MiniDismiss.shouldDismiss(dragPx = -50f, heightPx = 100f, velocityPxPerSec = 9_000f))
        assertFalse(MiniDismiss.shouldDismiss(dragPx = 50f, heightPx = 0f, velocityPxPerSec = 9_000f))
    }

    @Test
    fun dismissPausesStopsAndKeepsQueueForUndo() {
        val snap = QueueSnapshot(listOf(song("a"), song("b")), index = 1, positionMs = 42_000L, wasPlaying = true)
        val port = FakePort(snap)
        val dismisser = PlayerDismisser(port)
        assertTrue(dismisser.dismiss())
        assertEquals(listOf("pause", "stop"), port.calls)
        assertEquals(snap, dismisser.pending)
        assertTrue(dismisser.undo())
        assertEquals(snap, port.restored)
        assertEquals("restore:true", port.calls.last())
        assertNull(dismisser.pending)
    }

    @Test
    fun commitForgetsTheQueueAndNothingToDismissIsANoop() {
        val port = FakePort(QueueSnapshot(listOf(song("a")), 0, 0L, false))
        val dismisser = PlayerDismisser(port)
        dismisser.dismiss()
        dismisser.commit()
        assertNull(dismisser.pending)
        assertFalse(dismisser.undo())
        assertFalse(PlayerDismisser(FakePort(null)).dismiss())
        assertEquals(4_000L, MiniDismiss.UNDO_MS)
    }

    // ── Иконка приложения ──

    @Test
    fun exactlyOneIconAliasIsEnabled() {
        AppIcon.entries.forEach { chosen ->
            val states = AppIconPlan.states(chosen)
            assertEquals(1, states.values.count { it })
            assertTrue(states.getValue(chosen))
        }
        assertEquals(AppIcon.W, AppIconPlan.current(emptySet()))
        assertEquals(AppIcon.BARS, AppIconPlan.current(setOf(AppIcon.BARS.alias)))
    }

    @Test
    fun everyIconAliasIsDeclaredInTheManifestAndOnlyDefaultIsEnabled() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        AppIcon.entries.forEach { icon ->
            val block = manifest.substringAfter("android:name=\"${icon.alias}\"", "")
            assertTrue("alias ${icon.alias} missing", block.isNotEmpty())
            val head = block.substringBefore("</activity-alias>")
            val enabled = head.substringAfter("android:enabled=\"").substringBefore("\"")
            assertEquals(icon.alias, if (icon == AppIcon.W) "true" else "false", enabled)
            assertTrue(head.contains("android:targetActivity=\".MainActivity\""))
        }
        // MainActivity сам в лаунчере не светится: иконка — только через алиасы.
        val mainBlock = manifest.substringAfter("android:name=\".MainActivity\"").substringBefore("<activity-alias")
        assertFalse(mainBlock.contains("LAUNCHER"))
    }

    // ── История поиска ──

    @Test
    fun historyDedupesCaseInsensitivelyAndRaisesRepeat() {
        val after = SearchHistoryLogic.remember(listOf("радио", "Madk1d", "рок"), "  MADK1D ")
        assertEquals(listOf("MADK1D", "радио", "рок"), after)
        assertEquals(listOf("a", "b"), SearchHistoryLogic.clean(listOf("a", " A", "b", "", "  ")))
        assertEquals(SearchHistoryLogic.LIMIT, SearchHistoryLogic.remember((1..40).map { "q$it" }, "new").size)
    }

    @Test
    fun pickingFromHistoryReplacesTheQuery() {
        // Выбор записи даёт ровно её, без хвоста недонабранного.
        assertEquals("madk1d", SearchHistoryLogic.pick("  madk1d "))
        assertEquals("", SearchHistoryLogic.pick("   "))
    }

    // ── Математика и числа ──

    @Test
    fun speedIsAlwaysTwoDecimalsWithTimesSignWhateverTheLocale() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("ru", "RU"))
            assertEquals("1.00×", NumberText.speed(1f))
            assertEquals("0.85×", NumberText.speed(0.85f))
            assertEquals("1.05", NumberText.pitch(1.05f))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test
    fun motionAutoBacksOffOnSystemRequestOrPowerSaveButExplicitWins() {
        assertEquals(MotionLevel.OFF, resolveMotion(MotionMode.AUTO, 0f, powerSave = false, lowRam = false))
        assertEquals(MotionLevel.LITE, resolveMotion(MotionMode.AUTO, 1f, powerSave = true, lowRam = false))
        assertEquals(MotionLevel.LITE, resolveMotion(MotionMode.AUTO, 1f, powerSave = false, lowRam = true))
        assertEquals(MotionLevel.FULL, resolveMotion(MotionMode.AUTO, 1f, powerSave = false, lowRam = false))
        assertEquals(MotionLevel.FULL, resolveMotion(MotionMode.FULL, 0f, powerSave = true, lowRam = true))
        assertEquals(MotionLevel.OFF, resolveMotion(MotionMode.OFF, 1f, powerSave = false, lowRam = false))
    }

    @Test
    fun ringAngleAndTimeFormat() {
        assertEquals(0f, HairMath.angleFraction(0f, -1f), 0.001f)
        assertEquals(0.25f, HairMath.angleFraction(1f, 0f), 0.001f)
        assertEquals(0.5f, HairMath.angleFraction(0f, 1f), 0.001f)
        assertEquals("1:05", HairMath.formatTime(65_000L))
        assertEquals(60_000L, HairMath.seekMarkerStepMs(11 * 60_000L))
        assertEquals(10_000L, HairMath.seekMarkerStepMs(3 * 60_000L))
        val amps = HairMath.waveAmps(7)
        assertTrue(amps.all { it in 0f..1f })
        assertNotNull(amps)
    }
}
