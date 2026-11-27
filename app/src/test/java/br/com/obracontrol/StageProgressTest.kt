package br.com.obracontrol

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StageProgressTest {
    private val today = LocalDate.of(2026, 11, 26)
    private val stage = Stage(name = "Revestimento", progress = 40, materialWeight = 50)
    private val tiles = Purchase(name = "Piso", total = 300_000, stageId = stage.id, receivedProgress = 100)
    private val grout = Purchase(name = "Rejunte", total = 100_000, stageId = stage.id, receivedProgress = 20)
    private val project = Project(stages = listOf(stage), purchases = listOf(tiles, grout))

    @Test fun separatesExecutionAndValueWeightedMaterialReceiptsToComposeTheStage() {
        val result = project.stageProgress(stage)
        assertEquals(40, result.execution)
        assertEquals(80.0, result.materials!!, 0.0001)
        assertEquals(60.0, result.general, 0.0001)
        assertEquals(1, result.receivedCount)
        assertEquals(2, result.materialCount)
        assertTrue(result.byValue)
        assertEquals(40.0, Balance(project).physicalProgress, 0.0001)
        assertEquals(60.0, project.generalStageProgress, 0.0001)
    }

    @Test fun linkedServicesEnterTheCombinedPercentageAndOtherStagesAndUnlinkedPurchasesStayOut() {
        val service = Purchase(name = "Serviço", nature = CostNature.SERVICE, stageId = stage.id, total = 900_000, receivedDate = today)
        val other = Stage(name = "Outra etapa", progress = 20)
        val unrelated = Purchase(name = "Outro material", total = 2_000_000, stageId = other.id, receivedProgress = 0)
        val unlinked = Purchase(name = "Sem etapa", total = 1_000_000, receivedProgress = 0)
        val next = project.copy(stages = listOf(stage, other), purchases = project.purchases + listOf(service, unrelated, unlinked))
        next.validate()
        assertEquals(1220.0 / 13.0, next.stageProgress(stage).materials!!, 0.0001)
        assertEquals((40.0 + 1220.0 / 13.0) / 2.0, next.stageProgress(stage).general, 0.0001)
        assertEquals(1, next.unlinkedMaterials)
        assertEquals(10.0, next.stageProgress(other).general, 0.0001)
        assertEquals(((40.0 + 1220.0 / 13.0) / 2.0 + 10.0) / 2.0, next.generalStageProgress, 0.0001)
    }

    @Test fun moneyAndPurchaseTermsDoNotCreateDeliveryOrExecutionProgress() {
        val initial = project.copy(purchases = project.purchases.map { it.copy(receivedProgress = null) })
        val paid = initial.copy(payments = listOf(Payment(kind = CostKind.MATERIAL, targetId = tiles.id, amount = 300_000, date = today),
            Payment(targetId = stage.id, amount = 100_000, date = today)))
        assertEquals(initial.stageProgress(stage), paid.stageProgress(stage))
        assertEquals(0.0, paid.stageProgress(stage).materials!!, 0.0001)
        assertEquals(20.0, paid.stageProgress(stage).general, 0.0001)
        assertEquals(400_000L, Balance(paid).paid)
    }

    @Test fun legacyReceiptDatesAreCompleteAndPartialReceiptsRemainPartial() {
        assertEquals(100, tiles.copy(receivedProgress = null, receivedDate = today).receivedPercent)
        assertEquals(100, tiles.copy(receivedProgress = 20, receivedDate = today).receivedPercent)
        assertEquals(20, grout.receivedPercent)
        assertEquals(0, grout.copy(receivedProgress = null).receivedPercent)
    }

    @Test fun missingOrZeroPricesUseAnExplicitEqualPurchaseAverage() {
        val missing = project.copy(purchases = listOf(tiles.copy(total = null), grout))
        val result = missing.stageProgress(stage)
        assertFalse(result.byValue)
        assertEquals(60.0, result.materials!!, 0.0001)
        assertEquals(50.0, result.general, 0.0001)
        assertTrue(result.basisText.contains("média"))
        assertEquals(result.materials, missing.copy(purchases = listOf(tiles.copy(total = 0), grout)).stageProgress(stage).materials)
    }

    @Test fun adjustableWeightsAndStagesWithoutMaterialsRespectTheirOwnScope() {
        assertEquals(56.0, project.stageProgress(stage.copy(materialWeight = 40)).general, 0.0001)
        assertEquals(40.0, project.stageProgress(stage.copy(materialWeight = 0)).general, 0.0001)
        assertEquals(80.0, project.stageProgress(stage.copy(materialWeight = 100)).general, 0.0001)
        val executionOnly = Project(stages = listOf(stage)).stageProgress(stage)
        assertNull(executionOnly.materials)
        assertEquals(0, executionOnly.materialWeight)
        assertEquals(40.0, executionOnly.general, 0.0001)
        assertEquals(0.0, Project().generalStageProgress, 0.0001)
        assertEquals(stage, project.stages.single())
        assertNull(stage.actualEnd)
    }

    @Test fun newFieldsRoundTripAndOldBackupsKeepProgressReceiptsPaymentsAndIds() {
        val encoded = ProjectCodec.encode(project)
        assertEquals(project, ProjectCodec.decode(encoded))
        val old = JSONObject(encoded).put("version", 11)
        old.getJSONArray("stages").getJSONObject(0).remove("materialWeight")
        val orders = old.getJSONArray("purchases")
        (0 until orders.length()).forEach { orders.getJSONObject(it).remove("receivedProgress") }
        orders.getJSONObject(0).put("receivedDate", today.dateText())
        val migrated = ProjectCodec.decode(old.toString())
        assertEquals(40, migrated.stages.single().progress)
        assertEquals(stage.id, migrated.stages.single().id)
        assertEquals(50, migrated.stages.single().materialWeight)
        assertEquals(tiles.id, migrated.purchases.first().id)
        assertNull(migrated.purchases.first().receivedProgress)
        assertEquals(100, migrated.purchases.first().receivedPercent)
        assertEquals(project.payments, migrated.payments)
        assertEquals(migrated, ProjectCodec.decode(ProjectCodec.encode(migrated)))
    }

    @Test fun mergingPreservesLocalProgressAndImportsNewRecordsWithTheirWeights() {
        val other = Stage(name = "Nova etapa", progress = 70, materialWeight = 30)
        val source = project.copy(stages = listOf(stage.copy(materialWeight = 10), other),
            purchases = listOf(tiles.copy(receivedProgress = 0), grout, Purchase(name = "Novo material", stageId = other.id, receivedProgress = 45)))
        val merged = project.mergeFrom(source).project
        assertEquals(stage, merged.stages.first())
        assertEquals(tiles, merged.purchases.first())
        assertEquals(30, merged.stages.last().materialWeight)
        assertEquals(45, merged.purchases.last().receivedProgress)
    }

    @Test fun invalidPercentagesAndFractionalBackupValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { stage.copy(materialWeight = 101).validate() }
        assertThrows(IllegalArgumentException::class.java) { stage.copy(materialWeight = -1).validate() }
        assertThrows(IllegalArgumentException::class.java) { project.copy(purchases = listOf(tiles.copy(receivedProgress = 101))).validate() }
        assertThrows(IllegalArgumentException::class.java) { project.copy(purchases = listOf(tiles.copy(receivedProgress = -1))).validate() }
        val invalid = JSONObject(ProjectCodec.encode(project))
        invalid.getJSONArray("stages").getJSONObject(0).put("materialWeight", 50.5)
        assertThrows(Exception::class.java) { ProjectCodec.decode(invalid.toString()) }
    }
}
