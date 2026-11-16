package br.com.obracontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class GanttScheduleTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test
    fun sharesActualAndPlannedPeriodsAndRespectsReportDateForOngoingWork() {
        val start = today.minusDays(3)
        val stage = Stage(name = "Cobertura", plannedStart = start.minusDays(2), plannedEnd = today.plusDays(7),
            actualStart = start, progress = 35)

        val row = stage.ganttRow(today)
        assertEquals(GanttPeriod(start.minusDays(2), today.plusDays(7)), row.planned)
        assertEquals(GanttPeriod(start, today), row.actual)
        assertEquals("13/11/2026 · em curso", row.actualDates)
        assertEquals(GanttPeriod(start, start), stage.ganttRow(start).actual)
        assertNull(stage.ganttRow(start.minusDays(1)).actual)
    }

    @Test
    fun representsSingleDayAndLeavesUnknownDatesOpen() {
        val completed = Stage(name = "Muro", actualStart = today.minusDays(6), progress = 100)
        assertEquals(GanttPeriod(today.minusDays(6), today.minusDays(6)), completed.ganttRow(today).actual)
        assertEquals("10/11/2026 · fim a confirmar", completed.ganttRow(today).actualDates)
        assertEquals("16/11/2026", Stage(name = "Vistoria", actualEnd = today, progress = 100).ganttRow(today).actualDates)
        assertNull(GanttAxis.from(listOf(Stage(name = "Pintura", plannedStart = today).ganttRow(today))))

        val row = Stage(name = "Entrega", plannedStart = today, plannedEnd = today).ganttRow(today)
        val axis = requireNotNull(GanttAxis.from(listOf(row)))
        assertEquals(7L, axis.days)
        assertEquals(0f, axis.boundary(today), 0.0001f)
        assertEquals(1f / 7f, axis.boundary(today.plusDays(1)), 0.0001f)
        assertEquals(1f / 14f, axis.dayCenter(today), 0.0001f)
    }

    @Test
    fun longerSchedulesKeepReadableTicksAndIncludeAllPlannedDays() {
        val end = today.plusYears(100)
        val row = Stage(name = "Obra", plannedStart = today.plusDays(2), plannedEnd = end).ganttRow(today)
        val axis = requireNotNull(GanttAxis.from(listOf(row)))
        assertEquals(DayOfWeek.MONDAY, axis.start.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, axis.end.dayOfWeek)
        assertTrue(axis.ticks.size <= 28)
        assertTrue(axis.preferredWidth < 2_000)
        assertTrue(axis.includeTickYear)
        assertTrue(axis.boundary(end.plusDays(1)) <= 1f)
    }
}
