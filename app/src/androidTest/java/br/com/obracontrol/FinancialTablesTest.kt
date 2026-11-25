package br.com.obracontrol

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.graphics.asAndroidBitmap
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
import org.junit.Assert.assertTrue
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
        val stage = Stage(name = "Alvenaria", budget = 20_000, progress = 50,
            plannedStart = today.minusDays(7), plannedEnd = today.plusDays(7))
        val paid = Purchase(name = "Cimento", supplier = "Loja Exemplo", total = 10_000, installmentCount = 1,
            payer = "Pessoa A", method = "PIX")
        val partial = Purchase(name = "Revestimento", supplier = "Loja Exemplo", total = 12_000, installmentCount = 3,
            payer = "Pessoa B", method = "Cartão")
        val unpaid = Purchase(name = "Perfis metálicos", total = 15_000, dueDate = today.plusDays(1))
        val project = Project(stages = listOf(stage,
            Stage(name = "Cobertura", budget = 80_000, progress = 0, plannedStart = today.plusDays(7), plannedEnd = today.plusDays(15)),
            Stage(name = "Acabamentos", budget = 60_000, progress = 0, plannedStart = today.plusDays(15), plannedEnd = today.plusDays(22))),
            purchases = listOf(paid, partial, unpaid,
                Purchase(name = "Forro PVC", total = 36_000, installmentCount = 6),
                Purchase(name = "Argamassa", total = 4_800, installmentCount = 1),
                Purchase(name = "Iluminação", total = 18_000, installmentCount = 3)), payments = listOf(
            Payment(kind = CostKind.MATERIAL, targetId = paid.id, amount = 10_000, date = today, payer = "Pessoa A", method = "PIX"),
            Payment(kind = CostKind.MATERIAL, targetId = partial.id, amount = 4_000, date = today, payer = "Pessoa B", method = "Cartão"),
            Payment(targetId = stage.id, amount = 10_000, date = today, payer = "Pessoa A", method = "PIX"),
        ))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Compras") and hasClickAction()).performClick()
            app.onNodeWithContentDescription("Pago", useUnmergedTree = true).assertIsDisplayed()
            app.onNodeWithContentDescription("Parcial", useUnmergedTree = true).assertIsDisplayed()
            app.onAllNodes(androidx.compose.ui.test.hasContentDescription("Em aberto"), useUnmergedTree = true)[0].assertIsDisplayed()
            val rowHeight = app.onNodeWithTag("table-row-${partial.id}").fetchSemanticsNode().boundsInRoot.height /
                app.activity.resources.displayMetrics.density
            assertTrue("A tabela deve usar linhas compactas", rowHeight <= 56f)
            screenshot("compras.png")
            wideScreenshot("compras-planilha.png")
            app.onNodeWithTag("table-values-${partial.id}", useUnmergedTree = true).performTouchInput { swipeLeft() }
            app.onNodeWithText("Revestimento").assertIsDisplayed()
            screenshot("compras-colunas.png")

            app.onNodeWithText("Etapas").performClick()
            app.onNodeWithText("50%").assertExists()
            screenshot("etapas.png")
            wideScreenshot("etapas-planilha.png")

            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            screenshot("pagamentos.png")
            wideScreenshot("pagamentos-planilha.png")
            app.onNodeWithText("A pagar").performClick()
            app.onNodeWithText("Perfis metálicos").assertIsDisplayed()
            screenshot("pagamentos-em-aberto.png")
            wideScreenshot("pagamentos-em-aberto-planilha.png")
            app.onNodeWithText("Perfis metálicos").performSemanticsAction(SemanticsActions.OnClick) { it() }
            app.onNodeWithText("Valor pago (R$)").performTextReplacement("150,00")
            app.onNodeWithText("Salvar").performClick()
            assertEquals(15_000L, Balance(store.load()).paid(CostKind.MATERIAL, unpaid.id))
            app.onNodeWithText("Perfis metálicos").assertDoesNotExist()
            assertEquals(3, store.load().purchases.single { it.id == partial.id }.installmentCount)
        } finally {
            app.activity.runOnUiThread { app.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            store.save(previous)
        }
    }

    private fun wideScreenshot(name: String) {
        app.onNodeWithText("Tela cheia").performClick()
        app.activity.runOnUiThread { app.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        app.waitUntil(15_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        app.waitForIdle()
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val bitmap = app.onNodeWithTag("table-fullscreen").captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        app.onNodeWithText("Fechar tabela").performClick()
        app.activity.runOnUiThread { app.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        app.waitUntil(15_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        app.waitForIdle()
    }

    private fun screenshot(name: String) {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val bitmap = app.onRoot().captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
