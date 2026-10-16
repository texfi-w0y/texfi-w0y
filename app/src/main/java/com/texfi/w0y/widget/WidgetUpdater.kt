package com.texfi.w0y.widget

import android.content.Context
import android.graphics.Bitmap
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.texfi.w0y.data.Thumbnails
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Держит виджет в курсе того, что играет.
 *
 * Обложка сохраняется файлом, а не передаётся только в момент обновления:
 * систему перерисовать виджет может попросить кто угодно и когда угодно —
 * уже после того, как наш процесс убит, — и тогда картинку нужно взять
 * с диска.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastCoverUrl: String? = null

    fun push(
        title: String,
        artist: String,
        playing: Boolean,
        coverUrl: String?,
        accent: Int = WidgetState.DEFAULT_ACCENT,
    ) {
        // Сначала текст и состояние кнопки — они должны появиться сразу,
        // не дожидаясь сети за обложкой.
        val previous = WidgetState.read(context)
        val next =
            WidgetState(
                title = title,
                artist = artist,
                playing = playing,
                coverPath = previous.coverPath,
                pid = android.os.Process.myPid(),
                accent = accent,
                // Ошибка снимается, как только пошёл звук или сменился трек.
                error = previous.error && !playing && previous.title == title,
            )
        // Экономно: если ничего не изменилось, систему не трогаем.
        if (next != previous) {
            WidgetState.write(context, next)
            W0yWidget.refresh(context)
        }
        if (coverUrl == lastCoverUrl) return
        lastCoverUrl = coverUrl
        scope.launch { updateCover(coverUrl) }
    }

    /** Плеер сообщил об ошибке: виджет честно говорит, что не вышло. */
    fun markError() {
        val state = WidgetState.read(context)
        if (state.error || !state.hasSong) return
        WidgetState.write(context, state.copy(error = true, playing = false))
        W0yWidget.refresh(context)
    }

    /** Акцент приложения сменился — виджет перекрашивается. */
    fun setAccent(accent: Int) {
        val state = WidgetState.read(context)
        if (state.accent == accent) return
        WidgetState.write(context, state.copy(accent = accent))
        W0yWidget.refresh(context)
    }

    /**
     * Плеер закончился — виджет должен это показать.
     *
     * Иначе на домашнем экране осталась бы строка с треком и кнопки,
     * которые уже никому не адресованы.
     */
    fun clear() {
        WidgetState.write(context, WidgetState())
        lastCoverUrl = null
        W0yWidget.refresh(context)
    }

    private suspend fun updateCover(coverUrl: String?) {
        val file = File(context.cacheDir, COVER_FILE)
        val bitmap = coverUrl?.let { loadCover(it) }
        if (bitmap == null) {
            file.delete()
        } else {
            runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                .onFailure { Timber.w(it, "Обложка виджета не сохранилась") }
        }
        val state = WidgetState.read(context)
        WidgetState.write(context, state.copy(coverPath = file.takeIf { it.exists() }?.path))
        withContext(Dispatchers.Main) { W0yWidget.refresh(context) }
    }

    private suspend fun loadCover(url: String): Bitmap? {
        val request =
            ImageRequest
                .Builder(context)
                .data(Thumbnails.sized(url, COVER_PX))
                .size(COVER_PX, COVER_PX)
                // RemoteViews отдаёт картинку другому процессу, а
                // hardware-битмап туда не переживает передачу.
                .allowHardware(false)
                .build()
        val result = SingletonImageLoader.get(context).execute(request)
        return (result.image as? BitmapImage)?.bitmap?.let { rounded(Thumbnails.squareOf(it, url)) }
    }

    /** Скруглённые углы вшиты в картинку: RemoteViews не умеет обрезать по контуру. */
    private fun rounded(source: Bitmap): Bitmap {
        val size = source.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val radius = size * CORNER_SHARE
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private companion object {
        const val COVER_FILE = "widget_cover.png"

        /** Хватает крупной раскладке: обложка там до ~140 dp. */
        const val COVER_PX = 384

        /** Радиус скругления обложки — доля стороны. */
        const val CORNER_SHARE = 0.14f
    }
}
