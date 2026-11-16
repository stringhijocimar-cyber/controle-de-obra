package br.com.obracontrol

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.TemporalAdjusters.nextOrSame
import java.time.temporal.TemporalAdjusters.previousOrSame

internal data class GanttPeriod(val start: LocalDate, val end: LocalDate) {
    fun dates(): String = if (start == end) start.dateText() else "${start.dateText()} a ${end.dateText()}"
}

internal data class GanttRow(val stage: Stage, val planned: GanttPeriod?, val actual: GanttPeriod?) {
    val plannedDates: String get() = planned?.dates() ?: UNDEFINED

    val actualDates: String get() {
        val period = actual ?: return UNDEFINED
        if (stage.actualEnd != null) return period.dates()
        val detail = if (stage.progress < 100) "em curso" else "fim a confirmar"
        return "${period.start.dateText()} · $detail"
    }
}

internal fun Stage.ganttRow(asOf: LocalDate): GanttRow {
    val planned = plannedStart?.let { start -> plannedEnd?.let { GanttPeriod(start, it) } }
    val actual = when {
        actualStart != null && actualEnd != null -> GanttPeriod(actualStart, actualEnd)
        actualStart != null && actualStart <= asOf -> {
            val end = if (progress < 100) asOf else actualStart
            GanttPeriod(actualStart, end)
        }
        actualEnd != null -> GanttPeriod(actualEnd, actualEnd)
        else -> null
    }
    return GanttRow(this, planned, actual)
}

internal class GanttAxis private constructor(val start: LocalDate, val end: LocalDate) {
    val days = DAYS.between(start, end) + 1
    private val tickStep = when {
        days <= 14 -> 1L
        days <= 182 -> 7L
        days <= 728 -> 28L
        else -> ((days + 195) / 196) * 7
    }
    val ticks = (0L until days step tickStep).map(start::plusDays)
    val preferredWidth = ticks.size * if (tickStep == 1L) 41 else 68
    val includeTickYear = tickStep > 1 && start.year != end.year

    fun boundary(date: LocalDate): Float = DAYS.between(start, date).toDouble().div(days).toFloat()

    fun dayCenter(date: LocalDate): Float = (boundary(date) + boundary(date.plusDays(1))) / 2f

    companion object {
        fun from(rows: List<GanttRow>): GanttAxis? {
            val periods = rows.flatMap { listOfNotNull(it.planned, it.actual) }
            val first = periods.minOfOrNull { it.start } ?: return null
            val last = periods.maxOf { it.end }
            if (DAYS.between(first, last) < 14) {
                return GanttAxis(first, maxOf(last, first.plusDays(6)))
            }
            return GanttAxis(first.with(previousOrSame(DayOfWeek.MONDAY)), last.with(nextOrSame(DayOfWeek.SUNDAY)))
        }
    }
}
