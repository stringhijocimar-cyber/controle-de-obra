package br.com.obracontrol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PurchaseRegistrationTest {
    @Test
    fun recordsPurchaseAndPaymentTogetherAndKeepsOtherWorkData() {
        val day = LocalDate.of(2026, 11, 18)
        val stage = Stage(name = "Cobertura", budget = 100_000, plannedEnd = day)
        val older = Purchase(name = "Concreto", total = 30_000)
        val original = Project(name = "Obra teste", stages = listOf(stage), purchases = listOf(older),
            payments = listOf(Payment(targetId = stage.id, amount = 10_000, payer = "Pessoa anterior")),
            diaryEntries = listOf(DiaryEntry(title = "Vistoria", stageId = stage.id)), notes = "Observações existentes")
        val purchase = Purchase(name = "Piso", total = 120_000, payer = "Pagador de teste", method = "Pix")
        val payment = Payment(kind = CostKind.MATERIAL, targetId = purchase.id, amount = 40_000, date = day,
            payer = purchase.payer, method = purchase.method)

        val updated = original.recordPurchase(purchase, payment)
        assertEquals(original.stages, updated.stages)
        assertEquals(original.diaryEntries, updated.diaryEntries)
        assertEquals(original.notes, updated.notes)
        assertEquals(older, updated.purchases.first())
        assertEquals(original.payments.first(), updated.payments.first())
        assertEquals(40_000L, Balance(updated).materialPaid)
        assertEquals(80_000L, Balance(updated).materialOutstanding(purchase))
        assertEquals(updated, updated.recordPurchase(purchase, payment))
        assertEquals(2, updated.payments.size)
        assertThrows(IllegalArgumentException::class.java) {
            original.recordPurchase(purchase, payment.copy(targetId = stage.id))
        }
    }

    @Test
    fun savedPayersRemainAvailableAfterRecordsAreDeletedAndExistingNamesAreSuggested() {
        val order = Purchase(name = "Material", payer = "Pessoa da compra")
        val payment = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 100, payer = "Pessoa do pagamento")
        val project = Project(purchases = listOf(order), payments = listOf(payment))
        val registered = project.registerPayer("  Novo pagador  ").registerPayer("novo PAGADOR")

        assertEquals(listOf("Novo pagador", "Pessoa da compra", "Pessoa do pagamento"), registered.payerOptions)
        assertEquals(listOf("Novo pagador"), registered.copy(purchases = emptyList(), payments = emptyList()).payerOptions)
        assertThrows(IllegalArgumentException::class.java) { registered.registerPayer("  ") }
        assertFalse(project.payerNames.contains("Novo pagador"))
    }

    @Test
    fun readsVersionFourWithoutChangingAmountsDatesAttachmentsOrPayments() {
        val proof = DocumentAttachment("c".repeat(64), "comprovante.pdf", DocumentType.PDF, 100)
        val order = Purchase(name = "Compra anterior", total = 100_000, documents = listOf(proof))
        val previous = Project(name = "Obra existente", purchases = listOf(order),
            payments = listOf(Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 25_000,
                date = LocalDate.of(2026, 11, 16), payer = "Pessoa anterior", method = "Cartão", proofs = listOf(proof))))
        val json = JSONObject(ProjectCodec.encode(previous)).put("version", 4)
        json.remove("payerNames")
        json.getJSONArray("purchases").getJSONObject(0).apply { remove("payer"); remove("method") }
        val restored = ProjectCodec.decode(json.toString())

        assertEquals(previous, restored)
        assertEquals(listOf("Pessoa anterior"), restored.payerOptions)
        val next = restored.recordPurchase(order.copy(payer = "Novo pagador", method = "Pix"))
        assertEquals(next, ProjectCodec.decode(ProjectCodec.encode(next)))
        assertEquals(previous.payments, next.payments)
        assertEquals(Balance(previous).paid, Balance(next).paid)
    }

    @Test
    fun mergePreservesLocalEditsAndCombinesPayerRegistration() {
        val purchase = Purchase(name = "Piso", total = 100_000)
        val local = Project(name = "Obra teste", purchases = listOf(purchase), payerNames = listOf("Pessoa local"))
        val incoming = local.copy(purchases = listOf(purchase.copy(total = 110_000, payer = "Pessoa importada", method = "Pix")),
            payerNames = listOf("pessoa LOCAL", "Pessoa importada"))
        val merged = local.mergeFrom(incoming).project

        assertEquals(100_000L, merged.purchases.single().total)
        assertEquals("Pix", merged.purchases.single().method)
        assertEquals("Pessoa importada", merged.purchases.single().payer)
        assertEquals(listOf("Pessoa importada", "Pessoa local"), merged.payerOptions)
        assertEquals(0, merged.mergeFrom(incoming).added)
        assertTrue(merged.payments.isEmpty())
    }
}
