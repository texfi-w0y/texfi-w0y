package com.texfi.w0y.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.isSmooth
import com.texfi.w0y.ui.theme.styleTokens
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.clip

/**
 * Квадратный переключатель: рамка и заливка без скруглений и без
 * «морфинга» — Material-свитч поверх пиксельной графики выглядит чужим.
 */
@Composable
fun PixelSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalW0yColors.current
    val haptic = rememberHaptics()
    if (com.texfi.w0y.ui.theme.isHair) {
        HairSwitch(checked, onCheckedChange, haptic)
        return
    }
    if (isSmooth) {
        SmoothSwitch(checked, onCheckedChange, haptic)
        return
    }
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, tween(120), label = "switch")
    Box(
        Modifier
            .width(40.dp)
            .height(22.dp)
            .background(if (checked) colors.accentDeep else colors.surface)
            .border(2.dp, colors.border)
            .clickable {
                haptic(if (checked) Buzz.OFF else Buzz.ON)
                onCheckedChange(!checked)
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .background(if (checked) colors.accent else colors.textMuted),
        )
    }
}

/** Переключатель плавного стиля: капсула и круглый бегунок на пружине. */
@Composable
private fun SmoothSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, haptic: (Buzz) -> Unit) {
    val colors = LocalW0yColors.current
    val tokens = styleTokens
    val offset by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.7f), label = "smoothSwitch")
    val track by animateColorAsState(if (checked) colors.accent else colors.surfaceHigh, label = "smoothTrack")
    val thumb by animateColorAsState(if (checked) colors.onAccent else colors.textMuted, label = "smoothThumb")
    Box(
        Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(tokens.switchTrack)
            .background(track)
            .clickable {
                haptic(if (checked) Buzz.OFF else Buzz.ON)
                onCheckedChange(!checked)
            }.padding(4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(22.dp)
                .clip(tokens.switchThumb)
                .background(thumb),
        )
    }
}
