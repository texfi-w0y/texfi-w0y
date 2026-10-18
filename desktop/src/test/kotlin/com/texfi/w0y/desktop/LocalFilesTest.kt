package com.texfi.w0y.desktop

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalFilesTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4, 5, 6)

    private fun syncsafe(n: Int) = byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())

    private fun frame(id: String, body: ByteArray, v24: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray())
        out.write(if (v24) syncsafe(body.size) else byteArrayOf(0, 0, (body.size shr 8).toByte(), body.size.toByte()))
        out.write(byteArrayOf(0, 0))
        out.write(body)
        return out.toByteArray()
    }

    private fun id3(v24: Boolean, vararg frames: ByteArray): ByteArray {
        val body = frames.fold(ByteArray(0)) { a, b -> a + b }
        return "ID3".toByteArray() + byteArrayOf(if (v24) 4 else 3, 0, 0) + syncsafe(body.size) + body
    }

    private fun utf16(text: String) = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)

    @Test
    fun id3ReadsTextInAllEncodingsAndCover() {
        val pic = byteArrayOf(0) + "image/jpeg".toByteArray() + byteArrayOf(0, 3, 0) + jpeg
        val data = id3(
            false,
            frame("TIT2", utf16("Лифт Упал"), false),
            frame("TPE1", byteArrayOf(3) + "VILLIAN".toByteArray(), false),
            frame("TALB", byteArrayOf(0) + "Album".toByteArray(), false),
            frame("APIC", pic, false),
        )
        val file = Files.createTempFile("w0y", ".mp3").toFile().apply { writeBytes(data) }
        val tags = AudioTags.read(file)
        assertEquals("Лифт Упал", tags.title)
        assertEquals("VILLIAN", tags.artist)
        assertEquals("Album", tags.album)
        assertContentEquals(jpeg, tags.cover)
        file.delete()
    }

    @Test
    fun id3v24SyncsafeFrameSizesAreRead() {
        val data = id3(true, frame("TIT2", byteArrayOf(3) + "x".repeat(200).toByteArray(), true), frame("TPE1", byteArrayOf(3) + "Artist".toByteArray(), true))
        val file = Files.createTempFile("w0y", ".mp3").toFile().apply { writeBytes(data) }
        val tags = AudioTags.read(file)
        assertEquals(200, tags.title?.length)
        assertEquals("Artist", tags.artist)
        file.delete()
    }

    @Test
    fun cbrMp3DurationComesFromBitrate() {
        // 128 кбит/с, 44,1 кГц, слой III; 16 000 байт звука — ровно секунда.
        val frameHead = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
        val data = frameHead + ByteArray(16_000 - 4)
        val file = Files.createTempFile("w0y", ".mp3").toFile().apply { writeBytes(data) }
        val ms = AudioTags.read(file).durationMs
        assertTrue(ms in 950..1050, "длительность $ms мс")
        file.delete()
    }

    @Test
    fun flacTagsAndDurationAreRead() {
        fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
        fun block(type: Int, last: Boolean, body: ByteArray) = byteArrayOf((type or if (last) 0x80 else 0).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()) + body
        // 44 100 Гц, 441 000 отсчётов = 10 секунд.
        val packed = (44_100L shl 44) or (1L shl 41) or (15L shl 36) or 441_000L
        val streamInfo = ByteArray(10) + ByteArray(8) { ((packed shr (56 - 8 * it)) and 0xFF).toByte() } + ByteArray(16)
        val vendor = "w0y".toByteArray()
        val comments = listOf("TITLE=Песня", "ARTIST=Автор", "ALBUM=Альбом").map { it.toByteArray() }
        val vorbis = le(vendor.size) + vendor + le(comments.size) + comments.fold(ByteArray(0)) { a, c -> a + le(c.size) + c }
        val data = "fLaC".toByteArray() + block(0, false, streamInfo) + block(4, true, vorbis)
        val file = Files.createTempFile("w0y", ".flac").toFile().apply { writeBytes(data) }
        val tags = AudioTags.read(file)
        assertEquals("Песня", tags.title)
        assertEquals("Автор", tags.artist)
        assertEquals("Альбом", tags.album)
        assertEquals(10_000L, tags.durationMs)
        file.delete()
    }

    @Test
    fun garbageFileGivesEmptyTagsInsteadOfCrash() {
        val file = Files.createTempFile("w0y", ".mp3").toFile().apply { writeBytes(ByteArray(300) { (it * 7).toByte() }) }
        val tags = AudioTags.read(file)
        assertEquals(null, tags.title)
        file.delete()
    }

    @Test
    fun localIdsAndNames() {
        assertTrue(LocalFiles.isLocal("local:abc.mp3"))
        assertEquals(false, LocalFiles.isLocal("dQw4w9WgXcQ"))
        assertEquals("abc", LocalFiles.keyOf("local:abc.mp3"))
        assertEquals("Мой трек", LocalFiles.titleFromName("Мой_трек.mp3"))
        assertEquals("3:47", LocalFiles.durationText(227_000))
        assertEquals("1:02:10", LocalFiles.durationText(3_730_000))
    }

    @Test
    fun importCopiesFileReadsTagsAndKeepsCover() {
        System.setProperty("w0y.data", Files.createTempDirectory("w0y-data").toString())
        val pic = byteArrayOf(0) + "image/jpeg".toByteArray() + byteArrayOf(0, 3, 0) + jpeg
        val src = Files.createTempFile("my_song", ".mp3").toFile().apply {
            writeBytes(id3(false, frame("TIT2", byteArrayOf(3) + "Title".toByteArray(), false), frame("APIC", pic, false)))
        }
        val song = LocalFiles.import(src)
        assertTrue(LocalFiles.isLocal(song.id))
        assertEquals("Title", song.title)
        assertTrue(LocalFiles.file(song.id).isFile)
        assertNotNull(song.thumbnailUrl)
        LocalFiles.delete(song.id)
        assertEquals(false, LocalFiles.file(song.id).exists())
        src.delete()
    }

    @Test
    fun appImportsEditsAndRemovesAFileEverywhere() {
        System.setProperty("w0y.data", Files.createTempDirectory("w0y-data").toString())
        System.setProperty("w0y.ao", "null")
        val app = AppState(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined), Yt(), Store())
        val src = Files.createTempFile("track_one", ".mp3").toFile().apply { writeBytes(id3(false, frame("TIT2", byteArrayOf(3) + "Old".toByteArray(), false))) }
        app.importLocal(listOf(src))
        val deadline = System.currentTimeMillis() + 5000
        while (app.lib.localFiles.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val song = app.localSongs.single()
        assertEquals("Old", song.title)
        // Файл ложится в плейлист и в лайки; правка должна поменять все копии, а не только «Мои файлы».
        app.createPlaylist("Мой")
        val pl = app.lib.playlists.single()
        app.addToPlaylist(pl.id, song)
        app.toggleLike(song)
        app.editLocal(song.id, "New", "Artist", "Alb", null, false)
        val deadline2 = System.currentTimeMillis() + 5000
        while (app.lib.localFiles.single().title != "New" && System.currentTimeMillis() < deadline2) Thread.sleep(20)
        assertEquals("New", app.lib.playlists.single().songs.single().title)
        assertEquals("Artist", app.lib.liked.single().artist)
        app.removeLocal(song.id)
        assertTrue(app.lib.localFiles.isEmpty() && app.lib.liked.isEmpty() && app.lib.playlists.single().songs.isEmpty())
        assertEquals(false, LocalFiles.file(song.id).exists())
        src.delete()
    }
}
