package com.texfi.w0y.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.data.Accent
import com.texfi.w0y.data.ThemeMode
import com.texfi.w0y.data.UiStyle
import com.texfi.w0y.ui.components.Gutter
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.PixelSwitch
import com.texfi.w0y.ui.components.SegmentedBar
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelScreenTitle
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.W0yTheme
import com.texfi.w0y.ui.theme.screenBackground

/** Образец компонентов в текущем стиле — для настроек «Вид». */
@Composable
fun StyleSample() {
    val colors = LocalW0yColors.current
    var on by remember { mutableStateOf(true) }
    PixelCard(label = stringResource(R.string.style_sample_label)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(com.texfi.w0y.ui.theme.styleTokens.cover)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) { PixelSprite(Sprites.note, colors.onAccent, Modifier.size(18.dp)) }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("w0y", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                Text("TexFi", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            PixelSprite(Sprites.heart, colors.accent, Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            PixelSwitch(checked = on, onCheckedChange = { on = it })
        }
        Spacer(Modifier.height(12.dp))
        SegmentedBar(progress = { 0.42f }, lit = colors.secondary, dim = colors.border, modifier = Modifier.fillMaxWidth(), height = 5.dp)
    }
}
