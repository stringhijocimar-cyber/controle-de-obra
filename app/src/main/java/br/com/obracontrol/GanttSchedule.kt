package br.com.obracontrol

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.TemporalAdjusters.nextOrSame
import java.time.temporal.TemporalAdjusters.previousOrSame

internal data class GanttPeriod(val start: LocalDate, val end: LocalDate) {
    fun dates(): String = if (start == end) start.dateText() else "${start.dateText()} a ${end.dateText()}"
}

internal data class GanttRow(
    val stage: Stage,
    val planned: GanttPeriod?,
    val actual: GanttPeriod?,
    val spend: List<LaborSpendEntry>,
    val deliveries: List<GanttDelivery> = emptyList(),
) {
    val materialDates get() = deliveries.flatMap { listOfNotNull(it.expected, it.received) }
    val materialPeriod get() = materialDates.takeIf { it.isNotEmpty() }?.let { GanttPeriod(it.min(), it.max()) }
    val totalPeriod: GanttPeriod? get() {
        val dates = listOfNotNull(planned?.start, planned?.end, actual?.start, actual?.end, stage.plannedEnd) + materialDates
        return dates.takeIf { it.isNotEmpty() }?.let { GanttPeriod(it.min(), it.max()) }
    }
    fun elapsedDays(asOf: LocalDate): Long = totalPeriod?.let {
        (DAYS.between(it.start, minOf(asOf, it.end)) + 1).coerceAtLeast(0)
    } ?: 0
    val totalDays get() = totalPeriod?.let { DAYS.between(it.start, it.end) + 1 } ?: 0
    fun durationText(asOf: LocalDate) = if (totalPeriod == null) "Prazo a definir" else "${elapsedDays(asOf)} / $totalDays dias decorridos"
    val plannedDates: String get() = planned?.dates() ?: UNDEFINED

    val actualDates: String get() {
        val period = actual ?: return UNDEFINED
        if (stage.actualEnd != null) return period.dates()
        val detail = if (stage.progress < 100) "em curso" else "fim a confirmar"
        return "${period.start.dateText()} · $detail"
    }
}

internal data class GanttDelivery(val id: String, val name: String, val expected: LocalDate?, val received: LocalDate?, val progress: Int, val nature: CostNature = CostNature.MATERIAL)

internal fun Project.ganttRows(asOf: LocalDate, spend: LaborSpendCalendar): List<GanttRow> = stages.map { stage ->
    stage.ganttRow(asOf, spend.byStage[stage.id].orEmpty()).copy(deliveries = purchases.filter {
        it.stageId == stage.id
    }.map { GanttDelivery(it.id, it.name, it.deliveryExpected, it.receivedDate, it.receivedPercent, it.nature) })
}

internal fun Stage.ganttRow(asOf: LocalDate, spend: List<LaborSpendEntry> = emptyList()): GanttRow {
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
    return GanttRow(this, planned, actual, spend)
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
            val periods = rows.flatMap { listOfNotNull(it.planned, it.actual, it.totalPeriod) }
            val first = periods.minOfOrNull { it.start } ?: return null
            val last = periods.maxOf { it.end }
            if (DAYS.between(first, last) < 14) {
                return GanttAxis(first, maxOf(last, first.plusDays(6)))
            }
            return GanttAxis(first.with(previousOrSame(DayOfWeek.MONDAY)), last.with(nextOrSame(DayOfWeek.SUNDAY)))
        }
    }
}
