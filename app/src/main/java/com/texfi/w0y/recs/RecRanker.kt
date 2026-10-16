package com.texfi.w0y.recs

import com.texfi.w0y.data.SongItem
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Отбор подборки. Логика без Android и сети — чтобы проверяться тестами.
 *
 * Берёт кандидатов из источников, выкидывает уже показанное и недавно
 * игравшее, исполнителей из «не предлагать» и дубли, затем собирает набор
 * с заданной долей нового. Случайная добавка к оценке — своя на каждый
 * запрос: два запроса подряд не дают один и тот же порядок, а порядок
 * самого YouTube всё равно сохраняет вес.
 */
object RecRanker {
    private const val JITTER = 0.35
    private const val RADIO_WEIGHT = 1.0
    private const val TASTE_WEIGHT = 0.6
    private const val SAME_ARTIST_BONUS = 0.15

    fun rank(
        candidates: List<Candidate>,
        taste: TasteProfile,
        seen: Set<String>,
        recent: Set<String>,
        novelty: Double,
        random: Random,
        limit: Int,
    ): List<Rec> {
        val fresh = dedupe(candidates).filter { c ->
            c.song.id !in seen && c.song.id !in recent && !taste.isExcluded(c.song.artist)
        }
        if (fresh.isEmpty() || limit <= 0) return emptyList()
        val perSeed = fresh.groupBy { it.seedTitle }.mapValues { it.value.size }
        val scored = fresh.map { c ->
            val fam = taste.familiarity(c.song.artist)
            val position = 1.0 - c.rank.toDouble() / ((perSeed[c.seedTitle] ?: 1) + 1)
            var score = RADIO_WEIGHT * position * 0.5 + TASTE_WEIGHT * fam + random.nextDouble() * JITTER
            if (c.seedArtist != null && TasteProfile.key(c.seedArtist) == TasteProfile.key(c.song.artist)) score += SAME_ARTIST_BONUS
            Scored(c, score, isNew = c.origin == Origin.RADIO && taste.hasData && !taste.knows(c.song.artist))
        }.sortedByDescending { it.score }

        val newPool = scored.filter { it.isNew }
        val knownPool = scored.filterNot { it.isNew }
        val total = minOf(limit, scored.size)
        var wantNew = (total * novelty.coerceIn(0.0, 1.0)).roundToInt().coerceAtMost(newPool.size)
        var wantKnown = (total - wantNew).coerceAtMost(knownPool.size)
        // Одного из пулов не хватило — добираем из другого, но не режем набор.
        if (wantNew + wantKnown < total) wantNew = (total - wantKnown).coerceAtMost(newPool.size)
        if (wantNew + wantKnown < total) wantKnown = (total - wantNew).coerceAtMost(knownPool.size)

        val news = newPool.take(wantNew).iterator()
        val known = knownPool.take(wantKnown).iterator()
        val out = ArrayList<Scored>(total)
        var acc = 0.0
        val share = if (total == 0) 0.0 else wantNew.toDouble() / total
        // Новое равномерно между знакомым, а не одной пачкой в конце.
        while (news.hasNext() || known.hasNext()) {
            acc += share
            if (news.hasNext() && (acc >= 1.0 || !known.hasNext())) {
                acc -= 1.0
                out += news.next()
            } else if (known.hasNext()) {
                out += known.next()
            } else {
                out += news.next()
            }
        }
        return out.map { Rec(it.c.song, whyOf(it, taste)) }
    }

    private fun whyOf(s: Scored, taste: TasteProfile): Why =
        when {
            s.c.origin == Origin.LOCAL_FORGOTTEN -> Why.Forgotten
            s.c.origin == Origin.LOCAL_TIME -> Why.TimeOfDay
            s.isNew -> Why.NewArtist
            taste.familiarity(s.c.song.artist) >= 0.35 -> Why.YouListen(s.c.song.artist)
            else -> Why.SimilarTo(s.c.seedTitle.orEmpty())
        }

    /** Один и тот же трек приходит из нескольких радио и под разными id (клип, лирик-видео). */
    private fun dedupe(list: List<Candidate>): List<Candidate> {
        val ids = HashSet<String>()
        val names = HashSet<String>()
        return list.filter { c ->
            val name = nameKey(c.song)
            ids.add(c.song.id) && (name.isBlank() || names.add(name))
        }
    }

    internal fun nameKey(song: SongItem): String =
        (song.artist + "|" + song.title)
            .lowercase(Locale.ROOT)
            .replace(Regex("""\(.*?\)|\[.*?]"""), "")
            .replace(Regex("""[^\p{L}\p{N}|]+"""), " ")
            .trim()

    private class Scored(val c: Candidate, val score: Double, val isNew: Boolean)
}
