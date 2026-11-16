package br.com.obracontrol

import java.time.LocalDate

internal enum class AgendaKind(val label: String) {
    STAGE("Etapa"), DELIVERY("Entrega"), LABOR_PAYMENT("Mão de obra"), PURCHASE_PAYMENT("Pagamento de compra");

    val costKind: CostKind?
        get() = when (this) {
            LABOR_PAYMENT -> CostKind.LABOR
            PURCHASE_PAYMENT -> CostKind.MATERIAL
            else -> null
        }
}

internal enum class AgendaWindow(val label: String) {
    OVERDUE("Atrasados"), NEXT_SEVEN_DAYS("Até 7 dias"), LATER("Depois de 7 dias"), UNSCHEDULED("Sem data");
}

internal data class AgendaEntry(
    val targetId: String,
    val kind: AgendaKind,
    val name: String,
    val date: LocalDate?,
    val amount: Long? = null,
    val detail: String = "",
) {
    val key get() = "${kind.name}:$targetId"
    val isPayment get() = kind.costKind != null
}

internal class WorkAgenda(project: Project, balance: Balance, val today: LocalDate) {
    val cutoff: LocalDate = today.plusDays(7)
    val entries: List<AgendaEntry> = buildList {
        project.stages.filter { it.progress < 100 }.forEach { stage ->
            add(AgendaEntry(stage.id, AgendaKind.STAGE, stage.name, stage.plannedEnd,
                detail = "${stage.status} · ${stage.progress}% concluído"))
        }
        project.purchases.filter { it.deliveryExpected != null && it.receivedDate == null }.forEach { order ->
            add(AgendaEntry(order.id, AgendaKind.DELIVERY, order.name, order.deliveryExpected, detail = order.supplier))
        }
        balance.disbursements.forEach { payment ->
            val kind = if (payment.kind == CostKind.LABOR) AgendaKind.LABOR_PAYMENT else AgendaKind.PURCHASE_PAYMENT
            add(AgendaEntry(payment.targetId, kind, payment.name, payment.date, payment.amount))
        }
    }.sortedWith(compareBy(nullsLast()) { it.date })

    fun window(date: LocalDate?): AgendaWindow = when {
        date == null -> AgendaWindow.UNSCHEDULED
        date < today -> AgendaWindow.OVERDUE
        date <= cutoff -> AgendaWindow.NEXT_SEVEN_DAYS
        else -> AgendaWindow.LATER
    }

    fun inWindow(period: AgendaWindow) = entries.filter { window(it.date) == period }

    fun paymentsIn(window: AgendaWindow) = inWindow(window).filter { it.isPayment }
}

internal data class DiaryEntry(
    val id: String = newId(),
    val date: LocalDate = LocalDate.now(),
    val title: String = "",
    val stageId: String? = null,
    val activities: String = "",
    val occurrences: String = "",
    val attachments: List<DocumentAttachment> = emptyList(),
)

internal fun Project.diaryNewestFirst() = diaryEntries.asReversed().sortedByDescending { it.date }
