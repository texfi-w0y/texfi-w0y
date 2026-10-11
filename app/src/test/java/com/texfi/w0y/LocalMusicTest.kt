package com.texfi.w0y

import com.texfi.w0y.data.LocalMusic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicTest {
    @Test
    fun `свои файлы узнаются по префиксу`() {
        assertTrue(LocalMusic.isLocal("local:abc.mp3"))
        assertFalse(LocalMusic.isLocal("dQw4w9WgXcQ"))
        assertFalse(LocalMusic.isLocal(null))
    }

    @Test
    fun `длительность и название из имени файла`() {
        assertEquals("3:47", LocalMusic.durationText(227_000))
        assertEquals("1:02:11", LocalMusic.durationText(3_731_000))
        assertEquals("my song", LocalMusic.titleFromName("my_song.wav"))
    }
}
