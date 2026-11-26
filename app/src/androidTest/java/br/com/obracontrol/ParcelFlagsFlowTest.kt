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

class ParcelFlagsFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun order() = Purchase(name = "Piso de exemplo em 6x", total = 564_275, installmentCount = 6, method = "Cartão", payer = "Pessoa A")

    @Test fun firstPaidDateCreatesFutureParcelsAndInlineFlagsCanPayEditAndUndoWithoutDuplicates() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val order = order()
        val today = LocalDate.now()
        try {
            store.save(Project(name = "Obra de exemplo", purchases = listOf(order)))
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Compras") and hasClickAction()).performClick()
            app.onNodeWithTag("table-name-${order.id}", useUnmergedTree = true).performClick()
            app.onNodeWithTag("purchase-parcel-flag-1").performScrollTo().performClick()
            selectToday("Data do pagamento da parcela 1")
            app.onNodeWithText("Primeiro vencimento").performScrollTo()
            screenshot("parcelas-compra-primeira-paga.png")
            saveForm()
            val saved = store.load()
            assertEquals(6, saved.scheduledPayments.size)
            assertEquals((0L..5L).map { today.plusMonths(it) }, saved.scheduledPayments.map { it.date })
            assertEquals(94_046L, Balance(saved).paid)
            assertEquals(today, saved.payments.single().date)
            app.onNodeWithTag("table-name-${order.id}", useUnmergedTree = true).performClick()
            saveForm()
            assertEquals(saved, store.load())
            app.onNodeWithTag("table-parcels-${order.id}").performClick()
            app.onNodeWithTag("inline-parcel-flag-${order.id}-1").assertIsOn()
            app.onNodeWithTag("inline-parcel-flag-${order.id}-2").assertIsOff()
            screenshot("parcelas-tabela-aberta.png")
            app.onNodeWithTag("inline-parcel-flag-${order.id}-2").performScrollTo().performClick()
            selectToday("Data do pagamento da parcela")
            app.onNodeWithText("Salvar pagamento").performClick()
            val second = store.load()
            assertEquals(2, second.payments.size)
            assertEquals(188_092L, Balance(second).paid)
            app.onNodeWithTag("inline-parcel-flag-${order.id}-2").performScrollTo().performClick()
            app.onNodeWithText("Salvar pagamento").performClick()
            assertEquals(second.payments, store.load().payments)
            app.onNodeWithTag("inline-parcel-flag-${order.id}-2").performScrollTo().performClick()
            app.onNodeWithText("Marcar não paga").performClick()
            val undone = store.load()
            assertEquals(saved.payments, undone.payments)
            assertEquals(1, undone.paymentAudit.size)
            assertEquals(second.payments.last(), undone.paymentAudit.single().payment)
            app.onNodeWithTag("table-total-1").assertTextEquals(94_046L.brl())
            val backup = PortableBackup(DocumentFiles(app.activity))
            val out = ByteArrayOutputStream()
            backup.write(out, undone)
            assertEquals(undone, backup.read(ByteArrayInputStream(out.toByteArray())))
            val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
            File(folder, "parcelas-relatorio.pdf").outputStream().use { FullReport.write(it, undone) }
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            app.onNodeWithText("Parcelados").performClick()
            app.onNodeWithTag("table-parcels-parcelado/${order.id}").performClick()
            app.onNodeWithTag("inline-parcel-flag-${order.id}-1").assertIsOn()
            app.onNodeWithTag("inline-parcel-flag-${order.id}-2").assertIsOff()
            screenshot("parcelas-pagamentos-flags.png")
            assertEquals(undone, store.load())
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    @Test fun generatingParcelsKeepsAnOldFullPaymentAndItsProofWithoutNewCharges() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val order = order()
        val proof = DocumentFiles(app.activity).save(ByteArrayInputStream(android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW/UAAAAASUVORK5CYII=", 0)), "comprovante-anterior.png")
        val payment = Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 564_275, date = LocalDate.now(), proofs = listOf(proof))
        try {
            store.save(Project(name = "Obra de exemplo", purchases = listOf(order), payments = listOf(payment)))
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Compras") and hasClickAction()).performClick()
            app.onNodeWithTag("table-name-${order.id}", useUnmergedTree = true).performClick()
            app.onNodeWithTag("purchase-parcel-flag-1").performScrollTo().assertIsOn().assertIsNotEnabled()
            saveForm()
            val saved = store.load()
            assertEquals(listOf(payment), saved.payments)
            assertEquals(6, saved.scheduledPayments.size)
            assertEquals(564_275L, Balance(saved).paid)
            assertEquals(listOf(proof), saved.documentAttachments())
            app.onNodeWithTag("table-parcels-${order.id}").performClick()
            app.onNodeWithTag("inline-parcel-flag-${order.id}-1").performScrollTo().performClick()
            app.onNodeWithText("Editar pago efetivo").assertExists()
            assertEquals(saved, store.load())
            val backup = PortableBackup(DocumentFiles(app.activity))
            val out = ByteArrayOutputStream()
            backup.write(out, saved)
            assertEquals(saved, backup.read(ByteArrayInputStream(out.toByteArray())))
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    private fun selectToday(label: String) {
        app.onNodeWithText(label).performScrollTo().performClick()
        app.onNodeWithText("Hoje").performClick()
        app.onNodeWithText("Aplicar").performClick()
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
            File(folder, "parcelas-falha.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } }
        File(folder, "parcelas-falha.txt").writeText(runCatching { app.onRoot(useUnmergedTree = true).printToString() }.getOrDefault("Sem árvore"))
    }
}
