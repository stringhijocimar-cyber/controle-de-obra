package br.com.obracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

internal val Navy = Color(0xFF14263B)
internal val Mint = Color(0xFF48C6A3)
internal val Green = Color(0xFF08745B)
internal val Blue = Color(0xFF276C9F)
internal val Amber = Color(0xFF8F4C08)
internal val Mist = Color(0xFFF1F5F8)
internal val Line = Color(0xFFD9E4EE)
internal val Muted = Color(0xFF455C71)
private val SoftGreen = Color(0xFFE2F3EC)
private val SoftAmber = Color(0xFFFFEED6)
private val Panel = RoundedCornerShape(18.dp)

internal enum class Section(val title: String, val label: String, val mark: String) {
    DASHBOARD("Visão geral", "Painel", "◫"),
    STAGES("Cronograma e execução", "Etapas", "≡"),
    PURCHASES("Orçamentos e pedidos", "Compras", "▣"),
    PAYMENTS("Prestação de contas", "Pagamentos", "R$"),
    REPORT("Exportação de arquivos", "Relatório", "▤"),
}

@Composable
internal fun ObraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green, onPrimary = Color.White, primaryContainer = SoftGreen,
            onPrimaryContainer = Navy, secondary = Blue, secondaryContainer = Line,
            onSecondaryContainer = Navy, background = Mist, onBackground = Navy,
            surface = Color.White, onSurface = Navy, surfaceVariant = Mist,
            onSurfaceVariant = Muted, outline = Color(0xFF74899C), outlineVariant = Line,
        ),
        content = content,
    )
}

@Composable
internal fun ObraHeader(title: String, subtitle: String, onBack: (() -> Unit)?, onSettings: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 10.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 7.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Mint)) { Text("‹ Voltar") }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFCCDCE8), style = MaterialTheme.typography.bodySmall)
        }
        if (onSettings != null) {
            TextButton(onClick = onSettings, colors = ButtonDefaults.textButtonColors(contentColor = Mint)) { Text("Dados da obra") }
        }
    }
}

@Composable
internal fun ObraNavigation(active: Section, onSelect: (Section) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        Section.entries.forEach { section ->
            NavigationBarItem(
                selected = section == active,
                onClick = { onSelect(section) },
                icon = { Text(section.mark, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                label = { Text(section.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = SoftGreen, selectedIconColor = Green,
                    selectedTextColor = Green, unselectedIconColor = Muted, unselectedTextColor = Muted),
            )
        }
    }
}

@Composable
internal fun ObraList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        content = content,
    )
}

@Composable
internal fun SheetCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = Panel, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
internal fun Heading(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    subtitle?.let { Detail(it) }
}

