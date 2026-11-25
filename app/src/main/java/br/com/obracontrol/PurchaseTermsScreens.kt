package br.com.obracontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun PurchaseTermsEditor(project: Project, id: String, save: (PurchaseTermsChange) -> Unit) {
    val order = project.purchases.firstOrNull { it.id == id }
    if (order == null) { SheetCard { Detail("Compra não encontrada.") }; return }
    var method by rememberSaveable(id) { mutableStateOf(order.method) }
    var condition by rememberSaveable(id) { mutableStateOf(order.termsKey()) }
    var count by rememberSaveable(id) { mutableStateOf(order.installmentCount?.toString().orEmpty()) }
    var total by rememberSaveable(id) { mutableStateOf(order.total.moneyInput()) }
    Form(save = { save(readPurchaseTerms(order, method, condition, count, total)) }) {
        Heading("Cartão e parcelamento", order.name)
        PurchaseTermsFields(project, order, method, { method = it }, condition, { condition = it }, count, { count = it }, total, { total = it })
    }
}

@Composable
internal fun PurchaseTermsFields(
    project: Project, order: Purchase,
    method: String, setMethod: (String) -> Unit, condition: String, setCondition: (String) -> Unit,
    count: String, setCount: (String) -> Unit, total: String, setTotal: (String) -> Unit,
) {
    SuggestedField("Método da compra", method, project.paymentMethodOptions(), setMethod)
    Select("Condição da compra", condition,
        listOf("unknown" to "Não informado", "single" to "À vista", "installments" to "Parcelado"), setCondition)
    if (condition == "installments") Field("Quantidade de parcelas da compra", count, setCount, KeyboardType.Number)
    Field("Total da compra ou cotação (R$)", total, setTotal, KeyboardType.Decimal)
    Detail("Se o total ficar em branco, o valor é calculado pelos itens quando todos estiverem preenchidos.")
    val paid = Balance(project).paid(CostKind.MATERIAL, order.id)
    runCatching { readPurchaseTerms(order, method, condition, count, total) }.getOrNull()?.let { change ->
        val draft = order.copy(method = change.method, installmentCount = change.installmentCount, total = change.total)
        LedgerLine("Total conhecido", draft.forecast.costText())
        if (change.installmentCount != null) {
            Heading("Divisão do valor")
            Detail(draft.installmentText(), Green)
        }
        LedgerLine("Pago efetivo", paid.brl())
        LedgerLine("Saldo a pagar", InstallmentBalance(draft, paid).remaining.costText())
    }
    Detail("A divisão distribui os centavos para fechar o total. Os pagamentos registrados, comprovantes e vencimentos são preservados.")
    Detail("Para definir as datas de cada parcela, abra o custo e use Programar pagamentos.")
}
