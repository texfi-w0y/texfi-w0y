package com.texfi.w0y.ui.components

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.texfi.w0y.data.VideoSource
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Видео поверх обложки. Звук играет как играл — из сервиса; здесь свой
 * беззвучный плеер только с картинкой, и он идёт за звуком: каждые полсекунды
 * сверяет позицию и подтягивается, если разошёлся больше чем на 0,6 с.
 * Пауза и воспроизведение повторяются тем же, что у звука.
 */
@OptIn(UnstableApi::class)
@Composable
fun HairVideoSurface(
    source: VideoSource,
    sabrFactory: androidx.media3.datasource.DataSource.Factory?,
    playing: Boolean,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player =
        remember(source.url) {
            val http: androidx.media3.datasource.DataSource.Factory =
                if (source.sabr && sabrFactory != null) sabrFactory else DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                .build()
                .apply {
                    volume = 0f
                    setMediaItem(MediaItem.fromUri(source.url))
                    prepare()
                }
        }
    val isPlaying by rememberUpdatedState(playing)
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player) {
        player.seekTo(positionProvider())
        while (true) {
            val target = positionProvider()
            if (abs(player.currentPosition - target) > DRIFT_MS) player.seekTo(target)
            player.playWhenReady = isPlaying
            delay(SYNC_MS)
        }
    }
    AndroidView(
        factory = { TextureView(it).also { view -> player.setVideoTextureView(view) } },
        modifier = modifier.background(Color.Black),
    )
}

private const val SYNC_MS = 500L
private const val DRIFT_MS = 600L
