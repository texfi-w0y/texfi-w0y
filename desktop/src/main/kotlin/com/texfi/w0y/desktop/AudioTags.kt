package com.texfi.w0y.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Теги и длительность из аудиофайла без сторонних библиотек: ID3v2 (mp3), FLAC, MP4/M4A,
 * Ogg Vorbis/Opus и WAV. Чего нет в файле или что не разобралось, остаётся пустым —
 * название тогда берётся из имени файла, а длительность узнаётся при первом проигрывании.
 */
class AudioTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** Обложка из файла как есть (jpeg/png). */
    val cover: ByteArray? = null,
    val durationMs: Long = 0L,
) {
    companion object {
        private const val MAX_TAG = 24 * 1024 * 1024

        fun read(file: File): AudioTags =
            runCatching { RandomAccessFile(file, "r").use { parse(it, file.length()) } }.getOrDefault(AudioTags())

        private fun parse(f: RandomAccessFile, length: Long): AudioTags {
            val head = ByteArray(minOf(12L, length).toInt()).also { f.seek(0); f.readFully(it) }
            return when {
                head.size >= 4 && head.ascii(0, 3) == "ID3" -> id3(f, length)
                head.size >= 4 && head.ascii(0, 4) == "fLaC" -> flac(f, length)
                head.size >= 8 && head.ascii(4, 4) == "ftyp" -> mp4(f, length)
                head.size >= 4 && head.ascii(0, 4) == "OggS" -> ogg(f, length)
                head.size >= 12 && head.ascii(0, 4) == "RIFF" && head.ascii(8, 4) == "WAVE" -> wav(f, length)
                else -> AudioTags(durationMs = mpegDuration(f, 0, length))
            }
        }

        // ---- ID3v2
        private fun id3(f: RandomAccessFile, length: Long): AudioTags {
            val h = ByteArray(10).also { f.seek(0); f.readFully(it) }
            val major = h[3].toInt()
            val flags = h[5].toInt()
            val size = syncsafe(h, 6)
            if (major !in 2..4 || size <= 0 || size > MAX_TAG || 10L + size > length) return AudioTags(durationMs = mpegDuration(f, 10L + size.coerceAtLeast(0), length))
            var body = ByteArray(size).also { f.readFully(it) }
            if (flags and 0x80 != 0 && major < 4) body = unsync(body)
            var pos = 0
            if (flags and 0x40 != 0 && body.size >= 4) {
                // Расширенный заголовок: в v2.4 размер синхробезопасный и включает себя, в v2.3 — не включает.
                pos = if (major == 4) syncsafe(body, 0) else be32(body, 0) + 4
            }
            var title: String? = null
            var artist: String? = null
            var albumArtist: String? = null
            var album: String? = null
            var cover: ByteArray? = null
            var coverType = -1
            val idLen = if (major == 2) 3 else 4
            val headLen = if (major == 2) 6 else 10
            while (pos + headLen <= body.size && body[pos].toInt() != 0) {
                val id = body.ascii(pos, idLen)
                val frameSize = when (major) {
                    2 -> (body[pos + 3].u() shl 16) or (body[pos + 4].u() shl 8) or body[pos + 5].u()
                    4 -> syncsafe(body, pos + 4)
                    else -> be32(body, pos + 4)
                }
                val frameFlags2 = if (major == 2) 0 else body[pos + 9].u()
                var start = pos + headLen
                var end = start + frameSize
                if (frameSize < 0 || end > body.size) break
                if (major == 4 && frameFlags2 and 0x01 != 0) start += 4 // индикатор длины данных
                pos = end
                if (start >= end) continue
                var data = body.copyOfRange(start, end)
                if (major == 4 && frameFlags2 and 0x02 != 0) data = unsync(data)
                when (id) {
                    "TIT2", "TT2" -> title = title ?: textFrame(data)
                    "TPE1", "TP1" -> artist = artist ?: textFrame(data)
                    "TPE2", "TP2" -> albumArtist = albumArtist ?: textFrame(data)
                    "TALB", "TAL" -> album = album ?: textFrame(data)
                    "APIC", "PIC" -> {
                        val pic = picFrame(data, major == 2)
                        if (pic != null && (cover == null || (pic.first == 3 && coverType != 3))) {
                            cover = pic.second
                            coverType = pic.first
                        }
                    }
                }
            }
            val audioStart = 10L + size + if (flags and 0x10 != 0) 10 else 0
            return AudioTags(title, artist ?: albumArtist, album, cover, mpegDuration(f, audioStart, length))
        }

        private fun textFrame(data: ByteArray): String? {
            if (data.isEmpty()) return null
            val text = decode(data, 1, data.size, data[0].toInt())
            return text.substringBefore('\u0000').trim().takeIf { it.isNotEmpty() }
        }

        /** (тип картинки, байты). */
        private fun picFrame(data: ByteArray, v22: Boolean): Pair<Int, ByteArray>? {
            if (data.size < 8) return null
            val enc = data[0].toInt()
            var p = 1
            if (v22) p += 3 else {
                while (p < data.size && data[p].toInt() != 0) p++
                p++
            }
            if (p >= data.size) return null
            val type = data[p].u()
            p++
            // Описание заканчивается нулём (в UTF-16 — двойным).
            val wide = enc == 1 || enc == 2
            while (p < data.size) {
                if (wide) {
                    if (p + 1 < data.size && data[p].toInt() == 0 && data[p + 1].toInt() == 0) { p += 2; break }
                    p += 2
                } else {
                    if (data[p].toInt() == 0) { p++; break }
                    p++
                }
            }
            if (p >= data.size) return null
            val img = data.copyOfRange(p, data.size)
            return if (looksLikeImage(img)) type to img else null
        }

        private fun decode(data: ByteArray, from: Int, to: Int, enc: Int): String {
            if (from >= to) return ""
            return when (enc) {
                1 -> {
                    val bom = to - from >= 2 && data[from].u() == 0xFF && data[from + 1].u() == 0xFE
                    val be = to - from >= 2 && data[from].u() == 0xFE && data[from + 1].u() == 0xFF
                    when {
                        bom -> String(data, from + 2, to - from - 2, Charsets.UTF_16LE)
                        be -> String(data, from + 2, to - from - 2, Charsets.UTF_16BE)
                        else -> String(data, from, to - from, Charsets.UTF_16LE)
                    }
                }
                2 -> String(data, from, to - from, Charsets.UTF_16BE)
                3 -> String(data, from, to - from, Charsets.UTF_8)
                else -> String(data, from, to - from, latin1OrCp1251(data, from, to))
            }
        }

        /** В старых русских mp3 «ISO-8859-1» на деле cp1251: если байты похожи на кириллицу, читаем так. */
        private fun latin1OrCp1251(data: ByteArray, from: Int, to: Int): Charset {
            var high = 0
            var cyr = 0
            for (i in from until to) {
                val b = data[i].u()
                if (b >= 0x80) {
                    high++
                    if (b in 0xC0..0xFF || b == 0xA8 || b == 0xB8) cyr++
                }
            }
            return if (high > 0 && cyr * 10 >= high * 8) Charset.forName("windows-1251") else Charsets.ISO_8859_1
        }

        private fun unsync(data: ByteArray): ByteArray {
            val out = java.io.ByteArrayOutputStream(data.size)
            var i = 0
            while (i < data.size) {
                out.write(data[i].toInt())
                if (data[i].u() == 0xFF && i + 1 < data.size && data[i + 1].toInt() == 0) i++
                i++
            }
            return out.toByteArray()
        }

        // ---- MPEG: длительность из заголовка Xing/VBRI или по постоянному битрейту (слой III)
        private val BR_V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
        private val BR_V2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
        private val SR_V1 = intArrayOf(44100, 48000, 32000)
        private val SR_V2 = intArrayOf(22050, 24000, 16000)
        private val SR_V25 = intArrayOf(11025, 12000, 8000)

        private fun mpegDuration(f: RandomAccessFile, from: Long, length: Long): Long {
            if (from < 0 || from + 4 >= length) return 0L
            val window = ByteArray(minOf(8192L, length - from).toInt()).also { f.seek(from); f.readFully(it) }
            var i = 0
            while (i + 4 <= window.size) {
                if (window[i].u() == 0xFF && window[i + 1].u() and 0xE0 == 0xE0) {
                    val ver = (window[i + 1].u() shr 3) and 3
                    val layer = (window[i + 1].u() shr 1) and 3
                    val brIdx = (window[i + 2].u() shr 4) and 0xF
                    val srIdx = (window[i + 2].u() shr 2) and 3
                    if (ver != 1 && layer == 1 && brIdx in 1..14 && srIdx < 3) {
                        val v1 = ver == 3
                        val sampleRate = when (ver) { 3 -> SR_V1[srIdx]; 2 -> SR_V2[srIdx]; else -> SR_V25[srIdx] }
                        val bitrate = (if (v1) BR_V1_L3 else BR_V2_L3)[brIdx] * 1000
                        val perFrame = if (v1) 1152 else 576
                        val mono = ((window[i + 3].u() shr 6) and 3) == 3
                        val crc = window[i + 1].u() and 1 == 0
                        val side = if (v1) (if (mono) 17 else 32) else (if (mono) 9 else 17)
                        val xing = i + 4 + (if (crc) 2 else 0) + side
                        if (xing + 12 <= window.size) {
                            val tag = window.ascii(xing, 4)
                            if (tag == "Xing" || tag == "Info") {
                                if (window[xing + 7].u() and 1 != 0) {
                                    val frames = be32(window, xing + 8).toLong()
                                    if (frames > 0) return frames * perFrame * 1000L / sampleRate
                                }
                            }
                        }
                        val vbri = i + 36
                        if (vbri + 18 <= window.size && window.ascii(vbri, 4) == "VBRI") {
                            val frames = be32(window, vbri + 14).toLong()
                            if (frames > 0) return frames * perFrame * 1000L / sampleRate
                        }
                        val audioBytes = length - from - i
                        return if (bitrate > 0) audioBytes * 8L * 1000L / bitrate else 0L
                    }
                }
                i++
            }
            return 0L
        }

        // ---- FLAC
        private fun flac(f: RandomAccessFile, length: Long): AudioTags {
            var pos = 4L
            var duration = 0L
            var comments: Map<String, String> = emptyMap()
            var cover: ByteArray? = null
            while (pos + 4 <= length) {
                val h = ByteArray(4).also { f.seek(pos); f.readFully(it) }
                val last = h[0].u() and 0x80 != 0
                val type = h[0].u() and 0x7F
                val size = (h[1].u() shl 16) or (h[2].u() shl 8) or h[3].u()
                if (size > MAX_TAG || pos + 4 + size > length) break
                val data = ByteArray(size).also { f.readFully(it) }
                when (type) {
                    0 -> if (size >= 18) {
                        var v = 0L
                        for (k in 10 until 18) v = (v shl 8) or data[k].u().toLong()
                        val rate = (v ushr 44) and 0xFFFFF
                        val total = v and 0xFFFFFFFFFL
                        if (rate > 0) duration = total * 1000L / rate
                    }
                    4 -> comments = vorbisComments(data, 0)
                    6 -> if (cover == null) cover = flacPicture(data)
                }
                pos += 4 + size
                if (last) break
            }
            return AudioTags(comments["TITLE"], comments["ARTIST"] ?: comments["ALBUMARTIST"], comments["ALBUM"], cover, duration)
        }

        private fun le32(b: ByteArray, o: Int) = (b[o].u()) or (b[o + 1].u() shl 8) or (b[o + 2].u() shl 16) or (b[o + 3].u() shl 24)

        /** Комментарии Vorbis: длины little-endian, пары «КЛЮЧ=значение» в UTF-8. */
        private fun vorbisComments(b: ByteArray, start: Int): Map<String, String> {
            val out = HashMap<String, String>()
            var p = start
            if (p + 4 > b.size) return out
            p += 4 + le32(b, p)
            if (p < 0 || p + 4 > b.size) return out
            val count = le32(b, p)
            p += 4
            repeat(count.coerceIn(0, 4096)) {
                if (p + 4 > b.size) return out
                val n = le32(b, p)
                p += 4
                if (n < 0 || p + n > b.size) return out
                val line = String(b, p, n, Charsets.UTF_8)
                p += n
                val eq = line.indexOf('=')
                if (eq > 0) out.putIfAbsent(line.substring(0, eq).uppercase(), line.substring(eq + 1).trim())
            }
            return out
        }

        private fun flacPicture(d: ByteArray): ByteArray? {
            var p = 4
            if (d.size < 32) return null
            p += 4 + be32(d, p)
            if (p + 4 > d.size) return null
            p += 4 + be32(d, p)
            p += 16
            if (p + 4 > d.size) return null
            val n = be32(d, p)
            p += 4
            if (n < 0 || p + n > d.size) return null
            return d.copyOfRange(p, p + n).takeIf(::looksLikeImage)
        }

        // ---- MP4 / M4A
        private fun mp4(f: RandomAccessFile, length: Long): AudioTags {
            var pos = 0L
            while (pos + 8 <= length) {
                val h = ByteArray(8).also { f.seek(pos); f.readFully(it) }
                var size = be32(h, 0).toLong() and 0xFFFFFFFFL
                var header = 8
                if (size == 1L) {
                    val ext = ByteArray(8).also { f.readFully(it) }
                    size = 0
                    for (k in 0 until 8) size = (size shl 8) or ext[k].u().toLong()
                    header = 16
                } else if (size == 0L) size = length - pos
                if (size < header || pos + size > length) break
                if (h.ascii(4, 4) == "moov") {
                    val body = size - header
                    if (body > MAX_TAG) return AudioTags()
                    val data = ByteArray(body.toInt()).also { f.seek(pos + header); f.readFully(it) }
                    return mp4Moov(data)
                }
                pos += size
            }
            return AudioTags()
        }

        private class Atom(val type: String, val from: Int, val to: Int)

        private fun atoms(b: ByteArray, from: Int, to: Int): List<Atom> {
            val out = ArrayList<Atom>()
            var p = from
            while (p + 8 <= to) {
                val size = be32(b, p)
                if (size < 8 || p + size > to) break
                out += Atom(b.latin(p + 4, 4), p + 8, p + size)
                p += size
            }
            return out
        }

        private fun mp4Moov(b: ByteArray): AudioTags {
            var duration = 0L
            var title: String? = null
            var artist: String? = null
            var albumArtist: String? = null
            var album: String? = null
            var cover: ByteArray? = null
            for (a in atoms(b, 0, b.size)) {
                when (a.type) {
                    "mvhd" -> {
                        val v = b[a.from].toInt()
                        if (v == 0 && a.to - a.from >= 20) {
                            val scale = be32(b, a.from + 12).toLong()
                            val dur = be32(b, a.from + 16).toLong() and 0xFFFFFFFFL
                            if (scale > 0) duration = dur * 1000L / scale
                        } else if (v == 1 && a.to - a.from >= 32) {
                            val scale = be32(b, a.from + 20).toLong()
                            var dur = 0L
                            for (k in 0 until 8) dur = (dur shl 8) or b[a.from + 24 + k].u().toLong()
                            if (scale > 0) duration = dur * 1000L / scale
                        }
                    }
                    "udta" -> for (m in atoms(b, a.from, a.to)) if (m.type == "meta") {
                        for (ilst in atoms(b, m.from + 4, m.to)) if (ilst.type == "ilst") {
                            for (item in atoms(b, ilst.from, ilst.to)) {
                                val data = atoms(b, item.from, item.to).firstOrNull { it.type == "data" } ?: continue
                                val payloadFrom = data.from + 8
                                if (payloadFrom > data.to) continue
                                val payload = b.copyOfRange(payloadFrom, data.to)
                                when (item.type) {
                                    "©nam" -> title = String(payload, Charsets.UTF_8).trim()
                                    "©ART" -> artist = String(payload, Charsets.UTF_8).trim()
                                    "aART" -> albumArtist = String(payload, Charsets.UTF_8).trim()
                                    "©alb" -> album = String(payload, Charsets.UTF_8).trim()
                                    "covr" -> if (looksLikeImage(payload)) cover = payload
                                }
                            }
                        }
                    }
                }
            }
            return AudioTags(title?.ifBlank { null }, (artist ?: albumArtist)?.ifBlank { null }, album?.ifBlank { null }, cover, duration)
        }

        // ---- Ogg (Vorbis, Opus): только текстовые теги, обложка в Ogg лежит поперёк страниц
        private fun ogg(f: RandomAccessFile, length: Long): AudioTags {
            val head = ByteArray(minOf(262_144L, length).toInt()).also { f.seek(0); f.readFully(it) }
            var comments: Map<String, String> = emptyMap()
            val opus = head.indexOf("OpusTags")
            val vorbis = head.indexOf("\u0003vorbis")
            when {
                opus >= 0 -> comments = vorbisComments(head, opus + 8)
                vorbis >= 0 -> comments = vorbisComments(head, vorbis + 7)
            }
            var rate = 0
            val ident = head.indexOf("\u0001vorbis")
            if (ident >= 0 && ident + 16 <= head.size) rate = le32(head, ident + 12)
            if (head.indexOf("OpusHead") >= 0) rate = 48_000
            var duration = 0L
            if (rate > 0) {
                val tailLen = minOf(65_536L, length).toInt()
                val tail = ByteArray(tailLen).also { f.seek(length - tailLen); f.readFully(it) }
                val at = tail.lastIndexOf("OggS")
                if (at >= 0 && at + 14 <= tail.size) {
                    var granule = 0L
                    for (k in 7 downTo 0) granule = (granule shl 8) or tail[at + 6 + k].u().toLong()
                    if (granule > 0) duration = granule * 1000L / rate
                }
            }
            return AudioTags(comments["TITLE"], comments["ARTIST"] ?: comments["ALBUMARTIST"], comments["ALBUM"], null, duration)
        }

        // ---- WAV
        private fun wav(f: RandomAccessFile, length: Long): AudioTags {
            var pos = 12L
            var byteRate = 0
            var dataSize = 0L
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            while (pos + 8 <= length) {
                val h = ByteArray(8).also { f.seek(pos); f.readFully(it) }
                val size = le32(h, 4).toLong() and 0xFFFFFFFFL
                when (h.ascii(0, 4)) {
                    "fmt " -> if (size >= 12) byteRate = ByteArray(12).also { f.readFully(it) }.let { le32(it, 8) }
                    "data" -> dataSize = minOf(size, length - pos - 8)
                    "LIST" -> if (size in 4..65_536) {
                        val d = ByteArray(size.toInt()).also { f.readFully(it) }
                        if (d.ascii(0, 4) == "INFO") {
                            var p = 4
                            while (p + 8 <= d.size) {
                                val n = le32(d, p + 4)
                                if (n < 0 || p + 8 + n > d.size) break
                                val text = String(d, p + 8, n, Charsets.UTF_8).substringBefore('\u0000').trim().takeIf { it.isNotEmpty() }
                                when (d.ascii(p, 4)) { "INAM" -> title = text; "IART" -> artist = text; "IPRD" -> album = text }
                                p += 8 + n + (n and 1)
                            }
                        }
                    }
                }
                pos += 8 + size + (size and 1)
            }
            val duration = if (byteRate > 0) dataSize * 1000L / byteRate else 0L
            return AudioTags(title, artist, album, null, duration)
        }

        private fun looksLikeImage(b: ByteArray): Boolean =
            b.size > 8 && ((b[0].u() == 0xFF && b[1].u() == 0xD8) || (b[0].u() == 0x89 && b.ascii(1, 3) == "PNG") || b.ascii(0, 4) == "RIFF" || b.ascii(0, 3) == "GIF")

        private fun Byte.u() = toInt() and 0xFF

        private fun ByteArray.ascii(o: Int, n: Int) = String(this, o, minOf(n, size - o).coerceAtLeast(0), Charsets.ISO_8859_1)

        private fun ByteArray.latin(o: Int, n: Int) = ascii(o, n)

        private fun ByteArray.indexOf(s: String): Int {
            val needle = s.toByteArray(Charsets.ISO_8859_1)
            outer@ for (i in 0..size - needle.size) {
                for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }

        private fun ByteArray.lastIndexOf(s: String): Int {
            val needle = s.toByteArray(Charsets.ISO_8859_1)
            outer@ for (i in size - needle.size downTo 0) {
                for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }

        private fun be32(b: ByteArray, o: Int) = (b[o].u() shl 24) or (b[o + 1].u() shl 16) or (b[o + 2].u() shl 8) or b[o + 3].u()

        private fun syncsafe(b: ByteArray, o: Int) = ((b[o].u() and 0x7F) shl 21) or ((b[o + 1].u() and 0x7F) shl 14) or ((b[o + 2].u() and 0x7F) shl 7) or (b[o + 3].u() and 0x7F)
    }
}
