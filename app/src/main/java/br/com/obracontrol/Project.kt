package br.com.obracontrol

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID

internal val brazil = Locale.forLanguageTag("pt-BR")
private val dateFormat = DateTimeFormatter.ofPattern("d/M/uuuu", brazil)
    .withResolverStyle(ResolverStyle.STRICT)
private val dateDisplay = DateTimeFormatter.ofPattern("dd/MM/uuuu", brazil)
internal const val UNDEFINED = "A definir"
internal const val UNREPORTED = "Não informado"

internal fun newId(): String = UUID.randomUUID().toString()
internal fun Long.brl(): String = NumberFormat.getCurrencyInstance(brazil)
    .format(BigDecimal.valueOf(this, 2)).replace('\u00a0', ' ')
internal fun Long?.costText(): String = this?.brl() ?: UNDEFINED
internal fun Long?.moneyInput(): String = this?.let {
    BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',')
}.orEmpty()
internal fun BigDecimal?.quantityInput(): String = this?.stripTrailingZeros()?.toPlainString()
    ?.replace('.', ',').orEmpty()
internal fun LocalDate?.dateText(): String = this?.format(dateDisplay).orEmpty()
internal fun String?.textOr(fallback: String = UNREPORTED): String =
    this?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
internal fun LocalDate?.dateOr(fallback: String = UNDEFINED): String = dateText().textOr(fallback)
internal fun parseDate(value: String): LocalDate? = value.trim().takeIf { it.isNotEmpty() }?.let {
    require(Regex("[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}").matches(it)) { "Data inválida. Use dia/mês/ano." }
    runCatching { LocalDate.parse(it, dateFormat) }.getOrElse {
        throw IllegalArgumentException("Data inválida. Use dia/mês/ano.")
    }
}

private val groupedMoney = Regex("[0-9]{1,3}(\\.[0-9]{3})+(,[0-9]{1,2})?")
private val decimalMoney = Regex("[0-9]+([.,][0-9]{1,2})?")
private val decimalQuantity = Regex("[0-9]+([.,][0-9]{1,4})?")
private const val MAX_CENTS = 99_999_999_999L

internal fun checkedMoney(value: Long): Long {
    require(value in 0..MAX_CENTS) { "Valor fora da faixa permitida." }
    return value
}

internal fun checkedQuantity(value: BigDecimal): BigDecimal {
    require(value.signum() >= 0 && value <= BigDecimal.valueOf(999_999_999L) &&
        value.stripTrailingZeros().scale() <= 4) { "Quantidade fora da faixa permitida." }
    return value
}

internal fun parseMoney(value: String): Long? {
    val raw = value.trim().removePrefix("R$").trim()
    if (raw.isEmpty()) return null
    val normalized = when {
        groupedMoney.matches(raw) -> raw.replace(".", "").replace(',', '.')
        decimalMoney.matches(raw) -> raw.replace(',', '.')
        else -> throw IllegalArgumentException("Valor inválido. Exemplo: 1.234,56.")
    }
    return try {
        checkedMoney(BigDecimal(normalized).movePointRight(2).longValueExact())
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("Valor fora da faixa permitida.")
    }
}

internal fun parseQuantity(value: String): BigDecimal? {
    if (value.isBlank()) return null
    val raw = value.trim()
    require(decimalQuantity.matches(raw)) { "Quantidade inválida. Exemplo: 3,2." }
    return checkedQuantity(BigDecimal(raw.replace(',', '.')))
}

