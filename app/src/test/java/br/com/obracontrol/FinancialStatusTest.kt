package br.com.obracontrol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class FinancialStatusTest {
    private val today = LocalDate.of(2026, 11, 25)

    @Test
    fun colorsReflectMoneyPaidAndConfirmedDueDatesIndependentlyOfWorkProgress() {
        assertEquals(FinancialStatus.PAID, financialStatus(10_000, 10_000, today.minusDays(1), today))
        assertEquals(FinancialStatus.PARTIAL, financialStatus(10_000, 4_000, today, today))
        assertEquals(FinancialStatus.PENDING, financialStatus(10_000, 0, null, today))
        assertEquals(FinancialStatus.OVERDUE, financialStatus(10_000, 0, today.minusDays(1), today))
        assertEquals(FinancialStatus.PARTIAL_OVERDUE, financialStatus(10_000, 4_000, today.minusDays(1), today))
        assertEquals(FinancialStatus.OVERPAID, financialStatus(10_000, 11_000, null, today))
        assertEquals(FinancialStatus.UNPRICED, financialStatus(null, 0, null, today))
        assertEquals(FinancialStatus.PAID_UNPRICED, financialStatus(null, 500, null, today))
        assertEquals(FinancialStatus.NO_COST, financialStatus(0, 0, null, today))
    }

    @Test
    fun installmentAmountsPreserveTheTotalIncludingRoundingCents() {
        assertEquals(UNREPORTED, Purchase(name = "Antiga").installmentText())
        assertEquals("À vista", Purchase(name = "À vista", installmentCount = 1).installmentText())
        assertEquals("3x de ${40_000L.brl()}",
            Purchase(name = "Parcelada", total = 120_000, installmentCount = 3).installmentText())
        assertEquals("3x\n1 de ${3_334L.brl()} + 2 de ${3_333L.brl()}",
            Purchase(name = "Ajuste", total = 10_000, installmentCount = 3).installmentText())
        assertEquals("4x · valor a definir", Purchase(name = "Cotação", installmentCount = 4).installmentText())
        assertThrows(IllegalArgumentException::class.java) {
            Project(purchases = listOf(Purchase(name = "Inválida", installmentCount = 0))).validate()
        }
    }

    @Test
    fun versionFiveKeepsEveryExistingPaymentAndLeavesUnknownInstallmentsOpen() {
        val proof = DocumentAttachment("d".repeat(64), "nota.pdf", DocumentType.PDF, 100)
        val purchase = Purchase(name = "Compra antiga", total = 120_000, payer = "Pessoa anterior",
            method = "Cartão", documents = listOf(proof))
        val previous = Project(purchases = listOf(purchase), payments = listOf(
            Payment(kind = CostKind.MATERIAL, targetId = purchase.id, amount = 40_000, payer = "Pessoa anterior"),
            Payment(kind = CostKind.MATERIAL, targetId = purchase.id, amount = 20_000, proofs = listOf(proof)),
        ))
        val legacy = JSONObject(ProjectCodec.encode(previous)).put("version", 5)
        legacy.getJSONArray("purchases").getJSONObject(0).remove("installmentCount")
        val loaded = ProjectCodec.decode(legacy.toString())
        assertEquals(previous, loaded)
        assertNull(loaded.purchases.single().installmentCount)
        val updated = loaded.copy(purchases = listOf(purchase.copy(installmentCount = 3)))
        assertEquals(updated, ProjectCodec.decode(ProjectCodec.encode(updated)))
        assertEquals(Balance(previous).total, Balance(updated).total)
        assertEquals(Balance(previous).paid, Balance(updated).paid)
        assertEquals(previous.documentAttachments(), updated.documentAttachments())
    }

    @Test
    fun mergingFillsMissingInstallmentsAndKeepsTheLocalPaymentTerms() {
        val purchase = Purchase(name = "Material", total = 100_000)
        val local = Project(name = "Mesma obra", purchases = listOf(purchase))
        val imported = local.copy(purchases = listOf(purchase.copy(installmentCount = 3)))
        val merged = local.mergeFrom(imported).project
        assertEquals(3, merged.purchases.single().installmentCount)
        assertEquals(0, merged.mergeFrom(imported).added)
        val edited = merged.copy(purchases = listOf(purchase.copy(installmentCount = 4)))
        assertEquals(4, edited.mergeFrom(imported).project.purchases.single().installmentCount)
    }
}
