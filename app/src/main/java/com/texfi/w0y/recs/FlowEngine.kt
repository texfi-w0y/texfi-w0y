package com.texfi.w0y.recs

import com.texfi.w0y.data.LocalMusic
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.db.W0yDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Настроение fl0w. [ru] и [en] — как его ищут словами: `%s` — жанр, [ruAlone]/[enAlone] — запрос,
 * когда жанр не выбран. Слова подобраны под выдачу YouTube, а не под грамматику.
 */
enum class FlowMood(val ru: String, val ruAlone: String, val en: String, val enAlone: String) {
    CALM("спокойный %s", "спокойная музыка", "chill %s", "chill music"),
    SAD("грустный %s", "грустные песни", "sad %s", "sad songs"),
    DRIVE("энергичный %s", "энергичная музыка", "upbeat %s", "upbeat music"),
    ANGRY("агрессивный %s", "агрессивная музыка", "aggressive %s", "aggressive music"),
    LOVE("%s про любовь", "песни про любовь", "%s love songs", "love songs"),
    FOCUS("%s для концентрации", "музыка для концентрации", "%s for focus", "focus music"),
    NIGHT("ночной %s", "музыка для ночной дороги", "late night %s", "late night drive music"),
    PARTY("%s для тусовки", "музыка для тусовки", "%s party", "party music"),
}

/** Жанр fl0w: слово для запроса на двух языках. */
enum class FlowGenre(val ru: String, val en: String) {
    RAP("рэп", "rap"),
    POP("поп", "pop"),
    ROCK("рок", "rock"),
    ELECTRONIC("электроника", "electronic"),
    PHONK("фонк", "phonk"),
    INDIE("инди", "indie"),
    RNB("r&b", "r&b"),
    LOFI("lo-fi", "lo-fi"),
    METAL("метал", "metal"),
    JAZZ("джаз", "jazz"),
}

/** Что выбрано: настроение (может не быть), жанры, доля нового 0–100. */
data class FlowChoice(
    val mood: FlowMood? = null,
    val genres: Set<FlowGenre> = emptySet(),
    val novelty: Int = 35,
) {
    fun encodeGenres(): String = genres.joinToString(",") { it.name }

    companion object {
        fun decode(mood: String, genres: String, novelty: Int) =
            FlowChoice(
                mood = FlowMood.entries.firstOrNull { it.name == mood },
                genres = genres.split(',').mapNotNull { g -> FlowGenre.entries.firstOrNull { it.name == g } }.toSet(),
                novelty = novelty.coerceIn(0, 100),
            )
    }
}

/** Готовая подборка fl0w. [offline] — сеть не ответила, подборка только из своего. */
data class FlowSet(val items: List<Rec>, val offline: Boolean = false)

/**
 * fl0w — «не знаю, что включить». Человек говорит, как он сейчас, движок собирает очередь.
 *
 * Своих настроений и жанров YouTube Music клиенту не отдаёт, поэтому настроение ищется словами
 * (до двух запросов: по жанру и настроению), от лучших находок берётся радио — оно держит тот же
 * звук. Свои треки входят через исполнителей: если исполнитель из лайков нашёлся в этом настроении,
 * его лайки тоже в нём. Дальше общий [RecRanker]: вкус, доля нового, разный порядок каждый раз.
 * Не больше пяти запросов к сети на одну подборку ([networkCalls] — для проверки в тестах).
 */
