package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test

class InstallmentBalanceTest {
    @Test
    fun declaringInstallmentsDoesNotPayAndPartialPaymentsReduceOnlyTheirOwnBalance() {
        val purchase = Purchase(name = "Piso", total = 120_000, installmentCount = 3)
        val other = Purchase(name = "Forro", total = 200_000, installmentCount = 4)
        val stage = Stage(name = "Montagem", budget = 60_000)
        val original = Project(purchases = listOf(purchase, other), stages = listOf(stage))
        assertEquals(0L, original.installmentBalances().sumOf { it.paid })
        assertEquals(320_000L, original.installmentBalances().sumOf { it.remaining!! })

        val payment = Payment(kind = CostKind.MATERIAL, targetId = purchase.id, amount = 40_000)
        val updated = original.recordPurchase(purchase, payment).copy(payments = listOf(payment,
            Payment(targetId = stage.id, amount = 10_000)))
        val positions = updated.installmentBalances()
        assertEquals(40_000L, positions.first().paid)
        assertEquals(80_000L, positions.first().remaining)
        assertEquals(0L, positions.last().paid)
        assertEquals(200_000L, positions.last().remaining)
        assertEquals(updated, ProjectCodec.decode(ProjectCodec.encode(updated)))
        assertEquals(positions, updated.recordPurchase(purchase).installmentBalances())
        assertEquals(positions, updated.recordPurchase(purchase, payment).installmentBalances())
    }

    @Test
    fun unknownTotalDoesNotPretendToBeSettledAndSinglePaymentsAreExcluded() {
        val unknown = Purchase(name = "Marcenaria", installmentCount = 5)
        val single = Purchase(name = "Material à vista", total = 20_000, installmentCount = 1)
        val unspecified = Purchase(name = "Sem condição", total = 10_000)
        val project = Project(purchases = listOf(unknown, single, unspecified), payments = listOf(
            Payment(kind = CostKind.MATERIAL, targetId = unknown.id, amount = 5_000)))
        val position = project.installmentBalances().single()
        assertEquals(unknown, position.purchase)
        assertEquals(5_000L, position.paid)
        assertNull(position.remaining)
        assertNull(position.credit)
        assertTrue(tableAmountTotal(listOf(position.remaining)).entirelyUndefined)
    }

    @Test
    fun overpaymentsDoNotSettleAnotherPurchaseAndItemTotalsKeepTheirCents() {
        val overpaid = Purchase(name = "Projeto", total = 10_001, installmentCount = 3)
        val pending = Purchase(name = "Obra", total = 20_000, installmentCount = 2)
        val project = Project(purchases = listOf(overpaid, pending), payments = listOf(
            Payment(kind = CostKind.MATERIAL, targetId = overpaid.id, amount = 12_000)))
        val positions = project.installmentBalances()
        assertEquals(0L, positions.first().remaining)
        assertEquals(1_999L, positions.first().credit)
        assertEquals(20_000L, positions.sumOf { it.remaining!! })
        assertEquals(30_001L, positions.sumOf { it.total!! })
    }
}
