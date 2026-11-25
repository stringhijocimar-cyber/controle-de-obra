package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PurchaseTermsTest {
    private val date = LocalDate.of(2026, 11, 25)

    @Test fun editingCardTermsPreservesActualPaymentsDocumentsAndScheduledDueDates() {
        val order = Purchase(name = "Revestimento", total = 100_001, installmentCount = 2, method = "Pix", notes = "Manter")
        val plans = buildPaymentSchedule(CostKind.MATERIAL, order.id, 100_001, 2, date, false)
        val proof = DocumentAttachment("a".repeat(64), "comprovante.pdf", DocumentType.PDF, 100)
        val payment = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 20_000, date = date, proofs = listOf(proof), scheduleId = plans.first().id)
        val project = Project(purchases = listOf(order), payments = listOf(payment), scheduledPayments = plans)
        val edited = project.applyPurchaseTerms(PurchaseTermsChange(order.id, "Cartão de crédito", 3, order.total))
        assertEquals(project.payments, edited.payments)
        assertEquals(project.scheduledPayments, edited.scheduledPayments)
        assertEquals(order.copy(method = "Cartão de crédito", installmentCount = 3), edited.purchases.single())
        assertEquals(80_001L, InstallmentBalance(edited.purchases.single(), Balance(edited).materialPaid).remaining)
        assertEquals("3x\n2 de ${33_334L.brl()} + 1 de ${33_333L.brl()}", edited.purchases.single().installmentText())
        assertEquals(edited, ProjectCodec.decode(ProjectCodec.encode(edited)))
    }

    @Test fun paymentAndItsPurchaseTermsSaveAtomicallyWithoutDuplicatingAnotherPayment() {
        val order = Purchase(name = "Compra", total = 100_001)
        val payment = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 10_000, date = date, method = "Pix")
        val other = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 5_000, date = date.minusDays(1))
        val project = Project(purchases = listOf(order), payments = listOf(payment, other))
        val changed = payment.copy(amount = 20_000, method = "Cartão de crédito")
        val terms = PurchaseTermsChange(order.id, "Cartão de crédito", 5, order.total)
        val result = project.recordPayment(changed, terms)
        assertEquals(listOf(changed, other), result.payments)
        assertEquals(5, result.purchases.single().installmentCount)
        assertEquals(25_000L, Balance(result).paid)
        assertEquals(result, result.recordPayment(changed, terms))
    }

    @Test fun foreignTermsAndTotalsBelowExistingScheduleAreRejectedWithoutPartialEdits() {
        val order = Purchase(name = "Compra", total = 50_000)
        val other = Purchase(name = "Outra", total = 20_000)
        val project = Project(purchases = listOf(order, other), scheduledPayments = buildPaymentSchedule(CostKind.MATERIAL, order.id, 50_000, 2, date, false))
        assertThrows(IllegalArgumentException::class.java) { project.recordPayment(Payment(kind = CostKind.MATERIAL,
            targetId = order.id, amount = 1_000), PurchaseTermsChange(other.id, "Cartão", 3, other.total)) }
        assertThrows(IllegalArgumentException::class.java) { project.applyPurchaseTerms(PurchaseTermsChange(order.id, "Cartão", 4, 10_000)) }
        assertTrue(project.payments.isEmpty())
        assertEquals(50_000L, project.purchases.first().total)
    }

    @Test fun missingPricesRemainUnknownAndCardClassificationUsesPurchaseAndPaymentMethods() {
        val order = Purchase(name = "A cotar")
        val project = Project(purchases = listOf(order))
        val result = project.applyPurchaseTerms(readPurchaseTerms(order, "CARTAO DE CREDITO", "installments", "4", ""))
        assertNull(result.purchases.single().forecast)
        assertTrue(result.purchases.single().installmentText().contains("valor a definir"))
        assertEquals(1, result.cardAndInstallmentPurchases().size)
        assertTrue(result.payments.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { readPurchaseTerms(order, "Cartão", "installments", "1", "") }
    }

    @Test fun managerialTotalsReconcileAndDueHorizonsNeverOverlapAtBoundaries() {
        val stage = Stage(name = "Mão de obra", budget = 20_000, dueDate = date.plusDays(29))
        val orders = listOf(Purchase(name = "Material", total = 30_000, dueDate = date.minusDays(1)),
            Purchase(name = "Serviço", total = 40_000, nature = CostNature.SERVICE, dueDate = date.plusDays(6)),
            Purchase(name = "Dia oito", total = 50_000, dueDate = date.plusDays(7)),
            Purchase(name = "Futuro", total = 60_000, dueDate = date.plusDays(30)), Purchase(name = "Sem preço e data"))
        val payment = Payment(kind = CostKind.MATERIAL, targetId = orders.first().id, amount = 5_000, date = date)
        val project = Project(stages = listOf(stage), purchases = orders, payments = listOf(payment))
        val report = ManagementReportSummary(project, date)
        assertEquals(Balance(project).total, report.forecast)
        assertEquals(Balance(project).paid, report.paid)
        assertEquals(25_000L, report.pending(DueHorizon.OVERDUE).known)
        assertEquals(40_000L, report.pending(DueHorizon.NEXT_SEVEN).known)
        assertEquals(70_000L, report.pending(DueHorizon.DAYS_EIGHT_TO_THIRTY).known)
        assertEquals(60_000L, report.pending(DueHorizon.LATER).known)
        assertEquals(1, report.pending(DueHorizon.UNDATED).undefinedCount)
        assertEquals(report.calendar.pending.sumOf { it.amount ?: 0 }, DueHorizon.entries.sumOf { report.pending(it).known })
    }
}
