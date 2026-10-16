package com.texfi.w0y.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.theme.LocalW0yColors
import kotlin.math.sin
import kotlin.math.abs

/**
 * Насколько плотно рисуются строки списков. Задаётся один раз в оболочке
 * из настроек, чтобы каждый список не тащил их через параметры.
 */
val LocalCompactRows = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Id играющего трека — на весь интерфейс сразу.
 *
 * Иначе каждый экран со списком тянул бы состояние плеера ради одной
 * подсветки, и рано или поздно один из них про неё забыл бы.
 */
val LocalPlayingSongId = androidx.compose.runtime.compositionLocalOf<String?> { null }

/**
 * Строка трека — одна на все списки: поиск, плейлист, лайки, загрузки.
 * Действия справа задаёт вызывающий экран, чтобы в загрузках не было
 * кнопки «скачать», а в поиске — «удалить».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = song.id == LocalPlayingSongId.current,
    progressPercent: Float? = null,
    /** Долгое нажатие открывает панель действий над треком, если она есть. */
    onLongClick: (() -> Unit)? = null,
    /** Кадр 16:9 вместо квадрата — для выдачи видео, как в YouTube Music. */
    wideCover: Boolean = false,
    /**
     * Действия по долгому нажатию (удалить из списка и т. п.): вместо видимых
     * кнопок-корзин. Если заданы, долгое нажатие открывает их меню; общее меню
     * трека доступно из него строкой «ещё».
     */
    rowActions: List<RowAction> = emptyList(),
    /** Моно-метка под строкой: «только на этом телефоне», «файл недоступен». */
    tag: String? = null,
    tagWarn: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalW0yColors.current
    val compact = LocalCompactRows.current
    val cover = if (compact) 40.dp else 48.dp
    val rowInteraction = remember { MutableInteractionSource() }
    val actionsHost = LocalSongActions.current
    // Свой обработчик сильнее общего меню: экран, которому нужно другое
    // действие по долгому нажатию, по-прежнему может его задать.
    var rowMenuOpen by remember { mutableStateOf(false) }
    val moreLabel = stringResource(R.string.row_menu_more)
    val withMore =
        if (actionsHost != null && rowActions.isNotEmpty()) rowActions + RowAction(moreLabel) { actionsHost.openMenu(song) } else rowActions
    val longClick =
        if (rowActions.isNotEmpty()) {
            { rowMenuOpen = true }
        } else {
            onLongClick ?: actionsHost?.let { host -> { host.openMenu(song) } }
        }
    if (rowMenuOpen) {
        RowActionsMenu(title = song.title, actions = withMore, cancel = stringResource(R.string.library_cancel), onDismiss = { rowMenuOpen = false })
    }
    SwipeRow(
        modifier = modifier.fillMaxWidth(),
        onSwipeRight = actionsHost?.let { host -> { host.playNext(song) } },
        rightLabel = stringResource(R.string.queue_play_next),
        onSwipeLeft = actionsHost?.let { host -> { host.enqueue(song) } },
        leftLabel = stringResource(R.string.queue_enqueue),
    ) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                // Строка сжимается слабее плитки: в длинном списке сильный
                // отклик выглядит как дёрганье всего списка.
                .pressScale(rowInteraction, pressed = 0.975f)
                .combinedClickable(
                    interactionSource = rowInteraction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = longClick,
                ).padding(vertical = if (compact) 5.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(if (wideCover) cover * 16 / 9 else cover, cover)) {
                CoverImage(
                    url = song.thumbnailUrl,
                    px = Thumbnails.ROW,
                    modifier = Modifier.fillMaxSize(),
                    rect = wideCover,
                )
                // Играющий трек помечен живым столбиком, а не только цветом
                // названия: в длинном списке цвет строки глазом не найти,
                // а движение находится сразу.
                if (highlighted) {
                    if (com.texfi.w0y.ui.theme.isHair) {
                        HairPlayingMark(Modifier.matchParentSize(), wideCover)
                    } else {
                        PlayingMark(Modifier.align(Alignment.BottomStart).padding(3.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (song.explicit) {
                        ExplicitBadge()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (highlighted) colors.accent else colors.text,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Метка «твоей версии»: по ней в списке сразу видно, что
                    // это не просто песня, а та, которую ты подкрутил под себя.
                    song.sound?.takeIf { !it.isPlain }?.let {
                        Spacer(Modifier.width(6.dp))
                        VersionBadge(it.label)
                    }
                }
                Text(
                    text =
                        listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album, song.plays)
                            .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                )
                if (tag != null) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tagWarn) colors.secondary else colors.textMuted,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            song.durationText?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Spacer(Modifier.width(8.dp))
            actions()
        }
        if (progressPercent != null && progressPercent < 100f) {
            SegmentedBar(
                progress = { progressPercent / 100f },
                lit = colors.secondary,
                dim = colors.surfaceHigh,
                modifier = Modifier.fillMaxWidth(),
                height = 3.dp,
                cell = 8.dp,
            )
        }
        if (com.texfi.w0y.ui.theme.isHair) {
            // Тонкий разделитель от края названия: список читается как таблица, без карточек.
            Box(
                Modifier
                    .padding(start = cover + 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )
        }
    }
    }
}

