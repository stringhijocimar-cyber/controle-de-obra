package br.com.obracontrol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

internal val MaterialArrival = Color(0xFF7B4BA3)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StageGantt(
    project: Project, spend: LaborSpendCalendar, today: LocalDate, scroll: ScrollState, edit: (String) -> Unit,
    scale: GanttScale, changeScale: (GanttScale) -> Unit,
) {
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var zoomName by rememberSaveable { mutableStateOf(GanttZoom.NORMAL.name) }
    var zoomAnchor by remember { mutableStateOf<Float?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }
    var selectedStage by rememberSaveable { mutableStateOf<String?>(null) }
    val detailRows = remember(project, spend, today) { project.ganttRows(today, spend) }
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("gantt-scale-selector"),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (fullScreen) CompactChartTabs(GanttScale.entries.map { it.label }, scale.ordinal) { changeScale(GanttScale.entries[it]) }
                    else ViewTabs(GanttScale.entries.map { it.label }, scale.ordinal) { changeScale(GanttScale.entries[it]) }
                }
                if (!fullScreen) TextButton(onClick = { fullScreen = true }, modifier = Modifier.testTag("gantt-full-screen")) { Text("Tela cheia") }
            }
            if (!fullScreen) Text("Toque nas barras para consultar datas e entregas. No nome da etapa, você pode editar.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
            if (!fullScreen && project.unlinkedMaterials > 0) Text("${project.unlinkedMaterials} compra(s) de material sem etapa vinculada.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = Amber)
            Box(Modifier.weight(1f)) {
                StageGanttChart(project, spend, today, scroll, { fullScreen = false; edit(it) }, scale, listState,
                    GanttZoom.valueOf(zoomName), zoomAnchor, { next, anchor -> zoomAnchor = anchor; zoomName = next.name },
                    { zoomAnchor = null }, { help = true }, { selectedStage = it })
            }
        }
        if (help) GanttHelp { help = false }
        selectedStage?.let { id -> detailRows.firstOrNull { it.stage.id == id }?.let { row ->
            GanttStageDetails(row, project.stageProgress(row.stage), today, { selectedStage = null }, {
                selectedStage = null; fullScreen = false; edit(id)
            })
        } }
    }
    if (fullScreen) FullScreenChart("Gantt · evolução por etapa", { fullScreen = false }, content) else content()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageGanttChart(
    project: Project, spend: LaborSpendCalendar, today: LocalDate, scroll: ScrollState, edit: (String) -> Unit, scale: GanttScale, listState: LazyListState,
    zoom: GanttZoom, zoomAnchor: Float?, changeZoom: (GanttZoom, Float) -> Unit, appliedZoom: () -> Unit,
    help: () -> Unit, inspect: (String) -> Unit,
) {
    val stages = project.stages
    val evolution = remember(project) { stages.associate { it.id to project.stageProgress(it) } }
    val rows = remember(project, spend, today) { project.ganttRows(today, spend) }
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
        val labelWidth = (maxWidth * 0.31f).coerceIn(124.dp, 172.dp)
        val viewport = (maxWidth - labelWidth).value
        val chartWidth = zoom.width(viewport, timeline.preferredWidth).dp
        val rowHeight = maxOf(184.dp, (184 * density.fontScale).dp)
        LaunchedEffect(zoom, zoomAnchor, chartWidth) {
            zoomAnchor?.let { anchor ->
                withFrameNanos { }
                scroll.scrollTo((with(density) { chartWidth.toPx() } * anchor).roundToInt().coerceIn(0, scroll.maxValue))
                appliedZoom()
            }
        }
        fun chooseZoom(next: GanttZoom) {
            val anchor = if (next == GanttZoom.FIT) 0f else scroll.value / with(density) { chartWidth.toPx() }
            changeZoom(next, anchor)
        }
        val headerHeight = maxOf(18.dp, (18 * density.fontScale).dp) * timeline.rowCount
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TextButton(onClick = { chooseZoom(GanttZoom.FIT) }, modifier = Modifier.height(32.dp).testTag("gantt-fit"), contentPadding = PaddingValues(5.dp)) { Text("Ajustar") }
                val smaller = zoom.next(viewport, timeline.preferredWidth, false)
                TextButton(enabled = smaller != null, onClick = { smaller?.let(::chooseZoom) }, modifier = Modifier.size(32.dp).testTag("gantt-zoom-out"), contentPadding = PaddingValues(0.dp)) { Text("−") }
                Text(zoom.label, Modifier.testTag("gantt-zoom-label"), style = MaterialTheme.typography.labelSmall, color = Muted)
                val larger = zoom.next(viewport, timeline.preferredWidth, true)
                TextButton(enabled = larger != null, onClick = { larger?.let(::chooseZoom) }, modifier = Modifier.size(32.dp).testTag("gantt-zoom-in"), contentPadding = PaddingValues(0.dp)) { Text("+") }
                if (marker != null) TextButton(onClick = {
                    val center = with(density) { (chartWidth.toPx() * axis.dayCenter(marker)).roundToInt() }
                    scope.launch { scroll.animateScrollTo((center - scroll.viewportSize / 2).coerceIn(0, scroll.maxValue)) }
                }, modifier = Modifier.height(32.dp).testTag("gantt-today"), contentPadding = PaddingValues(5.dp)) { Text("Ir para hoje", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = help, modifier = Modifier.height(32.dp).testTag("gantt-help"), contentPadding = PaddingValues(5.dp)) { Text("Como ler", style = MaterialTheme.typography.labelSmall) }
            }
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
                    LazyColumn(Modifier.fillMaxSize().testTag("gantt-stage-list"), state = listState) {
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
                                    Text(row.durationText(today), color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                                    StageBars(timeline, today, row, chartWidth, rowHeight, scroll) { inspect(stage.id) }
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
private fun StageBars(timeline: GanttTimeline, asOf: LocalDate, row: GanttRow, width: Dp, height: Dp, scroll: ScrollState, inspect: () -> Unit) {
    val axis = timeline.axis
    val labelSize = with(LocalDensity.current) { 9.sp.toPx() }
    val labelPaint = remember(labelSize) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = labelSize; color = Muted.toArgb(); typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    } }
    val description = buildString {
        append("Previsto ${row.plannedDates} · Realizado ${row.actualDates}")
        append(" · Prazo total ${row.totalPeriod?.dates() ?: UNDEFINED} · ${row.durationText(asOf)}")
        row.deliveries.forEach { item -> append(" · Material ${item.name}: previsto ${item.expected.dateOr()}, recebido ${item.received.dateOr()}, ${item.progress}%") }
        append(" · Fim da execução previsto ${row.stage.plannedEnd.dateOr()}, real ${row.stage.actualEnd.dateOr()}")
        row.spend.forEach { item ->
            append(" · ${item.status.label} ${item.amount.costText()} em ${item.date.dateOr("data a definir")}")
        }
    }
    Canvas(Modifier.size(width, height).testTag("gantt-bars-${row.stage.id}").clickable(onClickLabel = "Ver datas e entregas da etapa", onClick = inspect).semantics { contentDescription = description }) {
        val lane = size.height / 4f
        repeat(4) { index ->
            drawRect(if (index % 2 == 0) Color(0xFFFAFCFE) else Color.White,
                Offset(0f, index * lane), Size(size.width, lane))
            if (index > 0) drawLine(Line.copy(alpha = 0.65f), Offset(0f, index * lane), Offset(size.width, index * lane), 0.5.dp.toPx())
        }
        for (tick in timeline.gridDates(width.value)) {
            val x = size.width * axis.boundary(tick)
            drawLine(Line, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
        }
        row.totalPeriod?.let { total ->
            drawPeriod(total, axis, lane * 0.50f, Blue.copy(alpha = 0.18f))
            if (asOf >= total.start) drawPeriod(GanttPeriod(total.start, minOf(asOf, total.end)), axis, lane * 0.50f, Blue)
        }
        row.planned?.let { drawPeriod(it, axis, lane * 1.50f, Green.copy(alpha = 0.48f), planned = true) }
        row.actual?.let { drawPeriod(it, axis, lane * 1.50f, Green) }
        asOf.takeIf { it in axis.start..axis.end }?.let {
            val x = size.width * axis.dayCenter(it)
            drawLine(Muted, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
        }
        row.materialPeriod?.let { drawPeriod(it, axis, lane * 2.50f, MaterialArrival.copy(alpha = 0.22f)) }
        row.deliveries.forEach { item ->
            item.expected?.let { drawMilestone(it, axis, lane * 2.50f + 6.dp.toPx(), MaterialArrival, false) }
            item.received?.let { drawMilestone(it, axis, lane * 2.50f + 6.dp.toPx(), MaterialArrival, true) }
        }
        row.stage.plannedEnd?.let { drawMilestone(it, axis, lane * 1.50f + 6.dp.toPx(), Green, false) }
        row.stage.actualEnd?.let { drawMilestone(it, axis, lane * 1.50f + 6.dp.toPx(), Green, true) }
        row.spend.forEach { item ->
            item.date?.let { date ->
                val x = size.width * axis.dayCenter(date)
                val y = lane * 3.50f + if (item.paid) 0.dp.toPx() else 8.dp.toPx()
                drawCircle(if (item.paid) Navy else Amber, 4.dp.toPx(), Offset(x, y))
            }
        }
        listOf("Tempo decorrido / prazo total", "Execução prevista / realizada", "Entregas de materiais", "Pagamentos · pago / a pagar").forEachIndexed { index, label ->
            val width = (scroll.viewportSize - 10.dp.toPx()).coerceAtLeast(0f)
            val count = labelPaint.breakText(label, true, width, null)
            val visibleLabel = if (count >= label.length) label else label.take((count - 1).coerceAtLeast(0)) + "…"
            drawContext.canvas.nativeCanvas.drawText(visibleLabel, scroll.value + 5.dp.toPx(), index * lane + maxOf(labelSize * 1.35f, 13.dp.toPx()), labelPaint)
        }
    }
}

private fun DrawScope.drawPeriod(period: GanttPeriod, axis: GanttAxis, y: Float, color: Color, planned: Boolean = false) {
    val minWidth = 2.dp.toPx()
    val left = (size.width * axis.boundary(period.start)).coerceIn(0f, (size.width - minWidth).coerceAtLeast(0f))
    val right = (size.width * axis.boundary(period.end.plusDays(1))).coerceAtMost(size.width)
    val width = (right - left).coerceAtLeast(minWidth)
    val corner = 4.dp.toPx()
    drawRoundRect(color, Offset(left, y), Size(width, 12.dp.toPx()), CornerRadius(corner),
        style = if (planned) Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))) else androidx.compose.ui.graphics.drawscope.Fill)
}

private fun DrawScope.drawMilestone(date: LocalDate, axis: GanttAxis, y: Float, color: Color, real: Boolean) {
    val x = size.width * axis.dayCenter(date)
    val radius = 4.5.dp.toPx()
    val path = Path().apply { moveTo(x, y - radius); lineTo(x + radius, y); lineTo(x, y + radius); lineTo(x - radius, y); close() }
    drawPath(path, if (real) color else Color.White)
    if (!real) drawPath(path, color, style = Stroke(1.5.dp.toPx()))
}
