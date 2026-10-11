package com.texfi.w0y.ui.icons

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.texfi.w0y.data.IconStyle
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.hairLook

/**
 * Иконки Hairline — один набор линий на сетке 24×24, заданный путями SVG.
 *
 * Не растр и не вектор из файлов: пути лежат в коде, разбираются один раз и
 * рисуются штрихом нужной толщины. Толщину задаёт настройка «Line weight», а
 * вид — «Icon style»: линия, линия с лёгкой заливкой замкнутых контуров или
 * точки (штрих-пунктир с круглыми точками).
 */
enum class HairGlyph(vararg val d: String) {
    HOME("M4 11 L12 4 L20 11", "M6 9.5 V20 H18 V9.5", "M10 20 V14 H14 V20"),
    SEARCH("M10.5 4.5 a6 6 0 1 0 0.01 0 Z", "M15 15 L20 20"),
    YOURS("M5 5.5 h14 a1 1 0 0 1 1 1 v3 a1 1 0 0 1 -1 1 h-14 a1 1 0 0 1 -1 -1 v-3 a1 1 0 0 1 1 -1 Z", "M5 14 h14 a1 1 0 0 1 1 1 v3 a1 1 0 0 1 -1 1 h-14 a1 1 0 0 1 -1 -1 v-3 a1 1 0 0 1 1 -1 Z"),
    PLAY("M8 5 L19 12 L8 19 Z"),
    PAUSE("M8.5 5 V19", "M15.5 5 V19"),
    NEXT("M6 5 L15 12 L6 19 Z", "M18.5 5 V19"),
    PREVIOUS("M18 5 L9 12 L18 19 Z", "M5.5 5 V19"),
    SHUFFLE("M4 7 H8 C13 7 11 17 16 17 H20", "M4 17 H8 C9 17 9.8 16.4 10.6 15.4", "M13.4 8.6 C14 7.8 14.8 7 16 7 H20", "M18 5 L20 7 L18 9", "M18 15 L20 17 L18 19"),
    REPEAT("M17 3.5 L20 7 L17 10.5", "M20 7 H8 a4 4 0 0 0 -4 4 V12", "M7 20.5 L4 17 L7 13.5", "M4 17 H16 a4 4 0 0 0 4 -4 V12"),
    COLLAPSE("M6 9 L12 15 L18 9"),
    EXPAND("M6 15 L12 9 L18 15"),
    GEAR("M12 9 a3 3 0 1 0 0.01 0 Z", "M12 3.5 V6", "M12 18 V20.5", "M3.5 12 H6", "M18 12 H20.5", "M6 6 L7.8 7.8", "M16.2 16.2 L18 18", "M18 6 L16.2 7.8", "M7.8 16.2 L6 18"),
    PLUS("M12 5 V19", "M5 12 H19"),
    HEART("M12 19.5 C4.5 14.2 3.2 9.6 6 6.6 C8.4 4.4 11 5.5 12 8 C13 5.5 15.6 4.4 18 6.6 C20.8 9.6 19.5 14.2 12 19.5 Z"),
    DOWNLOAD("M12 4 V15", "M7 10.5 L12 15.5 L17 10.5", "M5 19.5 H19"),
    TRASH("M5 7 H19", "M9 7 V5 H15 V7", "M7 7 L8 19.5 H16 L17 7 Z"),
    TIMER("M12 6 a7 7 0 1 0 0.01 0 Z", "M12 13 V9.5", "M9.5 3 H14.5"),
    PIN("M9 4 H15 L14 10 L17.5 13.5 H6.5 L10 10 Z", "M12 13.5 V20"),
    ARTIST("M12 4.5 a3.5 3.5 0 1 0 0.01 0 Z", "M5 20 C5 15.5 8 13.5 12 13.5 C16 13.5 19 15.5 19 20"),
    RELEASE("M12 4 a8 8 0 1 0 0.01 0 Z", "M12 10 a2 2 0 1 0 0.01 0 Z"),
    CHEVRON_RIGHT("M9 5 L16 12 L9 19"),
    CHEVRON_LEFT("M15 5 L8 12 L15 19"),
    SLIDERS("M4 6 H20", "M4 12 H20", "M4 18 H20", "M8 4.5 a1.5 1.5 0 1 0 0.01 0 Z", "M15 10.5 a1.5 1.5 0 1 0 0.01 0 Z", "M10 16.5 a1.5 1.5 0 1 0 0.01 0 Z"),
    WAVE("M5 10 V14", "M8.5 7 V17", "M12 4 V20", "M15.5 8 V16", "M19 10.5 V13.5"),
    STATS("M5 20 V13", "M10 20 V6", "M15 20 V10", "M20 20 V4"),
    CLOSE("M6 6 L18 18", "M18 6 L6 18"),
    ROTATE("M19 12 a7 7 0 1 1 -2.1 -5", "M17.5 3.5 V7.5 H13.5"),
    CHECK("M5 12.5 L10 17.5 L19 7"),
    CODE("M9 7 L4 12 L9 17", "M15 7 L20 12 L15 17"),
    LICENSE("M6 3.5 H14 L18 7.5 V20.5 H6 Z", "M14 3.5 V7.5 H18", "M9 12 H15", "M9 16 H15"),
    GLOBE("M12 3.5 a8.5 8.5 0 1 0 0.01 0 Z", "M3.5 12 H20.5", "M12 3.5 C8.5 7.5 8.5 16.5 12 20.5", "M12 3.5 C15.5 7.5 15.5 16.5 12 20.5"),
    HEADPHONES("M4 15 V12 a8 8 0 0 1 16 0 V15", "M4 14 H8 V19 H5 a1 1 0 0 1 -1 -1 Z", "M20 14 H16 V19 H19 a1 1 0 0 0 1 -1 Z"),
    SYNC("M5 12 a7 7 0 0 1 12 -5", "M17.5 3.5 V7.5 H13.5", "M19 12 a7 7 0 0 1 -12 5", "M6.5 20.5 V16.5 H10.5"),
    PALETTE("M12 3.5 a8.5 8.5 0 1 0 0 17 c1.4 0 1.8 -1 1.2 -1.9 c-.7 -1 0 -2.1 1.3 -2.1 H17 a3.5 3.5 0 0 0 3.5 -3.5 C20.5 7 16.8 3.5 12 3.5 Z", "M8 11 a.6 .6 0 1 0 0.01 0 Z", "M11 7.5 a.6 .6 0 1 0 0.01 0 Z", "M15 8 a.6 .6 0 1 0 0.01 0 Z"),
    SHIELD("M12 3.5 L19 6.5 V12 C19 16.5 16 19.5 12 21 C8 19.5 5 16.5 5 12 V6.5 Z"),
    ROCKET("M12 3 C15 5 16.5 8.5 16 13 L12 17 L8 13 C7.5 8.5 9 5 12 3 Z", "M12 9.5 a1.5 1.5 0 1 0 0.01 0 Z", "M8 14 L5 17 L8.5 17.5", "M16 14 L19 17 L15.5 17.5", "M12 17 V21"),
    BOX("M12 3.5 L19.5 7.5 V16.5 L12 20.5 L4.5 16.5 V7.5 Z", "M4.5 7.5 L12 11.5 L19.5 7.5", "M12 11.5 V20.5"),
    SHARE("M12 15 V4", "M8 8 L12 4 L16 8", "M6 12 V19.5 H18 V12"),
    NOTE("M9 18 V6 L19 4 V16", "M9 9 L19 7", "M7 16 a2 2 0 1 0 0.01 0 Z", "M17 14 a2 2 0 1 0 0.01 0 Z"),
    GRIP("M9 7 a.8 .8 0 1 0 0.01 0 Z", "M15 7 a.8 .8 0 1 0 0.01 0 Z", "M9 12 a.8 .8 0 1 0 0.01 0 Z", "M15 12 a.8 .8 0 1 0 0.01 0 Z", "M9 17 a.8 .8 0 1 0 0.01 0 Z", "M15 17 a.8 .8 0 1 0 0.01 0 Z"),
    STAND("M8.5 3.5 H15.5 a1 1 0 0 1 1 1 V16 a1 1 0 0 1 -1 1 H8.5 a1 1 0 0 1 -1 -1 V4.5 a1 1 0 0 1 1 -1 Z", "M5.5 20.5 H18.5"),
    FULLSCREEN("M4 9 V4 H9", "M15 4 H20 V9", "M20 15 V20 H15", "M9 20 H4 V15"),

