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

class PaymentIntegrityFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test fun repeatedNewPaymentIsBlockedAndExistingRecordCanBeOpenedForEditing() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val order = Purchase(name = "Materiais de exemplo", total = 660_000)
        val first = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 330_000, date = LocalDate.now())
        try {
            store.save(Project(name = "Obra de exemplo", purchases = listOf(order), payments = listOf(first)))
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            newPayment(order.name)
            app.onNodeWithText("Pagamento semelhante já registrado").performScrollTo().assertExists()
            saveForm()
            assertEquals(listOf(first), store.load().payments)
            app.onNodeWithText("Editar pagamento existente 1").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            app.onNodeWithText("Observações do pagamento").performScrollTo().performTextReplacement("Conferido")
            saveForm()
            assertEquals(1, store.load().payments.size)
            assertEquals(first.id, store.load().payments.single().id)
            assertEquals(330_000L, Balance(store.load()).paid)
            newPayment(order.name)
            app.onNodeWithText("Confirmar outro pagamento igual").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            screenshot("aviso-pagamento-semelhante.png")
            saveForm()
            assertEquals(2, store.load().payments.size)
            assertEquals(660_000L, Balance(store.load()).paid)
            app.activityRule.scenario.recreate()
            assertEquals(2, store.load().payments.size)
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    @Test fun paymentTabReviewCorrectsDuplicatesAndKeepsProofsAuditAndBackup() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val order = Purchase(name = "Materiais de exemplo", total = 330_000)
        val proof = DocumentFiles(app.activity).save(ByteArrayInputStream(android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW/UAAAAASUVORK5CYII=", 0)), "comprovante-exemplo.png")
        val first = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 330_000, date = LocalDate.now(), proofs = listOf(proof))
        val original = Project(name = "Obra de exemplo", purchases = listOf(order), payments = listOf(first, first.copy(id = newId()), first.copy(id = newId())))
        try {
            store.save(original)
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            app.onNode(hasText("Conferir duplicados", substring = true) and hasClickAction()).performClick()
            app.onNodeWithTag("review-cost-MATERIAL/${order.id}").assertExists()
            screenshot("conferencia-pagamentos.png")
            app.onNodeWithText("Revisar pagamentos").performScrollTo().performClick()
            app.onNodeWithText("Manter só um por grupo semelhante").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            app.onNodeWithText("Pago após revisão").performScrollTo()
            screenshot("revisao-duplicados.png")
            saveForm()
            val corrected = store.load()
            assertEquals(listOf(first), corrected.payments)
            assertEquals(2, corrected.paymentAudit.size)
            assertEquals(330_000L, Balance(corrected).paid)
            assertEquals(original.documentAttachments().toSet(), corrected.documentAttachments().toSet())
            val backup = PortableBackup(DocumentFiles(app.activity))
            val out = ByteArrayOutputStream()
            backup.write(out, corrected)
            assertEquals(corrected, backup.read(ByteArrayInputStream(out.toByteArray())))
            assertEquals(330_000L, Balance(corrected.mergeFrom(original).project).paid)
            app.activity.runOnUiThread { app.activity.onBackPressedDispatcher.onBackPressed() }
            app.onNodeWithTag("table-total-0").assertTextEquals(330_000L.brl())
            screenshot("pagamentos-apos-revisao.png")
            app.activityRule.scenario.recreate()
            assertEquals(corrected, store.load())
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    private fun newPayment(name: String) {
        app.onNodeWithText("Novo").performClick()
        app.onNodeWithText("Custo vinculado").performClick()
        app.onNodeWithText(name).performClick()
        app.onNodeWithText("Valor pago (R$)").performScrollTo().performTextInput("3300,00")
    }
    private fun saveForm() {
        app.activity.runOnUiThread { app.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(app.activity.window.decorView.windowToken, 0) }
        app.waitForIdle()
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
            File(folder, "conferencia-falha.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } }
        File(folder, "conferencia-falha.txt").writeText(runCatching { app.onRoot(useUnmergedTree = true).printToString() }.getOrDefault("Sem árvore"))
    }
}
