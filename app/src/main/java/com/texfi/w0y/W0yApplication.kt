package com.texfi.w0y

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.texfi.w0y.data.LocalePrefs
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class W0yApplication : Application() {
    /**
     * Язык применяется и к контексту приложения.
     *
     * Через него строки берут сервис воспроизведения и репозитории — если
     * обернуть только активити, экран будет на выбранном языке, а
     * сообщения об ошибках и уведомление плеера останутся на системном.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocalePrefs.wrap(base))
    }

    /**
     * Смена языка или размера шрифта в системе приходит сюда — выбранный
     * язык нужно наложить заново, иначе он потеряется до перезапуска.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocalePrefs.apply(this)
    }

    override fun onCreate() {
        super.onCreate()
        com.texfi.w0y.playback.localMusicDir = com.texfi.w0y.data.LocalMusic.dir(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
