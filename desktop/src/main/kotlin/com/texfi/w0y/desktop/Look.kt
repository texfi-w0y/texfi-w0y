package com.texfi.w0y.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Состояние оформления: меняется в настройках, всё нарисованное перерисовывается само. */
object ThemeState {
    var mode by mutableStateOf(ThemeMode.DARK)
    var accent by mutableStateOf(Accent.SAND)
    var custom by mutableStateOf(0xFFA06CFF.toInt())
    var style by mutableStateOf(UiStyle.PIXEL)
    var glass by mutableStateOf(true)
}

/**
 * Что отличает стили. Компоненты не спрашивают «какой стиль» у экранов —
 * форма, рамка и тень приходят отсюда, поэтому новый экран сразу рисуется в обоих.
 */
object Look {
    val smooth: Boolean get() = ThemeState.style == UiStyle.SMOOTH

    /** Стекло: панели полупрозрачные и размывают то, что под ними. */
    val glass: Boolean get() = smooth && ThemeState.glass

    /** Скругление карточек: у Pixel почти квадрат, у Smooth крупно, как на телефоне. */
    val card: Dp get() = if (smooth) 22.dp else 6.dp
    val control: Dp get() = if (smooth) 50.dp else 4.dp
    val cover: Dp get() = if (smooth) 12.dp else 4.dp
    val row: Dp get() = if (smooth) 14.dp else 6.dp
}

/**
 * Токены TexFi: графитовый фон, акцент и вторичный — те же значения, что в
 * Android-теме. Свойства читаются при отрисовке, поэтому смена темы
 * подхватывается сразу.
 */
object C {
    private val light get() = ThemeState.mode == ThemeMode.LIGHT
    private val oled get() = ThemeState.mode == ThemeMode.OLED
    private val accent get() = ThemeState.accent

    val Blue: Color get() = if (accent == Accent.CUSTOM) Color(ThemeState.custom) else Color(accent.accent)
    val BlueDeep: Color get() = if (accent == Accent.CUSTOM) lerp(Color(ThemeState.custom), Color.Black, 0.45f) else Color(accent.deep)
    val BlueLight = Color(0xFF7FB5FF)
    val Sand: Color get() = if (light && accent == Accent.BLUE) Color(0xFFB98232) else Color(accent.secondary)
    val SandDeep = Color(0xFF8A5F26)
    val Background: Color get() = if (light) Color(0xFFF7F1E4) else if (oled) Color(0xFF000000) else Color(0xFF15151B)
    val Surface: Color get() = if (light) Color(0xFFFFFDF7) else if (oled) Color(0xFF000000) else Color(0xFF1F1F27)
    val SurfaceHigh: Color get() = if (light) Color(0xFFEDE4D2) else if (oled) Color(0xFF101014) else Color(0xFF2A2A34)
    val Border: Color get() = if (light) Color(0xFFD5C7AC) else if (oled) Color(0xFF2C2C36) else Color(0xFF383845)
    val Shadow: Color get() = if (light) Color(0xFFC9B99B) else if (oled) Color(0xFF17171D) else Color(0xFF0A0A0E)
    val Text: Color get() = if (light) Color(0xFF191921) else Color(0xFFF6F1E5)
    val Muted: Color get() = if (light) Color(0xFF6B6455) else Color(0xFF8A8A96)
    val Danger = Color(0xFFFF5A5A)

    /** Текст на заливке акцентом — светлый или тёмный по контрасту, как на телефоне. */
    val OnAccent: Color
        get() {
            val bg = Blue
            val luminance = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
            return if (luminance > 0.55f) Color(0xFF111116) else Color(0xFFF6F1E5)
        }

    /** Фиолетовое пятно стеклянного фона, как на телефоне. */
    val GlassViolet = Color(0xFF8A5CF6)

    /** Серое стекло групп настроек: светлее фона, как в iOS. */
    val GlassGroup: Color get() = if (light) Surface else Color(0xFF2E2E33)

    /** Дымка стеклянных панелей: тёмная или белая по теме. */
    val Haze: Color get() = if (light) Color.White else Color(0xFF0E0E11)

    val isLight: Boolean get() = light
}

val PixelFont = FontFamily(Font(resource = "press_start_2p.ttf"))
