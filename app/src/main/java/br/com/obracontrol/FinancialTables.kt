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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
private val GridLine = Color(0xFFE5EBF0)
private val TableHeader = Color(0xFFF1F5F8)
private val AlternateRow = Color(0xFFFAFCFD)
private val SummaryBackground = Color(0xFFEDF6F2)
private val TableShape = RoundedCornerShape(16.dp)

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
    val detail: String = "",
    val settle: (() -> Unit)? = null,
)

private data class SelectedTableCost(val rowKey: String, val value: String)

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
        Modifier.size(24.dp).background(status.color.copy(alpha = 0.10f), CircleShape)
            .semantics { contentDescription = status.label },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(when (status) {
            FinancialStatus.PAID, FinancialStatus.OVERPAID -> AppSymbol.CHECK
            FinancialStatus.OVERDUE, FinancialStatus.PARTIAL_OVERDUE -> AppSymbol.ALERT
            else -> AppSymbol.CLOCK
        }, Modifier.size(15.dp), status.color)
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
    var selectedCost by remember { mutableStateOf<SelectedTableCost?>(null) }
    val scroll = rememberScrollState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val showCost: (FinancialRow, String) -> Unit = { row, value ->
        focus.clearFocus()
        keyboard?.hide()
        selectedCost = SelectedTableCost(row.key, value)
    }
    val summary = tableSummary(columns, rows)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${recordCount(rows.size)} · valores em R$", Modifier.weight(1f), color = Muted, fontSize = 11.sp)
            TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                AppIcon(AppSymbol.EXPAND, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tela cheia", fontSize = 12.sp)
            }
        }
        Box(Modifier.weight(1f)) {
            FinancialGrid(nameColumn, columns, rows, scroll, summary, empty,
                open = { it.open() }, showCost = showCost)
        }
        TableLegend()
        Text("Marque para quitar · toque no valor para editar", color = Muted,
            fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp))
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
                            showCost = showCost)
                    }
                    TableLegend()
                }
            }
        }
    }
    // Keep the menu outside the lazy rows: scrolling or an IME resize can recycle a cell.
    selectedCost?.let { selection ->
        rows.firstOrNull { it.key == selection.rowKey }?.let { row ->
            AlertDialog(onDismissRequest = { selectedCost = null }, title = { Text("Dados do custo") },
                text = { Text("${row.name}\nValor selecionado: ${if (selection.value == "A definir") selection.value else "R$ ${selection.value}"}") },
                confirmButton = { TextButton(onClick = { selectedCost = null; expanded = false; row.edit() }) { Text("Editar dados") } },
                dismissButton = { TextButton(onClick = { selectedCost = null }) { Text("Cancelar") } })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableLegend() {
    FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("Pago" to Green, "Parcial" to Amber, "Em aberto / atraso" to UnpaidRed).forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(5.dp).background(color, CircleShape))
                Text(label, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun TableSummaryMetricContent(index: Int, metric: TableSummaryMetric, metricsSize: Int, stacked: Boolean, modifier: Modifier = Modifier) {
    val baseLabel = if (metricsSize == 1) "Pago efetivo" else when (metric.label) {
        "Total", "Previsto" -> "Total previsto"
        "Saldo", "A pagar" -> "A pagar"
        else -> "Pago efetivo"
    }
    val label = if (metric.total.partial && !metric.total.entirelyUndefined) {
        if (metric.label in listOf("Saldo", "A pagar")) "Saldo conhecido" else "Previsto conhecido"
    } else baseLabel
    val color = if (metric.label in listOf("Pago", "Valor pago", "Pago efetivo")) Green else Navy
    val text = if (metric.total.entirelyUndefined) "A definir" else metric.total.known.brl()
    if (stacked) {
        Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = Muted, fontSize = 11.sp)
            Text(text, Modifier.testTag("table-total-$index"), color = color, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Muted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1)
            Text(text, Modifier.testTag("table-total-$index"), color = color,
                fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
        }
    }
}

@Composable
private fun TableSummary(metrics: List<TableSummaryMetric>, height: Dp, stacked: Boolean) {
    val undefined = metrics.maxOfOrNull { it.total.undefinedCount } ?: 0
    Column(Modifier.fillMaxWidth().height(height).background(SummaryBackground).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center) {
        if (stacked) {
            metrics.forEachIndexed { index, metric ->
                TableSummaryMetricContent(index, metric, metrics.size, stacked)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEachIndexed { index, metric -> TableSummaryMetricContent(index, metric, metrics.size, stacked, Modifier.weight(1f)) }
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
    showCost: (FinancialRow, String) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fixedWidth = if (maxWidth >= 600.dp) 190.dp else 126.dp
        val statusWidth = 44.dp
        val contentWidth = columns.fold(0.dp) { width, column -> width + column.width }
        val rowHeight = maxOf(52.dp, (42 * fontScale).dp)
        val headerHeight = maxOf(40.dp, (34 * fontScale).dp)
        val stacked = fontScale > 1.2f || maxWidth < 330.dp ||
            summary.any { it.total.known.brl().length > 13 && summary.size > 2 }
        val summaryHeight = (if (stacked) maxOf(60.dp, (metricsHeight(summary.size) * fontScale).dp)
            else maxOf(66.dp, (60 * fontScale).dp)) +
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
                            GridCell("Pago", statusWidth, header = true)
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
                                GridCell(row.name, fixedWidth, strong = true, tag = "table-name-${row.key}", detail = row.detail)
                                Box(Modifier.weight(1f).horizontalScroll(scroll).testTag("table-values-${row.key}")) {
                                    Row(Modifier.width(statusWidth + contentWidth), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.width(statusWidth).fillMaxHeight().drawBehind {
                                            drawLine(GridLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                                            },
                                            contentAlignment = Alignment.Center) {
                                            PaymentFlag(row.status, "payment-completed-${row.key}", row.settle ?: row.edit)
                                        }
                                        columns.zip(row.values).forEachIndexed { columnIndex, (column, value) ->
                                            val costIndex = columns.take(columnIndex + 1).count { it.cost } - 1
                                            GridCell(value, column.width, numeric = column.numeric,
                                                edit = if (column.cost) ({ showCost(row, value) }) else null,
                                                tone = if (column.title in listOf("Pago", "Valor pago", "Pago efetivo")) Green else Navy,
                                                tag = if (column.cost) "table-cost-${row.key}-$costIndex" else null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                TableSummary(summary, summaryHeight, stacked)
            }
        }
    }
}

private fun metricsHeight(size: Int) = size * 28 + 20

@Composable
private fun GridCell(
    value: String, width: Dp, header: Boolean = false, numeric: Boolean = false, strong: Boolean = false,
    edit: (() -> Unit)? = null, tag: String? = null, tone: Color = Navy, detail: String = "",
) {
    val interaction = if (edit == null) Modifier else Modifier
        .clickable(onClickLabel = "Opções do custo", onClick = edit)
    Box(Modifier.width(width).fillMaxHeight()
        .then(if (tag == null) Modifier else Modifier.testTag(tag))
        .then(interaction).drawBehind {
            drawLine(GridLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            if (strong) drawLine(GridLine, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(horizontal = if (width <= 50.dp) 3.dp else 10.dp),
        contentAlignment = if (numeric) Alignment.CenterEnd else Alignment.CenterStart) {
        val nature = !header && value in CostNature.entries.map { it.label }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = if (numeric) Alignment.End else Alignment.Start) {
            Text(value.replace('\n', ' '), color = if (header) Muted else tone,
            modifier = if (nature) Modifier.background(if (value == CostNature.SERVICE.label) Green.copy(alpha = 0.08f)
                else Blue.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 4.dp) else Modifier,
            fontSize = if (nature) 10.sp else if (header) 11.sp else 12.sp, lineHeight = 16.sp,
            fontWeight = if (header || strong) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = if (numeric) TextAlign.End else TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = if (numeric) "tnum" else null),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank() && !header) Text(detail, color = Muted, fontSize = 10.sp, lineHeight = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun StageTable(project: Project, balance: Balance, today: LocalDate, edit: (String) -> Unit,
    complete: (CostKind, String) -> Unit) {
    val columns = listOf(costColumn("Previsto", 94.dp), costColumn("Pago efetivo", 100.dp),
        costColumn("A pagar"), TableColumn("Tipo", 84.dp), TableColumn("Execução", 70.dp, true), TableColumn("Andamento", 130.dp),
        TableColumn("Início previsto", 108.dp), TableColumn("Fim previsto", 108.dp),
        TableColumn("Início real", 108.dp), TableColumn("Fim real", 108.dp), TableColumn("Pagamento", 108.dp))
    val rows = project.stages.map { stage ->
        val paid = balance.paid(CostKind.LABOR, stage.id)
        FinancialRow(stage.id, stage.name, financialStatus(stage.budget, paid, stage.dueDate, today),
            listOf(stage.budget.tableMoney(), paid.tableMoney(), stage.budget?.minus(paid).tableMoney(), CostNature.SERVICE.label,
                "${stage.progress}%", stage.status, stage.plannedStart.dateOr(), stage.plannedEnd.dateOr(),
                stage.actualStart.dateOr(), stage.actualEnd.dateOr(), stage.dueDate.dateOr()),
            "Editar etapa", { edit(stage.id) }, costs = listOf(stage.budget, paid, stage.budget?.minus(paid)),
            detail = "Serviço · ${stage.progress}%", settle = {
                if (stage.budget != null && stage.budget > paid) complete(CostKind.LABOR, stage.id) else edit(stage.id)
            })
    }
    FinancialTable("Etapa", columns, rows, "Cadastre a primeira etapa para montar o cronograma.")
}

@Composable
internal fun PurchaseTableScreen(
    project: Project, balance: Balance, today: LocalDate, add: () -> Unit, open: (String) -> Unit,
    complete: (CostKind, String) -> Unit,
) {
    val columns = listOf(costColumn("Total"), costColumn("Pago efetivo", 100.dp),
        costColumn("A pagar"), TableColumn("Tipo", 84.dp), TableColumn("Parcelas", 64.dp), TableColumn("Condição / valores", 230.dp),
        TableColumn("Quem pagou", 120.dp), TableColumn("Método", 110.dp), TableColumn("Vencimento", 108.dp),
        TableColumn("Fornecedor", 150.dp), TableColumn("Entrega", 108.dp),
        TableColumn("Anexos", 60.dp, true), TableColumn("Documento", 150.dp))
    val rows = project.purchases.map { order ->
        val paid = balance.paid(CostKind.MATERIAL, order.id)
        FinancialRow(order.id, order.name,
            financialStatus(order.forecast, paid, order.dueDate, today),
            listOf(order.forecast.tableMoney(), paid.tableMoney(), order.forecast?.minus(paid).tableMoney(), order.nature.label,
                order.tableInstallments(), order.installmentText(), project.purchasePayers(order), project.purchaseMethods(order),
                order.dueDate.dateOr(), order.supplier.textOr(),
                order.receivedDate?.let { "✓ ${it.dateText()}" } ?: order.deliveryExpected.dateOr(),
                order.documents.size.toString(), order.document.textOr()),
            "Abrir compra", { open(order.id) }, costs = listOf(order.forecast, paid, order.forecast?.minus(paid)),
            detail = listOfNotNull(order.nature.label, order.tableInstallments().takeUnless { it == "—" }).joinToString(" · "),
            settle = { if (order.forecast != null && order.forecast!! > paid) complete(CostKind.MATERIAL, order.id) else open(order.id) })
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            ListHeading("Compras", "Nova compra", add, "Materiais, serviços e condições de pagamento")
        }
        Box(Modifier.weight(1f)) {
            FinancialTable("Compra", columns, rows, "Cadastre uma compra, cotação ou material a cotar.")
        }
    }
}

private enum class PaymentView(val label: String) {
    REGISTERED("Registrados"), OUTSTANDING("A pagar"), INSTALLMENTS("Parcelados"), WEEKS("Semanas")
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
    complete: (CostKind, String) -> Unit,
    openCost: (CostKind, String) -> Unit,
) {
    var selectedView by rememberSaveable { mutableStateOf(PaymentView.REGISTERED.name) }
    val view = PaymentView.valueOf(selectedView)
    val outstanding = view == PaymentView.OUTSTANDING
    val installments = view == PaymentView.INSTALLMENTS
    val purchases = project.purchases.associateBy { it.id }
    val columns = if (installments) {
        listOf(costColumn("Total"), costColumn("Pago efetivo", 100.dp), costColumn("A pagar"), TableColumn("Parcelas", 64.dp),
            TableColumn("Condição / valores", 230.dp), TableColumn("Tipo", 84.dp), TableColumn("Quem pagou", 120.dp),
            TableColumn("Método", 110.dp), TableColumn("Vencimento", 108.dp), TableColumn("Crédito", 100.dp, numeric = true))
    } else if (outstanding) {
        listOf(costColumn("A pagar"), costColumn("Pago efetivo", 100.dp), TableColumn("Tipo", 84.dp), TableColumn("Parcelas", 64.dp),
            TableColumn("Vencimento", 108.dp), TableColumn("Tipo de data", 100.dp),
            TableColumn("Condição / valores", 230.dp), TableColumn("Origem", 105.dp))
    } else {
        listOf(costColumn("Pago efetivo", 100.dp), TableColumn("Tipo", 84.dp), TableColumn("Data", 108.dp), TableColumn("Quem pagou", 120.dp),
            TableColumn("Método", 110.dp), TableColumn("Parcelas", 64.dp), TableColumn("Condição / valores", 230.dp),
            TableColumn("Origem", 105.dp), TableColumn("Anexos", 60.dp, true), TableColumn("Comprovante", 150.dp))
    }
    val rows = if (installments) {
        project.installmentBalances(balance).map { position ->
            val order = position.purchase
            FinancialRow("parcelado/${order.id}", order.name, financialStatus(position.total, position.paid, order.dueDate, today),
                listOf(position.total.tableMoney(), position.paid.tableMoney(), position.remaining.tableMoney(), order.tableInstallments(),
                    order.installmentText(), order.nature.label, project.purchasePayers(order), project.purchaseMethods(order),
                    order.dueDate.dateOr(), position.credit.tableMoney()),
                if (position.remaining == 0L) "Editar compra" else "Registrar pagamento",
                { if (position.remaining == 0L) editCost(CostKind.MATERIAL, order.id) else payTarget(CostKind.MATERIAL, order.id) },
                edit = { editCost(CostKind.MATERIAL, order.id) }, costs = listOf(position.total, position.paid, position.remaining),
                detail = "${order.nature.label} · ${order.tableInstallments()}",
                settle = { if (position.remaining != null && position.remaining!! > 0) complete(CostKind.MATERIAL, order.id) else editCost(CostKind.MATERIAL, order.id) })
        }
    } else if (outstanding) {
        balance.disbursements.map { entry ->
            val purchase = purchases[entry.targetId]
            val paid = balance.paid(entry.kind, entry.targetId)
            val value = if (entry.kind == CostKind.MATERIAL) purchase?.forecast
                else project.stages.firstOrNull { it.id == entry.targetId }?.budget
            val due = entry.forecastDate.dateOr("Sem data")
            FinancialRow("${entry.kind}/${entry.targetId}", entry.name,
                financialStatus(value, paid, entry.date, today),
                listOf(entry.amount.tableMoney(), paid.tableMoney(), project.costNature(entry.kind, entry.targetId).label, purchase?.tableInstallments() ?: "—", due,
                    if (entry.forecastDate == null) "Sem data" else if (entry.dateEstimated) "Estimativa" else "Informada",
                    purchase?.installmentText() ?: "—", entry.kind.label),
                "Registrar pagamento", { payTarget(entry.kind, entry.targetId) },
                edit = { editCost(entry.kind, entry.targetId) }, costs = listOf(entry.amount, paid),
                detail = listOfNotNull(project.costNature(entry.kind, entry.targetId).label,
                    purchase?.tableInstallments()?.takeUnless { it == "—" }).joinToString(" · "),
                settle = { if (value != null && entry.amount != null && entry.amount > 0) complete(entry.kind, entry.targetId) else editCost(entry.kind, entry.targetId) })
        }
    } else {
        project.payments.sortedWith(compareBy(nullsLast(reverseOrder())) { it.date }).map { payment ->
            val terms = if (payment.kind == CostKind.MATERIAL) purchases[payment.targetId]?.installmentText().orEmpty() else "—"
            FinancialRow(payment.id, project.targetName(payment), FinancialStatus.PAID,
                listOf(payment.amount.tableMoney(), project.costNature(payment.kind, payment.targetId).label, payment.date.dateOr(UNREPORTED), payment.payer.textOr(), payment.method.textOr(),
                    if (payment.kind == CostKind.MATERIAL) purchases[payment.targetId]?.tableInstallments() ?: "—" else "—",
                    terms, payment.kind.label, payment.proofs.size.toString(), payment.receipt.textOr()),
                "Editar pagamento", { edit(payment.id) }, costs = listOf(payment.amount),
                detail = "Tipo: ${project.costNature(payment.kind, payment.targetId).label}")
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ListHeading("Pagamentos", "+ Novo", add, "Valores pagos e compromissos em aberto")
            ViewTabs(PaymentView.entries.map { it.label }, view.ordinal) { selectedView = PaymentView.entries[it].name }
            if (installments) Text("Pago efetivo considera somente os pagamentos registrados. As parcelas futuras ficam em A pagar.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.weight(1f)) {
            if (view == PaymentView.WEEKS) CostPaymentCalendarScreen(project, today, openCost)
            else FinancialTable(if (installments) "Compra" else "Referência", columns, rows,
                if (installments) "Nenhuma compra cadastrada como parcelada."
                else if (outstanding) "Nenhum valor em aberto." else "Nenhum pagamento registrado.")
        }
    }
}
