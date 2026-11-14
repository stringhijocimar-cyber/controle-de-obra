package br.com.obracontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class DocumentImportTest {
    @Test
    fun recognizesPurchaseMoneyWithoutImportingIdsTaxesOrDates() {
        val rows = listOf(
            "CNPJ 12.345.678/0001-95", "Emissão 14/11/2026", "Quantidade 12,99",
            "Valor total dos tributos R$ 18,17", "ICMS 36,00", "Subtotal   R$ 3.309,40",
            "Desconto  -9,40", "VALOR TOTAL DA NOTA", "R$ 3.300,00", "Troco R$ 5,00",
        )
        val amounts = sortedAmounts(recognizeAmounts(rows, 2, RecognitionScope.PURCHASE))
        assertEquals(listOf(
            MoneyCandidate(330000, MoneyField.TOTAL, 2),
            MoneyCandidate(940, MoneyField.DISCOUNT, 2),
            MoneyCandidate(330940, MoneyField.SUBTOTAL, 2),
        ), amounts)
    }

    @Test
    fun readsPixAndDifferentMonetarySeparators() {
        val rows = listOf("Comprovante Pix", "Valor da transação", "R$ 1 018,60", "Agência 0001", "CPF 111.222.333-44")
        assertEquals(listOf(MoneyCandidate(101860, MoneyField.TOTAL, 1)), recognizeAmounts(rows, 1, RecognitionScope.PAYMENT))
        val decimal = recognizeAmounts(listOf("TOTAL GERAL BRL 1,234.56", "DESCONTO R$ 9,50"), 1, RecognitionScope.PURCHASE)
        assertEquals(listOf(123456L, 950L), decimal.map { it.cents })
        assertTrue(recognizeAmounts(listOf("Emissão 12/11/2026", "Documento 0000631", "Piso 30 x 30"), 1, RecognitionScope.PURCHASE).isEmpty())
    }

    @Test
    fun keepsAttachmentsInVersionThreeAndAcceptsEarlierBackups() {
        val pdf = DocumentAttachment("a".repeat(64), "Orcamento.pdf", DocumentType.PDF, 550)
        val purchase = Purchase(name = "Piso", documents = listOf(pdf))
        val project = Project(purchases = listOf(purchase), payments = listOf(Payment(kind = CostKind.MATERIAL,
            targetId = purchase.id, amount = 100, proofs = listOf(pdf))))
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(project)))
        val v2 = """{"version":2,"project":"Anterior","stages":[],"purchases":[],"payments":[],"issues":[]}"""
        assertEquals(Project(name = "Anterior"), ProjectCodec.decode(v2))
        val invalid = project.copy(purchases = listOf(purchase.copy(documents = listOf(pdf.copy(name = "../arquivo.pdf")))))
        assertThrows(IllegalArgumentException::class.java) { ProjectCodec.decode(ProjectCodec.encode(invalid)) }
    }

    @Test
    fun mergeKeepsEditsAndDoesNotDuplicateInstallmentsOrRepeatedLines() {
        val one = PurchaseLine(id = "l1", name = "Conexão", quantity = BigDecimal("1"), unitPrice = 400)
        val second = one.copy(id = "l2")
        val order = Purchase(id = "compra", name = "Hidráulica", total = 50000, lines = listOf(one, second))
        val payment = Payment(id = "p1", kind = CostKind.MATERIAL, targetId = order.id, amount = 20000)
        val incoming = Project(name = "Área Gourmet", purchases = listOf(order), payments = listOf(payment, payment.copy(id = "p2")))
        val previous = incoming.copy(purchases = listOf(order.copy(total = 55000, lines = listOf(one))), payments = listOf(payment))
        val merged = previous.mergeFrom(incoming)
        assertEquals(2, merged.added)
        assertEquals(2, merged.project.purchases.single().lines.size)
        assertEquals(55000L, merged.project.purchases.single().total)
        assertEquals(40000L, Balance(merged.project).paid)
        assertEquals(0, merged.project.mergeFrom(incoming).added)
        assertFalse(incoming.isSameWork(Project(name = "Outra obra")))
    }
}
