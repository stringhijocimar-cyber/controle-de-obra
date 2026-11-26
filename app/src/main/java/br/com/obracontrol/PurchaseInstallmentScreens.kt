package br.com.obracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

internal val purchaseInstallmentSaver = listSaver<PurchaseInstallmentDraft, String>(
    save = { draft -> listOf(draft.firstDate, draft.automatic.toString(), draft.anchorFromPayment.toString()) +
        draft.rows.flatMap { listOf(it.id, it.label, it.amount.toString(), it.due, it.paymentId, it.paidDate, it.action) } },
    restore = { values -> PurchaseInstallmentDraft(values[0], values[1].toBoolean(), values[2].toBoolean(),
        values.drop(3).chunked(7).map { InstallmentRowDraft(it[0], it[1], it[2].toLong(), it[3], it[4], it[5], it[6]) }) },
)

internal fun PurchaseInstallmentDraft.preview(project: Project, order: Purchase): Project {
    val base = project.copy(purchases = project.purchases.upsert(order, Purchase::id))
    return runCatching { change(order.id)?.let { base.applyPurchaseInstallments(order, it, validate = false) } ?: base }.getOrDefault(base)
}

@Composable
internal fun PurchaseInstallmentFields(project: Project, order: Purchase, draft: PurchaseInstallmentDraft,
    change: (PurchaseInstallmentDraft) -> Unit, prefix: String = "purchase-parcel") {
    LaunchedEffect(order.forecast, order.installmentCount) {
        if (draft.automatic) runCatching { draft.rebuild(project, order) }.onSuccess(change)
    }
    HorizontalDivider(color = Line)
    Heading("Parcelas da compra", "Marque somente as parcelas efetivamente pagas. As demais permanecem em A pagar.")
    CalendarField("Primeiro vencimento", draft.firstDate, { date -> submit { change(draft.firstDue(date, project, order)) } })
    Detail("As datas seguintes são mensais. Ao pagar a primeira sem vencimento definido, sua data inicia o calendário das demais.")
    OutlinedButton(onClick = { submit { change(draft.rebuild(project, order, dates = true)) } }) { Text("Gerar / atualizar parcelas mensais") }
    if (draft.rows.isEmpty()) { Detail("Informe o valor e a quantidade de parcelas para ver a divisão."); return }
    val preview = draft.preview(project, order)
    val positions = preview.costPaymentPositions(CostKind.MATERIAL, order.id).associateBy { it.schedule?.id }
    ParcelHeader()
    draft.rows.forEachIndexed { index, row ->
        val position = positions[row.id]
        val completed = position?.completed == true
        val editable = project.payments.any { it.id == row.paymentId }
        val legacy = completed && !editable && row.action == "keep"
        key(row.id) {
            Row(Modifier.fillMaxWidth().background(if (index % 2 == 0) Mist else androidx.compose.ui.graphics.Color.Transparent)
                .testTag("$prefix-row-${index + 1}").padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                ParcelCells("${index + 1}/${draft.rows.size}", row.due.textOr("Sem data"), row.amount.brl())
                PaymentFlag(financialStatus(row.amount, position?.applied ?: 0, parseDate(row.due), LocalDate.now()), "$prefix-flag-${index + 1}",
                    if (legacy) null else ({ submit { change(draft.toggle(row.id, !completed, project, order)) } }))
            }
            if (completed && (editable || row.action == "pay")) CalendarField("Data do pagamento da parcela ${index + 1}", row.paidDate,
                { date -> submit { change(draft.paymentDate(row.id, date, project, order)) } })
            else if (legacy) Detail("Parcela ${index + 1} coberta pelos pagamentos já registrados.", Green)
            else if ((position?.applied ?: 0) > 0) Detail("Parcela ${index + 1}: ${position!!.applied.brl()} já abatidos; falta ${position.remaining.costText()}.", Amber)
        }
    }
    val paid = Balance(preview).paid(CostKind.MATERIAL, order.id)
    LedgerLine("Pago após salvar as parcelas", paid.brl())
    LedgerLine("Saldo após salvar", InstallmentBalance(order, paid).remaining.costText())
    if (draft.rows.any { row -> positions[row.id]?.completed == true && project.payments.none { it.id == row.paymentId } && row.action == "keep" })
        Detail("Pagamentos antigos já abatem as parcelas. Para mudar esses valores, use Corrigir / editar pago efetivo; criar as parcelas não lança o total novamente.", Amber)
}

