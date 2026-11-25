package br.com.obracontrol

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CostScheduleTest {
    private val date = LocalDate.of(2026, 11, 23)

    @Test
    fun monthlyDatesUseTheOriginalDayAndCentsReconcileToTheCost() {
        val plans = buildPaymentSchedule(CostKind.MATERIAL, "cost", 10_001, 3, LocalDate.of(2028, 1, 31), false)
        assertEquals(listOf(3_334L, 3_334L, 3_333L), plans.map { it.amount })
        assertEquals(listOf(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31)), plans.map { it.date })
        assertEquals(10_001L, plans.sumOf { it.amount })
        assertThrows(IllegalArgumentException::class.java) { buildPaymentSchedule(CostKind.LABOR, "x", 1, 2, date, true) }
    }

    @Test
    fun linkedFutureParcelAndLegacyPaymentsAreAppliedOnceAndKeepActualPaymentDates() {
        val stage = Stage(name = "Montagem", budget = 90_000)
        val plans = buildPaymentSchedule(CostKind.LABOR, stage.id, 90_000, 3, date.plusWeeks(1), true)
        val direct = Payment(targetId = stage.id, amount = 20_000, date = date, scheduleId = plans[1].id)
        val legacy = Payment(targetId = stage.id, amount = 25_000, date = date.plusDays(1))
        val project = Project(stages = listOf(stage), scheduledPayments = plans, payments = listOf(direct, legacy))
        project.validate()
        val positions = project.costPaymentPositions(CostKind.LABOR, stage.id)
        assertEquals(listOf(25_000L, 20_000L, 0L), positions.map { it.applied })
        assertEquals(listOf(5_000L, 10_000L, 30_000L), positions.map { it.remaining })
        val calendar = CostPaymentCalendar(project)
        assertEquals(45_000L, calendar.weeks.sumOf { it.paid })
        assertEquals(45_000L, calendar.weeks.sumOf { it.pending.known })
        assertEquals(90_000L, calendar.weeks.sumOf { it.knownTotal })
        assertEquals(date, calendar.weeks.first().start)
        assertEquals(45_000L, calendar.weeks.first().paid)
        assertEquals(0L, calendar.weeks.first().pending.known)
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(project)))
    }

    @Test
    fun advancePaymentRemovesTheFutureChargeAndRepeatSaveDoesNotDuplicateThePayment() {
        val order = Purchase(name = "Serviço", total = 60_000, nature = CostNature.SERVICE)
        val plans = buildPaymentSchedule(CostKind.MATERIAL, order.id, 60_000, 2, date.plusWeeks(1), true)
        val payment = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 30_000, date = date, scheduleId = plans[1].id)
        val project = Project(purchases = listOf(order), scheduledPayments = plans).recordPurchase(order, payment)
        assertEquals(project, project.recordPurchase(order, payment))
        val positions = project.costPaymentPositions(CostKind.MATERIAL, order.id)
        assertEquals(30_000L, positions.first().remaining)
        assertTrue(positions.last().completed)
        val calendar = CostPaymentCalendar(project)
        assertEquals(1, calendar.pending.size)
        assertEquals(plans.first().date, calendar.pending.single().date)
        assertEquals(date, calendar.entries.single { it.paid }.date)
        assertEquals(CostCategory.SERVICE, calendar.pending.single().target.category)
    }

    @Test
    fun unpricedUndatedAndEstimatedCostsRemainVisibleAcrossAllThreeCategories() {
        val stage = Stage(name = "Montagem", budget = 20_000, plannedEnd = date.plusDays(6))
        val material = Purchase(name = "Material a cotar")
        val service = Purchase(name = "Instalação", total = 30_000, nature = CostNature.SERVICE, dueDate = date.plusDays(7))
        val calendar = CostPaymentCalendar(Project(stages = listOf(stage), purchases = listOf(material, service)))
        assertEquals(3, calendar.pending.size)
        assertTrue(calendar.pending.single { it.target.kind == CostKind.LABOR }.estimatedDate)
        assertEquals(date, calendar.weeks.first().start)
        assertEquals(date.plusWeeks(1), calendar.weeks[1].start)
        assertNull(calendar.weeks.last().start)
        assertTrue(calendar.weeks.last().pending.entirelyUndefined)
        assertEquals(setOf(CostCategory.LABOR, CostCategory.MATERIAL, CostCategory.SERVICE), calendar.pending.map { it.target.category }.toSet())
    }

    @Test
    fun reprogrammingAndRemovingSchedulesPreservePaymentIdsAmountsDatesAndProofs() {
        val stage = Stage(name = "Cobertura", budget = 60_000)
        val plans = buildPaymentSchedule(CostKind.LABOR, stage.id, 60_000, 2, date, true)
        val proof = DocumentAttachment("a".repeat(64), "comprovante.pdf", DocumentType.PDF, 100)
        val payment = Payment(targetId = stage.id, amount = 30_000, date = date, payer = "Pessoa A", method = "Pix",
            proofs = listOf(proof), scheduleId = plans.first().id)
        val project = Project(stages = listOf(stage), scheduledPayments = plans, payments = listOf(payment), notes = "Manter histórico")
        val replaced = project.replacePaymentSchedule(CostKind.LABOR, stage.id,
            buildPaymentSchedule(CostKind.LABOR, stage.id, 60_000, 3, date.plusWeeks(1), true))
        assertEquals(listOf(payment.copy(scheduleId = null)), replaced.payments)
        assertEquals(project.notes, replaced.notes)
        assertEquals(30_000L, CostPaymentCalendar(replaced).pending.sumOf { it.amount!! })
        val removed = replaced.replacePaymentSchedule(CostKind.LABOR, stage.id, emptyList())
        assertTrue(removed.scheduledPayments.isEmpty())
        assertEquals(replaced.payments, removed.payments)
    }

    @Test
    fun versionSevenReadsWithoutChangingDataAndVersionEightMergesSchedulesWithoutDuplicates() {
        val stage = Stage(name = "Montagem", budget = 60_000)
        val old = Project(stages = listOf(stage), payments = listOf(Payment(targetId = stage.id, amount = 10_000)))
        val json = JSONObject(ProjectCodec.encode(old)).put("version", 7)
        json.remove("scheduledPayments")
        json.getJSONArray("payments").getJSONObject(0).remove("scheduleId")
        assertEquals(old, ProjectCodec.decode(json.toString()))
        val plans = buildPaymentSchedule(CostKind.LABOR, stage.id, 60_000, 2, date, true)
        val source = old.copy(scheduledPayments = plans,
            payments = listOf(old.payments.single().copy(scheduleId = plans.first().id)))
        assertEquals(source, ProjectCodec.decode(ProjectCodec.encode(source)))
        assertEquals(0, source.mergeFrom(source).added)
        val localStage = stage.copy(id = "local-stage")
        val localPlans = plans.mapIndexed { index, plan -> plan.copy(id = "local-plan-$index", targetId = localStage.id) }
        val local = source.copy(stages = listOf(localStage), scheduledPayments = localPlans,
            payments = listOf(source.payments.single().copy(id = "local-payment", targetId = localStage.id, scheduleId = localPlans.first().id)))
        val merged = local.mergeFrom(source)
        assertEquals(0, merged.added)
        assertEquals(local, merged.project)
        merged.project.validate()
    }

    @Test
    fun rejectsOverplannedAmountsAndPaymentsLinkedToAnotherCost() {
        val stage = Stage(name = "Montagem", budget = 10_000)
        val other = Stage(name = "Outra etapa", budget = 10_000)
        val plan = ScheduledPayment(kind = CostKind.LABOR, targetId = stage.id, amount = 11_000)
        assertThrows(IllegalArgumentException::class.java) {
            Project(stages = listOf(stage), scheduledPayments = listOf(plan)).validate()
        }
        val valid = plan.copy(amount = 10_000)
        assertThrows(IllegalArgumentException::class.java) {
            Project(stages = listOf(stage, other), scheduledPayments = listOf(valid),
                payments = listOf(Payment(targetId = other.id, amount = 1_000, scheduleId = valid.id))).validate()
        }
    }
}
