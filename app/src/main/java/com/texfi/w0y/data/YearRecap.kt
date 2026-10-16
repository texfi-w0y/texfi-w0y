package com.texfi.w0y.data

import com.texfi.w0y.data.db.ArtistPlays
import com.texfi.w0y.data.db.ListenRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Итоги календарного года — всё из истории на телефоне, без сети и без аккаунта.
 * Пустой год ([isEmpty]) экран не изображает «нулями», а честно говорит, что музыки мало.
 */
data class YearRecap(
    val year: Int,
    val minutes: Int = 0,
    val plays: Int = 0,
    val topSongs: List<Pair<SongItem, Int>> = emptyList(),
    val topArtists: List<ArtistPlays> = emptyList(),
    val newArtists: Int = 0,
    /** Час суток, когда музыки больше всего; null — данных нет. */
    val peakHour: Int? = null,
    val hourly: List<Int> = List(24) { 0 },
    /** Самый музыкальный месяц (1–12) и минуты в нём. */
    val bestMonth: Int? = null,
    val bestMonthMinutes: Int = 0,
    /** Самая длинная серия дней подряд с музыкой. */
    val streakDays: Int = 0,
    val activeDays: Int = 0,
    val bestDay: LocalDate? = null,
    val bestDayMinutes: Int = 0,
) {
    val isEmpty: Boolean get() = plays == 0 && minutes == 0
}

/** Чистая арифметика итогов: отдельно от базы, чтобы проверять тестами. */
object YearRecapMath {
    /** Начало года в миллисекундах — граница выборки. */
    fun startOf(year: Int, zone: ZoneId): Long = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun compute(
        year: Int,
        rows: List<ListenRow>,
        plays: Int,
        topSongs: List<Pair<SongItem, Int>>,
        topArtists: List<ArtistPlays>,
        newArtists: Int,
        zone: ZoneId,
    ): YearRecap {
        val inYear = rows.filter { Instant.ofEpochMilli(it.playedAt).atZone(zone).year == year }
        val perDay = HashMap<LocalDate, Long>()
        val perMonth = LongArray(12)
        inYear.forEach { row ->
            val ms = StatsRepository.msOf(row)
            if (ms <= 0) return@forEach
            val date = Instant.ofEpochMilli(row.playedAt).atZone(zone).toLocalDate()
            perDay[date] = (perDay[date] ?: 0L) + ms
            perMonth[date.monthValue - 1] += ms
        }
        val totalMs = perDay.values.sum()
        val hourly = StatsRepository.hourly(inYear, zone)
        val best = perDay.maxByOrNull { it.value }
        val month = perMonth.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }
        return YearRecap(
            year = year,
            minutes = (totalMs / 60_000L).toInt(),
            plays = plays,
            topSongs = topSongs,
            topArtists = topArtists,
            newArtists = newArtists,
            peakHour = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index,
            hourly = hourly,
            bestMonth = month?.let { it.index + 1 },
            bestMonthMinutes = ((month?.value ?: 0L) / 60_000L).toInt(),
            streakDays = streak(perDay.keys),
            activeDays = perDay.size,
            bestDay = best?.key,
            bestDayMinutes = ((best?.value ?: 0L) / 60_000L).toInt(),
        )
    }

    /** Самая длинная цепочка идущих подряд дат. */
    fun streak(days: Set<LocalDate>): Int {
        var best = 0
        days.forEach { day ->
            // Считаем только от начала цепочки: у её первого дня нет вчерашнего.
            if (day.minusDays(1) in days) return@forEach
            var len = 1
            var next = day.plusDays(1)
            while (next in days) {
                len++
                next = next.plusDays(1)
            }
            if (len > best) best = len
        }
        return best
    }
}
