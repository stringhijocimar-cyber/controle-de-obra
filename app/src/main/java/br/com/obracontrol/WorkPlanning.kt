package br.com.obracontrol

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.security.MessageDigest

internal enum class WorkPlanType(val label: String) {
    RENOVATION("Reforma"), CLADDING("Revestimento"), NEW_HOME("Casa nova"), CUSTOM("Personalizado / IA")
}

internal data class PlanInput(
    val id: String = newId(),
    val name: String = "",
    val category: CostCategory = CostCategory.MATERIAL,
    val quantity: BigDecimal? = null,
    val unit: String = "",
    val unitPrice: Long? = null,
    val priceSource: String = "",
    val priceDate: LocalDate? = null,
    val estimated: Boolean = true,
    val notes: String = "",
    val purchaseId: String? = null,
) {
    val subtotal: Long? get() = quantity?.let { count -> unitPrice?.let { cents ->
        checkedMoney(count.multiply(BigDecimal.valueOf(cents)).setScale(0, RoundingMode.HALF_UP).longValueExact())
    } }
    val ready: Boolean get() = subtotal != null && unit.isNotBlank() && priceSource.isNotBlank() && priceDate != null

    fun validate() {
        require(name.isNotBlank()) { "Informe a descrição do insumo." }
        quantity?.let { checkedQuantity(it); require(it.signum() > 0) { "A quantidade deve ser maior que zero." } }
        unitPrice?.let(::checkedMoney)
        runCatching { subtotal }.getOrElse { throw IllegalArgumentException("O custo do insumo excede o limite permitido.") }
    }
}

internal data class PlanService(val id: String = newId(), val name: String = "", val items: List<PlanInput> = emptyList())

internal data class PlanApproval(
    val revision: Int,
    val date: LocalDate,
    val approver: String,
    val notes: String = "",
    val items: List<PlanInput>,
) {
    val total: Long get() = items.sumOf { requireNotNull(it.subtotal) }
}

internal data class PlanStage(
    val id: String = newId(),
    val name: String = "",
    val notes: String = "",
    val services: List<PlanService> = emptyList(),
    val revision: Int = 1,
    val approvals: List<PlanApproval> = emptyList(),
    val linkedStageId: String? = null,
) {
    val items: List<PlanInput> get() = services.flatMap { it.items }
    val knownTotal: Long get() = items.sumOf { it.subtotal ?: 0L }
    val missing: Int get() = items.count { !it.ready } + services.count { it.items.isEmpty() } + if (services.isEmpty()) 1 else 0
    val latestApproval: PlanApproval? get() = approvals.lastOrNull()
    val approved: Boolean get() = latestApproval?.revision == revision
    val status: String get() = when {
        approved -> "Aprovado"
        approvals.isNotEmpty() -> "Revisão pendente"
        missing > 0 || items.isEmpty() -> "A cotar"
        else -> "Aguardando aprovação"
    }
}