internal data class Project(
    val name: String = "Minha obra",
    val start: LocalDate? = null,
    val referenceDate: LocalDate? = null,
    val laborBudget: Long? = null,
    val stages: List<Stage> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val issues: List<Issue> = emptyList(),
    val notes: String = "",
    val diaryEntries: List<DiaryEntry> = emptyList(),
    val payerNames: List<String> = emptyList(),
    val scheduledPayments: List<ScheduledPayment> = emptyList(),
    val workPlans: List<WorkPlan> = emptyList(),
    val ganttScale: GanttScale = GanttScale.WEEKS,
    val paymentAudit: List<PaymentAudit> = emptyList(),
) {
    val payerOptions: List<String> get() = (payerNames + purchases.map { it.payer } + payments.map { it.payer } + paymentAudit.map { it.payment.payer })
        .map(String::trim).filter(String::isNotBlank).distinctBy { it.lowercase(brazil) }
        .sortedBy { it.lowercase(brazil) }

    fun targets(kind: CostKind): List<Pair<String, String>> = when (kind) {
        CostKind.LABOR -> stages.map { it.id to it.name }
        CostKind.MATERIAL -> purchases.map { it.id to it.name }
    }

    fun stageName(id: String?): String? = stages.firstOrNull { it.id == id }?.name

    fun targetName(payment: Payment): String = when (payment.kind) {
        CostKind.LABOR -> stageName(payment.targetId)
        CostKind.MATERIAL -> purchases.firstOrNull { it.id == payment.targetId }?.name
    }.textOr(UNDEFINED)
}

internal data class Stage(
    val id: String = newId(),
    val name: String = "",
    val budget: Long? = null,
    val plannedStart: LocalDate? = null,
    val plannedEnd: LocalDate? = null,
    val actualStart: LocalDate? = null,
    val actualEnd: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val progress: Int = 0,
    val notes: String = "",
) {
    val status get() = when {
        progress == 100 -> "Concluída"
        progress > 0 || actualStart != null -> "Em andamento"
        plannedStart != null || plannedEnd != null -> "Prevista"
        else -> "A programar"
    }

    fun validate() {
        require(name.isNotBlank()) { "Informe o nome da etapa." }
        require(progress in 0..100) { "O avanço deve estar entre 0% e 100%." }
        require(plannedStart == null || plannedEnd == null || plannedEnd >= plannedStart) {
            "O fim previsto deve ser igual ou posterior ao início previsto."
        }
        require(actualStart == null || actualEnd == null || actualEnd >= actualStart) {
            "O fim real deve ser igual ou posterior ao início real."
        }
        require(actualEnd == null || progress == 100) { "Para informar o fim real, registre avanço de 100%." }
    }
}

internal data class Purchase(
    val id: String = newId(),
    val name: String = "",
    val supplier: String = "",
    val document: String = "",
    val documentDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val stageId: String? = null,
    val total: Long? = null,
    val discount: Long = 0,
    val extra: Boolean = false,
    val lines: List<PurchaseLine> = emptyList(),
    val notes: String = "",
    val documents: List<DocumentAttachment> = emptyList(),
    val deliveryExpected: LocalDate? = null,
    val receivedDate: LocalDate? = null,
    val payer: String = "",
    val method: String = "",
    val installmentCount: Int? = null,
    val nature: CostNature = CostNature.MATERIAL,
) {
    val lineSum: Long? get() = lines.takeIf { it.isNotEmpty() && it.all { line -> line.subtotal != null } }
        ?.sumOf { it.subtotal!! }
    val lineNet: Long? get() = lineSum?.minus(discount)
    val forecast: Long? get() = total ?: lineNet?.takeIf { it >= 0 }
    val variance: Long? get() = total?.let { amount -> lineNet?.let { amount - it } }
}

internal data class PurchaseLine(
    val id: String = newId(),
    val name: String = "",
    val quantity: BigDecimal? = null,
    val unit: String = "",
    val unitPrice: Long? = null,
    val documentSubtotal: Long? = null,
) {
    val subtotal get() = documentSubtotal ?: quantity?.let { count ->
        unitPrice?.let { cents -> count.multiply(BigDecimal.valueOf(cents))
            .setScale(0, RoundingMode.HALF_UP).longValueExact() }
    }

    fun validate() {
        require(name.isNotBlank()) { "Informe a descrição do item." }
        runCatching { subtotal?.let(::checkedMoney) }.getOrElse {
            throw IllegalArgumentException("O subtotal do item está fora da faixa permitida.")
        }
    }
}

