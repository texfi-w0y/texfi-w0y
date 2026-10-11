package com.texfi.w0y.data

/**
 * Чистая логика истории поиска — без Android, чтобы проверять тестом.
 *
 * Два правила: повтор поднимается наверх, а не задваивается (без учёта
 * регистра и пробелов по краям), и выбор записи из истории **заменяет**
 * строку запроса целиком, а не дописывается к набранному.
 */
object SearchHistoryLogic {
    const val LIMIT = 20

    /** Запись в начало списка; прежние совпадения и лишнее за пределом уходят. */
    fun remember(current: List<String>, query: String, limit: Int = LIMIT): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return clean(current, limit)
        return clean(listOf(trimmed) + current, limit)
    }

    /** Без пустых и повторов, порядок сохранён: первый из дублей остаётся. */
    fun clean(entries: List<String>, limit: Int = LIMIT): List<String> {
        val seen = HashSet<String>()
        return entries
            .map { it.trim() }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
            .take(limit)
    }

    /** Новая строка запроса после выбора записи: ровно она, без хвоста прежнего ввода. */
    fun pick(entry: String): String = entry.trim()
}
