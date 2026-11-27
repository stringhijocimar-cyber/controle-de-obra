package br.com.obracontrol

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.TemporalAdjusters.previousOrSame
import java.time.temporal.WeekFields
import kotlin.math.ceil

internal enum class GanttScale(val label: String) { DAYS("Dias"), WEEKS("Semanas"), MONTHS("Meses") }

internal data class GanttDateBand(
    val key: String, val start: LocalDate, val end: LocalDate, val label: String, val description: String,
)

/** Calendar bands use real day boundaries, including partial months and ISO weeks across New Year. */
internal class GanttTimeline(val axis: GanttAxis, val scale: GanttScale) {
    private val monthFormat = DateTimeFormatter.ofPattern("MMM", brazil)
    val rowCount: Int get() = when (scale) { GanttScale.DAYS -> 4; GanttScale.WEEKS -> 3; GanttScale.MONTHS -> 2 }
    val preferredWidth: Float = when (scale) {
        GanttScale.DAYS -> axis.days * 36f
        GanttScale.WEEKS -> axis.days / 7f * 82f
        GanttScale.MONTHS -> axis.days / 30.4375f * 112f
    }.coerceIn(280f, 24_000f)

    fun automaticWidth(viewport: Float): Float = when (scale) {
        GanttScale.DAYS -> maxOf(viewport, axis.days * 28f)
        GanttScale.WEEKS -> viewport * maxOf(1f, axis.days / (7f * 26f))
        GanttScale.MONTHS -> viewport * maxOf(1f, axis.days / (30.4375f * 18f))
    }.coerceAtMost(24_000f).coerceAtLeast(viewport)

    private fun bands(first: LocalDate, next: (LocalDate) -> LocalDate,
        describe: (LocalDate, LocalDate, LocalDate) -> GanttDateBand): List<GanttDateBand> = buildList {
        var cursor = first
        while (cursor <= axis.end) {
            val following = next(cursor)
            val start = maxOf(cursor, axis.start)
            val end = minOf(following.minusDays(1), axis.end)
            if (end >= start) add(describe(cursor, start, end))
            cursor = following
        }
    }

    val years = bands(axis.start.withDayOfYear(1), { it.plusYears(1) }) { original, start, end ->
        GanttDateBand("year-${original.year}", start, end, original.year.toString(), "Ano ${original.year}")
    }
    val months = bands(axis.start.withDayOfMonth(1), { it.plusMonths(1) }) { original, start, end ->
        GanttDateBand("month-${original.year}-${original.monthValue}", start, end,
            original.format(monthFormat).replace(".", "").replaceFirstChar { it.uppercase(brazil) },
            "${original.month.getDisplayName(java.time.format.TextStyle.FULL, brazil)} de ${original.year}")
    }
    val weeks = bands(axis.start.with(previousOrSame(DayOfWeek.MONDAY)), { it.plusWeeks(1) }) { original, start, end ->
        val number = original.get(WeekFields.ISO.weekOfWeekBasedYear())
        val year = original.get(WeekFields.ISO.weekBasedYear())
        val crossing = original.year != original.plusDays(6).year || year != start.year
        GanttDateBand("week-$year-$number", start, end,
            if (crossing) "S${number.toString().padStart(2, '0')} / $year" else "Sem. ${number.toString().padStart(2, '0')}",
            "Semana ISO $number de $year · ${original.dateText()} a ${original.plusDays(6).dateText()}")
    }

    fun bands(row: Int, width: Float = preferredWidth): List<GanttDateBand> = when (row) {
        0 -> years
        1 -> months
        2 -> {
            val stride = labelStride(width, 7f, 66f)
            weeks.filterIndexed { index, _ -> index % stride == 0 }.mapIndexed { index, band ->
                val end = weeks.getOrNull((index + 1) * stride)?.start?.minusDays(1) ?: axis.end
                val firstWeek = band.start.get(WeekFields.ISO.weekOfWeekBasedYear()).toString().padStart(2, '0')
                val lastWeek = end.get(WeekFields.ISO.weekOfWeekBasedYear()).toString().padStart(2, '0')
                band.copy(end = end, label = if (stride == 1) band.label else "S$firstWeek-$lastWeek", description = if (stride == 1) band.description
                    else "${band.description} · Faixa até ${end.dateText()}")
            }
        }
        else -> {
            val step = labelStride(width, 1f, 30f).toLong()
            (0L until axis.days step step).map { offset ->
                val date = axis.start.plusDays(offset)
                GanttDateBand("day-$date", date, minOf(date.plusDays(step - 1), axis.end), date.dayOfMonth.toString().padStart(2, '0'), date.dateText())
            }
        }
    }

    private fun labelStride(width: Float, days: Float, minimum: Float) = ceil(minimum / (width * days / axis.days)).toInt().coerceAtLeast(1)

    fun gridDates(width: Float = preferredWidth): List<LocalDate> = when (scale) {
        GanttScale.DAYS -> bands(3, width).map { it.start }
        GanttScale.WEEKS -> bands(2, width).map { it.start }
        GanttScale.MONTHS -> months.map { it.start }.filterIndexed { index, _ -> index % labelStride(width, 30f, 50f) == 0 }
    }

    fun fraction(band: GanttDateBand): Float = DAYS.between(band.start, band.end.plusDays(1)).toFloat() / axis.days
    val condensed: Boolean get() = when (scale) {
        GanttScale.DAYS -> axis.days * 36f > preferredWidth
        GanttScale.WEEKS -> axis.days / 7f * 82f > preferredWidth
        GanttScale.MONTHS -> axis.days / 30.4375f * 112f > preferredWidth
    }
}
