package br.com.obracontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LaborSpendTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun weeklyCalendarCountsPayDaysAndOnlyForecastsTheOutstandingLabor() {
        val stage = Stage(name = "Cobertura", budget = 300_000, plannedEnd = monday.plusDays(3),
            dueDate = monday.plusDays(12))
        val completed = Stage(name = "Elétrica", budget = 50_000, plannedEnd = monday, progress = 100)
        val purchase = Purchase(name = "Material", total = 40_000)
        val project = Project(stages = listOf(stage, completed), purchases = listOf(purchase), payments = listOf(
            Payment(targetId = stage.id, amount = 80_000, date = monday.minusDays(1)),
            Payment(targetId = stage.id, amount = 20_000, date = monday),
            Payment(targetId = completed.id, amount = 55_000, date = monday.plusDays(1)),
            Payment(kind = CostKind.MATERIAL, targetId = purchase.id, amount = 40_000, date = monday),
        ))
        val calendar = LaborSpendCalendar(project, Balance(project))

        assertEquals(listOf(monday.minusDays(7), monday, monday.plusDays(7)), calendar.weeks.map { it.start })
        assertEquals(listOf(80_000L, 75_000L, 0L), calendar.weeks.map { it.paid })
        assertEquals(listOf(0L, 0L, 200_000L), calendar.weeks.map { it.balance })
        val forecast = calendar.weeks.last().entries.single()
        assertEquals(stage.dueDate, forecast.date)
        assertEquals(LaborSpendStatus.DUE, forecast.status)
        assertTrue(calendar.byStage.getValue(completed.id).all { it.paid })
    }

    @Test
    fun calendarLabelsEstimatedDatesAndKeepsUndatedAndUnknownAmountsVisible() {
        val estimated = Stage(name = "Forro", budget = 120_000, plannedEnd = monday.plusDays(2))
        val unscheduled = Stage(name = "Pintura", budget = 80_000)
        val unknown = Stage(name = "Bancada", plannedEnd = monday.plusDays(4))
        val project = Project(stages = listOf(estimated, unscheduled, unknown),
            payments = listOf(Payment(targetId = estimated.id, amount = 20_000)))
        val balance = Balance(project)
        val calendar = LaborSpendCalendar(project, balance)

        val (week, undated) = calendar.weeks
        assertEquals(monday, week.start)
        assertEquals(100_000L, week.balance)
        assertEquals(1, week.undefined)
        assertEquals(LaborSpendStatus.ESTIMATED, week.entries.first().status)
        assertEquals(estimated.plannedEnd, week.entries.first().date)
        assertNull(week.entries.last().amount)
        assertNull(undated.start)
        assertEquals(80_000L, undated.balance)
        assertEquals(20_000L, undated.paid)
        assertNull(WorkAgenda(project, balance, monday).entries.single { it.targetId == estimated.id && it.isPayment }.date)
    }

    @Test
    fun ganttAxisIncludesEarlyAndLatePaymentsOutsideExecutionDates() {
        val stage = Stage(name = "Piso", budget = 300_000, plannedStart = monday, plannedEnd = monday.plusDays(1),
            dueDate = monday.plusDays(20))
        val early = monday.minusDays(10)
        val project = Project(stages = listOf(stage), payments = listOf(Payment(targetId = stage.id, amount = 50_000, date = early)))
        val spend = LaborSpendCalendar(project, Balance(project)).byStage.getValue(stage.id)

        val axis = requireNotNull(GanttAxis.from(listOf(stage.ganttRow(monday, spend))))
        assertTrue(requireNotNull(stage.dueDate) in axis.start..axis.end)
        assertTrue(early in axis.start..axis.end)
        assertTrue(axis.dayCenter(early) < axis.boundary(monday))
        val financialOnly = stage.copy(plannedStart = null, plannedEnd = null).ganttRow(monday, spend)
        assertEquals(axis.start, GanttAxis.from(listOf(financialOnly))?.start)
    }
}
