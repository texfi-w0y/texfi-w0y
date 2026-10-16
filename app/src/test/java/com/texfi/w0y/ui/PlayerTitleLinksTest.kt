package com.texfi.w0y.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.texfi.w0y.data.ArtistLink
import com.texfi.w0y.data.PlayerLayout
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.ui.screens.HairTitleBlock
import com.texfi.w0y.ui.theme.W0yTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Название в плеере ведёт в альбом, имя исполнителя — к исполнителю. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerTitleLinksTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun artistAndAlbumOpen() {
        val opened = mutableListOf<String>()
        val song = SongItem(id = "a", title = "Катюха", artist = "whitek3d", artistId = "UC1", albumId = "MPRE1", album = "Альбом")
        rule.setContent {
            W0yTheme {
                HairTitleBlock(song, PlayerLayout.COVER, null, onArtist = { a: ArtistLink -> opened += "artist:${a.id}" }, onAlbum = { opened += "album" })
            }
        }
        rule.onNodeWithTag("player-artist-0").performClick()
        rule.onNodeWithTag("player-title").performClick()
        rule.waitForIdle()
        assertEquals(listOf("artist:UC1", "album"), opened)
    }
}
