package com.texfi.w0y

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.PlaylistCard
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.db.SongEntity
import com.texfi.w0y.data.db.W0yDatabase
import com.texfi.w0y.recs.FlowChoice
import com.texfi.w0y.recs.FlowEngine
import com.texfi.w0y.recs.FlowGenre
import com.texfi.w0y.recs.FlowMood
import com.texfi.w0y.recs.RecEngine
import com.texfi.w0y.recs.RecSource
import com.texfi.w0y.recs.Why
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Поиск отдаёт по запросу 20 треков; «Mine» — исполнитель из лайков, он есть в каждой выдаче. */
private class FlowFakeSource : RecSource {
    val queries = ArrayList<String>()
    var radios = 0
    var offline = false

    override suspend fun search(query: String): List<SongItem> {
        queries += query
        if (offline) error("нет сети")
        return (0 until 20).map { i -> SongItem(id = "s${query.hashCode()}-$i", title = "$query $i", artist = if (i == 3) "Mine" else "New$i", thumbnailUrl = "u$i") }
    }

    override suspend fun radio(videoId: String): List<SongItem> {
        radios++
        if (offline) error("нет сети")
        return (0 until 30).map { i -> SongItem(id = "$videoId-r$i", title = "r$i", artist = "Radio${i % 10}") }
    }

    override suspend fun anchorFor(local: SongItem): SongItem? = null

    override suspend fun releases(artistBrowseId: String): List<PlaylistCard> = emptyList()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlowEngineTest {
    private class Env(val db: W0yDatabase, val source: FlowFakeSource, val engine: FlowEngine)

    private fun env(): Env {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, W0yDatabase::class.java).allowMainThreadQueries().build()
        val source = FlowFakeSource()
        val recs = RecEngine(source, db.dao(), SettingsRepository(context))
        return Env(db, source, FlowEngine(source, db.dao(), recs))
    }

    private suspend fun like(env: Env, id: String, artist: String) {
        env.db.dao().saveSongMeta(SongEntity(id = id, title = id, artist = artist, thumbnailUrl = "cover-$id"))
        env.db.dao().setLiked(id, true, 1000L)
    }

    @Test
    fun moodAndTwoGenresStayWithinFiveRequests() = runBlocking {
        val env = env()
        try {
            val set = env.engine.build(FlowChoice(FlowMood.SAD, setOf(FlowGenre.RAP, FlowGenre.ROCK, FlowGenre.POP), 40), russian = true)
            assertEquals("жанров в запросах не больше двух", 2, env.source.queries.size)
            assertTrue(env.source.queries.all { it.startsWith("грустный ") })
            assertTrue("сеть на подборку", env.engine.networkCalls <= 5)
            assertTrue(set.items.isNotEmpty())
            assertFalse(set.offline)
        } finally {
            env.db.close()
        }
    }

    @Test
    fun englishQueriesOutsideRussian() = runBlocking {
        val env = env()
        try {
            env.engine.build(FlowChoice(FlowMood.FOCUS, setOf(FlowGenre.LOFI), 40), russian = false)
            assertEquals(listOf("lo-fi for focus"), env.source.queries)
        } finally {
            env.db.close()
        }
    }

    @Test
    fun likesOfArtistsFoundInTheMoodComeInOthersDoNot() = runBlocking {
        val env = env()
        try {
            like(env, "mine1", "Mine")
            like(env, "other1", "Someone Else")
            val set = env.engine.build(FlowChoice(FlowMood.CALM, emptySet(), 0), russian = true)
            val ids = set.items.map { it.song.id }
            assertTrue("лайк исполнителя из настроения", "mine1" in ids)
            assertFalse("лайк чужого настроения", "other1" in ids)
            assertTrue(set.items.first { it.song.id == "mine1" }.why is Why.YouListen)
        } finally {
            env.db.close()
        }
    }

    @Test
    fun offlineFallsBackToOwnAndSaysSo() = runBlocking {
        val env = env()
        try {
            like(env, "mine1", "Mine")
            like(env, "mine2", "Mine")
            env.source.offline = true
            val set = env.engine.build(FlowChoice(FlowMood.CALM, emptySet(), 0), russian = true)
            assertEquals(setOf("mine1", "mine2"), set.items.map { it.song.id }.toSet())
            assertTrue(set.offline)
        } finally {
            env.db.close()
        }
    }

    @Test
    fun introUsesOwnCoversAndRandomSongsWhenThereAreNone() = runBlocking {
        val env = env()
        try {
            val stranger = env.engine.introCovers(russian = true, count = 6)
            assertTrue("без своего — обложки случайных песен", stranger.isNotEmpty())
            repeat(4) { like(env, "l$it", "A$it") }
            env.source.queries.clear()
            val own = env.engine.introCovers(russian = true, count = 6)
            assertTrue(own.all { it.startsWith("cover-") })
            assertTrue("своих хватает — сеть не нужна", env.source.queries.isEmpty())
        } finally {
            env.db.close()
        }
    }

    @Test
    fun choiceSurvivesSaving() {
        val c = FlowChoice(FlowMood.NIGHT, setOf(FlowGenre.RAP, FlowGenre.PHONK), 55)
        assertEquals(c, FlowChoice.decode(c.mood!!.name, c.encodeGenres(), c.novelty))
        assertEquals(FlowChoice(null, emptySet(), 100), FlowChoice.decode("", "", 140))
    }
}
