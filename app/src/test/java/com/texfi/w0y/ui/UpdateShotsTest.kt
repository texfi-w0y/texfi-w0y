package com.texfi.w0y.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.data.UpdateInfo
import com.texfi.w0y.data.UpdateState
import com.texfi.w0y.ui.shell.UpdateRoute
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.W0yTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Кадры загрузки обновления по этапам (build/update-shots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-w360dp-h300dp-xxhdpi")
class UpdateShotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf<UpdateState>(UpdateState.Idle)

    private fun shot(name: String) {
        val dir = File("build/update-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun stages() {
        val info = UpdateInfo(tag = "v0.0.4-beta-5", notes = "", apkUrl = "", apkSize = 33_000_000, sha256 = null, sumsUrl = null)
        state = UpdateState.Available(info)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            W0yTheme(style = UiStyle.ISLAND) {
                Box(Modifier.background(LocalW0yColors.current.surface).padding(24.dp)) {
                    UpdateRoute(state, from = "0.0.4 beta-4", to = info.label, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        shot("1-available")
        state = UpdateState.Downloading(info, done = 14_100_000, total = 33_000_000)
        compose.mainClock.advanceTimeBy(500)
        shot("2-downloading")
        state = UpdateState.Downloading(info, done = 0, total = 0)
        compose.mainClock.advanceTimeBy(700)
        shot("3-unknown-size")
        state = UpdateState.Ready(info, File("x.apk"))
        compose.mainClock.advanceTimeBy(320)
        shot("4-finale")
        compose.mainClock.advanceTimeBy(1200)
        shot("5-ready")
    }
}
