package br.com.obracontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun PaymentIntegrityScreen(project: Project, navigate: (String) -> Unit) {
    val costs = remember(project) { project.paymentReviewCosts() }
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = costs.filter { all || it.needsReview }
    ObraList {
        item { SheetCard {
            Heading("Conferir pagamentos", "Confira os lançamentos antes de retirar valores do total.")
            LedgerLine("Pago efetivo da obra", Balance(project).paid.brl())
            Detail("Custos para conferir: ${costs.count { it.needsReview }} · Possíveis repetições: ${costs.sumOf { it.possibleDuplicates }}")
            Detail("Parcelas e referências de comprovantes diferentes permanecem separadas. Valores iguais podem ser pagamentos reais distintos.")
            ViewTabs(listOf("Para conferir", "Todos os custos"), if (all) 1 else 0) { all = it == 1 }
        } }
        if (shown.isEmpty()) item { SheetCard {
            Detail(if (all) "Nenhum pagamento registrado." else "Nenhuma repetição exata ou pagamento a maior identificado. Use Todos os custos para conferir lançamentos com datas ou dados diferentes.")
        } }
        items(shown, key = { it.key }) { cost ->
            SheetCard(Modifier.testTag("review-cost-${cost.key}")) {
                Heading(cost.target.name, "${cost.target.category.label} · ${cost.payments.size} lançamento(s)")
                LedgerLine("Valor do custo", cost.target.total.costText())
                LedgerLine("Pago efetivo", cost.paid.brl())
                if (cost.possibleDuplicates > 0) Tag("Possíveis repetições: ${cost.possibleDuplicates}", warning = true)
                if (cost.overpaid > 0) Tag("Pago a maior: ${cost.overpaid.brl()}", warning = true)
                cost.payments.forEach { payment ->
                    Detail("${project.paymentParcelLabel(payment)} · ${payment.amount.brl()} · ${payment.date.dateOr("Sem data")}")
                    Detail("${payment.payer.textOr()} · ${payment.method.textOr()} · ${payment.proofs.size} comprovante(s)")
                }
                Button(onClick = { navigate("revisar-pago/${cost.target.kind.name}/${cost.target.id}") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Revisar pagamentos")
                }
                TextButton(onClick = { navigate("custo/${cost.target.kind.name}/${cost.target.id}") }) { Text("Abrir custo e cadastro") }
            }
        }
    }
}
