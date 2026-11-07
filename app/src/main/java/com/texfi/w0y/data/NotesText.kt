package com.texfi.w0y.data

/**
 * Описание релиза с GitHub — это Markdown. В окне «Что нового» его показываем текстом:
 * подзаголовки, пункты и абзацы, без решёток, звёздочек и адресов ссылок. Раздел
 * установки («## Install») в приложении не нужен — на нём текст кончается.
 */
object NotesText {
    enum class Kind { HEADING, BULLET, PARAGRAPH }

    /** Блок текста; в [text] жирное ещё отмечено `**`, его выделяет экран. */
    data class Block(val kind: Kind, val text: String)

    fun blocks(markdown: String): List<Block> {
        val out = mutableListOf<Block>()
        var kind: Kind? = null
        val buffer = StringBuilder()
        fun flush() {
            val text = buffer.toString().trim()
            if (kind != null && text.isNotEmpty()) out += Block(kind!!, inline(text))
            kind = null
            buffer.clear()
        }
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("## ") && trimmed.drop(3).trim().lowercase() in STOP -> break
                trimmed.startsWith("# ") -> flush()
                trimmed.startsWith("## ") || trimmed.startsWith("### ") -> {
                    flush()
                    out += Block(Kind.HEADING, inline(trimmed.trimStart('#').trim()))
                }
                trimmed.isEmpty() -> flush()
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flush()
                    kind = Kind.BULLET
                    buffer.append(trimmed.drop(2))
                }
                else -> {
                    // Продолжение пункта (строка с отступом) или абзаца — та же мысль.
                    if (kind == null) kind = Kind.PARAGRAPH
                    if (buffer.isNotEmpty()) buffer.append(' ')
                    buffer.append(trimmed)
                }
            }
        }
        flush()
        return out
    }

    /** Ссылки — только текст, код — без кавычек; `**` остаётся для выделения жирным. */
    private fun inline(text: String): String =
        text
            .replace(LINK) { it.groupValues[1] }
            .replace("`", "")
            .replace("__", "**")

    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val STOP = setOf("install", "установка", "встановлення", "instalacja")
}
