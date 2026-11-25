package br.com.obracontrol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CostNatureTest {
    @Test fun olderBackupsKeepTheirRecordsAndDefaultPurchasesToMaterial() {
        val order = Purchase(name = "Piso", total = 100_000, installmentCount = 3)
        val payment = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 25_000, payer = "Pessoa")
        val original = Project(purchases = listOf(order), payments = listOf(payment))
        val json = JSONObject(ProjectCodec.encode(original)).put("version", 6)
        json.getJSONArray("purchases").getJSONObject(0).remove("nature")
        assertEquals(original, ProjectCodec.decode(json.toString()))
    }

    @Test fun serviceClassificationSurvivesExportAndDoesNotChangeFinancialBalances() {
        val stage = Stage(name = "Etapa", budget = 50_000)
        val order = Purchase(name = "Instalação", total = 100_000, installmentCount = 2)
        val original = Project(stages = listOf(stage), purchases = listOf(order),
            payments = listOf(Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 25_000)))
        val service = original.copy(purchases = listOf(order.copy(nature = CostNature.SERVICE)))
        assertEquals(7, JSONObject(ProjectCodec.encode(service)).getInt("version"))
        assertEquals(service, ProjectCodec.decode(ProjectCodec.encode(service)))
        assertEquals(Balance(original).total, Balance(service).total)
        assertEquals(Balance(original).paid, Balance(service).paid)
        assertEquals(Balance(original).remaining, Balance(service).remaining)
        assertEquals(original.payments, service.payments)
        assertEquals(CostNature.SERVICE, service.costNature(CostKind.MATERIAL, order.id))
        assertEquals(CostNature.SERVICE, service.costNature(CostKind.LABOR, stage.id))
        assertEquals(CostNature.MATERIAL, original.costNature(CostKind.MATERIAL, order.id))
    }

    @Test fun mergePreservesTheClassificationAlreadyEditedOnTheDevice() {
        val order = Purchase(name = "Serviço", nature = CostNature.SERVICE)
        val local = Project(name = "Obra", purchases = listOf(order))
        val incoming = local.copy(purchases = listOf(order.copy(nature = CostNature.MATERIAL)))
        assertEquals(CostNature.SERVICE, local.mergeFrom(incoming).project.purchases.single().nature)
        assertEquals(0, local.mergeFrom(incoming).added)
    }

    @Test fun invalidClassificationIsRejectedInsteadOfChangingItsMeaning() {
        val json = JSONObject(ProjectCodec.encode(Project(purchases = listOf(Purchase(name = "Compra")))))
        json.getJSONArray("purchases").getJSONObject(0).put("nature", "Inválido")
        assertThrows(IllegalArgumentException::class.java) { ProjectCodec.decode(json.toString()) }
    }
}
