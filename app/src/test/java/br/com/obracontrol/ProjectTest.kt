package br.com.obracontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ProjectTest {
    @Test
    fun brazilianCurrencyAndInvalidInputs() {
        assertEquals(123456L, parseMoney("R$ 1.234,56"))
        assertEquals(123400L, parseMoney("1.234"))
        assertEquals(100001L, parseMoney("1000.01"))
        assertNull(parseMoney(""))
        assertThrows(IllegalArgumentException::class.java) { parseMoney("12,345") }
        assertThrows(IllegalArgumentException::class.java) { parseDate("29/02/2026") }
    }

    @Test
    fun paymentsAreSummedWithoutDuplicatingTheLaborContract() {
        val stage = Stage(name = "Base", budget = 40000, progress = 50)
        val known = Purchase(name = "Areia", supplier = "Loja", total = 18000, extra = true)
        val unknown = Purchase(name = "Piso a cotar")
        val project = Project(laborBudget = 50000, stages = listOf(stage), purchases = listOf(known, unknown), payments = listOf(
            Payment(kind = CostKind.LABOR, targetId = stage.id, amount = 10000),
            Payment(kind = CostKind.LABOR, targetId = stage.id, amount = 12000),
            Payment(kind = CostKind.MATERIAL, targetId = known.id, amount = 8000),
            Payment(kind = CostKind.MATERIAL, targetId = unknown.id, amount = 4000),
        ))
        val balance = Balance(project)
        assertEquals(72000L, balance.total)
        assertEquals(34000L, balance.paid)
        assertEquals(38000L, balance.remaining)
        assertEquals(10000L, balance.materialToPay)
        assertEquals(18000L, balance.extras)
        assertEquals("Parcialmente pago", balance.purchaseStatus(known))
        assertNull(balance.materialOutstanding(unknown))
        assertEquals(50.0, balance.physicalProgress, 0.001)
    }

    @Test
    fun purchaseReconciliationKeepsDocumentTotalAndHandlesItemDiscounts() {
        val line = PurchaseLine(name = "Areia", quantity = BigDecimal("1.245"), unitPrice = 950)
        val order = Purchase(name = "Compra", lines = listOf(line), total = 1200, discount = 75)
        assertEquals(1183L, line.subtotal)
        assertEquals(1108L, order.lineNet)
        assertEquals(1200L, order.forecast)
        assertEquals(92L, order.variance)
        assertEquals(1108L, order.copy(total = null).forecast)
        assertNull(order.copy(total = null, lines = listOf(line, PurchaseLine(name = "Sem cotação"))).forecast)
    }

    @Test
    fun legacyBackupCreatesPaymentsAndRoundTripsWithoutChangingTotals() {
        val original = ProjectCodec.decode("""
            {
                "project":"Obra teste", "start":"03/01/2026", "laborBudget":2000,
                "stages":[{"name":"Base","budget":1000,"paid":400,"completed":true,
                    "start":"03/01/2026","end":"08/01/2026","paymentDate":"04/01/2026"}],
                "materials":[{"name":"Areia","value":300.45,"paid":true,"method":"PIX",
                    "items":[{"name":"Areia","quantity":1.5,"unit":"m³","unitPrice":200.3,"total":300.45}]}]
            }
        """.trimIndent())
        assertEquals(2, original.payments.size)
        assertEquals(LocalDate.of(2026, 1, 8), original.stages.single().actualEnd)
        assertEquals(LocalDate.of(2026, 1, 4), original.payments.first().date)
        assertEquals(70045L, Balance(original).paid)
        assertEquals(160000L, Balance(original).remaining)
        assertEquals(original, ProjectCodec.decode(ProjectCodec.encode(original)))
    }

    @Test
    fun malformedBackupAndOrphanedPaymentsCannotReplaceProject() {
        assertThrows(Exception::class.java) { ProjectCodec.decode("{}") }
        assertThrows(IllegalArgumentException::class.java) {
            Project(payments = listOf(Payment(amount = 1))).validate()
        }
        assertTrue(ProjectCodec.decode(ProjectCodec.encode(Project())).stages.isEmpty())
    }
}
