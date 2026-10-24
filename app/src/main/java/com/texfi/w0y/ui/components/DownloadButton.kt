package com.texfi.w0y.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.playback.DownloadProgress
import com.texfi.w0y.playback.DownloadState
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel

/**
 * Состояние всех загрузок, раздаётся оболочкой один раз на всё приложение.
 * Читают его только кнопки загрузки — остальной экран на тики процентов
 * не перерисовывается.
 */
val LocalDownloadProgress = compositionLocalOf<Map<String, DownloadProgress>> { emptyMap() }

/**
 * Кнопка загрузки, которая показывает, что с треком происходит.
 *
 * Раньше это была стрелка, которая после нажатия оставалась той же
 * стрелкой: понять, пошла ли загрузка и куда, было нельзя — первый же
 * посторонний тестер на этом споткнулся. Теперь четыре состояния: стрелка,
 * часы (в очереди или ждёт Wi-Fi), проценты с полоской, галочка.
 */
@Composable
fun DownloadButton(
    songId: String,
    onDownload: () -> Unit,
    downloaded: Boolean = false,
) {
    val colors = LocalW0yColors.current
    val progress = LocalDownloadProgress.current[songId]
    if (com.texfi.w0y.ui.theme.isHair) {
        HairDownloadButton(
            onDownload = onDownload,
            done = downloaded || progress?.state == DownloadState.DONE,
            waiting = progress?.state == DownloadState.WAITING || progress?.state == DownloadState.PAUSED,
            running = progress?.state == DownloadState.RUNNING,
            fraction = { ((progress?.percent ?: 0f) / 100f).coerceIn(0f, 1f) },
        )
        return
    }
    when {
        downloaded || progress?.state == DownloadState.DONE ->
            SpriteButton(Sprites.check, onClick = {}, active = true)

        progress?.state == DownloadState.WAITING || progress?.state == DownloadState.PAUSED ->
            SpriteButton(Sprites.timer, onClick = {}, active = true)

        progress?.state == DownloadState.RUNNING -> {
            val percent = progress.percent.toInt().coerceIn(0, 99)
            Column(Modifier.size(24.dp, 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = "$percent", style = PixelSectionLabel.copy(fontSize = 8.sp), color = colors.accent)
                }
                SegmentedBar(
                    progress = { percent / 100f },
                    lit = colors.accent,
                    dim = colors.border,
                    modifier = Modifier.fillMaxWidth(),
                    height = 3.dp,
                    cell = 4.dp,
                    gap = 1.dp,
                )
            }
        }

        else -> SpriteButton(Sprites.download, onClick = onDownload)
    }
}


/**
 * Загрузка в Hairline: стрелка → кольцо прогресса → галочка, нарисованная
 * линией. Кольцо — тонкая синяя дуга по дорожке-разделителю.
 */
@Composable
private fun HairDownloadButton(
    onDownload: () -> Unit,
    done: Boolean,
    waiting: Boolean,
    running: Boolean,
    fraction: () -> Float,
) {
    val colors = LocalW0yColors.current
    val look = com.texfi.w0y.ui.theme.hairLook
    val tone = com.texfi.w0y.ui.theme.Contrast.ensure(colors.accent, colors.background)
    when {
        done -> {
            val draw = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (look.motion.draws) 0f else 1f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (draw.value < 1f) draw.animateTo(1f, androidx.compose.animation.core.tween(com.texfi.w0y.ui.theme.HairTokens.MID_MS, easing = com.texfi.w0y.ui.theme.HairTokens.EaseOut))
            }
            com.texfi.w0y.ui.icons.HairIcon(com.texfi.w0y.ui.icons.HairGlyph.CHECK, tone, Modifier.size(20.dp), reveal = { draw.value })
        }

        waiting -> SpriteButton(Sprites.timer, onClick = {}, active = true)

        running -> {
            // Кольцо догоняет проценты плавно, а не скачками по мере прихода замеров.
            val shown by androidx.compose.animation.core.animateFloatAsState(fraction(), hairSpec(com.texfi.w0y.ui.theme.HairTokens.LONG_MS), label = "dlRing")
            androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
                val w = look.line.toPx()
                val inset = w / 2f + 1.dp.toPx()
                val rect = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(colors.border, 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), rect, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                drawArc(colors.progress, -90f, 360f * shown, false, androidx.compose.ui.geometry.Offset(inset, inset), rect, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }

        else -> SpriteButton(Sprites.download, onClick = onDownload)
    }
}
