package com.texfi.w0y.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.SettingsRepository
import com.texfi.w0y.data.StartTab
import com.texfi.w0y.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.texfi.w0y.playback.DownloadsRepository
import com.texfi.w0y.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** То, из чего собирается вид Hairline; уровень движения решается уже в окне, по системе. */
data class LookState(
    val weight: com.texfi.w0y.data.LineWeight = com.texfi.w0y.data.LineWeight.THIN,
    val corners: com.texfi.w0y.data.CornerStyle = com.texfi.w0y.data.CornerStyle.SHARP,
    val cover: com.texfi.w0y.data.CoverShape = com.texfi.w0y.data.CoverShape.ROUNDED,
    val layout: com.texfi.w0y.data.PlayerLayout = com.texfi.w0y.data.PlayerLayout.RING,
    val caps: com.texfi.w0y.data.CapsMode = com.texfi.w0y.data.CapsMode.TITLES_LABELS,
    val density: com.texfi.w0y.data.Density = com.texfi.w0y.data.Density.COMFORTABLE,
    val motion: com.texfi.w0y.data.MotionMode = com.texfi.w0y.data.MotionMode.AUTO,
    val icons: com.texfi.w0y.data.IconStyle = com.texfi.w0y.data.IconStyle.LINE,
)

@HiltViewModel
class ShellViewModel @Inject constructor(
    val player: PlayerConnection,
    private val settings: SettingsRepository,
    val downloads: DownloadsRepository,
) : ViewModel() {
    val smoothGlass: StateFlow<Boolean> =
        settings.settings
            .map { it.smoothGlass }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setSmoothGlass(value: Boolean) = viewModelScope.launch { settings.setSmoothGlass(value) }

    /** null, пока настройки не прочитаны: показывать приветствие вслепую нельзя. */
    val welcomeSeen: StateFlow<Boolean?> =
        settings.settings
            .map { it.welcomeSeen }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeWelcome() = viewModelScope.launch { settings.setWelcomeSeen(true) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }

    fun setStartTab(tab: StartTab) = viewModelScope.launch { settings.setStartTab(tab) }

    /** null, пока настройки не прочитаны: до этого экран не переключаем. */
    val startTab: StateFlow<StartTab?> =
        settings.settings
            .map { it.startTab }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Куда идти на старте: выбранная вкладка или последняя открытая. null, пока не прочитано. */
    val launchTab: StateFlow<StartTab?> =
        settings.settings
            .map { if (it.startTab == StartTab.LAST) it.lastTab else it.startTab }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setLastTab(tab: StartTab) = viewModelScope.launch { settings.setLastTab(tab) }

    val compactRows: StateFlow<Boolean> =
        settings.settings
            .map { it.compactRows }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val look: StateFlow<LookState> =
        settings.settings
            .map {
                LookState(it.lineWeight, it.corners, it.coverShape, it.playerLayout, it.caps, it.density, it.motion, it.iconStyle)
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, LookState())

    val hapticLevel: StateFlow<com.texfi.w0y.data.HapticLevel> =
        settings.settings
            .map { it.hapticLevel }
            .stateIn(viewModelScope, SharingStarted.Eagerly, com.texfi.w0y.data.HapticLevel.FULL)

    val swipeDismiss: StateFlow<Boolean> =
        settings.settings
            .map { it.swipeDismiss }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val animatedBackground: StateFlow<Boolean> =
        settings.settings
            .map { it.animatedBackground }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val accent: StateFlow<Accent> =
        settings.settings
            .map { it.accent }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Accent.SAND)

    val customAccent: StateFlow<Int> =
        settings.settings
            .map { it.customAccent }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0xFFA06CFF.toInt())

    val haptics: StateFlow<Boolean> =
        settings.settings
            .map { it.haptics }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val uiStyle: StateFlow<com.texfi.w0y.data.UiStyle> =
        settings.settings
            .map { it.uiStyle }
            .stateIn(viewModelScope, SharingStarted.Eagerly, com.texfi.w0y.data.UiStyle.HAIRLINE)

    /** null, пока настройки не прочитаны: выбор стиля вслепую не показываем. */
    val stylePicked: StateFlow<Boolean?> =
        settings.settings
            .map { it.stylePicked }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setUiStyle(style: com.texfi.w0y.data.UiStyle) = viewModelScope.launch { settings.setUiStyle(style) }

    fun setAccent(accent: Accent) = viewModelScope.launch { settings.setAccent(accent) }

    /** Выбор стиля закрыт — выбором или пропуском; больше не показываем. */
    fun completeStylePick() = viewModelScope.launch { settings.setStylePicked(true) }

    val theme: StateFlow<ThemeMode> =
        settings.settings
            .map { it.theme }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
}
