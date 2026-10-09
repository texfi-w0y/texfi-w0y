package com.texfi.w0y.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Переключатель: у Pixel — квадратный, без морфинга; у Smooth — таблетка с плавным бегунком. */
@Composable
fun AppToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    if (Look.smooth) {
        val knob by animateDpAsState(if (checked) 22.dp else 2.dp, tween(160))
        Box(
            Modifier.width(48.dp).height(28.dp).clip(CircleShape)
                .background(if (checked) C.Blue else C.SurfaceHigh)
                .clickable { onChange(!checked) },
        ) {
            Box(Modifier.padding(start = knob, top = 2.dp).width(24.dp).height(24.dp).clip(CircleShape).background(if (checked) C.OnAccent else C.Muted))
        }
    } else {
        Box(
            Modifier.width(48.dp).height(26.dp).clip(RoundedCornerShape(3.dp))
                .background(if (checked) C.Blue else C.SurfaceHigh)
                .border(2.dp, if (checked) C.BlueDeep else C.Border, RoundedCornerShape(3.dp))
                .clickable { onChange(!checked) },
        ) {
            Box(
                Modifier.padding(3.dp).width(16.dp).height(16.dp).then(if (checked) Modifier.padding(start = 22.dp) else Modifier)
                    .background(if (checked) C.OnAccent else C.Muted),
            )
        }
    }
}

/** Ползунок: сегменты у Pixel, гладкая полоса с бегунком у Smooth. */
@Composable
fun AppSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = C.Blue,
    step: Float = 0.05f,
    segments: Int = 24,
) {
    if (!Look.smooth) {
        PixelSlider(value, range, onChange, modifier, color, step, segments)
        return
    }
    fun at(x: Float, width: Int): Float {
        val raw = range.start + (x / width).coerceIn(0f, 1f) * (range.endInclusive - range.start)
        return (Math.round(raw / step) * step).coerceIn(range.start, range.endInclusive)
    }
    Box(
        modifier.height(22.dp)
            .pointerInput(Unit) { detectTapGestures { onChange(at(it.x, size.width)) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> onChange(at(c.position.x, size.width)) } }
            .drawBehind {
                val h = 5.dp.toPx()
                val y = (size.height - h) / 2
                val f = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                drawRoundRect(C.SurfaceHigh, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
                drawRoundRect(color, Offset(0f, y), Size(size.width * f, h), CornerRadius(h / 2))
                drawCircle(color, 7.dp.toPx(), Offset(size.width * f, size.height / 2))
            },
    )
}

/** Поле ввода одной строкой на подложке по стилю; Enter — [onSubmit]. */
@Composable
fun AppTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: Glyph? = null,
    size: Int = 15,
    onSubmit: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (Look.smooth) 50.dp else 6.dp)
    RowCenter(
        modifier.fillMaxWidth().clip(shape).background(if (Look.smooth) C.SurfaceHigh.copy(alpha = 0.7f) else C.Surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (leading != null) {
            Icon(leading, C.Muted, 18.dp)
            Gap(w = 10)
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Txt(placeholder, color = C.Muted, size = size)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = size.sp),
                cursorBrush = SolidColor(C.Blue),
                keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Default),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth().textFieldFocus().submitOnEnter(onSubmit),
            )
        }
        if (value.isNotEmpty() && onClear != null) IconButton(Glyph.Close, onClick = onClear, color = C.Muted, size = 14.dp, box = 28.dp)
    }
}

private fun Modifier.submitOnEnter(onSubmit: (() -> Unit)?): Modifier =
    if (onSubmit == null) {
        this
    } else {
        this.then(
            Modifier.onKeyEventEnter(onSubmit),
        )
    }

private fun Modifier.onKeyEventEnter(onSubmit: () -> Unit): Modifier =
    this.onKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
            onSubmit()
            true
        } else {
            false
        }
    }

/** Квадрат цвета для выбора схемы. */
@Composable
fun Swatch(color: Color, selected: Boolean, size: Dp = 44.dp, onClick: () -> Unit) {
    val shape = if (Look.smooth) CircleShape else RoundedCornerShape(4.dp)
    Box(
        Modifier.width(size).height(size).background(color, shape)
            .border(if (selected) 3.dp else 2.dp, if (selected) C.Text else if (Look.smooth) Color.Transparent else C.Border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {}
}
