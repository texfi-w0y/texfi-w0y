package com.texfi.w0y

import android.Manifest
import android.os.Build
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.texfi.w0y.data.LocalePrefs
import com.texfi.w0y.data.YouTubeRepository
import com.texfi.w0y.playback.PlayerConnection
import javax.inject.Inject
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.texfi.w0y.ui.shell.ShellViewModel
import com.texfi.w0y.ui.shell.StyleRevealHost
import com.texfi.w0y.ui.shell.W0yShell
import com.texfi.w0y.ui.theme.W0yTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playerConnection: PlayerConnection

    @Inject lateinit var youtube: YouTubeRepository

    // Язык подставляется до создания экрана: ресурсы читаются уже при
    // первом кадре, и менять их позже — значит показать один кадр на
    // системном языке, а следующий на выбранном.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePrefs.wrap(newBase))
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Системный сплэш снимается сразу: он только закрывает белую вспышку
        // холодного старта. Задерживать его ради анимации нельзя — скорость
        // запуска здесь дороже эффекта.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        playerConnection.connect()
        // Без разрешения Android 13+ просто не покажет медиа-уведомление —
        // музыка играть будет, а управлять ею с шторки не выйдет.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val shellViewModel: ShellViewModel = hiltViewModel()
            val theme by shellViewModel.theme.collectAsStateWithLifecycle()
            val accent by shellViewModel.accent.collectAsStateWithLifecycle()
            val customAccent by shellViewModel.customAccent.collectAsStateWithLifecycle()
            val style by shellViewModel.uiStyle.collectAsStateWithLifecycle()
            val glass by shellViewModel.smoothGlass.collectAsStateWithLifecycle()
            val lookState by shellViewModel.look.collectAsStateWithLifecycle()
            val motion = com.texfi.w0y.ui.components.rememberMotionLevel(lookState.motion)
            val look =
                androidx.compose.runtime.remember(lookState, motion) {
                    com.texfi.w0y.ui.theme.HairlineLook(
                        weight = lookState.weight,
                        corners = lookState.corners,
                        coverShape = lookState.cover,
                        layout = lookState.layout,
                        caps = lookState.caps,
                        density = lookState.density,
                        motion = motion,
                        iconStyle = lookState.icons,
                    )
                }
            W0yTheme(mode = theme, accent = accent, customAccent = customAccent, style = style, glass = glass, look = look) {
                // Смена стиля — снимок старого вида и круг нового поверх, без
                // пересоздания экрана: музыка и состояние экранов не трогаются.
                StyleRevealHost(current = style, onApply = shellViewModel::setUiStyle) {
                    W0yShell(shellViewModel)
                }
            }
        }
        // Прогрев извлечения (клиенты, ключи плеера) — после первого кадра и
        // в фоне: первое нажатие на трек не ждёт этой подготовки, а запуск
        // приложения не ждёт её саму.
        window.decorView.post { youtube.prewarm() }
    }

    /**
     * Возвращение в приложение — повод перечитать состояние плеера.
     *
     * Пока экран не виден, связь с сервисом может оборваться: другое
     * приложение забирает звук, воспроизведение встаёт, сервис
     * останавливается — и сообщить об этом уже некому. Без этой строки
     * пользователь возвращается к кнопке «пауза», хотя музыка не играет.
     */
    override fun onStart() {
        super.onStart()
        playerConnection.refresh()
    }
}
