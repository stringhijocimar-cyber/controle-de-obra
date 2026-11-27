package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GanttZoomTest {
    @Test fun fitShowsTheWholeRangeAndZoomStepsAreStrictlyMonotonicInEitherOrientation() {
        listOf(210f, 540f, 1200f).forEach { viewport ->
            val preferred = 1300f
            assertEquals(viewport, GanttZoom.FIT.width(viewport, preferred), 0.001f)
            var zoom = GanttZoom.FIT
            var count = 0
            while (true) {
                val next = zoom.next(viewport, preferred, true) ?: break
                assertTrue(next.width(viewport, preferred) > zoom.width(viewport, preferred))
                zoom = next; count++
                assertTrue(count < 10)
            }
            assertNull(zoom.next(viewport, preferred, true))
            while (true) {
                val next = zoom.next(viewport, preferred, false) ?: break
                assertTrue(next.width(viewport, preferred) < zoom.width(viewport, preferred))
                zoom = next
            }
            assertEquals(viewport, zoom.width(viewport, preferred), 0.001f)
        }
    }

    @Test fun shortSchedulesNeverShrinkBelowTheViewportAndLongOnesStayBounded() {
        GanttZoom.entries.forEach { zoom ->
            assertTrue(zoom.width(700f, 280f) >= 700f)
            assertTrue(zoom.width(220f, 24_000f) <= 24_000f)
        }
        assertNull(GanttZoom.FIT.next(700f, 280f, false))
        assertNull(GanttZoom.FOUR.next(220f, 24_000f, true))
    }

    @Test fun condensedWeeklyHeaderCoversTheEntireAxisWithoutGapsOrOverlaps() {
        val stage = Stage(name = "Período longo", plannedStart = LocalDate.of(2026, 11, 1), plannedEnd = LocalDate.of(2027, 7, 31))
        val timeline = GanttTimeline(requireNotNull(GanttAxis.from(listOf(stage.ganttRow(LocalDate.of(2026, 11, 27))))), GanttScale.WEEKS)
        val bands = timeline.bands(2, 220f)
        assertTrue(bands.size < timeline.weeks.size)
        assertEquals(timeline.axis.start, bands.first().start)
        assertEquals(timeline.axis.end, bands.last().end)
        assertTrue(bands.zipWithNext().all { (a, b) -> a.end.plusDays(1) == b.start })
        assertEquals(1f, bands.sumOf { timeline.fraction(it).toDouble() }.toFloat(), 0.0001f)
        assertTrue(bands.all { timeline.fraction(it) * 220f > 0 })
    }

    @Test fun readableWeeklyHeadersRetainTheirIsoLabelsAndTheirActualDates() {
        val stage = Stage(name = "Virada do ano", plannedStart = LocalDate.of(2026, 12, 28), plannedEnd = LocalDate.of(2027, 1, 8))
        val axis = requireNotNull(GanttAxis.from(listOf(stage.ganttRow(LocalDate.of(2026, 12, 31)))))
        val timeline = GanttTimeline(axis, GanttScale.WEEKS)
        assertEquals(timeline.weeks, timeline.bands(2, 1200f))
        assertEquals("week-2026-53", timeline.bands(2, 1200f).first().key)
    }
}
