package br.com.obracontrol

import java.text.Normalizer

internal data class PurchaseTermsChange(val purchaseId: String, val method: String, val installmentCount: Int?, val total: Long?)

internal fun Project.applyPurchaseTerms(change: PurchaseTermsChange): Project {
    val current = purchases.firstOrNull { it.id == change.purchaseId } ?: throw IllegalArgumentException("A compra vinculada não existe.")
    require(change.installmentCount == null || change.installmentCount > 0) { "Informe uma quantidade de parcelas maior que zero." }
    change.total?.let(::checkedMoney)
    return copy(purchases = purchases.upsert(current.copy(method = change.method.trim(),
        installmentCount = change.installmentCount, total = change.total), Purchase::id)).also(Project::validate)
}

/** One atomic edit: purchase terms and this actual payment. Other payments/proofs and due dates stay intact. */
internal fun Project.recordPayment(payment: Payment, terms: PurchaseTermsChange? = null, allowSimilar: Boolean = false): Project {
    require(allowSimilar || similarExistingPayments(payment).isEmpty()) {
        "Já existe um pagamento com os mesmos dados. Edite o existente ou confirme que este é outro pagamento."
    }
    if (terms != null) require(payment.kind == CostKind.MATERIAL && payment.targetId == terms.purchaseId) {
        "As condições precisam pertencer à compra deste pagamento."
    }
    val updated = if (terms == null) this else applyPurchaseTerms(terms)
    val next = updated.copy(payments = updated.payments.upsert(payment, Payment::id))
    return (if (payment.payer.isBlank()) next else next.registerPayer(payment.payer)).also(Project::validate)
}

internal fun Project.paymentMethodOptions(): List<String> =
    (listOf("Pix", "Dinheiro", "Cartão de crédito", "Cartão de débito", "Boleto", "Transferência bancária") +
        purchases.map { it.method } + payments.map { it.method } + paymentAudit.map { it.payment.method }).map(String::trim).filter(String::isNotBlank)
        .distinctBy { it.lowercase(brazil) }

internal fun isCardMethod(method: String): Boolean = Normalizer.normalize(method.trim().lowercase(brazil), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").startsWith("cartao")

internal fun Project.cardAndInstallmentPurchases(): List<Purchase> = purchases.filter { order ->
    isCardMethod(order.method) || (order.installmentCount ?: 0) > 1 ||
        payments.any { it.kind == CostKind.MATERIAL && it.targetId == order.id && isCardMethod(it.method) }
}

internal fun readPurchaseTerms(order: Purchase, method: String, condition: String, count: String, total: String): PurchaseTermsChange =
    PurchaseTermsChange(order.id, method.trim(), when (condition) {
        "single" -> 1
        "installments" -> count.toIntOrNull()?.takeIf { it >= 2 } ?: throw IllegalArgumentException("Informe pelo menos 2 parcelas.")
        else -> null
    }, parseMoney(total))

internal fun Purchase.termsKey() = when (installmentCount) { null -> "unknown"; 1 -> "single"; else -> "installments" }
