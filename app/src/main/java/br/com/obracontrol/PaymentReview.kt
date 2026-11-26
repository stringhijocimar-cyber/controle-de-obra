package br.com.obracontrol

import java.time.LocalDate

internal data class PaymentAudit(
    val id: String = newId(), val payment: Payment, val date: LocalDate,
    val reason: String, val voided: Boolean, val restoredAt: LocalDate? = null,
    val costName: String = "",
)

internal fun Project.paymentsFor(kind: CostKind, target: String) = payments.filter { it.kind == kind && it.targetId == target }

internal fun similarPaymentKey(payment: Payment): String = listOf(payment.kind.name, payment.targetId, payment.amount,
    payment.date, payment.payer.trim().lowercase(brazil), payment.method.trim().lowercase(brazil)).joinToString("|")

/** Apply only reviewed rows. Original records and their proofs remain in the audit, outside paid totals. */
internal fun Project.reviewPayments(kind: CostKind, targetId: String, amounts: Map<String, Long?>,
    reason: String, date: LocalDate = LocalDate.now()): Project {
    require(costTarget(kind, targetId) != null) { "O custo não existe." }
    val source = paymentsFor(kind, targetId)
    require(amounts.keys == source.map { it.id }.toSet()) { "Confira todos os pagamentos do custo antes de salvar." }
    val changed = source.filter { amounts[it.id] != it.amount }
    if (changed.isEmpty()) return this
    require(reason.isNotBlank()) { "Informe o motivo da correção." }
    amounts.values.filterNotNull().forEach { require(it > 0) { "O pagamento deve ser positivo; desmarque os duplicados." }; checkedMoney(it) }
    val updated = payments.mapNotNull { payment ->
        if (payment.kind != kind || payment.targetId != targetId) payment
        else amounts[payment.id]?.let { payment.copy(amount = it) }
    }
    val history = changed.map { PaymentAudit(payment = it, date = date, reason = reason.trim(), voided = amounts[it.id] == null, costName = targetName(it)) }
    return copy(payments = updated, paymentAudit = paymentAudit + history).also(Project::validate)
}

internal fun Project.restoreReviewedPayment(auditId: String, date: LocalDate = LocalDate.now()): Project {
    val entry = paymentAudit.firstOrNull { it.id == auditId } ?: throw IllegalArgumentException("Histórico não encontrado.")
    require(entry.voided && entry.restoredAt == null) { "Este registro não está disponível para restauração." }
    require(payments.none { it.id == entry.payment.id }) { "O pagamento já está ativo." }
    require(costTarget(entry.payment.kind, entry.payment.targetId) != null) { "Restaure ou cadastre o custo antes de restaurar o pagamento." }
    val restored = entry.payment.let { payment -> payment.copy(scheduleId = payment.scheduleId?.takeIf { id ->
        scheduledPayments.any { it.id == id && it.kind == payment.kind && it.targetId == payment.targetId }
    }) }
    return copy(payments = payments + restored, paymentAudit = paymentAudit.map {
        if (it.id == auditId) it.copy(restoredAt = date) else it
    }).also(Project::validate)
}

internal fun Project.stageOptions(): List<Pair<String, String>> = listOf("" to "Sem vínculo") + stages.map { it.id to it.name }

internal data class LinkedCost(val kind: CostKind, val id: String, val stageId: String?, val nature: CostNature, val label: String) {
    val key: String get() = "${kind.name}/$id"
}
internal fun Project.linkedCosts(): List<LinkedCost> =
    stages.map { LinkedCost(CostKind.LABOR, it.id, it.id, CostNature.SERVICE, "Mão de obra · ${it.name}") } +
        purchases.map { LinkedCost(CostKind.MATERIAL, it.id, it.stageId, it.nature, it.name) }

internal fun Project.linkedCostOptions(nature: CostNature, stageId: String): List<LinkedCost> =
    linkedCosts().filter { it.nature == nature && it.stageId.orEmpty() == stageId }
