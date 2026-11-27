package br.com.obracontrol

import java.time.LocalDate
import java.time.temporal.ChronoUnit.WEEKS

internal enum class CashView(val label: String) { TOTAL("Total"), PENDING("A pagar"), PAID("Pago efetivo") }
internal enum class FlowLayer { FAMILY, STAGE, COST, ENTRY }
internal data class FlowNode(
    val key: String, val label: String, val layer: FlowLayer, val entries: List<CashFlowEntry>,
    val children: List<FlowNode> = emptyList(), val kind: CostKind? = null, val targetId: String? = null,
) {
    val paid: Long get() = entries.filter { it.paid }.sumOf { it.amount ?: 0 }
    val pending: TableAmountTotal get() = tableAmountTotal(entries.filterNot { it.paid }.map { it.amount })
    fun amounts(week: LocalDate?, view: CashView): TableAmountTotal = tableAmountTotal(entries.filter {
        it.date?.cashWeekEnding() == week && (view == CashView.TOTAL || it.paid == (view == CashView.PAID))
    }.map { it.amount })
}

internal class DisbursementTable(val project: Project, category: CostCategory? = null) {
    val entries = CostPaymentCalendar(project).entries.filter { category == null || it.target.category == category }
    val total = FlowNode("total", "TOTAL DA OBRA", FlowLayer.FAMILY, entries)
    val firstWeek: LocalDate? = entries.mapNotNull { it.date }.minOrNull()?.cashWeekEnding()
    val lastWeek: LocalDate? = entries.mapNotNull { it.date }.maxOrNull()?.cashWeekEnding()
    val weekCount = if (firstWeek == null || lastWeek == null) 0 else WEEKS.between(firstWeek, lastWeek).toInt() + 1
    val pageCount = ((weekCount + 25) / 26).coerceAtLeast(1)
    fun columns(page: Int): List<LocalDate?> {
        val index = page.coerceIn(0, pageCount - 1)
        val weeks = if (firstWeek == null) emptyList() else (index * 26 until minOf((index + 1) * 26, weekCount)).map { firstWeek.plusWeeks(it.toLong()) }
        return weeks + if (index == pageCount - 1 && entries.any { it.date == null }) listOf(null) else emptyList()
    }
    val roots: List<FlowNode> = CostCategory.entries.mapNotNull { family ->
        val all = entries.filter { it.target.category == family }
        if (all.isEmpty()) null else {
            val stages = all.groupBy { entry -> if (entry.target.kind == CostKind.LABOR) entry.target.id
                else project.purchases.firstOrNull { it.id == entry.target.id }?.stageId }
            FlowNode("family-${family.name}", family.label, FlowLayer.FAMILY, all, stages.map { (stageId, rows) ->
                FlowNode("stage-${family.name}-${stageId ?: "none"}", project.stageName(stageId) ?: "Sem etapa vinculada", FlowLayer.STAGE, rows,
                    rows.groupBy { it.target.kind to it.target.id }.map { (cost, values) ->
                        FlowNode("cost-${cost.first.name}/${cost.second}", if (cost.first == CostKind.LABOR) "Mão de obra da etapa" else values.first().target.name,
                            FlowLayer.COST, values, values.map { entry ->
                                FlowNode("entry-${entry.key}", if (entry.paid) "Pago · ${entry.date.dateOr("Sem data")}" else entry.label + if (entry.estimatedDate) " · data estimada" else "",
                                    FlowLayer.ENTRY, listOf(entry), kind = cost.first, targetId = cost.second)
                            }, kind = cost.first, targetId = cost.second)
                    })
            })
        }
    }
    fun visible(open: Set<String>): List<Pair<FlowNode, Int>> = buildList {
        fun visit(node: FlowNode, depth: Int) { add(node to depth); if (node.key in open) node.children.forEach { visit(it, depth + 1) } }
        roots.forEach { visit(it, 0) }
    }
    fun people(node: FlowNode, method: Boolean): String = node.entries.map { entry ->
        if (entry.paid) project.payments.firstOrNull { it.id == entry.paymentId }?.let { if (method) it.method else it.payer }.orEmpty()
        else if (entry.target.kind == CostKind.MATERIAL) project.purchases.firstOrNull { it.id == entry.target.id }?.let { if (method) it.method else it.payer }.orEmpty()
        else ""
    }.map { it.textOr("A definir") }.distinct().joinToString(" · ").ifBlank { "—" }
}
