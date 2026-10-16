package com.texfi.w0y.ui.screens

import java.util.Locale

/** Что известно о строке для поиска: уже подписанное на языке экрана. */
class SearchEntry(
    val id: String,
    val title: String,
    val description: String,
    val keywords: String,
    val categoryTitle: String,
)

/**
 * Поиск по настройкам: название, пояснение, ключевые слова и синонимы на русском
 * и английском. Чистая логика без Android — проверяется тестом.
 */
object SettingsSearch {
    /** Группы слов-синонимов. Запрос подходит, если он входит в любое слово группы (или слово в него). */
    internal val synonyms: List<List<String>> =
        listOf(
            listOf("громкость", "volume", "normalize", "нормализация", "выравнивание", "loudness"),
            listOf("кэш", "кеш", "cache", "память", "storage", "memory", "место"),
            listOf("скорость", "speed", "tempo", "темп", "slowed", "ускорение", "замедление"),
            listOf("тон", "высота", "pitch", "тональность"),
            listOf("иконка", "icon", "значок", "launcher", "лаунчер"),
            listOf("тема", "theme", "тёмная", "темная", "светлая", "dark", "light"),
            listOf("акцент", "цвет", "colour", "color", "accent"),
            listOf("загрузки", "скачать", "скачивание", "download", "офлайн", "offline", "загрузка"),
            listOf("сон", "таймер", "sleep", "timer", "засыпание", "выключить"),
            listOf("качество", "quality", "bitrate", "битрейт"),
            listOf("текст", "lyrics", "слова", "караоке", "субтитры"),
            listOf("визуализатор", "visualizer", "спектр", "волна"),
            listOf("эквалайзер", "equalizer", "eq", "эквалайзер"),
            listOf("подставка", "stand", "ночник", "док"),
            listOf("панель", "dock", "bar", "остров", "island", "подписи", "labels", "навигация"),
            listOf("удаление", "удалить", "delete", "защита", "protection", "корзина", "случайно"),
            listOf("история", "history"),
            listOf("аккаунт", "account", "вход", "login", "войти", "google"),
            listOf("синхронизация", "sync", "зеркало"),
            listOf("поиск", "search", "подсказки", "suggestions"),
            listOf("рекомендации", "recommend", "подборки", "taste", "вкус", "discover", "открытия", "похожее"),
            listOf("мат", "explicit", "clean", "цензура", "ненормативная"),
            listOf("анимации", "motion", "animation", "reduce", "движение"),
            listOf("вибрация", "haptic", "haptics", "vibration", "вибро", "отклик"),
            listOf("язык", "language", "locale", "перевод"),
            listOf("резерв", "backup", "экспорт", "импорт", "export", "import", "копия"),
            listOf("диагностика", "debug", "отладка", "connection", "соединение", "сеть", "ошибка"),
            listOf("сброс", "reset", "стереть", "factory"),
            listOf("виджет", "widget"),
            listOf("скругления", "corners", "radius", "углы"),
            listOf("толщина", "weight", "линии", "line", "hairline"),
            listOf("очередь", "queue", "дальше", "next"),
            listOf("наушники", "headphones", "bluetooth", "блютуз"),
        )

    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace('ё', 'е')

    /** Слова, которыми можно заменить [token], включая его самого. */
    internal fun expand(token: String): Set<String> {
        val t = norm(token)
        val out = linkedSetOf(t)
        if (t.length >= 3) {
            synonyms.forEach { group ->
                val g = group.map(::norm)
                if (g.any { w -> w.contains(t) || (w.length >= 4 && t.contains(w)) }) out += g
            }
        }
        return out
    }

    /** Подходящие записи; название выше пояснения, пояснение выше синонимов. */
    fun search(query: String, entries: List<SearchEntry>): List<SearchEntry> {
        val tokens = norm(query).split(' ', '\t').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val expanded = tokens.map(::expand)
        val scored =
            entries.mapNotNull { e ->
                val title = norm(e.title)
                val rest = norm("${e.description} ${e.keywords} ${e.categoryTitle}")
                var score = 0
                for (options in expanded) {
                    val inTitle = options.any { it in title }
                    val inRest = options.any { it in rest }
                    if (!inTitle && !inRest) return@mapNotNull null
                    score += if (inTitle) 2 else 1
                }
                e to score
            }
        return scored.sortedByDescending { it.second }.map { it.first }
    }
}
