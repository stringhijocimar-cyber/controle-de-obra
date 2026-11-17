package br.com.obracontrol

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.previousOrSame

internal enum class LaborSpendStatus(val label: String) {
    PAID("Pago"), DUE("A pagar"), ESTIMATED("A pagar · estimativa pelo fim previsto"),
}

internal data class LaborSpendEntry(
    val stageId: String,
    val stageName: String,
    val date: LocalDate?,
    val amount: Long?,
    val status: LaborSpendStatus,
) {
    val paid get() = status == LaborSpendStatus.PAID
}

internal data class LaborSpendWeek(val start: LocalDate?, val entries: List<LaborSpendEntry>) {
    val label = start?.let { "${it.dateText()} a ${it.plusDays(6).dateText()}" } ?: "Sem data"
    val paid = entries.filter { it.paid }.sumOf { it.amount ?: 0 }
    val balance = entries.filterNot { it.paid }.sumOf { it.amount ?: 0 }
    val undefined = entries.count { !it.paid && it.amount == null }
}

internal class LaborSpendCalendar(project: Project, balance: Balance) {
    private val stageNames = project.stages.associate { it.id to it.name }
    private val entries = buildList {
        for (payment in project.payments) {
            if (payment.kind == CostKind.LABOR) {
                add(LaborSpendEntry(payment.targetId, stageNames[payment.targetId].textOr(UNDEFINED),
                    payment.date, payment.amount, LaborSpendStatus.PAID))
            }
        }
        for (forecast in balance.laborDisbursements) {
            val status = if (forecast.dateEstimated) LaborSpendStatus.ESTIMATED else LaborSpendStatus.DUE
            add(LaborSpendEntry(forecast.targetId, forecast.name, forecast.forecastDate, forecast.amount, status))
        }
    }.sortedWith(compareBy(nullsLast()) { it.date })

    val byStage = entries.groupBy { it.stageId }
    val weeks = entries.groupBy { it.date?.with(previousOrSame(DayOfWeek.MONDAY)) }
        .map { (start, items) -> LaborSpendWeek(start, items) }
}
