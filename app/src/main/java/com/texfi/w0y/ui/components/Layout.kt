package com.texfi.w0y.ui.components

import androidx.compose.ui.draw.drawBehind

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.texfi.w0y.ui.theme.matte
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val island = com.texfi.w0y.ui.theme.isIsland
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                if (island) {
                    // Hairline New: «назад» — матовый кружок, как кнопки плеера.
                    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val backLabel = androidx.compose.ui.res.stringResource(com.texfi.w0y.R.string.common_back)
                    Box(
                        Modifier
                            .size(44.dp)
                            .pressScale(interaction, pressed = 0.9f)
                            .matte(androidx.compose.foundation.shape.CircleShape)
                            .semantics { contentDescription = backLabel; role = androidx.compose.ui.semantics.Role.Button }
                            .clickable(interactionSource = interaction, indication = null, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        com.texfi.w0y.ui.icons.HairIcon(com.texfi.w0y.ui.icons.HairGlyph.CHEVRON_LEFT, colors.text, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                } else {
                    SpriteButton(Sprites.chevronLeft, onClick = onBack, size = 22)
                    Spacer(Modifier.width(12.dp))
                }
            }
            Text(title.asTitle(), style = PixelScreenTitle, color = colors.text, modifier = Modifier.weight(1f))
            actions()
        }
        if (!isSmooth && !island) {
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
    if (com.texfi.w0y.ui.theme.isIsland) {
        IslandSectionHeader(label, modifier, hint, index, action)
        return
    }
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
                // Стрелку ставит сам заголовок: если она уже есть в строке, второй не будет.
                val bare = label.removePrefix("❯").trimStart()
                Text(
                    text = if (hair) "❯ ${bare.asLabel()}" else if (smooth) bare else "❯ ${bare.uppercase()}",
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
 * Раздел Hairline New, как на сайте: номер «01» моно, название гротеском обычным регистром,
 * линия до края и действие справа.
 */
@Composable
private fun IslandSectionHeader(
    label: String,
    modifier: Modifier,
    hint: String?,
    index: Int?,
    action: @Composable () -> Unit,
) {
    val colors = LocalW0yColors.current
    val bare = label.removePrefix("❯").trimStart()
    // Строки разделов в словаре записаны заглавными; здесь они звучат как фраза.
    val title = if (bare.any { it.isLetter() } && bare == bare.uppercase()) bare.lowercase().replaceFirstChar { it.uppercase() } else bare
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxTitle = maxWidth * 0.8f
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (index != null) {
                Text(index.toString().padStart(2, '0'), style = com.texfi.w0y.ui.theme.HairLabelStyle, color = colors.textMuted)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                title,
                style = com.texfi.w0y.ui.theme.HairTitleStyle.copy(fontSize = 20.sp, lineHeight = 24.sp),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Название берёт сколько нужно (до 80% ширины), линия — остаток.
                modifier = Modifier.widthIn(max = maxTitle),
            )
            Spacer(Modifier.width(12.dp))
            // Линия раздела дорисовывается до края при первом показе — как на сайте.
            val drawsOn = com.texfi.w0y.ui.theme.hairLook.motion.draws
            val draw = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (drawsOn) 0f else 1f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (draw.value < 1f) draw.animateTo(1f, androidx.compose.animation.core.tween(com.texfi.w0y.ui.theme.HairTokens.LONG_MS, easing = com.texfi.w0y.ui.theme.HairTokens.EaseOut))
            }
            val ink = colors.border
            Box(Modifier.weight(1f).height(1.dp).drawBehind { drawLine(ink, androidx.compose.ui.geometry.Offset(0f, size.height / 2f), androidx.compose.ui.geometry.Offset(size.width * draw.value, size.height / 2f), size.height) })
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
        if (com.texfi.w0y.ui.theme.isIsland) {
            // Hairline New: лента — моно-подпись, без квадрата и без второго заголовка.
            Text(text.asLabel(), style = com.texfi.w0y.ui.theme.HairLabelStyle, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@Row
        }
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

/**
 * Края бегущей строки тают в прозрачность, а не режутся ножом по краю экрана.
 * Маска, а не свет: под ней ничего не рисуется, просто текст плавно уходит.
 */
fun Modifier.fadeEdges(edge: androidx.compose.ui.unit.Dp = 16.dp): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val e = (edge.toPx() / size.width).coerceIn(0f, 0.5f)
            drawRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent,
                    e to androidx.compose.ui.graphics.Color.Black,
                    1f - e to androidx.compose.ui.graphics.Color.Black,
                    1f to androidx.compose.ui.graphics.Color.Transparent,
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }

