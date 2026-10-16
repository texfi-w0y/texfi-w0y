package com.texfi.w0y.recs

import com.texfi.w0y.data.LocalMusic
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.db.RecSeenEntity
import com.texfi.w0y.data.db.W0yDao
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Блоки подборок на главной. Пустой блок не показывается. */
data class HomeBlocks(
    val discoveries: List<Rec> = emptyList(),
    val releases: List<PlaylistCard> = emptyList(),
    val forgotten: List<Rec> = emptyList(),
    val timeOfDay: List<Rec> = emptyList(),
) {
    val isEmpty: Boolean get() = discoveries.isEmpty() && releases.isEmpty() && forgotten.isEmpty() && timeOfDay.isEmpty()
}

/**
 * Движок подборок.
 *
 * Источник один — радио YouTube Music по опорному треку; свой слой — отбор:
 * память о показанном (14 дней или 2000 записей), вкус по локальной истории,
 * доля нового и разный порядок на каждый запрос. Сеть дёргается редко: радио
 * по треку кэшируется на 15 минут, поэтому смена трека стоит ровно один запрос,
 * а повторные обращения (вкладка «Похожее», бесконечное продолжение) идут из кэша.
 */
@Singleton
class RecEngine @Inject constructor(
    private val source: RecSource,
    private val dao: W0yDao,
    private val settings: SettingsRepository,
) {
    internal var clock: () -> Long = System::currentTimeMillis
    internal var random: () -> Random = { Random(System.nanoTime()) }

    /** Сколько обращений к сети сделано — для проверки лимита в тестах и в отладке. */
    @Volatile
    internal var networkCalls = 0

    private class Cached(val at: Long, val songs: List<SongItem>)

    private val radioCache =
        object : LinkedHashMap<String, Cached>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > CACHE_SIZE
        }
    private val lastSets = HashMap<String, RecSet>()
    private var lastGlobal: RecSet? = null
    private var homeCache: Pair<Long, HomeBlocks>? = null
    private var releasesCache: Pair<Long, List<PlaylistCard>>? = null

    suspend fun tasteProfile(): TasteProfile {
        val s = settings.settings.first()
        val excluded = TasteProfile.parseExcluded(s.tasteExcluded)
        if (!s.tasteLearning) return TasteProfile(emptyMap(), excluded)
        val since = clock() - TASTE_WINDOW_MS
        return TasteProfile.from(dao.topArtistsSince(since, limit = 60), dao.likedArtists(), excluded)
    }

    /** Забыть, что уже показывали: следующие подборки могут повторить прежние. */
    suspend fun forgetSeen() {
        dao.clearSeen()
        synchronized(lastSets) { lastSets.clear() }
    }

    /**
     * Похожее на [current]. Автоматический вызов обходится одним запросом (или
     * ни одним, если радио в кэше); ручное обновление гарантированно даёт другой
     * набор: показанное раньше исключено, а если свежего не хватает — берутся
     * опорные треки из недавних включений.
     */
    suspend fun similar(current: SongItem, manual: Boolean, limit: Int = SIMILAR_LIMIT): RecSet {
        val s = settings.settings.first()
        val now = clock()
        val anchor = anchorOf(current)
        if (anchor == null) return fallback(current.id)
        val taste = tasteProfile()
        val seen = dao.seenSince(now - SEEN_AGE_MS).toSet()
        val recent = recentIds(current)

        val candidates = ArrayList<Candidate>()
        var failed = false
        suspend fun addSeed(seed: SongItem, title: String, artist: String) {
            val songs = radioOf(seed.id) ?: run { failed = true; return }
            songs.forEachIndexed { i, song ->
                if (!LocalMusic.isLocal(song.id)) candidates += Candidate(song, Origin.RADIO, i, title, artist)
            }
        }
        addSeed(anchor, current.title, current.artist)

        fun pick() =
            RecRanker.rank(candidates, taste, seen, recent, s.recNovelty / 100.0, random(), limit)

        var ranked = pick()
        if (manual && ranked.size < limit / 2) {
            for (extra in recentSeeds(current, EXTRA_SEEDS)) {
                addSeed(extra, extra.title, extra.artist)
                ranked = pick()
                if (ranked.size >= limit / 2) break
            }
        }
        if (ranked.isEmpty() && failed) return fallback(current.id)
        remember(ranked.map { it.song.id }, now)
        return RecSet(ranked).also {
            synchronized(lastSets) { lastSets[current.id] = it }
            lastGlobal = it
        }
    }

    /**
     * Продолжение очереди по последним 3–5 включениям. Возвращает треки для
     * добавления; пусто — продолжать нечем (нет сети), очередь остаётся как есть.
     */
    suspend fun continuation(current: SongItem, exclude: Set<String>, limit: Int = CONTINUATION_LIMIT): List<SongItem> {
        val s = settings.settings.first()
        val now = clock()
        val taste = tasteProfile()
        val seen = dao.seenSince(now - SEEN_AGE_MS).toSet()
        val recent = recentIds(current) + exclude
        val candidates = ArrayList<Candidate>()
        val seeds = listOf(current) + recentSeeds(current, EXTRA_SEEDS)
        for ((i, seed) in seeds.withIndex()) {
            val anchor = anchorOf(seed) ?: continue
            val songs = radioOf(anchor.id) ?: continue
            songs.forEachIndexed { rank, song ->
                if (!LocalMusic.isLocal(song.id)) candidates += Candidate(song, Origin.RADIO, rank, seed.title, seed.artist)
            }
            // Хватает с запасом — лишних запросов не делаем.
            if (i == 0 && candidates.size >= limit * 3) break
        }
        val ranked = RecRanker.rank(candidates, taste, seen, recent, s.recNovelty / 100.0, random(), limit)
        remember(ranked.map { it.song.id }, now)
        return ranked.map { it.song }
    }

    /** Блоки для главной. Кэшируются на полчаса; сеть нужна только «открытиям» и релизам. */
    suspend fun homeBlocks(force: Boolean = false): HomeBlocks {
        val now = clock()
        homeCache?.let { (at, blocks) -> if (!force && now - at < HOME_TTL_MS) return blocks }
        val s = settings.settings.first()
        if (!s.homeRecBlocks) return HomeBlocks()
        val taste = tasteProfile()
        val rnd = random()
        val recent = dao.recentPlays(RECENT_WINDOW).map { it.id }.toSet()

        val forgotten =
            if (s.tasteLearning) {
                dao.forgottenFavorites(now - FORGOTTEN_AFTER_MS, minPlays = 3, limit = 40)
                    .map { it.toItem() }
                    .filter { it.id !in recent && !taste.isExcluded(it.artist) }
                    .shuffled(java.util.Random(rnd.nextLong()))
                    .take(BLOCK_SIZE)
                    .map { Rec(it, Why.Forgotten) }
            } else {
                emptyList()
            }

        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val hours = listOf((hour + 23) % 24, hour, (hour + 1) % 24)
        val timeOfDay =
            if (s.tasteLearning) {
                dao.playedAtHours(hours, limit = 30)
                    .map { it.toItem() }
                    .filter { it.id !in recent && !taste.isExcluded(it.artist) }
                    .shuffled(java.util.Random(rnd.nextLong()))
                    .take(BLOCK_SIZE)
                    .map { Rec(it, Why.TimeOfDay) }
                    .takeIf { it.size >= MIN_TIME_BLOCK }
                    .orEmpty()
            } else {
                emptyList()
            }

        val discoveries = discoveries(s.tasteLearning, taste, recent, rnd, now)
        val releases = if (s.tasteLearning) releasesOfYourArtists(taste, now) else emptyList()
        return HomeBlocks(discoveries = discoveries, releases = releases, forgotten = forgotten, timeOfDay = timeOfDay)
            .also { homeCache = now to it }
    }

    // ---- внутреннее

    private suspend fun discoveries(learning: Boolean, taste: TasteProfile, recent: Set<String>, rnd: Random, now: Long): List<Rec> {
        if (!learning || !taste.hasData) return emptyList()
        val seen = dao.seenSince(now - SEEN_AGE_MS).toSet()
        val candidates = ArrayList<Candidate>()
        // Две разные опоры из недавнего — не больше двух запросов на блок.
        val seeds = recentSeeds(null, 2)
        for (seed in seeds) {
            val anchor = anchorOf(seed) ?: continue
            radioOf(anchor.id)?.forEachIndexed { i, song ->
                if (!LocalMusic.isLocal(song.id)) candidates += Candidate(song, Origin.RADIO, i, seed.title, seed.artist)
            }
        }
        // Открытия — только исполнители, которых раньше не слушали.
        val ranked =
            RecRanker.rank(candidates, taste, seen, recent, novelty = 1.0, random = rnd, limit = BLOCK_SIZE)
                .filter { it.why == Why.NewArtist }
        remember(ranked.map { it.song.id }, now)
        return ranked
    }

    private suspend fun releasesOfYourArtists(taste: TasteProfile, now: Long): List<PlaylistCard> {
        releasesCache?.let { (at, cards) -> if (now - at < RELEASES_TTL_MS) return cards }
        val year = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        val cards = ArrayList<PlaylistCard>()
        for ((artist, _) in taste.top(RELEASE_ARTISTS)) {
            val id = dao.artistIdOf(artist) ?: continue
            val found = runCatching { call { source.releases(id) } }.getOrNull() ?: continue
            // Даты выхода клиент не отдаёт, только подпись с годом: «новым» считаем этот год и прошлый.
            cards += found.filter { c -> Regex("""20\d\d""").findAll(c.subtitle.orEmpty()).any { it.value.toInt() >= year - 1 } }.take(2)
        }
        return cards.distinctBy { it.browseId }.take(BLOCK_SIZE).also { releasesCache = now to it }
    }

    private suspend fun anchorOf(song: SongItem): SongItem? =
        if (LocalMusic.isLocal(song.id)) runCatching { call { source.anchorFor(song) } }.getOrNull() else song

    /** Радио с кэшем; null — сети нет. */
    private suspend fun radioOf(videoId: String): List<SongItem>? {
        val now = clock()
        synchronized(radioCache) { radioCache[videoId] }?.let { if (now - it.at < RADIO_TTL_MS) return it.songs }
        return try {
            val songs = call { source.radio(videoId) }
            synchronized(radioCache) { radioCache[videoId] = Cached(now, songs) }
            songs
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Устаревший кэш лучше пустоты: офлайн показываем то, что было.
            synchronized(radioCache) { radioCache[videoId] }?.songs
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T {
        networkCalls++
        return block()
    }

    private suspend fun recentIds(current: SongItem): Set<String> =
        dao.recentPlays(RECENT_WINDOW).map { it.id }.toSet() + current.id

    /** Опорные треки из недавних включений: разные исполнители, без [except]. */
    private suspend fun recentSeeds(except: SongItem?, count: Int): List<SongItem> =
        dao.recentPlays(RECENT_WINDOW)
            .map { it.toItem() }
            .filter { it.id != except?.id && (except == null || TasteProfile.key(it.artist) != TasteProfile.key(except.artist)) }
            .distinctBy { TasteProfile.key(it.artist) }
            .take(count)

    private suspend fun remember(ids: List<String>, now: Long) {
        if (ids.isEmpty()) return
        dao.markSeen(ids.map { RecSeenEntity(it, now) })
        dao.pruneSeen(now - SEEN_AGE_MS)
        dao.capSeen(SEEN_MAX)
    }

    private fun fallback(id: String): RecSet {
        val last = synchronized(lastSets) { lastSets[id] } ?: lastGlobal
        return RecSet(last?.items.orEmpty(), offline = true)
    }

    companion object {
        const val SEEN_AGE_MS = 14L * 24 * 60 * 60 * 1000
        const val SEEN_MAX = 2000
        const val SIMILAR_LIMIT = 20
        const val CONTINUATION_LIMIT = 25
        private const val CACHE_SIZE = 16
        private const val RADIO_TTL_MS = 15L * 60 * 1000
        private const val HOME_TTL_MS = 30L * 60 * 1000
        private const val RELEASES_TTL_MS = 6L * 60 * 60 * 1000
        private const val TASTE_WINDOW_MS = 120L * 24 * 60 * 60 * 1000
        private const val FORGOTTEN_AFTER_MS = 45L * 24 * 60 * 60 * 1000
        private const val EXTRA_SEEDS = 2
        private const val RECENT_WINDOW = 30
        private const val BLOCK_SIZE = 10
        private const val MIN_TIME_BLOCK = 3
        private const val RELEASE_ARTISTS = 4
    }
}
