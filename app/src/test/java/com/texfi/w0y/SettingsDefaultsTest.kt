package com.texfi.w0y

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.texfi.w0y.data.DockLabels
import com.texfi.w0y.data.SettingsMigration
import com.texfi.w0y.data.W0ySettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Умолчания новых установок, миграция старых и страж нижнего отступа списков. */
class SettingsDefaultsTest {
    private val normalize = booleanPreferencesKey("normalize_volume")
    private val labels = stringPreferencesKey("dock_labels")
    private val schema = intPreferencesKey("settings_schema")

    @Test
    fun freshInstallGetsNewDefaults() {
        val d = W0ySettings()
        assertFalse("выравнивание громкости выключено", d.normalizeVolume)
        assertEquals("подпись только у активной вкладки", DockLabels.ACTIVE, d.dockLabels)
        assertTrue("защита от удаления включена", d.deleteProtection)
        val prefs = mutablePreferencesOf()
        SettingsMigration.apply(prefs, SettingsMigration.CURRENT)
        // Чистая установка ничего не закрепляет: читаются новые умолчания.
        assertFalse(normalize in prefs)
        assertFalse(labels in prefs)
        assertEquals(SettingsMigration.CURRENT, prefs[schema])
    }

    @Test
    fun existingUserKeepsOldBehaviour() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("accent") to "SAND")
        SettingsMigration.apply(prefs, SettingsMigration.CURRENT)
        assertEquals(true, prefs[normalize])
        assertEquals(DockLabels.ALL.name, prefs[labels])
    }

    @Test
    fun explicitChoiceIsNeverOverwritten() {
        val prefs = mutablePreferencesOf(normalize to false, labels to DockLabels.NONE.name)
        SettingsMigration.apply(prefs, SettingsMigration.CURRENT)
        assertEquals(false, prefs[normalize])
        assertEquals(DockLabels.NONE.name, prefs[labels])
    }

    @Test
    fun migrationRunsOnce() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("accent") to "SAND")
        SettingsMigration.apply(prefs, SettingsMigration.CURRENT)
        prefs[normalize] = false // человек выключил после миграции
        SettingsMigration.apply(prefs, SettingsMigration.CURRENT)
        assertEquals(false, prefs[normalize])
    }

    // ---- страж: нижний отступ списков идёт из одного источника

    private val ui = File("src/main/java/com/texfi/w0y/ui")

    @Test
    fun noManualBottomInsetInScreens() {
        val offenders =
            File(ui, "screens").walkTopDown().filter { it.extension == "kt" }.filter {
                val t = it.readText()
                Regex("""PaddingValues\([^)]*bottom\s*=\s*[^)]*LocalBottomInset""").containsMatchIn(t) ||
                    "Spacer(Modifier.height(com.texfi.w0y.ui.components.LocalBottomInset" in t
            }.map { it.name }.toList()
        assertTrue("ручной нижний отступ в: $offenders (используй bottomContentPadding)", offenders.isEmpty())
    }

    /** Экраны под панелями: любой их список обязан брать отступ из общего источника. */
    @Test
    fun scrollScreensUseSharedBottomPadding() {
        val screens = listOf("AlbumScreen", "ArtistScreen", "SearchScreen", "HomeScreen", "LibraryScreen", "LibraryHair", "StatsScreen", "SettingsScreen", "AboutScreen", "LocalFilesScreen")
        val missing = screens.filter { name ->
            val t = File(ui, "screens/$name.kt").readText()
            "bottomContentPadding" !in t && "BottomInsetSpacer" !in t
        }
        assertTrue("без общего нижнего отступа: $missing", missing.isEmpty())
    }
}
