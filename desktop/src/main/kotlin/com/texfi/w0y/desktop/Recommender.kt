package com.texfi.w0y.desktop

import com.texfi.w0y.data.SongItem
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Умные рекомендации: что включить рядом с этим треком.
 *
 * Основа — радиостанции YouTube: жанр и звучание у них уже учтены, и выдумывать
 * свою «похожесть» поверх чужого каталога было бы самообманом. Но порядок
 * подбирается под слушателя:
 *  - семена не один трек, а текущий плюс один из недавних лайков — подборка
 *    не залипает на одной песне;
 *  - вперёд идут исполнители, которых ты слушаешь чаще и которых лайкнул,
 *    и всё, что похоже на недавние поисковые слова;
 *  - только что игранное уходит в конец — рекомендация не должна повторять вчерашнее;
 *  - один исполнитель не идёт больше двух раз подряд.
 */
class Recommender(private val app: AppState, private val yt: Yt) {
    suspend fun forSeed(seed: SongItem, exclude: Set<String> = emptySet(), limit: Int = 30): List<SongItem> = coroutineScope {
        val likedSeed = app.lib.liked.filter { it.id != seed.id }.take(15).shuffled().firstOrNull()?.toItem()
        val pools =
            listOfNotNull(seed, likedSeed).map { s -> async { runCatching { yt.radio(s.id) }.getOrDefault(emptyList()) } }.map { it.await() }
        // Радио текущего трека — главное: его кандидаты чередуются с кандидатами второго семени.
        val merged = LinkedHashMap<String, SongItem>()
        val longest = pools.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) pools.forEach { pool -> pool.getOrNull(i)?.let { merged.putIfAbsent(it.id, it) } }
        val excluded = app.st.tasteExcluded
        val candidates = app.visible(merged.values.filter { it.id !in exclude && it.id != seed.id && norm(it.artist) !in excluded })
        if (candidates.isEmpty()) return@coroutineScope emptyList()

        // «Учитывать мой вкус» выключено — подборка без данных о слушателе; «знакомое ↔ новое» двигает вес известных исполнителей.
        val taste = app.st.tasteLearning
        val plays = if (taste) app.lib.artistPlays else emptyMap()
        val likedArtists = if (taste) app.lib.liked.map { norm(it.artist) }.toSet() else emptySet()
        val novelty = app.st.recNovelty.coerceIn(0, 100)
        val recent = app.lib.history.take(30).map { it.id }.toSet()
        val words =
            app.lib.searchHistory.flatMap { it.lowercase(Locale.ROOT).split(' ') }.filter { it.length >= 3 }.toSet()
        val seedArtist = norm(seed.artist)

        val ranked =
            candidates.sortedByDescending { c ->
                val artist = norm(c.artist)
                var score = 0
                score += (3 * (plays[artist] ?: 0).coerceAtMost(5) * (100 - novelty)) / 50
                if (taste && artist.isNotBlank() && artist !in plays && artist !in likedArtists) score += novelty / 10
                if (artist.isNotBlank() && artist == seedArtist) score += 4
                if (artist in likedArtists) score += 3
                if (words.any { it in "$artist ${c.title.lowercase(Locale.ROOT)}" }) score += 6
                if (c.id in recent) score -= 8
                score
            }
        diversify(ranked).take(limit)
    }

    private fun norm(artist: String) = artist.substringBefore(',').trim().lowercase(Locale.ROOT)

    /** Не больше двух подряд от одного исполнителя: лишние откладываются дальше по списку. */
    private fun diversify(list: List<SongItem>): List<SongItem> {
        val rest = list.toMutableList()
        val out = ArrayList<SongItem>(list.size)
        while (rest.isNotEmpty()) {
            val lastTwo = out.takeLast(2).map { norm(it.artist) }
            val pick = rest.indexOfFirst { !(lastTwo.size == 2 && lastTwo.all { a -> a == norm(it.artist) }) }.takeIf { it >= 0 } ?: 0
            out += rest.removeAt(pick)
        }
        return out
    }
}
