package com.texfi.w0y.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R
import com.texfi.w0y.data.UiStyle

/**
 * Пиксельный шрифт — только заголовки, лейблы секций и крупные числа.
 * Всё, что читают, а не разглядывают (названия треков, пункты настроек,
 * описания), набирается системным: Press Start 2P на 12sp нечитаем.
 *
 * В плавном стиле те же роли набираются гротеском — системным Roboto
 * (Apache 2.0): он уже есть в телефоне, ничего не скачивается и не
 * утяжеляет приложение. Экраны берут стили по роли и не знают, какой
 * стиль включён.
 */
val PixelFamily = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))
val BodyFamily = FontFamily.Default

private val PixelTitleStyle = TextStyle(fontFamily = PixelFamily, fontSize = 15.sp, lineHeight = 24.sp)
private val PixelScreenTitleStyle = TextStyle(fontFamily = PixelFamily, fontSize = 19.sp, lineHeight = 26.sp)
private val PixelSectionLabelStyle =
    TextStyle(fontFamily = PixelFamily, fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = 1.sp)
private val PixelBigNumberStyle = TextStyle(fontFamily = PixelFamily, fontSize = 22.sp, lineHeight = 30.sp)

private val SmoothTitleStyle =
    TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp)
private val SmoothScreenTitleStyle =
    TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    )
private val SmoothSectionLabelStyle =
    TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp,
    )
private val SmoothBigNumberStyle =
    TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp)

private fun smooth(style: UiStyle) = style == UiStyle.SMOOTH
private fun hair(style: UiStyle) = style == UiStyle.HAIRLINE

/** Заголовок блока. */
val PixelTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = LocalStyleTokens.current.style.let { if (hair(it)) HairTitleStyle else if (smooth(it)) SmoothTitleStyle else PixelTitleStyle }

/** Заголовок экрана — крупнее внутренних: он задаёт верх страницы. */
val PixelScreenTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = LocalStyleTokens.current.style.let { if (hair(it)) HairScreenTitleStyle else if (smooth(it)) SmoothScreenTitleStyle else PixelScreenTitleStyle }

val PixelSectionLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = LocalStyleTokens.current.style.let { if (hair(it)) HairLabelStyle else if (smooth(it)) SmoothSectionLabelStyle else PixelSectionLabelStyle }

val PixelBigNumber: TextStyle
    @Composable @ReadOnlyComposable
    get() = LocalStyleTokens.current.style.let { if (hair(it)) HairBigNumberStyle else if (smooth(it)) SmoothBigNumberStyle else PixelBigNumberStyle }

/** Типографика Material под стиль: заголовочные роли меняются, текстовые общие. */
fun typographyFor(style: UiStyle): Typography {
    val title = if (hair(style)) HairTitleStyle else if (smooth(style)) SmoothTitleStyle else PixelTitleStyle
    return Typography(
        titleLarge = title,
        titleMedium = if (hair(style)) HairTitleStyle.copy(fontSize = 14.sp, lineHeight = 20.sp) else if (smooth(style)) title.copy(fontSize = 16.sp, lineHeight = 22.sp) else title.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelSmall = if (hair(style)) HairLabelStyle else if (smooth(style)) SmoothSectionLabelStyle else PixelSectionLabelStyle,
        bodyLarge = TextStyle(fontFamily = BodyFamily, fontSize = 15.sp, lineHeight = 21.sp),
        bodyMedium = TextStyle(fontFamily = BodyFamily, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = BodyFamily, fontSize = 12.sp, lineHeight = 17.sp),
        labelMedium = if (hair(style)) HairLabelStyle.copy(letterSpacing = 1.sp) else TextStyle(fontFamily = BodyFamily, fontSize = 11.sp, lineHeight = 15.sp),
    )
}

val W0yTypography = typographyFor(UiStyle.PIXEL)
