package com.texfi.w0y.recs

import com.texfi.w0y.data.SongItem

/** Почему трек в подборке — показывается рядом с ним, чтобы не гадать. */
sealed interface Why {
    data class SimilarTo(val title: String) : Why

    data class YouListen(val artist: String) : Why

    data object NewArtist : Why

    data object Forgotten : Why

    data object TimeOfDay : Why
}

data class Rec(val song: SongItem, val why: Why)

enum class Origin { RADIO, LOCAL_FORGOTTEN, LOCAL_TIME }

/** Кандидат до отбора. [rank] — место в выдаче источника (0 — самый похожий). */
data class Candidate(
    val song: SongItem,
    val origin: Origin,
    val rank: Int = 0,
    /** Название опорного трека, к которому кандидат «похож». */
    val seedTitle: String? = null,
    val seedArtist: String? = null,
)

/** Готовый набор для показа. [offline] — сеть не ответила, показан последний сохранённый набор. */
data class RecSet(val items: List<Rec>, val offline: Boolean = false)
