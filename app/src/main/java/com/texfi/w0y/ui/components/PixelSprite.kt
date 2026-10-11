package com.texfi.w0y.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.texfi.w0y.ui.icons.SmoothIcons
import com.texfi.w0y.ui.theme.isHair
import com.texfi.w0y.ui.theme.isSmooth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Пиксельный спрайт: сетка символов, '#' — закрашенная ячейка. Рисуется
 * кодом, а не растровым ассетом, — масштабируется под любой размер и
 * правится в одну строку.
 *
 * Ячейки рисуются с нахлёстом (+0.5px к стороне): без него на субпиксельном
 * рендере между ними появляются щели и контур рассыпается.
 */
@Composable
fun PixelSprite(
    rows: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Плавный стиль рисует ту же иконку из своего набора: имя одно, рисунок
    // разный. Фирменные знаки TexFi пиксельные в обоих стилях — у них
    // плавной версии нет и быть не должно.
    if (isHair) {
        com.texfi.w0y.ui.icons.HairIcons.forSprite(rows)?.let { glyph ->
            com.texfi.w0y.ui.icons.HairIcon(glyph, color, modifier)
            return
        }
    }
    if (isSmooth) {
        SmoothIcons.forSprite(rows)?.let { vector ->
            Image(rememberVectorPainter(vector), null, modifier, colorFilter = ColorFilter.tint(color))
            return
        }
    }
    // Сетка собирается в один контур один раз на размер: раньше каждая
    // клетка была отдельным прямоугольником на каждом кадре — до 144 вызовов
    // на иконку, а иконок на экране десятки.
    Spacer(
        modifier.drawWithCache {
            val cols = rows.maxOf { it.length }
            val cell = minOf(size.width / cols, size.height / rows.size)
            val originX = (size.width - cell * cols) / 2f
            val originY = (size.height - cell * rows.size) / 2f
            val path = Path()
            rows.forEachIndexed { y, row ->
                var x = 0
                while (x < row.length) {
                    if (row[x] != '#') {
                        x++
                        continue
                    }
                    // Подряд идущие клетки — одним прямоугольником.
                    var run = x
                    while (run < row.length && row[run] == '#') run++
                    path.addRect(
                        Rect(
                            originX + x * cell,
                            originY + y * cell,
                            originX + run * cell + OVERLAP,
                            originY + (y + 1) * cell + OVERLAP,
                        ),
                    )
                    x = run
                }
            }
            onDrawBehind { drawPath(path, color) }
        },
    )
}

/** Нахлёст ячеек: без него на субпиксельном рендере между ними щели. */
private const val OVERLAP = 0.5f
