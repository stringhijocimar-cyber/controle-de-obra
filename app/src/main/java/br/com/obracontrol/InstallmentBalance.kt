package br.com.obracontrol

/** The purchase terms are a commitment. Only recorded payments reduce its balance. */
internal data class InstallmentBalance(val purchase: Purchase, val paid: Long) {
    val total: Long? get() = purchase.forecast
    val remaining: Long? get() = total?.let { (it - paid).coerceAtLeast(0) }
    val credit: Long? get() = total?.let { (paid - it).coerceAtLeast(0) }
}

internal fun Project.installmentBalances(balance: Balance = Balance(this)): List<InstallmentBalance> =
    purchases.filter { (it.installmentCount ?: 0) > 1 }.map {
        InstallmentBalance(it, balance.paid(CostKind.MATERIAL, it.id))
    }
