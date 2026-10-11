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

/**
 * Выбор стиля и акцента — один раз: на первом запуске после приветствия и
 * один раз тем, кто обновился. Пропуск оставляет Pixel.
 *
 * Каждый вариант показан живым кусочком интерфейса в своём стиле, а не
 * картинкой: что видно здесь, то и будет в приложении. Нажатие сразу
 * переключает всё приложение — переход виден за карточками.
 */
@Composable
fun StylePickerScreen(
    current: UiStyle,
    accent: Accent,
    customAccent: Int,
    theme: ThemeMode,
    glass: Boolean,
    onGlass: (Boolean) -> Unit,
    onAccent: (Accent) -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val switcher = LocalStyleSwitcher.current
    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.welcome_skip),
                    style = PixelSectionLabel,
                    color = colors.textMuted,
                    modifier = Modifier.clickable(onClick = onSkip).padding(12.dp),
                )
            }
            Text(stringResource(R.string.style_pick_title), style = PixelScreenTitle, color = colors.text)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.style_pick_text), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            Spacer(Modifier.height(18.dp))
            UiStyle.entries.forEach { style ->
                StyleOption(
                    style = style,
                    selected = style == current,
                    accent = accent,
                    customAccent = customAccent,
                    theme = theme,
                    glass = glass,
                    onPick = { from -> switcher.switch(style, from) },
                )
                // У Smooth два вида поверхностей: стекло с бликами или матовые.
                if (style == UiStyle.SMOOTH && style == current) {
                    Spacer(Modifier.height(10.dp))
                    com.texfi.w0y.ui.components.PixelSegmented(
                        options = listOf(stringResource(R.string.style_glass), stringResource(R.string.style_matte)),
                        selectedIndex = if (glass) 0 else 1,
                        onSelect = { onGlass(it == 0) },
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(stringResource(R.string.style_pick_accent), style = PixelSectionLabel, color = colors.accentText)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accent.entries.filter { it != Accent.CUSTOM }.forEach { option ->
                    val active = option == accent
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onAccent(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(if (active) 36.dp else 30.dp)
                                .clip(CircleShape)
                                .background(Color(option.accent))
                                .then(if (active) Modifier.border(3.dp, colors.text, CircleShape) else Modifier),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        PixelButton(
            text = stringResource(R.string.style_pick_done),
            onClick = onDone,
            modifier = Modifier.padding(horizontal = Gutter, vertical = 16.dp).widthIn(min = 200.dp),
        )
    }
}

@Composable
private fun StyleOption(
    style: UiStyle,
    selected: Boolean,
    accent: Accent,
    customAccent: Int,
    theme: ThemeMode,
    glass: Boolean,
    onPick: (Offset?) -> Unit,
) {
    val colors = LocalW0yColors.current
    var center by remember { mutableStateOf<Offset?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.border, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onPick(center) }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(style.label), style = MaterialTheme.typography.bodyLarge, color = colors.text, modifier = Modifier.weight(1f))
            if (selected) PixelSprite(Sprites.check, colors.accent, Modifier.size(20.dp))
        }
        Text(stringResource(style.hint), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        // Живой образец: тот же код компонентов, только под этим стилем.
        W0yTheme(mode = theme, accent = accent, customAccent = customAccent, style = style, glass = glass, look = com.texfi.w0y.ui.theme.LocalHairLook.current) {
            StyleSample()
        }
    }
}

/** Образец компонентов в текущем стиле — для выбора стиля и настроек «Вид». */
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
