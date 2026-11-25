package br.com.obracontrol

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class WorkPlanningTest {
    private val date = LocalDate.of(2026, 11, 25)
    private fun priced(name: String, category: CostCategory, price: Long, quantity: String = "1") = PlanInput(
        name = name, category = category, quantity = BigDecimal(quantity), unit = "m²", unitPrice = price,
        priceSource = "Cotação de exemplo · MG", priceDate = date, estimated = true,
    )
    private fun sample(): WorkPlan = WorkPlan(title = "Revestimento de exemplo", location = "Cidade / MG", created = date,
        stages = listOf(PlanStage(name = "Assentamento", services = listOf(PlanService(name = "Piso",
            items = listOf(priced("Piso", CostCategory.MATERIAL, 5_000, "2.5"),
                priced("Assentamento", CostCategory.LABOR, 3_000, "2"), priced("Frete", CostCategory.SERVICE, 2_000)))))))
    private fun approved(plan: WorkPlan = sample()) = Project(name = "Minha obra", workPlans = listOf(plan))
        .approvePlanStage(plan.id, plan.stages.single().id, "Responsável", date, "Aprovação de teste")

    @Test fun templatesCoverAllProjectTypesAndNeverInventPricesOrStructuralQuantities() {
        WorkPlanType.entries.forEach { type ->
            val plan = createWorkPlan(type, "Teste", "Escopo", "Cidade / MG", BigDecimal("20"), today = date)
            plan.validate()
            assertTrue(plan.stages.all { it.items.isNotEmpty() })
            assertTrue(plan.stages.flatMap { it.items }.all { it.unitPrice == null && it.subtotal == null })
            assertTrue(plan.stages.none { it.approved })
        }
        val cladding = createWorkPlan(WorkPlanType.CLADDING, "Piso", "", "", BigDecimal("20"), BigDecimal("12.5"), date)
        val tile = cladding.stages.flatMap { it.items }.single { it.name == "Revestimento com perda estimada" }
        assertEquals(0, BigDecimal("22.5").compareTo(tile.quantity))
        assertNull(cladding.stages.flatMap { it.items }.single { it.name == "Argamassa de assentamento" }.quantity)
        val home = createWorkPlan(WorkPlanType.NEW_HOME, "Casa", "", "", BigDecimal("100"), today = date)
        assertTrue(home.stages.single { it.name == "Estrutura" }.items.all { it.quantity == null || it.category == CostCategory.SERVICE })
    }

    @Test fun incompleteInputsAndEmptyServicesBlockApprovalWithoutChangingActualLedger() {
        val plan = sample()
        val stage = plan.stages.single()
        val incomplete = stage.copy(services = stage.services.map { it.copy(items = it.items.map { input -> input.copy(priceSource = "") }) })
        val project = Project(workPlans = listOf(plan.copy(stages = listOf(incomplete))))
        assertThrows(IllegalArgumentException::class.java) { project.approvePlanStage(plan.id, stage.id, "A", date, "") }
        val empty = stage.copy(services = stage.services + PlanService(name = "Ainda sem insumos"))
        assertThrows(IllegalArgumentException::class.java) { Project(workPlans = listOf(plan.copy(stages = listOf(empty))))
            .approvePlanStage(plan.id, stage.id, "A", date, "") }
        assertTrue(project.payments.isEmpty() && project.purchases.isEmpty() && project.stages.isEmpty())
    }

    @Test fun approvalFreezesInputsAndRevisionKeepsOldBaselineUntilExplicitReapproval() {
        val first = approved()
        val plan = first.workPlans.single()
        val stage = plan.stages.single()
        assertEquals(20_500L, stage.latestApproval!!.total)
        assertTrue(stage.approved)
        val revised = first.editPlanStage(plan.id, stage.id) { old -> old.copy(services = old.services.map { service ->
            service.copy(items = service.items.map { if (it.category == CostCategory.MATERIAL) it.copy(unitPrice = 6_000) else it }) }) }
        val next = revised.workPlans.single().stages.single()
        assertFalse(next.approved)
        assertEquals("Revisão pendente", next.status)
        assertEquals(20_500L, next.latestApproval!!.total)
        assertEquals(23_000L, next.knownTotal)
        val second = revised.approvePlanStage(plan.id, stage.id, "Outro responsável", date.plusDays(1), "Revisão")
        assertEquals(listOf(20_500L, 23_000L), second.workPlans.single().stages.single().approvals.map { it.total })
        assertEquals(first.payments, second.payments)
        assertThrows(IllegalArgumentException::class.java) { second.approvePlanStage(plan.id, stage.id, "A", date, "") }
    }

    @Test fun activationCreatesExpectedCostsOnceAndDoesNotCountLaborTwiceOrInventPayments() {
        val base = approved()
        val plan = base.workPlans.single()
        val stage = plan.stages.single()
        val active = base.activatePlanStage(plan.id, stage.id)
        assertEquals(1, active.stages.size)
        assertEquals(6_000L, active.stages.single().budget)
        assertEquals(2, active.purchases.size)
        assertEquals(setOf(CostNature.MATERIAL, CostNature.SERVICE), active.purchases.map { it.nature }.toSet())
        assertEquals(20_500L, Balance(active).total)
        assertTrue(active.payments.isEmpty() && active.scheduledPayments.isEmpty())
        assertEquals(active, active.activatePlanStage(plan.id, stage.id))
        val linked = active.workPlans.single().stages.single()
        assertEquals(20_500L, active.planControl(linked)!!.forecast)
        val payment = Payment(targetId = active.stages.single().id, amount = 2_000, date = date)
        val paid = active.copy(payments = listOf(payment), purchases = active.purchases.map { it.copy(total = it.total!! + 500) })
        assertEquals(21_500L, paid.planControl(linked)!!.forecast)
        assertEquals(2_000L, paid.planControl(linked)!!.paid)
        assertEquals(20_500L, linked.latestApproval!!.total)
        assertEquals(paid, ProjectCodec.decode(ProjectCodec.encode(paid)))
    }

    @Test fun bindingToExistingStagePreservesEveryCostPaymentAndGlobalLaborBudget() {
        val existing = Stage(name = "Etapa existente", budget = 40_000, progress = 35)
        val purchase = Purchase(name = "Compra existente", total = 50_000, stageId = existing.id)
        val payment = Payment(targetId = existing.id, amount = 10_000, date = date)
        val base = approved().copy(stages = listOf(existing), purchases = listOf(purchase), payments = listOf(payment), laborBudget = 70_000)
        val plan = base.workPlans.single()
        val linked = base.activatePlanStage(plan.id, plan.stages.single().id, existing.id)
        assertEquals(base.stages, linked.stages)
        assertEquals(base.purchases, linked.purchases)
        assertEquals(base.payments, linked.payments)
        assertEquals(70_000L, linked.laborBudget)
        assertEquals(90_000L, linked.planControl(linked.workPlans.single().stages.single())!!.forecast)
        val extra = base.activatePlanStage(plan.id, plan.stages.single().id, addToLaborContract = true)
        assertEquals(76_000L, extra.laborBudget)
    }

    @Test fun backupReadsVersionEightAndRoundTripsFullPlanningHistoryAndLinksInVersionNine() {
        val stage = Stage(name = "Antiga", budget = 10_000)
        val old = Project(stages = listOf(stage), payments = listOf(Payment(targetId = stage.id, amount = 5_000, date = date)))
        val json = JSONObject(ProjectCodec.encode(old)).put("version", 8).apply { remove("workPlans") }.toString()
        assertEquals(old, ProjectCodec.decode(json))
        val current = approved().let { p -> p.activatePlanStage(p.workPlans.single().id, p.workPlans.single().stages.single().id) }
        assertEquals(9, JSONObject(ProjectCodec.encode(current)).getInt("version"))
        assertEquals(current, ProjectCodec.decode(ProjectCodec.encode(current)))
        val changed = current.workPlans.single().stages.single().let { s -> s.copy(services = s.services.map { service ->
            service.copy(items = service.items.map { it.copy(unitPrice = 1L) }) }) }
        assertThrows(IllegalArgumentException::class.java) { current.copy(workPlans = listOf(current.workPlans.single().copy(stages = listOf(changed)))).validate() }
    }

    @Test fun aiImportAcceptsDraftsOnlyAndRepeatImportDoesNotDuplicateOrOverrideLocalEdits() {
        val plan = sample()
        val root = WorkPlanCodec.encode(listOf(plan)).getJSONObject(0).put("format", "obracontrol-planning-v1")
        root.getJSONArray("stages").getJSONObject(0).put("linkedStageId", "forged-stage").put("revision", 90)
            .put("approvals", JSONArray().put(JSONObject().put("approved", true)))
        val json = root.toString(2)
        val imported = WorkPlanCodec.decodeAi("```json\n$json\n```")
        assertEquals(WorkPlanType.CUSTOM, imported.type)
        assertFalse(imported.stages.single().approved)
        assertNull(imported.stages.single().linkedStageId)
        assertEquals(1, imported.stages.single().revision)
        val first = Project().addWorkPlan(imported)
        val edited = first.editPlanStage(imported.id, imported.stages.single().id) { it.copy(name = "Edição local") }
        assertEquals(edited, edited.addWorkPlan(WorkPlanCodec.decodeAi(root.toString())))
        assertThrows(IllegalArgumentException::class.java) { WorkPlanCodec.decodeAi(root.put("format", "outro").toString()) }
        assertTrue(WorkPlanCodec.aiPrompt(plan).contains("CENTAVOS inteiros"))
    }

    @Test fun mergeRemapsExecutionLinksAndKeepsExistingEditsWithoutDuplicatingPlans() {
        val source = approved().let { it.activatePlanStage(it.workPlans.single().id, it.workPlans.single().stages.single().id) }
        val localStage = source.stages.single().copy(id = "local-stage", budget = 8_000)
        val localOrders = source.purchases.mapIndexed { i, it -> it.copy(id = "local-$i", stageId = localStage.id, total = it.total!! + 100) }
        val local = Project(name = source.name, stages = listOf(localStage), purchases = localOrders)
        val merged = local.mergeFrom(source).project
        merged.validate()
        val linked = merged.workPlans.single().stages.single()
        assertEquals(localStage.id, linked.linkedStageId)
        assertEquals(localOrders.map { it.id }.toSet(), linked.items.mapNotNull { it.purchaseId }.toSet())
        assertEquals(localStage, merged.stages.single())
        assertEquals(localOrders, merged.purchases)
        assertEquals(0, merged.mergeFrom(source).added)
    }

    @Test fun movingAPlannedPurchaseClearsOnlyItsLinkAndKeepsApprovedHistory() {
        val active = approved().let { it.activatePlanStage(it.workPlans.single().id, it.workPlans.single().stages.single().id) }
        val purchase = active.purchases.first()
        val moved = active.recordPurchase(purchase.copy(stageId = null))
        assertEquals(active.workPlans.single().stages.single().approvals, moved.workPlans.single().stages.single().approvals)
        assertTrue(moved.workPlans.single().stages.single().items.none { it.purchaseId == purchase.id })
        assertEquals(active.payments, moved.payments)
        assertEquals(active.purchases.size, moved.purchases.size)
    }
}
