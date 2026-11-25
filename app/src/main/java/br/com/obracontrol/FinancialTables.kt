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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

private val UnpaidRed = Color(0xFFB3261E)
private val GridLine = Color(0xFFBCCBD8)

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
)

private fun Long?.tableMoney(): String = this?.brl()?.removePrefix("R$")?.trim() ?: "A definir"

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
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${rows.size} registro(s) · valores em R$", Modifier.weight(1f),
                color = Muted, fontSize = 11.sp)
            TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Tela cheia", fontSize = 12.sp)
            }
        }
        Box(Modifier.weight(1f)) {
            FinancialGrid(nameColumn, columns, rows, scroll, open = { it.open() }, edit = { it.edit() })
        }
        TableLegend()
        Text("Toque no custo para editar · deslize para ver as colunas", color = Muted,
            fontSize = 10.sp, modifier = Modifier.padding(bottom = 5.dp))
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize().testTag("table-fullscreen"), color = Color.White) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$nameColumn · ${rows.size} registro(s)", Modifier.weight(1f),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { expanded = false }) { Text("Fechar tabela") }
                    }
                    Text("Valores em R$ · gire o celular para ver mais colunas", color = Muted,
                        fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Box(Modifier.weight(1f)) {
                        FinancialGrid(nameColumn, columns, rows, scroll,
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
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✓ Pago", color = Green, fontSize = 11.sp)
        Text("◐ Parcial", color = Amber, fontSize = 11.sp)
        Text("○ Aberto / ! Atrasado", color = UnpaidRed, fontSize = 11.sp)
    }
}

@Composable
private fun FinancialGrid(
    nameColumn: String,
    columns: List<TableColumn>,
    rows: List<FinancialRow>,
    scroll: androidx.compose.foundation.ScrollState,
    open: (FinancialRow) -> Unit,
    edit: (FinancialRow) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().border(1.dp, GridLine)) {
        val fixedWidth = if (maxWidth >= 600.dp) 170.dp else 108.dp
        val statusWidth = 36.dp
        val contentWidth = columns.fold(0.dp) { width, column -> width + column.width }
        val rowHeight = maxOf(48.dp, (40 * fontScale).dp)
        val headerHeight = maxOf(38.dp, (34 * fontScale).dp)
        Surface(Modifier.fillMaxSize(), color = Color.White) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(headerHeight).background(Navy),
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
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                            Row(
                                Modifier.fillMaxWidth().height(rowHeight)
                                    .background(if (index % 2 == 0) Color.White else Mist)
                                    .testTag("table-row-${row.key}")
                                    .clickable(onClickLabel = row.action, onClick = { open(row) }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GridCell(row.name, fixedWidth, strong = true, tag = "table-name-${row.key}")
                                Box(Modifier.weight(1f).horizontalScroll(scroll).testTag("table-values-${row.key}")) {
                                    Row(Modifier.width(statusWidth + contentWidth), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.width(statusWidth).fillMaxHeight().border(0.5.dp, GridLine),
                                            contentAlignment = Alignment.Center) { StatusIcon(row.status) }
                                        columns.zip(row.values).forEachIndexed { index, (column, value) ->
                                            GridCell(value, column.width, numeric = column.numeric,
                                                edit = if (column.cost) ({ edit(row) }) else null,
                                                tag = if (column.cost) "table-cost-${row.key}-$index" else null)
                                        }
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
private fun GridCell(
    value: String, width: Dp, header: Boolean = false, numeric: Boolean = false, strong: Boolean = false,
    edit: (() -> Unit)? = null, tag: String? = null,
) {
    var options by remember { mutableStateOf(false) }
    val interaction = if (edit == null) Modifier else Modifier
        .clickable(onClickLabel = "Opções do custo") { options = true }
    Box(Modifier.width(width).fillMaxHeight()
        .then(if (tag == null) Modifier else Modifier.testTag(tag))
        .then(interaction).border(0.5.dp, if (header) Color(0xFF536678) else GridLine)
        .padding(horizontal = 6.dp), contentAlignment = if (numeric) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(value.replace('\n', ' '), color = if (header) Color.White else if (edit != null) Blue else Navy,
            fontSize = if (header) 11.sp else 12.sp,
            fontWeight = if (header || strong) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = if (numeric && !header) FontFamily.Monospace else FontFamily.Default,
            textAlign = if (numeric) TextAlign.End else TextAlign.Start,
            textDecoration = if (edit != null) TextDecoration.Underline else TextDecoration.None,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (edit != null) {
            DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                DropdownMenuItem(text = { Text("Editar dados") }, onClick = { options = false; edit() })
            }
        }
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
            "Editar etapa", { edit(stage.id) })
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
            "Abrir compra", { open(order.id) })
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
                edit = { editCost(entry.kind, entry.targetId) })
        }
    } else {
        project.payments.sortedWith(compareBy(nullsLast(reverseOrder())) { it.date }).map { payment ->
            val terms = if (payment.kind == CostKind.MATERIAL) purchases[payment.targetId]?.installmentText().orEmpty() else "—"
            FinancialRow(payment.id, project.targetName(payment), FinancialStatus.PAID,
                listOf(payment.amount.tableMoney(), payment.date.dateOr(UNREPORTED), payment.payer.textOr(), payment.method.textOr(),
                    if (payment.kind == CostKind.MATERIAL) purchases[payment.targetId]?.tableInstallments() ?: "—" else "—",
                    terms, payment.kind.label, payment.proofs.size.toString(), payment.receipt.textOr()),
                "Editar pagamento", { edit(payment.id) })
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ListHeading("Pagamentos", "+ Novo", add)
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
