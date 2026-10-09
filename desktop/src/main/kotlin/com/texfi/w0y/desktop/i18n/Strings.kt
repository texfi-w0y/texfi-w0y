package com.texfi.w0y.desktop.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Строки интерфейса. Файлы те же, что у Android-приложения (их копирует
 * задача androidStrings), плюс свои строки десктопа в strings/desktop-<язык>.xml:
 * переводы пишутся один раз и работают на обоих.
 *
 * Язык — состояние Compose: смена языка в настройках перерисовывает всё,
 * что читает [S].
 */
object Strings {
    val supported = listOf("en", "ru", "uk", "pl")

    /** Язык интерфейса. Меняется только через [select]. */
    var tag by mutableStateOf("en")
        private set

    private val cache = HashMap<String, Map<String, String>>()

    /** Выбрать язык: null — как в системе, а неизвестный язык заменяется английским. */
    fun select(requested: String?) {
        val wanted = (requested ?: Locale.getDefault().language).lowercase()
        tag = if (wanted in supported) wanted else "en"
    }

    fun load(language: String): Map<String, String> =
        synchronized(cache) {
            cache.getOrPut(language) {
                read("/strings/$language/strings.xml") + read("/strings/desktop-$language.xml")
            }
        }

    fun has(key: String): Boolean = key in load(tag) || key in load("en")

    fun get(key: String): String = load(tag)[key] ?: load("en")[key] ?: key.also { System.err.println("w0y: нет строки «$key»") }

    private fun read(path: String): Map<String, String> {
        val stream = Strings::class.java.getResourceAsStream(path) ?: return emptyMap()
        return stream.use { parse(it) }
    }

    /** Разбор файла в формате Android `<resources><string name="…">…</string></resources>`. */
    fun parse(input: java.io.InputStream): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        val nodes = doc.getElementsByTagName("string")
        val out = HashMap<String, String>(nodes.length * 2)
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            if (element.getAttribute("translatable") == "false") continue
            out[element.getAttribute("name")] = unescape(element.textContent)
        }
        return out
    }

    /**
     * Правила aapt: пробелы схлопываются, края обрезаются, кавычки по краям
     * сохраняют пробелы, а `\n`, `\'`, `\"`, `\@`, `\?`, `\\`, `\uXXXX` раскрываются.
     */
    fun unescape(raw: String): String {
        val text = raw.trim()
        val quoted = text.length >= 2 && text.first() == '"' && text.last() == '"'
        val body = if (quoted) text.substring(1, text.length - 1) else text.replace(Regex("\\s+"), " ")
        val out = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                val next = body[i + 1]
                when (next) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'u' -> {
                        val hex = body.drop(i + 2).take(4)
                        if (hex.length == 4 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                            out.append(hex.toInt(16).toChar())
                            i += 4
                        } else {
                            out.append('u')
                        }
                    }
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

/**
 * Строка по ключу. С аргументами подставляет их по правилам Android
 * (`%1$s`, `%1$d`); без аргументов отдаёт текст как есть — так же, как
 * `getString` на телефоне.
 */
fun S(key: String, vararg args: Any?): String {
    val text = Strings.get(key)
    return if (args.isEmpty()) text else runCatching { String.format(Locale.forLanguageTag(Strings.tag), text, *args) }.getOrDefault(text)
}