@Composable
private fun ParcelHeader() {
    Row(Modifier.fillMaxWidth().background(Mist).padding(horizontal = 5.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ParcelCells("Parcela", "Vencimento", "Valor", header = true)
        Text("Pago", Modifier.width(38.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope.ParcelCells(number: String, due: String, amount: String, header: Boolean = false) {
    listOf(number to 0.7f, due to 1.2f, amount to 1.2f).forEach { (value, weight) ->
        Text(value, Modifier.weight(weight).padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
internal fun PurchaseInstallmentsEditor(project: Project, id: String, save: (Project) -> Unit) {
    val order = project.purchases.firstOrNull { it.id == id }
    if (order == null) { Detail("Compra não encontrada."); return }
    var draft by rememberSaveable(id, stateSaver = purchaseInstallmentSaver) { mutableStateOf(PurchaseInstallmentDraft.initial(project, order)) }
    Form(save = {
        val change = requireNotNull(draft.change(id)) { "Informe o valor e a quantidade de parcelas na compra." }
        save(project.applyPurchaseInstallments(order, change))
    }) { Heading(order.name, "Material ou serviço · datas e pagamento de cada parcela"); PurchaseInstallmentFields(project, order, draft, { draft = it }) }
}

/** Expanded beneath a purchase row; summary totals count only its parent row. */
@Composable
internal fun PurchaseInstallmentPanel(project: Project, order: Purchase, save: (Project) -> Unit,
    edit: () -> Unit, review: () -> Unit) {
    val draft = remember(project, order) { PurchaseInstallmentDraft.initial(project, order) }
    val preview = draft.preview(project, order)
    val positions = preview.costPaymentPositions(CostKind.MATERIAL, order.id).associateBy { it.schedule?.id }
    var selected by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFFF7FAF9)).padding(horizontal = 9.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Parcelas · ${order.name}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = edit) { Text("Editar datas", style = MaterialTheme.typography.labelSmall) }
        }
        if (draft.rows.isEmpty()) TextButton(onClick = edit) { Text("Definir valor e parcelas na compra") }
        else {
            ParcelHeader()
            draft.rows.forEachIndexed { index, row ->
                val position = positions[row.id]
                Row(Modifier.fillMaxWidth().testTag("inline-parcel-${order.id}-${index + 1}"), verticalAlignment = Alignment.CenterVertically) {
                    ParcelCells("${index + 1}/${draft.rows.size}", row.due.textOr("Sem data"), row.amount.brl())
                    PaymentFlag(financialStatus(row.amount, position?.applied ?: 0, parseDate(row.due), LocalDate.now()), "inline-parcel-flag-${order.id}-${index + 1}") {
                        if (position?.completed == true && project.payments.none { it.id == row.paymentId }) review() else selected = row.id
                    }
                }
                val actual = project.payments.firstOrNull { it.id == row.paymentId }
                if (actual != null) Text("Pago em ${actual.date.dateOr("data não informada")}", style = MaterialTheme.typography.labelSmall, color = Green)
            }
        }
    }
    selected?.let { parcelId ->
        val row = draft.rows.first { it.id == parcelId }
        val position = positions[parcelId]
        val original = project.payments.firstOrNull { it.id == row.paymentId }
        var date by rememberSaveable(parcelId) { mutableStateOf(original?.date?.dateText() ?: LocalDate.now().dateText()) }
        var first by rememberSaveable(parcelId) { mutableStateOf(draft.firstDate) }
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(row.label) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Detail(order.name)
                LedgerLine(if (original != null) "Valor pago" else "Valor a pagar", (original?.amount ?: position?.remaining).costText())
                CalendarField("Data do pagamento da parcela", date, { date = it })
                if (draft.firstDate.isBlank() && draft.rows.first().id != parcelId) CalendarField("Primeiro vencimento", first, { first = it })
                if (original != null) Detail("Ao marcar como não paga, o lançamento e seus comprovantes ficam no histórico de revisão.")
            } },
            confirmButton = { TextButton(onClick = { submit {
                var changed = draft
                if (changed.firstDate.isBlank()) changed = changed.firstDue(
                    if (changed.rows.first().id == parcelId) date else first.also { require(it.isNotBlank()) { "Selecione o primeiro vencimento." } }, project, order)
                changed = changed.toggle(parcelId, true, project, order).paymentDate(parcelId, date, project, order)
                save(project.applyPurchaseInstallments(order, requireNotNull(changed.change(order.id)))); selected = null
            } }) { Text("Salvar pagamento") } },
            dismissButton = { Row {
                if (original != null) TextButton(onClick = { submit {
                    val changed = draft.toggle(parcelId, false, project, order)
                    save(project.applyPurchaseInstallments(order, requireNotNull(changed.change(order.id)))); selected = null
                } }) { Text("Marcar não paga") }
                TextButton(onClick = { selected = null }) { Text("Cancelar") }
            } })
    }
}
