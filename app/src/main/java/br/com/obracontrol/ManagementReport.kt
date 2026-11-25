package br.com.obracontrol

import java.time.LocalDate

internal data class ManagementCostGroup(val category: CostCategory, val forecast: Long, val paid: Long, val undefined: Int) {
    val balance: Long get() = forecast - paid
}

internal enum class DueHorizon(val label: String) {
    OVERDUE("Vencidos"), NEXT_SEVEN("Hoje e próximos 6 dias"), DAYS_EIGHT_TO_THIRTY("Do 8º ao 30º dia"), LATER("Após 30 dias"), UNDATED("Sem data")
}

internal class ManagementReportSummary(val project: Project, val today: LocalDate) {
    private val balance = Balance(project)
    val calendar = CostPaymentCalendar(project)
    val groups: List<ManagementCostGroup> = CostCategory.entries.map { category ->
        if (category == CostCategory.LABOR) ManagementCostGroup(category, balance.laborForecast, balance.laborPaid,
            if (project.laborBudget != null) 0 else project.stages.count { it.budget == null })
        else {
            val orders = project.purchases.filter { (it.nature == CostNature.MATERIAL) == (category == CostCategory.MATERIAL) }
            ManagementCostGroup(category, orders.sumOf { it.forecast ?: balance.paid(CostKind.MATERIAL, it.id) },
                orders.sumOf { balance.paid(CostKind.MATERIAL, it.id) }, orders.count { it.forecast == null })
        }
    }
    val forecast: Long get() = groups.sumOf { it.forecast }
    val paid: Long get() = groups.sumOf { it.paid }
    val undefined: Int get() = groups.sumOf { it.undefined }
    fun horizon(date: LocalDate?): DueHorizon = when {
        date == null -> DueHorizon.UNDATED
        date < today -> DueHorizon.OVERDUE
        date <= today.plusDays(6) -> DueHorizon.NEXT_SEVEN
        date <= today.plusDays(29) -> DueHorizon.DAYS_EIGHT_TO_THIRTY
        else -> DueHorizon.LATER
    }
    fun pending(window: DueHorizon): TableAmountTotal = tableAmountTotal(calendar.pending.filter { horizon(it.date) == window }.map { it.amount })
    val cardPurchases: List<Purchase> = project.cardAndInstallmentPurchases()
    val decisions: List<Pair<String, String>> = (balance.alerts() + project.stages.filter {
        it.progress < 100 && it.plannedEnd?.let { end -> end < today } == true
    }.map { it.name to "Etapa atrasada: revisar prazo e plano de execução." } + project.issues.filterNot { it.closed }
        .map { it.title to it.action.textOr("Definir responsável e próxima ação.") }).distinct()
}
