package com.texfi.w0y.recs

import com.texfi.w0y.data.db.ArtistPlays
import java.util.Locale
import kotlin.math.ln
import kotlin.math.min

/**
 * Вкус слушателя — только то, что видно на телефоне: сколько раз засчитаны
 * включения каждого исполнителя и чьи треки лайкнуты. Никуда не уходит и
 * целиком показывается на экране «Мой вкус».
 */
class TasteProfile(
    artistPlays: Map<String, Int>,
    val excluded: Set<String> = emptySet(),
) {
    private val plays: Map<String, Int> = artistPlays.mapKeys { key(it.key) }.filterKeys { it !in excluded }

    /** Есть ли вообще данные о вкусе: без них слова «новое» и «знакомое» ничего не значат. */
    val hasData: Boolean get() = plays.isNotEmpty()

    /** Знаком ли исполнитель: хоть одно засчитанное включение или лайк. */
    fun knows(artist: String): Boolean = (plays[key(artist)] ?: 0) > 0

    /** 0..1: логарифм числа включений, чтобы один залипший артист не глушил остальных. */
    fun familiarity(artist: String): Double {
        val n = plays[key(artist)] ?: return 0.0
        return min(1.0, ln(1.0 + n) / ln(1.0 + CAP))
    }

    fun isExcluded(artist: String): Boolean = key(artist) in excluded

    /** Топ для экрана «Мой вкус»: исполнитель и вес в процентах от лидера. */
    fun top(limit: Int = 12): List<Pair<String, Int>> {
        val lead = plays.values.maxOrNull()?.takeIf { it > 0 } ?: return emptyList()
        return plays.entries.sortedByDescending { it.value }.take(limit).map { it.key to (it.value * 100 / lead) }
    }

    companion object {
        const val CAP = 30

        fun key(artist: String): String = artist.trim().lowercase(Locale.ROOT)

        fun parseExcluded(raw: String): Set<String> = raw.lines().map(::key).filter { it.isNotBlank() }.toSet()

        fun formatExcluded(names: Collection<String>): String = names.map(::key).filter { it.isNotBlank() }.distinct().joinToString("\n")

        /** Лайк весит как два включения. */
        fun from(rows: List<ArtistPlays>, likedArtists: List<String>, excluded: Set<String> = emptySet()): TasteProfile {
            val map = HashMap<String, Int>()
            rows.forEach { map.merge(key(it.artist), it.plays) { a, b -> a + b } }
            likedArtists.forEach { map.merge(key(it), 2) { a, b -> a + b } }
            return TasteProfile(map, excluded)
        }

        val EMPTY = TasteProfile(emptyMap())
    }
}
