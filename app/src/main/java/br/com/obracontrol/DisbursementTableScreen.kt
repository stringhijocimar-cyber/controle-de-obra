package br.com.obracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private val FlowNameWidth = 132.dp
private val FlowMetaWidth = 112.dp
private val FlowWeekWidth = 108.dp
private val flowDate = DateTimeFormatter.ofPattern("dd/MM", brazil)
private val flowMonth = DateTimeFormatter.ofPattern("MMM/yy", brazil)

@Composable
internal fun WeeklyDisbursementScreen(project: Project, today: LocalDate, openCost: (CostKind, String) -> Unit) {
    val table = remember(project) { DisbursementTable(project) }
    var open by rememberSaveable { mutableStateOf(ArrayList(table.roots.map { it.key })) }
    var view by rememberSaveable { mutableStateOf(CashView.TOTAL.name) }
    var page by rememberSaveable { mutableStateOf(0) }
    var info by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val scroll = rememberScrollState(initial = with(density) { (FlowMetaWidth * 4).roundToPx() })
    val scope = rememberCoroutineScope()
    val activePage = page.coerceIn(0, table.pageCount - 1)
    val columns = table.columns(activePage)
    val rows = table.visible(open.toSet())
    val mode = CashView.valueOf(view)
    val width = FlowMetaWidth * 4 + FlowWeekWidth * columns.size
    fun toggle(key: String) { open = ArrayList(if (key in open) open - key else open + key) }
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text("Como ler os desembolsos") },
        text = { Text("Abra + para ver tipo, etapa, custo e pagamentos ou parcelas. Total soma o pago na data real e o saldo no vencimento. Mão de obra integra Serviço. Deslize para ver as semanas ou os responsáveis; valores e responsáveis desconhecidos ficam a definir.") },
        confirmButton = { TextButton(onClick = { info = false }) { Text("Entendi") } })
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Calendário de desembolsos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        ViewTabs(CashView.entries.map { it.label }, mode.ordinal) { view = CashView.entries[it].name }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { scope.launch { scroll.animateScrollTo(0) } }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(3.dp)) { Text("Responsáveis e saldos", style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = { scope.launch { scroll.animateScrollTo(with(density) { (FlowMetaWidth * 4).roundToPx() }.coerceAtMost(scroll.maxValue)) } }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(3.dp)) { Text("Ver semanas", style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = { info = true }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(3.dp)) { Text("Como ler", style = MaterialTheme.typography.labelSmall) }
        }
        if (table.pageCount > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = activePage > 0, onClick = { page = activePage - 1 }) { Text("‹ Anterior") }
            Text("${activePage + 1} / ${table.pageCount}")
            TextButton(enabled = activePage + 1 < table.pageCount, onClick = { page = activePage + 1 }) { Text("Próximo ›") }
        }
        Surface(Modifier.fillMaxWidth().weight(1f).border(1.dp, Line, RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp)) {
            Column {
                Row(Modifier.fillMaxWidth().height(60.dp).background(Navy)) {
                    FlowCell("Etapa / descrição", FlowNameWidth, header = true)
                    Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                        Row(Modifier.width(width)) {
                            listOf("Quem paga", "Como paga", "Pago efetivo", "A pagar").forEach { FlowCell(it, FlowMetaWidth, header = true) }
                            Column(Modifier.width(FlowWeekWidth * columns.size)) {
                                FlowHeaderGroups(columns.map { it?.year?.toString() ?: "—" }, scroll.value)
                                FlowHeaderGroups(columns.map { it?.format(flowMonth)?.replace(".", "") ?: "—" }, scroll.value)
                                Row(Modifier.height(28.dp)) {
                                    columns.forEach { date ->
                                        val label = date?.let {
                                            val year = it.get(WeekFields.ISO.weekBasedYear())
                                            "S${it.get(WeekFields.ISO.weekOfWeekBasedYear()).toString().padStart(2, '0')}" +
                                                if (year != it.year) "/${year % 100}\n${it.format(flowDate)}" else "\n${it.format(flowDate)}"
                                        } ?: "Sem data"
                                        FlowCell(label, FlowWeekWidth, header = true, tag = "flow-week-${date ?: "undated"}")
                                    }
                                }
                            }
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f).testTag("flow-table-body")) {
                    items(rows, key = { it.first.key }) { (node, depth) ->
                        FlowTableRow(table, node, depth, columns, mode, width, scroll, node.key in open, { toggle(node.key) }, openCost)
                    }
                    if (rows.isEmpty()) item { Detail("Nenhum desembolso cadastrado.") }
                }
                FlowTableRow(table, table.total, 0, columns, mode, width, scroll, false, {}, openCost, total = true)
            }
        }
    }
}

