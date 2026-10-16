package com.texfi.w0y.data

import androidx.annotation.StringRes
import com.texfi.w0y.R
import com.texfi.w0y.data.db.ArtistPlays
import com.texfi.w0y.data.db.ListenRow
import com.texfi.w0y.data.db.W0yDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** За какой срок считаем итоги и чем делим график. */
enum class StatsPeriod(@StringRes val label: Int, val days: Int?, val bucket: StatsBucket) {
    WEEK(R.string.stats_period_week, 7, StatsBucket.DAY),
    MONTH(R.string.stats_period_month, 30, StatsBucket.DAY),
    YEAR(R.string.stats_period_year, 365, StatsBucket.MONTH),
    ALL(R.string.stats_period_all, null, StatsBucket.MONTH),
}

enum class StatsBucket { DAY, WEEK, MONTH }

/** Столбик графика: начало отрезка и минуты в нём. */
data class StatsBar(val start: LocalDate, val minutes: Int)

/** Итоги прослушивания за период. */
data class ListeningStats(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val plays: Int = 0,
    val minutes: Int = 0,
    /** Проигранное время точнее минут: меньше минуты — это не «ничего». */
    val listenedMs: Long = 0L,
    val topSongs: List<Pair<SongItem, Int>> = emptyList(),
    val topArtists: List<ArtistPlays> = emptyList(),
    val since: Long = 0L,
    val bars: List<StatsBar> = emptyList(),
    val bucket: StatsBucket = StatsBucket.DAY,
    /** В периоде есть записи до правила прослушивания — минуты по ним оценочные. */
    val hasLegacy: Boolean = false,
    /** Пока false, экран показывает загрузку, а не «пусто». */
    val loaded: Boolean = false,
    /** Исполнители, впервые засчитанные в этом периоде. */
    val newArtists: Int = 0,
    /** Минуты по часам суток (24 значения): когда обычно слушаешь. */
    val hourly: List<Int> = List(24) { 0 },
) {
    /** Пусто честно: ни минут, ни засчитанных прослушиваний. */
    val isEmpty: Boolean get() = plays == 0 && listenedMs <= 0L
}