/**
 * Пиксельная кнопка-иконка.
 *
 * Нажатие вдавливает, включение — подбрасывает: у лайка и у закрепления
 * должен быть виден сам момент, иначе непонятно, сработало или нет.
 */
@Composable
fun SpriteButton(
    rows: List<String>,
    onClick: () -> Unit,
    active: Boolean = false,
    size: Int = 20,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    val tap = rememberTapHaptic()
    val color by animateColorAsState(
        targetValue = if (active) colors.accent else colors.textMuted,
        animationSpec = tween(180),
        label = "spriteColor",
    )
    if (com.texfi.w0y.ui.theme.isHair) {
        val glyph = com.texfi.w0y.ui.icons.HairIcons.forSprite(rows)
        if (glyph != null) {
            // Контур сердца и значка закрепления заполняется, когда включено.
            val fill by animateFloatAsState(
                if (active && (glyph == com.texfi.w0y.ui.icons.HairGlyph.HEART || glyph == com.texfi.w0y.ui.icons.HairGlyph.PIN)) 1f else 0f,
                hairSpec(com.texfi.w0y.ui.theme.HairTokens.MID_MS),
                label = "hairFill",
            )
            com.texfi.w0y.ui.icons.HairIcon(
                glyph = glyph,
                color = color,
                fill = { fill },
                modifier =
                    Modifier
                        .size(size.dp)
                        .pressScale(interaction, pressed = 0.82f)
                        .popWhenActivated(active)
                        .clickable(interactionSource = interaction, indication = null) {
                            tap()
                            onClick()
                        },
            )
            return
        }
    }
    PixelSprite(
        rows = rows,
        color = color,
        modifier =
            Modifier
                .size(size.dp)
                .pressScale(interaction, pressed = 0.82f)
                .popWhenActivated(active)
                .clickable(interactionSource = interaction, indication = null) {
                    tap()
                    onClick()
                },
    )
}

/**
 * Метка «твоей версии» трека — своё звучание, запомненное за ним.
 *
 * Пиксельный шрифт здесь уместен: это акцентное число, а не текст,
 * который читают построчно.
 */
@Composable
fun VersionBadge(text: String, modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Box(
        modifier
            .background(colors.accent)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp, lineHeight = 11.sp),
            color = colors.background,
            maxLines = 1,
        )
    }
}

/**
 * Метка «E»: запись с ненормативной лексикой.
 *
 * Показывается так же, как у самого YouTube, — иначе непонятно, почему
 * режим «без мата» подменяет именно этот трек.
 */
@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    Box(
        modifier
            .size(14.dp)
            .background(colors.textMuted),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            text = "E",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp, lineHeight = 10.sp),
            color = colors.background,
        )
    }
}

/**
 * Отметка играющего трека: три столбика, которые дышат.
 *
 * Отдельная анимация именно под это состояние — не отклик на нажатие, а
 * признак того, что сейчас звучит. Поэтому она идёт сама и не привязана
 * к касанию.
 */
