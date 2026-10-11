package com.texfi.w0y.data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** Слово с моментом начала — из расширенного LRC (`<01:02.30>слово`). */
data class LyricWord(val timeMs: Long, val text: String)

/** Строка синхронизированной лирики; [words] пусты, если пословных меток нет. */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

data class Lyrics(
    val plain: String?,
    val synced: List<LyricLine>,
) {
    val isEmpty: Boolean get() = plain.isNullOrBlank() && synced.isEmpty()
}

/**
 * Лирика из LRCLIB — открытой базы без ключей и регистрации.
 *
 * Сначала точный запрос по исполнителю и названию, потом поиск: у YouTube
 * названия часто с приписками вроде «(Official Video)», и точное совпадение
 * не находится.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val okHttp: OkHttpClient,
) {
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val translations = ConcurrentHashMap<String, List<String>>()

    /**
     * Перевод строк лирики на [lang]: по одной строке на каждую строку оригинала
     * (синхронизированную, если она есть, иначе строки простого текста).
     * Переводит неофициальный публичный endpoint Google Translate. При сбое возвращаем null,
     * а не выдумываем текст.
     */
    suspend fun translate(songId: String, lyrics: Lyrics, lang: String): List<String>? =
        withContext(Dispatchers.IO) {
            val key = "$songId|$lang|${lyrics.synced.size}"
            translations[key]?.let { return@withContext it }
            val source =
                if (lyrics.synced.isNotEmpty()) {
                    lyrics.synced.map { it.text }
                } else {
                    lyrics.plain.orEmpty().lines()
                }
            if (source.isEmpty()) return@withContext null
            val out = ArrayList<String>(source.size)
            for (chunk in source.chunked(40)) {
                val translated = runCatching { translateChunk(chunk, lang) }.getOrNull() ?: return@withContext null
                out += translated
            }
            out.also { translations[key] = it }
        }

    private fun translateChunk(lines: List<String>, lang: String): List<String>? {
        val body =
            okhttp3.FormBody
                .Builder()
                .add("q", lines.joinToString("\n"))
                .build()
        val request =
            Request
                .Builder()
                .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$lang&dt=t")
                .header("User-Agent", "Mozilla/5.0")
                .post(body)
                .build()
        val raw =
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            } ?: return null
        val segments = JSONArray(raw).getJSONArray(0)
        val joined = StringBuilder()
        for (i in 0 until segments.length()) {
            joined.append(segments.getJSONArray(i).optString(0))
        }
        val result = joined.toString().split("\n")
        return if (result.size == lines.size) result else null
    }

    /**
     * Текст по исполнителю и названию. [allSources] — после LRCLIB пробуем и другие
     * базы: NetEase (с таймингами), lyrics.ovh и Genius (простой текст). Первая
     * нашедшая побеждает; синхронизированный текст всегда лучше простого.
     */
    suspend fun lyrics(song: SongItem, allSources: Boolean = true): Lyrics? = withContext(Dispatchers.IO) {
        val key = if (allSources) song.id else "${song.id}|lrclib"
        cache[key]?.let { return@withContext it }
        val cleanTitle = cleanTitle(song.title)
        val artist = song.artist.substringBefore(',').substringBefore(" & ").trim()
        val found =
            attempt("lrclib") { exact(song.artist, cleanTitle) ?: search(song.artist, cleanTitle) }
                ?: if (allSources) {
                    attempt("netease") { netease(artist, cleanTitle) }
                        ?: attempt("lyrics.ovh") { ovh(artist, cleanTitle) }
                        ?: attempt("genius") { genius(artist, cleanTitle) }
                } else {
                    null
                }
        found?.also { cache[key] = it }
    }

    private inline fun attempt(source: String, block: () -> Lyrics?): Lyrics? =
        runCatching(block)
            .onFailure { Timber.w(it, "Лирика: источник %s не ответил", source) }
            .getOrNull()

    /** Название без приписок в скобках и после « - » (Remastered, Official Video, feat.). */
    private fun cleanTitle(title: String): String =
        title
            .replace(Regex("\\s*[(\\[].*?[)\\]]"), "")
            .replace(Regex("\\s+-\\s+.*$"), "")
            .replace(Regex("(?i)\\s+(feat|ft)\\.?\\s+.*$"), "")
            .trim()
            .ifBlank { title }

    private fun norm(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    /** Подходит ли найденное под искомое: название содержит друг друга, исполнитель пересекается. */
    private fun matches(wantTitle: String, wantArtist: String, gotTitle: String, gotArtist: String): Boolean {
        val wt = norm(wantTitle)
        val gt = norm(gotTitle)
        if (wt.isEmpty() || gt.isEmpty() || !(wt.contains(gt) || gt.contains(wt))) return false
        val wa = norm(wantArtist)
        val ga = norm(gotArtist)
        return wa.isEmpty() || ga.isEmpty() || wa.contains(ga) || ga.contains(wa)
    }

    /** NetEase Cloud Music: открытый поиск и текст с таймингами. */
    private fun netease(artist: String, title: String): Lyrics? {
        val search =
            "https://music.163.com/api/search/get/web"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("s", "$artist $title".trim())
                .addQueryParameter("type", "1")
                .addQueryParameter("offset", "0")
                .addQueryParameter("limit", "6")
                .build()
        val headers = mapOf("Referer" to "https://music.163.com/", "User-Agent" to BROWSER_UA)
        val songs = JSONObject(get(search.toString(), headers) ?: return null).optJSONObject("result")?.optJSONArray("songs") ?: return null
        for (i in 0 until songs.length()) {
            val item = songs.getJSONObject(i)
            val names = item.optJSONArray("artists")?.let { arr -> (0 until arr.length()).joinToString(" ") { arr.getJSONObject(it).optString("name") } }.orEmpty()
            if (!matches(title, artist, item.optString("name"), names)) continue
            val url = "https://music.163.com/api/song/lyric?id=${item.getLong("id")}&lv=1&kv=1&tv=-1"
            val lrc = JSONObject(get(url, headers) ?: continue).optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() } ?: continue
            val synced = LrcParser.parse(lrc)
            if (synced.isNotEmpty()) return Lyrics(plain = null, synced = synced)
        }
        return null
    }

    /** lyrics.ovh: простой текст по исполнителю и названию. */
    private fun ovh(artist: String, title: String): Lyrics? {
        if (artist.isBlank()) return null
        val url = "https://api.lyrics.ovh/v1".toHttpUrl().newBuilder().addPathSegment(artist).addPathSegment(title).build()
        val text = JSONObject(get(url.toString()) ?: return null).optString("lyrics").trim()
        return text.takeIf { it.isNotBlank() }?.let { Lyrics(plain = it.replace("\r", ""), synced = emptyList()) }
    }

    /** Genius: поиск по их же сайту, текст — из блоков data-lyrics-container. */
    private fun genius(artist: String, title: String): Lyrics? {
        val search =
            "https://genius.com/api/search/multi"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", "$artist $title".trim())
                .build()
        val headers = mapOf("User-Agent" to BROWSER_UA)
        val sections = JSONObject(get(search.toString(), headers) ?: return null).optJSONObject("response")?.optJSONArray("sections") ?: return null
        for (s in 0 until sections.length()) {
            val hits = sections.getJSONObject(s).optJSONArray("hits") ?: continue
            for (h in 0 until hits.length()) {
                val hit = hits.getJSONObject(h)
                if (hit.optString("type") != "song") continue
                val result = hit.optJSONObject("result") ?: continue
                if (!matches(title, artist, result.optString("title"), result.optJSONObject("primary_artist")?.optString("name").orEmpty())) continue
                val page = get(result.optString("url"), headers) ?: continue
                val text = geniusText(page)
                if (text.isNotBlank()) return Lyrics(plain = text, synced = emptyList())
            }
        }
        return null
    }

    private fun geniusText(html: String): String {
        val blocks = Regex("<div[^>]*data-lyrics-container=\"true\"[^>]*>(.*?)</div>\\s*(?=<div|<aside|<script|$)", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        val raw = blocks.joinToString("\n") { it.groupValues[1] }
        return raw
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun exact(artist: String, title: String): Lyrics? {
        val url =
            "https://lrclib.net/api/get"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("track_name", title)
                .build()
        val body = get(url.toString()) ?: return null
        return parse(JSONObject(body))
    }

    private fun search(artist: String, title: String): Lyrics? {
        val url =
            "https://lrclib.net/api/search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", "$artist $title".trim())
                .build()
        val body = get(url.toString()) ?: return null
        val array = JSONArray(body)
        if (array.length() == 0) return null
        return parse(array.getJSONObject(0))
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val request = Request.Builder().url(url).header("User-Agent", "TexFi-w0y").apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun parse(obj: JSONObject): Lyrics? {
        val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() }
        val syncedRaw = obj.optString("syncedLyrics").takeIf { it.isNotBlank() }
        val synced = syncedRaw?.let(::parseLrc).orEmpty()
        val lyrics = Lyrics(plain = plain, synced = synced)
        return lyrics.takeUnless { it.isEmpty }
    }

    private fun parseLrc(raw: String): List<LyricLine> = LrcParser.parse(raw)
}

