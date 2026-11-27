package br.com.obracontrol

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
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

class TimelineCardFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    private fun example(): Project {
        val stage = Stage(name = "Acabamento de exemplo", budget = 60_000,
            plannedStart = LocalDate.of(2026, 12, 28), plannedEnd = LocalDate.of(2027, 1, 8), dueDate = LocalDate.of(2027, 1, 8))
        val next = Stage(name = "Entrega de exemplo", budget = 10_000,
            plannedStart = LocalDate.of(2027, 1, 10), plannedEnd = LocalDate.of(2027, 1, 20))
        val purchase = Purchase(name = "Revestimento de exemplo", total = 100_001, installmentCount = 2,
            method = "Cartão de crédito", stageId = stage.id, payer = "Pessoa A", notes = "Preservar cadastro")
        val service = Purchase(name = "Instalação de exemplo", total = 20_000, nature = CostNature.SERVICE, stageId = stage.id)
        val plans = buildPaymentSchedule(CostKind.MATERIAL, purchase.id, 100_001, 2, LocalDate.of(2026, 12, 10), false)
        val proofs = DocumentFiles(app.activity).save(ByteArrayInputStream(android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW/UAAAAASUVORK5CYII=", 0)), "comprovante-exemplo.png")
        return Project(name = "Obra de exemplo", stages = listOf(stage, next), purchases = listOf(purchase, service), scheduledPayments = plans,
            payments = listOf(Payment(kind = CostKind.MATERIAL, targetId = purchase.id, amount = 10_000, date = LocalDate.now(),
                method = "Pix", payer = "Pessoa A", proofs = listOf(proofs), scheduleId = plans.first().id),
                Payment(kind = CostKind.MATERIAL, targetId = purchase.id, amount = 5_000, date = LocalDate.now(), method = "Pix", payer = "Pessoa A")))
    }

    @Test fun calendarScaleShowsYearMonthAndIsoWeekPersistsAndAppearsInTheCompleteManagerialReport() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val project = example()
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            app.onNodeWithText("Etapas").performClick()
            app.onNodeWithText("Gantt").performClick()
            app.onNodeWithTag("gantt-year-2026").assertExists()
            app.onNodeWithTag("gantt-year-2027").assertExists()
            app.onNodeWithTag("gantt-month-2026-12").assertExists()
            app.onNodeWithTag("gantt-week-2026-53").assertIsDisplayed()
            screenshot("cronograma-semanas.png")
            app.onNodeWithText("Dias").performClick()
            app.onNodeWithTag("gantt-day-2026-12-28").assertIsDisplayed()
            assertEquals(GanttScale.DAYS, store.load().ganttScale)
            screenshot("cronograma-dias.png")
            app.onNodeWithText("Meses").performClick()
            app.onNodeWithTag("gantt-week-2026-53").assertDoesNotExist()
            assertEquals(GanttScale.MONTHS, store.load().ganttScale)
            screenshot("cronograma-meses.png")
            val snapshot = store.load()
            assertEquals(project.payments, snapshot.payments)
            assertEquals(project.purchases, snapshot.purchases)
            app.activityRule.scenario.recreate()
            assertEquals(snapshot, store.load())
            app.onNodeWithText("Relatório").performClick()
            app.onNodeWithText("Relatório gerencial completo").assertExists()
            app.onNodeWithText("Dias").performClick()
            assertEquals(GanttScale.DAYS, store.load().ganttScale)
            val saved = store.load()
            val backup = ByteArrayOutputStream()
            val backups = PortableBackup(DocumentFiles(app.activity))
            backups.write(backup, saved)
            assertEquals(saved, backups.read(ByteArrayInputStream(backup.toByteArray())))
            val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
            GanttScale.entries.forEach { scale ->
                val report = File(folder, "relatorio-gerencial-${scale.name.lowercase()}.pdf")
                report.outputStream().use { FullReport.write(it, saved.copy(ganttScale = scale)) }
                PdfRenderer(ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                    if (Build.VERSION.SDK_INT >= 35) {
                        val text = (0 until pdf.pageCount).joinToString("\n") { page -> pdf.openPage(page).use { it.textContents.joinToString(" ") { block -> block.text } } }
                        File(folder, "relatorio-texto-${scale.name.lowercase()}.txt").writeText(text)
                        listOf("RESUMO GERENCIAL", "PROGRAMAÇÃO FINANCEIRA", "CARTÃO E PARCELAMENTOS", "CRONOGRAMA FÍSICO",
                            "PRESTAÇÃO DE CONTAS", "CALENDÁRIO DE DESEMBOLSOS", "DIÁRIO DE OBRA").forEach { assertTrue(it, text.contains(it)) }
                        assertTrue("Escala ${scale.label}", Regex("Escala\\s*:\\s*${Regex.escape(scale.label)}").containsMatchIn(text))
                        assertTrue(text.contains("2026") && text.contains("2027"))
                    }
                }
            }
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    @Test fun cardTermsAreEditableFromBothTablesAndInsidePaymentWithoutLosingProofsOrOtherPayments() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val project = example()
        val order = project.purchases.first()
        val payment = project.payments.first()
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Compras") and hasClickAction()).performClick()
            termsCell(order.id).performScrollTo().performClick()
            app.onNodeWithText("Quantidade de parcelas da compra").performScrollTo().performTextReplacement("3")
            hideKeyboard()
            screenshot("cartao-editar-compras.png")
            saveForm()
            assertEquals(3, store.load().purchases.first().installmentCount)
            assertEquals(project.payments, store.load().payments)
            assertEquals(project.scheduledPayments, store.load().scheduledPayments)
            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            app.onNodeWithText("Registrados").performClick()
            termsCell(payment.id).performScrollTo().performClick()
            app.onNodeWithText("Quantidade de parcelas da compra").performScrollTo().performTextReplacement("4")
            saveForm()
            assertEquals(4, store.load().purchases.first().installmentCount)
            app.onNodeWithTag("table-terms-${payment.id}-Método", useUnmergedTree = true).performScrollTo().performClick()
            app.onNodeWithText("Valor pago (R$)").performScrollTo().performTextReplacement("200,00")
            app.onNodeWithText("Editar cartão e parcelamento da compra").performScrollTo().performClick()
            app.onNodeWithText("Método da compra").assert(hasText("Cartão de crédito"))
            app.onNodeWithText("Quantidade de parcelas da compra").performScrollTo().performTextReplacement("5")
            app.onNodeWithText("Método de pagamento").performScrollTo().performTextReplacement("Cartão de crédito")
            app.onNodeWithText("Quantidade de parcelas da compra").performScrollTo()
            hideKeyboard()
            screenshot("cartao-editar-pagamento.png")
            saveForm()
            val saved = store.load()
            assertEquals(5, saved.purchases.first().installmentCount)
            assertEquals(order.copy(installmentCount = 5), saved.purchases.first())
            assertEquals(listOf(payment.copy(amount = 20_000, method = "Cartão de crédito"), project.payments.last()), saved.payments)
            assertEquals(project.scheduledPayments, saved.scheduledPayments)
            assertEquals(25_000L, Balance(saved).paid)
            assertEquals(75_001L, InstallmentBalance(saved.purchases.first(), Balance(saved).materialPaid).remaining)
            app.activityRule.scenario.recreate()
            assertEquals(saved, store.load())
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    private fun termsCell(id: String) = app.onNodeWithTag("table-terms-$id-Parcelas", useUnmergedTree = true)
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
            File(folder, "cronograma-cartao-falha.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } }
        File(folder, "cronograma-cartao-falha.txt").writeText(runCatching { app.onRoot(useUnmergedTree = true).printToString() }.getOrDefault("Sem árvore"))
    }
}
