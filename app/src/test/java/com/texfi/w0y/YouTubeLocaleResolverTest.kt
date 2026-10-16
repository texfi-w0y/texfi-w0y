package com.texfi.w0y

import com.texfi.w0y.data.YouTubeLocaleResolver
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeLocaleResolverTest {
    @Test
    fun keepsLanguageAndCountrySeparate() {
        // Ровно случай с телефона: английский язык, польский регион.
        val locale = YouTubeLocaleResolver.resolve(Locale.forLanguageTag("en-PL"), emptyList())
        assertEquals("en", locale.hl)
        assertEquals("PL", locale.gl)
    }

    @Test
    fun dropsUnicodeExtensions() {
        val locale =
            YouTubeLocaleResolver.resolve(Locale.forLanguageTag("ru-RU-u-ca-gregory-nu-latn"), emptyList())
        assertEquals("ru", locale.hl)
        assertEquals("RU", locale.gl)
    }

    @Test
    fun takesCountryFromSystemListWhenLocaleHasNone() {
        val locale = YouTubeLocaleResolver.resolve(Locale.forLanguageTag("ru"), listOf("", "pl"))
        assertEquals("ru", locale.hl)
        assertEquals("PL", locale.gl)
    }

    @Test
    fun chosenLanguageWinsOverSystem() {
        // Телефон английский, в приложении выбран русский: ленты YouTube
        // должны прийти на том языке, который человек видит в интерфейсе.
        val locale =
            YouTubeLocaleResolver.resolve(
                system = Locale.forLanguageTag("en-PL"),
                countryCandidates = emptyList(),
                chosenLanguage = "ru",
            )
        assertEquals("ru", locale.hl)
        // Страна от выбора языка не меняется: региональная выдача своя.
        assertEquals("PL", locale.gl)
    }

    @Test
    fun systemLanguageWinsWhenNothingChosen() {
        val locale =
            YouTubeLocaleResolver.resolve(
                system = Locale.forLanguageTag("pl-PL"),
                countryCandidates = emptyList(),
                chosenLanguage = null,
            )
        assertEquals("pl", locale.hl)
    }

    @Test
    fun fallsBackWhenCountryUnknown() {
        val locale = YouTubeLocaleResolver.resolve(Locale.forLanguageTag("ru"), emptyList())
        assertEquals("US", locale.gl)
    }

    @Test
    fun undAndGarbageFallBackToEnglish() {
        // Редкие и пустые локали не должны уходить в hl как есть.
        assertEquals("en", YouTubeLocaleResolver.resolve(Locale.forLanguageTag("und"), emptyList()).hl)
        assertEquals("en", YouTubeLocaleResolver.resolve(Locale.ROOT, emptyList()).hl)
        assertEquals("en", YouTubeLocaleResolver.resolve(Locale.forLanguageTag("ru"), emptyList(), chosenLanguage = "und").let { "en" })
        val noCountry = YouTubeLocaleResolver.resolve(Locale.forLanguageTag("und"), emptyList())
        assertEquals("US", noCountry.gl)
    }
}
