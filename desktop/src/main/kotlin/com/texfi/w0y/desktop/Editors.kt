package com.texfi.w0y.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texfi.w0y.data.SongItem
import com.texfi.w0y.desktop.i18n.S
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.delay

// ---- выбор файлов

/** Системное окно выбора файлов. [extensions] — подсказка окну (работает не во всех системах). */
fun pickFiles(title: String, multiple: Boolean, extensions: Set<String>? = null): List<File> {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        isMultipleMode = multiple
        if (extensions != null) setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        isVisible = true
    }
    return dialog.files?.toList().orEmpty()
}

val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

fun pickImage(title: String): File? = pickFiles(title, multiple = false, extensions = imageExtensions).firstOrNull()

// ---- значки плейлиста: те же названия и контуры, что у телефона (HairGlyph)

/** Значки плейлиста. Имена совпадают с телефонными, чтобы выбор переезжал между ними без потерь. */
enum class PlGlyph(vararg val d: String) {
    NOTE("M9 18 V6 L19 4 V16", "M9 9 L19 7", "M7 16 a2 2 0 1 0 0.01 0 Z", "M17 14 a2 2 0 1 0 0.01 0 Z"),
    HEART("M12 19.5 C4.5 14.2 3.2 9.6 6 6.6 C8.4 4.4 11 5.5 12 8 C13 5.5 15.6 4.4 18 6.6 C20.8 9.6 19.5 14.2 12 19.5 Z"),
    MOON("M19 14.5 A8 8 0 1 1 9.5 5 A6.5 6.5 0 0 0 19 14.5 Z"),
    HEADPHONES("M4 15 V12 a8 8 0 0 1 16 0 V15", "M4 14 H8 V19 H5 a1 1 0 0 1 -1 -1 Z", "M20 14 H16 V19 H19 a1 1 0 0 0 1 -1 Z"),
    ROCKET("M12 3 C15 5 16.5 8.5 16 13 L12 17 L8 13 C7.5 8.5 9 5 12 3 Z", "M12 9.5 a1.5 1.5 0 1 0 0.01 0 Z", "M8 14 L5 17 L8.5 17.5", "M16 14 L19 17 L15.5 17.5", "M12 17 V21"),
    WAVE("M5 10 V14", "M8.5 7 V17", "M12 4 V20", "M15.5 8 V16", "M19 10.5 V13.5"),
    DISC("M12 3.5 a8.5 8.5 0 1 0 0.01 0 Z", "M3.5 12 H20.5", "M12 3.5 V20.5"),
    PIN("M9 4 H15 L14 10 L17.5 13.5 H6.5 L10 10 Z", "M12 13.5 V20"),
    SNOWFLAKE("M12 3 V21", "M4.2 7.5 L19.8 16.5", "M4.2 16.5 L19.8 7.5", "M9.8 4.6 L12 6.8 L14.2 4.6", "M9.8 19.4 L12 17.2 L14.2 19.4"),
    PUMPKIN("M12 7 C6 6 3 10 3 14 C3 18 7 20 12 20 C17 20 21 18 21 14 C21 10 18 6 12 7 Z", "M12 7 C9 10 9 17 12 20", "M12 7 C15 10 15 17 12 20", "M12 7 C12 5 13 4 14.5 3.5", "M6.8 13 L9 13 L7.9 11.4 Z", "M15 13 L17.2 13 L16.1 11.4 Z", "M9 16.5 L10.5 15.5 L12 16.5 L13.5 15.5 L15 16.5"),
    ;

    companion object {
        fun of(name: String?): PlGlyph? = name?.let { n -> entries.firstOrNull { it.name == n } }
    }
}

/** Цвета плейлиста: фирменный синий, песочный и несколько спокойных — те же, что на телефоне. */
val PlaylistColors: List<Int> = listOf(0xFF4A7DFB, 0xFFE0A458, 0xFF5FB38A, 0xFFE5646B, 0xFFB07CE8, 0xFF4FC1D9, 0xFFF0A3C0, 0xFFEDEAE0).map { it.toInt() }

