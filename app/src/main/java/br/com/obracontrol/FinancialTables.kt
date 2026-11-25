package br.com.obracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

private val UnpaidRed = Color(0xFFB3261E)
private val GridLine = Color(0xFFE7EDF2)
private val TableHeader = Color(0xFFF3F6F8)
private val AlternateRow = Color(0xFFFAFCFD)
private val SummaryBackground = Color(0xFFF0F7F4)
private val TableShape = RoundedCornerShape(10.dp)

private data class TableColumn(
    val title: String, val width: Dp, val numeric: Boolean = false, val cost: Boolean = false,
)

private fun costColumn(title: String, width: Dp = 90.dp) = TableColumn(title, width, numeric = true, cost = true)

private data class FinancialRow(
    val key: String,
    val name: String,
    val status: FinancialStatus,
    val values: List<String>,
    val action: String,
    val open: () -> Unit,
    val edit: () -> Unit = open,
    val costs: List<Long?>,
)

private fun Long?.tableMoney(): String = this?.brl()?.replace("R$", "")?.replace("\u00a0", "")?.trim() ?: "A definir"

private fun Purchase.tableInstallments(): String = when (installmentCount) {
    null -> "—"
    1 -> "À vista"
    else -> "${installmentCount}x"
}

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

private data class TableSummaryMetric(val label: String, val total: TableAmountTotal)

private fun tableSummary(columns: List<TableColumn>, rows: List<FinancialRow>): List<TableSummaryMetric> =
    columns.filter { it.cost }.mapIndexed { index, column ->
        TableSummaryMetric(column.title, tableAmountTotal(rows.map { it.costs[index] }))
    }

private fun recordCount(size: Int) = "$size ${if (size == 1) "registro" else "registros"}"

@Composable
private fun FinancialTable(
    nameColumn: String,
    columns: List<TableColumn>,
    rows: List<FinancialRow>,
    empty: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val summary = tableSummary(columns, rows)
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${recordCount(rows.size)} · valores em R$", Modifier.weight(1f), color = Muted, fontSize = 11.sp)
            TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Tela cheia", fontSize = 12.sp)
            }
        }
        Box(Modifier.weight(1f)) {
            FinancialGrid(nameColumn, columns, rows, scroll, summary, empty,
                open = { it.open() }, edit = { it.edit() })
        }
        TableLegend()
        Text("Toque no custo para editar · deslize para ver as colunas", color = Muted,
            fontSize = 10.sp, modifier = Modifier.padding(bottom = 5.dp))
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize().testTag("table-fullscreen"), color = Mist) {
                Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(nameColumn, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("${recordCount(rows.size)} · valores em R$", color = Muted, fontSize = 10.sp)
                        }
                        TextButton(onClick = { expanded = false }) { Text("Fechar tabela") }
                    }
                    Box(Modifier.weight(1f)) {
                        FinancialGrid(nameColumn, columns, rows, scroll, summary, empty,
                            open = { row -> expanded = false; row.open() },
                            edit = { row -> expanded = false; row.edit() })
                    }
                    TableLegend()
                }
            }
        }
    }
}

@Composable
private fun TableLegend() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✓ Pago", color = Green, fontSize = 10.sp)
        Text("◐ Parcial", color = Amber, fontSize = 10.sp)
        Text("○ Aberto / ! Atrasado", color = UnpaidRed, fontSize = 10.sp)
    }
}

