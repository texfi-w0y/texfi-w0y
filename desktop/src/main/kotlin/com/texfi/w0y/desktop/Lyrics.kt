package com.texfi.w0y.desktop

import com.texfi.w0y.data.SongItem
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val plain: String?, val synced: List<LyricLine>) {
    val isEmpty: Boolean get() = plain.isNullOrBlank() && synced.isEmpty()
}

/** Тексты из LRCLIB (открытая база без ключей) и перевод через публичный endpoint Google Translate. */
class LyricsRepo {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val translations = ConcurrentHashMap<String, List<String>>()

    /** «Искать текст везде»: если в LRCLIB пусто, пробуем ещё lyrics.ovh (текст без таймингов). */
    @Volatile
    var allSources = true

    /** Готовый текст без запроса в сеть: для проверок интерфейса. */
    internal fun seed(songId: String, lyrics: Lyrics) {
        cache[songId] = lyrics
    }

    suspend fun lyrics(song: SongItem): Lyrics? = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }
        val title = song.title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()
        val artist = song.artist.substringBefore(',').trim()
        runCatching { exact(artist, title) ?: search(artist, title) ?: if (allSources) ovh(artist, title) else null }.getOrNull()?.also { cache[song.id] = it }
    }

    private fun ovh(artist: String, title: String): Lyrics? {
        val body = get("https://api.lyrics.ovh/v1/${q(artist)}/${q(title)}") ?: return null
        val text = (Json.parseToJsonElement(body).jsonObject["lyrics"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return Lyrics(text, emptyList())
    }

    private fun q(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun get(url: String): String? {
        val request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "TexFi-w0y").timeout(Duration.ofSeconds(10)).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body().takeIf { response.statusCode() == 200 }
    }

    private fun exact(artist: String, title: String): Lyrics? =
        get("https://lrclib.net/api/get?artist_name=${q(artist)}&track_name=${q(title)}")?.let {
            parse(Json.parseToJsonElement(it).jsonObject)
        }

    private fun search(artist: String, title: String): Lyrics? {
        val body = get("https://lrclib.net/api/search?q=${q("$artist $title".trim())}") ?: return null
        val array = Json.parseToJsonElement(body).jsonArray
        return array.firstNotNullOfOrNull { (it as? JsonObject)?.let(::parse) }
    }

    private fun parse(obj: JsonObject): Lyrics? {
        val plain = (obj["plainLyrics"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val synced = (obj["syncedLyrics"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let(::parseLrc).orEmpty()
        return Lyrics(plain, synced).takeUnless { it.isEmpty }
    }

    private fun parseLrc(raw: String): List<LyricLine> {
        val stamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        return raw.lineSequence().mapNotNull { line ->
            val m = stamp.find(line) ?: return@mapNotNull null
            val (mm, ss, frac) = m.destructured
            val ms = mm.toLong() * 60_000 + ss.toLong() * 1000 + (frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0)
            line.substring(m.range.last + 1).trim().takeIf { it.isNotBlank() }?.let { LyricLine(ms, it) }
        }.sortedBy { it.timeMs }.toList()
    }

    /** Построчный перевод; null, если сервис не ответил, — текст не выдумываем. */
    suspend fun translate(songId: String, lyrics: Lyrics, lang: String): List<String>? = withContext(Dispatchers.IO) {
        val key = "$songId|$lang|${lyrics.synced.size}"
        translations[key]?.let { return@withContext it }
        val source = if (lyrics.synced.isNotEmpty()) lyrics.synced.map { it.text } else lyrics.plain.orEmpty().lines()
        if (source.isEmpty()) return@withContext null
        val out = ArrayList<String>(source.size)
        for (chunk in source.chunked(40)) {
            out += runCatching { translateChunk(chunk, lang) }.getOrNull() ?: return@withContext null
        }
        out.also { translations[key] = it }
    }

    private fun translateChunk(lines: List<String>, lang: String): List<String>? {
        val request =
            HttpRequest.newBuilder(URI.create("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$lang&dt=t"))
                .header("User-Agent", "Mozilla/5.0")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("q=" + q(lines.joinToString("\n"))))
                .timeout(Duration.ofSeconds(10))
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null
        val segments = Json.parseToJsonElement(response.body()).jsonArray[0] as JsonArray
        val joined = segments.joinToString("") { ((it as JsonArray)[0] as? JsonPrimitive)?.contentOrNull.orEmpty() }
        val result = joined.split("\n")
        return if (result.size == lines.size) result else null
    }
}