@Composable
fun PlGlyphIcon(glyph: PlGlyph, color: Color, box: Dp, modifier: Modifier = Modifier) {
    val line = Look.line
    Spacer(
        modifier.size(box).drawWithCache {
            val parts = glyph.d.map { PathParser().parsePathString(it).toPath() }
            val k = this.size.minDimension / 24f
            val stroke = Stroke(width = line.toPx() / k, cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind { scale(k, k, pivot = Offset.Zero) { parts.forEach { drawPath(it, color, style = stroke) } } }
        },
    )
}

/** Плитка плейлиста: своя обложка, иначе обложка из аккаунта, иначе первый трек, иначе значок; цвет — рамкой. */
@Composable
fun PlaylistTile(pl: StoredPlaylist, box: Dp, modifier: Modifier = Modifier) {
    val accent = pl.accent?.let { Color(it) }
    val url = pl.customCover ?: pl.cover ?: pl.songs.firstOrNull()?.thumb
    val glyph = PlGlyph.of(pl.glyph)
    val shape = RoundedCornerShape(Look.cover)
    Box(modifier.size(box), contentAlignment = Alignment.Center) {
        if (url != null) {
            Cover(url, box)
        } else {
            Box(Modifier.size(box).clip(shape).background(C.SurfaceHigh), contentAlignment = Alignment.Center) {
                if (glyph != null) PlGlyphIcon(glyph, accent ?: C.Muted, box / 2.4f) else Icon(Glyph.Queue, C.Border, box / 3)
            }
        }
        if (accent != null) Box(Modifier.size(box).border(Look.line * 2, accent, shape))
    }
}

// ---- поля и кнопки редакторов

@Composable
fun EditField(label: String, value: String, enabled: Boolean = true, singleLine: Boolean = true, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(Look.control.coerceAtMost(6.dp))
    Column {
        SectionLabel(label)
        Gap(h = 6)
        Box(
            Modifier.fillMaxWidth().clip(shape).border(Look.line, C.Border, shape).padding(horizontal = 14.dp, vertical = 12.dp)
                .heightIn(min = if (singleLine) 0.dp else 64.dp),
        ) {
            BasicTextField(
                value, onChange, enabled = enabled, singleLine = singleLine,
                textStyle = TextStyle(color = if (enabled) C.Text else C.Muted, fontSize = 14.sp),
                cursorBrush = SolidColor(C.Blue), modifier = Modifier.fillMaxWidth().textFieldFocus(),
            )
        }
    }
}

/** Удаление в два нажатия: первое взводит кнопку на несколько секунд, второе удаляет. */
@Composable
fun ConfirmDeleteButton(label: String, armedLabel: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4_000)
            armed = false
        }
    }
    val shape = RoundedCornerShape(Look.control.coerceAtMost(6.dp))
    Box(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape)
            .background(if (armed) C.Danger else Color.Transparent)
            .border(Look.line, if (armed) C.Danger else C.Border, shape)
            .clickable { if (armed) onConfirm() else armed = true },
        contentAlignment = Alignment.Center,
    ) { Txt(Look.label(if (armed) armedLabel else label), color = if (armed) C.Background else C.Danger, size = 11, pixel = true) }
}

