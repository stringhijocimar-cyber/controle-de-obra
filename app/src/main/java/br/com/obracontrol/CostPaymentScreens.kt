package br.com.obracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
internal fun PaymentFlag(status: FinancialStatus, tag: String, onClick: (() -> Unit)?) {
    val paid = status == FinancialStatus.PAID || status == FinancialStatus.OVERPAID
    val partial = status in listOf(FinancialStatus.PARTIAL, FinancialStatus.PARTIAL_OVERDUE, FinancialStatus.PAID_UNPRICED)
    TriStateCheckbox(state = if (paid) ToggleableState.On else if (partial) ToggleableState.Indeterminate else ToggleableState.Off,
        onClick = onClick, enabled = onClick != null,
        modifier = Modifier.testTag(tag).semantics { contentDescription = status.label },
        colors = CheckboxDefaults.colors(checkedColor = if (partial) Amber else Green,
            uncheckedColor = if (status == FinancialStatus.UNPRICED) Muted else Amber, disabledCheckedColor = Green))
}

@Composable
private fun FlowAmounts(paid: Long, pending: TableAmountTotal, total: Long, tag: String, totalLabel: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Pago efetivo", paid.brl(), Green),
            Triple(if (pending.partial) "A pagar conhecido" else "A pagar", if (pending.entirelyUndefined) "A definir" else pending.known.brl(), Blue),
            Triple(totalLabel ?: if (pending.partial) "Total conhecido" else "Total da semana", total.brl(), Navy))
            .forEachIndexed { index, (label, value, color) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text(value, Modifier.testTag("$tag-$index"), color = color, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CostPaymentCalendarScreen(project: Project, today: LocalDate, openCost: (CostKind, String) -> Unit) {
    val calendar = remember(project) { CostPaymentCalendar(project) }
    var filter by rememberSaveable { mutableStateOf("") }
    var openWeeks by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var openCategories by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val entries = calendar.entries.filter { filter.isBlank() || it.target.category.name == filter }
    val weeks = calendar.weeks.map { week -> week.copy(entries = week.entries.filter { filter.isBlank() || it.target.category.name == filter }) }
        .filter { it.entries.isNotEmpty() }
    fun toggle(items: ArrayList<String>, key: String) = ArrayList(if (key in items) items - key else items + key)
    ObraList {
        item {
            SheetCard {
                Heading("Calendário de custos", "Pagos pela data do pagamento; a pagar pelo vencimento. Abra a semana, o tipo e o custo.")
                val pending = tableAmountTotal(entries.filterNot { it.paid }.map { it.amount })
                val paid = entries.filter { it.paid }.sumOf { it.amount ?: 0 }
                FlowAmounts(paid, pending, paid + pending.known, "cashflow-total", "Total conhecido")
                if (pending.partial) Detail("${pending.undefinedCount} vencimento(s) com valor a definir.", Amber)
                if (project.laborBudget != null && project.stages.sumOf { it.budget ?: 0 } != project.laborBudget) {
                    Detail("Os valores de mão de obra seguem as etapas. Confira a diferença entre a soma das etapas e o contrato em Pendências.", Amber)
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("" to "Todos") + CostCategory.entries.map { it.name to it.label }).forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) })
                }
            }
        }
        if (weeks.isEmpty()) item { SheetCard { Detail("Cadastre custos e vencimentos para montar a programação semanal.") } }
        items(weeks, key = { it.key }) { week ->
            val expanded = week.key in openWeeks
            SheetCard(Modifier.testTag("cashflow-week-${week.key}")) {
                Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Abrir semana") { openWeeks = toggle(openWeeks, week.key) }
                    .testTag("cashflow-week-toggle-${week.key}"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(week.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${week.entries.size} lançamento(s)", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (expanded) "Fechar −" else "Abrir +", color = Green, style = MaterialTheme.typography.labelLarge)
                    }
                    FlowAmounts(week.paid, week.pending, week.knownTotal, "cashflow-week-${week.key}")
                    val ratio = if (week.knownTotal > 0) week.paid.toFloat() / week.knownTotal else 0f
                    LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = Green, trackColor = Line, gapSize = 0.dp, drawStopIndicator = {})
                    if (week.start?.let { today >= it && today <= it.plusDays(6) } == true) Tag("Esta semana")
                    if (week.pending.partial) Detail("Há valores a definir nesta semana.", Amber)
                }
                if (expanded) CostCategory.entries.forEach { category ->
                    val group = week.entries.filter { it.target.category == category }
                    if (group.isNotEmpty()) {
                        val categoryKey = "${week.key}/${category.name}"
                        val categoryExpanded = categoryKey in openCategories
                        HorizontalDivider(color = Line)
                        Row(Modifier.fillMaxWidth().background(Mist, RoundedCornerShape(8.dp))
                            .clickable { openCategories = toggle(openCategories, categoryKey) }
                            .testTag("cashflow-category-$categoryKey").padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(if (categoryExpanded) "Ocultar −" else "Detalhar +", color = Green, style = MaterialTheme.typography.labelMedium)
                        }
                        if (categoryExpanded) group.groupBy { it.target.kind to it.target.id }.values.forEach { costs ->
                            val target = costs.first().target
                            val pending = tableAmountTotal(costs.filterNot { it.paid }.map { it.amount })
                            Row(Modifier.fillMaxWidth().clickable { openCost(target.kind, target.id) }
                                .testTag("cashflow-cost-${week.key}-${target.id}").padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(target.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text("${costs.size} lançamento(s) · Abrir custo e parcelas", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    if (costs.any { it.estimatedDate }) Text("Vencimento estimado pelo fim da etapa", color = Amber, style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (pending.entirelyUndefined) "A definir" else pending.known.brl(), color = Blue,
                                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("a pagar na semana", color = Muted, style = MaterialTheme.typography.labelSmall)
                                    val paid = costs.filter { it.paid }.sumOf { it.amount ?: 0 }
                                    if (paid > 0) Text("${paid.brl()} pago", color = Green, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CostPaymentDetailScreen(project: Project, kind: CostKind, id: String, navigate: (String) -> Unit) {
    val target = project.costTarget(kind, id)
    if (target == null) { ObraList { item { SheetCard { Detail("Custo não encontrado.") } } }; return }
    val balance = remember(project) { Balance(project) }
    val actual = project.payments.filter { it.kind == kind && it.targetId == id }
    val paid = balance.paid(kind, id)
    val remaining = target.total?.let { (it - paid).coerceAtLeast(0) }
    val positions = remember(project, kind, id) { project.costPaymentPositions(kind, id) }
    val editRoute = if (kind == CostKind.LABOR) "etapa/$id" else "compra/$id"
    ObraList {
        item {
            SheetCard {
                Heading(target.name, "${target.category.label} · Visão completa deste custo")
                LedgerLine("Valor cadastrado", target.total.costText())
                LedgerLine("Pago efetivo", paid.brl())
                LedgerLine("Saldo a pagar", remaining.costText())
                if (target.total != null && paid > target.total) LedgerLine("Pago a maior", (paid - target.total).brl())
                Tag(if (target.total != null && remaining == 0L && target.total > 0) "Pagamento concluído" else if (paid > 0) "Pagamento parcial" else "Pagamento em aberto",
                    warning = remaining != 0L)
                if (remaining != null && remaining > 0) Button(onClick = { navigate("pagamento/novo/${kind.name}/$id/saldo") },
                    modifier = Modifier.fillMaxWidth()) { Text("Marcar como pago") }
                OutlinedButton(onClick = { navigate("programacao/${kind.name}/$id") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Programar pagamentos")
                }
                TextButton(onClick = { navigate(editRoute) }) { Text("Editar cadastro do custo") }
            }
        }
        item {
            Heading("Parcelas e vencimentos", "Marque a caixa e confirme o pagamento. Pagamentos anteriores sem parcela vinculada são abatidos por ordem de vencimento.")
        }
        items(positions, key = { it.key }) { position ->
            SheetCard(Modifier.testTag("cost-parcel-${position.key}")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(position.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Detail(position.date.dateOr("Sem data") + if (position.estimatedDate) " · estimativa" else "")
                    }
                    PaymentFlag(financialStatus(position.gross, position.applied, position.date, LocalDate.now()),
                        "cost-parcel-paid-${position.key}", if (position.completed) null else ({
                            if (position.remaining == null) navigate(editRoute)
                            else navigate("pagamento/novo/${kind.name}/$id/${position.schedule?.id ?: "saldo"}")
                        }))
                }
                LedgerLine("Programado", position.gross.costText())
                LedgerLine("Abatido dos pagamentos", position.applied.brl())
                LedgerLine("A pagar", position.remaining.costText())
            }
        }
        item { Heading("Pagamentos registrados", "Para corrigir uma quitação, edite o pagamento correspondente. Os comprovantes permanecem vinculados ao registro.") }
        if (actual.isEmpty()) item { SheetCard { Detail("Nenhum pagamento registrado para este custo.") } }
        items(actual.sortedWith(compareBy(nullsLast(reverseOrder())) { it.date }), key = { it.id }) { payment ->
            SheetCard {
                LedgerLine(payment.date.dateOr(UNREPORTED), payment.amount.brl())
                Detail("${payment.payer.textOr()} · ${payment.method.textOr()}")
                if (payment.proofs.isNotEmpty()) Detail("${payment.proofs.size} comprovante(s)")
                TextButton(onClick = { navigate("pagamento/${payment.id}") }) { Text("Editar pagamento") }
            }
        }
    }
}

private data class ScheduleDraft(val id: String, val label: String, val date: String, val amount: String)
private val scheduleDraftSaver = listSaver<List<ScheduleDraft>, String>(
    save = { rows -> rows.flatMap { listOf(it.id, it.label, it.date, it.amount) } },
    restore = { values -> values.chunked(4).map { ScheduleDraft(it[0], it[1], it[2], it[3]) } },
)

@Composable
internal fun PaymentScheduleEditor(project: Project, kind: CostKind, id: String, save: (Project) -> Unit) {
    val target = project.costTarget(kind, id)
    if (target == null) { ObraList { item { Detail("Custo não encontrado.") } }; return }
    val existing = project.scheduledPayments.filter { it.kind == kind && it.targetId == id }
    var count by rememberSaveable { mutableStateOf((existing.size.takeIf { it > 0 }
        ?: project.purchases.firstOrNull { kind == CostKind.MATERIAL && it.id == id }?.installmentCount ?: 1).toString()) }
    var firstDate by rememberSaveable { mutableStateOf((existing.firstOrNull()?.date ?: target.due).dateText()) }
    var period by rememberSaveable { mutableStateOf("monthly") }
    var rows by rememberSaveable(stateSaver = scheduleDraftSaver) {
        mutableStateOf(existing.map { ScheduleDraft(it.id, it.label, it.date.dateText(), it.amount.moneyInput()) })
    }
    Form(remove = if (existing.isEmpty()) null else ({ save(project.replacePaymentSchedule(kind, id, emptyList())) }),
        deletePrompt = "Remover somente a programação deste custo? Todos os pagamentos e comprovantes já registrados serão mantidos.",
        save = {
            require(rows.isNotEmpty()) { "Gere as parcelas antes de salvar a programação." }
            save(project.replacePaymentSchedule(kind, id, rows.map { row ->
                ScheduledPayment(row.id, kind, id, parseMoney(row.amount) ?: 0, parseDate(row.date), row.label.trim())
            }))
        }) {
        Heading(target.name, "${target.category.label} · Programe os vencimentos do valor total do custo.")
        LedgerLine("Valor do custo", target.total.costText())
        Detail("Os pagamentos já registrados serão abatidos desta programação. Sem vínculo com uma parcela, o abatimento segue a ordem de vencimento.")
        Field("Quantidade de vencimentos", count, { count = it }, KeyboardType.Number)
        Select("Periodicidade", period, listOf("weekly" to "Semanal", "monthly" to "Mensal")) { period = it }
        CalendarField("Primeiro vencimento", firstDate, { firstDate = it })
        OutlinedButton(onClick = { submit {
            val total = requireNotNull(target.total) { "Defina primeiro o valor do custo no cadastro." }
            val date = requireNotNull(parseDate(firstDate)) { "Selecione o primeiro vencimento." }
            val number = count.toIntOrNull() ?: throw IllegalArgumentException("Informe a quantidade de vencimentos.")
            rows = buildPaymentSchedule(kind, id, total, number, date, period == "weekly", rows.map { it.id })
                .map { ScheduleDraft(it.id, it.label, it.date.dateText(), it.amount.moneyInput()) }
        } }) { Text("Gerar parcelas") }
        if (rows.isNotEmpty()) Heading("Conferir vencimentos", "Ajuste cada data e valor antes de salvar. Um saldo sem programação aparece em Sem data.")
        rows.forEachIndexed { index, row ->
            key(row.id) {
                HorizontalDivider(color = Line)
                Field("Descrição ${index + 1}", row.label, { value -> rows = rows.map { if (it.id == row.id) it.copy(label = value) else it } })
                CalendarField("Vencimento ${index + 1}", row.date, { value -> rows = rows.map { if (it.id == row.id) it.copy(date = value) else it } })
                Field("Valor programado ${index + 1} (R$)", row.amount,
                    { value -> rows = rows.map { if (it.id == row.id) it.copy(amount = value) else it } }, KeyboardType.Decimal)
            }
        }
    }
}
