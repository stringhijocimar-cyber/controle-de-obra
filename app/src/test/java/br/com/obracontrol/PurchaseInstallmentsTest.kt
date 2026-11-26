package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PurchaseInstallmentsTest {
    private val date = LocalDate.of(2026, 11, 26)
    private fun purchase() = Purchase(name = "Piso de exemplo", total = 564_275, installmentCount = 6, payer = "Pessoa A", method = "Cartão")

    @Test fun firstPaidDateCreatesMonthlyDueDatesAndOnlyOneActualPaymentOnRepeatedSave() {
        val order = purchase()
        val original = Project(purchases = listOf(order))
        var draft = PurchaseInstallmentDraft.initial(original, order)
        assertEquals(listOf(94_046L, 94_046L, 94_046L, 94_046L, 94_046L, 94_045L), draft.rows.map { it.amount })
        assertTrue(draft.rows.all { it.due.isBlank() })
        val firstId = draft.rows.first().id
        draft = draft.toggle(firstId, true, original, order).paymentDate(firstId, date.dateText(), original, order)
        val change = requireNotNull(draft.change(order.id))
        var saved = original.recordPurchase(order, installments = change)
        repeat(3) { saved = saved.recordPurchase(order, installments = change) }
        assertEquals(6, saved.scheduledPayments.size)
        assertEquals((0L..5L).map { date.plusMonths(it) }, saved.scheduledPayments.map { it.date })
        assertEquals(94_046L, Balance(saved).paid)
        assertEquals(470_229L, InstallmentBalance(order, Balance(saved).paid).remaining)
        assertEquals(date, saved.payments.single().date)
        assertEquals(firstId, saved.payments.single().scheduleId)
        assertEquals(date.plusMonths(1), saved.purchaseNextDue(order))
        assertEquals(5, saved.costPaymentPositions(CostKind.MATERIAL, order.id).count { !it.completed })
        val reopened = PurchaseInstallmentDraft.initial(saved, order)
        assertEquals(saved, saved.recordPurchase(order, installments = reopened.change(order.id)))
        assertEquals(saved, ProjectCodec.decode(ProjectCodec.encode(saved)))
    }

    @Test fun monthlyDatesKeepOriginalDayAndExistingPaymentIdsWhenAmountsOrDatesAreRegenerated() {
        val order = purchase()
        val original = Project(purchases = listOf(order))
        var draft = PurchaseInstallmentDraft.initial(original, order).firstDue("31/01/2027", original, order)
        assertEquals(listOf("31/01/2027", "28/02/2027", "31/03/2027"), draft.rows.take(3).map { it.due })
        draft = draft.toggle(draft.rows[1].id, true, original, order).paymentDate(draft.rows[1].id, "25/02/2027", original, order)
        val saved = original.recordPurchase(order, installments = draft.change(order.id))
        val changedOrder = order.copy(total = 600_000)
        val next = PurchaseInstallmentDraft.initial(saved, changedOrder).rebuild(saved, changedOrder)
        val changed = saved.recordPurchase(changedOrder, installments = next.change(order.id))
        assertEquals(saved.payments, changed.payments)
        assertEquals(saved.scheduledPayments.map { it.id }, changed.scheduledPayments.map { it.id })
        assertEquals(600_000L, changed.scheduledPayments.sumOf { it.amount })
        assertEquals(LocalDate.of(2027, 2, 25), changed.payments.single().date)
    }

    @Test fun oldFullPaymentIsPreservedAndDoesNotBecomeSixNewPayments() {
        val order = purchase()
        val proof = DocumentAttachment("a".repeat(64), "comprovante.pdf", DocumentType.PDF, 50)
        val payment = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 564_275, date = date, proofs = listOf(proof))
        val original = Project(purchases = listOf(order), payments = listOf(payment))
        val draft = PurchaseInstallmentDraft.initial(original, order).firstDue(date.dateText(), original, order)
        val saved = original.recordPurchase(order, installments = draft.change(order.id))
        assertEquals(listOf(payment), saved.payments)
        assertTrue(saved.costPaymentPositions(CostKind.MATERIAL, order.id).all { it.completed })
        val attempted = draft.toggle(draft.rows.first().id, true, original, order)
        assertEquals(saved, saved.applyPurchaseInstallments(order, requireNotNull(attempted.change(order.id))))
        assertEquals(original.documentAttachments(), saved.documentAttachments())
    }

    @Test fun partialOldPaymentIsDeductedOnceAndUndoPreservesProofsInAudit() {
        val order = Purchase(name = "Serviço", total = 100_000, installmentCount = 2, nature = CostNature.SERVICE)
        val proof = DocumentAttachment("b".repeat(64), "recibo.pdf", DocumentType.PDF, 70)
        val previous = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 20_000, date = date, proofs = listOf(proof))
        val original = Project(purchases = listOf(order), payments = listOf(previous))
        var draft = PurchaseInstallmentDraft.initial(original, order).firstDue(date.dateText(), original, order)
        draft = draft.toggle(draft.rows.first().id, true, original, order)
        val paid = original.recordPurchase(order, installments = draft.change(order.id))
        assertEquals(listOf(20_000L, 30_000L), paid.payments.map { it.amount })
        assertEquals(50_000L, Balance(paid).paid)
        val linked = paid.payments.last().copy(proofs = listOf(proof))
        val withProof = paid.copy(payments = listOf(previous, linked))
        val reopened = PurchaseInstallmentDraft.initial(withProof, order)
        val undo = reopened.toggle(reopened.rows.first().id, false, withProof, order)
        val restored = withProof.recordPurchase(order, installments = undo.change(order.id))
        assertEquals(listOf(previous), restored.payments)
        assertEquals(linked, restored.paymentAudit.single().payment)
        assertEquals(20_000L, Balance(restored).paid)
        assertEquals(restored, restored.recordPurchase(order, installments = undo.change(order.id)))
        assertEquals(restored, ProjectCodec.decode(ProjectCodec.encode(restored)))
    }

    @Test fun payingFutureParcelKeepsRealDateAndRejectsAnotherCostsPaymentAndMixedEntry() {
        val order = purchase()
        val other = purchase().copy(name = "Outra compra")
        val original = Project(purchases = listOf(order, other))
        var draft = PurchaseInstallmentDraft.initial(original, order).firstDue(date.plusMonths(1).dateText(), original, order)
        val last = draft.rows.last().id
        draft = draft.toggle(last, true, original, order).paymentDate(last, date.dateText(), original, order)
        val saved = original.recordPurchase(order, installments = draft.change(order.id))
        assertEquals(date, saved.payments.single().date)
        assertEquals(94_045L, CostPaymentCalendar(saved).entries.filter { it.paid }.sumOf { it.amount!! })
        assertEquals(470_230L, CostPaymentCalendar(saved).pending.filter { it.target.id == order.id }.sumOf { it.amount ?: 0 })
        val foreign = Payment(kind = CostKind.MATERIAL, targetId = other.id, amount = 1_000)
        val invalid = requireNotNull(draft.change(order.id)).copy(payments = listOf(InstallmentPaymentChange(last, foreign.id, true, date)))
        assertThrows(IllegalArgumentException::class.java) { original.copy(payments = listOf(foreign)).applyPurchaseInstallments(order, invalid) }
        assertThrows(IllegalArgumentException::class.java) { original.recordPurchase(order, foreign.copy(targetId = order.id), installments = draft.change(order.id)) }
    }
}
