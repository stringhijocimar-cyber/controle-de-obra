package br.com.obracontrol

/** Equal values on different parcels or transaction references are distinct records. Never auto-delete them. */
internal fun Project.similarExistingPayments(candidate: Payment): List<Payment> =
    if (payments.any { it.id == candidate.id }) emptyList()
    else payments.filter { similarPaymentKey(it) == similarPaymentKey(candidate) }

internal data class PaymentReviewCost(val target: CostTarget, val payments: List<Payment>) {
    val key get() = "${target.kind.name}/${target.id}"
    val paid get() = payments.sumOf { it.amount }
    val repeatedGroups get() = payments.groupBy(::similarPaymentKey).values.filter { it.size > 1 }
    val possibleDuplicates get() = repeatedGroups.sumOf { it.size - 1 }
    val overpaid get() = target.total?.let { (paid - it).coerceAtLeast(0) } ?: 0
    val needsReview get() = possibleDuplicates > 0 || overpaid > 0
}

internal fun Project.paymentReviewCosts(): List<PaymentReviewCost> = payments.groupBy { it.kind to it.targetId }.mapNotNull { (key, rows) ->
    costTarget(key.first, key.second)?.let { PaymentReviewCost(it, rows) }
}.sortedWith(compareByDescending<PaymentReviewCost> { it.needsReview }.thenByDescending { it.possibleDuplicates }.thenBy { it.target.name.lowercase(brazil) })

internal fun Project.paymentParcelLabel(payment: Payment): String = scheduledPayments.firstOrNull {
    it.id == payment.scheduleId && it.kind == payment.kind && it.targetId == payment.targetId
}?.label ?: "Pagamento avulso"