internal data class WorkPlan(
    val id: String = newId(),
    val title: String = "",
    val type: WorkPlanType = WorkPlanType.CUSTOM,
    val scope: String = "",
    val location: String = "",
    val area: BigDecimal? = null,
    val lossPercent: BigDecimal = BigDecimal.TEN,
    val created: LocalDate = LocalDate.now(),
    val assumptions: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
    val stages: List<PlanStage> = emptyList(),
    val originKey: String = "",
) {
    val knownTotal: Long get() = stages.sumOf { it.knownTotal }
    val approvedTotal: Long get() = stages.sumOf { it.latestApproval?.total ?: 0L }
    val missing: Int get() = stages.sumOf { it.missing }

    fun validate() {
        require(title.isNotBlank()) { "Informe o nome do planejamento." }
        area?.let { checkedQuantity(it); require(it.signum() > 0) { "A área deve ser positiva." } }
        require(lossPercent >= BigDecimal.ZERO && lossPercent <= BigDecimal.valueOf(100)) { "A perda deve ficar entre 0% e 100%." }
        require(stages.size in 1..100) { "O planejamento deve conter de 1 a 100 etapas." }
        requireDistinctIds(stages.map { it.id })
        val services = stages.flatMap { it.services }
        val inputs = services.flatMap { it.items }
        require(services.size <= 500 && inputs.size <= 5_000) { "O planejamento contém itens demais." }
        requireDistinctIds(services.map { it.id })
        requireDistinctIds(inputs.map { it.id })
        stages.forEach { stage ->
            require(stage.name.isNotBlank() && stage.revision > 0) { "Confira o nome e a revisão da etapa." }
            require(stage.services.all { it.name.isNotBlank() }) { "Informe a descrição do serviço." }
            stage.items.forEach(PlanInput::validate)
            stage.approvals.forEach { approval ->
                require(approval.revision in 1..stage.revision && approval.approver.isNotBlank() && approval.items.isNotEmpty()) { "Aprovação inválida." }
                approval.items.forEach { it.validate(); require(it.ready) { "O orçamento aprovado precisa de quantidade, unidade, preço, fonte e data." } }
                requireDistinctIds(approval.items.map { it.id })
                checkedMoney(approval.total)
            }
            require(stage.approvals.map { it.revision }.zipWithNext().all { (a, b) -> a < b }) { "Histórico de aprovações fora de ordem." }
            if (stage.approved) require(stage.latestApproval!!.items == stage.items.map { it.copy(purchaseId = null) } && stage.missing == 0) {
                "Os insumos foram alterados após a aprovação. Abra uma nova revisão."
            }
            checkedMoney(stage.knownTotal)
        }
        checkedMoney(knownTotal)
    }
}

internal data class PlanStageControl(val forecast: Long, val paid: Long, val undefined: Int, val progress: Int)

internal fun Project.planControl(stage: PlanStage): PlanStageControl? {
    val actual = stages.firstOrNull { it.id == stage.linkedStageId } ?: return null
    val balance = Balance(this)
    val orders = purchases.filter { it.stageId == actual.id }
    return PlanStageControl(
        forecast = (actual.budget ?: balance.paid(CostKind.LABOR, actual.id)) + orders.sumOf { it.forecast ?: balance.paid(CostKind.MATERIAL, it.id) },
        paid = balance.paid(CostKind.LABOR, actual.id) + orders.sumOf { balance.paid(CostKind.MATERIAL, it.id) },
        undefined = (if (actual.budget == null) 1 else 0) + orders.count { it.forecast == null },
        progress = actual.progress,
    )
}

internal fun Project.changePlanStage(planId: String, stageId: String, update: (PlanStage) -> PlanStage): Project = copy(
    workPlans = workPlans.map { plan -> if (plan.id != planId) plan else plan.copy(stages = plan.stages.map { stage ->
        if (stage.id == stageId) update(stage) else stage
    }) },
)

internal fun Project.editPlanStage(planId: String, stageId: String, update: (PlanStage) -> PlanStage): Project =
    changePlanStage(planId, stageId) { old ->
        val next = update(old).copy(revision = old.revision, approvals = old.approvals, linkedStageId = old.linkedStageId)
        if (next == old) old else next.copy(revision = Math.addExact(old.revision, 1))
    }.also(Project::validate)

internal fun Project.approvePlanStage(planId: String, stageId: String, approver: String, date: LocalDate, notes: String): Project =
    changePlanStage(planId, stageId) { stage ->
        require(!stage.approved) { "Esta revisão já está aprovada." }
        require(stage.items.isNotEmpty() && stage.missing == 0) { "Preencha quantidade, unidade, preço, fonte e data de todos os insumos antes de aprovar." }
        require(approver.isNotBlank()) { "Informe quem aprova o orçamento." }
        stage.copy(approvals = stage.approvals + PlanApproval(stage.revision, date, approver.trim(), notes.trim(),
            stage.items.map { it.copy(purchaseId = null) }))
    }.also(Project::validate)

