package br.com.obracontrol

import java.text.Normalizer

internal data class PaymentShare(val label: String, val amount: Long)

internal data class PaymentBreakdown(val byPayer: List<PaymentShare>, val byMethod: List<PaymentShare>) {
    val total: Long get() = byPayer.sumOf { it.amount }
}

private fun String.paymentLabel(): String = trim().replace(Regex("\\s+"), " ")
private fun String.paymentIdentity(): String = paymentLabel().lowercase(brazil)
private fun String.methodIdentity(): String = Normalizer.normalize(paymentIdentity(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")

private val methodLabels = mapOf(
    "pix" to "Pix", "dinheiro" to "Dinheiro", "cartao" to "Cartão",
    "cartao de credito" to "Cartão de crédito", "cartao credito" to "Cartão de crédito",
    "cartao de debito" to "Cartão de débito", "cartao debito" to "Cartão de débito",
    "boleto" to "Boleto", "transferencia bancaria" to "Transferência bancária",
    "nao informado" to UNREPORTED,
)

/** Both charts reconcile to the same recorded payments; purchase terms never create paid amounts. */
internal fun Project.paymentBreakdown(): PaymentBreakdown {
    val payerLabels = linkedMapOf<String, String>()
    (payerNames + purchases.map { it.payer } + payments.map { it.payer }).forEach { label ->
        if (label.isNotBlank()) payerLabels.putIfAbsent(label.paymentIdentity(), label.paymentLabel())
    }
    payerLabels[UNREPORTED.paymentIdentity()] = UNREPORTED
    val customMethods = linkedMapOf<String, String>()
    payments.forEach { customMethods.putIfAbsent(it.method.methodIdentity(), it.method.paymentLabel()) }
    fun aggregate(label: (Payment) -> String): List<PaymentShare> = payments.groupBy(label)
        .map { (name, payments) -> PaymentShare(name, payments.sumOf { it.amount }) }
        .filter { it.amount > 0 }
        .sortedWith(compareByDescending<PaymentShare> { it.amount }.thenBy { it.label.lowercase(brazil) })
    return PaymentBreakdown(
        byPayer = aggregate { payment ->
            payerLabels[payment.payer.paymentIdentity()]?.takeIf(String::isNotBlank) ?: UNREPORTED
        },
        byMethod = aggregate { payment ->
            val name = payment.method.paymentLabel()
            methodLabels[name.methodIdentity()] ?: customMethods[name.methodIdentity()]?.takeIf(String::isNotBlank) ?: UNREPORTED
        },
    )
}
