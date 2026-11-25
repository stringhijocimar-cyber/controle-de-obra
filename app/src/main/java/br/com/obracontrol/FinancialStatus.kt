package br.com.obracontrol

import java.time.LocalDate

internal enum class FinancialStatus(val label: String, val icon: String) {
    PAID("Pago", "✓"),
    PARTIAL("Parcial", "◐"),
    PENDING("Em aberto", "○"),
    OVERDUE("Atrasado", "!"),
    PARTIAL_OVERDUE("Parcial em atraso", "!"),
    UNPRICED("A definir", "?"),
    PAID_UNPRICED("Total a confirmar", "◐"),
    OVERPAID("Pago a maior", "✓"),
    NO_COST("Sem custo", "–"),
}

internal fun financialStatus(total: Long?, paid: Long, due: LocalDate?, today: LocalDate): FinancialStatus = when {
    total == null && paid > 0 -> FinancialStatus.PAID_UNPRICED
    total == null -> FinancialStatus.UNPRICED
    paid > total -> FinancialStatus.OVERPAID
    total == 0L -> FinancialStatus.NO_COST
    paid == total -> FinancialStatus.PAID
    due != null && due < today && paid > 0 -> FinancialStatus.PARTIAL_OVERDUE
    due != null && due < today -> FinancialStatus.OVERDUE
    paid > 0 -> FinancialStatus.PARTIAL
    else -> FinancialStatus.PENDING
}

internal fun Purchase.installmentText(): String {
    val count = installmentCount ?: return UNREPORTED
    if (count == 1) return "À vista"
    val amount = forecast ?: return "${count}x · valor a definir"
    val base = amount / count
    val extra = amount % count
    if (extra == 0L) return "${count}x de ${base.brl()}"
    return "${count}x\n${extra} de ${(base + 1).brl()} + ${count - extra} de ${base.brl()}"
}

internal fun Project.purchasePayers(purchase: Purchase): String =
    (listOf(purchase.payer) + payments.filter { it.kind == CostKind.MATERIAL && it.targetId == purchase.id }.map { it.payer })
        .filter(String::isNotBlank).distinctBy { it.trim().lowercase(brazil) }.joinToString(" · ").textOr()

internal fun Project.purchaseMethods(purchase: Purchase): String =
    (listOf(purchase.method) + payments.filter { it.kind == CostKind.MATERIAL && it.targetId == purchase.id }.map { it.method })
        .filter(String::isNotBlank).distinctBy { it.trim().lowercase(brazil) }.joinToString(" · ").textOr()
