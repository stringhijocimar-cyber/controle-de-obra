package br.com.obracontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun PaymentReviewScreen(project: Project, kind: CostKind, targetId: String, save: (Project) -> Unit, navigate: (String) -> Unit, back: () -> Unit) {
    val payments = project.paymentsFor(kind, targetId)
    var kept by rememberSaveable { mutableStateOf(ArrayList(payments.map { it.id })) }
    var values by rememberSaveable { mutableStateOf(ArrayList(payments.map { it.amount.moneyInput() })) }
    var reason by rememberSaveable { mutableStateOf("Correção do pago efetivo / lançamento duplicado") }
    val proposed = runCatching { payments.mapIndexedNotNull { index, payment -> if (payment.id in kept)
        requireNotNull(parseMoney(values[index])) else null }.sum() }.getOrNull()
    val history = project.paymentAudit.filter { it.payment.kind == kind && it.payment.targetId == targetId }
    Form(save = {
        val amounts = payments.mapIndexed { index, payment -> payment.id to if (payment.id in kept)
            requireNotNull(parseMoney(values[index])) { "Preencha o valor dos pagamentos mantidos." } else null }.toMap()
        save(project.reviewPayments(kind, targetId, amounts, reason)); back()
    }) {
        Heading("Editar pago efetivo", project.costTarget(kind, targetId)?.name)
        LedgerLine("Pago efetivo atual", payments.sumOf { it.amount }.brl())
        LedgerLine("Pago após revisão", proposed.costText())
        Detail("Mantenha os pagamentos reais e desmarque os lançamentos duplicados. Os registros revisados e os comprovantes ficam no histórico e no backup.")
        if (payments.groupBy(::similarPaymentKey).any { it.value.size > 1 }) {
            OutlinedButton(onClick = { val seen = hashSetOf<String>(); kept = ArrayList(payments.filter { seen.add(similarPaymentKey(it)) }.map { it.id }) }) {
                Text("Manter só um por grupo semelhante")
            }
            Detail("Confira datas, valores e comprovantes: pagamentos iguais também podem ser parcelas reais distintas.", Amber)
        }
        payments.forEachIndexed { index, payment ->
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(payment.id in kept, { checked -> kept = ArrayList(if (checked) kept + payment.id else kept - payment.id) },
                    Modifier.testTag("review-keep-${payment.id}"))
                Column(Modifier.weight(1f)) {
                    Text("Pagamento ${index + 1} · ${project.paymentParcelLabel(payment)} · ${payment.date.dateOr("Sem data")}")
                    Detail("${payment.payer.textOr()} · ${payment.method.textOr()} · ${payment.proofs.size} comprovante(s)")
                    if (payment.receipt.isNotBlank()) Detail("Referência: ${payment.receipt}")
                }
            }
            if (payment.id in kept) Field("Valor do pagamento ${index + 1} (R$)", values[index], { next ->
                values = ArrayList(values.mapIndexed { position, value -> if (position == index) next else value })
            }, KeyboardType.Decimal) else Tag("Retirado do total na revisão", warning = true)
            TextButton(onClick = { navigate("pagamento/${payment.id}") }) { Text("Abrir pagamento e comprovantes") }
        }
        if (payments.isEmpty()) Detail("Nenhum pagamento ativo para este custo.")
        Field("Motivo da revisão", reason, { reason = it }, multiline = true)
        if (history.isNotEmpty()) {
            HorizontalDivider()
            Heading("Histórico de revisão")
            history.asReversed().forEach { entry ->
                Detail("${entry.date.dateText()} · ${entry.payment.amount.brl()} · ${entry.payment.date.dateOr("Sem data")} · ${entry.reason}")
                Tag(if (!entry.voided) "Valor anterior" else if (entry.restoredAt != null) "Restaurado" else "Fora do total pago", entry.voided && entry.restoredAt == null)
                if (entry.voided && entry.restoredAt == null && project.payments.none { it.id == entry.payment.id }) {
                    OutlinedButton(onClick = { submit { save(project.restoreReviewedPayment(entry.id)); back() } }) { Text("Restaurar pagamento revisado") }
                }
            }
        }
    }
}
