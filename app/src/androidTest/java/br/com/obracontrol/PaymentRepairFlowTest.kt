package br.com.obracontrol

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class PaymentRepairFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun proof() = DocumentFiles(app.activity).save(ByteArrayInputStream(android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW/UAAAAASUVORK5CYII=", 0)), "comprovante-exemplo.png")

    @Test fun reopeningPurchaseUpdatesTheSamePaymentAndExistingDuplicatesCanBeReviewedWithoutLosingProofs() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val order = Purchase(name = "Materiais de exemplo", total = 330_000, method = "Pix", payer = "Pessoa A")
        val first = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 330_000, method = "Pix", payer = "Pessoa A", proofs = listOf(proof()))
        try {
            store.save(Project(name = "Obra de exemplo", purchases = listOf(order), payments = listOf(first)))
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Compras") and hasClickAction()).performClick()
            repeat(3) {
                app.onNodeWithTag("table-name-${order.id}", useUnmergedTree = true).performClick()
                app.onNodeWithText("Registrar pagamento nesta compra").performScrollTo()
                    .performSemanticsAction(SemanticsActions.OnClick) { it() }
                app.onNodeWithText("Valor pago (R$)").performScrollTo().performTextReplacement("3300,00")
                saveForm()
                assertEquals(listOf(first), store.load().payments)
                assertEquals(330_000L, Balance(store.load()).paid)
            }
            val duplicated = store.load().copy(payments = listOf(first, first.copy(id = newId(), date = LocalDate.now()), first.copy(id = newId(), date = LocalDate.now())))
            store.save(duplicated)
            app.activityRule.scenario.recreate()
            app.onNodeWithTag("table-cost-${order.id}-1", useUnmergedTree = true).performScrollTo().performClick()
            app.onNodeWithText("Editar dados").performClick()
            duplicated.payments.drop(1).forEach { app.onNodeWithTag("review-keep-${it.id}").performScrollTo().performClick() }
            hideKeyboard()
            screenshot("revisao-pago-3300.png")
            saveForm()
            val corrected = store.load()
            assertEquals(listOf(first), corrected.payments)
            assertEquals(2, corrected.paymentAudit.size)
            assertEquals(330_000L, Balance(corrected).paid)
            assertEquals(duplicated.documentAttachments().toSet(), corrected.documentAttachments().toSet())
            screenshot("pago-efetivo-corrigido.png")
            val backups = PortableBackup(DocumentFiles(app.activity))
            val zip = ByteArrayOutputStream()
            backups.write(zip, corrected)
            assertEquals(corrected, backups.read(ByteArrayInputStream(zip.toByteArray())))
            val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
            File(folder, "revisao-pago-relatorio.pdf").outputStream().use { FullReport.write(it, corrected) }
            app.activityRule.scenario.recreate()
            assertEquals(corrected, store.load())
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    @Test fun serviceAndMaterialShareStageLinksAndWeeklyTableRollsUpLayersWithPayersAndMethods() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val stage = Stage(name = "Cobertura", budget = 90_000, dueDate = LocalDate.now().plusDays(1))
        val material = Purchase(name = "Perfis metálicos", total = 120_000, stageId = stage.id, dueDate = LocalDate.now().plusWeeks(1), payer = "Pessoa A", method = "Cartão de crédito")
        val service = Purchase(name = "Montagem contratada", total = 180_000, stageId = stage.id, nature = CostNature.SERVICE, dueDate = LocalDate.now(), payer = "Pessoa B", method = "Pix")
        val initial = Project(name = "Obra de exemplo", stages = listOf(stage), purchases = listOf(material, service), payments = listOf(
            Payment(kind = CostKind.MATERIAL, targetId = material.id, amount = 20_000, date = LocalDate.now(), payer = "Pessoa A", method = "Pix")))
        try {
            store.save(initial)
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            app.onNodeWithText("Novo").performClick()
            app.onNodeWithText("Tipo de custo").assertExists()
            app.onNodeWithText("Etapa vinculada").assertExists()
            app.onNodeWithText("Custo vinculado").performClick()
            app.onNodeWithText(service.name).performClick()
            app.onNodeWithText("Valor pago (R$)").performScrollTo().performTextInput("10,00")
            app.onNodeWithText("Quem pagou").performScrollTo().performTextInput("Pessoa B")
            app.onNodeWithText("Método de pagamento").performScrollTo().performTextInput("Pix")
            saveForm()
            val saved = store.load()
            assertEquals(2, saved.payments.size)
            assertEquals(CostKind.MATERIAL, saved.payments.last().kind)
            assertEquals(service.id, saved.payments.last().targetId)
            app.onNodeWithText("Etapas").performClick()
            app.onNodeWithText("Desembolsos").performClick()
            app.onNodeWithTag("flow-paid-total", useUnmergedTree = true).assertTextEquals(21_000L.brl())
            scrollTo("flow-toggle-stage-SERVICE-${stage.id}")
            app.onNodeWithTag("flow-toggle-stage-SERVICE-${stage.id}").performClick()
            scrollTo("flow-toggle-cost-MATERIAL/${service.id}")
            app.onNodeWithTag("flow-toggle-cost-MATERIAL/${service.id}").performClick()
            screenshot("desembolsos-semana-camadas.png")
            app.onNodeWithText("Responsáveis e saldos").performClick()
            screenshot("desembolsos-responsaveis.png")
            app.onNodeWithTag("flow-paid-cost-MATERIAL/${service.id}", useUnmergedTree = true).assertTextEquals(1_000L.brl())
            val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
            File(folder, "desembolsos-tabela-relatorio.pdf").outputStream().use { FullReport.write(it, saved) }
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    private fun scrollTo(tag: String) {
        app.onNodeWithTag("flow-table-body").performScrollToNode(hasTestTag(tag))
        app.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }
    private fun hideKeyboard() {
        app.activity.runOnUiThread { app.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(app.activity.window.decorView.windowToken, 0) }
        app.waitForIdle()
    }
    private fun saveForm() {
        hideKeyboard()
        app.onNodeWithText("Salvar").performSemanticsAction(SemanticsActions.OnClick) { it() }
        app.waitForIdle()
    }
    private fun screenshot(name: String) {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val bitmap = app.onRoot().captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun captureFailure() {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        runCatching { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "revisao-falha.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } }
        File(folder, "revisao-falha.txt").writeText(runCatching { app.onRoot(useUnmergedTree = true).printToString() }.getOrDefault("Sem árvore"))
    }
}
