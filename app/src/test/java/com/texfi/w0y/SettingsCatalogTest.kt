package com.texfi.w0y

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.texfi.w0y.data.SettingsCatalog
import com.texfi.w0y.data.SettingsCategory
import com.texfi.w0y.data.SettingsMigration
import com.texfi.w0y.data.W0ySettings
import com.texfi.w0y.ui.screens.SearchEntry
import com.texfi.w0y.ui.screens.SettingsSearch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    @Test
    fun everyIdLivesInExactlyOneCategory() {
        val ids = SettingsCatalog.entries.map { it.id }
        assertEquals("повторяющиеся id: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}", ids.size, ids.toSet().size)
        assertEquals(10, SettingsCategory.entries.size)
        SettingsCategory.entries.forEach { c -> assertTrue("пустая категория $c", SettingsCatalog.entries.any { it.category == c }) }
    }

    @Test
    fun defaultFavoritesExistAndFitTheLimit() {
        val favorites = SettingsCatalog.parseFavorites(SettingsCatalog.DEFAULT_FAVORITES)
        assertEquals(6, favorites.size)
        assertEquals(SettingsCatalog.DEFAULT_FAVORITES, SettingsCatalog.formatFavorites(favorites))
        assertEquals(SettingsCatalog.DEFAULT_FAVORITES, W0ySettings().settingsFavorites)
        // Больше шести не помещается, неизвестные id отбрасываются.
        assertEquals(6, SettingsCatalog.parseFavorites(SettingsCatalog.DEFAULT_FAVORITES + ",hl_set_motion,nope").size)
    }

    /** Каждая настройка из экрана присутствует в каталоге (по тексту исходника). */
    @Test
    fun screenRowsAreAllCatalogued() {
        val src = File("src/main/java/com/texfi/w0y/ui/screens/SettingsScreen.kt").readText()
        val used = Regex("""(?:id = |choice\()"([a-z_0-9]+)"""").findAll(src).map { it.groupValues[1] }.toSet()
        val missing = used.filter { SettingsCatalog.entry(it) == null }
        assertTrue("нет в каталоге: $missing", missing.isEmpty())
        val unused = SettingsCatalog.entries.map { it.id }.filterNot { it in used }
        assertTrue("есть в каталоге, нет на экране: $unused", unused.isEmpty())
    }

    /** docs/SETTINGS_MAP.md собирается из каталога; если разошлись — файл перезаписывается и тест просит закоммитить. */
    @Test
    fun settingsMapDocIsInSync() {
        val file = listOf(File("docs/SETTINGS_MAP.md"), File("../docs/SETTINGS_MAP.md")).first { it.parentFile?.exists() == true }
        val expected = SettingsCatalog.markdown()
        if (!file.exists() || file.readText() != expected) {
            file.writeText(expected)
            throw AssertionError("docs/SETTINGS_MAP.md обновлён из каталога — закоммить изменение и запусти тесты снова")
        }
    }

    // ---- поиск

    private fun entries() =
        listOf(
            SearchEntry("settings_normalize_title", "Volume normalization", "Evens out loud and quiet tracks", "normalize", "SOUND"),
            SearchEntry("settings_cache_title", "Cache limit", "How much audio to keep", "", "DOWNLOADS AND STORAGE"),
            SearchEntry("hl_set_appicon", "App icon", "Icon on the home screen", "launcher", "LOOK"),
            SearchEntry("settings_speed_title", "Speed", "Default playback speed", "slowed sped", "TEMPO"),
        )

    @Test
    fun searchUnderstandsSynonymsBothLanguages() {
        fun ids(q: String) = SettingsSearch.search(q, entries()).map { it.id }
        assertTrue("громкость" , "settings_normalize_title" in ids("громкость"))
        assertTrue("normalize", "settings_normalize_title" in ids("normalize"))
        assertTrue("кэш", "settings_cache_title" in ids("кэш"))
        assertTrue("кеш", "settings_cache_title" in ids("кеш"))
        assertTrue("скорость", "settings_speed_title" in ids("скорость"))
        assertTrue("иконка", "hl_set_appicon" in ids("иконка"))
        assertFalse("иконка не про кэш", "settings_cache_title" in ids("иконка"))
        assertTrue(ids("xyzzy").isEmpty())
    }

    @Test
    fun titleHitRanksAboveDescriptionHit() {
        val list =
            listOf(
                SearchEntry("a", "Something", "mentions speed in text", "", "X"),
                SearchEntry("b", "Speed", "", "", "X"),
            )
        assertEquals("b", SettingsSearch.search("speed", list).first().id)
    }

    // ---- хранилище настроек

    @Test
    fun migrationKeepsEveryStoredKey() {
        val prefs =
            mutablePreferencesOf(
                stringPreferencesKey("accent") to "SAND",
                stringPreferencesKey("quality_wifi") to "LOW",
                booleanPreferencesKey("normalize_volume") to false,
                stringPreferencesKey("dock_labels") to "NONE",
                booleanPreferencesKey("delete_protection") to false,
                intPreferencesKey("cache_limit_mb") to 1024,
            )
        val before = prefs.asMap().toMap()
        SettingsMigration.apply(prefs, SettingsMigration.CURRENT)
        before.forEach { (key, value) -> assertEquals("ключ ${key.name} изменён миграцией", value, prefs.asMap()[key]) }
        // Закреплённое не появляется само: у существующего пользователя избранное — умолчание из таблицы.
        assertFalse(stringPreferencesKey("settings_favorites") in prefs)
    }
}
