package com.texfi.w0y.desktop

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.texfi.w0y.desktop.i18n.S

/**
 * Мини-плеер: маленькое окно поверх остальных (вместо виджета с телефона). Тащится за любое место
 * без кнопок, «развернуть» возвращает главное окно, «закрыть» закрывает приложение целиком
 * (как крестик главного окна — с учётом «в трей»).
 */
@Composable
fun WindowScope.MiniPlayer(app: AppState, onRestore: () -> Unit) {
    val p = app.player
    val song = p.current
    val shape = RoundedCornerShape(10.dp)
    WindowDraggableArea {
        Column(Modifier.fillMaxSize().background(C.Background, shape).border(1.dp, C.Border, shape).clip(shape).padding(10.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Cover(song?.thumbnailUrl, 64.dp)
                Gap(w = 12)
                Column(Modifier.weight(1f)) {
                    Txt(song?.title ?: S("d_idle"), size = 14, weight = androidx.compose.ui.text.font.FontWeight.Medium)
                    Txt(p.error ?: song?.artist.orEmpty(), color = C.Muted, size = 12)
                }
                IconButton(Glyph.Prev, onClick = p::previous, size = 18.dp, box = 34.dp)
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(3.dp)).background(C.Text).clickable(onClick = p::toggle), contentAlignment = Alignment.Center) {
                    Icon(if (p.playing) Glyph.Pause else Glyph.Play, C.Background, 18.dp)
                }
                IconButton(Glyph.Next, onClick = { p.nextClick() }, size = 18.dp, box = 34.dp)
                IconButton(Glyph.ChevronRight, onClick = onRestore, color = C.Muted, size = 16.dp, box = 30.dp)
            }
            Spacer(Modifier.height(6.dp))
            SegmentedSeek(p.position, p.duration, onSeek = p::seekFraction)
        }
    }
}
