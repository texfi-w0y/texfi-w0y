package com.texfi.w0y.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import com.texfi.w0y.MainActivity
import com.texfi.w0y.R
import com.texfi.w0y.playback.W0yPlayerService
import java.io.File

/**
 * Виджет «что играет», в языке Hairline.
 *
 * Три раскладки по высоте (компактная, средняя, крупная), четыре состояния
 * (играет, пауза, тишина, ошибка), акцент приложения и системная светлая или
 * тёмная тема. Состояние берётся не у плеера, а из [WidgetState]: систему
 * просят перерисовать виджет и тогда, когда процесс приложения уже убит.
 */
class W0yWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) = render(context, appWidgetManager, appWidgetIds)

    /** Растянули или сжали виджет — раскладка может смениться. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) = render(context, appWidgetManager, intArrayOf(appWidgetId))

    internal companion object {
        /** Перерисовать все размещённые виджеты по последнему состоянию. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, W0yWidget::class.java))
            if (ids.isEmpty()) return
            render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            // Состояние и обложка читаются один раз на все виджеты.
            // Виджет не должен ронять приложение: сбой отрисовки одного виджета не трогает ни плеер, ни остальные.
            val state = runCatching { WidgetState.read(context) }.getOrElse { WidgetState() }
            val cover = runCatching { coverBitmap(state) }.getOrNull()
            ids.forEach { id ->
                runCatching {
                    val height = manager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
                    manager.updateAppWidget(id, build(context, state, cover, WidgetSize.forHeightDp(height)))
                }.onFailure { timber.log.Timber.w(it, "Виджет: не отрисовался") }
            }
        }

        private var coverCache: Triple<String, Long, Bitmap>? = null

        /** Обложка с диска; одна и та же не декодируется заново, пока файл не менялся. */
        private fun coverBitmap(state: WidgetState): Bitmap? {
            val file = state.coverPath?.let { File(it) }?.takeIf { it.exists() } ?: return null
            coverCache?.let { (path, modified, bitmap) -> if (path == file.path && modified == file.lastModified()) return bitmap }
            val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
            coverCache = Triple(file.path, file.lastModified(), bitmap)
            return bitmap
        }

        internal fun build(context: Context, state: WidgetState, cover: Bitmap?, size: WidgetSize): RemoteViews {
            val layout =
                when (size) {
                    WidgetSize.COMPACT -> R.layout.widget_compact
                    WidgetSize.MEDIUM -> R.layout.widget_medium
                    WidgetSize.LARGE -> R.layout.widget_large
                }
            val views = RemoteViews(context.packageName, layout)
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val accent =
                WidgetColors.accentOn(
                    state.accent,
                    card = if (night) NIGHT_CARD else DAY_CARD,
                    text = if (night) NIGHT_TEXT else DAY_TEXT,
                )
            val kind = state.kind()

            views.setTextColor(R.id.widget_state, accent)
            views.setInt(R.id.widget_toggle, "setColorFilter", accent)
            views.setTextViewText(
                R.id.widget_state,
                context.getString(
                    when (kind) {
                        WidgetKind.PLAYING -> R.string.widget_state_playing
                        WidgetKind.PAUSED -> R.string.widget_state_paused
                        WidgetKind.IDLE -> R.string.widget_state_idle
                        WidgetKind.ERROR -> R.string.widget_state_error
                    },
                ),
            )
            when (kind) {
                WidgetKind.IDLE -> {
                    // Честное пустое состояние: играть нечего или плеер с тех
                    // пор перезапускался — кнопки, которые ничего не сделают,
                    // здесь не рисуются.
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_idle))
                    views.setViewVisibility(R.id.widget_artist, View.GONE)
                    views.setViewVisibility(R.id.widget_controls, View.GONE)
                }

                else -> {
                    views.setTextViewText(R.id.widget_title, state.title)
                    views.setTextViewText(R.id.widget_artist, state.artist)
                    views.setViewVisibility(R.id.widget_artist, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_controls, View.VISIBLE)
                    views.setImageViewResource(
                        R.id.widget_toggle,
                        if (kind == WidgetKind.PLAYING) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    )
                }
            }

            if (cover != null && kind != WidgetKind.IDLE) {
                views.setImageViewBitmap(R.id.widget_cover, cover)
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover)
            }

            views.setOnClickPendingIntent(R.id.widget_body, openApp(context))
            views.setOnClickPendingIntent(R.id.widget_previous, transport(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            views.setOnClickPendingIntent(R.id.widget_toggle, transport(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next, transport(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            return views
        }

        // Токены Hairline: карточка и текст в тёмной и светлой темах (как в res/values*/colors.xml).
        private const val NIGHT_CARD = 0xFF121116.toInt()
        private const val NIGHT_TEXT = 0xFFF4F2F7.toInt()
        private const val DAY_CARD = 0xFFF8F7FA.toInt()
        private const val DAY_TEXT = 0xFF16141B.toInt()

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /**
         * Кнопка управления — медиа-клавиша прямо в сервис.
         *
         * Именно так виджет разговаривает с уже живым плеером: свой
         * контроллер здесь заводить не на чем, а `MediaSessionService`
         * разбирает `ACTION_MEDIA_BUTTON` сам. Кнопки показываются только
         * когда трек загружен, то есть сервис уже поднят, и запуск его из
         * фона не требуется.
         */
        private fun transport(context: Context, keyCode: Int): PendingIntent {
            val intent =
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(context, W0yPlayerService::class.java))
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            return PendingIntent.getService(
                context,
                keyCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
