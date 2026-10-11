package com.texfi.w0y.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.texfi.w0y.data.UiStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.ThemeMode

/**
 * Токены, которых нет в Material: пиксельная граница, офсетная тень и
 * собственный акцент приложения. Material-схема остаётся для компонентов,
 * которые берём готовыми, всё пиксельное читает эти значения.
 */
@Immutable
data class W0yColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val shadow: Color,
    val accent: Color,
    val accentDeep: Color,
    val secondary: Color,
    /** Тень песочной заливки: как accentDeep у синей. */
    val secondaryDeep: Color,
    val text: Color,
    val textMuted: Color,
    /** Стеклянная тема: скругления, полупрозрачность, без офсетной тени. */
    val glass: Boolean = false,
    /** Подпись на заливке акцентом — выводится по контрасту, не задаётся руками. */
    val onAccent: Color = Color.White,
    /** Текст цветом акцента, подтянутый до читаемого на фоне. */
    val accentText: Color = TexFiBlue,
    /** Прогресс и время — всегда синий TexFi, какой бы ни был акцент действия. */
    val progress: Color = TexFiBlue,
    /** Контур интерактивных элементов Hairline; у других стилей совпадает с рамкой. */
    val lineStrong: Color = Color.Unspecified,
) {
    /** Контур интерактивного элемента: у Hairline ярче разделителя, у остальных — рамка. */
    val outline: Color get() = if (lineStrong == Color.Unspecified) border else lineStrong
}

/**
 * Тёмная — графит, а не чернота: карточки заметно светлее фона. Раньше
 * фон был #0D0D11, и на экране телефона тёмная тема от чёрной не
 * отличалась ничем — это было замечено первым же посторонним тестером.
 */
private val DarkColors =
    W0yColors(
        background = Color(0xFF15151B),
        surface = Color(0xFF1F1F27),
        surfaceHigh = Color(0xFF2A2A34),
        border = Color(0xFF383845),
        shadow = Color(0xFF0A0A0E),
        accent = TexFiBlue,
        accentDeep = TexFiBlueDeep,
        secondary = TexFiSand,
        secondaryDeep = TexFiSandDeep,
        text = W0yText,
        textMuted = W0yTextMuted,
    )

/**
 * Чёрная для AMOLED: чёрный не только фон, но и карточки. Серые подложки
 * держат пиксели включёнными и съедают весь смысл темы; карточку здесь
 * очерчивает только рамка, а тень — тонкая ступенька цвета рамки.
 */
private val OledColors =
    DarkColors.copy(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceHigh = Color(0xFF101014),
        border = Color(0xFF2C2C36),
        shadow = Color(0xFF17171D),
    )

/**
 * Стекло — экспериментальная тема для тех, кому пиксельной резкости мало.
 * Поверхности полупрозрачные поверх глубокого фона с цветными пятнами,
 * рамка — светлый блик сверху. Чужой для TexFi язык, поэтому спрятана.
 */
private val GlassColors =
    W0yColors(
        background = Color(0xFF0A0E1C),
        surface = Color(0x14FFFFFF),
        surfaceHigh = Color(0x24FFFFFF),
        border = Color(0x33FFFFFF),
        shadow = Color(0x00000000),
        accent = TexFiBlue,
        accentDeep = TexFiBlueDeep,
        secondary = TexFiSand,
        secondaryDeep = TexFiSandDeep,
        text = W0yText,
        textMuted = Color(0xFFA3A8BC),
        glass = true,
    )

/**
 * Светлая тема тёплая, а не бело-серая: голый Material-белый выглядит как
 * дефолт фреймворка, а в TexFi светлая тема всегда с тёплой бумагой.
 */
private val LightColors =
    W0yColors(
        background = Color(0xFFF7F1E4),
        surface = Color(0xFFFFFDF7),
        surfaceHigh = Color(0xFFEDE4D2),
        border = Color(0xFFD5C7AC),
        shadow = Color(0xFFC9B99B),
        accent = TexFiBlue,
        accentDeep = TexFiBlueDeep,
        secondary = Color(0xFFB98232),
        secondaryDeep = TexFiSandDeep,
        text = Color(0xFF191921),
        textMuted = Color(0xFF6B6455),
    )

/**
 * Палитры Hairline. Фон и карточка из токенов языка; разделитель `border`
 * для линий-подложек, `lineStrong` — контур всего, что нажимается (контраст
 * к фону не ниже 1.6:1, проверяется тестом).
 */
private val HairDark =
    DarkColors.copy(
        background = Color(0xFF0A090C),
        surface = Color(0xFF121116),
        surfaceHigh = Color(0xFF1B1A21),
        border = Color(0xFF2A2A34),
        lineStrong = Color(0xFF3A3A46),
        shadow = Color(0x00000000),
        text = Color(0xFFF6F1E5),
        textMuted = Color(0xFF9C978B),
    )

private val HairBlack =
    HairDark.copy(
        background = Color(0xFF000000),
        surface = Color(0xFF050507),
        surfaceHigh = Color(0xFF111015),
        border = Color(0xFF24242D),
    )

private val HairLight =
    LightColors.copy(
        background = Color(0xFFF6F1E5),
        surface = Color(0xFFFFFDF7),
        surfaceHigh = Color(0xFFEDE5D2),
        border = Color(0xFFD9D0BB),
        lineStrong = Color(0xFFA29780),
        shadow = Color(0x00000000),
        text = Color(0xFF14121A),
        textMuted = Color(0xFF5E584B),
        secondary = Color(0xFF2F5FD0),
    )

