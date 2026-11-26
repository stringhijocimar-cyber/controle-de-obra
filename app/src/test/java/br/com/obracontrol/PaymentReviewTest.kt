package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PaymentReviewTest {
    private val date = LocalDate.of(2026, 11, 26)
    @Test fun editingAnExistingPurchasePaymentReusesItsIdAndAdditionalPaymentsAreExplicit() {
        val order = Purchase(name = "Materiais", total = 330_000)
        val first = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 330_000, date = date)
        var project = Project().recordPurchase(order, first)
        repeat(3) { project = project.recordPurchase(order.copy(notes = "Editado"), first.copy(amount = 330_000)) }
        assertEquals(1, project.payments.size)
        assertEquals(330_000L, Balance(project).paid)
        val second = first.copy(id = newId(), amount = 1_000)
        assertThrows(IllegalArgumentException::class.java) { project.recordPurchase(order, second) }
        val additional = project.recordPurchase(order, second, additionalPayment = true)
        assertEquals(331_000L, Balance(additional).paid)
        assertEquals(additional, additional.recordPurchase(order, second, additionalPayment = true))
    }

    @Test fun reviewingNineThousandNineHundredToThreeThousandThreeHundredKeepsHistoryAndProofs() {
        val order = Purchase(name = "Materiais", total = 330_000)
        val payments = (0..2).map { index -> Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 330_000,
            date = if (index == 0) null else date, proofs = listOf(DocumentAttachment((index+1).toString().repeat(64), "comprovante$index.pdf", DocumentType.PDF, 10))) }
        val original = Project(purchases = listOf(order), payments = payments)
        val reviewed = original.reviewPayments(CostKind.MATERIAL, order.id, payments.associate { it.id to if (it == payments.first()) it.amount else null }, "Duplicados", date)
        assertEquals(990_000L, Balance(original).paid)
        assertEquals(330_000L, Balance(reviewed).paid)
        assertEquals(listOf(payments.first()), reviewed.payments)
        assertEquals(payments.drop(1), reviewed.paymentAudit.map { it.payment })
        assertEquals(original.documentAttachments().toSet(), reviewed.documentAttachments().toSet())
        assertEquals(330_000L, reviewed.paymentBreakdown().total)
        assertEquals(330_000L, DisbursementTable(reviewed).total.paid)
        assertEquals(reviewed, ProjectCodec.decode(ProjectCodec.encode(reviewed)))
        assertEquals(330_000L, Balance(reviewed.mergeFrom(original).project).paid)
        val restored = reviewed.restoreReviewedPayment(reviewed.paymentAudit.first().id, date)
        assertEquals(660_000L, Balance(restored).paid)
        assertEquals(payments[1], restored.payments.last())
    }

    @Test fun editingAmountsCreatesAnAuditAndRequiresACompleteReviewedSelection() {
        val stage = Stage(name = "Serviço", budget = 40_000)
        val payment = Payment(targetId = stage.id, amount = 30_000, date = date, payer = "Pessoa A", method = "Pix", notes = "Manter")
        val original = Project(stages = listOf(stage), payments = listOf(payment))
        val changed = original.reviewPayments(CostKind.LABOR, stage.id, mapOf(payment.id to 20_000L), "Correção", date)
        assertEquals(payment.copy(amount = 20_000), changed.payments.single())
        assertEquals(payment, changed.paymentAudit.single().payment)
        assertFalse(changed.paymentAudit.single().voided)
        assertThrows(IllegalArgumentException::class.java) { original.reviewPayments(CostKind.LABOR, stage.id, emptyMap(), "Erro") }
        assertThrows(IllegalArgumentException::class.java) { original.reviewPayments(CostKind.LABOR, stage.id, mapOf(payment.id to 0L), "Erro") }
        assertEquals(original, original.reviewPayments(CostKind.LABOR, stage.id, mapOf(payment.id to 30_000L), ""))
    }

    @Test fun materialAndServiceUseTheSameStageOptionsAndLaborIsAService() {
        val stage = Stage(name = "Cobertura", budget = 50_000)
        val material = Purchase(name = "Perfis", stageId = stage.id)
        val service = Purchase(name = "Montagem", stageId = stage.id, nature = CostNature.SERVICE)
        val other = Purchase(name = "Sem vínculo")
        val project = Project(stages = listOf(stage), purchases = listOf(material, service, other))
        assertEquals(listOf("" to "Sem vínculo", stage.id to stage.name), project.stageOptions())
        assertEquals(listOf(material.id), project.linkedCostOptions(CostNature.MATERIAL, stage.id).map { it.id })
        assertEquals(setOf(stage.id, service.id), project.linkedCostOptions(CostNature.SERVICE, stage.id).map { it.id }.toSet())
        assertEquals(listOf(other.id), project.linkedCostOptions(CostNature.MATERIAL, "").map { it.id })
    }

    @Test fun tableSubtotalsReconcileAtEveryLayerAndWeekPagingIncludesEmptyWeeks() {
        val stage = Stage(name = "Etapa", budget = 30_000, dueDate = date.plusWeeks(30))
        val material = Purchase(name = "Material", total = 40_000, stageId = stage.id, dueDate = date.plusWeeks(2))
        val service = Purchase(name = "Serviço", total = 50_000, stageId = stage.id, nature = CostNature.SERVICE, dueDate = date)
        val project = Project(stages = listOf(stage), purchases = listOf(material, service), payments = listOf(
            Payment(kind = CostKind.MATERIAL, targetId = material.id, amount = 10_000, date = date), Payment(targetId = stage.id, amount = 5_000)))
        val model = DisbursementTable(project)
        fun verify(node: FlowNode) {
            if (node.children.isNotEmpty()) {
                assertEquals(node.paid, node.children.sumOf { it.paid })
                assertEquals(node.pending.known, node.children.sumOf { it.pending.known })
                node.children.forEach(::verify)
            }
        }
        model.roots.forEach(::verify)
        assertEquals(Balance(project).paid, model.total.paid)
        assertEquals(setOf("Material", "Serviço"), model.roots.map { it.label }.toSet())
        assertEquals(2, model.pageCount)
        assertEquals(26, model.columns(0).size)
        assertNull(model.columns(1).last())
        assertTrue(model.columns(0).filterNotNull().zipWithNext().all { (a,b) -> a.plusWeeks(1) == b })
        val allWeeks = (0 until model.pageCount).flatMap { model.columns(it) }
        assertEquals(120_000L, allWeeks.sumOf { model.total.amounts(it, CashView.TOTAL).known })
    }
}
