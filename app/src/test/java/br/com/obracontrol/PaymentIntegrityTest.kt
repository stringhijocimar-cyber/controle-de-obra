package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PaymentIntegrityTest {
    private val date = LocalDate.of(2026, 11, 26)

    @Test fun newIdenticalPaymentNeedsExplicitConfirmationButEditingTheSameIdNeverAddsAnother() {
        val order = Purchase(name = "Materiais", total = 100_000)
        val first = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 30_000, date = date, payer = "Pessoa A", method = "Pix")
        val project = Project(purchases = listOf(order)).recordPayment(first)
        val repeated = first.copy(id = newId(), payer = " pessoa a ", method = " PIX ")
        assertEquals(listOf(first), project.similarExistingPayments(repeated))
        assertThrows(IllegalArgumentException::class.java) { project.recordPayment(repeated) }
        assertEquals(30_000L, Balance(project).paid)
        assertEquals(project, project.recordPayment(first))
        assertEquals(1, project.recordPayment(first.copy(notes = "Correção" )).payments.size)
        val confirmed = project.recordPayment(repeated, allowSimilar = true)
        assertEquals(60_000L, Balance(confirmed).paid)
        assertEquals(confirmed, confirmed.recordPayment(repeated))
    }

    @Test fun differentParcelsDatesAndReferencesRemainDistinctEvenWithEqualValues() {
        val order = Purchase(name = "Compra em 2x", total = 60_000, installmentCount = 2)
        val plans = buildPaymentSchedule(CostKind.MATERIAL, order.id, 60_000, 2, date, false)
        val first = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 30_000, date = date, scheduleId = plans.first().id)
        val second = first.copy(id = newId(), scheduleId = plans.last().id)
        val project = Project(purchases = listOf(order), scheduledPayments = plans).recordPayment(first).recordPayment(second)
        assertEquals(0, project.paymentReviewCosts().single().possibleDuplicates)
        assertFalse(project.paymentReviewCosts().single().needsReview)
        assertNotEquals(similarPaymentKey(first), similarPaymentKey(second))
        val old = first.copy(scheduleId = null)
        val base = Project(purchases = listOf(order), payments = listOf(old))
        assertTrue(base.similarExistingPayments(old.copy(id = newId(), date = date.plusDays(1))).isEmpty())
        val referenced = old.copy(receipt = "PIX-01")
        val withRef = base.copy(payments = listOf(referenced))
        assertTrue(withRef.similarExistingPayments(referenced.copy(id = newId(), receipt = "PIX-02")).isEmpty())
    }

    @Test fun reviewIndexFindsRepetitionsAndOverpaymentsWithoutChangingAnyRecordsOrFutureParcels() {
        val order = Purchase(name = "Materiais", total = 330_000, installmentCount = 3)
        val first = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 330_000, date = date)
        val plans = buildPaymentSchedule(CostKind.MATERIAL, order.id, 330_000, 3, date, false)
        val project = Project(purchases = listOf(order), scheduledPayments = plans, payments = listOf(first, first.copy(id = newId()), first.copy(id = newId())))
        val index = project.paymentReviewCosts().single()
        assertEquals(2, index.possibleDuplicates)
        assertEquals(660_000L, index.overpaid)
        assertEquals(990_000L, index.paid)
        assertEquals(3, project.payments.size)
        assertEquals(990_000L, Balance(project).paid)
        val corrected = project.reviewPayments(CostKind.MATERIAL, order.id, project.payments.associate { it.id to if (it.id == first.id) it.amount else null }, "Duplicados", date)
        assertEquals(0, corrected.paymentReviewCosts().single().possibleDuplicates)
        assertFalse(corrected.paymentReviewCosts().single().needsReview)
        assertEquals(330_000L, Balance(corrected).paid)
        assertEquals(2, corrected.paymentAudit.size)
        assertEquals(plans, corrected.scheduledPayments)
        assertEquals(330_000L, Balance(corrected.mergeFrom(project).project).paid)
        assertEquals(corrected, ProjectCodec.decode(ProjectCodec.encode(corrected)))
    }

    @Test fun overpaidDifferentDatesRemainReviewableAndFieldSeparatorsDoNotCollide() {
        val order = Purchase(name = "Serviço", total = 20_000, nature = CostNature.SERVICE)
        val first = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 20_000, date = date, payer = "A|B", method = "C")
        val second = first.copy(id = newId(), date = date.plusDays(1))
        val project = Project(purchases = listOf(order), payments = listOf(first, second))
        assertEquals(0, project.paymentReviewCosts().single().possibleDuplicates)
        assertTrue(project.paymentReviewCosts().single().needsReview)
        assertEquals(20_000L, project.paymentReviewCosts().single().overpaid)
        assertNotEquals(similarPaymentKey(first), similarPaymentKey(first.copy(payer = "A", method = "B|C")))
    }
}
