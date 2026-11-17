package br.com.obracontrol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LaborSpendScreen(calendar: LaborSpendCalendar, edit: (String) -> Unit) {
    ObraList {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Heading("Calendário de desembolsos", "Mão de obra por semana")
                if (calendar.weeks.isEmpty()) {
                    Detail("Cadastre os valores das etapas e registre os pagamentos para acompanhar os desembolsos.")
                }
            }
        }
        items(calendar.weeks, key = { it.start?.toString() ?: "sem-data" }) { week ->
            SheetCard {
                Text(week.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LedgerLine("Pago", week.paid.brl())
                LedgerLine(if (week.undefined > 0) "Saldo conhecido a pagar" else "Saldo a pagar", week.balance.brl())
                week.entries.forEach { entry ->
                    HorizontalDivider(color = Line)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(onClickLabel = "Editar etapa") { edit(entry.stageId) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry.stageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Detail(listOfNotNull(entry.date?.dateText(), entry.status.label).joinToString(" · "))
                        }
                        Text(entry.amount.costText(), color = if (entry.paid) Green else Amber,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