/**
 * Свои итоги вместо годового «рекапа».
 *
 * Всё считается на телефоне по локальной истории и доступно в любой день,
 * а не раз в декабре. Никуда не отправляется.
 *
 * Минуты раньше не показывались вовсе: их пытались посчитать по строке
 * длительности из выдачи YouTube, а у треков, включённых из плеера,
 * очереди или радио, этой строки нет — сумма выходила нулевой. Теперь
 * минуты — реально проигранное время каждой записи; у старых записей
 * (до обновления) оно не мерилось, и для них берётся длительность трека.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val dao: W0yDao,
) {
    suspend fun stats(period: StatsPeriod, zone: ZoneId = ZoneId.systemDefault()): ListeningStats {
        val since = period.days?.let { System.currentTimeMillis() - it * DAY_MS } ?: 0L
        val rows = dao.listensSince(since)
        val first = dao.firstPlayAt()
        val totalMs = rows.sumOf(::msOf)
        return ListeningStats(
            period = period,
            plays = dao.playsSince(since),
            minutes = (totalMs / 60_000L).toInt(),
            listenedMs = totalMs,
            topSongs = dao.topSongsSince(since).map { it.song.toItem() to it.plays },
            topArtists = dao.topArtistsSince(since),
            since = first ?: since,
            bars = bars(rows, period, first ?: since, zone),
            bucket = period.bucket,
            hasLegacy = rows.any { it.legacy },
            loaded = true,
            newArtists = dao.newArtistsSince(since),
            hourly = hourly(rows, zone),
        )
    }

    /** Итоги календарного года [year] (по умолчанию — текущего). */
    suspend fun yearRecap(year: Int = LocalDate.now().year, zone: ZoneId = ZoneId.systemDefault()): YearRecap {
        val since = YearRecapMath.startOf(year, zone)
        return YearRecapMath.compute(
            year = year,
            rows = dao.listensSince(since),
            plays = dao.playsSince(since),
            topSongs = dao.topSongsSince(since, limit = 5).map { it.song.toItem() to it.plays },
            topArtists = dao.topArtistsSince(since, limit = 5),
            newArtists = dao.newArtistsSince(since),
            zone = zone,
        )
    }

    /** Статистика, которая пересчитывается сама при любой записи в историю. */
    fun observe(period: StatsPeriod, zone: ZoneId = ZoneId.systemDefault()): kotlinx.coroutines.flow.Flow<ListeningStats> =
        dao.historyPulse().distinctUntilChanged().map { stats(period, zone) }

    /** Дописывает проигранное время в строки, где его не успели записать. Возвращает, сколько строк обновлено. */
    suspend fun backfill(): Int {
        val updates = StatsBackfill.plan(dao.historyForBackfill())
        updates.forEach { dao.updateListen(it.id, it.listenedMs, it.counted) }
        return updates.size
    }

    /** Стереть счётчики; записи истории остаются. */
    suspend fun reset() = dao.resetListening()

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * Минуты одной записи: проигранное время, а у старых — длительность
         * трека (тогда засчитывался любой старт, и точнее не узнать).
         */
        fun msOf(row: ListenRow): Long =
            if (!row.legacy) {
                row.listenedMs
            } else {
                row.durationMs?.takeIf { it > 0 } ?: (durationSeconds(row.durationText) * 1000L)
            }

        /** Минуты по часам суток в часовом поясе пользователя. */
        fun hourly(rows: List<ListenRow>, zone: ZoneId): List<Int> {
            val ms = LongArray(24)
            rows.forEach { row ->
                val hour = Instant.ofEpochMilli(row.playedAt).atZone(zone).hour
                ms[hour] += msOf(row)
            }
            return ms.map { (it / 60_000L).toInt() }
        }

        /**
         * Столбики по дням или месяцам. Пустые отрезки тоже есть: дыра в
         * графике — это честное «в этот день не слушал», а не пропуск.
         */
        fun bars(rows: List<ListenRow>, period: StatsPeriod, firstMs: Long, zone: ZoneId): List<StatsBar> {
            val today = LocalDate.now(zone)
            val bucket = period.bucket
            val startDay =
                when (val days = period.days) {
                    null -> Instant.ofEpochMilli(firstMs.coerceAtLeast(0)).atZone(zone).toLocalDate()
                    else -> today.minusDays((days - 1).toLong())
                }.let { align(it, bucket) }
            val sums = LinkedHashMap<LocalDate, Long>()
            var cursor = startDay
            val last = align(today, bucket)
            var guard = 0
            while (!cursor.isAfter(last) && guard++ < MAX_BARS) {
                sums[cursor] = 0L
                cursor = next(cursor, bucket)
            }
            rows.forEach { row ->
                val day = Instant.ofEpochMilli(row.playedAt).atZone(zone).toLocalDate()
                val key = align(day, bucket)
                if (key in sums) sums[key] = sums.getValue(key) + msOf(row)
            }
            return sums.map { (start, ms) -> StatsBar(start, (ms / 60_000L).toInt()) }.takeLast(MAX_BARS)
        }

        private fun align(day: LocalDate, bucket: StatsBucket): LocalDate =
            when (bucket) {
                StatsBucket.DAY -> day
                StatsBucket.WEEK -> day.minusDays((day.dayOfWeek.value - 1).toLong())
                StatsBucket.MONTH -> day.withDayOfMonth(1)
            }

        private fun next(day: LocalDate, bucket: StatsBucket): LocalDate =
            when (bucket) {
                StatsBucket.DAY -> day.plus(1, ChronoUnit.DAYS)
                StatsBucket.WEEK -> day.plus(1, ChronoUnit.WEEKS)
                StatsBucket.MONTH -> day.plus(1, ChronoUnit.MONTHS)
            }

        /** Больше столбиков на экран не помещается читаемо. */
        private const val MAX_BARS = 36

        /** «3:47» или «1:02:11» в секунды. */
        fun durationSeconds(text: String?): Int {
            val parts = text?.split(':')?.mapNotNull { it.trim().toIntOrNull() } ?: return 0
            return when (parts.size) {
                2 -> parts[0] * 60 + parts[1]
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                else -> 0
            }
        }
    }
}
