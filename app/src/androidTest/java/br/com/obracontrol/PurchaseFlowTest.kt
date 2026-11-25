package br.com.obracontrol

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PurchaseFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun purchaseSavesPartialPaymentAndPayerCanBeReusedAfterRestart() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        try {
            store.save(Project())
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Compras") and hasClickAction()).performClick()
            app.onNodeWithText("Nova compra").performClick()
            app.onNodeWithText("Nome da compra ou serviço").performTextInput("Piso de teste")
            app.onNodeWithText("Tipo de custo").performScrollTo().performClick()
            app.onNodeWithText("Serviço").performClick()
            app.onNodeWithText("Total do documento ou da cotação (R$)").performScrollTo().performTextInput("1200,00")
            app.onNodeWithText("Condição de pagamento").performScrollTo().performClick()
            app.onNodeWithText("Parcelado").performClick()
            app.onNodeWithText("Quantidade de parcelas").performScrollTo().performTextInput("3")
            app.onNodeWithText("Método de pagamento").performScrollTo().performClick()
            app.onNodeWithText("Pix").performClick()
            app.onNodeWithText("Cadastrar pagador").performScrollTo().performClick()
            app.onNodeWithText("Nome do pagador").performTextInput("Pagador de teste")
            app.onNodeWithText("Cadastrar").performClick()
            assertTrue(store.load().payerNames.contains("Pagador de teste"))
            app.onNodeWithText("Registrar pagamento nesta compra").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            app.onNodeWithText("Registrar pagamento nesta compra").assertIsOn()
            app.onNodeWithText("Valor pago (R$)").performScrollTo().performTextReplacement("400,00")
            app.onNodeWithText("Salvar").assertExists("Primeiro pagamento\n" + app.onRoot(useUnmergedTree = true).printToString())
                .performClick()

            val saved = store.load()
            val purchase = saved.purchases.single()
            assertEquals("Pix", purchase.method)
            assertEquals("Pagador de teste", purchase.payer)
            assertEquals(3, purchase.installmentCount)
            assertEquals(CostNature.SERVICE, purchase.nature)
            app.onNodeWithText("Serviço").assertExists()
            app.onNodeWithText("3x de ${40_000L.brl()}").assertExists()
            assertEquals(purchase.id, saved.payments.single().targetId)
            assertEquals(40_000L, saved.payments.single().amount)
            assertEquals(80_000L, Balance(saved).materialOutstanding(purchase))
            app.onNodeWithTag("table-name-${purchase.id}", useUnmergedTree = true).performClick()
            app.onNodeWithText("Salvar").assertExists("Edição da compra\n" + app.onRoot(useUnmergedTree = true).printToString())
                .performClick()
            assertEquals(saved.payments, store.load().payments)

            app.activityRule.scenario.recreate()
            app.onNode(hasText("Compras") and hasClickAction()).performClick()
            app.onNodeWithText("Nova compra").performClick()
            app.onNodeWithText("Quem pagou").performScrollTo().performClick()
            app.onNodeWithText("Pagador de teste").performClick()
            app.onNodeWithText("Pagador de teste").assertIsDisplayed()
        } finally {
            store.save(previous)
        }
    }
}