@Composable
private fun FlowHeaderGroups(labels: List<String>, scrollPixels: Int) {
    val offset = with(LocalDensity.current) { scrollPixels.toDp() } - FlowMetaWidth * 4
    Box(Modifier.width(FlowWeekWidth * labels.size).height(16.dp)) {
        var first = 0
        while (first < labels.size) {
            val label = labels[first]
            var next = first + 1
            while (next < labels.size && labels[next] == label) next++
            val left = FlowWeekWidth * first
            val bandWidth = FlowWeekWidth * (next - first)
            val labelWidth = minOf(bandWidth - 8.dp, 92.dp)
            val x = maxOf(left + 4.dp, offset + 4.dp).coerceAtMost(left + bandWidth - labelWidth)
            Box(Modifier.offset(x = left).width(bandWidth).fillMaxHeight().border(0.5.dp, Line.copy(alpha = 0.3f)))
            Text(label, Modifier.offset(x = x).width(labelWidth).align(Alignment.CenterStart), color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            first = next
        }
    }
}

@Composable
private fun FlowTableRow(table: DisbursementTable, node: FlowNode, depth: Int, columns: List<LocalDate?>, view: CashView,
    width: Dp, scroll: androidx.compose.foundation.ScrollState, expanded: Boolean, toggle: () -> Unit,
    openCost: (CostKind, String) -> Unit, total: Boolean = false) {
    val group = node.layer in listOf(FlowLayer.FAMILY, FlowLayer.STAGE)
    val color = if (total) androidx.compose.ui.graphics.Color(0xFFE7F4EF) else if (group) Mist else androidx.compose.ui.graphics.Color.White
    Row(Modifier.fillMaxWidth().height(54.dp).background(color).testTag("flow-row-${node.key}")) {
        Row(Modifier.width(FlowNameWidth).fillMaxHeight().padding(start = (3 + depth * 3).dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (node.children.isNotEmpty()) TextButton(onClick = toggle, modifier = Modifier.size(27.dp).testTag("flow-toggle-${node.key}"), contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
            }
            Text(node.label, Modifier.weight(1f).then(if (node.kind != null && node.targetId != null)
                Modifier.clickable { openCost(node.kind, node.targetId) } else Modifier),
                style = MaterialTheme.typography.labelMedium, fontWeight = if (group || total) FontWeight.Bold else FontWeight.Medium,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Row(Modifier.width(width)) {
                FlowCell(if (total) "—" else table.people(node, false), FlowMetaWidth)
                FlowCell(if (total) "—" else table.people(node, true), FlowMetaWidth)
                FlowCell(node.paid.brl(), FlowMetaWidth, tone = Green, tag = "flow-paid-${node.key}")
                FlowCell(if (node.pending.entirelyUndefined) UNDEFINED else node.pending.known.brl() + if (node.pending.partial) " + ?" else "",
                    FlowMetaWidth, tone = Blue, tag = "flow-pending-${node.key}")
                columns.forEach { week ->
                    val amount = node.amounts(week, view)
                    val matching = node.entries.filter { it.date?.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) == week }
                    val hasRows = matching.any { view == CashView.TOTAL || it.paid == (view == CashView.PAID) }
                    val label = when { !hasRows -> "—"; amount.entirelyUndefined -> UNDEFINED; else -> amount.known.brl() + if (amount.partial) " + ?" else "" }
                    FlowCell(label, FlowWeekWidth, tone = if (view == CashView.PAID || matching.all { it.paid }) Green else Navy,
                        tag = "flow-value-${node.key}-${week ?: "undated"}-${view.name}")
                }
            }
        }
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun FlowCell(value: String, width: Dp, header: Boolean = false, tone: androidx.compose.ui.graphics.Color = Navy, tag: String? = null) {
    Box(Modifier.width(width).fillMaxHeight().border(0.4.dp, Line.copy(alpha = if (header) 0.2f else 0.65f))
        .padding(horizontal = 5.dp), contentAlignment = Alignment.CenterStart) {
        Text(value, modifier = if (tag != null) Modifier.testTag(tag) else Modifier, color = if (header) androidx.compose.ui.graphics.Color.White else tone,
            style = if (header) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp) else MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = if (header) 2 else 3, overflow = TextOverflow.Ellipsis)
    }
}