val LocalW0yColors = staticCompositionLocalOf { DarkColors }

@Composable
fun W0yTheme(
    mode: ThemeMode = ThemeMode.DARK,
    accent: Accent = Accent.SAND,
    customAccent: Int = 0xFFA06CFF.toInt(),
    style: UiStyle = UiStyle.HAIRLINE,
    glass: Boolean = true,
    look: HairlineLook = HairlineLook(),
    content: @Composable () -> Unit,
) {
    // «По системе» решается здесь, где известна тема телефона; ниже всюду
    // только светлая или тёмная.
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val effective = if (mode == ThemeMode.SYSTEM) (if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT) else mode
    val target = resolveColors(effective, accent, customAccent, style)
    // Смена акцента — перетекание цвета, а не скачок: так видно, что
    // поменялся именно акцент, а не перерисовался весь экран.
    val spec = tween<Color>(ACCENT_MS)
    val accentNow by animateColorAsState(target.accent, spec, label = "accent")
    val accentDeepNow by animateColorAsState(target.accentDeep, spec, label = "accentDeep")
    val secondaryNow by animateColorAsState(target.secondary, spec, label = "secondary")
    val colors =
        target.copy(
            accent = accentNow,
            accentDeep = accentDeepNow,
            secondary = secondaryNow,
        )
    val scheme =
        if (effective == ThemeMode.LIGHT) {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = Color.White,
                secondary = colors.secondary,
                background = colors.background,
                onBackground = colors.text,
                surface = colors.surface,
                onSurface = colors.text,
                surfaceVariant = colors.surfaceHigh,
                onSurfaceVariant = colors.textMuted,
                outline = colors.border,
                error = W0yDanger,
            )
        } else {
            // Material-компоненты (диалоги, меню) полупрозрачными быть не
            // должны: у них нет своего фона, и сквозь них читался бы экран.
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.background,
                secondary = colors.secondary,
                background = colors.background,
                onBackground = colors.text,
                surface = if (colors.glass) GlassOpaqueSurface else colors.surface,
                onSurface = colors.text,
                surfaceVariant = if (colors.glass) GlassOpaqueSurface else colors.surfaceHigh,
                onSurfaceVariant = colors.textMuted,
                outline = colors.border,
                error = W0yDanger,
            )
        }

    val tokens = remember(style, glass, look) { tokensFor(style, glass, look) }
    val typography = remember(style) { typographyFor(style) }
    SideEffect { com.texfi.w0y.ui.components.W0yMotion.stepped = tokens.stepped }
    CompositionLocalProvider(LocalW0yColors provides colors, LocalStyleTokens provides tokens, LocalHairLook provides look) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = content,
        )
    }
}

/**
 * Палитра темы с акцентом — без Compose, чтобы проверять контраст всех
 * сочетаний тестом.
 */
fun resolveColors(mode: ThemeMode, accent: Accent, customAccent: Int, style: UiStyle = UiStyle.PIXEL): W0yColors {
    val hair = style == UiStyle.HAIRLINE
    val base =
        when (mode) {
            ThemeMode.DARK, ThemeMode.SYSTEM -> if (hair) HairDark else DarkColors
            ThemeMode.OLED -> if (hair) HairBlack else OledColors
            // Стекло — только Smooth; у Hairline его нет, берём тёмную.
            ThemeMode.GLASS -> if (hair) HairDark else GlassColors
            ThemeMode.LIGHT -> if (hair) HairLight else LightColors
        }
    // Схема меняет только пару акцентов: фон, поверхности и текст остаются
    // от темы. Иначе «розовая» схема на светлой теме превращалась бы
    // в отдельную, четвёртую тему, которую никто не проверял.
    val tinted =
        if (accent == Accent.BLUE) {
            base
        } else {
            val main = if (accent == Accent.CUSTOM) Color(customAccent) else Color(accent.accent)
            base.copy(
                accent = main,
                accentDeep = if (accent == Accent.CUSTOM) deepen(main) else Color(accent.deep),
                secondary = Color(accent.secondary),
                secondaryDeep = deepen(Color(accent.secondary)),
            )
        }
    // Стекло полупрозрачное: контраст считается по непрозрачной подложке.
    val ground = if (tinted.glass) GlassOpaqueSurface else tinted.background
    return tinted.copy(
        text = Contrast.ensure(tinted.text, ground),
        textMuted = Contrast.ensure(tinted.textMuted, if (tinted.glass) GlassOpaqueSurface else tinted.surface),
        onAccent = Contrast.on(tinted.accent),
        // Hairline: текст акцентом лежит и на карточках, и на «приподнятых»
        // поверхностях — читаться он должен на самой неудобной из них.
        accentText =
            if (hair) {
                listOf(ground, tinted.surface, tinted.surfaceHigh).fold(tinted.accent) { c, g -> Contrast.ensure(c, g) }
            } else {
                Contrast.ensure(tinted.accent, ground)
            },
        // Прогресс — синий; на светлой теме берём тот, что держит контраст линии 3:1.
        progress = Contrast.ensure(TexFiBlue, ground, if (hair) 3f else Contrast.TEXT),
    )
}

private const val ACCENT_MS = 450

private val GlassOpaqueSurface = Color(0xFF1A2036)

/** Тень цвета: тот же оттенок, темнее на 45% — так считаются смещённые тени. */
internal fun deepen(color: Color): Color = androidx.compose.ui.graphics.lerp(color, Color.Black, 0.45f)