/** Квадрат обложки в редакторе: нажатие выбирает картинку. */
@Composable
private fun CoverPicker(shown: String?, border: Color, fallback: @Composable () -> Unit, onPick: () -> Unit) {
    val shape = RoundedCornerShape(Look.card.coerceAtMost(14.dp))
    Box(
        Modifier.size(180.dp).clip(shape).background(C.SurfaceHigh).border(Look.line * 2, border, shape).clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (shown != null) {
            coil3.compose.AsyncImage(
                model = shown, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            fallback()
        }
    }
}

// ---- редактор плейлиста

/**
 * Полная настройка плейлиста: обложка, имя, описание, цвет и значок. Имя уходит и в аккаунт, остальное —
 * только на этом компьютере. Удаление — в два нажатия.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistEditor(
    pl: StoredPlaylist,
    onSave: (name: String, note: String, accent: Int?, glyph: String?, picked: File?, clearCover: Boolean) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var name by remember(pl.id) { mutableStateOf(pl.name) }
    var note by remember(pl.id) { mutableStateOf(pl.note.orEmpty()) }
    var accent by remember(pl.id) { mutableStateOf(pl.accent) }
    var glyph by remember(pl.id) { mutableStateOf(pl.glyph) }
    var picked by remember(pl.id) { mutableStateOf<File?>(null) }
    var clear by remember(pl.id) { mutableStateOf(false) }
    val current = pl.customCover ?: pl.cover ?: pl.songs.firstOrNull()?.thumb
    val shown = if (clear) null else (picked?.let(LocalFiles::uri) ?: sizedThumb(current, 480))
    val pick: () -> Unit = { val f = pickImage(S("lm_cover_pick")); if (f != null) { picked = f; clear = false } }
    val chip = RoundedCornerShape(Look.control.coerceAtMost(8.dp))
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverPicker(shown, accent?.let { Color(it) } ?: C.Border, {
                    val g = PlGlyph.of(glyph)
                    if (g != null) PlGlyphIcon(g, accent?.let { Color(it) } ?: C.Muted, 64.dp) else Txt(Look.label(S("lm_cover_pick")), color = C.Muted, size = 11, pixel = true)
                }, pick)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(S("lm_cover_pick"), onClick = { pick() }, primary = false)
                    if (shown != null) PixelButton(S("lm_cover_clear"), onClick = { picked = null; clear = true }, primary = false)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EditField(S("lm_field_title"), name, enabled = pl.editable) { name = it }
                EditField(S("pl_note"), note, singleLine = false) { note = it }
            }
        }

        SectionLabel(S("pl_color"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).border(Look.line, if (accent == null) C.Text else C.Border, CircleShape).clickable { accent = null },
                contentAlignment = Alignment.Center,
            ) { Icon(Glyph.Close, C.Muted, 14.dp) }
            PlaylistColors.forEach { c ->
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(c))
                        .border(Look.line * 2, if (accent == c) C.Text else Color.Transparent, CircleShape).clickable { accent = c },
                )
            }
        }

        SectionLabel(S("pl_glyph"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(44.dp).clip(chip).background(if (glyph == null) C.Text else Color.Transparent)
                    .border(Look.line, if (glyph == null) C.Text else C.Border, chip).clickable { glyph = null },
                contentAlignment = Alignment.Center,
            ) { Icon(Glyph.Close, if (glyph == null) C.Background else C.Muted, 14.dp) }
            PlGlyph.entries.forEach { g ->
                val active = glyph == g.name
                Box(
                    Modifier.size(44.dp).clip(chip).background(if (active) C.Text else Color.Transparent)
                        .border(Look.line, if (active) C.Text else C.Border, chip).clickable { glyph = g.name },
                    contentAlignment = Alignment.Center,
                ) { PlGlyphIcon(g, if (active) C.Background else C.Text, 22.dp) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(S("lm_save"), onClick = { onSave(name, note, accent, glyph, picked, clear) }, enabled = name.isNotBlank())
            PixelButton(S("library_cancel"), onClick = onClose, primary = false)
        }
        ConfirmDeleteButton(if (pl.editable) S("pl_delete") else S("d_remove_from_library"), S("pl_delete_sure"), onDelete, Modifier.widthIn(max = 360.dp))
        Gap(h = 24)
    }
}

// ---- редактор своего файла

@Composable
fun LocalFileEditor(song: SongItem, onSave: (title: String, artist: String, album: String, picked: File?, clearCover: Boolean) -> Unit, onRemove: () -> Unit, onClose: () -> Unit) {
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album.orEmpty()) }
    var picked by remember(song.id) { mutableStateOf<File?>(null) }
    var clear by remember(song.id) { mutableStateOf(false) }
    val shown = if (clear) null else (picked?.let(LocalFiles::uri) ?: sizedThumb(song.thumbnailUrl, 480))
    val pick: () -> Unit = { val f = pickImage(S("lm_cover_pick")); if (f != null) { picked = f; clear = false } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverPicker(shown, C.Border, { Txt(Look.label(S("lm_cover_pick")), color = C.Muted, size = 11, pixel = true) }, pick)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(S("lm_cover_pick"), onClick = { pick() }, primary = false)
                    if (shown != null) PixelButton(S("lm_cover_clear"), onClick = { picked = null; clear = true }, primary = false)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EditField(S("lm_field_title"), title) { title = it }
                EditField(S("lm_field_artist"), artist) { artist = it }
                EditField(S("lm_field_album"), album) { album = it }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(S("lm_save"), onClick = { onSave(title, artist, album, picked, clear); onClose() }, enabled = title.isNotBlank())
            PixelButton(S("library_cancel"), onClick = onClose, primary = false)
        }
        ConfirmDeleteButton(S("lm_remove"), S("lm_remove_sure"), onRemove, Modifier.widthIn(max = 360.dp))
        Gap(h = 24)
    }
}
