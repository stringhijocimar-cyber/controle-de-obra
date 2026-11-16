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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
internal fun StageGantt(stages: List<Stage>, today: LocalDate, scroll: ScrollState, edit: (String) -> Unit) {
    val rows = remember(stages, today) { stages.map { it.ganttRow(today) } }
    val axis = remember(rows) { GanttAxis.from(rows) }
    if (axis == null) {
        ObraList {
            item { SheetCard { Detail("Informe o início e o fim previstos ou registre datas reais para montar o Gantt.") } }
            itemsIndexed(rows, key = { _, row -> row.stage.id }) { _, row ->
                SheetCard {
                    Heading(row.stage.name, "Datas a definir")
                    TextButton(onClick = { edit(row.stage.id) }) { Text("Editar etapa") }
                }
            }
        }
        return
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val marker = today.takeIf { it in axis.start..axis.end }
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        val labelWidth = (maxWidth * 0.39f).coerceIn(105.dp, 188.dp)
        val chartWidth = maxOf(maxWidth - labelWidth, axis.preferredWidth.dp)
        val rowHeight = maxOf(68.dp, (60 * density.fontScale).dp)
        val headerHeight = maxOf(33.dp, (29 * density.fontScale).dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GanttLegend("Previsto", Blue)
                GanttLegend("Realizado", Green)
                if (marker != null) GanttLegend("Data de hoje", Amber)
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
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(headerHeight).background(Navy), verticalAlignment = Alignment.CenterVertically) {
                        Text("Etapa", Modifier.width(labelWidth).padding(start = 10.dp), color = Color.White,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                            DateAxis(axis, chartWidth, headerHeight)
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
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
                                    Text("$status · ${stage.progress}%", color = if (overdue) Amber else Muted,
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                                    StageBars(axis, marker, row, chartWidth, rowHeight)
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
private fun GanttLegend(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 12.dp, height = 7.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

@Composable
private fun DateAxis(axis: GanttAxis, width: Dp, height: Dp) {
    val format = if (axis.includeTickYear) yearTick else shortTick
    Box(Modifier.size(width, height)) {
        Canvas(Modifier.matchParentSize()) {
            for (tick in axis.ticks) {
                val x = size.width * axis.boundary(tick)
                drawLine(Muted, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
            }
        }
        for (tick in axis.ticks) {
            val labelWidth = if (axis.includeTickYear) 55.dp else 36.dp
            val x = (width * axis.boundary(tick) + 4.dp).coerceAtMost(width - labelWidth)
            Text(tick.format(format), Modifier.align(Alignment.CenterStart).offset(x = x),
                maxLines = 1, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StageBars(axis: GanttAxis, marker: LocalDate?, row: GanttRow, width: Dp, height: Dp) {
    val description = "Previsto ${row.plannedDates} · Realizado ${row.actualDates}"
    Canvas(Modifier.size(width, height).semantics { contentDescription = description }) {
        for (tick in axis.ticks) {
            val x = size.width * axis.boundary(tick)
            drawLine(Line, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
        }
        row.planned?.let { drawPeriod(it, axis, size.height / 2 - 15.dp.toPx(), Blue) }
        row.actual?.let { drawPeriod(it, axis, size.height / 2 + 3.dp.toPx(), Green) }
        marker?.let {
            val x = size.width * axis.dayCenter(it)
            drawLine(Amber, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
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
