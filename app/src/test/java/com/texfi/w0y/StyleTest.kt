package com.texfi.w0y

import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.icons.SmoothIcons
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.resolveColors
import com.texfi.w0y.ui.theme.tokensFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Слой стиля: токены, иконки, контраст всех сочетаний, сохранение выбора. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StyleTest {
    @Test
    fun everyStyleHasTokens() {
        // Hairline New рисуется компонентами Hairline (и берёт все его настройки), отличие — флаг острова.
        UiStyle.entries.forEach { style ->
            val tokens = tokensFor(style)
            assertEquals(if (style == UiStyle.ISLAND) UiStyle.HAIRLINE else style, tokens.style)
            assertEquals(style == UiStyle.ISLAND, tokens.island)
        }
        val pixel = tokensFor(UiStyle.PIXEL)
        val smooth = tokensFor(UiStyle.SMOOTH)
        assertTrue(pixel.stepped && pixel.segmented && pixel.cardShadow.value > 0)
        assertTrue(!smooth.stepped && !smooth.segmented && smooth.cardShadow.value == 0f)
        assertNotEquals(pixel.card, smooth.card)
    }

    @Test
    fun everySpriteHasSmoothIconExceptBrandMarks() {
        val sprites =
            Sprites::class.java.declaredFields
                .filter { List::class.java.isAssignableFrom(it.type) }
                .associate { field ->
                    field.isAccessible = true
                    field.name to (field.get(Sprites) as List<*>)
                }
        assertTrue("спрайты не нашлись", sprites.size > 20)
        sprites.forEach { (name, rows) ->
            @Suppress("UNCHECKED_CAST")
            val icon = SmoothIcons.forSprite(rows as List<String>)
            if (name.startsWith("mark")) {
                assertNull("$name — фирменный знак, остаётся пиксельным", icon)
            } else {
                assertNotNull("у спрайта $name нет плавной пары", icon)
            }
        }
    }

    @Test
    fun allThemeAccentCombinationsAreReadable() {
        val customs = listOf(0xFFFFFF00, 0xFF000080, 0xFF808080, 0xFFFFFFFF, 0xFF000000, 0xFF00FF7F).map { it.toInt() }
        ThemeMode.entries.forEach { mode ->
            val combos = Accent.entries.filter { it != Accent.CUSTOM }.map { it to 0 } + customs.map { Accent.CUSTOM to it }
            combos.forEach { (accent, custom) ->
                val c = resolveColors(mode, accent, custom)
                val ground = if (c.glass) Color(0xFF1A2036) else c.background
                val surface = if (c.glass) Color(0xFF1A2036) else c.surface
                val tag = "$mode/$accent/${Integer.toHexString(custom)}"
                assertTrue("текст $tag", Contrast.ratio(c.text, ground) >= Contrast.TEXT)
                assertTrue("вторичный текст $tag", Contrast.ratio(c.textMuted, surface) >= Contrast.TEXT - 0.01f)
                assertTrue("подпись на акценте $tag", Contrast.ratio(c.onAccent, c.accent) >= Contrast.TEXT - 0.2f)
                assertTrue("текст акцентом $tag", Contrast.ratio(c.accentText, ground) >= Contrast.TEXT - 0.01f)
            }
        }
    }

    @Test
    fun brandBlueStaysWhenReadable() {
        val dark = resolveColors(ThemeMode.DARK, Accent.BLUE, 0)
        assertEquals(Color(0xFF4A7CFB), dark.accent)
        assertEquals(dark.accent, dark.accentText)
    }

    @Test
    fun styleAndThemePersist() = runBlocking {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext())
        repo.setUiStyle(UiStyle.SMOOTH)
        repo.setTheme(ThemeMode.LIGHT)
        repo.setStylePicked(true)
        val read = SettingsRepository(ApplicationProvider.getApplicationContext()).settings.first()
        assertEquals(UiStyle.SMOOTH, read.uiStyle)
        assertEquals(ThemeMode.LIGHT, read.theme)
        assertTrue(read.stylePicked)
        repo.setUiStyle(UiStyle.PIXEL)
        assertEquals(UiStyle.PIXEL, repo.settings.first().uiStyle)
    }
}