@Singleton
class FlowEngine @Inject constructor(
    private val source: RecSource,
    private val dao: W0yDao,
    private val recs: RecEngine,
) {
    internal var random: () -> Random = { Random(System.nanoTime()) }

    @Volatile
    internal var networkCalls = 0

    suspend fun build(choice: FlowChoice, russian: Boolean, limit: Int = LIMIT): FlowSet {
        val rnd = random()
        val taste = recs.tasteProfile()
        val liked = dao.likedSongs().first().map { it.toItem() }.filterNot { LocalMusic.isLocal(it.id) || taste.isExcluded(it.artist) }
        val recent = dao.recentPlays(RECENT).map { it.id }.toSet()
        // «Удиви меня» без настроения — настроение выбирается само, иначе искать нечего.
        val mood = choice.mood ?: if (choice.novelty >= SURPRISE_AT || liked.isEmpty()) FlowMood.entries.random(rnd) else null

        val candidates = ArrayList<Candidate>()
        var failed = false
        var calls = 0
        suspend fun <T> net(block: suspend () -> T): T? {
            if (calls >= MAX_CALLS) return null
            calls++
            return fetch(block).also { if (it == null) failed = true }
        }
        val vibeArtists = HashSet<String>()
        for (query in queries(mood, choice.genres, russian, rnd)) {
            val found = net { source.search(query) } ?: continue
            found.forEachIndexed { i, song ->
                candidates += Candidate(song, Origin.RADIO, i, query, null)
                vibeArtists += TasteProfile.key(song.artist)
            }
        }
        // Радио от лучших находок разных исполнителей: поиск даёт настроение, радио — его продолжение.
        val seeds = candidates.distinctBy { TasteProfile.key(it.song.artist) }.sortedByDescending { taste.familiarity(it.song.artist) }.take(RADIO_SEEDS)
        for (seed in seeds) {
            val songs = net { source.radio(seed.song.id) } ?: continue
            songs.forEachIndexed { i, song ->
                if (!LocalMusic.isLocal(song.id)) candidates += Candidate(song, Origin.RADIO, i, seed.song.title, seed.song.artist)
                vibeArtists += TasteProfile.key(song.artist)
            }
        }
        // Свои: лайки исполнителей, которые нашлись в этом настроении. Без настроения — все лайки.
        // Сеть не ответила — настроение не узнать, играет своё целиком: лучше так, чем тишина.
        val mine = if ((mood == null && choice.genres.isEmpty()) || (failed && vibeArtists.isEmpty())) liked else liked.filter { TasteProfile.key(it.artist) in vibeArtists }
        mine.shuffled(java.util.Random(rnd.nextLong())).take(limit).forEachIndexed { i, song ->
            candidates += Candidate(song, Origin.LIBRARY, i, null, song.artist)
        }
        if (mine.isEmpty() && !failed && choice.novelty < OWN_RADIO_BELOW && liked.isNotEmpty()) {
            // Своего в этом настроении не нашлось, а просили своё — радио от самого слушаемого лайка.
            val anchor = liked.maxBy { taste.familiarity(it.artist) }
            net { source.radio(anchor.id) }?.forEachIndexed { i, song ->
                if (!LocalMusic.isLocal(song.id)) candidates += Candidate(song, Origin.RADIO, i, anchor.title, anchor.artist)
            }
        }
        val ranked = RecRanker.rank(candidates, taste, emptySet(), recent, choice.novelty / 100.0, rnd, limit)
        return FlowSet(ranked, offline = failed && ranked.all { it.why is Why.YouListen && it.song.id in likedIds(liked) })
    }

    /**
     * Обложки для вступления: свои плейлисты и лайки; если своего нет — треки по случайному
     * настроению, чтобы первое знакомство не было пустым.
     */
    suspend fun introCovers(russian: Boolean, count: Int = INTRO_COVERS): List<String> {
        val rnd = random()
        val own =
            (dao.playlists().first().mapNotNull { it.coverUrl } + dao.likedSongs().first().mapNotNull { it.thumbnailUrl })
                .distinct()
                .shuffled(java.util.Random(rnd.nextLong()))
        if (own.size >= count / 2) return own.take(count)
        val mood = FlowMood.entries.random(rnd)
        val found = fetch { source.search(if (russian) mood.ruAlone else mood.enAlone) }.orEmpty().mapNotNull { it.thumbnailUrl }
        return (own + found).distinct().take(count)
    }

    private fun queries(mood: FlowMood?, genres: Set<FlowGenre>, russian: Boolean, rnd: Random): List<String> {
        val picked = genres.shuffled(rnd).take(MAX_QUERIES)
        return when {
            mood == null && picked.isEmpty() -> emptyList()
            mood == null -> picked.map { if (russian) it.ru else it.en }
            picked.isEmpty() -> listOf(if (russian) mood.ruAlone else mood.enAlone)
            else -> picked.map { g -> (if (russian) mood.ru else mood.en).format(if (russian) g.ru else g.en) }
        }
    }

    private fun likedIds(liked: List<SongItem>): Set<String> = liked.mapTo(HashSet()) { it.id }

    /** null — сети нет или ответ упал. */
    private suspend fun <T> fetch(block: suspend () -> T): T? {
        networkCalls++
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val LIMIT = 40
        private const val RECENT = 10
        private const val RADIO_SEEDS = 2
        private const val MAX_QUERIES = 2
        /** Два поиска, два радио и одно своё — больше на подборку не нужно. */
        private const val MAX_CALLS = 5
        private const val SURPRISE_AT = 80
        private const val OWN_RADIO_BELOW = 50
        private const val INTRO_COVERS = 18
    }
}