    // Только в Hairline: у пиксельного набора пары нет.
    QUEUE("M4 6 H15", "M4 12 H15", "M4 18 H10", "M17 13.5 L21 16.5 L17 19.5 Z"),
    LYRICS("M5 8 H19", "M5 12 H15", "M5 16 H17", "M5 20 H11", "M5 4 H13"),
    SOUND("M4 9.5 H8 L13 5 V19 L8 14.5 H4 Z", "M16 9 C17.5 10.5 17.5 13.5 16 15", "M18.5 6.5 C21.5 9.5 21.5 14.5 18.5 17.5"),
    MOON("M19 14.5 A8 8 0 1 1 9.5 5 A6.5 6.5 0 0 0 19 14.5 Z"),
    DISC("M12 3.5 a8.5 8.5 0 1 0 0.01 0 Z", "M3.5 12 H20.5", "M12 3.5 V20.5"),
    ;

    /** Замкнутые контуры — те, что в стиле «контур с заливкой» получают лёгкую заливку. */
    val closedFlags: List<Boolean> get() = d.map { it.trimEnd().endsWith("Z") || it.trimEnd().endsWith("z") }
}

object HairIcons {
    private val bySprite: Map<List<String>, HairGlyph> by lazy {
        val s = Sprites
        mapOf(
            s.home to HairGlyph.HOME, s.search to HairGlyph.SEARCH, s.library to HairGlyph.YOURS,
            s.play to HairGlyph.PLAY, s.pause to HairGlyph.PAUSE, s.next to HairGlyph.NEXT,
            s.previous to HairGlyph.PREVIOUS, s.shuffle to HairGlyph.SHUFFLE, s.repeat to HairGlyph.REPEAT,
            s.collapse to HairGlyph.COLLAPSE, s.gear to HairGlyph.GEAR, s.plus to HairGlyph.PLUS,
            s.heart to HairGlyph.HEART, s.download to HairGlyph.DOWNLOAD, s.trash to HairGlyph.TRASH,
            s.timer to HairGlyph.TIMER, s.pin to HairGlyph.PIN, s.artist to HairGlyph.ARTIST,
            s.release to HairGlyph.RELEASE, s.chevronRight to HairGlyph.CHEVRON_RIGHT,
            s.chevronLeft to HairGlyph.CHEVRON_LEFT, s.sliders to HairGlyph.SLIDERS, s.wave to HairGlyph.WAVE,
            s.stats to HairGlyph.STATS, s.close to HairGlyph.CLOSE, s.rotate to HairGlyph.ROTATE,
            s.check to HairGlyph.CHECK, s.github to HairGlyph.CODE, s.license to HairGlyph.LICENSE,
            s.globe to HairGlyph.GLOBE, s.headphones to HairGlyph.HEADPHONES, s.sync to HairGlyph.SYNC,
            s.palette to HairGlyph.PALETTE, s.shield to HairGlyph.SHIELD, s.rocket to HairGlyph.ROCKET,
            s.box to HairGlyph.BOX, s.share to HairGlyph.SHARE, s.chevronUp to HairGlyph.EXPAND,
            s.note to HairGlyph.NOTE, s.grip to HairGlyph.GRIP, s.stand to HairGlyph.STAND,
            s.fullscreen to HairGlyph.FULLSCREEN,
        )
    }

