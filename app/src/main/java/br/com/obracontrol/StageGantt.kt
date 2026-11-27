package br.com.obracontrol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val shortTick = DateTimeFormatter.ofPattern("dd/MM", brazil)
private val yearTick = DateTimeFormatter.ofPattern("dd/MM/yy", brazil)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StageGantt(
    project: Project, spend: LaborSpendCalendar, today: LocalDate, scroll: ScrollState, edit: (String) -> Unit,
    scale: GanttScale, changeScale: (GanttScale) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("gantt-scale-selector")) {
            ViewTabs(GanttScale.entries.map { it.label }, scale.ordinal) { changeScale(GanttScale.entries[it]) }
        }
        Text("Geral combina execução e materiais recebidos. Ajuste os pesos ao editar a etapa.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
        if (project.unlinkedMaterials > 0) Text("${project.unlinkedMaterials} compra(s) de material sem etapa vinculada.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = Amber)
        Box(Modifier.weight(1f)) { StageGanttChart(project, spend, today, scroll, edit, scale) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageGanttChart(
    project: Project, spend: LaborSpendCalendar, today: LocalDate, scroll: ScrollState, edit: (String) -> Unit, scale: GanttScale,
) {
    val stages = project.stages
    val evolution = remember(project) { stages.associate { it.id to project.stageProgress(it) } }
    val rows = remember(stages, spend, today) { stages.map { it.ganttRow(today, spend.byStage[it.id].orEmpty()) } }
    val axis = remember(rows) { GanttAxis.from(rows) }
    if (axis == null) {
        ObraList {
            item {
                SheetCard {
                    Detail(if (stages.isEmpty()) "Cadastre a primeira etapa para montar o cronograma."
                        else "Informe as datas da execução ou do pagamento para montar o Gantt.")
                }
            }
            itemsIndexed(rows, key = { _, row -> row.stage.id }) { _, row ->
                SheetCard {
                    Heading(row.stage.name, "Datas a definir")
                    StageProgressReadout(row.stage.id, evolution.getValue(row.stage.id))
                    LedgerLine("Mão de obra", row.stage.budget.costText())
                    TextButton(onClick = { edit(row.stage.id) }) { Text("Editar etapa") }
                }
            }
        }
        return
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val timeline = remember(axis, scale) { GanttTimeline(axis, scale) }
    val marker = today.takeIf { it in axis.start..axis.end }
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        val labelWidth = (maxWidth * 0.39f).coerceIn(105.dp, 188.dp)
        val chartWidth = maxOf(maxWidth - labelWidth, timeline.preferredWidth.dp)
        val rowHeight = maxOf(154.dp, (154 * density.fontScale).dp)
        val headerHeight = maxOf(24.dp, (24 * density.fontScale).dp) * timeline.rowCount
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GanttLegend("Previsto", Blue)
                GanttLegend("Realizado", Green)
                GanttLegend("Pago", Navy, day = true)
                GanttLegend("A pagar", Amber, day = true)
                if (marker != null) GanttLegend("Hoje", Muted)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${axis.start.dateText()} a ${axis.end.dateText()}", Modifier.weight(1f),
                    color = Muted, style = MaterialTheme.typography.bodySmall)
                if (marker != null) {
                    TextButton(onClick = {
                        val center = with(density) { (chartWidth.toPx() * axis.dayCenter(marker)).roundToInt() }
                        scope.launch { scroll.animateScrollTo((center - scroll.viewportSize / 2).coerceIn(0, scroll.maxValue)) }
                    }) { Text("Ir para hoje") }
                }
            }
            if (scale != GanttScale.MONTHS) Text("Semanas ISO: segunda a domingo; a virada do ano mantém o ano da semana.",
                color = Muted, style = MaterialTheme.typography.labelSmall)
            if (timeline.condensed) Detail("Período extenso: algumas datas intermediárias ficam ocultas para manter a leitura.")
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("gantt-table"),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(headerHeight).background(Navy), verticalAlignment = Alignment.CenterVertically) {
                        Text("Etapa / evolução", Modifier.width(labelWidth).padding(start = 10.dp), color = Color.White,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                            DateAxis(timeline, chartWidth, headerHeight, scroll)
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize().testTag("gantt-stage-list")) {
                        itemsIndexed(rows, key = { _, row -> row.stage.id }) { index, row ->
                            val stage = row.stage
                            val overdue = stage.progress < 100 && stage.plannedEnd?.let { it < today } == true
                            Row(
                                Modifier.fillMaxWidth().height(rowHeight)
                                    .background(if (index % 2 == 0) Color.White else Mist)
                                    .clickable(onClickLabel = "Editar etapa") { edit(stage.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.width(labelWidth).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(stage.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val status = when {
                                        overdue -> "Atrasada"
                                        row.planned == null && row.actual == null -> "Datas a definir"
                                        else -> stage.status
                                    }
                                    Text(status, color = if (overdue) Amber else Muted,
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    StageProgressReadout(stage.id, evolution.getValue(stage.id))
                                    Text(stage.budget.costText(), color = Navy, style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                                    StageBars(timeline, marker, row, chartWidth, rowHeight)
                                }
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageProgressReadout(id: String, progress: StageProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(Triple("Execução", stagePercent(progress.execution.toDouble()), Green),
            Triple("Materiais", if (progress.materials == null) "—" else progress.materialsText, Blue),
            Triple("Geral", progress.generalText, Navy)).forEachIndexed { index, (label, value, color) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(value, Modifier.testTag("gantt-${listOf("execution", "materials", "general")[index]}-$id"),
                    color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        LinearProgressIndicator(progress = { (progress.general / 100).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp), color = Green, trackColor = Line,
            gapSize = 0.dp, drawStopIndicator = {})
    }
}

@Composable
private fun GanttLegend(label: String, color: Color, day: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = if (day) 7.dp else 12.dp, height = 7.dp)
            .background(color, if (day) CircleShape else RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

@Composable
private fun DateAxis(timeline: GanttTimeline, width: Dp, height: Dp, scroll: ScrollState) {
    val visibleStart = with(LocalDensity.current) { scroll.value.toDp() }
    val rowHeight = height / timeline.rowCount
    Column(Modifier.size(width, height).testTag("gantt-calendar-header")) {
        repeat(timeline.rowCount) { index ->
            val bands = timeline.bands(index, width.value)
            Box(Modifier.width(width).height(rowHeight).background(if (index % 2 == 0) Navy else Color(0xFF254655))) {
                Canvas(Modifier.matchParentSize()) {
                    for (band in bands) {
                        val x = size.width * timeline.axis.boundary(band.start)
                        drawLine(Color.White.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
                    }
                }
                bands.forEach { band ->
                    val left = width * timeline.axis.boundary(band.start)
                    val bandWidth = width * timeline.fraction(band)
                    if (bandWidth >= 18.dp) {
                        val labelWidth = minOf(bandWidth - 4.dp, if (index == 0) 95.dp else if (index == 1) 85.dp else 90.dp)
                        val x = maxOf(left + 2.dp, visibleStart + 2.dp).coerceAtMost(left + bandWidth - labelWidth)
                        Text(band.label, Modifier.align(Alignment.CenterStart).offset(x = x).width(labelWidth)
                            .testTag("gantt-${band.key}").semantics { contentDescription = band.description },
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StageBars(timeline: GanttTimeline, marker: LocalDate?, row: GanttRow, width: Dp, height: Dp) {
    val axis = timeline.axis
    val description = buildString {
        append("Previsto ${row.plannedDates} · Realizado ${row.actualDates}")
        row.spend.forEach { item ->
            append(" · ${item.status.label} ${item.amount.costText()} em ${item.date.dateOr("data a definir")}")
        }
    }
    Canvas(Modifier.size(width, height).semantics { contentDescription = description }) {
        for (tick in timeline.gridDates(width.value)) {
            val x = size.width * axis.boundary(tick)
            drawLine(Line, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
        }
        row.planned?.let { drawPeriod(it, axis, size.height / 2 - 26.dp.toPx(), Blue) }
        row.actual?.let { drawPeriod(it, axis, size.height / 2 - 8.dp.toPx(), Green) }
        marker?.let {
            val x = size.width * axis.dayCenter(it)
            drawLine(Muted, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
        }
        row.spend.forEach { item ->
            item.date?.let { date ->
                val x = size.width * axis.dayCenter(date)
                val y = size.height / 2 + if (item.paid) 14.dp.toPx() else 26.dp.toPx()
                drawCircle(if (item.paid) Navy else Amber, 4.dp.toPx(), Offset(x, y))
            }
        }
    }
}

private fun DrawScope.drawPeriod(period: GanttPeriod, axis: GanttAxis, y: Float, color: Color) {
    val minWidth = 2.dp.toPx()
    val left = (size.width * axis.boundary(period.start)).coerceIn(0f, (size.width - minWidth).coerceAtLeast(0f))
    val right = (size.width * axis.boundary(period.end.plusDays(1))).coerceAtMost(size.width)
    val width = (right - left).coerceAtLeast(minWidth)
    val corner = 4.dp.toPx()
    drawRoundRect(color, Offset(left, y), Size(width, 12.dp.toPx()), CornerRadius(corner))
}
