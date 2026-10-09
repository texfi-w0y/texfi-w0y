package com.texfi.w0y.desktop

import com.texfi.w0y.desktop.i18n.Strings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {
    private val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    /** Ключи, которые код просит строкой: S("…") и ключи в перечислениях настроек. */
    private fun usedKeys(): Set<String> {
        val keys = HashSet<String>()
        val call = Regex("""\bS\(\s*"([a-z][a-z0-9_]*)"""")
        val enumKey = Regex("""[A-Z_]+\("([a-z][a-z0-9_]*)"(?:,\s*"([a-z][a-z0-9_]*)")?""")
        sources.forEach { f ->
            val text = f.readText()
            call.findAll(text).forEach { keys += it.groupValues[1] }
            if (f.name == "Settings.kt" || f.name == "SoundProfile.kt" || f.name == "Keys.kt") {
                enumKey.findAll(text).forEach { m ->
                    // У действий клавиш первый аргумент — id, а не ключ строки.
                    val candidates = if (f.name == "Keys.kt") listOf(m.groupValues[2]) else m.groupValues.drop(1)
                    candidates.filter { it.isNotEmpty() && it.contains('_') }.forEach { keys += it }
                }
            }
        }
        // «${key}_sum» в списке разделов настроек.
        keys += listOf("sound", "tone", "player", "speed", "downloads", "search", "account", "clean", "look", "devices", "data").map { "settings_section_${it}_sum" }
        keys += "d_section_keys_sum"
        return keys
    }

    @Test
    fun everyUsedKeyExistsInAllLanguages() {
        val missing = ArrayList<String>()
        for (lang in Strings.supported) {
            val table = Strings.load(lang)
            usedKeys().filter { it !in table }.forEach { missing += "$lang:$it" }
        }
        assertTrue(missing.isEmpty(), "нет строк: " + missing.sorted().joinToString())
    }

    @Test
    fun desktopStringsAreTranslatedEverywhere() {
        fun keys(lang: String) = Strings::class.java.getResourceAsStream("/strings/desktop-$lang.xml")!!.use { Strings.parse(it).keys }
        val en = keys("en")
        for (lang in listOf("ru", "uk", "pl")) assertEquals(en, keys(lang), "desktop-$lang.xml расходится с desktop-en.xml")
    }

    @Test
    fun placeholdersMatchBetweenLanguages() {
        val re = Regex("""%\d\$[sd]""")
        val en = Strings.load("en")
        val bad = ArrayList<String>()
        for (lang in listOf("ru", "uk", "pl")) {
            val t = Strings.load(lang)
            en.forEach { (k, v) ->
                val other = t[k] ?: return@forEach
                if (re.findAll(v).map { it.value }.toSortedSet() != re.findAll(other).map { it.value }.toSortedSet()) bad += "$lang:$k"
            }
        }
        assertTrue(bad.isEmpty(), "разные подстановки: " + bad.joinToString())
    }

    @Test
    fun unescapeFollowsAapt() {
        assertEquals("it's", Strings.unescape("it\\'s"))
        assertEquals("a\nb", Strings.unescape("a\\nb"))
        assertEquals("a b", Strings.unescape("  a    b  "))
        assertEquals("  x ", Strings.unescape("\"  x \""))
        assertEquals("é", Strings.unescape("\\u00e9"))
    }
}
