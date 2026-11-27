package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DisbursementCalendarTest {
    @Test fun categoriesSeparateLaborFromExternalServicesWithoutChangingLinksOrAmounts() {
        val stage = Stage(name = "Bancada", budget = 100_000)
        val material = Purchase(name = "Insumos", total = 30_000, stageId = stage.id)
        val service = Purchase(name = "Pia sob encomenda", nature = CostNature.SERVICE, total = 200_000, stageId = stage.id)
        val project = Project(stages = listOf(stage), purchases = listOf(material, service), payments = listOf(
            Payment(targetId = stage.id, amount = 25_000),
            Payment(kind = CostKind.MATERIAL, targetId = material.id, amount = 10_000),
            Payment(kind = CostKind.MATERIAL, targetId = service.id, amount = 50_000)))
        val before = ProjectCodec.encode(project)
        assertEquals(listOf(stage.id), project.linkedCostOptions(CostCategory.LABOR, stage.id).map { it.id })
        assertEquals(listOf(service.id), project.linkedCostOptions(CostCategory.SERVICE, stage.id).map { it.id })
        assertEquals(listOf(material.id), project.linkedCostOptions(CostCategory.MATERIAL, stage.id).map { it.id })
        val scopes = CostCategory.entries.map { DisbursementTable(project, it) }
        assertEquals(85_000L, scopes.sumOf { it.total.paid })
        assertEquals(245_000L, scopes.sumOf { it.total.pending.known })
        assertEquals(50_000L, DisbursementTable(project, CostCategory.SERVICE).total.paid)
        assertEquals(setOf(CostCategory.SERVICE), DisbursementTable(project, CostCategory.SERVICE).entries.map { it.target.category }.toSet())
        assertEquals(before, ProjectCodec.encode(project))
        assertEquals(project, ProjectCodec.decode(before))
    }

    @Test fun monthlyGridIncludesLeapDayAndAllSixWeeksWhenRequired() {
        for (year in 2024..2027) for (month in 1..12) {
            val date = YearMonth.of(year, month)
            val grid = DisbursementMonth(date, emptyList())
            assertTrue(grid.weeks.size in 4..6)
            assertTrue(grid.weeks.all { it.size == 7 && it.first().date.dayOfWeek == DayOfWeek.MONDAY && it.last().date.dayOfWeek == DayOfWeek.SUNDAY })
            assertEquals((1..date.lengthOfMonth()).toList(), grid.weeks.flatten().map { it.date }.filter { YearMonth.from(it) == date }.map { it.dayOfMonth })
            assertEquals(grid.weeks.flatten().size, grid.weeks.flatten().map { it.date }.distinct().size)
        }
        assertEquals(6, DisbursementMonth(YearMonth.of(2026, 11), emptyList()).weeks.size)
        assertTrue(DisbursementMonth(YearMonth.of(2024, 2), emptyList()).weeks.flatten().any { it.date == LocalDate.of(2024, 2, 29) })
    }

    @Test fun calendarUsesActualPaymentDatesAndRemainingParcelsOnceAndKeepsUndatedUnknownAmounts() {
        val due = LocalDate.of(2026, 12, 15)
        val paidDate = LocalDate.of(2026, 11, 27)
        val order = Purchase(name = "Vidro sob encomenda", total = 100_001, installmentCount = 2, nature = CostNature.SERVICE)
        val unknown = Purchase(name = "A cotar")
        val plans = buildPaymentSchedule(CostKind.MATERIAL, order.id, 100_001, 2, due, false)
        val payment = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 50_001, date = paidDate, scheduleId = plans.first().id)
        val project = Project(purchases = listOf(order, unknown), payments = listOf(payment), scheduledPayments = plans)
        val calendar = MonthlyDisbursementCalendar(project)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2027, 1)), calendar.months.map { it.month })
        assertEquals(50_001L, calendar.months.sumOf { it.paid })
        assertEquals(50_000L, calendar.months.sumOf { it.pending.known })
        assertEquals(2, calendar.months.sumOf { it.weeks.flatten().sumOf { day -> day.entries.size } })
        assertEquals(paidDate, calendar.months.first().entries.single().date)
        assertEquals(due.plusMonths(1), calendar.months.last().entries.single().date)
        assertEquals(unknown.id, calendar.undated.single().target.id)
        assertNull(calendar.undated.single().amount)
        assertTrue(calendar.months.first().pending.known == 0L)
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(project)))
    }
}
