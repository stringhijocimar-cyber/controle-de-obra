package br.com.obracontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun GanttHelp(close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Como ler o Gantt") },
        text = {
            Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.55f).dp)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("São três barras por etapa: azul para o total acumulado, verde para mão de obra e roxo para materiais e serviços. A parte sólida corresponde ao percentual concluído; o restante fica em cor suave.")
                Text("O total combina mão de obra e materiais/serviços com os pesos da etapa (padrão 50% / 50%). Materiais usam o recebimento informado; serviços usam a conclusão registrada na compra. Pagar não aumenta o avanço físico.")
                Text("As três barras usam o prazo total da etapa como referência para comparar seus avanços. A linha vertical identifica hoje. Os pagamentos e as datas de cada entrega continuam disponíveis na consulta.")
                Text("A escala automática compacta as semanas. Ao escolher Dias, as colunas se ampliam para detalhar as datas. Trocar a escala restaura o ajuste automático.")
                Text("Ajustar mostra o período completo. Use − e + para ampliar; Ir para hoje centraliza a data atual. Deslize as barras para percorrer o período.")
                Text("Toque nas barras para consultar datas, materiais e pagamentos. Toque no nome da etapa para editar. Semanas do Gantt seguem o calendário ISO; o calendário financeiro fecha na sexta-feira.")
            }
        }, confirmButton = { TextButton(onClick = close) { Text("Entendi") } })
}

@Composable
internal fun GanttStageDetails(row: GanttRow, progress: StageProgress, today: java.time.LocalDate,
    close: () -> Unit, edit: () -> Unit) {
    AlertDialog(onDismissRequest = close, modifier = Modifier.testTag("gantt-details"),
        title = { Text(row.stage.name, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.55f).dp)
                .verticalScroll(rememberScrollState()).testTag("gantt-details-body"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prazo total", fontWeight = FontWeight.Bold, color = Blue)
                Text(row.totalPeriod?.dates() ?: UNDEFINED)
                Text(row.durationText(today))
                Text("Mão de obra", fontWeight = FontWeight.Bold, color = Green)
                Text("Prevista: ${row.plannedDates}")
                Text("Realizada: ${row.actualDates}")
                Text("Fim previsto: ${row.stage.plannedEnd.dateOr()}")
                Text("Fim real: ${row.stage.actualEnd.dateOr()}")
                Text("Avanço", fontWeight = FontWeight.Bold)
                Text("Mão de obra ${stagePercent(progress.execution.toDouble())} · Materiais e serviços ${progress.materialsText} · Total ${progress.generalText}")
                Text(progress.weightsText)
                HorizontalDivider()
                Text("Materiais e serviços", fontWeight = FontWeight.Bold, color = MaterialArrival)
                if (row.deliveries.isEmpty()) Text("Nenhum material ou serviço vinculado a esta etapa.")
                row.deliveries.forEach { item ->
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    Text(item.nature.label, style = MaterialTheme.typography.labelSmall)
                    Text("Previsão: ${item.expected.dateOr()} · ${if (item.nature == CostNature.SERVICE) "Concluído em" else "Recebido em"}: ${item.received.dateOr()}")
                    Text("${if (item.nature == CostNature.SERVICE) "Conclusão" else "Recebimento"}: ${item.progress}%")
                }
                HorizontalDivider()
                Text("Mão de obra: ${row.stage.budget.costText()}", fontWeight = FontWeight.Bold)
                Text("Pagamentos de mão de obra", fontWeight = FontWeight.Bold)
                if (row.spend.isEmpty()) Text("Nenhum pagamento ou vencimento cadastrado.")
                row.spend.forEach { Text("${it.status.label} · ${it.amount.costText()} · ${it.date.dateOr()}") }
            }
        }, confirmButton = { TextButton(onClick = edit, modifier = Modifier.testTag("gantt-details-edit")) { Text("Editar etapa") } },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.testTag("gantt-details-close")) { Text("Fechar") } })
}