internal enum class CostKind(val label: String) {
    LABOR("Mão de obra"), MATERIAL("Compras")
}

internal data class Payment(
    val id: String = newId(),
    val kind: CostKind = CostKind.LABOR,
    val targetId: String = "",
    val amount: Long = 0,
    val date: LocalDate? = null,
    val payer: String = "",
    val method: String = "",
    val receipt: String = "",
    val notes: String = "",
    val proofs: List<DocumentAttachment> = emptyList(),
    val scheduleId: String? = null,
) {
    fun validate(project: Project) {
        require(project.targets(kind).any { it.first == targetId }) { "Selecione a etapa ou a compra do pagamento." }
        require(amount > 0) { "Informe um valor de pagamento maior que zero." }
    }
}

internal data class Issue(
    val id: String = newId(),
    val title: String = "",
    val action: String = "",
    val closed: Boolean = false,
)

internal data class Disbursement(
    val targetId: String,
    val name: String,
    val kind: CostKind,
    val date: LocalDate?,
    val amount: Long?,
    val forecastDate: LocalDate? = date,
) {
    val dateEstimated get() = date == null && forecastDate != null
}

internal data class PayerGroup(val payer: String, val method: String, val kind: CostKind, val amount: Long)

internal class Balance(private val project: Project) {
    private val byTarget = project.payments.groupBy { it.kind to it.targetId }
    fun paid(kind: CostKind, id: String): Long = byTarget[kind to id].orEmpty().sumOf { it.amount }
    val laborPaid = project.payments.filter { it.kind == CostKind.LABOR }.sumOf { it.amount }
    val materialPaid = project.payments.filter { it.kind == CostKind.MATERIAL }.sumOf { it.amount }
    val laborAllocated = project.stages.sumOf { it.budget ?: 0 }
    val laborForecast = project.laborBudget ?: project.stages.sumOf { it.budget ?: paid(CostKind.LABOR, it.id) }
    val materialForecast = project.purchases.sumOf { it.forecast ?: paid(CostKind.MATERIAL, it.id) }
    val total = materialForecast + laborForecast
    val paid = materialPaid + laborPaid
    val overpaidByCost = project.stages.sumOf { stage -> stage.budget?.let { (paid(CostKind.LABOR, stage.id) - it).coerceAtLeast(0) } ?: 0L } +
        project.purchases.sumOf { order -> order.forecast?.let { (paid(CostKind.MATERIAL, order.id) - it).coerceAtLeast(0) } ?: 0L }
    val remaining = total - paid
    val extras = project.purchases.filter { it.extra }.sumOf { it.forecast ?: paid(CostKind.MATERIAL, it.id) }
    val completed = project.stages.count { it.progress == 100 }
    val physicalProgress = project.stages.map { it.progress }.average().takeUnless(Double::isNaN) ?: 0.0
    val projectedPurchases = project.purchases.filter { purchase ->
        purchase.forecast == null || (materialOutstanding(purchase) ?: 0) > 0
    }
    val materialToPay = projectedPurchases.sumOf { materialOutstanding(it) ?: 0 }
    val laborToPay = (laborForecast - laborPaid).coerceAtLeast(0)
    val spendBySupplier = project.purchases.groupBy { it.supplier.textOr("Fornecedor a definir") }
        .mapValues { (_, orders) -> orders.sumOf { paid(CostKind.MATERIAL, it.id) } }
        .filterValues { it > 0 }.toList().sortedByDescending { it.second }
    val payers = project.payments.groupBy { Triple(it.payer.textOr(), it.method.textOr(), it.kind) }
        .map { (key, values) -> PayerGroup(key.first, key.second, key.third, values.sumOf { it.amount }) }
        .sortedByDescending { it.amount }
    val laborDisbursements = project.stages.mapNotNull { stage ->
        val outstanding = stage.budget?.minus(paid(CostKind.LABOR, stage.id))
        if (outstanding != null && outstanding <= 0) null
        else project.costDisbursement(CostKind.LABOR, stage.id, outstanding)
    }
    val disbursements = (projectedPurchases.mapNotNull {
        project.costDisbursement(CostKind.MATERIAL, it.id, materialOutstanding(it))
    } + laborDisbursements).sortedWith(compareBy(nullsLast()) { it.forecastDate })
    val pendingByCost = tableAmountTotal(disbursements.map { it.amount })

