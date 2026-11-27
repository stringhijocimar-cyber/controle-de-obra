package br.com.obracontrol

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters.previousOrSame

internal data class DisbursementDay(val date: LocalDate, val entries: List<CashFlowEntry>) {
    val paid: Long get() = entries.filter { it.paid }.sumOf { it.amount ?: 0 }
    val pending: TableAmountTotal get() = tableAmountTotal(entries.filterNot { it.paid }.map { it.amount })
}

internal data class DisbursementMonth(val month: YearMonth, val entries: List<CashFlowEntry>) {
    val paid: Long get() = entries.filter { it.paid }.sumOf { it.amount ?: 0 }
    val pending: TableAmountTotal get() = tableAmountTotal(entries.filterNot { it.paid }.map { it.amount })
    private val byDate = entries.groupBy { it.date }
    private val first = month.atDay(1).with(previousOrSame(DayOfWeek.MONDAY))
    private val dayCount = ((month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7) * 7
    val weeks: List<List<DisbursementDay>> = (0 until dayCount).map { offset ->
        val date = first.plusDays(offset.toLong())
        DisbursementDay(date, byDate[date].orEmpty())
    }.chunked(7)
}

/** Reuses the reconciled cash flow: actual payment date and outstanding parcel due date. */
internal class MonthlyDisbursementCalendar(project: Project) {
    val entries = CostPaymentCalendar(project).entries
    val months = entries.filter { it.date != null }.groupBy { YearMonth.from(it.date!!) }
        .toSortedMap().map { (month, rows) -> DisbursementMonth(month, rows) }
    val undated = entries.filter { it.date == null }
}
