package com.texfi.w0y

import android.graphics.Bitmap
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.widget.W0yWidget
import com.texfi.w0y.widget.WidgetSize
import com.texfi.w0y.widget.WidgetState
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Виджет собирается и надувается во всех размерах и состояниях, в том числе на пустых и устаревших данных. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRenderTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun everySizeAndStateInflates() {
        val cover = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val states =
            listOf(
                WidgetState(), // пусто: ничего не играло
                WidgetState(title = "t", artist = "a", playing = true, pid = android.os.Process.myPid()),
                WidgetState(title = "t", artist = "a", playing = false, pid = android.os.Process.myPid()),
                WidgetState(title = "t", artist = "a", playing = true, error = true, pid = android.os.Process.myPid()),
                // устаревшее: записал процесс, которого уже нет
                WidgetState(title = "t", artist = "a", playing = true, pid = 1),
                WidgetState(title = "", artist = "", playing = false, coverPath = "/no/such/file.png"),
            )
        for (size in WidgetSize.entries) {
            for (state in states) {
                for (c in listOf(cover, null)) {
                    val views = W0yWidget.build(context, state, c, size)
                    assertNotNull(views.apply(context, FrameLayout(context)))
                }
            }
        }
    }
}
