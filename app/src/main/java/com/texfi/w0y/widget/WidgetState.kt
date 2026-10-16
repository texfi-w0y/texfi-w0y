package com.texfi.w0y.widget

import android.content.Context
import kotlin.math.max
import kotlin.math.min

/** Что рисует виджет: четыре состояния, которых хватает честному виджету. */
internal enum class WidgetKind { PLAYING, PAUSED, IDLE, ERROR }

/** Раскладка по высоте, которую дал лаунчер. */
internal enum class WidgetSize {
    COMPACT,
    MEDIUM,
    LARGE,
    ;

    companion object {
        /** Границы в dp: до 110 — одна строка, до 200 — средний, выше — крупный. */
        fun forHeightDp(heightDp: Int): WidgetSize =
            when {
                heightDp < 110 -> COMPACT
                heightDp < 200 -> MEDIUM
                else -> LARGE
            }
    }
}

/**
 * Что показывает виджет — отдельно от живого плеера.
 *
 * Виджет обновляется системой и тогда, когда процесс приложения давно
 * убит: спрашивать плеер в этот момент не у кого. Поэтому последнее
 * известное состояние лежит в крошечном SharedPreferences-файле и
 * читается синхронно прямо в `onUpdate`.
 */
internal data class WidgetState(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    /** Файл обложки во внутреннем хранилище; null — обложки нет. */
    val coverPath: String? = null,
    /**
     * Процесс, который это состояние записал.
     *
     * По нему видно, жив ли ещё тот плеер. Виджет рисуется в нашем же
     * процессе — система его для этого и поднимает, — поэтому чужой pid
     * означает ровно одно: приложение с тех пор перезапускалось, а
     * управлять нечем.
     */
    val pid: Int = 0,
    /** Акцент приложения (ARGB): виджет следует за ним. */
    val accent: Int = DEFAULT_ACCENT,
    /** Плеер сообщил об ошибке и ещё не восстановился. */
    val error: Boolean = false,
) {
    val hasSong: Boolean get() = title.isNotBlank()

    /** Можно ли показывать кнопки: плеер, записавший состояние, ещё жив. */
    val controllable: Boolean get() = hasSong && pid == android.os.Process.myPid()

    /** Состояние для отрисовки; [alive] — жив ли записавший процесс (параметр для тестов). */
    fun kind(alive: Boolean = controllable): WidgetKind =
        when {
            !alive -> WidgetKind.IDLE
            error -> WidgetKind.ERROR
            playing -> WidgetKind.PLAYING
            else -> WidgetKind.PAUSED
        }

    companion object {
        const val DEFAULT_ACCENT = 0xFF4A7CFB.toInt()
        private const val FILE = "w0y_widget"

        fun read(context: Context): WidgetState {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return WidgetState(
                title = prefs.getString("title", "").orEmpty(),
                artist = prefs.getString("artist", "").orEmpty(),
                playing = prefs.getBoolean("playing", false),
                coverPath = prefs.getString("cover", null),
                pid = prefs.getInt("pid", 0),
                accent = prefs.getInt("accent", DEFAULT_ACCENT),
                error = prefs.getBoolean("error", false),
            )
        }

        fun write(context: Context, state: WidgetState) {
            context
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putString("title", state.title)
                .putString("artist", state.artist)
                .putBoolean("playing", state.playing)
                .putString("cover", state.coverPath)
                .putInt("pid", state.pid)
                .putInt("accent", state.accent)
                .putBoolean("error", state.error)
                .apply()
        }
    }
}

/** Цвет акцента на карточке виджета: темнеет или светлеет, пока не станет читаемым (≥ 3:1). */
internal object WidgetColors {
    fun accentOn(accent: Int, card: Int, text: Int): Int {
        var c = accent
        var step = 0
        while (contrast(c, card) < MIN_CONTRAST && step < 12) {
            c = mix(c, text, 0.12f)
            step++
        }
        return c
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) * (1 - t) + (b shr shift and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun luminance(color: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(color shr 16 and 0xFF) + 0.7152 * lin(color shr 8 and 0xFF) + 0.0722 * lin(color and 0xFF)
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private const val MIN_CONTRAST = 3.0
}
