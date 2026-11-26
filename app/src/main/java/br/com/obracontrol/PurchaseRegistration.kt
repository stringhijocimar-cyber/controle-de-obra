package br.com.obracontrol

internal fun Project.registerPayer(name: String): Project {
    val trimmed = name.trim()
    require(trimmed.isNotBlank()) { "Informe o nome de quem pagou." }
    if (payerNames.any { it.equals(trimmed, ignoreCase = true) }) return this
    return copy(payerNames = payerNames + trimmed)
}

internal fun Project.recordPurchase(purchase: Purchase, payment: Payment? = null, additionalPayment: Boolean = false): Project {
    var result = copy(purchases = purchases.upsert(purchase, Purchase::id))
    if (workPlans.any { plan -> plan.stages.any { stage -> stage.linkedStageId != purchase.stageId && stage.items.any { it.purchaseId == purchase.id } } }) {
        result = result.unlinkPlanningPurchase(purchase.id)
    }
    if (purchase.payer.isNotBlank()) result = result.registerPayer(purchase.payer)
    if (payment != null) {
        require(payment.kind == CostKind.MATERIAL && payment.targetId == purchase.id) {
            "O pagamento precisa estar vinculado a esta compra."
        }
        require(additionalPayment || paymentsFor(CostKind.MATERIAL, purchase.id).isEmpty() || payments.any { it.id == payment.id }) {
            "Selecione o pagamento existente para editar ou escolha Novo pagamento adicional."
        }
        result = result.copy(payments = result.payments.upsert(payment, Payment::id))
        if (payment.payer.isNotBlank()) result = result.registerPayer(payment.payer)
    }
    result.validate()
    return result
}

internal fun <T> List<T>.upsert(value: T, id: (T) -> String): List<T> =
    if (any { id(it) == id(value) }) map { if (id(it) == id(value)) value else it } else this + value
