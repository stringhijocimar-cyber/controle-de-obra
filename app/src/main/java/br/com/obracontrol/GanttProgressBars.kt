package br.com.obracontrol

internal enum class GanttPart(val key: String, val label: String) {
    TOTAL("general", "Total acumulado"), LABOR("execution", "Mão de obra"), INPUTS("materials", "Material e serviço");
}

internal data class GanttProgressBar(val part: GanttPart, val percent: Double?, val period: GanttPeriod?) {
    val fraction get() = ((percent ?: 0.0).coerceIn(0.0, 100.0) / 100.0).toFloat()
    val value get() = percent?.let(::stagePercent) ?: "Sem itens"
}

internal fun GanttRow.progressBars(progress: StageProgress): List<GanttProgressBar> = listOf(
    GanttProgressBar(GanttPart.TOTAL, progress.general, totalPeriod),
    GanttProgressBar(GanttPart.LABOR, progress.execution.toDouble(), totalPeriod),
    GanttProgressBar(GanttPart.INPUTS, progress.materials, totalPeriod),
)
