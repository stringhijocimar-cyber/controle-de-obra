package br.com.obracontrol

import java.text.Normalizer

internal data class ProjectImport(val project: Project, val added: Int)

internal fun Project.mergeFrom(incoming: Project): ProjectImport {
    require(isSameWork(incoming)) {
        "O backup pertence a outra obra. Exporte a obra atual antes de usar Substituir obra."
    }
    var added = 0
    val stageIds = mutableMapOf<String, String>()
    val importedStages = stages.toMutableList()
    val matchedStages = incoming.stages.map { it.id }.intersect(stages.map { it.id }.toSet()).toMutableSet()
    for (stage in incoming.stages) {
        val match = importedStages.firstOrNull { it.id == stage.id }
            ?: stages.firstOrNull { it.id !in matchedStages && importKey(it.name) == importKey(stage.name) }
        stageIds[stage.id] = match?.id ?: stage.id
        if (match != null) matchedStages += match.id
        if (match == null) {
            importedStages += stage
            added++
        }
    }
    val purchaseIds = mutableMapOf<String, String>()
    val importedPurchases = purchases.toMutableList()
    val matchedPurchases = incoming.purchases.map { it.id }.intersect(purchases.map { it.id }.toSet()).toMutableSet()
    for (source in incoming.purchases) {
        val order = source.copy(stageId = source.stageId?.let(stageIds::get))
        val index = importedPurchases.indexOfFirst { it.id == order.id }.takeIf { it >= 0 }
            ?: purchases.indexOfFirst { it.id !in matchedPurchases && it.samePurchase(order) }
        if (index < 0) {
            importedPurchases += order
            purchaseIds[order.id] = order.id
            added += 1 + order.lines.size + order.documents.size
        } else {
            val current = importedPurchases[index]
            matchedPurchases += current.id
            val lines = current.lines.toMutableList()
            val matched = mutableSetOf<String>()
            val localLineIds = current.lines.mapTo(hashSetOf()) { it.id }
            order.lines.filter { it.id in localLineIds }.forEach { matched += it.id }
            for (line in order.lines.filterNot { it.id in localLineIds }) {
                val same = current.lines.firstOrNull { it.id !in matched && it.sameItem(line) }
                if (same != null) matched += same.id else {
                    lines += line
                    added++
                }
            }
            val documents = (current.documents + order.documents).distinctBy { it.id }
            added += documents.size - current.documents.size
            if (current.payer.isBlank() && order.payer.isNotBlank()) added++
            if (current.method.isBlank() && order.method.isNotBlank()) added++
            if (current.installmentCount == null && order.installmentCount != null) added++
            importedPurchases[index] = current.copy(lines = lines, documents = documents,
                payer = current.payer.ifBlank { order.payer }, method = current.method.ifBlank { order.method },
                installmentCount = current.installmentCount ?: order.installmentCount)
            purchaseIds[order.id] = current.id
        }
    }
    val planIds = mutableMapOf<String, String>()
    val importedPlans = scheduledPayments.toMutableList()
    val matchedPlans = incoming.scheduledPayments.map { it.id }.intersect(scheduledPayments.map { it.id }.toSet()).toMutableSet()
    for (source in incoming.scheduledPayments) {
        val targets = if (source.kind == CostKind.LABOR) stageIds else purchaseIds
        val plan = source.copy(targetId = targets.getValue(source.targetId))
        val match = importedPlans.firstOrNull { it.id == source.id }
            ?: if (source.targetId != plan.targetId) scheduledPayments.firstOrNull {
                it.id !in matchedPlans && it.kind == plan.kind && it.targetId == plan.targetId &&
                    it.date == plan.date && it.amount == plan.amount && importKey(it.label) == importKey(plan.label)
            } else null
        if (match == null) { importedPlans += plan; added++ } else matchedPlans += match.id
        planIds[source.id] = match?.id ?: plan.id
    }
    val importedPayments = payments.toMutableList()
    val reviewedIds = paymentAudit.filter { it.voided && it.restoredAt == null }.map { it.payment.id }.toSet()
    val matchedPayments = incoming.payments.map { it.id }.intersect(payments.map { it.id }.toSet()).toMutableSet()
    for (source in incoming.payments) {
        if (source.id in reviewedIds) continue
        val ids = if (source.kind == CostKind.MATERIAL) purchaseIds else stageIds
        val payment = source.copy(targetId = ids.getValue(source.targetId), scheduleId = source.scheduleId?.let(planIds::getValue))
        val index = payments.indexOfFirst { it.id == payment.id }.takeIf { it >= 0 }
            ?: if (payment.targetId != source.targetId) {
                payments.indexOfFirst { it.id !in matchedPayments && it.samePayment(payment) }
            } else -1
        if (index < 0) {
            importedPayments += payment
            added += 1 + payment.proofs.size
        } else {
            val current = importedPayments[index]
            matchedPayments += current.id
            val proofs = (current.proofs + payment.proofs).distinctBy { it.id }
            added += proofs.size - current.proofs.size
            importedPayments[index] = current.copy(proofs = proofs)
        }
    }
    val importedIssues = issues.toMutableList()
    for (issue in incoming.issues) {
        if (importedIssues.none { it.id == issue.id || importKey(it.title) == importKey(issue.title) }) {
            importedIssues += issue
            added++
        }
    }
    val diary = diaryEntries.associateBy { it.id }.toMutableMap()
    for (entry in incoming.diaryEntries) {
        val current = diary[entry.id]
        if (current == null) {
            diary[entry.id] = entry.copy(stageId = entry.stageId?.let(stageIds::getValue))
            added += 1 + entry.attachments.size
        } else {
            val attachments = (current.attachments + entry.attachments).distinctBy { it.id }
            diary[entry.id] = current.copy(attachments = attachments)
            added += attachments.size - current.attachments.size
        }
    }
    val names = (payerNames + incoming.payerNames).distinctBy { it.trim().lowercase(brazil) }
    val history = paymentAudit.toMutableList()
    incoming.paymentAudit.filter { entry -> history.none { it.id == entry.id } }.forEach { entry ->
        val ids = if (entry.payment.kind == CostKind.LABOR) stageIds else purchaseIds
        history += entry.copy(payment = entry.payment.copy(targetId = ids[entry.payment.targetId] ?: entry.payment.targetId,
            scheduleId = entry.payment.scheduleId?.let(planIds::get)))
        added++
    }
    added += names.size - payerNames.size
    val planning = workPlans.toMutableList()
    for (source in incoming.workPlans) {
        if (planning.any { it.id == source.id || source.originKey.isNotBlank() && it.originKey == source.originKey }) continue
        val remapped = source.copy(stages = source.stages.map { stage -> stage.copy(
            linkedStageId = stage.linkedStageId?.let(stageIds::getValue),
            services = stage.services.map { service -> service.copy(items = service.items.map { item ->
                item.copy(purchaseId = item.purchaseId?.let(purchaseIds::getValue))
            }) },
        ) })
        // Existing local planning owns its execution links; retain imported drafts without duplicating that ownership.
        val owned = planning.flatMap { it.stages }.mapNotNull { it.linkedStageId }.toSet()
        planning += remapped.copy(stages = remapped.stages.map { stage ->
            if (stage.linkedStageId !in owned) stage else stage.copy(linkedStageId = null,
                services = stage.services.map { it.copy(items = it.items.map { input -> input.copy(purchaseId = null) }) })
        })
        added++
    }
    val merged = copy(start = start ?: incoming.start, referenceDate = referenceDate ?: incoming.referenceDate,
        laborBudget = laborBudget ?: incoming.laborBudget, notes = notes.ifBlank { incoming.notes },
        stages = importedStages, purchases = importedPurchases, payments = importedPayments, issues = importedIssues,
        diaryEntries = diary.values.toList(),
        payerNames = names, scheduledPayments = importedPlans, workPlans = planning, paymentAudit = history)
    return ProjectImport(merged, added)
}

internal fun Project.hasWork() = copy(ganttScale = GanttScale.WEEKS) != Project()

internal fun Project.isSameWork(other: Project) = importKey(name) == importKey(other.name)

private fun Purchase.samePurchase(other: Purchase): Boolean =
    importKey(name) == importKey(other.name) && importKey(supplier) == importKey(other.supplier) &&
        importKey(document) == importKey(other.document) ||
        document.isNotBlank() && supplier.isNotBlank() && importKey(document) == importKey(other.document) &&
        importKey(supplier) == importKey(other.supplier)

private fun PurchaseLine.sameItem(other: PurchaseLine): Boolean =
    importKey(name) == importKey(other.name) && importKey(unit) == importKey(other.unit) &&
        quantity?.stripTrailingZeros() == other.quantity?.stripTrailingZeros() && unitPrice == other.unitPrice && subtotal == other.subtotal

private fun Payment.samePayment(other: Payment): Boolean =
    kind == other.kind && targetId == other.targetId && amount == other.amount && date == other.date &&
        importKey(payer) == importKey(other.payer) && importKey(method) == importKey(other.method) &&
        importKey(receipt) == importKey(other.receipt)

private fun importKey(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").trim().lowercase(brazil).replace(Regex("\\s+"), " ")