    /** Линейная пара к пиксельному спрайту; null — фирменные знаки без пары. */
    fun forSprite(rows: List<String>): HairGlyph? = bySprite[rows]

    /** Сколько спрайтов имеют пару — для теста полноты набора. */
    internal val mappedCount: Int get() = bySprite.size

    private val paths = HashMap<HairGlyph, List<Path>>()

    /** Контуры глифа в сетке 24×24, разобранные один раз. */
    fun pathsOf(glyph: HairGlyph): List<Path> =
        synchronized(paths) {
            paths.getOrPut(glyph) { glyph.d.map { PathParser().parsePathString(it).toPath() } }
        }
}

/**
 * Иконка Hairline. [weight] — толщина штриха в пикселях экрана, а не в
 * единицах сетки: линия остаётся одинаковой при любом размере иконки.
 */
@Composable
fun HairIcon(
    glyph: HairGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
    /** Заливка замкнутых контуров 0..1: сердце и закрепление заполняются, когда включены. */
    fill: () -> Float = { 0f },
) {
    val look = hairLook
    val style = look.iconStyle
    val weightDp = look.line
    Spacer(
        modifier
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .drawWithCache {
                val parts = HairIcons.pathsOf(glyph)
                val closed = glyph.closedFlags
                val k = minOf(size.width, size.height) / GRID
                val px = weightDp.toPx() / k
                val stroke =
                    Stroke(
                        width = px,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (style == IconStyle.DOTS) PathEffect.dashPathEffect(floatArrayOf(0.01f, px * 2.4f)) else null,
                    )
                val dx = (size.width - GRID * k) / 2f
                val dy = (size.height - GRID * k) / 2f
                onDrawBehind {
                    translate(dx, dy) {
                        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                            parts.forEachIndexed { i, p ->
                                val f = fill()
                                val base = if (style == IconStyle.FILLED_OUTLINE) FILL_ALPHA else 0f
                                if (closed[i] && (base > 0f || f > 0f)) drawPath(p, color.copy(alpha = maxOf(base, f)), style = Fill)
                                drawPath(p, color, style = stroke)
                            }
                        }
                    }
                }
            },
    )
}

private const val GRID = 24f
private const val FILL_ALPHA = 0.22f