@Composable
internal fun Detail(value: String, color: Color = Muted) {
    Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
internal fun Tag(value: String, warning: Boolean = false) {
    Text(
        value,
        Modifier.background(if (warning) SoftAmber else SoftGreen, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (warning) Amber else Green,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun ListHeading(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
internal fun RecoveryScreen(restore: () -> Unit) {
    ObraList {
        item {
            SheetCard {
                Heading("Recuperar cadastro", "Não foi possível ler os dados salvos. Selecione um backup para voltar a editar a obra.")
                Button(onClick = restore) { Text("Restaurar backup") }
            }
        }
    }
}

@Composable
internal fun Dashboard(
    project: Project,
    total: Balance,
    agenda: WorkAgenda,
    edit: () -> Unit,
    issues: () -> Unit,
    restore: () -> Unit,
    openAgenda: () -> Unit,
    openDiary: () -> Unit,
) {
    val isEmpty = project.stages.isEmpty() && project.purchases.isEmpty() && project.payments.isEmpty()
    ObraList {
        item {
            Card(shape = Panel, colors = CardDefaults.cardColors(containerColor = Navy)) {
                Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (project.referenceDate != null) {
                        Text("BASE DE ${project.referenceDate.dateText()}", style = MaterialTheme.typography.labelMedium, color = Mint)
                    }
                    Text(project.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    if (project.start != null) Text("Início ${project.start.dateText()}", color = Color(0xFFCCDCE8))
                    if (isEmpty) {
                        Text("Cadastre as etapas e as compras para montar o cronograma e o balanço.", color = Color(0xFFCCDCE8))
                        FilledTonalButton(onClick = edit) { Text("Configurar obra") }
                        TextButton(onClick = restore, colors = ButtonDefaults.textButtonColors(contentColor = Mint)) { Text("Importar dados da obra") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Previsão parcial", total.total.brl(), Navy, Modifier.weight(1f))
                Metric("Total pago", total.paid.brl(), Green, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Saldo conhecido", total.remaining.brl(), Blue, Modifier.weight(1f))
                Metric("Acréscimos", total.extras.brl(), Amber, Modifier.weight(1f))
            }
        }
        item { ManagementOverview(agenda, openAgenda, openDiary) }
        item {
            SheetCard {
                Heading("Execução da obra")
                val physical = total.physicalProgress / 100.0
                ProgressBar("Avanço físico médio", percent(physical), physical, Green)
                Detail("${total.completed} de ${project.stages.size} etapas concluídas")
                Spacer(Modifier.height(3.dp))
                val financial = if (total.total > 0) total.paid.toDouble() / total.total else 0.0
                ProgressBar("Execução financeira", percent(financial), financial, Blue)
            }
        }
        item {
            SheetCard {
                Heading("Composição dos custos")
                val largest = maxOf(total.laborForecast, total.materialForecast, 1)
                ProgressBar("Materiais e equipamentos", total.materialForecast.brl(), total.materialForecast.toDouble() / largest, Blue)
                ProgressBar("Mão de obra", total.laborForecast.brl(), total.laborForecast.toDouble() / largest, Green)
                Detail("Valores a pagar conhecidos: ${total.materialToPay.brl()} em compras e ${total.laborToPay.brl()} em mão de obra.")
            }
        }
        if (total.payers.isNotEmpty()) {
            item {
                SheetCard {
                    Heading("Quem pagou")
                    total.payers.forEach { entry ->
                        LedgerLine("${entry.payer} · ${entry.kind.label}", entry.amount.brl())
                        Detail(entry.method)
                    }
                }
            }
        }
        item {
            SheetCard {
                Heading("Pendências")
                val count = project.issues.count { !it.closed } + total.alerts().size
                Detail(if (count == 1) "1 registro em aberto" else "$count registros em aberto")
                TextButton(onClick = issues) { Text("Ver pendências") }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier) {
    SheetCard(modifier) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelLarge)
        Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

internal fun percent(ratio: Double): String = NumberFormat.getPercentInstance(brazil).apply {
    maximumFractionDigits = 1
}.format(ratio)

@Composable
private fun ProgressBar(label: String, value: String, ratio: Double, color: Color) {
    LedgerLine(label, value)
    LinearProgressIndicator(progress = { ratio.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp),
        color = color, trackColor = Line, gapSize = 0.dp, drawStopIndicator = {})
}

@Composable
internal fun LedgerLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun StageList(project: Project, total: Balance, add: () -> Unit, edit: (String) -> Unit) {
    ObraList {
        item { ListHeading("Etapas da obra", "Nova etapa", add) }
        if (project.stages.isEmpty()) item { SheetCard { Detail("Cadastre a primeira etapa para montar o cronograma.") } }
        items(project.stages, key = { it.id }) { stage ->
            SheetCard {
                Heading(stage.name)
                Tag("${stage.status} · ${stage.progress}%")
                Detail("Previsto ${stage.plannedStart.dateOr()} a ${stage.plannedEnd.dateOr()}")
                if (stage.actualStart != null || stage.actualEnd != null) {
                    Detail("Realizado ${stage.actualStart.dateOr()} a ${stage.actualEnd.dateOr()}")
                }
                LedgerLine("Previsto · pago", "${stage.budget.costText()} · ${total.paid(CostKind.LABOR, stage.id).brl()}")
                TextButton(onClick = { edit(stage.id) }) { Text("Editar etapa") }
            }
        }
    }
}

@Composable
internal fun PurchaseList(project: Project, total: Balance, add: () -> Unit, open: (String) -> Unit) {
    ObraList {
        item { ListHeading("Compras e materiais", "Nova compra", add) }
        if (project.purchases.isEmpty()) item { SheetCard { Detail("Cadastre uma compra, cotação ou material a cotar.") } }
        items(project.purchases, key = { it.id }) { purchase ->
            SheetCard {
                Heading(purchase.name, listOf(purchase.supplier, purchase.document).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank))
                Tag(total.purchaseStatus(purchase), purchase.forecast == null)
                LedgerLine("Previsão · pago", "${purchase.forecast.costText()} · ${total.paid(CostKind.MATERIAL, purchase.id).brl()}")
                purchase.deliveryExpected?.let { Detail("Entrega prevista em ${it.dateText()}") }
                purchase.receivedDate?.let { Detail("Recebido em ${it.dateText()}", Green) }
                if (purchase.extra) Tag("Acréscimo declarado", true)
                if (purchase.documents.isNotEmpty()) Detail("${purchase.documents.size} documento(s) anexado(s)")
                TextButton(onClick = { open(purchase.id) }) { Text("Abrir compra") }
            }
        }
    }
}

@Composable
internal fun PaymentsScreen(project: Project, total: Balance, add: () -> Unit, edit: (String) -> Unit) {
    ObraList {
        item { ListHeading("Pagamentos", "Novo pagamento", add) }
        item {
            SheetCard {
                LedgerLine("Total pago", total.paid.brl())
                LedgerLine("Compras", total.materialPaid.brl())
                LedgerLine("Mão de obra", total.laborPaid.brl())
                if (project.stages.isEmpty() && project.purchases.isEmpty()) Detail("Cadastre uma etapa ou compra para registrar um pagamento.")
            }
        }
        if (project.payments.isEmpty()) item { SheetCard { Detail("Nenhum pagamento registrado.") } }
        items(project.payments.sortedWith(compareBy(nullsLast(reverseOrder())) { it.date }), key = { it.id }) { payment ->
            SheetCard {
                Heading(project.targetName(payment), payment.kind.label)
                LedgerLine(payment.date.dateOr(UNREPORTED), payment.amount.brl())
                Detail("${payment.payer.textOr()} · ${payment.method.textOr()}")
                if (payment.receipt.isNotBlank()) Detail("Comprovante ${payment.receipt}")
                if (payment.proofs.isNotEmpty()) Detail("${payment.proofs.size} comprovante(s) anexado(s)")
                TextButton(onClick = { edit(payment.id) }) { Text("Editar pagamento") }
            }
        }
    }
}

@Composable
internal fun ExportScreen(busy: Boolean, pdf: () -> Unit, openPdf: (() -> Unit)?, backup: () -> Unit, restore: () -> Unit) {
    ObraList {
        item {
            SheetCard {
                Heading("Relatório executivo em PDF", "Painel, gráficos, Gantt, cronograma físico-financeiro, balanço, projeções, compras detalhadas, pagamentos, pendências, agenda e diário.")
                Button(enabled = !busy, onClick = pdf) { Text("Gerar PDF completo") }
                if (openPdf != null) TextButton(enabled = !busy, onClick = openPdf) { Text("Abrir último PDF gerado") }
            }
        }
        item {
            SheetCard {
                Heading("Backup da obra", "Exporte o cadastro com os anexos ou importe um arquivo ZIP ou JSON.")
                OutlinedButton(enabled = !busy, onClick = backup) { Text("Exportar backup com anexos") }
                OutlinedButton(enabled = !busy, onClick = restore) { Text("Importar dados da obra") }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}
