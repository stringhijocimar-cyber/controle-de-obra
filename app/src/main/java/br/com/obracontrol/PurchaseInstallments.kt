package br.com.obracontrol

import java.time.LocalDate

internal data class InstallmentPaymentChange(val scheduleId: String, val paymentId: String, val paid: Boolean, val date: LocalDate?)
internal data class PurchaseInstallmentChange(val rows: List<ScheduledPayment>, val payments: List<InstallmentPaymentChange> = emptyList())

internal fun Project.purchaseNextDue(order: Purchase): LocalDate? =
    if (scheduledPayments.none { it.kind == CostKind.MATERIAL && it.targetId == order.id }) order.dueDate
    else nextPendingPosition(CostKind.MATERIAL, order.id)?.date

/** A parcel flag records only the outstanding amount. Repeated saves update the same payment ID. */
internal fun Project.applyPurchaseInstallments(order: Purchase, change: PurchaseInstallmentChange, validate: Boolean = true): Project {
    require(change.rows.all { it.kind == CostKind.MATERIAL && it.targetId == order.id }) { "Confira o vínculo das parcelas com a compra." }
    require(change.payments.map { it.scheduleId }.distinct().size == change.payments.size) { "Revise cada parcela uma única vez." }
    require(change.payments.map { it.paymentId }.distinct().size == change.payments.size) { "Os pagamentos das parcelas precisam ter registros distintos." }
    val oldIds = scheduledPayments.filter { it.kind == CostKind.MATERIAL && it.targetId == order.id }.map { it.id }.toSet()
    val removed = oldIds - change.rows.map { it.id }.toSet()
    var next = copy(purchases = purchases.upsert(order, Purchase::id),
        scheduledPayments = scheduledPayments.filterNot { it.id in oldIds } + change.rows,
        payments = payments.map { if (it.scheduleId in removed) it.copy(scheduleId = null) else it })
    change.payments.forEach { edit ->
        require(change.rows.any { it.id == edit.scheduleId }) { "A parcela não existe nesta programação." }
        val original = next.payments.firstOrNull { it.id == edit.paymentId }
        require(original == null || original.kind == CostKind.MATERIAL && original.targetId == order.id && original.scheduleId == edit.scheduleId) {
            "O pagamento não pertence a esta parcela."
        }
        if (!edit.paid && original != null) {
            next = next.copy(payments = next.payments.filterNot { it.id == original.id }, paymentAudit = next.paymentAudit + PaymentAudit(
                payment = original, date = LocalDate.now(), reason = "Parcela marcada como não paga", voided = true, costName = order.name))
        }
    }
    change.payments.filter { it.paid }.forEach { edit ->
        val date = requireNotNull(edit.date) { "Selecione a data real de pagamento da parcela." }
        val original = next.payments.firstOrNull { it.id == edit.paymentId }
        if (original != null) {
            next = next.copy(payments = next.payments.upsert(original.copy(date = date), Payment::id))
        } else {
            val remaining = next.costPaymentPositions(CostKind.MATERIAL, order.id).first { it.schedule?.id == edit.scheduleId }.remaining ?: 0
            if (remaining > 0) {
                val payment = Payment(id = edit.paymentId, kind = CostKind.MATERIAL, targetId = order.id, amount = remaining,
                    date = date, payer = order.payer, method = order.method, scheduleId = edit.scheduleId)
                next = next.copy(payments = next.payments + payment)
                if (payment.payer.isNotBlank()) next = next.registerPayer(payment.payer)
            }
        }
    }
    return if (validate) next.also(Project::validate) else next
}

internal fun Project.parcelPayment(position: CostPaymentPosition): Payment? {
    if (!position.completed) return null
    val direct = payments.filter { it.kind == position.target.kind && it.targetId == position.target.id && it.scheduleId == position.schedule?.id }
    return direct.singleOrNull()
}

internal data class InstallmentRowDraft(val id: String, val label: String, val amount: Long, val due: String,
    val paymentId: String = newId(), val paidDate: String = "", val action: String = "keep") {
    fun plan(orderId: String) = ScheduledPayment(id, CostKind.MATERIAL, orderId, amount, parseDate(due), label)
}

