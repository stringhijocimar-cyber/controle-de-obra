package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyClosingAndDeliveryTest {
    private val friday = LocalDate.of(2026, 11, 27)

    @Test fun cashColumnsCloseOnFridayAndWeekendsMoveForwardWithoutChangingAnyPayments() {
        val order = Purchase(name = "Material", total = 10_000, dueDate = friday.plusWeeks(2))
        val dates = (-6L..2L).map(friday::plusDays)
        val payments = dates.map { Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 100, date = it) }
        val project = Project(purchases = listOf(order), payments = payments)
        val before = ProjectCodec.encode(project)
        val table = DisbursementTable(project)
        assertEquals(listOf(friday, friday.plusWeeks(1), friday.plusWeeks(2)), table.columns(0))
        assertEquals(700L, table.total.amounts(friday, CashView.PAID).known)
        assertEquals(200L, table.total.amounts(friday.plusWeeks(1), CashView.PAID).known)
        assertEquals(9_100L, table.total.amounts(friday.plusWeeks(2), CashView.PENDING).known)
        assertEquals(10_000L, table.columns(0).sumOf { table.total.amounts(it, CashView.TOTAL).known })
        assertTrue(table.columns(0).filterNotNull().all { it.dayOfWeek == DayOfWeek.FRIDAY })
        assertEquals("S48\n27/11", friday.cashWeekLabel())
        assertEquals("S53/26\n01/01", LocalDate.of(2027, 1, 1).cashWeekLabel())
        assertEquals(before, ProjectCodec.encode(project))
    }

    @Test fun pagesKeepEveryFridayAndUndatedUnknownCostsOnceIncludingAcrossYears() {
        val first = Purchase(name = "Primeiro", total = 101, dueDate = LocalDate.of(2026, 12, 31))
        val last = Purchase(name = "Último", total = 202, dueDate = LocalDate.of(2027, 7, 10))
        val unknown = Purchase(name = "A definir")
        val table = DisbursementTable(Project(purchases = listOf(first, last, unknown)))
        val columns = (0 until table.pageCount).flatMap(table::columns)
        assertEquals(2, table.pageCount)
        assertEquals(LocalDate.of(2027, 1, 1), columns.first())
        assertEquals(LocalDate.of(2027, 7, 16), columns.filterNotNull().last())
        assertEquals(columns.size, columns.distinct().size)
        assertNull(columns.last())
        assertTrue(table.total.amounts(null, CashView.TOTAL).entirelyUndefined)
        assertEquals(303L, columns.sumOf { table.total.amounts(it, CashView.TOTAL).known })
    }

    @Test fun totalDurationIncludesLinkedMaterialDatesButDoesNotFollowPaymentDatesOrUnlinkedInputs() {
        val stage = Stage(name = "Bancada", plannedStart = friday.minusDays(5), plannedEnd = friday.plusDays(5),
            actualStart = friday.minusDays(4), progress = 40)
        val material = Purchase(name = "Pedra", stageId = stage.id, deliveryExpected = friday.minusDays(9), receivedDate = friday.minusDays(7))
        val later = Purchase(name = "Rejunte", stageId = stage.id, deliveryExpected = friday.plusDays(8), receivedProgress = 20)
        val service = Purchase(name = "Serviço", stageId = stage.id, nature = CostNature.SERVICE, deliveryExpected = friday.plusDays(12))
        val unlinked = Purchase(name = "Outra etapa", deliveryExpected = friday.minusYears(2))
        val project = Project(stages = listOf(stage), purchases = listOf(material, later, service, unlinked),
            payments = listOf(Payment(targetId = stage.id, amount = 50, date = friday.plusDays(30))))
        val rows = project.ganttRows(friday, LaborSpendCalendar(project, Balance(project)))
        val row = rows.single()
        assertEquals(setOf(material.id, later.id, service.id), row.deliveries.map { it.id }.toSet())
        assertEquals(GanttPeriod(friday.minusDays(9), friday.plusDays(12)), row.totalPeriod)
        assertEquals(22L, row.totalDays)
        assertEquals(10L, row.elapsedDays(friday))
        assertEquals(0L, row.elapsedDays(friday.minusDays(10)))
        assertEquals(22L, row.elapsedDays(friday.plusYears(1)))
        assertNull(row.deliveries.last().received)
        assertNull(row.stage.actualEnd)
        val axis = requireNotNull(GanttAxis.from(rows))
        assertFalse(friday.plusDays(30) in axis.start..axis.end)
        assertTrue(material.deliveryExpected!! in axis.start..axis.end)
        assertTrue(axis.days < 50)
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(project)))
    }

    @Test fun receiptsWithoutDatesNeverInventArrivalMilestonesAndMaterialOnlyStagesHaveAnAxis() {
        val stage = Stage(name = "Sem datas")
        val noDate = Purchase(name = "Já disponível", stageId = stage.id, receivedProgress = 100)
        val project = Project(stages = listOf(stage), purchases = listOf(noDate))
        val spend = LaborSpendCalendar(project, Balance(project))
        assertNull(GanttAxis.from(project.ganttRows(friday, spend)))
        val dated = project.copy(purchases = listOf(noDate.copy(deliveryExpected = friday)))
        val row = dated.ganttRows(friday, LaborSpendCalendar(dated, Balance(dated))).single()
        assertEquals(GanttPeriod(friday, friday), row.materialPeriod)
        assertEquals(1L, row.totalDays)
        assertNotNull(GanttAxis.from(listOf(row)))
        assertNull(row.actual)
        assertNull(row.deliveries.single().received)
    }
}
