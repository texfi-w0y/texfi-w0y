package com.texfi.w0y.ui.screens

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.texfi.w0y.R
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.ui.components.HairIconButton
import com.texfi.w0y.ui.components.HairMath
import com.texfi.w0y.ui.icons.HairGlyph
import com.texfi.w0y.ui.theme.HairLabelStyle
import com.texfi.w0y.ui.theme.LocalW0yColors

/**
 * Палитра страницы поверх размытой обложки: фон всегда тёмный, текст белый — независимо
 * от темы приложения, иначе на тёмном размытии светлая тема давала бы нечитаемый текст.
 */
@Composable
private fun overBlurColors() =
    LocalW0yColors.current.copy(
        background = Color(0xFF0B0B0E),
        surface = Color.Black.copy(alpha = 0.35f),
        surfaceHigh = Color.White.copy(alpha = 0.14f),
        border = Color.White.copy(alpha = 0.18f),
        lineStrong = Color.White.copy(alpha = 0.38f),
        text = Color.White,
        textMuted = Color.White.copy(alpha = 0.68f),
        progress = Color.White,
        accentText = Color.White,
    )

/**
 * Размытая обложка на весь экран: цвета трека растекаются по фону сами, при смене трека
 * плавно перетекают в новые. С Android 12 размытие настоящее; на старых версиях картинка
 * берётся мелкой и растягивается — получается то же мягкое пятно.
 */
@Composable
fun PlayerBlurBackdrop(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize().background(Color(0xFF0B0B0E))) {
        Crossfade(targetState = url, animationSpec = tween(600), label = "blurBackdrop") { current ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(Thumbnails.sized(current, 96)).crossfade(false).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Чуть больше экрана: размытие не оставляет светлой каймы по краям.
                            scaleX = 1.35f
                            scaleY = 1.35f
                            alpha = 0.95f
                        }
                        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(56.dp) else Modifier),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.72f)))),
        )
    }
}

/** Обложка без рамки и формы: заполняет отведённое место целиком. */
@Composable
internal fun PlainCover(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(Thumbnails.sized(url, Thumbnails.HERO)).crossfade(200).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/**
 * Плеер «обложка на весь экран»: всё на одном экране, без прокрутки. Сверху обложка от края до
 * края, внизу она растворяется в размытии; ниже — название, полоса, управление и ряд значков:
 * текст песни, звук (скорость, тон), исполнитель, очередь с рекомендациями.
 *
 * Содержимое обложки ([stage]) — сама обложка, визуализатор, видео или текст: их выбирает
 * вызывающий, как в остальных раскладках.
 */
@Composable
internal fun CoverPlayerPage(
    song: SongItem,
    isPlaying: Boolean,
    shuffle: Boolean,
    repeatActive: Boolean,
    repeatOne: Boolean,
    positionMs: Long,
    durationMs: Long,
    liked: Boolean,
    lyricsOn: Boolean,
    positionLabel: String?,
    stage: @Composable (String?) -> Unit,
    titleBlock: @Composable (Modifier) -> Unit,
    onCollapse: () -> Unit,
    onCoverTap: () -> Unit,
    onSwipe: (Int) -> Unit,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onLike: () -> Unit,
    onDots: () -> Unit,
    onLyrics: () -> Unit,
    onSound: () -> Unit,
    onArtist: () -> Unit,
    onQueue: () -> Unit,
) {
    CompositionLocalProvider(com.texfi.w0y.ui.theme.LocalW0yColors provides overBlurColors()) {
        val colors = LocalW0yColors.current
        Box(Modifier.fillMaxSize()) {
            PlayerBlurBackdrop(song.thumbnailUrl)
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                // Обложка: от края до края, нижняя треть плавно уходит в прозрачность.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(song.id) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    when {
                                        total <= -SWIPE_PX -> onSwipe(1)
                                        total >= SWIPE_PX -> onSwipe(-1)
                                    }
                                },
                            ) { _, dx -> total += dx }
                        }
                        .pointerInput(Unit) { detectTapGestures { onCoverTap() } }
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(0.58f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                        },
                ) {
                    Crossfade(targetState = song.id, animationSpec = tween(260), label = "coverSong") { _ ->
                        Box(Modifier.fillMaxSize()) { stage(song.thumbnailUrl) }
                    }
                }

                Column(Modifier.padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        titleBlock(Modifier.weight(1f))
                        HairIconButton(
                            HairGlyph.HEART,
                            stringResource(R.string.player_cd_like),
                            onLike,
                            tint = if (liked) colors.accent else colors.textMuted,
                            fill = { if (liked) 1f else 0f },
                        )
                        Spacer(Modifier.width(2.dp))
                        Box(
                            Modifier
                                .padding(start = 4.dp)
                                .height(40.dp)
                                .width(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.surfaceHigh)
                                .clickable(onClick = onDots),
                            contentAlignment = Alignment.Center,
                        ) {
                            com.texfi.w0y.ui.icons.HairIcon(HairGlyph.DOTS, colors.text, Modifier.width(20.dp).height(20.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HairSeekLine(positionMs = positionMs, durationMs = durationMs, onDrag = onDrag, onDragEnd = onDragEnd)
                    Row(Modifier.fillMaxWidth()) {
                        Text(HairMath.formatTime(positionMs), style = HairLabelStyle, color = colors.textMuted)
                        Spacer(Modifier.weight(1f))
                        if (positionLabel != null) Text(positionLabel.uppercase(), style = HairLabelStyle, color = colors.textMuted)
                        Spacer(Modifier.weight(1f))
                        Text("-" + HairMath.formatTime((durationMs - positionMs).coerceAtLeast(0)), style = HairLabelStyle, color = colors.textMuted)
                    }
                    Spacer(Modifier.height(4.dp))
                    HairTransport(
                        playing = isPlaying,
                        shuffle = shuffle,
                        repeatActive = repeatActive,
                        repeatOne = repeatOne,
                        onShuffle = onShuffle,
                        onPrevious = onPrevious,
                        onToggle = onToggle,
                        onNext = onNext,
                        onRepeat = onRepeat,
                        playSize = 72.dp,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HairIconButton(HairGlyph.LYRICS, stringResource(R.string.player_lyrics), onLyrics, tint = if (lyricsOn) colors.accent else colors.text)
                        Row(
                            Modifier.clip(RoundedCornerShape(24.dp)).background(colors.surfaceHigh),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HairIconButton(HairGlyph.HEADPHONES, stringResource(R.string.sheet_badge_cd), onSound, tint = colors.text)
                            Box(Modifier.width(1.dp).height(24.dp).background(colors.border))
                            HairIconButton(HairGlyph.ARTIST, stringResource(R.string.player_to_artist), onArtist, tint = colors.text)
                        }
                        HairIconButton(HairGlyph.QUEUE, stringResource(R.string.sheet_open_cd), onQueue, tint = colors.text)
                    }
                }
            }
            // Верх: ручка и номер трека поверх обложки.
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .clickable(onClick = onCollapse)
                        .padding(horizontal = 40.dp, vertical = 10.dp),
                ) {
                    Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.7f)))
                }
            }
        }
    }
}

private const val SWIPE_PX = 120f
