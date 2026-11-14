package br.com.obracontrol

import java.math.BigDecimal
import java.text.Normalizer

internal enum class MoneyField(val label: String) {
    TOTAL("Total do documento"), DISCOUNT("Desconto"), SUBTOTAL("Subtotal"), AMOUNT("Outro valor");
}

internal data class MoneyCandidate(val cents: Long, val field: MoneyField, val page: Int) {
    val label get() = if (page > 1) "${field.label} · pág. $page" else field.label
}

internal enum class RecognitionScope { PURCHASE, PAYMENT }

private val moneyPattern = Regex(
    "(?<![\\p{L}0-9.,/+\\-])(?:(R\\s*\\$|BRL)\\s*)?([+\\-]?\\s*(?:[0-9]{1,3}(?:[. \\u00A0\\u202F][0-9]{3})+,[0-9]{2}|[0-9]{1,3}(?:,[0-9]{3})+\\.[0-9]{2}|[0-9]+[,.][0-9]{2}|[0-9]{1,3}(?:[. \\u00A0\\u202F][0-9]{3})+|[0-9]+))(?![\\p{L}0-9%/.,])",
    RegexOption.IGNORE_CASE,
)

private val excludedRow = Regex("\\b(?:CNPJ|CPF|CEP|CHAVE|TRIBUTOS?|ICMS|IPI|PIS|COFINS|ISS|AL[IÍ]QUOTA|BASE DE CALCULO|TROCO|VENCIMENTO|QUANTIDADE|QTDE|QTD|AGENCIA|AUTENTICACAO|TRANSACAO)\\b")
private val discount = Regex("\\b(?:DESCONTO|ABATIMENTO)S?\\b")
private val subtotal = Regex("\\b(?:SUB[ -]?TOTAL|TOTAL (?:BRUTO|DOS? (?:ITENS|PRODUTOS|SERVICOS)))\\b")
private val preferredTotal = Regex("\\b(?:VALOR (?:TOTAL DA|DA) (?:NOTA|NF[ -]?E|COMPRA)|TOTAL (?:DA (?:NOTA|COMPRA|NF[ -]?E)|GERAL|LIQUIDO|FINAL|DO (?:PEDIDO|ORCAMENTO|DOCUMENTO))|(?:TOTAL|VALOR) A PAGAR)\\b")
private val paymentTotal = Regex("\\b(?:VALOR (?:PAGO|TRANSFERIDO|ENVIADO|DA TRANSFERENCIA|DO PAGAMENTO|DO PIX|DA TRANSACAO)|TOTAL PAGO)\\b")
private val genericTotal = Regex("\\b(?:VALOR TOTAL|TOTAL)\\b")
private val explicitMoney = Regex("\\b(?:VALOR|PRECO)\\b")

private fun classify(label: String, scope: RecognitionScope): MoneyField? = when {
    scope == RecognitionScope.PAYMENT && paymentTotal.containsMatchIn(label) -> MoneyField.TOTAL
    preferredTotal.containsMatchIn(label) -> MoneyField.TOTAL
    discount.containsMatchIn(label) && !label.contains("COM DESCONTO") -> MoneyField.DISCOUNT
    subtotal.containsMatchIn(label) -> MoneyField.SUBTOTAL
    excludedRow.containsMatchIn(label) -> null
    genericTotal.containsMatchIn(label) || scope == RecognitionScope.PAYMENT && label.trim(':', '-', ' ') == "VALOR" -> MoneyField.TOTAL
    else -> null
}

internal fun recognizeAmounts(rows: List<String>, page: Int, scope: RecognitionScope): List<MoneyCandidate> {
    var pending: MoneyField? = null
    val candidates = mutableListOf<MoneyCandidate>()
    for (row in rows) {
        val normalized = Normalizer.normalize(row, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").uppercase(brazil)
        if (normalized.isBlank()) continue
        val field = classify(normalized, scope)
        val excluded = excludedRow.containsMatchIn(normalized)
        val isAmountRow = !normalized.any(Char::isLetter) || normalized.startsWith("R$") || normalized.startsWith("BRL")
        val previous = pending.takeIf { !excluded && isAmountRow }
        pending = null
        if (field == null && excluded) continue
        val matches = moneyPattern.findAll(normalized).mapNotNull { match ->
            val raw = match.groupValues[2].replace(Regex("[\\s\\u00A0\\u202F]"), "")
            val currency = match.groupValues[1].isNotBlank()
            val numeric = raw.lastIndexOfAny(charArrayOf(',', '.'))
            val hasCents = numeric >= 0 && raw.length - numeric == 3
            if (!currency && !hasCents && field == null && previous == null) return@mapNotNull null
            if (!currency && !hasCents && normalized.substring(match.range.last + 1).contains(Regex("\\d"))) return@mapNotNull null
            if (raw.startsWith('-') && (field ?: previous) != MoneyField.DISCOUNT) return@mapNotNull null
            if (!currency && field == null && previous == null && !explicitMoney.containsMatchIn(normalized) &&
                !row.contains(Regex("[0-9][,.][0-9]{2}\\s*$"))) return@mapNotNull null
            normalizeCents(raw.removePrefix("-").removePrefix("+"), hasCents)
        }.toList()
        if (matches.isEmpty()) {
            pending = field
            continue
        }
        val target = field ?: previous
        if (target != null) {
            candidates += MoneyCandidate(matches.last(), target, page)
        } else {
            candidates += matches.map { MoneyCandidate(it, MoneyField.AMOUNT, page) }
        }
    }
    return candidates
}

private fun normalizeCents(value: String, hasCents: Boolean): Long? = runCatching {
    val raw = if (hasCents) {
        val position = value.lastIndexOfAny(charArrayOf(',', '.'))
        val integer = value.substring(0, position).filter(Char::isDigit)
        "$integer.${value.substring(position + 1)}"
    } else value.filter(Char::isDigit)
    checkedMoney(BigDecimal(raw).movePointRight(2).longValueExact())
}.getOrNull()

internal fun sortedAmounts(amounts: List<MoneyCandidate>): List<MoneyCandidate> = amounts
    .sortedWith(compareBy<MoneyCandidate> { it.field.ordinal }.thenBy { it.page })
    .distinctBy { it.field to it.cents }
