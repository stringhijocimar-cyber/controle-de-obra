package br.com.obracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PaymentOverview(project: Project, balance: Balance) {
    val undefined = project.purchases.count { it.forecast == null } +
        if (project.laborBudget == null) project.stages.count { it.budget == null } else 0
    Column(Modifier.fillMaxWidth().background(Navy, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (undefined > 0) "Custo total conhecido" else "Custo total da obra", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 16.sp)
            Text(balance.total.brl(), Modifier.testTag("payments-overview-forecast"), color = Color.White,
                fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PaymentMetric("Total pago", balance.paid.brl(), "payments-overview-paid", Mint, Modifier.weight(1f))
            PaymentMetric(if (balance.pendingByCost.partial) "Total a pagar conhecido" else "Total a pagar",
                if (balance.pendingByCost.entirelyUndefined) UNDEFINED else balance.pendingByCost.known.brl(),
                "payments-overview-pending", Color(0xFFFFD899), Modifier.weight(1f))
        }
        if (undefined > 0) Text("$undefined custo(s) a definir; previsão parcial.", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 14.sp)
        if (balance.overpaidByCost > 0) Text("Pago a maior: ${balance.overpaidByCost.brl()} · conferir por custo", color = Color(0xFFFFD899), fontSize = 10.sp, lineHeight = 14.sp)
        if (project.laborBudget != null && balance.laborAllocated != project.laborBudget) {
            Text("Contrato de mão de obra: conferir a distribuição por etapas.", color = Color(0xFFFFD899), fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun PaymentMetric(label: String, value: String, tag: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 12.sp)
        Text(value, Modifier.testTag(tag), color = color, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaymentCategoryFilters(project: Project, balance: Balance, category: CostCategory?, onSelect: (CostCategory?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (listOf<CostCategory?>(null, CostCategory.MATERIAL, CostCategory.LABOR, CostCategory.SERVICE)).forEach { item ->
                FilterChip(selected = category == item, onClick = { onSelect(item) },
                    modifier = Modifier.testTag("payments-category-${item?.name ?: "ALL"}"),
                    label = { Text(item?.filterLabel ?: "Todos", fontSize = 11.sp) })
            }
        }
        category?.let { selected ->
            val orders = project.purchases.filter { project.costCategory(CostKind.MATERIAL, it.id) == selected }
            val paid = project.payments.filter { project.costCategory(it.kind, it.targetId) == selected }.sumOf { it.amount }
            val forecast = if (selected == CostCategory.LABOR) balance.laborForecast
                else orders.sumOf { it.forecast ?: balance.paid(CostKind.MATERIAL, it.id) }
            val pending = tableAmountTotal(balance.disbursements.filter { project.costCategory(it.kind, it.targetId) == selected }.map { it.amount })
            val pendingText = if (pending.entirelyUndefined) UNDEFINED else pending.known.brl() + if (pending.partial) " + ?" else ""
            val partial = if (selected == CostCategory.LABOR) project.laborBudget == null && project.stages.any { it.budget == null }
                else orders.any { it.forecast == null }
            Text("${selected.filterLabel}: ${forecast.brl()}${if (partial) " + ?" else ""} previstos · ${paid.brl()} pagos · $pendingText a pagar",
                Modifier.testTag("payments-category-summary"), color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}
