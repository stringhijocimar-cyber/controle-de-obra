package br.com.obracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate

internal val Navy = Color(0xFF172D3C)
internal val Mint = Color(0xFF70DFC1)
internal val Green = Color(0xFF087B62)
internal val Blue = Color(0xFF326CA3)
internal val Amber = Color(0xFF995B12)
internal val Mist = Color(0xFFF5F7FA)
internal val Line = Color(0xFFE2E8EE)
internal val Muted = Color(0xFF586B7B)
private val SoftGreen = Color(0xFFE5F4EE)
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
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp)),
        typography = Typography(
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}

@Composable
internal fun ObraHeader(title: String, subtitle: String, onBack: (() -> Unit)?, onSettings: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 7.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Green)) { Text("‹ Voltar") }
            Spacer(Modifier.width(8.dp))
        } else {
            Box(Modifier.size(38.dp).background(Green, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                AppIcon(AppSymbol.HOME, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Navy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onSettings != null) {
            TextButton(onClick = onSettings, contentPadding = PaddingValues(horizontal = 6.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Green)) {
                AppIcon(AppSymbol.SETTINGS, Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Dados da obra", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun ObraNavigation(active: Section, onSelect: (Section) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp,
        modifier = Modifier.background(Line).padding(top = 1.dp)) {
        Section.entries.forEach { section ->
            NavigationBarItem(
                selected = section == active,
                onClick = { onSelect(section) },
                icon = { AppIcon(when (section) {
                    Section.DASHBOARD -> AppSymbol.HOME
                    Section.STAGES -> AppSymbol.STAGES
                    Section.PURCHASES -> AppSymbol.PURCHASE
                    Section.PAYMENTS -> AppSymbol.PAYMENT
                    Section.REPORT -> AppSymbol.REPORT
                }) },
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
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
internal fun SheetCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = Panel, border = BorderStroke(1.dp, Line.copy(alpha = 0.8f)),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun Heading(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
internal fun ListHeading(title: String, action: String, onAction: () -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
        Button(onClick = onAction, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)) {
            AppIcon(AppSymbol.PLUS, Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(action.removePrefix("+ "))
        }
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
    openPlanning: () -> Unit,
) {
    val isEmpty = project.stages.isEmpty() && project.purchases.isEmpty() && project.payments.isEmpty()
    val paymentBreakdown = remember(project) { project.paymentBreakdown() }
    ObraList {
        item {
            Card(shape = Panel) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Navy, Color(0xFF174C4B))))
                    .padding(21.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        AppIcon(AppSymbol.LAYERS, Modifier.size(16.dp), Mint)
                        Text("GESTÃO DA OBRA", style = MaterialTheme.typography.labelSmall, color = Mint, letterSpacing = 1.2.sp)
                    }
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
                Metric("Previsão parcial", total.total.brl(), Navy, Modifier.weight(1f), AppSymbol.TREND)
                Metric("Pago efetivo", total.paid.brl(), Green, Modifier.weight(1f), AppSymbol.CHECK)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Saldo conhecido", total.remaining.brl(), Blue, Modifier.weight(1f), AppSymbol.CLOCK)
                Metric("Acréscimos", total.extras.brl(), Amber, Modifier.weight(1f), AppSymbol.PLUS)
            }
        }
        val installments = project.installmentBalances(total)
        if (installments.isNotEmpty()) item {
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconBadge(AppSymbol.PAYMENT, Blue)
                    Column(Modifier.weight(1f)) {
                        Heading("Compras parceladas")
                        Detail("${installments.size} ${if (installments.size == 1) "compra" else "compras"} com pagamento dividido")
                    }
                }
                LedgerLine("Pago efetivo", installments.sumOf { it.paid }.brl())
                val pending = tableAmountTotal(installments.map { it.remaining })
                LedgerLine(if (pending.partial) "A pagar conhecido" else "A pagar", if (pending.entirelyUndefined) "A definir" else pending.known.brl())
                Detail("As parcelas futuras continuam no saldo até registrar o pagamento.")
            }
        }
        item { PlanningDashboardCard(project, openPlanning) }
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
                ProgressBar("Compras de materiais e serviços", total.materialForecast.brl(), total.materialForecast.toDouble() / largest, Blue)
                ProgressBar("Mão de obra", total.laborForecast.brl(), total.laborForecast.toDouble() / largest, Green)
                Detail("Valores a pagar conhecidos: ${total.materialToPay.brl()} em compras e ${total.laborToPay.brl()} em mão de obra.")
            }
        }
        item(key = "payment-chart-payer") { PaymentPieCard("Quem pagou", paymentBreakdown.byPayer, "payer") }
        item(key = "payment-chart-method") { PaymentPieCard("Como foi pago", paymentBreakdown.byMethod, "method") }
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
private fun Metric(label: String, value: String, color: Color, modifier: Modifier, symbol: AppSymbol) {
    SheetCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(symbol, Modifier.size(15.dp), color)
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        Text(value, color = color, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold)
    }
}

internal fun percent(ratio: Double): String = NumberFormat.getPercentInstance(brazil).apply {
    maximumFractionDigits = 1
}.format(ratio)

@Composable
private fun ProgressBar(label: String, value: String, ratio: Double, color: Color) {
    LedgerLine(label, value)
    LinearProgressIndicator(progress = { ratio.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
        color = color, trackColor = Line, gapSize = 0.dp, drawStopIndicator = {})
}

@Composable
internal fun LedgerLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private enum class StageView(val label: String) {
    LIST("Tabela"), GANTT("Gantt"), SPEND("Desembolsos"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StageList(project: Project, total: Balance, today: LocalDate, add: () -> Unit, edit: (String) -> Unit,
    complete: (CostKind, String) -> Unit, changeScale: (GanttScale) -> Unit,
    openCost: (CostKind, String) -> Unit, reviewPaid: (CostKind, String) -> Unit) {
    var view by rememberSaveable { mutableStateOf(StageView.LIST) }
    val chartScroll = rememberScrollState()
    val spend = remember(project, total) { LaborSpendCalendar(project, total) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ListHeading("Etapas da obra", "Nova etapa", add, "Execução e planejamento financeiro")
            ViewTabs(StageView.entries.map { it.label }, view.ordinal) { view = StageView.entries[it] }
        }
        Box(Modifier.weight(1f)) {
            when (view) {
                StageView.LIST -> StageTable(project, total, today, edit, complete, reviewPaid)
                StageView.GANTT -> StageGantt(project.stages, spend, today, chartScroll, edit, project.ganttScale, changeScale)
                StageView.SPEND -> WeeklyDisbursementScreen(project, today, openCost)
            }
        }
    }
}

@Composable
internal fun ExportScreen(busy: Boolean, pdf: () -> Unit, openPdf: (() -> Unit)?, backup: () -> Unit, restore: () -> Unit,
    scale: GanttScale, changeScale: (GanttScale) -> Unit) {
    ObraList {
        item {
            SheetCard {
                IconBadge(AppSymbol.REPORT)
                Heading("Relatório gerencial completo", "Resumo executivo, custos por tipo, próximos vencimentos, cartão e parcelas, gráficos, Gantt, cronograma físico-financeiro, projeções, composição dos custos, aprovações, compras, pagamentos, pendências, agenda e diário.")
                Detail("Escala do cronograma no relatório")
                ViewTabs(GanttScale.entries.map { it.label }, scale.ordinal) { changeScale(GanttScale.entries[it]) }
                Detail("Cabeçalho por ano e mês, com semanas ISO e dias conforme a escala. Em períodos extensos, o PDF espaça os rótulos para manter a leitura.")
                Button(enabled = !busy, onClick = pdf, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    AppIcon(AppSymbol.DOWNLOAD, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Gerar PDF completo")
                }
                if (openPdf != null) TextButton(enabled = !busy, onClick = openPdf) { Text("Abrir último PDF gerado") }
            }
        }
        item {
            SheetCard {
                IconBadge(AppSymbol.LAYERS, Blue)
                Heading("Backup da obra", "Exporte o cadastro com os anexos ou importe um arquivo ZIP ou JSON.")
                OutlinedButton(enabled = !busy, onClick = backup, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    AppIcon(AppSymbol.DOWNLOAD, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Exportar backup com anexos")
                }
                OutlinedButton(enabled = !busy, onClick = restore, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    AppIcon(AppSymbol.UPLOAD, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Importar dados da obra")
                }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}