internal fun Project.activatePlanStage(planId: String, stageId: String, existingStageId: String? = null, addToLaborContract: Boolean = false): Project {
    val plan = workPlans.firstOrNull { it.id == planId } ?: error("Planejamento não encontrado.")
    val stage = plan.stages.firstOrNull { it.id == stageId } ?: error("Etapa não encontrada.")
    if (stage.linkedStageId != null) return this
    require(stage.approved) { "Aprove a revisão atual antes de ativar a etapa." }
    if (existingStageId != null) {
        require(stages.any { it.id == existingStageId }) { "A etapa selecionada não existe." }
        require(workPlans.flatMap { it.stages }.none { it.linkedStageId == existingStageId }) { "Esta etapa já está vinculada a um planejamento." }
        return changePlanStage(planId, stageId) { it.copy(linkedStageId = existingStageId) }.also(Project::validate)
    }
    val labor = stage.items.filter { it.category == CostCategory.LABOR }.sumOf { it.subtotal!! }
    val actual = Stage(name = stage.name, budget = labor, notes = "Planejamento: ${plan.title}. ${stage.notes}".trim())
    val newPurchases = stage.services.flatMap { service -> service.items.filter { it.category != CostCategory.LABOR }.map { item ->
        item.id to Purchase(name = item.name, stageId = actual.id, total = item.subtotal,
            nature = if (item.category == CostCategory.MATERIAL) CostNature.MATERIAL else CostNature.SERVICE,
            notes = "Previsão aprovada • ${plan.title} / ${stage.name} / ${service.name}. Fonte: ${item.priceSource}, ${item.priceDate.dateText()}. ${item.notes}".trim(),
            lines = listOf(PurchaseLine(name = item.name, quantity = item.quantity, unit = item.unit, unitPrice = item.unitPrice)))
    } }.toMap()
    return copy(stages = stages + actual, purchases = purchases + newPurchases.values,
        laborBudget = if (addToLaborContract && laborBudget != null) checkedMoney(laborBudget + labor) else laborBudget,
    ).changePlanStage(planId, stageId) { value -> value.copy(linkedStageId = actual.id,
        services = value.services.map { service -> service.copy(items = service.items.map { input ->
            input.copy(purchaseId = newPurchases[input.id]?.id)
        }) }) }.also(Project::validate)
}

internal fun Project.addWorkPlan(plan: WorkPlan): Project {
    plan.validate()
    if (plan.originKey.isNotBlank() && workPlans.any { it.originKey == plan.originKey }) return this
    return copy(workPlans = workPlans + plan).also(Project::validate)
}

internal fun Project.unlinkPlanningStage(id: String): Project = copy(workPlans = workPlans.map { plan ->
    plan.copy(stages = plan.stages.map { stage -> if (stage.linkedStageId != id) stage else stage.copy(linkedStageId = null,
        services = stage.services.map { it.copy(items = it.items.map { input -> input.copy(purchaseId = null) }) }) })
})

internal fun Project.unlinkPlanningPurchase(id: String): Project = copy(workPlans = workPlans.map { plan ->
    plan.copy(stages = plan.stages.map { stage -> stage.copy(services = stage.services.map { service ->
        service.copy(items = service.items.map { if (it.purchaseId == id) it.copy(purchaseId = null) else it })
    }) })
})

internal fun WorkPlan.contentFingerprint(): String {
    val canonical = listOf(title, scope, location, area?.stripTrailingZeros()?.toPlainString().orEmpty(), lossPercent.stripTrailingZeros().toPlainString(),
        assumptions.joinToString("\u001e"), questions.joinToString("\u001e"),
        stages.joinToString("\u001e") { stage -> listOf(stage.name, stage.notes, stage.services.joinToString("\u001e") { service ->
            service.name + "\u001f" + service.items.joinToString("\u001e") { item ->
                listOf(item.name, item.category.name, item.quantity?.stripTrailingZeros()?.toPlainString(), item.unit,
                    item.unitPrice, item.priceSource, item.priceDate, item.estimated, item.notes).joinToString("\u001f")
            }
        }).joinToString("\u001f") }).joinToString("\u001d")
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