internal data class PurchaseInstallmentDraft(val firstDate: String = "", val automatic: Boolean = true,
    val anchorFromPayment: Boolean = true, val rows: List<InstallmentRowDraft> = emptyList()) {
    fun change(orderId: String) = rows.takeIf { it.isNotEmpty() }?.let { values -> PurchaseInstallmentChange(
        values.map { it.plan(orderId) }, values.filter { it.action != "keep" }.map {
            InstallmentPaymentChange(it.id, it.paymentId, it.action == "pay", if (it.action == "pay") parseDate(it.paidDate) else null)
        }) }

    fun rebuild(project: Project, order: Purchase, dates: Boolean = false): PurchaseInstallmentDraft {
        val count = requireNotNull(order.installmentCount) { "Informe a quantidade de parcelas." }
        require(count in 2..120) { "Informe de 2 a 120 parcelas." }
        val total = requireNotNull(order.forecast) { "Informe primeiro o valor da compra." }
        val anchor = parseDate(firstDate)
        val plans = buildPaymentSchedule(CostKind.MATERIAL, order.id, total, count, anchor ?: LocalDate.now(), false, rows.map { it.id })
            .map { if (anchor == null) it.copy(date = null) else it }
        val base = project.copy(purchases = project.purchases.upsert(order, Purchase::id),
            scheduledPayments = project.scheduledPayments.filterNot { it.kind == CostKind.MATERIAL && it.targetId == order.id } + plans)
        val positions = base.costPaymentPositions(CostKind.MATERIAL, order.id)
        return copy(automatic = true, rows = plans.mapIndexed { index, plan ->
            rows.getOrNull(index)?.copy(label = plan.label, amount = plan.amount, due = if (dates) plan.date.dateText() else rows[index].due)
                ?: fromPlan(plan, base.parcelPayment(positions.first { it.schedule?.id == plan.id }))
        })
    }

    fun firstDue(value: String, project: Project, order: Purchase) = copy(firstDate = value, anchorFromPayment = false).rebuild(project, order, dates = true)

    fun toggle(id: String, paid: Boolean, project: Project, order: Purchase): PurchaseInstallmentDraft {
        val row = rows.first { it.id == id }
        val original = project.payments.firstOrNull { it.id == row.paymentId }
        var next = copy(rows = rows.map { if (it.id != id) it else it.copy(
            action = if (paid) "pay" else if (original != null) "unpay" else "keep",
            paidDate = if (paid && it.paidDate.isBlank()) LocalDate.now().dateText() else it.paidDate) })
        if (paid && rows.firstOrNull()?.id == id && anchorFromPayment && firstDate.isBlank()) {
            next = next.copy(firstDate = next.rows.first().paidDate).rebuild(project, order, dates = true)
        }
        return next
    }

    fun paymentDate(id: String, value: String, project: Project, order: Purchase): PurchaseInstallmentDraft {
        var next = copy(rows = rows.map { if (it.id == id) it.copy(paidDate = value, action = "pay") else it })
        if (rows.firstOrNull()?.id == id && anchorFromPayment) next = next.copy(firstDate = value).rebuild(project, order, dates = true)
        return next
    }

    companion object {
        private fun fromPlan(plan: ScheduledPayment, payment: Payment?) = InstallmentRowDraft(plan.id, plan.label, plan.amount,
            plan.date.dateText(), payment?.id ?: newId(), payment?.date.dateText())
        fun initial(project: Project, order: Purchase): PurchaseInstallmentDraft {
            val plans = project.scheduledPayments.filter { it.kind == CostKind.MATERIAL && it.targetId == order.id }
                .sortedWith(compareBy<ScheduledPayment, LocalDate?>(nullsLast()) { it.date })
            if (plans.isNotEmpty()) {
                val positions = project.costPaymentPositions(CostKind.MATERIAL, order.id)
                return PurchaseInstallmentDraft(plans.first().date.dateText(), automatic = false, anchorFromPayment = false,
                    rows = plans.map { plan -> fromPlan(plan, project.parcelPayment(positions.first { it.schedule?.id == plan.id })) })
            }
            return runCatching { PurchaseInstallmentDraft(order.dueDate.dateText(), anchorFromPayment = order.dueDate == null).rebuild(project, order) }
                .getOrDefault(PurchaseInstallmentDraft())
        }
    }
}