private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

/**
 * Разбор LRC без Android — проверяется тестом.
 *
 * Строка `[мм:сс.хх]текст`; в расширенном варианте внутри строки ещё
 * метки слов `<мм:сс.хх>`. Метки слов вырезаются из текста и становятся
 * [LyricWord]: по ним подсвечивается слово, которое звучит.
 */
object LrcParser {
    private val lineStamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val wordStamp = Regex("<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>")

    fun parse(raw: String): List<LyricLine> =
        raw
            .lineSequence()
            .mapNotNull { line ->
                val match = lineStamp.find(line) ?: return@mapNotNull null
                val start = millis(match.groupValues)
                val body = line.substring(match.range.last + 1)
                val words = words(body)
                val text = wordStamp.replace(body, "").replace(Regex("\\s+"), " ").trim()
                text.takeIf { it.isNotBlank() }?.let { LyricLine(start, it, words) }
            }.sortedBy { it.timeMs }
            .toList()

    private fun words(body: String): List<LyricWord> {
        val stamps = wordStamp.findAll(body).toList()
        if (stamps.isEmpty()) return emptyList()
        return stamps.mapIndexedNotNull { i, m ->
            val end = stamps.getOrNull(i + 1)?.range?.first ?: body.length
            val word = body.substring(m.range.last + 1, end).trim()
            word.takeIf { it.isNotEmpty() }?.let { LyricWord(millis(m.groupValues), it) }
        }
    }

    private fun millis(g: List<String>): Long =
        g[1].toLong() * 60_000 + g[2].toLong() * 1_000 + (g[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0)

    /** Индекс строки, которая звучит в [positionMs]; -1 — ещё ни одна. */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}
