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
    var zoomName by rememberSaveable { mutableStateOf(GanttZoom.AUTO.name) }
    var zoomAnchor by remember { mutableStateOf<Float?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }
    var selectedStage by rememberSaveable { mutableStateOf<String?>(null) }
    val detailRows = remember(project, spend, today) { project.ganttRows(today, spend) }
    fun selectScale(next: GanttScale) { zoomName = GanttZoom.AUTO.name; zoomAnchor = 0f; changeScale(next) }
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("gantt-scale-selector"),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (fullScreen) CompactChartTabs(GanttScale.entries.map { it.label }, scale.ordinal) { selectScale(GanttScale.entries[it]) }
                    else ViewTabs(GanttScale.entries.map { it.label }, scale.ordinal) { selectScale(GanttScale.entries[it]) }
                }
                if (!fullScreen) TextButton(onClick = { fullScreen = true }, modifier = Modifier.testTag("gantt-full-screen")) { Text("Tela cheia") }
            }
            if (!fullScreen) Text("Sólido: concluído. Suave: restante. Toque nas barras para consultar a etapa.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
            if (!fullScreen && project.unlinkedMaterials > 0) Text("${project.unlinkedMaterials} material(is)/serviço(s) sem etapa vinculada.",
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
                        else "Informe as datas da etapa ou das entregas para montar a linha do tempo.")
                }
            }
            itemsIndexed(rows, key = { _, row -> row.stage.id }) { _, row ->
                SheetCard {
                    Heading(row.stage.name, "Datas a definir")
                    StageProgressReadout(row.stage.id, evolution.getValue(row.stage.id), bars = true)
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
        val automatic = timeline.automaticWidth(viewport)
        val chartWidth = zoom.width(viewport, timeline.preferredWidth, automatic).dp
        val compactHeight = when (scale) { GanttScale.DAYS -> 168f; GanttScale.WEEKS -> 144f; GanttScale.MONTHS -> 136f }
        val rowHeight = maxOf(compactHeight.dp, (compactHeight * density.fontScale).dp)
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
                val smaller = zoom.next(viewport, timeline.preferredWidth, false, automatic)
                TextButton(enabled = smaller != null, onClick = { smaller?.let(::chooseZoom) }, modifier = Modifier.size(32.dp).testTag("gantt-zoom-out"), contentPadding = PaddingValues(0.dp)) { Text("−") }
                Text(zoom.label, Modifier.testTag("gantt-zoom-label"), style = MaterialTheme.typography.labelSmall, color = Muted)
                val larger = zoom.next(viewport, timeline.preferredWidth, true, automatic)
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
                                    .background(if (index % 2 == 0) Color.White else Mist),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.width(labelWidth).padding(horizontal = 10.dp).clickable(onClickLabel = "Editar etapa") { edit(stage.id) }.testTag("gantt-edit-${stage.id}"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(stage.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                        maxLines = if (scale == GanttScale.DAYS) 2 else 1, overflow = TextOverflow.Ellipsis)
                                    val status = when {
                                        overdue -> "Atrasada"
                                        row.planned == null && row.actual == null -> "Datas a definir"
                                        else -> stage.status
                                    }
                                    Text(status, color = if (overdue) Amber else Muted,
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    StageProgressReadout(stage.id, evolution.getValue(stage.id))
                                    Text(if (row.totalPeriod == null) "Datas a definir" else "Prazo: ${row.totalDays} dias", color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                                    StageBars(timeline, today, row, evolution.getValue(stage.id), chartWidth, rowHeight, scroll) { inspect(stage.id) }
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
private fun StageProgressReadout(id: String, progress: StageProgress, bars: Boolean = false) {
    val items = listOf(
        Triple("Total", progress.general, Blue) to "general",
        Triple("Mão de obra", progress.execution.toDouble(), Green) to "execution",
        Triple("Mat./serv.", progress.materials, MaterialArrival) to "materials")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { (item, key) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.first, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.second?.let(::stagePercent) ?: "—", Modifier.testTag("gantt-$key-$id"),
                    color = item.third, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            if (bars) LinearProgressIndicator(progress = { ((item.second ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).testTag("gantt-meter-$key-$id"), color = item.third,
                trackColor = item.third.copy(alpha = 0.18f), gapSize = 0.dp, drawStopIndicator = {})
        }
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
private fun StageBars(timeline: GanttTimeline, asOf: LocalDate, row: GanttRow, progress: StageProgress,
    width: Dp, height: Dp, scroll: ScrollState, inspect: () -> Unit) {
    val axis = timeline.axis
    val bars = row.progressBars(progress)
    val labelSize = with(LocalDensity.current) { 10.sp.toPx() }
    val labelPaint = remember(labelSize) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = labelSize; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    } }
    val description = buildString {
        append("Previsto ${row.plannedDates} · Realizado ${row.actualDates}")
        bars.forEach { append(" · ${it.part.label}: ${it.value}") }
        row.deliveries.forEach { item -> append(" · ${item.nature.label} ${item.name}: previsto ${item.expected.dateOr()}, recebido ${item.received.dateOr()}, ${item.progress}%") }
        append(" · Fim da execução previsto ${row.stage.plannedEnd.dateOr()}, real ${row.stage.actualEnd.dateOr()}")
        row.spend.forEach { append(" · ${it.status.label} ${it.amount.costText()} em ${it.date.dateOr()}") }
    }
    Canvas(Modifier.size(width, height).testTag("gantt-bars-${row.stage.id}")
        .clickable(onClickLabel = "Ver datas e avanço da etapa", onClick = inspect).semantics { contentDescription = description }) {
        val lane = size.height / 3f
        repeat(3) { index ->
            drawRect(if (index % 2 == 0) Color(0xFFFAFCFE) else Color.White,
                Offset(0f, index * lane), Size(size.width, lane))
            if (index > 0) drawLine(Line.copy(alpha = 0.65f), Offset(0f, index * lane), Offset(size.width, index * lane), 0.5.dp.toPx())
        }
        timeline.gridDates(width.value).forEach { date ->
            val x = size.width * axis.boundary(date)
            drawLine(Line, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
        }
        bars.forEachIndexed { index, bar ->
            val color = when (bar.part) { GanttPart.TOTAL -> Blue; GanttPart.LABOR -> Green; GanttPart.INPUTS -> MaterialArrival }
            val top = lane * index + lane * 0.53f
            val barHeight = 14.dp.toPx()
            bar.period?.let { period ->
                val left = size.width * axis.boundary(period.start)
                val right = size.width * axis.boundary(period.end.plusDays(1))
                val totalWidth = (right - left).coerceAtLeast(1.dp.toPx())
                drawRoundRect(color.copy(alpha = 0.18f), Offset(left, top), Size(totalWidth, barHeight), CornerRadius(4.dp.toPx()))
                if (bar.fraction > 0f) drawRoundRect(color, Offset(left, top), Size(totalWidth * bar.fraction, barHeight), CornerRadius(4.dp.toPx()))
            }
            labelPaint.color = color.toArgb()
            val label = "${bar.part.label} · ${bar.value}"
            val maxWidth = (scroll.viewportSize - 10.dp.toPx()).coerceAtLeast(0f)
            val count = labelPaint.breakText(label, true, maxWidth, null)
            val visibleLabel = if (count >= label.length) label else label.take((count - 1).coerceAtLeast(0)) + "…"
            drawContext.canvas.nativeCanvas.drawText(visibleLabel, scroll.value + 5.dp.toPx(), index * lane + maxOf(labelSize * 1.35f, 15.dp.toPx()), labelPaint)
        }
        asOf.takeIf { it in axis.start..axis.end }?.let {
            val x = size.width * axis.dayCenter(it)
            drawLine(Muted, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
        }
    }
}
