package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GanttThreeBarsTest {
    private val today = LocalDate.of(2026, 11, 27)
    private val stage = Stage(name = "Bancada", progress = 60, plannedStart = today.minusDays(7), plannedEnd = today.plusDays(14))
    private val material = Purchase(name = "Pedra", stageId = stage.id, total = 100_000, receivedProgress = 100)
    private val service = Purchase(name = "Fabricação", stageId = stage.id, nature = CostNature.SERVICE, total = 100_000, receivedProgress = 60)
    private val project = Project(stages = listOf(stage), purchases = listOf(material, service))

    @Test fun threeBarsUseOverallLaborAndCombinedMaterialServiceCompletion() {
        val progress = project.stageProgress(stage)
        val bars = project.ganttRows(today, LaborSpendCalendar(project, Balance(project))).single().progressBars(progress)
        assertEquals(listOf(GanttPart.TOTAL, GanttPart.LABOR, GanttPart.INPUTS), bars.map { it.part })
        assertEquals(listOf(70.0, 60.0, 80.0), bars.map { it.percent })
        assertEquals(listOf(0.7f, 0.6f, 0.8f), bars.map { it.fraction })
        assertEquals(1, bars.map { it.period }.distinct().size)
    }

    @Test fun fillFractionsCoverZeroPartialAndCompleteWithoutInventingUnknownProgress() {
        assertEquals(0f, GanttProgressBar(GanttPart.LABOR, 0.0, null).fraction)
        assertEquals(0.25f, GanttProgressBar(GanttPart.LABOR, 25.0, null).fraction)
        assertEquals(1f, GanttProgressBar(GanttPart.LABOR, 100.0, null).fraction)
        val empty = Project(stages = listOf(stage))
        val bars = stage.ganttRow(today).progressBars(empty.stageProgress(stage))
        assertEquals(60.0, bars.first().percent!!, 0.001)
        assertNull(bars.last().percent)
        assertEquals("Sem itens", bars.last().value)
        assertEquals(0f, bars.last().fraction)
    }

    @Test fun serviceCompletionWeightsAndBackupKeepFinancialRecordsUnchanged() {
        val paid = project.copy(payments = listOf(Payment(kind = CostKind.MATERIAL, targetId = service.id, amount = 100_000, date = today)))
        assertEquals(project.stageProgress(stage), paid.stageProgress(stage))
        val completed = paid.copy(purchases = listOf(material, service.copy(receivedProgress = 100)))
        assertEquals(80.0, completed.stageProgress(stage).general, 0.001)
        assertEquals(100.0, completed.stageProgress(stage).materials!!, 0.001)
        assertEquals(paid.payments, completed.payments)
        assertEquals(completed, ProjectCodec.decode(ProjectCodec.encode(completed)))
        assertEquals(68.0, project.stageProgress(stage.copy(materialWeight = 40)).general, 0.001)
    }

    @Test fun weeksFitTheViewportWhileDaysExpandAndLongSchedulesRemainBounded() {
        val axis = requireNotNull(GanttAxis.from(listOf(stage.ganttRow(today))))
        listOf(220f, 540f).forEach { viewport ->
            val weekWidth = GanttTimeline(axis, GanttScale.WEEKS).automaticWidth(viewport)
            val dayWidth = GanttTimeline(axis, GanttScale.DAYS).automaticWidth(viewport)
            assertEquals(viewport, weekWidth, 0.001f)
            assertTrue(dayWidth > weekWidth)
            assertTrue(dayWidth / axis.days >= 31.99f)
        }
        val long = requireNotNull(GanttAxis.from(listOf(stage.copy(plannedEnd = today.plusYears(20)).ganttRow(today))))
        GanttScale.entries.forEach { assertTrue(GanttTimeline(long, it).automaticWidth(220f) <= 24_000f) }
    }

    @Test fun paymentsOutsideTheStageDoNotStretchThePhysicalSchedule() {
        val extended = project.copy(payments = listOf(Payment(targetId = stage.id, amount = 100, date = today.plusYears(1))))
        val rows = extended.ganttRows(today, LaborSpendCalendar(extended, Balance(extended)))
        val axis = requireNotNull(GanttAxis.from(rows))
        assertFalse(today.plusYears(1) in axis.start..axis.end)
        assertTrue(rows.single().spend.any { it.date == today.plusYears(1) })
    }
}