@Composable
private fun PlayingMark(modifier: Modifier = Modifier) {
    val colors = LocalW0yColors.current
    // Столбики рисуются на канве фиксированного размера, а фаза читается
    // внутри лямбды отрисовки. Раньше высота задавалась через Modifier.height,
    // то есть строка — а вместе с ней и весь ленивый список — переизмерялась
    // на каждом кадре, пока играющий трек виден на экране.
    // На паузе столбики стоят: анимация 30 кадров в секунду на паузе
    // держала экран в постоянной перерисовке.
    val playing = LocalIsPlaying.current
    val phase = rememberAnimationPhase(MARK_PERIOD_MS, MARK_FPS, enabled = playing)
    Canvas(
        modifier
            .background(colors.background.copy(alpha = 0.72f))
            .size(width = MARK_WIDTH, height = MARK_HEIGHT)
            .padding(2.dp),
    ) {
        val bar = 2.dp.toPx()
        val gap = 1.dp.toPx()
        repeat(MARK_BARS) { index ->
            // Столбики сдвинуты по фазе: в один такт они читались бы как
            // один мигающий прямоугольник.
            val height = (3f + 6f * abs(sin(phase.floatValue + index * 0.9f))).dp.toPx()
            drawRect(
                color = colors.accent,
                topLeft = Offset(index * (bar + gap), size.height - height),
                size = Size(bar, height),
            )
        }
    }
}

/** 3 столбика по 2dp с зазорами 1dp плюс подложка по 2dp с каждой стороны. */
private val MARK_WIDTH = 12.dp
private val MARK_HEIGHT = 13.dp
private const val MARK_PERIOD_MS = 1_100

/** Полоскам эквалайзера хватает 30 кадров: это индикатор, а не анимация. */
private const val MARK_FPS = 30

private const val MARK_BARS = 3

/**
 * Действия над треком, общие для всех списков: «следующим», «в конец
 * очереди» и меню по долгому нажатию. Раздаются оболочкой один раз, чтобы
 * каждая строка любого списка умела смахивание и меню без передачи
 * колбэков через каждый экран.
 */
class SongActions(
    val playNext: (SongItem) -> Unit,
    val enqueue: (SongItem) -> Unit,
    val openMenu: (SongItem) -> Unit,
)

/** Идёт ли звук прямо сейчас — для отметки играющего трека. */
val LocalIsPlaying = androidx.compose.runtime.compositionLocalOf { false }

val LocalSongActions = androidx.compose.runtime.staticCompositionLocalOf<SongActions?> { null }


/**
 * Отметка играющего трека в Hairline: обложка затемняется, по центру
 * четыре тонкие линии качаются. Только на видимых строках, 30 кадров, на
 * паузе стоят; на «Без движения» — просто стоят.
 */
@Composable
internal fun HairPlayingMark(modifier: Modifier, rect: Boolean, boxed: Boolean = true) {
    val colors = LocalW0yColors.current
    val playing = LocalIsPlaying.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val live = playing && look.motion.moves
    val phase = rememberAnimationPhase(MARK_PERIOD_MS, MARK_FPS, enabled = live)
    val shape = com.texfi.w0y.ui.theme.styleTokens.cover.takeIf { !rect } ?: androidx.compose.foundation.shape.RoundedCornerShape(percent = 8)
    val tone = com.texfi.w0y.ui.theme.Contrast.ensure(colors.accent, if (boxed) androidx.compose.ui.graphics.Color.Black else colors.background)
    Canvas(if (boxed) modifier.clip(shape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.58f)) else modifier) {
        val w = look.line.toPx()
        val gap = 5.dp.toPx()
        val total = 3 * gap
        val x0 = (size.width - total) / 2f
        val maxH = size.height * 0.34f
        repeat(4) { i ->
            val h = (0.35f + 0.65f * abs(sin(phase.floatValue + i * 0.9f))) * maxH
            val x = x0 + i * gap
            drawLine(tone, Offset(x, size.height / 2f - h / 2f), Offset(x, size.height / 2f + h / 2f), w, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
