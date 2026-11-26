package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinancialIntegrationTest {
    private val today = LocalDate.of(2026, 11, 26)
    private fun fixture(): Project {
        val stage = Stage(name = "Cobertura", budget = 100_000, progress = 35, plannedStart = today.minusDays(4), plannedEnd = today.plusDays(1))
        val material = Purchase(name = "Material em 3x", total = 300_000, installmentCount = 3, stageId = stage.id, dueDate = today.minusDays(20))
        val service = Purchase(name = "Serviço em 2x", total = 100_000, installmentCount = 2, stageId = stage.id, nature = CostNature.SERVICE)
        val plans = buildPaymentSchedule(CostKind.MATERIAL, material.id, 300_000, 3, today, false) +
            buildPaymentSchedule(CostKind.MATERIAL, service.id, 100_000, 2, today.plusDays(3), false) +
            buildPaymentSchedule(CostKind.LABOR, stage.id, 100_000, 2, today.plusDays(7), true)
        return Project(stages = listOf(stage), purchases = listOf(material, service), laborBudget = stage.budget, scheduledPayments = plans,
            payments = listOf(Payment(kind = CostKind.MATERIAL, targetId = material.id, amount = 100_000, date = today,
                payer = "Pessoa A", method = "Pix", scheduleId = plans.first().id),
                Payment(targetId = stage.id, amount = 20_000, date = today, payer = "Pessoa B", method = "Dinheiro")))
    }

    private fun verify(project: Project) {
        project.validate()
        val balance = Balance(project)
        val calendar = CostPaymentCalendar(project)
        val manager = ManagementReportSummary(project, today)
        val table = DisbursementTable(project)
        val agenda = WorkAgenda(project, balance, today)
        val charts = project.paymentBreakdown()
        val pending = tableAmountTotal(calendar.pending.map { it.amount })
        assertEquals(project.payments.sumOf { it.amount }, balance.paid)
        assertEquals(balance.paid, charts.total)
        assertEquals(balance.paid, charts.byMethod.sumOf { it.amount })
        assertEquals(balance.paid, balance.payers.sumOf { it.amount })
        assertEquals(balance.paid, manager.paid)
        assertEquals(balance.total, manager.forecast)
        assertEquals(balance.paid, table.total.paid)
        assertEquals(balance.paid, calendar.entries.filter { it.paid }.sumOf { it.amount ?: 0 })
        assertEquals(pending, table.total.pending)
        assertEquals(pending.known, balance.pendingByCost.known)
        assertEquals(pending.undefinedCount, balance.pendingByCost.undefinedCount)
        assertEquals(pending, tableAmountTotal(agenda.entries.filter { it.isPayment }.map { it.amount }))
        assertEquals(pending.known, DueHorizon.entries.sumOf { manager.pending(it).known })
        assertEquals(pending.undefinedCount, DueHorizon.entries.sumOf { manager.pending(it).undefinedCount })
        val labor = LaborSpendCalendar(project, balance)
        assertEquals(balance.laborPaid, labor.weeks.sumOf { it.paid })
        assertEquals(calendar.pending.filter { it.target.kind == CostKind.LABOR }.sumOf { it.amount ?: 0 }, labor.weeks.sumOf { it.balance })
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(project)))
    }

    @Test fun allViewsReconcileAfterPayingEditingAndReviewingAParcel() {
        val initial = fixture()
        verify(initial)
        assertEquals(120_000L, Balance(initial).paid)
        assertEquals(380_000L, Balance(initial).pendingByCost.known)
        val order = initial.purchases.first()
        assertEquals(today.plusMonths(1), Balance(initial).disbursements.first { it.targetId == order.id }.date)
        assertEquals(initial.purchaseNextDue(order), Balance(initial).disbursements.first { it.targetId == order.id }.date)
        val draft = PurchaseInstallmentDraft.initial(initial, order)
        val second = draft.rows[1]
        val paid = initial.applyPurchaseInstallments(order, requireNotNull(draft.toggle(second.id, true, initial, order).change(order.id)))
        verify(paid)
        assertEquals(220_000L, Balance(paid).paid)
        assertEquals(today.plusMonths(2), Balance(paid).disbursements.first { it.targetId == order.id }.date)
        val firstPayment = paid.payments.first()
        val edited = paid.recordPayment(firstPayment.copy(amount = 90_000))
        verify(edited)
        assertEquals(today, Balance(edited).disbursements.first { it.targetId == order.id }.date)
        assertEquals(35, edited.stages.single().progress)
        val amounts = edited.paymentsFor(CostKind.MATERIAL, order.id).associate { it.id to if (it.id == firstPayment.id) null else it.amount }
        val reviewed = edited.reviewPayments(CostKind.MATERIAL, order.id, amounts, "Conferência", today)
        verify(reviewed)
        assertEquals(120_000L, Balance(reviewed).paid)
        assertEquals(initial.scheduledPayments, reviewed.scheduledPayments)
    }

    @Test fun overpaymentsRemainSeparateFromOtherOpenCostsAndNetBalance() {
        val stage = Stage(name = "Etapa", budget = 10_000)
        val order = Purchase(name = "Material", total = 10_000)
        val service = Purchase(name = "Serviço em aberto", total = 10_000, nature = CostNature.SERVICE)
        val project = Project(stages = listOf(stage), purchases = listOf(order, service), payments = listOf(
            Payment(targetId = stage.id, amount = 15_000), Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 15_000)))
        verify(project)
        val balance = Balance(project)
        assertEquals(0L, balance.remaining)
        assertEquals(10_000L, balance.pendingByCost.known)
        assertEquals(10_000L, balance.overpaidByCost)
        assertEquals(service.id, balance.disbursements.single().targetId)
        assertEquals(balance.total, balance.paid + balance.pendingByCost.known - balance.overpaidByCost)
    }

    @Test fun unknownAmountsAndEstimatedDatesStayExplicitAcrossAgendaCalendarAndBalance() {
        val stage = Stage(name = "Etapa sem preço", plannedEnd = today.plusDays(2))
        val order = Purchase(name = "A cotar")
        val project = Project(stages = listOf(stage), purchases = listOf(order), payments = listOf(
            Payment(targetId = stage.id, amount = 2_000), Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 3_000)))
        verify(project)
        assertEquals(2, Balance(project).pendingByCost.undefinedCount)
        val estimate = Balance(project).disbursements.first { it.targetId == stage.id }
        assertNull(estimate.date)
        assertEquals(today.plusDays(2), estimate.forecastDate)
        assertTrue(estimate.dateEstimated)
        assertNull(WorkAgenda(project, Balance(project), today).entries.single { it.isPayment && it.targetId == stage.id }.date)
        assertEquals(5_000L, project.paymentBreakdown().total)
    }

    @Test fun commercialTermsPreserveActualPaymentsAndFlagScheduleDifferences() {
        val initial = fixture()
        val order = initial.purchases.first()
        val changed = initial.applyPurchaseTerms(PurchaseTermsChange(order.id, "Cartão", 4, order.total))
        verify(changed)
        assertEquals(initial.payments, changed.payments)
        assertEquals(initial.scheduledPayments, changed.scheduledPayments)
        assertTrue(Balance(changed).alerts().any { it.first == order.name && it.second.contains("condição comercial") })
        val missingDates = Project(stages = initial.stages, purchases = listOf(order.copy(dueDate = null)))
        assertTrue(Balance(missingDates).alerts().any { it.second.contains("sem programação") })
        assertTrue(missingDates.payments.isEmpty())
    }

    @Test fun stagePaymentSchedulesStayLinkedToGanttWithoutCompletingPhysicalExecution() {
        val project = fixture()
        verify(project)
        val stage = project.stages.single()
        val rows = LaborSpendCalendar(project, Balance(project)).byStage.getValue(stage.id)
        val gantt = stage.ganttRow(today, rows)
        assertEquals(20_000L, gantt.spend.filter { it.paid }.sumOf { it.amount ?: 0 })
        assertEquals(80_000L, gantt.spend.filterNot { it.paid }.sumOf { it.amount ?: 0 })
        assertEquals(today.plusDays(7), project.nextPendingPosition(CostKind.LABOR, stage.id)?.date)
        assertTrue(GanttAxis.from(listOf(gantt))!!.end >= today.plusDays(14))
        assertEquals(35, stage.progress)
    }
}
