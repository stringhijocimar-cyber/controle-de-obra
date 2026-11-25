package br.com.obracontrol

import org.junit.Assert.*
import org.junit.Test

class PaymentBreakdownTest {
    @Test
    fun bothChartsReconcileActualPaymentsAcrossStagesAndPurchasesAndIgnoreFutureInstallments() {
        val stage = Stage(name = "Montagem", budget = 90_000)
        val order = Purchase(name = "Materiais", total = 100_000, installmentCount = 5)
        val future = Purchase(name = "Compra futura", total = 900_000, installmentCount = 6,
            payer = "Pessoa planejada", method = "Boleto")
        val project = Project(stages = listOf(stage), purchases = listOf(order, future), payments = listOf(
            Payment(targetId = stage.id, amount = 40_000, payer = "Pessoa A", method = "Pix"),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 10_000, payer = "Pessoa A", method = "Cartão de crédito"),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 30_000, payer = "Pessoa B", method = "pix"),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 20_000, payer = "Pessoa B", method = "Dinheiro")))
        val charts = project.paymentBreakdown()
        assertEquals(listOf(PaymentShare("Pessoa A", 50_000), PaymentShare("Pessoa B", 50_000)), charts.byPayer)
        assertEquals(listOf(PaymentShare("Pix", 70_000), PaymentShare("Dinheiro", 20_000), PaymentShare("Cartão de crédito", 10_000)), charts.byMethod)
        assertEquals(Balance(project).paid, charts.total)
        assertEquals(charts.total, charts.byMethod.sumOf { it.amount })
        assertFalse(charts.byPayer.any { it.label == future.payer })
        assertFalse(charts.byMethod.any { it.label == future.method })
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(project)))
    }

    @Test
    fun normalizesLegacyLabelsAndKeepsUnknownPayersAndMethodsInTheTotal() {
        val order = Purchase(name = "Projeto", total = 100_000)
        val project = Project(purchases = listOf(order), payerNames = listOf("Pessoa A"), payments = listOf(
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 1_000, payer = "  pessoa   a  ", method = " PIX "),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 2_000, payer = "PESSOA A", method = "pix"),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 3_000, payer = "", method = ""),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 4_000, payer = "Pessoa B", method = "Cartao de credito"),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 5_000, payer = "Pessoa B", method = "Cartão de crédito"),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 6_000, payer = "Pessoa B", method = "CARTÃO")))
        val charts = project.paymentBreakdown()
        assertEquals(3_000L, charts.byPayer.single { it.label == "Pessoa A" }.amount)
        assertEquals(3_000L, charts.byPayer.single { it.label == UNREPORTED }.amount)
        assertEquals(3_000L, charts.byMethod.single { it.label == "Pix" }.amount)
        assertEquals(9_000L, charts.byMethod.single { it.label == "Cartão de crédito" }.amount)
        assertEquals(6_000L, charts.byMethod.single { it.label == "Cartão" }.amount)
        assertEquals(3_000L, charts.byMethod.single { it.label == UNREPORTED }.amount)
        assertEquals(21_000L, charts.total)
        assertEquals(charts.total, charts.byMethod.sumOf { it.amount })
    }

    @Test
    fun editsAndDeletionsRecalculateWithoutPersistingDerivedChartData() {
        val order = Purchase(name = "Piso", total = 12_000, installmentCount = 3)
        val payment = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 4_000, payer = "Pessoa A", method = "Pix")
        val project = Project(purchases = listOf(order), payments = listOf(payment))
        val edited = project.copy(payments = listOf(payment.copy(amount = 5_000, payer = "Pessoa B", method = "Dinheiro")))
        assertEquals(listOf(PaymentShare("Pessoa B", 5_000)), edited.paymentBreakdown().byPayer)
        assertEquals(listOf(PaymentShare("Dinheiro", 5_000)), edited.paymentBreakdown().byMethod)
        assertEquals(edited.paymentBreakdown(), ProjectCodec.decode(ProjectCodec.encode(edited)).paymentBreakdown())
        val empty = edited.copy(payments = emptyList()).paymentBreakdown()
        assertEquals(0L, empty.total)
        assertTrue(empty.byPayer.isEmpty())
        assertTrue(empty.byMethod.isEmpty())
    }
}
