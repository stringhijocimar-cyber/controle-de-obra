package br.com.obracontrol

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
            app.onNodeWithText("Compras").performClick()
            app.onNodeWithText("Nova compra").performClick()
            app.onNodeWithText("Nome da compra ou material").performTextInput("Piso de teste")
            app.onNodeWithText("Total do documento ou da cotação (R$)").performScrollTo().performTextInput("1200,00")
            app.onNodeWithText("Método de pagamento").performScrollTo().performClick()
            app.onNodeWithText("Pix").performClick()
            app.onNodeWithText("Cadastrar pagador").performScrollTo().performClick()
            app.onNodeWithText("Nome do pagador").performTextInput("Pagador de teste")
            app.onNodeWithText("Cadastrar").performClick()
            assertTrue(store.load().payerNames.contains("Pagador de teste"))
            app.onNodeWithText("Registrar pagamento nesta compra").performScrollTo().performClick()
            app.onNodeWithText("Valor pago (R$)").performScrollTo().performTextReplacement("400,00")
            app.onNodeWithText("Salvar").performClick()

            val saved = store.load()
            val purchase = saved.purchases.single()
            assertEquals("Pix", purchase.method)
            assertEquals("Pagador de teste", purchase.payer)
            assertEquals(purchase.id, saved.payments.single().targetId)
            assertEquals(40_000L, saved.payments.single().amount)
            assertEquals(80_000L, Balance(saved).materialOutstanding(purchase))
            app.onNodeWithText("Abrir compra").performClick()
            app.onNodeWithText("Salvar").performClick()
            assertEquals(saved.payments, store.load().payments)

            app.activityRule.scenario.recreate()
            app.onNodeWithText("Compras").performClick()
            app.onNodeWithText("Nova compra").performClick()
            app.onNodeWithText("Quem pagou").performScrollTo().performClick()
            app.onNodeWithText("Pagador de teste").performClick()
            app.onNodeWithText("Pagador de teste").assertIsDisplayed()
        } finally {
            store.save(previous)
        }
    }
}