@Composable
private fun TableSummary(metrics: List<TableSummaryMetric>, height: Dp) {
    val undefined = metrics.maxOfOrNull { it.total.undefinedCount } ?: 0
    Column(Modifier.fillMaxWidth().height(height).background(SummaryBackground).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            metrics.forEachIndexed { index, metric ->
                val baseLabel = if (metrics.size == 1) "Total pago" else when (metric.label) {
                    "Total" -> "Total previsto"
                    "Previsto" -> "Total previsto"
                    "Saldo" -> if (index == 0) "Total a pagar" else "Saldo"
                    else -> "Total pago"
                }
                val label = if (metric.total.partial && !metric.total.entirelyUndefined) {
                    if (metric.label == "Saldo") "Saldo conhecido" else "Previsto conhecido"
                } else baseLabel
                val color = if (metric.label in listOf("Pago", "Valor pago")) Green else Navy
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, color = Muted, fontSize = 10.sp, lineHeight = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (metric.total.entirelyUndefined) "A definir" else metric.total.known.brl(),
                        Modifier.testTag("table-total-$index"), color = color,
                        fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (undefined > 0) {
            Text("Total parcial · $undefined ${if (undefined == 1) "valor a definir" else "valores a definir"}",
                Modifier.testTag("table-partial-total").padding(top = 4.dp), color = Amber, fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun FinancialGrid(
    nameColumn: String,
    columns: List<TableColumn>,
    rows: List<FinancialRow>,
    scroll: androidx.compose.foundation.ScrollState,
    summary: List<TableSummaryMetric>,
    empty: String,
    open: (FinancialRow) -> Unit,
    edit: (FinancialRow) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fixedWidth = if (maxWidth >= 600.dp) 170.dp else 108.dp
        val statusWidth = 36.dp
        val contentWidth = columns.fold(0.dp) { width, column -> width + column.width }
        val rowHeight = maxOf(48.dp, (40 * fontScale).dp)
        val headerHeight = maxOf(38.dp, (34 * fontScale).dp)
        val summaryHeight = maxOf(60.dp, (56 * fontScale).dp) +
            if (summary.any { it.total.partial }) (18 * fontScale).dp else 0.dp
        val bodyHeight = minOf(if (rows.isEmpty()) 72.dp else rowHeight * rows.size,
            (maxHeight - headerHeight - summaryHeight).coerceAtLeast(0.dp))
        Surface(Modifier.fillMaxWidth().height(headerHeight + bodyHeight + summaryHeight)
            .border(1.dp, GridLine, TableShape), shape = TableShape, color = Color.White) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(headerHeight).background(TableHeader),
                    verticalAlignment = Alignment.CenterVertically) {
                    GridCell(nameColumn, fixedWidth, header = true)
                    Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                        Row(Modifier.width(statusWidth + contentWidth), verticalAlignment = Alignment.CenterVertically) {
                            GridCell("Sit.", statusWidth, header = true)
                            columns.forEach { column ->
                                GridCell(column.title, column.width, header = true, numeric = column.numeric)
                            }
                        }
                    }
                }
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(bodyHeight).padding(14.dp), contentAlignment = Alignment.CenterStart) {
                        Text(empty, color = Muted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(bodyHeight)) {
                        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                            Row(Modifier.fillMaxWidth().height(rowHeight)
                                .background(if (index % 2 == 0) Color.White else AlternateRow)
                                .testTag("table-row-${row.key}")
                                .clickable(onClickLabel = row.action, onClick = { open(row) }),
                                verticalAlignment = Alignment.CenterVertically) {
                                GridCell(row.name, fixedWidth, strong = true, tag = "table-name-${row.key}")
                                Box(Modifier.weight(1f).horizontalScroll(scroll).testTag("table-values-${row.key}")) {
                                    Row(Modifier.width(statusWidth + contentWidth), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.width(statusWidth).fillMaxHeight().border(0.5.dp, GridLine),
                                            contentAlignment = Alignment.Center) { StatusIcon(row.status) }
                                        columns.zip(row.values).forEachIndexed { columnIndex, (column, value) ->
                                            GridCell(value, column.width, numeric = column.numeric,
                                                edit = if (column.cost) ({ edit(row) }) else null,
                                                recordName = row.name,
                                                tone = if (column.title in listOf("Pago", "Valor pago")) Green else Navy,
                                                tag = if (column.cost) "table-cost-${row.key}-$columnIndex" else null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                TableSummary(summary, summaryHeight)
            }
        }
    }
}

@Composable
private fun GridCell(
    value: String, width: Dp, header: Boolean = false, numeric: Boolean = false, strong: Boolean = false,
    edit: (() -> Unit)? = null, tag: String? = null, recordName: String = "", tone: Color = Navy,
) {
    var options by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = if (edit == null) Modifier else Modifier
        .clickable(onClickLabel = "Opções do custo") {
            focus.clearFocus()
            keyboard?.hide()
            options = true
        }
    Box(Modifier.width(width).fillMaxHeight()
        .then(if (tag == null) Modifier else Modifier.testTag(tag))
        .then(interaction).border(0.5.dp, GridLine)
        .padding(horizontal = 6.dp), contentAlignment = if (numeric) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(value.replace('\n', ' '), color = if (header) Muted else tone,
            fontSize = if (header) 11.sp else 12.sp, lineHeight = 16.sp,
            fontWeight = if (header || strong) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = if (numeric) TextAlign.End else TextAlign.Start,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
    if (options && edit != null) {
        AlertDialog(onDismissRequest = { options = false }, title = { Text("Dados do custo") },
            text = { Text("$recordName\nValor selecionado: ${if (value == "A definir") value else "R$ $value"}") },
            confirmButton = { TextButton(onClick = { options = false; edit() }) { Text("Editar dados") } },
            dismissButton = { TextButton(onClick = { options = false }) { Text("Cancelar") } })
    }
}

@Composable
internal fun StageTable(project: Project, balance: Balance, today: LocalDate, edit: (String) -> Unit) {
    val columns = listOf(costColumn("Previsto", 94.dp), costColumn("Pago"),
        costColumn("Saldo"), TableColumn("Execução", 70.dp, true), TableColumn("Andamento", 130.dp),
        TableColumn("Início previsto", 108.dp), TableColumn("Fim previsto", 108.dp),
        TableColumn("Início real", 108.dp), TableColumn("Fim real", 108.dp), TableColumn("Pagamento", 108.dp))
    val rows = project.stages.map { stage ->
        val paid = balance.paid(CostKind.LABOR, stage.id)
        FinancialRow(stage.id, stage.name, financialStatus(stage.budget, paid, stage.dueDate, today),
            listOf(stage.budget.tableMoney(), paid.tableMoney(), stage.budget?.minus(paid).tableMoney(),
                "${stage.progress}%", stage.status, stage.plannedStart.dateOr(), stage.plannedEnd.dateOr(),
                stage.actualStart.dateOr(), stage.actualEnd.dateOr(), stage.dueDate.dateOr()),
            "Editar etapa", { edit(stage.id) }, costs = listOf(stage.budget, paid, stage.budget?.minus(paid)))
    }
    FinancialTable("Etapa", columns, rows, "Cadastre a primeira etapa para montar o cronograma.")
}

@Composable
internal fun PurchaseTableScreen(
    project: Project, balance: Balance, today: LocalDate, add: () -> Unit, open: (String) -> Unit,
) {
    val columns = listOf(costColumn("Total"), costColumn("Pago"),
        costColumn("Saldo"), TableColumn("Parcelas", 64.dp), TableColumn("Condição / valores", 230.dp),
        TableColumn("Quem pagou", 120.dp), TableColumn("Método", 110.dp), TableColumn("Vencimento", 108.dp),
        TableColumn("Fornecedor", 150.dp), TableColumn("Entrega", 108.dp),
        TableColumn("Anexos", 60.dp, true), TableColumn("Documento", 150.dp))
    val rows = project.purchases.map { order ->
        val paid = balance.paid(CostKind.MATERIAL, order.id)
        FinancialRow(order.id, order.name,
            financialStatus(order.forecast, paid, order.dueDate, today),
            listOf(order.forecast.tableMoney(), paid.tableMoney(), order.forecast?.minus(paid).tableMoney(),
                order.tableInstallments(), order.installmentText(), project.purchasePayers(order), project.purchaseMethods(order),
                order.dueDate.dateOr(), order.supplier.textOr(),
                order.receivedDate?.let { "✓ ${it.dateText()}" } ?: order.deliveryExpected.dateOr(),
                order.documents.size.toString(), order.document.textOr()),
            "Abrir compra", { open(order.id) }, costs = listOf(order.forecast, paid, order.forecast?.minus(paid)))
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            ListHeading("Compras", "Nova compra", add)
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
    editCost: (CostKind, String) -> Unit,
) {
    var outstanding by rememberSaveable { mutableStateOf(false) }
    val purchases = project.purchases.associateBy { it.id }
    val columns = if (outstanding) {
        listOf(costColumn("Saldo"), costColumn("Pago"), TableColumn("Parcelas", 64.dp),
            TableColumn("Vencimento", 108.dp), TableColumn("Tipo de data", 100.dp),
            TableColumn("Condição / valores", 230.dp), TableColumn("Categoria", 105.dp))
    } else {
        listOf(costColumn("Valor pago"), TableColumn("Data", 108.dp), TableColumn("Quem pagou", 120.dp),
            TableColumn("Método", 110.dp), TableColumn("Parcelas", 64.dp), TableColumn("Condição / valores", 230.dp),
            TableColumn("Categoria", 105.dp), TableColumn("Anexos", 60.dp, true), TableColumn("Comprovante", 150.dp))
    }
    val rows = if (outstanding) {
        balance.disbursements.map { entry ->
            val purchase = purchases[entry.targetId]
            val paid = balance.paid(entry.kind, entry.targetId)
            val value = if (entry.kind == CostKind.MATERIAL) purchase?.forecast
                else project.stages.firstOrNull { it.id == entry.targetId }?.budget
            val due = entry.forecastDate.dateOr("Sem data")
            FinancialRow("${entry.kind}/${entry.targetId}", entry.name,
                financialStatus(value, paid, entry.date, today),
                listOf(entry.amount.tableMoney(), paid.tableMoney(), purchase?.tableInstallments() ?: "—", due,
                    if (entry.forecastDate == null) "Sem data" else if (entry.dateEstimated) "Estimativa" else "Informada",
                    purchase?.installmentText() ?: "—", entry.kind.label),
                "Registrar pagamento", { payTarget(entry.kind, entry.targetId) },
                edit = { editCost(entry.kind, entry.targetId) }, costs = listOf(entry.amount, paid))
        }
    } else {
        project.payments.sortedWith(compareBy(nullsLast(reverseOrder())) { it.date }).map { payment ->
            val terms = if (payment.kind == CostKind.MATERIAL) purchases[payment.targetId]?.installmentText().orEmpty() else "—"
            FinancialRow(payment.id, project.targetName(payment), FinancialStatus.PAID,
                listOf(payment.amount.tableMoney(), payment.date.dateOr(UNREPORTED), payment.payer.textOr(), payment.method.textOr(),
                    if (payment.kind == CostKind.MATERIAL) purchases[payment.targetId]?.tableInstallments() ?: "—" else "—",
                    terms, payment.kind.label, payment.proofs.size.toString(), payment.receipt.textOr()),
                "Editar pagamento", { edit(payment.id) }, costs = listOf(payment.amount))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ListHeading("Pagamentos", "+ Novo", add)
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
