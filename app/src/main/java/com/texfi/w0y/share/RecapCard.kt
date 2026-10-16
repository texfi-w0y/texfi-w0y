package com.texfi.w0y.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.texfi.w0y.R
import com.texfi.w0y.data.Thumbnails
import com.texfi.w0y.data.YearRecap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Карточка итогов года для сторис (9:16). Рисуется на телефоне из того, что уже посчитано:
 * минуты, исполнитель года, пять главных треков с обложками из кэша и три коротких факта.
 * Тот же язык, что у карточки трека: почти чёрный лист, сетка, свет цвета главной обложки,
 * моно-подписи заглавными, тонкие линии, знак w0y внизу.
 */
@Singleton
class RecapCardRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun render(recap: YearRecap): File =
        withContext(Dispatchers.Default) {
            val covers = recap.topSongs.map { (song, _) -> loadCover(song.thumbnailUrl) }
            val bitmap = draw(recap, covers)
            val file = File(context.cacheDir, FILE_NAME)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            covers.forEach { it?.recycle() }
            file
        }

    private suspend fun loadCover(url: String?): Bitmap? {
        url ?: return null
        val request = ImageRequest.Builder(context).data(Thumbnails.sized(url, COVER_PX)).allowHardware(false).build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        return (result as? SuccessResult)?.image?.let { runCatching { Thumbnails.squareOf(it.toBitmap(), url) }.getOrNull() }
    }

    private fun draw(recap: YearRecap, covers: List<Bitmap?>): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val display = ResourcesCompat.getFont(context, R.font.unbounded_extrabold)
        val mono = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium)
        val left = MARGIN.toFloat()
        val right = (WIDTH - MARGIN).toFloat()
        val width = right - left

        canvas.drawColor(BACKGROUND)
        val grid = Paint().apply { color = GRID; strokeWidth = 2f }
        var g = 0f
        while (g <= WIDTH) { canvas.drawLine(g, 0f, g, HEIGHT.toFloat(), grid); g += GRID_STEP }
        g = 0f
        while (g <= HEIGHT) { canvas.drawLine(0f, g, WIDTH.toFloat(), g, grid); g += GRID_STEP }
        val tint = covers.firstOrNull()?.let(::averageColor) ?: ACCENT
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT * 0.55f, Paint().apply {
            shader = RadialGradient(WIDTH * 0.5f, 0f, WIDTH.toFloat(), withAlpha(tint, 0.34f), AndroidColor.TRANSPARENT, Shader.TileMode.CLAMP)
        })

        var y = MARGIN + LABEL_SIZE.toFloat()
        val label = text(mono, LABEL_SIZE, TEXT_MUTED).apply { letterSpacing = 0.14f }
        canvas.drawText(context.getString(R.string.recap_card_label, recap.year).uppercase(), left, y, label)

        // Главная цифра — минуты, крупно, как на первом экране итогов.
        y += 210f
        canvas.drawText(recap.minutes.toString(), left - 6f, y, text(display, 200, TEXT))
        y += 64f
        canvas.drawText(context.getString(R.string.recap_minutes_title).uppercase(), left, y, text(mono, 34, ACCENT).apply { letterSpacing = 0.12f })

        y += 70f
        canvas.drawLine(left, y, right, y, line())

        recap.topArtists.firstOrNull()?.let { artist ->
            y += 70f
            canvas.drawText(context.getString(R.string.recap_artist_label).uppercase(), left, y, label)
            y += 96f
            canvas.drawText(ellipsize(artist.artist, text(display, 76, TEXT), width), left, y, text(display, 76, TEXT))
            y += 30f
        }

        if (recap.topSongs.isNotEmpty()) {
            y += 70f
            canvas.drawText(context.getString(R.string.recap_songs_label).uppercase(), left, y, label)
            y += 30f
            val titlePaint = text(null, 40, TEXT)
            val artistPaint = text(null, 32, TEXT_MUTED).apply { typeface = Typeface.DEFAULT }
            val numPaint = text(mono, 34, ACCENT)
            recap.topSongs.take(5).forEachIndexed { i, (song, _) ->
                val rowTop = y + i * ROW_H
                canvas.drawText("%02d".format(i + 1), left, rowTop + ROW_COVER * 0.62f, numPaint)
                val coverLeft = left + 80f
                val rect = RectF(coverLeft, rowTop, coverLeft + ROW_COVER, rowTop + ROW_COVER)
                drawCover(canvas, covers.getOrNull(i), rect)
                val textLeft = rect.right + 28f
                val textWidth = right - textLeft
                canvas.drawText(ellipsize(song.title, titlePaint, textWidth), textLeft, rowTop + 46f, titlePaint)
                canvas.drawText(ellipsize(song.artist, artistPaint, textWidth), textLeft, rowTop + 92f, artistPaint)
            }
            y += recap.topSongs.take(5).size * ROW_H
        }

        // Три факта в ряд: подпись моно, значение крупным шрифтом.
        val factsTop = (HEIGHT - MARGIN - MARK_SIDE - 120 - 170).toFloat()
        canvas.drawLine(left, factsTop, right, factsTop, line())
        val facts =
            listOf(
                context.getString(R.string.recap_fact_streak) to recap.streakDays.toString(),
                context.getString(R.string.recap_fact_days) to recap.activeDays.toString(),
                context.getString(R.string.recap_fact_hour) to (recap.peakHour?.let { "%02d:00".format(it) } ?: "—"),
            )
        val col = width / facts.size
        facts.forEachIndexed { i, (name, value) ->
            val x = left + col * i
            canvas.drawText(name.uppercase(), x, factsTop + 56f, text(mono, 24, TEXT_MUTED).apply { letterSpacing = 0.1f })
            canvas.drawText(value, x, factsTop + 140f, text(display, 64, TEXT))
        }

        val footTop = (HEIGHT - MARGIN - MARK_SIDE).toFloat()
        canvas.drawLine(left, footTop - 28f, right, footTop - 28f, line())
        drawMark(canvas, left, footTop, MARK_SIDE.toFloat())
        canvas.drawText("w0y", left + MARK_SIDE + 22f, footTop + MARK_SIDE * 0.62f, text(display, 32, TEXT))
        canvas.drawText(
            context.getString(R.string.share_card_source),
            right,
            footTop + MARK_SIDE * 0.62f,
            text(mono, 28, TEXT_MUTED).apply { textAlign = Paint.Align.RIGHT },
        )
        return bitmap
    }

    private fun drawCover(canvas: Canvas, cover: Bitmap?, rect: RectF) {
        val fill = Paint().apply { isAntiAlias = true }
        if (cover != null) {
            val layer = canvas.saveLayer(rect, null)
            fill.color = AndroidColor.WHITE
            canvas.drawRoundRect(rect, 8f, 8f, fill)
            canvas.drawBitmap(cover, null, rect, Paint().apply { isFilterBitmap = true; xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
            canvas.restoreToCount(layer)
        } else {
            fill.color = SURFACE
            canvas.drawRoundRect(rect, 8f, 8f, fill)
        }
        canvas.drawRoundRect(rect, 8f, 8f, line().apply { style = Paint.Style.STROKE; isAntiAlias = true })
    }

    /** Знак w0y: тонкая «W» и синее кольцо над ней. */
    private fun drawMark(canvas: Canvas, left: Float, top: Float, side: Float) {
        val k = side / 108f * 1.2f
        fun px(x: Float) = left + (x - 18f) * k
        fun py(y: Float) = top + (y - 20f) * k
        val path = Path().apply {
            moveTo(px(28f), py(40f)); lineTo(px(41f), py(74f)); lineTo(px(54f), py(50f)); lineTo(px(67f), py(74f)); lineTo(px(80f), py(40f))
        }
        canvas.drawPath(path, Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 7f * k; color = TEXT; isAntiAlias = true
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
        canvas.drawCircle(px(54f), py(30f), 7.5f * k, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3.6f * k; color = ACCENT; isAntiAlias = true })
    }

    private fun text(typeface: Typeface?, size: Int, color: Int) =
        TextPaint().apply {
            this.color = color
            textSize = size.toFloat()
            this.typeface = typeface ?: Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

    private fun line() = Paint().apply { color = BORDER_COLOR; strokeWidth = 2f }

    private fun ellipsize(value: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).toString()

    private fun averageColor(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
        val pixels = IntArray(256)
        small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        if (small !== bitmap) small.recycle()
        var r = 0L
        var gr = 0L
        var b = 0L
        pixels.forEach { r += (it shr 16) and 0xFF; gr += (it shr 8) and 0xFF; b += it and 0xFF }
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(AndroidColor.rgb((r / 256).toInt(), (gr / 256).toInt(), (b / 256).toInt()), hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.4f)
        hsv[2] = hsv[2].coerceIn(0.45f, 0.85f)
        return AndroidColor.HSVToColor(hsv)
    }

    private fun withAlpha(color: Int, alpha: Float) =
        AndroidColor.argb((alpha * 255).toInt(), AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))

    private companion object {
        const val FILE_NAME = "w0y-recap.png"
        const val WIDTH = 1080
        const val HEIGHT = 1920
        const val MARGIN = 84
        const val COVER_PX = 240
        const val GRID_STEP = 54f
        const val LABEL_SIZE = 26
        const val ROW_H = 132f
        const val ROW_COVER = 108f
        const val MARK_SIDE = 84
        const val BACKGROUND = 0xFF0D0D11.toInt()
        const val SURFACE = 0xFF20202A.toInt()
        const val BORDER_COLOR = 0xFF3A3D4A.toInt()
        const val GRID = 0xFF14151C.toInt()
        const val ACCENT = 0xFF4A7CFB.toInt()
        const val TEXT = 0xFFF2F2F5.toInt()
        const val TEXT_MUTED = 0xFF8A8A96.toInt()
    }
}
