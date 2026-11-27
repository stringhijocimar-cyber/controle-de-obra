package br.com.obracontrol

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class InstallmentFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun installmentViewSeparatesActualPaymentsAndPendingCommitmentsAfterRestart() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val today = LocalDate.now()
        val purchase = Purchase(name = "Piso e revestimentos", total = 120_000, installmentCount = 3,
            method = "Cartão de crédito", payer = "Pessoa A", dueDate = today.plusDays(12))
        val roof = Purchase(name = "Cobertura metálica", total = 200_000, installmentCount = 4,
            method = "Boleto", dueDate = today.plusDays(25))
        val service = Purchase(name = "Projeto elétrico", total = 60_000, installmentCount = 2, nature = CostNature.SERVICE)
        val single = Purchase(name = "Material à vista", total = 5_000, installmentCount = 1)
        val project = Project(name = "Minha área gourmet", start = today.minusDays(22),
            stages = listOf(Stage(name = "Alvenaria", budget = 100_000, progress = 100),
                Stage(name = "Cobertura", budget = 180_000, progress = 40), Stage(name = "Acabamentos", budget = 90_000)),
            purchases = listOf(purchase, roof, service, single), payments = listOf(
                Payment(kind = CostKind.MATERIAL, targetId = purchase.id, amount = 40_000, date = today),
                Payment(kind = CostKind.MATERIAL, targetId = service.id, amount = 60_000, date = today)))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            screenshot("painel.png")
            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            app.onNodeWithText("Parcelados").performScrollTo().performClick()
            app.onNodeWithTag("table-total-0").assertTextEquals(380_000L.brl())
            app.onNodeWithTag("table-total-1").assertTextEquals(100_000L.brl())
            app.onNodeWithTag("table-total-2").assertTextEquals(280_000L.brl())
            app.onNodeWithText(single.name).assertDoesNotExist()
            assertEquals(project.payments, store.load().payments)
            screenshot("parcelados.png")
            app.onNodeWithText("Tela cheia").performClick()
            app.activity.runOnUiThread { app.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            app.waitUntil(15_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            app.waitForIdle()
            screenshot("parcelados-planilha.png", "table-fullscreen")
            app.onNodeWithText("Fechar tabela").performClick()
            app.activity.runOnUiThread { app.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            app.waitUntil(15_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            app.waitForIdle()
            app.onNodeWithTag("table-name-parcelado/${purchase.id}", useUnmergedTree = true).performClick()
            app.onNodeWithText("Valor pago (R$)").performScrollTo().performTextReplacement("400,00")
            app.onNodeWithText("Salvar").performSemanticsAction(SemanticsActions.OnClick) { it() }
            assertEquals(3, store.load().payments.size)
            app.onNodeWithTag("table-total-1").assertTextEquals(140_000L.brl())
            app.onNodeWithTag("table-total-2").assertTextEquals(240_000L.brl())
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            app.onNodeWithText("Parcelados").performScrollTo().performClick()
            app.onNodeWithTag("table-total-0").assertTextEquals(380_000L.brl())
            app.onNodeWithTag("table-total-1").assertTextEquals(140_000L.brl())
            app.onNodeWithTag("table-total-2").assertTextEquals(240_000L.brl())
        } finally {
            app.activity.runOnUiThread { app.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            store.save(previous)
        }
    }

    private fun screenshot(name: String, tag: String? = null) {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val bitmap = (if (tag == null) app.onRoot() else app.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
