package br.com.obracontrol

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GanttTimelineTest {
    private fun axis(start: String, end: String) = requireNotNull(GanttAxis.from(listOf(Stage(name = "Teste",
        plannedStart = LocalDate.parse(start), plannedEnd = LocalDate.parse(end)).ganttRow(LocalDate.of(2026, 11, 25)))))

    @Test fun headerRollsUpCivilYearsMonthsAndIsoWeeksAtNewYearWithoutLosingDays() {
        val timeline = GanttTimeline(axis("2026-12-28", "2027-01-08"), GanttScale.WEEKS)
        assertEquals(listOf("2026", "2027"), timeline.years.map { it.label })
        assertEquals(listOf("month-2026-12", "month-2027-1"), timeline.months.map { it.key })
        assertEquals(listOf("week-2026-53", "week-2027-1"), timeline.weeks.map { it.key })
        assertEquals("S53 / 2026", timeline.weeks.first().label)
        assertTrue(timeline.weeks.first().description.contains("28/12/2026 a 03/01/2027"))
        listOf(timeline.years, timeline.months, timeline.weeks).forEach { bands ->
            assertEquals(timeline.axis.start, bands.first().start)
            assertEquals(timeline.axis.end, bands.last().end)
            assertEquals(1f, bands.sumOf { timeline.fraction(it).toDouble() }.toFloat(), 0.00001f)
            assertTrue(bands.zipWithNext().all { (a, b) -> a.end.plusDays(1) == b.start })
        }
    }

    @Test fun firstDaysOfJanuaryCanBelongToThePreviousIsoYear() {
        val timeline = GanttTimeline(axis("2021-01-01", "2021-01-07"), GanttScale.DAYS)
        assertEquals("week-2020-53", timeline.weeks.first().key)
        assertEquals("S53 / 2020", timeline.weeks.first().label)
        assertEquals("2021", timeline.years.single().label)
    }

    @Test fun dailyHeaderIncludesLeapDayAndAllScalesShareTheSameBarCoordinates() {
        val range = axis("2024-02-27", "2024-03-04")
        val days = GanttTimeline(range, GanttScale.DAYS)
        assertTrue(days.bands(3).any { it.key == "day-2024-02-29" && it.label == "29" })
        assertEquals(4, days.rowCount)
        assertEquals(3, GanttTimeline(range, GanttScale.WEEKS).rowCount)
        assertEquals(2, GanttTimeline(range, GanttScale.MONTHS).rowCount)
        GanttScale.entries.forEach { scale ->
            assertEquals(range.dayCenter(LocalDate.of(2024, 2, 29)), GanttTimeline(range, scale).axis.dayCenter(LocalDate.of(2024, 2, 29)))
        }
        val longer = axis("2026-11-01", "2027-02-01")
        assertTrue(GanttTimeline(longer, GanttScale.DAYS).preferredWidth > GanttTimeline(longer, GanttScale.WEEKS).preferredWidth)
        assertTrue(GanttTimeline(longer, GanttScale.WEEKS).preferredWidth > GanttTimeline(longer, GanttScale.MONTHS).preferredWidth)
    }

    @Test fun centuryLongRangesKeepCanvasAndLabelCountsBounded() {
        val axis = axis("2000-01-01", "2100-01-01")
        GanttScale.entries.forEach { scale ->
            val timeline = GanttTimeline(axis, scale)
            assertTrue(timeline.preferredWidth <= 24_000)
            assertTrue(timeline.gridDates().size < 1_000)
            assertTrue(timeline.condensed)
        }
    }

    @Test fun displayPreferenceSurvivesBackupAndOlderDataDefaultsToWeeksWithoutChangingMoney() {
        val stage = Stage(name = "Existente", budget = 12_000)
        val project = Project(stages = listOf(stage), payments = listOf(Payment(targetId = stage.id, amount = 3_000)))
        val old = JSONObject(ProjectCodec.encode(project)).put("version", 9).apply { remove("ganttScale") }
        assertEquals(project, ProjectCodec.decode(old.toString()))
        val selected = project.copy(ganttScale = GanttScale.MONTHS)
        assertEquals(selected, ProjectCodec.decode(ProjectCodec.encode(selected)))
        assertEquals(project, project.mergeFrom(selected).project)
    }
}
