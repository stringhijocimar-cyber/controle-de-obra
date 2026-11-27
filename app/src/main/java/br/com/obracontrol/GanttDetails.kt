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
                Text("Azul claro: prazo total. Azul preenchido: tempo decorrido, em dias. O avanço físico aparece nos percentuais da etapa.")
                Text("Execução: contorno verde tracejado para o período previsto e barra verde para o realizado. O fim previsto fica vazado; o fim real fica preenchido.")
                Text("Materiais: a faixa roxa mostra a janela de entregas. Losangos vazados são previsões e preenchidos indicam recebimentos com data.")
                Text("Pagamentos: pontos escuros são pagos e pontos âmbar são a pagar. A linha vertical identifica hoje.")
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
                Text("Execução", fontWeight = FontWeight.Bold, color = Green)
                Text("Prevista: ${row.plannedDates}")
                Text("Realizada: ${row.actualDates}")
                Text("Fim previsto: ${row.stage.plannedEnd.dateOr()}")
                Text("Fim real: ${row.stage.actualEnd.dateOr()}")
                Text("Avanço", fontWeight = FontWeight.Bold)
                Text("Execução ${stagePercent(progress.execution.toDouble())} · Materiais ${progress.materialsText} · Geral ${progress.generalText}")
                Text(progress.weightsText)
                HorizontalDivider()
                Text("Entregas de materiais", fontWeight = FontWeight.Bold, color = MaterialArrival)
                if (row.deliveries.isEmpty()) Text("Nenhum material vinculado a esta etapa.")
                row.deliveries.forEach { item ->
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    Text("Previsão: ${item.expected.dateOr()} · Recebido em: ${item.received.dateOr()}")
                    Text("Recebimento: ${item.progress}%")
                }
                HorizontalDivider()
                Text("Pagamentos de mão de obra", fontWeight = FontWeight.Bold)
                if (row.spend.isEmpty()) Text("Nenhum pagamento ou vencimento cadastrado.")
                row.spend.forEach { Text("${it.status.label} · ${it.amount.costText()} · ${it.date.dateOr()}") }
            }
        }, confirmButton = { TextButton(onClick = edit, modifier = Modifier.testTag("gantt-details-edit")) { Text("Editar etapa") } },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.testTag("gantt-details-close")) { Text("Fechar") } })
}
