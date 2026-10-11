package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.isSmooth
import com.texfi.w0y.ui.theme.PixelScreenTitle
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.asTitle

/** Единый боковой отступ на всех экранах. Разнобой в полях — первое, что читается как небрежность. */
val Gutter: Dp = 18.dp

/**
 * Заголовок экрана: крупная пиксельная надпись и короткая акцентная
 * подчёркивающая планка под ней. Планка держит верх страницы — без неё
 * заголовок висит в пустоте и экран выглядит незаконченным.
 */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Ноль для экранов, где боковое поле уже задано контейнером. */
    horizontalPadding: Dp = Gutter,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalW0yColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = if (com.texfi.w0y.ui.theme.isHair) 28.dp else 18.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                SpriteButton(Sprites.chevronLeft, onClick = onBack, size = 22)
                Spacer(Modifier.width(12.dp))
            }
            Text(title.asTitle(), style = PixelScreenTitle, color = colors.text, modifier = Modifier.weight(1f))
            actions()
        }
        if (!isSmooth) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .width(34.dp)
                    .height(3.dp)
                    .background(colors.accent),
            )
        }
        subtitle?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

/**
 * Заголовок раздела: «❯ НАЗВАНИЕ», линия до правого края и, если нужно,
 * действие. Линия — не украшение: она отделяет ленты друг от друга, без
 * неё длинная страница читается как один сплошной поток.
 */
@Composable
fun SectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    /** Номер раздела — та же нумерация, что на сайте TexFi. */
    index: Int? = null,
    action: @Composable () -> Unit = {},
) {
    val colors = LocalW0yColors.current
    val smooth = isSmooth
    val hair = com.texfi.w0y.ui.theme.isHair
    Column(modifier.fillMaxWidth()) {
        // Подписи отдаётся столько, сколько ей нужно, но не больше 80%:
        // иначе линия и действие делили бы ширину с ней поровну, и
        // «Послушать ещё раз» обрезалось бы при свободном месте справа.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelMax = maxWidth * 0.8f
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index != null) {
                    Text(
                        text = index.toString().padStart(2, '0'),
                        style = PixelSectionLabel,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (hair) "❯ ${label.asLabel()}" else if (smooth) label else "❯ ${label.uppercase()}",
                    style = if (hair) PixelSectionLabel else if (smooth) MaterialTheme.typography.titleMedium else PixelSectionLabel,
                    color = if (hair) colors.textMuted else if (smooth) colors.text else colors.accentText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = labelMax),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (hair) 1.dp else if (smooth) 0.dp else 2.dp)
                        .background(colors.border),
                )
                Spacer(Modifier.width(10.dp))
                action()
            }
        }
        hint?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

/**
 * Заголовок ленты, пришедшей от YouTube, — второй уровень под разделом.
 *
 * Раньше у лент был свой вид — жёлтый квадрат и обычный текст, — и на
 * одной главной жили три разных заголовка. Теперь уровней два: раздел
 * (синий, с линией) и лента внутри него — тот же шрифт, цвет текста,
 * без линии. Иерархия видна без третьего стиля.
 */
@Composable
fun ShelfTitle(text: String, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!isSmooth) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(colors.accent),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = if (com.texfi.w0y.ui.theme.isHair) text.asLabel() else if (isSmooth) text else text.uppercase(),
            style = PixelSectionLabel,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Пустое состояние: спрайт, короткая строка и объяснение, что сделать.
 * Просто «Пока пусто» посреди чёрного экрана выглядит как сбой.
 */
@Composable
fun EmptyState(
    sprite: List<String>,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    val colors = LocalW0yColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelSprite(rows = sprite, color = if (com.texfi.w0y.ui.theme.isHair) colors.outline else colors.border, modifier = Modifier.size(56.dp))
        Text(title.asLabel(), style = PixelSectionLabel, color = colors.textMuted, textAlign = TextAlign.Center)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

/** Разделитель между блоками списка. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(if (com.texfi.w0y.ui.theme.isHair) 1.dp else 2.dp)
            .background(colors.border),
    )
}
