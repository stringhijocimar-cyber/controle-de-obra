package br.com.obracontrol

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.previousOrSame

internal enum class CostCategory(val label: String) {
    MATERIAL("Material"), SERVICE("Serviço"), LABOR("Mão de obra")
}

internal data class CostTarget(
    val kind: CostKind, val id: String, val name: String, val total: Long?,
    val due: LocalDate?, val estimatedDate: Boolean, val category: CostCategory,
)

internal fun Project.costTarget(kind: CostKind, id: String): CostTarget? = when (kind) {
    CostKind.LABOR -> stages.firstOrNull { it.id == id }?.let {
        CostTarget(kind, id, it.name, it.budget, it.dueDate ?: it.plannedEnd,
            it.dueDate == null && it.plannedEnd != null, CostCategory.LABOR)
    }
    CostKind.MATERIAL -> purchases.firstOrNull { it.id == id }?.let {
        CostTarget(kind, id, it.name, it.forecast, it.dueDate, false,
            if (it.nature == CostNature.SERVICE) CostCategory.SERVICE else CostCategory.MATERIAL)
    }
}

internal data class ScheduledPayment(
    val id: String = newId(), val kind: CostKind, val targetId: String,
    val amount: Long, val date: LocalDate? = null, val label: String = "Pagamento previsto",
)

internal data class CostPaymentPosition(
    val target: CostTarget, val schedule: ScheduledPayment?, val gross: Long?, val applied: Long,
    val remaining: Long?, val date: LocalDate?, val estimatedDate: Boolean, val label: String,
) {
    val key get() = schedule?.id ?: "saldo-${target.kind}-${target.id}"
    val completed get() = remaining == 0L && (gross ?: 0) > 0
}

/** Direct parcel links are applied first. Unlinked payments are deducted once, by due date. */
internal fun Project.costPaymentPositions(kind: CostKind, targetId: String): List<CostPaymentPosition> {
    val target = costTarget(kind, targetId) ?: return emptyList()
    val plans = scheduledPayments.filter { it.kind == kind && it.targetId == targetId }
        .sortedWith(compareBy<ScheduledPayment, LocalDate?>(nullsLast()) { it.date })
    val actual = payments.filter { it.kind == kind && it.targetId == targetId }
    val direct = actual.filter { it.scheduleId != null }.groupBy { it.scheduleId }
        .mapValues { (_, values) -> values.sumOf { it.amount } }
    var pool = actual.sumOf { it.amount } - plans.sumOf { minOf(direct[it.id] ?: 0, it.amount) }
    val result = plans.map { plan ->
        val linked = minOf(direct[plan.id] ?: 0, plan.amount)
        val extra = minOf(pool, plan.amount - linked).coerceAtLeast(0)
        pool -= extra
        CostPaymentPosition(target, plan, plan.amount, linked + extra, plan.amount - linked - extra,
            plan.date, false, plan.label)
    }.toMutableList()
    val unscheduled = target.total?.minus(plans.sumOf { it.amount })
    if (plans.isEmpty() || unscheduled == null || unscheduled > 0) {
        val applied = if (unscheduled == null) pool else minOf(pool, unscheduled)
        result += CostPaymentPosition(target, null, unscheduled, applied,
            unscheduled?.let { (it - applied).coerceAtLeast(0) },
            if (plans.isEmpty()) target.due else null, plans.isEmpty() && target.estimatedDate,
            if (plans.isEmpty()) "Saldo do custo" else "Saldo sem programação")
    }
    return result
}

internal fun buildPaymentSchedule(
    kind: CostKind, targetId: String, total: Long, count: Int, firstDate: LocalDate,
    weekly: Boolean, existingIds: List<String> = emptyList(),
): List<ScheduledPayment> {
    require(count in 1..120) { "Informe de 1 a 120 parcelas." }
    require(total >= count) { "O valor é insuficiente para dividir nessa quantidade de parcelas." }
    checkedMoney(total)
    return (0 until count).map { index ->
        ScheduledPayment(existingIds.getOrNull(index) ?: newId(), kind, targetId,
            total / count + if (index < total % count) 1 else 0,
            if (weekly) firstDate.plusWeeks(index.toLong()) else firstDate.plusMonths(index.toLong()),
            if (count == 1) "Pagamento único" else "Parcela ${index + 1}/$count")
    }
}

internal fun Project.replacePaymentSchedule(kind: CostKind, targetId: String, rows: List<ScheduledPayment>): Project {
    require(rows.all { it.kind == kind && it.targetId == targetId }) { "A programação precisa pertencer a este custo." }
    val oldIds = scheduledPayments.filter { it.kind == kind && it.targetId == targetId }.map { it.id }.toSet()
    val removedIds = oldIds - rows.map { it.id }.toSet()
    return copy(
        scheduledPayments = scheduledPayments.filterNot { it.id in oldIds } + rows,
        payments = payments.map { if (it.scheduleId in removedIds) it.copy(scheduleId = null) else it },
    ).also { it.validate() }
}

internal data class CashFlowEntry(
    val key: String, val target: CostTarget, val date: LocalDate?, val amount: Long?, val paid: Boolean,
    val label: String, val paymentId: String? = null, val scheduleId: String? = null, val estimatedDate: Boolean = false,
)

internal data class CashFlowWeek(val start: LocalDate?, val entries: List<CashFlowEntry>) {
    val key get() = start?.toString() ?: "sem-data"
    val label get() = start?.let { "${it.dateText()} a ${it.plusDays(6).dateText()}" } ?: "Sem data"
    val paid get() = entries.filter { it.paid }.sumOf { it.amount ?: 0 }
    val pending get() = tableAmountTotal(entries.filterNot { it.paid }.map { it.amount })
    val knownTotal get() = paid + pending.known
}

internal class CostPaymentCalendar(project: Project) {
    val positions = CostKind.entries.flatMap { kind ->
        project.targets(kind).flatMap { (id, _) -> project.costPaymentPositions(kind, id) }
    }
    val pending = positions.filter { it.remaining == null || it.remaining > 0 }.map { position ->
        CashFlowEntry(position.key, position.target, position.date, position.remaining, false, position.label,
            scheduleId = position.schedule?.id, estimatedDate = position.estimatedDate)
    }
    val entries = (project.payments.map { payment ->
        CashFlowEntry("pago-${payment.id}", requireNotNull(project.costTarget(payment.kind, payment.targetId)),
            payment.date, payment.amount, true, listOf(payment.payer.textOr(), payment.method.textOr()).joinToString(" · "),
            paymentId = payment.id, scheduleId = payment.scheduleId)
    } + pending).sortedWith(compareBy(nullsLast()) { it.date })
    val weeks = entries.groupBy { it.date?.with(previousOrSame(DayOfWeek.MONDAY)) }
        .map { (start, items) -> CashFlowWeek(start, items) }
        .sortedWith(compareBy(nullsLast()) { it.start })
}
