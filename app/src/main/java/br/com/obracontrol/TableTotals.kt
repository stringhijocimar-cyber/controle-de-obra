package br.com.obracontrol

internal data class TableAmountTotal(val known: Long, val definedCount: Int, val undefinedCount: Int) {
    val partial get() = undefinedCount > 0
    val entirelyUndefined get() = definedCount == 0 && partial
}

internal fun tableAmountTotal(values: Iterable<Long?>): TableAmountTotal {
    var known = 0L
    var defined = 0
    var undefined = 0
    values.forEach { amount ->
        if (amount == null) undefined++ else { known += amount; defined++ }
    }
    return TableAmountTotal(known, defined, undefined)
}
