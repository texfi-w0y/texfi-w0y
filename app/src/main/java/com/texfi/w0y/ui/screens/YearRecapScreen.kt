package com.texfi.w0y.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.R
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.data.YearRecap
import com.texfi.w0y.ui.components.CoverImage
import com.texfi.w0y.ui.components.HairIconButton
import com.texfi.w0y.ui.components.HourlyLine
import com.texfi.w0y.ui.components.hairSpec
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.theme.Contrast
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.HairScreenTitleStyle
import com.texfi.w0y.ui.theme.HairTokens
import com.texfi.w0y.ui.theme.HairTitleStyle
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.asLabel
import com.texfi.w0y.ui.theme.hairLook
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Итоги года — пять страниц поверх размытой обложки главного трека года, как сторис: сверху
 * полоски страниц, касание справа — дальше, слева — назад, можно и листать. Страницы: минуты,
 * исполнитель года, пять треков, твой ритм (час, серия дней, месяц, рекордный день) и финал с
 * карточкой «поделиться». Всё посчитано на телефоне — об этом сказано на последней странице.
 */
@Composable
fun YearRecapScreen(
    recap: YearRecap,
    onClose: () -> Unit,
    onShare: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val pages = if (recap.isEmpty) 1 else PAGES
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    val coverUrl = recap.topSongs.firstOrNull()?.first?.thumbnailUrl
    Box(Modifier.fillMaxSize().testTag("year-recap")) {
        PlayerBlurBackdrop(coverUrl, playing = true)
        CompositionLocalProvider(LocalW0yColors provides overBlurColors()) {
            val colors = LocalW0yColors.current
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Полоски страниц: пройденные и текущая — акцентом, следующие — линией.
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(pages) { i ->
                            val lit by animateFloatAsState(if (i <= pager.currentPage) 1f else 0f, hairSpec(HairTokens.LONG_MS), label = "recapBar")
                            Box(Modifier.weight(1f).height(2.dp).background(colors.outline)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(lit).background(Contrast.ensure(colors.accent, colors.background)))
                            }
                        }
                    }
                    HairIconButton(HairGlyph.CLOSE, stringResource(R.string.sheet_close_cd), onClose, tint = colors.text)
                }
                HorizontalPager(
                    state = pager,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(pages) {
                                detectTapGestures { offset ->
                                    val target = if (offset.x < size.width * 0.3f) pager.currentPage - 1 else pager.currentPage + 1
                                    if (target in 0 until pages) scope.launch { pager.animateScrollToPage(target) }
                                }
                            },
                ) { page ->
                    val shown = pager.settledPage == page
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("recap-page-$page"),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when {
                            recap.isEmpty -> EmptyPage(recap, shown)
                            page == 0 -> MinutesPage(recap, shown)
                            page == 1 -> ArtistPage(recap, shown)
                            page == 2 -> SongsPage(recap, shown)
                            page == 3 -> RhythmPage(recap, shown)
                            else -> FinalPage(recap, shown, onShare)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Появление на странице: снизу на 24 dp и из прозрачности, ступенью по номеру. Запускается,
 * когда страница встала на место, а не когда её подготовил пейджер, — иначе анимация успевала
 * пройти за кадром.
 */
@Composable
private fun Modifier.recapEnter(index: Int, shown: Boolean): Modifier {
    val moves = hairLook.motion.moves
    val anim = remember { Animatable(if (moves) 0f else 1f) }
    LaunchedEffect(shown) {
        if (moves && shown && anim.value < 1f) {
            delay(80L * index)
            anim.animateTo(1f, tween(HairTokens.LONG_MS, easing = HairTokens.EaseOut))
        }
    }
    return graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 24.dp.toPx()
    }
}

@Composable
private fun PageLabel(text: String, shown: Boolean) {
    val colors = LocalW0yColors.current
    Text(text.asLabel(), style = HairLabelStyle, color = Contrast.ensure(colors.accent, colors.background), modifier = Modifier.recapEnter(0, shown))
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun EmptyPage(recap: YearRecap, shown: Boolean) {
    val colors = LocalW0yColors.current
    PageLabel(stringResource(R.string.recap_card_label, recap.year), shown)
    Text(stringResource(R.string.recap_empty), style = HairTitleStyle, color = colors.text, modifier = Modifier.recapEnter(1, shown))
}

@Composable
private fun MinutesPage(recap: YearRecap, shown: Boolean) {
    val colors = LocalW0yColors.current
    // Число накручивается, когда страница открылась: итог — это событие, а не строка в таблице.
    val moves = hairLook.motion.moves
    val count = remember { Animatable(if (moves) 0f else recap.minutes.toFloat()) }
    LaunchedEffect(shown) {
        if (shown && count.value < recap.minutes) count.animateTo(recap.minutes.toFloat(), tween(1400, easing = HairTokens.EaseOut))
    }
    PageLabel(stringResource(R.string.recap_card_label, recap.year), shown)
    Text(
        text = count.value.toInt().toString(),
        style = HairScreenTitleStyle.copy(fontSize = 88.sp, lineHeight = 92.sp),
        color = colors.text,
        maxLines = 1,
        modifier = Modifier.recapEnter(1, shown).testTag("recap-minutes"),
    )
    Text(stringResource(R.string.recap_minutes_title), style = HairTitleStyle, color = colors.text, modifier = Modifier.recapEnter(2, shown))
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.recap_hours, recap.minutes / 60),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textMuted,
        modifier = Modifier.recapEnter(3, shown),
    )
    Text(
        text = stringResource(R.string.recap_plays, recap.plays),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textMuted,
        modifier = Modifier.recapEnter(3, shown),
    )
}

