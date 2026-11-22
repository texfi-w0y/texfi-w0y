package com.texfi.w0y.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.StatsRepository
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.HairIconButton
import com.texfi.w0y.ui.components.HairPlayButton
import com.texfi.w0y.ui.components.formatLength
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.icons.HairIcon
import com.texfi.w0y.ui.theme.GeologicaFamily
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.matte

/**
 * Шапка плейлиста в Hairline New. Сверху — тот же переливающийся фон из цветов обложки,
 * что у постера в плеере; он гаснет в фон экрана к середине обложки, поэтому название и
 * кнопки стоят на спокойном цвете в любой теме. Обложка крупно по центру (своя или мозаика
 * из первых треков), под ней моно-строка «плейлист · треков · длина», название, владелец,
 * описание и остров действий: перемешать · большая «играть» · скачать всё.
 */
@Composable
internal fun IslandCollectionHero(
    title: String,
    kind: String,
    owner: String?,
    note: String?,
    coverUrl: String?,
    songs: List<SongItem>,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: () -> Unit,
    onCustomize: (() -> Unit)? = null,
) {
    val colors = LocalW0yColors.current
    val tiles = songs.mapNotNull { it.thumbnailUrl }.distinct().take(4)
    val flowUrl = coverUrl ?: tiles.firstOrNull()
    val seconds = songs.sumOf { StatsRepository.durationSeconds(it.durationText) }
    val meta =
        listOfNotNull(
            kind,
            stringResource(R.string.collection_meta_tracks, songs.size),
            seconds.takeIf { it > 0 }?.let(::formatLength),
        ).joinToString("  ·  ")
    Box(Modifier.fillMaxWidth()) {
        // Цвет обложки только за верхней частью. Края тают в прозрачное, а не в цвет фона: под
        // шапкой может лежать фото или точки, и полоса цвета фона легла бы на них прямоугольником.
        // До самого верха экрана, под строку состояния: сверху не тает, иначе там снова тёмная полоса.
        val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(Modifier.fillMaxWidth().height(HERO_FLOW + statusTop).softEdges(top = 0f, bottom = 0.4f)) {
            IslandFlowBackdrop(flowUrl, playing = true, modifier = Modifier.fillMaxSize())
        }
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroCircle(HairGlyph.CHEVRON_LEFT, stringResource(R.string.common_back), onBack)
                Spacer(Modifier.weight(1f))
                if (onCustomize != null) HeroCircle(HairGlyph.SLIDERS, stringResource(R.string.pl_customize), onCustomize)
            }
            Spacer(Modifier.height(10.dp))
            HeroCover(coverUrl, tiles, Modifier.hairEnter(0))
            Spacer(Modifier.height(22.dp))
            Column(Modifier.hairEnter(1), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(meta.asLabel(), style = HairLabelStyle, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(
                    title,
                    style =
                        TextStyle(
                            fontFamily = GeologicaFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                            letterSpacing = (-0.6).sp,
                        ),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                owner?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                note?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .hairEnter(2)
                    .matte(RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val enabled = songs.isNotEmpty()
                HairIconButton(HairGlyph.SHUFFLE, stringResource(R.string.player_cd_shuffle), { if (enabled) onShuffle() }, tint = if (enabled) colors.text else colors.textMuted)
                HairPlayButton(playing = false, onClick = { if (enabled) onPlay() }, size = 60.dp)
                HairIconButton(HairGlyph.DOWNLOAD, stringResource(R.string.collection_download_all), { if (enabled) onDownload() }, tint = if (enabled) colors.text else colors.textMuted)
            }
            Spacer(Modifier.height(22.dp))
            // Начало списка: тонкая линия на всю ширину, как граница острова.
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.text.copy(alpha = 0.07f)))
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Круглая кнопка шапки. С [backdrop] — стекло над картинкой: под ней видно размытое, а не плотную заливку. */
@Composable
internal fun HeroCircle(glyph: HairGlyph, label: String, onClick: () -> Unit, backdrop: Backdrop? = null) {
    val colors = LocalW0yColors.current
    Box(
        Modifier
            .size(44.dp)
            .matte(CircleShape, backdrop)
            .semantics {
                contentDescription = label
                role = Role.Button
            }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        HairIcon(glyph, colors.text, Modifier.size(18.dp))
    }
}

/** Обложка крупно: своя картинка или мозаика 2×2 из первых обложек, если своей нет. */
@Composable
private fun HeroCover(url: String?, tiles: List<String>, modifier: Modifier) {
    val colors = LocalW0yColors.current
    val shape = RoundedCornerShape(18.dp)
    val frame =
        modifier
            .size(HERO_COVER)
            .clip(shape)
            .border(1.dp, colors.text.copy(alpha = 0.1f), shape)
    if (url != null || tiles.size < 4) {
        Box(frame.background(colors.surface), contentAlignment = Alignment.Center) {
            val single = url ?: tiles.firstOrNull()
            if (single != null) {
                CoverImage(single, Thumbnails.HERO, Modifier.fillMaxSize(), corner = 0)
            } else {
                HairIcon(HairGlyph.DISC, colors.textMuted, Modifier.size(48.dp))
            }
        }
        return
    }
    Column(frame) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.weight(1f)) {
                row.forEach { tile ->
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        CoverImage(tile, Thumbnails.TILE, Modifier.fillMaxSize(), corner = 0)
                    }
                }
            }
        }
    }
}

