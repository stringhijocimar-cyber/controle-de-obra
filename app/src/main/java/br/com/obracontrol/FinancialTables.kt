package br.com.obracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private val UnpaidRed = Color(0xFFB3261E)

private data class TableColumn(val title: String, val width: Dp, val numeric: Boolean = false)

private data class FinancialRow(
    val key: String,
    val name: String,
    val detail: String,
    val status: FinancialStatus,
    val values: List<String>,
    val action: String,
    val open: () -> Unit,
)

private val FinancialStatus.color: Color
    get() = when (this) {
        FinancialStatus.PAID -> Green
        FinancialStatus.PENDING, FinancialStatus.OVERDUE, FinancialStatus.PARTIAL_OVERDUE -> UnpaidRed
        FinancialStatus.PARTIAL, FinancialStatus.PAID_UNPRICED, FinancialStatus.OVERPAID -> Amber
        FinancialStatus.UNPRICED, FinancialStatus.NO_COST -> Muted
    }

@Composable
private fun StatusIcon(status: FinancialStatus) {
    Box(
        Modifier.size(23.dp).background(status.color.copy(alpha = 0.11f), CircleShape)
            .semantics { contentDescription = status.label },
        contentAlignment = Alignment.Center,
    ) {
        Text(status.icon, color = status.color, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FinancialTable(
    nameColumn: String,
    columns: List<TableColumn>,
    rows: List<FinancialRow>,
    empty: String,
) {
    if (rows.isEmpty()) {
        ObraList { item { SheetCard { Detail(empty) } } }
        return
    }
    val scroll = rememberScrollState()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        val fixedWidth = (maxWidth * 0.45f).coerceIn(125.dp, 220.dp)
        val contentWidth = columns.fold(0.dp) { width, column -> width + column.width }
        val tableWidth = maxOf(contentWidth, maxWidth - fixedWidth)
        val horizontalOverflow = contentWidth > maxWidth - fixedWidth
        val rowHeight = maxOf(113.dp, (104 * fontScale).dp)
        val headerHeight = maxOf(42.dp, (38 * fontScale).dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (horizontalOverflow) {
                Text("Deslize para os lados para ver todas as colunas.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(14.dp), color = Color.White) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(headerHeight).background(Navy),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(nameColumn, Modifier.width(fixedWidth).padding(horizontal = 10.dp),
                            color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                            Row(Modifier.width(tableWidth), verticalAlignment = Alignment.CenterVertically) {
                                columns.forEach { column ->
                                    Text(column.title, Modifier.width(column.width).padding(horizontal = 10.dp),
                                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                                        textAlign = if (column.numeric) TextAlign.End else TextAlign.Start)
                                }
                            }
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                            Row(
                                Modifier.fillMaxWidth().height(rowHeight)
                                    .background(if (index % 2 == 0) Color.White else Mist)
                                    .clickable(onClickLabel = row.action, onClick = row.open),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.width(fixedWidth).padding(horizontal = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        StatusIcon(row.status)
                                        Text(row.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(row.status.label, color = row.status.color,
                                        style = MaterialTheme.typography.labelSmall)
                                    if (row.detail.isNotBlank()) {
                                        Text(row.detail, color = Muted, style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(row.action, Modifier.clickable(onClick = row.open),
                                        color = Blue, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                                }
                                Box(Modifier.weight(1f).horizontalScroll(scroll).testTag("table-values-${row.key}")) {
                                    Row(Modifier.width(tableWidth), verticalAlignment = Alignment.CenterVertically) {
                                        columns.zip(row.values).forEach { (column, value) ->
                                            Text(value, Modifier.width(column.width).padding(horizontal = 10.dp),
                                                color = Navy, style = MaterialTheme.typography.bodyMedium,
                                                textAlign = if (column.numeric) TextAlign.End else TextAlign.Start,
                                                maxLines = 4, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StageTable(project: Project, balance: Balance, today: LocalDate, edit: (String) -> Unit) {
    val columns = listOf(TableColumn("Valor previsto", 120.dp, true), TableColumn("Pago", 120.dp, true),
        TableColumn("Saldo", 120.dp, true), TableColumn("Execução", 145.dp),
        TableColumn("Previsto", 180.dp), TableColumn("Realizado", 180.dp), TableColumn("Pagamento", 130.dp))
    val rows = project.stages.map { stage ->
        val paid = balance.paid(CostKind.LABOR, stage.id)
        FinancialRow(stage.id, stage.name, stage.status, financialStatus(stage.budget, paid, stage.dueDate, today),
            listOf(stage.budget.costText(), paid.brl(), stage.budget?.minus(paid).costText(),
                "${stage.progress}% · ${stage.status}",
                "${stage.plannedStart.dateOr()} a ${stage.plannedEnd.dateOr()}",
                "${stage.actualStart.dateOr()} a ${stage.actualEnd.dateOr()}", stage.dueDate.dateOr()),
            "Editar etapa", { edit(stage.id) })
    }
    FinancialTable("Etapa", columns, rows, "Cadastre a primeira etapa para montar o cronograma.")
}

@Composable
internal fun PurchaseTableScreen(
    project: Project, balance: Balance, today: LocalDate, add: () -> Unit, open: (String) -> Unit,
) {
    val columns = listOf(TableColumn("Total", 120.dp, true), TableColumn("Pago", 120.dp, true),
        TableColumn("Saldo", 120.dp, true), TableColumn("Parcelamento", 205.dp), TableColumn("Quem pagou", 160.dp),
        TableColumn("Método", 155.dp), TableColumn("Vencimento", 130.dp), TableColumn("Entrega", 145.dp),
        TableColumn("Documentos", 155.dp))
    val rows = project.purchases.map { order ->
        val paid = balance.paid(CostKind.MATERIAL, order.id)
        FinancialRow(order.id, order.name, order.supplier.textOr("Fornecedor a definir"),
            financialStatus(order.forecast, paid, order.dueDate, today),
            listOf(order.forecast.costText(), paid.brl(), order.forecast?.minus(paid).costText(),
                order.installmentText(), project.purchasePayers(order), project.purchaseMethods(order), order.dueDate.dateOr(),
                order.receivedDate?.let { "Recebido em ${it.dateText()}" } ?: order.deliveryExpected.dateOr(),
                "${order.documents.size} anexo(s)\n${order.document.textOr()}"),
            "Abrir compra", { open(order.id) })
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            ListHeading("Compras e materiais", "Nova compra", add)
        }
        Box(Modifier.weight(1f)) {
            FinancialTable("Compra", columns, rows, "Cadastre uma compra, cotação ou material a cotar.")
        }
    }
}

@Composable
internal fun PaymentTableScreen(
    project: Project,
    balance: Balance,
    today: LocalDate,
    add: () -> Unit,
    edit: (String) -> Unit,
    payTarget: (CostKind, String) -> Unit,
) {
    var outstanding by rememberSaveable { mutableStateOf(false) }
    val purchases = project.purchases.associateBy { it.id }
    val columns = if (outstanding) {
        listOf(TableColumn("Saldo a pagar", 125.dp, true), TableColumn("Vencimento", 160.dp),
            TableColumn("Parcelamento", 205.dp), TableColumn("Pago até agora", 130.dp, true))
    } else {
        listOf(TableColumn("Valor pago", 120.dp, true), TableColumn("Data", 130.dp), TableColumn("Quem pagou", 160.dp),
            TableColumn("Método", 155.dp), TableColumn("Condição da compra", 205.dp), TableColumn("Comprovantes", 175.dp))
    }
    val rows = if (outstanding) {
        balance.disbursements.map { entry ->
            val purchase = purchases[entry.targetId]
            val paid = balance.paid(entry.kind, entry.targetId)
            val value = if (entry.kind == CostKind.MATERIAL) purchase?.forecast
                else project.stages.firstOrNull { it.id == entry.targetId }?.budget
            val due = entry.forecastDate.dateOr("Sem data") + if (entry.dateEstimated) "\nEstimativa" else ""
            FinancialRow("${entry.kind}/${entry.targetId}", entry.name, entry.kind.label,
                financialStatus(value, paid, entry.date, today),
                listOf(entry.amount.costText(), due, if (entry.kind == CostKind.MATERIAL) purchase?.installmentText().orEmpty() else "—",
                    paid.brl()), "Registrar pagamento", { payTarget(entry.kind, entry.targetId) })
        }
    } else {
        project.payments.sortedWith(compareBy(nullsLast(reverseOrder())) { it.date }).map { payment ->
            val terms = if (payment.kind == CostKind.MATERIAL) purchases[payment.targetId]?.installmentText().orEmpty() else "—"
            FinancialRow(payment.id, project.targetName(payment), payment.kind.label, FinancialStatus.PAID,
                listOf(payment.amount.brl(), payment.date.dateOr(UNREPORTED), payment.payer.textOr(), payment.method.textOr(),
                    terms, "${payment.proofs.size} anexo(s)\n${payment.receipt.textOr()}"),
                "Editar pagamento", { edit(payment.id) })
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ListHeading("Pagamentos", "Novo pagamento", add)
            Text("Total pago ${balance.paid.brl()}  ·  Saldo conhecido ${balance.remaining.brl()}",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !outstanding, onClick = { outstanding = false }, label = { Text("Registrados") })
                FilterChip(selected = outstanding, onClick = { outstanding = true }, label = { Text("A pagar") })
            }
        }
        Box(Modifier.weight(1f)) {
            FinancialTable("Referência", columns, rows,
                if (outstanding) "Nenhum valor em aberto." else "Nenhum pagamento registrado.")
        }
    }
}