@Composable
private fun ArtistPage(recap: YearRecap, shown: Boolean) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    val top = recap.topArtists.firstOrNull()
    PageLabel(stringResource(R.string.recap_artist_label), shown)
    if (top == null) return
    Text(top.artist, style = HairScreenTitleStyle.copy(fontSize = 44.sp, lineHeight = 50.sp), color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.recapEnter(1, shown))
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.recap_plays, top.plays), style = HairLabelStyle, color = colors.textMuted, modifier = Modifier.recapEnter(2, shown))
    Spacer(Modifier.height(36.dp))
    // Остальные — тонкими полосами в доле от первого: видно, насколько он впереди.
    recap.topArtists.drop(1).forEachIndexed { i, artist ->
        val share = artist.plays.toFloat() / top.plays.coerceAtLeast(1)
        val fill by animateFloatAsState(if (shown) share else 0f, tween(HairTokens.LONG_MS + 200, delayMillis = 120 * i, easing = HairTokens.EaseOut), label = "artistShare")
        Column(Modifier.padding(vertical = 8.dp).recapEnter(3 + i, shown)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(i + 2), style = HairLabelStyle, color = tone)
                Spacer(Modifier.width(12.dp))
                Text(artist.artist, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(artist.plays.toString(), style = HairLabelStyle, color = colors.textMuted)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.outline)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(tone))
            }
        }
    }
}

@Composable
private fun SongsPage(recap: YearRecap, shown: Boolean) {
    val colors = LocalW0yColors.current
    val tone = Contrast.ensure(colors.accent, colors.background)
    PageLabel(stringResource(R.string.recap_songs_label), shown)
    recap.topSongs.take(5).forEachIndexed { i, (song, plays) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).recapEnter(1 + i, shown), verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(i + 1), style = HairLabelStyle, color = tone, modifier = Modifier.width(30.dp))
            CoverImage(url = song.thumbnailUrl, px = Thumbnails.ROW, modifier = Modifier.size(if (i == 0) 72.dp else 56.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = if (i == 0) HairTitleStyle else MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text("×$plays", style = HairLabelStyle, color = colors.textMuted)
        }
    }
}

@Composable
private fun RhythmPage(recap: YearRecap, shown: Boolean) {
    val colors = LocalW0yColors.current
    val locale = LocalConfiguration.current.locales[0]
    PageLabel(stringResource(R.string.recap_rhythm_label), shown)
    recap.peakHour?.let { hour ->
        Text("%02d:00".format(hour), style = HairScreenTitleStyle.copy(fontSize = 64.sp, lineHeight = 70.sp), color = colors.text, modifier = Modifier.recapEnter(1, shown))
        Text(stringResource(R.string.recap_peak_hour), style = MaterialTheme.typography.bodyLarge, color = colors.textMuted, modifier = Modifier.recapEnter(2, shown))
        Spacer(Modifier.height(14.dp))
        HourlyLine(recap.hourly, Modifier.recapEnter(2, shown))
        Spacer(Modifier.height(18.dp))
    }
    val facts =
        listOfNotNull(
            stringResource(R.string.recap_fact_streak) to recap.streakDays.toString(),
            stringResource(R.string.recap_fact_days) to recap.activeDays.toString(),
            recap.bestMonth?.let { stringResource(R.string.recap_fact_month) to Month.of(it).getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { c -> c.titlecase(locale) } },
            recap.bestDay?.let { stringResource(R.string.recap_fact_best_day) to "${it.format(DateTimeFormatter.ofPattern("d MMMM", locale))} · ${recap.bestDayMinutes}′" },
            stringResource(R.string.recap_fact_new_artists) to recap.newArtists.toString(),
        )
    facts.forEachIndexed { i, (name, value) ->
        Column(Modifier.recapEnter(3 + i, shown)) {
            Box(Modifier.fillMaxWidth().height(hairLook.line).background(colors.outline))
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
                Text(value, style = HairTitleStyle.copy(fontSize = 17.sp), color = colors.text, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FinalPage(recap: YearRecap, shown: Boolean, onShare: () -> Unit) {
    val colors = LocalW0yColors.current
    PageLabel(stringResource(R.string.recap_card_label, recap.year), shown)
    Text(stringResource(R.string.recap_final_title), style = HairScreenTitleStyle.copy(fontSize = 34.sp, lineHeight = 40.sp), color = colors.text, modifier = Modifier.recapEnter(1, shown))
    Spacer(Modifier.height(22.dp))
    val summary =
        listOfNotNull(
            stringResource(R.string.recap_minutes_title) to recap.minutes.toString(),
            recap.topArtists.firstOrNull()?.let { stringResource(R.string.recap_artist_label) to it.artist },
            recap.topSongs.firstOrNull()?.let { stringResource(R.string.recap_song_label) to it.first.title },
        )
    summary.forEachIndexed { i, (name, value) ->
        Column(Modifier.recapEnter(2 + i, shown)) {
            Box(Modifier.fillMaxWidth().height(hairLook.line).background(colors.outline))
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name.asLabel(), style = HairLabelStyle, color = colors.textMuted, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(value, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f, fill = false))
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    // Главное действие страницы — светлая плоская кнопка, как основная кнопка Hairline.
    val shape = com.texfi.w0y.ui.theme.styleTokens.button
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(colors.text)
            .border(hairLook.line, colors.text, shape)
            .clickable(role = Role.Button, onClick = onShare)
            .testTag("recap-share")
            .recapEnter(5, shown),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.recap_share).asLabel(), style = HairLabelStyle, color = colors.background)
    }
    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.recap_private), style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.recapEnter(6, shown))
}

private const val PAGES = 5
