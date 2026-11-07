package com.texfi.w0y

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.texfi.w0y.data.BackdropMode
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.UiStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Смена стиля не сбрасывает фон, выбранный самим человеком; заменяются только «контуры» по умолчанию. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StyleKeepsBackdropTest {
    private val repo = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())

    /** Файл настроек общий на весь прогон — возвращаем как было, чтобы не задеть другие тесты. */
    @After
    fun restore() =
        runBlocking {
            val d = com.texfi.w0y.data.W0ySettings()
            repo.setUiStyle(d.uiStyle)
            repo.setBackdropMode(d.backdropMode)
            repo.setBackdropImage(null)
            repo.setPlayerLayout(d.playerLayout)
            repo.setCaps(d.caps)
        }

    @Test
    fun ownPhotoSurvivesSwitchingStyles() =
        runBlocking {
            repo.setBackdropImage("/data/backdrop.jpg")
            repo.setBackdropMode(BackdropMode.PHOTO)
            repo.setUiStyle(UiStyle.ISLAND)
            repo.setUiStyle(UiStyle.entries.first { it != UiStyle.ISLAND })
            repo.setUiStyle(UiStyle.ISLAND)
            val s = repo.settings.first()
            assertEquals(BackdropMode.PHOTO, s.backdropMode)
            assertEquals("/data/backdrop.jpg", s.backdropImage)
        }

    @Test
    fun defaultContoursBecomeDotsInHairlineNew() =
        runBlocking {
            repo.setBackdropMode(BackdropMode.TOPO)
            repo.setUiStyle(UiStyle.ISLAND)
            assertEquals(BackdropMode.GRID, repo.settings.first().backdropMode)
        }
}
