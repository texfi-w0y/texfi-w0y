package com.texfi.w0y.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.ui.screens.IslandArtistHero
import com.texfi.w0y.ui.screens.IslandCollectionHero
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.W0yTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Шапки артиста и плейлиста в Hairline New идут под строку состояния до самого верха. Строку
 * состояния (32 dp) подставляем вручную и рисуем поверх часы — видно, что обложка под ними, а
 * кнопка «назад» ниже (build/hero-shots).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class HeroTopShotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun withStatusBar(content: @androidx.compose.runtime.Composable () -> Unit) {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        // У шапки плейлиста фон течёт без конца — ждём не покоя, а просто время.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            W0yTheme(style = UiStyle.ISLAND) {
                Box(Modifier.fillMaxSize().background(LocalW0yColors.current.background)) {
                    content()
                    Text("15:51", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
                }
            }
        }
        val px = (32 * compose.activity.resources.displayMetrics.density).toInt()
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px, 0, 0)).build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets) }
        compose.mainClock.advanceTimeBy(900)
    }

    private fun shot(name: String) {
        val dir = File("build/hero-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun artist() {
        withStatusBar { IslandArtistHero(thumbnailUrl = null, title = "dabbackwood", subtitle = null, onBack = {}) }
        shot("artist")
    }

    @Test
    fun playlist() {
        val songs = (1..6).map { SongItem(id = "s$it", title = "Трек $it", artist = "VILLIAN") }
        withStatusBar {
            IslandCollectionHero(title = "w//y", kind = "Плейлист", owner = "mistqwk", note = null, coverUrl = null, songs = songs, onBack = {}, onPlay = {}, onShuffle = {}, onDownload = {}, onCustomize = {})
        }
        shot("playlist")
    }
}
