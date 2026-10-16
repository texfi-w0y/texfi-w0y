package com.texfi.w0y

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.db.HistoryEntity
import com.texfi.w0y.data.db.RecSeenEntity
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDatabase
import com.texfi.w0y.recs.Candidate
import com.texfi.w0y.recs.Origin
import com.texfi.w0y.recs.RecEngine
import com.texfi.w0y.recs.RecRanker
import com.texfi.w0y.recs.RecSource
import com.texfi.w0y.recs.TasteProfile
import com.texfi.w0y.recs.Why
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeSource : RecSource {
    var calls = 0
    var offline = false

    override suspend fun radio(videoId: String): List<SongItem> {
        calls++
        if (offline) error("нет сети")
        // 60 разных треков 20 исполнителей; набор зависит от опоры.
        return (0 until 60).map { i -> SongItem(id = "$videoId-$i", title = "t$videoId$i", artist = "Artist${i % 20}") }
    }

    override suspend fun anchorFor(local: SongItem): SongItem? = null

    override suspend fun releases(artistBrowseId: String): List<PlaylistCard> = emptyList()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecEngineTest {
    private fun song(id: String, artist: String = "A") = SongItem(id = id, title = id, artist = artist)

    private fun cand(i: Int, artist: String = "X$i", origin: Origin = Origin.RADIO) =
        Candidate(song("c$i", artist), origin, rank = i, seedTitle = "seed", seedArtist = "Seed")

    // ---- ранжировщик

    @Test
    fun rankerDropsSeenRecentExcludedAndDuplicates() {
        val taste = TasteProfile(mapOf("known" to 5), excluded = setOf("banned"))
        val list =
            listOf(
                cand(1, "Known"), cand(2, "Banned"), cand(3, "New3"), cand(4, "New4"),
                Candidate(song("c3", "New3-copy"), Origin.RADIO, 5), // тот же id
                Candidate(SongItem("zzz", "c4 (Official Video)", "New4"), Origin.RADIO, 6), // тот же трек другим id
            )
        val out = RecRanker.rank(list, taste, seen = setOf("c1"), recent = emptySet(), novelty = 0.5, random = Random(1), limit = 10)
        val ids = out.map { it.song.id }
        assertFalse("показанное", "c1" in ids)
        assertFalse("исключённый исполнитель", "c2" in ids)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(2, ids.size)
    }

    @Test
    fun noveltyShareIsRespected() {
        val taste = TasteProfile(mapOf("a0" to 9, "a1" to 9, "a2" to 9, "a3" to 9, "a4" to 9))
        val known = (0 until 20).map { cand(it, "a${it % 5}") }
        val fresh = (20 until 40).map { cand(it, "n$it") }
        fun share(novelty: Double): Int =
            RecRanker.rank(known + fresh, taste, emptySet(), emptySet(), novelty, Random(7), 10)
                .count { it.why == Why.NewArtist }
        assertEquals(0, share(0.0))
        assertEquals(3, share(0.3))
        assertEquals(10, share(1.0))
    }

    @Test
    fun differentSeedsGiveDifferentOrder() {
        val taste = TasteProfile(mapOf("a" to 3))
        val pool = (0 until 40).map { cand(it, "n$it") }
        val a = RecRanker.rank(pool, taste, emptySet(), emptySet(), 0.3, Random(1), 10).map { it.song.id }
        val b = RecRanker.rank(pool, taste, emptySet(), emptySet(), 0.3, Random(2), 10).map { it.song.id }
        assertTrue(a != b)
    }

    @Test
    fun withoutTasteDataNothingIsCalledNew() {
        val out = RecRanker.rank((0 until 5).map { cand(it) }, TasteProfile.EMPTY, emptySet(), emptySet(), 0.3, Random(1), 5)
        assertTrue(out.none { it.why == Why.NewArtist })
    }

    // ---- движок

    private fun env(): Triple<W0yDatabase, FakeSource, RecEngine> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, W0yDatabase::class.java).allowMainThreadQueries().build()
        val source = FakeSource()
        val engine = RecEngine(source, db.dao(), SettingsRepository(context))
        return Triple(db, source, engine)
    }

    @Test
    fun trackChangeCostsOneRequestAndRepeatsComeFromCache() = runBlocking {
        val (db, source, engine) = env()
        try {
            val cur = song("cur")
            engine.similar(cur, manual = false)
            assertEquals(1, source.calls)
            engine.similar(cur, manual = false)
            assertEquals("радио в кэше", 1, source.calls)
        } finally {
            db.close()
        }
    }

    @Test
    fun manualRefreshAlwaysGivesDifferentSet() = runBlocking {
        val (db, source, engine) = env()
        try {
            val cur = song("cur")
            val first = engine.similar(cur, manual = false).items.map { it.song.id }
            val second = engine.similar(cur, manual = true).items.map { it.song.id }
            val third = engine.similar(cur, manual = true).items.map { it.song.id }
            assertTrue(first.isNotEmpty() && second.isNotEmpty())
            assertTrue("пересечение первого и второго", first.intersect(second.toSet()).isEmpty())
            assertTrue(second.intersect(third.toSet()).isEmpty())
            assertTrue("запомнено в базе", db.dao().seenCount() >= first.size + second.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun offlineShowsLastSetAndSaysSo() = runBlocking {
        val (db, source, engine) = env()
        try {
            val cur = song("cur")
            val shown = engine.similar(cur, manual = false)
            source.offline = true
            // В кэше радио ещё свежее, поэтому сначала «оффлайн» без кэша: другой трек.
            val other = engine.similar(song("other"), manual = false)
            assertTrue(other.offline)
            assertEquals("последний показанный набор", shown.items.map { it.song.id }, other.items.map { it.song.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun seenIsPrunedByAgeAndCount() = runBlocking {
        val (db, _, _) = env()
        try {
            val dao = db.dao()
            val now = 100L * RecEngine.SEEN_AGE_MS
            dao.markSeen((0 until 2500).map { RecSeenEntity("s$it", now - it) })
            dao.markSeen(listOf(RecSeenEntity("old", now - RecEngine.SEEN_AGE_MS - 1)))
            dao.pruneSeen(now - RecEngine.SEEN_AGE_MS)
            dao.capSeen(RecEngine.SEEN_MAX)
            assertEquals(RecEngine.SEEN_MAX, dao.seenCount())
            assertFalse("old" in dao.seenSince(0))
            assertTrue("s0" in dao.seenSince(0))
        } finally {
            db.close()
        }
    }

    @Test
    fun continuationExcludesQueueAndUsesRecentSeeds() = runBlocking {
        val (db, source, engine) = env()
        try {
            val dao = db.dao()
            // История: три разных исполнителя, чтобы были опорные треки.
            listOf("h1" to "Aa", "h2" to "Bb", "h3" to "Cc").forEachIndexed { i, (id, artist) ->
                dao.saveSongMeta(SongEntity(id = id, title = id, artist = artist))
                dao.addHistory(HistoryEntity(songId = id, playedAt = 1000L + i, listenedMs = 200_000, counted = true))
            }
            val queue = (0 until 5).map { "cur-$it" }.toSet()
            val out = engine.continuation(song("cur", "Zz"), exclude = queue, limit = 25)
            assertTrue(out.isNotEmpty())
            assertTrue(out.none { it.id in queue })
            assertTrue("не больше трёх запросов", source.calls <= 3)
        } finally {
            db.close()
        }
    }

    @Test
    fun sameSeedManyTimesNeverLeavesQueueEmpty() = runBlocking {
        val (db, _, engine) = env()
        try {
            // Радио даёт 60 треков, каждый запуск помечает 25 показанными: к третьему разу свежих нет.
            repeat(5) { round ->
                val out = engine.continuation(song("cur", "Zz"), exclude = setOf("cur"), limit = 25)
                assertTrue("запуск ${round + 1}: ${out.size}", out.size >= 8)
                assertTrue(out.none { it.id == "cur" })
            }
        } finally {
            db.close()
        }
    }
}
