package com.texfi.w0y.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.PlayerLayout
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.ui.screens.CoverPlayerPage
import com.texfi.w0y.ui.screens.IslandTrackPanel
import com.texfi.w0y.ui.screens.HairTitleBlock
import com.texfi.w0y.ui.screens.HairTransport
import com.texfi.w0y.ui.screens.IslandPlayerHeader
import com.texfi.w0y.ui.screens.IslandPlayerTabs
import com.texfi.w0y.ui.screens.IslandTitleRow
import com.texfi.w0y.ui.theme.W0yTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Кадры основного плеера Hairline New (постер) — посмотреть глазами без телефона (build/player-shots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class PlayerShotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val song = SongItem(id = "s1", title = "Не забывай меня", artist = "mistqkw", album = "Свэг", durationText = "3:12", albumId = "MPRE1")

    @Composable
    private fun Cover() {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF6E4A2E), Color(0xFF1C2A44), Color(0xFF0E0E12)))))
    }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1500)
        val dir = File("build/player-shots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private var doubles = 0

    @Test
    fun poster() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            W0yTheme(style = UiStyle.ISLAND) {
                var panel by remember { mutableStateOf(false) }
                CoverPlayerPage(
                    song = song,
                    poster = true,
                    isPlaying = true,
                    positionMs = 74_000,
                    durationMs = 192_000,
                    stage = { Cover() },
                    number = "№ 11 / 40",
                    soundLine = null,
                    header = { IslandPlayerHeader(onCollapse = {}, onMenu = {}, videoAvailable = true, videoOn = false, onToggleVideo = {}) },
                    titleBlock = { m -> IslandTitleRow(true, {}, m) { tm -> HairTitleBlock(song, PlayerLayout.RING, null, tm) } },
                    actions = {},
                    transport = {
                        HairTransport(playing = true, shuffle = false, repeatActive = false, repeatOne = false, onShuffle = {}, onPrevious = {}, onToggle = {}, onNext = {}, onRepeat = {}, playSize = 76.dp)
                    },
                    plate = { IslandPlayerTabs(lyricsOn = false, onLyrics = {}, onQueue = {}, onSimilar = {}) },
                    onCoverTap = {},
                    onSwipe = {},
                    onDrag = {},
                    onDragEnd = {},
                    onCoverDoubleTap = { doubles++; true },
                    onCoverLongPress = { panel = true },
                )
                IslandTrackPanel(
                    visible = panel,
                    song = song,
                    pinned = true,
                    inPlaylist = true,
                    sharing = false,
                    sleepLeftMs = 754_000,
                    onPin = {}, onAdd = {}, onDownload = {}, onShare = {}, onCopyLink = {}, onSleep = {}, onSound = {}, onLook = {},
                    onDismiss = { panel = false },
                )
            }
        }
        shot("after")
        // Два касания по обложке — сердце посередине постера.
        compose.onRoot().performTouchInput { doubleClick(Offset(centerX, height * 0.3f)) }
        compose.mainClock.advanceTimeBy(140)
        org.junit.Assert.assertEquals("два касания засчитаны", 1, doubles)
        compose.mainClock.advanceTimeBy(1000)
        // Удержание — панель «Трек».
        compose.onRoot().performTouchInput { longClick(Offset(centerX, height * 0.3f)) }
        shot("track-panel")
    }

    @Test
    fun trackMenuMarksPlaylists() {
        compose.mainClock.autoAdvance = false
        val removed = mutableListOf<Long>()
        compose.setContent {
            W0yTheme(style = UiStyle.ISLAND) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.texfi.w0y.ui.components.LocalPlaylistMembership provides
                        com.texfi.w0y.ui.components.PlaylistMembership(of = { kotlinx.coroutines.flow.flowOf(setOf(2L, 4L)) }, remove = { id, _ -> removed += id }),
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF0E0E12))) {
                        com.texfi.w0y.ui.components.AddToPlaylistPanel(
                            song = song,
                            playlists = listOf(1L to "Для зала", 2L to "Ночь", 3L to "Старое", 4L to "В дорогу"),
                            newName = null,
                            onNewNameChange = {},
                            onPick = {},
                            onCreate = {},
                            onDismiss = {},
                            onPlayNext = {},
                            onEnqueue = {},
                            onDownload = {},
                        )
                    }
                }
            }
        }
        shot("track-menu")
        compose.onNode(androidx.compose.ui.test.hasText("Ночь")).performClick()
        compose.mainClock.advanceTimeBy(400)
        org.junit.Assert.assertEquals("нажатие по плейлисту с галочкой убирает трек", listOf(2L), removed)
    }
}
