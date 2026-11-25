package br.com.obracontrol

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class FinancialTablesTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun tablesShowPaymentStatesKeepNamesDuringScrollingAndOpenPendingPayments() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val today = LocalDate.now()
        val stage = Stage(name = "Etapa de teste", budget = 20_000, progress = 50)
        val paid = Purchase(name = "Compra quitada", total = 10_000, installmentCount = 1)
        val partial = Purchase(name = "Materiais parcelados", total = 12_000, installmentCount = 3)
        val unpaid = Purchase(name = "Compra em aberto", total = 15_000, dueDate = today.plusDays(1))
        val project = Project(stages = listOf(stage), purchases = listOf(paid, partial, unpaid), payments = listOf(
            Payment(kind = CostKind.MATERIAL, targetId = paid.id, amount = 10_000, date = today),
            Payment(kind = CostKind.MATERIAL, targetId = partial.id, amount = 4_000, date = today),
            Payment(targetId = stage.id, amount = 10_000, date = today),
        ))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            app.onNodeWithText("Compras").performClick()
            app.onNodeWithContentDescription("Pago", useUnmergedTree = true).assertIsDisplayed()
            app.onNodeWithContentDescription("Parcial", useUnmergedTree = true).assertIsDisplayed()
            app.onNodeWithContentDescription("Em aberto", useUnmergedTree = true).assertIsDisplayed()
            screenshot("compras.png")
            app.onNodeWithTag("table-values-${partial.id}", useUnmergedTree = true).performTouchInput { swipeLeft() }
            app.onNodeWithText("Materiais parcelados").assertIsDisplayed()
            screenshot("compras-colunas.png")

            app.onNodeWithText("Etapas").performClick()
            app.onNodeWithText("50% · Em andamento").assertExists()
            screenshot("etapas.png")

            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            screenshot("pagamentos.png")
            app.onNodeWithText("A pagar").performClick()
            app.onNodeWithText("Compra em aberto").assertIsDisplayed()
            screenshot("pagamentos-em-aberto.png")
            app.onNodeWithText("Compra em aberto").performSemanticsAction(SemanticsActions.OnClick) { it() }
            app.onNodeWithText("Valor pago (R$)").performTextReplacement("150,00")
            app.onNodeWithText("Salvar").performClick()
            assertEquals(15_000L, Balance(store.load()).paid(CostKind.MATERIAL, unpaid.id))
            app.onNodeWithText("Compra em aberto").assertDoesNotExist()
            assertEquals(3, store.load().purchases.single { it.id == partial.id }.installmentCount)
        } finally {
            store.save(previous)
        }
    }

    private fun screenshot(name: String) {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val bitmap = app.onRoot().captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
