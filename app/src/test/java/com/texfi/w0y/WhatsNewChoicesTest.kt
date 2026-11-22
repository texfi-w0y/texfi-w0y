package com.texfi.w0y

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.NotesText
import com.texfi.w0y.data.ReleaseChoices
import com.texfi.w0y.data.SettingsCatalog
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.W0ySettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Окно после обновления: текст релиза без разметки и выбор новинок, который правда меняет настройки. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhatsNewChoicesTest {
    @Test
    fun releaseNotesBecomePlainBlocks() {
        val file = listOf(File("docs/RELEASE_NOTES.md"), File("../docs/RELEASE_NOTES.md")).first { it.exists() }
        val blocks = NotesText.blocks(file.readText())
        assertTrue(blocks.isNotEmpty())
        blocks.forEach { b ->
            assertFalse("решётка: ${b.text}", b.text.startsWith("#"))
            assertFalse("адрес ссылки: ${b.text}", b.text.contains("](") || b.text.contains("https://"))
        }
        assertFalse("раздел установки не показываем", blocks.any { it.text.contains("SHA256SUMS") })
        // Пункт, разбитый в файле на несколько строк, — одна строка в окне. Берём первый такой
        // пункт из самого файла: описание меняется с каждой версией, слова в тест не зашиваем.
        val lines = file.readLines()
        val at = lines.indices.first { i -> lines[i].startsWith("- ") && i + 1 < lines.size && lines[i + 1].startsWith("  ") }
        val head = lines[at].removePrefix("- ").replace("**", "").take(12)
        val tail = lines[at + 1].trim()
        val item = blocks.first { it.text.replace("**", "").startsWith(head) }
        assertEquals(NotesText.Kind.BULLET, item.kind)
        assertTrue("продолжение пункта в той же строке: $tail", item.text.contains(tail))
    }

    @Test
    fun beta4ChoicesAreRealSettings() =
        runBlocking {
            val choices = ReleaseChoices.of(17)
            assertEquals(6, choices.size)
            val repo = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
            val defaults = W0ySettings()
            choices.forEach { c ->
                assertTrue("${c.id} нет в Настройках", SettingsCatalog.entry(c.id)?.key != null)
                assertTrue("${c.id} по умолчанию включено", c.get(defaults))
                c.set(repo, false)
                assertFalse("${c.id} не выключилось", c.get(repo.settings.first()))
                c.set(repo, true)
                assertTrue(c.get(repo.settings.first()))
            }
            assertTrue(ReleaseChoices.of(16).isEmpty())
        }
}