/**
 * Края блока тают в прозрачное по плавной кривой: сверху на долю [top] высоты, снизу на [bottom].
 * Так шапка ложится на любой фон экрана — точки, фото, цвет обложки — без видимой границы.
 */
internal fun Modifier.softEdges(top: Float, bottom: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val stops = ArrayList<Pair<Float, Color>>()
            for (i in 0..EDGE_STEPS) {
                val k = i.toFloat() / EDGE_STEPS
                val eased = k * k * (3f - 2f * k)
                if (top > 0f) stops += top * k to Color.Black.copy(alpha = eased)
            }
            for (i in 0..EDGE_STEPS) {
                val k = i.toFloat() / EDGE_STEPS
                val eased = k * k * (3f - 2f * k)
                stops += (1f - bottom) + bottom * k to Color.Black.copy(alpha = 1f - eased)
            }
            drawRect(Brush.verticalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
        }

private const val EDGE_STEPS = 8

private val HERO_COVER = 216.dp
private val HERO_FLOW = 300.dp

/** Отступ строк списка под шапкой Hairline New: шапка во всю ширину, строки — с полями. */
internal val IslandListGutter = 18.dp

/**
 * Страница с шапкой до самого верха (артист, плейлист): пока шапка видна, строка состояния
 * лежит прямо на обложке. Шапка уехала — под часами проявляется фон, и строки списка не лезут
 * под значки. [enabled] — только там, где страница правда идёт под строку состояния.
 */
@Composable
internal fun Modifier.statusScrim(list: androidx.compose.foundation.lazy.LazyListState, enabled: Boolean): Modifier {
    if (!enabled) return this
    val colors = com.texfi.w0y.ui.theme.LocalW0yColors.current
    val statusTop = WindowInsets.statusBars.getTop(androidx.compose.ui.platform.LocalDensity.current).toFloat()
    val shown by androidx.compose.runtime.remember(list) { androidx.compose.runtime.derivedStateOf { list.firstVisibleItemIndex > 0 } }
    val a by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, com.texfi.w0y.ui.components.hairSpec(com.texfi.w0y.ui.theme.HairTokens.MID_MS), label = "statusScrim")
    return drawWithContent {
        drawContent()
        if (a > 0f && statusTop > 0f) {
            val h = statusTop + 20.dp.toPx()
            drawRect(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = 0.94f * a),
                    statusTop / h to colors.background.copy(alpha = 0.8f * a),
                    1f to Color.Transparent,
                    endY = h,
                ),
                size = androidx.compose.ui.geometry.Size(size.width, h),
            )
        }
    }
}