    fun materialOutstanding(purchase: Purchase): Long? = purchase.forecast?.let {
        (it - paid(CostKind.MATERIAL, purchase.id)).coerceAtLeast(0)
    }

    fun purchaseStatus(purchase: Purchase): String {
        val paid = paid(CostKind.MATERIAL, purchase.id)
        val value = purchase.forecast
        return when {
            value == null && paid > 0 -> "Pago em parte • a confirmar"
            value == null -> "A cotar"
            paid > value -> "Pago acima do total"
            paid > 0 && paid == value -> "Pago"
            paid > 0 -> "Parcialmente pago"
            value == 0L -> "Sem custo previsto"
            else -> "A comprar"
        }
    }

    fun alerts(): List<Pair<String, String>> = buildList {
        if (project.laborBudget != null && laborAllocated != project.laborBudget && project.stages.isNotEmpty()) {
            add("Mão de obra" to "Contrato de ${laborForecast.brl()} e ${laborAllocated.brl()} distribuídos entre as etapas. Confira a alocação.")
        }
        project.stages.filter { it.budget == null }.forEach {
            add(it.name to "Valor da etapa a definir. Confirme a inclusão no orçamento de mão de obra.")
        }
        project.stages.filter { it.progress < 100 && (it.plannedStart == null || it.plannedEnd == null) }.forEach {
            add(it.name to "Cronograma previsto ainda incompleto.")
        }
        project.purchases.filter { it.forecast == null }.forEach {
            add(it.name to "Valor a cotar. O saldo conhecido considera apenas valores informados.")
        }
        project.purchases.filter { it.variance?.let { difference -> difference != 0L } == true }.forEach {
            add(it.name to "Diferença de ${it.variance!!.brl()} entre o total do documento e o líquido dos itens. Confira o documento.")
        }
        project.purchases.filter { order -> order.forecast?.let { paid(CostKind.MATERIAL, order.id) > it } == true }.forEach {
            add(it.name to "Pagamentos acima do total previsto. Confira a conciliação.")
        }
        project.purchases.filter { (it.installmentCount ?: 0) > 1 && (materialOutstanding(it) == null || materialOutstanding(it)!! > 0) }.forEach { order ->
            val rows = project.scheduledPayments.filter { it.kind == CostKind.MATERIAL && it.targetId == order.id }
            if (rows.isEmpty()) add(order.name to "Compra parcelada sem programação de vencimentos. Defina a primeira data e confira as parcelas.")
            else {
                if (rows.size != order.installmentCount || rows.sumOf { it.amount } != order.forecast) {
                    add(order.name to "A condição comercial e a programação têm quantidades ou valores diferentes. Confira as parcelas; os pagamentos registrados foram preservados.")
                }
                if (project.costPaymentPositions(CostKind.MATERIAL, order.id).any { (it.remaining == null || it.remaining > 0) && it.date == null }) {
                    add(order.name to "Há saldo de parcelas sem vencimento definido.")
                }
            }
        }
        project.stages.filter { it.budget != null && paid(CostKind.LABOR, it.id) > it.budget }.forEach {
            add(it.name to "Pagamentos acima do valor previsto para a etapa.")
        }
        if (laborPaid > laborForecast) add("Mão de obra" to "Pagamentos acima do orçamento conhecido.")
    }
}
