package br.com.obracontrol

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class AgendaFilter(val label: String) {
    ALL("Tudo"), STAGES("Etapas"), DELIVERIES("Entregas"), PAYMENTS("Pagamentos");

    fun includes(entry: AgendaEntry) = when (this) {
        ALL -> true
        STAGES -> entry.kind == AgendaKind.STAGE
        DELIVERIES -> entry.kind == AgendaKind.DELIVERY
        PAYMENTS -> entry.isPayment
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManagementOverview(agenda: WorkAgenda, openAgenda: () -> Unit, openDiary: () -> Unit) {
    SheetCard {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(AppSymbol.CALENDAR)
            Column { Heading("Acompanhar a obra"); Detail("Agenda, prazos e registros de execução") }
        }
        LedgerLine("Prazos atrasados", agenda.inWindow(AgendaWindow.OVERDUE).size.toString())
        LedgerLine("Compromissos até ${agenda.cutoff.dateText()}", agenda.inWindow(AgendaWindow.NEXT_SEVEN_DAYS).size.toString())
        val overduePayments = agenda.paymentsIn(AgendaWindow.OVERDUE)
        if (overduePayments.any { it.amount != null }) {
            LedgerLine("Pagamentos vencidos", overduePayments.sumOf { it.amount ?: 0 }.brl())
        }
        val nextPayments = agenda.paymentsIn(AgendaWindow.NEXT_SEVEN_DAYS)
        if (nextPayments.any { it.amount != null }) {
            LedgerLine("Valores a pagar no período", nextPayments.sumOf { it.amount ?: 0 }.brl())
        }
        if (nextPayments.any { it.amount == null }) Detail("Há pagamentos com valor a definir no período.", Amber)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = openAgenda, modifier = Modifier.weight(1f)) {
                AppIcon(AppSymbol.CALENDAR, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Abrir agenda")
            }
            OutlinedButton(onClick = openDiary, modifier = Modifier.weight(1f)) {
                AppIcon(AppSymbol.DIARY, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Abrir diário")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgendaScreen(agenda: WorkAgenda, navigate: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(AgendaFilter.ALL.name) }
    val filter = AgendaFilter.valueOf(selected)
    val entries = agenda.entries.filter(filter::includes).groupBy { agenda.window(it.date) }
    ObraList {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Heading("Agenda da obra", "Período de ${agenda.today.dateText()} a ${agenda.cutoff.dateText()}")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AgendaFilter.entries.forEach { item ->
                        FilterChip(selected = filter == item, onClick = { selected = item.name }, label = { Text(item.label) })
                    }
                }
            }
        }
        if (entries.isEmpty()) item { SheetCard { Detail("Nenhum compromisso em aberto neste filtro.") } }
        AgendaWindow.entries.forEach { window ->
            val group = entries[window].orEmpty()
            if (group.isNotEmpty()) {
                item(key = window.name) { Heading(window.label) }
                items(group, key = AgendaEntry::key) { entry ->
                    SheetCard {
                        Heading(entry.name)
                        Tag("${entry.kind.label} · ${entry.date.dateOr("Sem data")}", window == AgendaWindow.OVERDUE)
                        if (entry.detail.isNotBlank()) Detail(entry.detail)
                        if (entry.isPayment) LedgerLine("Saldo da programação", entry.amount.costText())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val record = if (entry.kind == AgendaKind.STAGE || entry.kind == AgendaKind.LABOR_PAYMENT) "etapa" else "compra"
                            TextButton(onClick = { navigate("$record/${entry.targetId}") }) {
                                Text(when (entry.kind) {
                                    AgendaKind.STAGE -> "Abrir etapa"
                                    AgendaKind.DELIVERY -> "Abrir compra"
                                    else -> "Editar vencimento"
                                })
                            }
                            entry.kind.costKind?.let { kind ->
                                TextButton(onClick = { navigate("pagamento/novo/${kind.name}/${entry.targetId}") }) { Text("Registrar pagamento") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiaryScreen(project: Project, add: () -> Unit, open: (String) -> Unit) {
    ObraList {
        item { ListHeading("Diário de obra", "Novo registro", add) }
        if (project.diaryEntries.isEmpty()) item {
            SheetCard { Detail("Registre atividades, ocorrências e fotos para acompanhar a execução.") }
        }
        items(project.diaryNewestFirst(), key = DiaryEntry::id) { entry ->
            SheetCard {
                val subtitle = listOfNotNull(entry.date.dateText(), project.stageName(entry.stageId)).joinToString(" · ")
                Heading(entry.title, subtitle)
                if (entry.activities.isNotBlank()) Detail(entry.activities.take(240) + if (entry.activities.length > 240) "…" else "")
                if (entry.occurrences.isNotBlank()) Tag("Ocorrência registrada", warning = true)
                if (entry.attachments.isNotEmpty()) Detail("${entry.attachments.size} anexo(s)")
                TextButton(onClick = { open(entry.id) }) { Text("Abrir registro") }
            }
        }
    }
}
