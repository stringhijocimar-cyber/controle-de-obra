package br.com.obracontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableTotalsTest {
    @Test fun knownCostsUseCentsAndPreserveCredits() {
        assertEquals(TableAmountTotal(110_001, 3, 0), tableAmountTotal(listOf(100_000L, 20_001L, -10_000L)))
    }

    @Test fun unpricedRecordsMakeTheSumPartial() {
        val total = tableAmountTotal(listOf(10_000L, null, 0L))
        assertEquals(TableAmountTotal(10_000, 2, 1), total)
        assertTrue(total.partial)
        assertFalse(total.entirelyUndefined)
    }

    @Test fun noKnownCostIsDifferentFromAnEmptyTable() {
        assertTrue(tableAmountTotal(listOf(null, null)).entirelyUndefined)
        val empty = tableAmountTotal(emptyList())
        assertEquals(TableAmountTotal(0, 0, 0), empty)
        assertFalse(empty.entirelyUndefined)
    }

    @Test fun paidTotalsIncludePaymentsAgainstAnUnpricedCost() {
        val costs = listOf(10_000L, null)
        val paid = listOf(4_000L, 2_000L)
        val balances = costs.zip(paid).map { (cost, payment) -> cost?.minus(payment) }
        assertEquals(TableAmountTotal(10_000, 1, 1), tableAmountTotal(costs))
        assertEquals(TableAmountTotal(6_000, 2, 0), tableAmountTotal(paid))
        assertEquals(TableAmountTotal(6_000, 1, 1), tableAmountTotal(balances))
    }
}
