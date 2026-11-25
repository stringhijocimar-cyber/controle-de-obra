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
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.previousOrSame

class CostCalendarFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun completionFlagsRegisterExactlyTheRemainingAmountForMaterialServiceAndLabor() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val stage = Stage(name = "Montagem", budget = 20_000, progress = 35)
        val material = Purchase(name = "Areia", total = 10_000, method = "Pix", payer = "Pessoa A")
        val service = Purchase(name = "Serviço técnico", total = 30_000, nature = CostNature.SERVICE)
        val project = Project(stages = listOf(stage), purchases = listOf(material, service))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Compras") and hasClickAction()).performClick()
            app.onNodeWithTag("payment-completed-${material.id}").performClick()
            saveForm()
            app.onNodeWithTag("payment-completed-${material.id}").assertIsOn()
            assertEquals(10_000L, store.load().payments.single().amount)
            app.onNodeWithTag("payment-completed-${service.id}").performClick()
            saveForm()
            app.onNodeWithTag("payment-completed-${service.id}").assertIsOn()
            app.onNodeWithText("Etapas").performClick()
            app.onNodeWithTag("payment-completed-${stage.id}").performClick()
            saveForm()
            app.onNodeWithTag("payment-completed-${stage.id}").assertIsOn()
            assertEquals(3, store.load().payments.size)
            assertEquals(60_000L, Balance(store.load()).paid)
            assertEquals(project.stages, store.load().stages)
            assertEquals(project.purchases, store.load().purchases)
            app.onNodeWithTag("payment-completed-${stage.id}").performClick()
            app.onNodeWithText("Nome da etapa").assertExists()
            saveForm()
            assertEquals(3, store.load().payments.size)
            screenshot("quitacao-tabela.png")
        } finally { store.save(previous) }
    }

    @Test
    fun weeklyCalendarDrillsThroughTypesAndCostsSettlesParcelsAndPersistsNewSchedules() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val today = LocalDate.now()
        val monday = today.with(previousOrSame(DayOfWeek.MONDAY))
        val stage = Stage(name = "Montagem da cobertura", budget = 90_000, dueDate = monday.plusDays(3), progress = 30)
        val material = Purchase(name = "Perfis de aço", total = 40_000, dueDate = monday.plusDays(4))
        val service = Purchase(name = "Instalação elétrica", total = 60_000, dueDate = monday.plusDays(5),
            nature = CostNature.SERVICE, payer = "Pessoa A", method = "Pix")
        val unknown = Purchase(name = "Materiais a cotar")
        val plans = buildPaymentSchedule(CostKind.MATERIAL, service.id, 60_000, 2, monday.plusDays(5), true)
        val documents = DocumentFiles(app.activity.applicationContext)
        val proof = documents.save(ByteArrayInputStream(android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW/UAAAAASUVORK5CYII=", 0)), "comprovante-teste.png")
        val project = Project(name = "Obra de exemplo", stages = listOf(stage), purchases = listOf(material, service, unknown),
            payments = listOf(Payment(targetId = stage.id, amount = 30_000, date = monday, payer = "Pessoa A", method = "Pix", proofs = listOf(proof))),
            scheduledPayments = plans)
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            app.onNodeWithText("Semanas").performClick()
            scrollTo("cashflow-week-${monday}")
            app.onNodeWithTag("cashflow-week-${monday}-0").assertTextEquals(30_000L.brl())
            app.onNodeWithTag("cashflow-week-${monday}-1").assertTextEquals(130_000L.brl())
            screenshot("calendario-semanal.png")
            app.onNodeWithTag("cashflow-week-toggle-$monday").performClick()
            scrollTo("cashflow-category-$monday/SERVICE")
            app.onNodeWithTag("cashflow-category-$monday/SERVICE").performClick()
            scrollTo("cashflow-cost-$monday-${service.id}")
            app.onNodeWithTag("cashflow-cost-$monday-${service.id}").performClick()
            scrollTo("cost-parcel-paid-${plans[0].id}")
            app.onNodeWithTag("cost-parcel-paid-${plans[0].id}").performClick()
            saveForm()
            assertEquals(2, store.load().payments.size)
            val first = store.load().payments.single { it.scheduleId == plans[0].id }
            assertEquals(30_000L, first.amount)
            assertEquals(today, first.date)
            scrollTo("cost-parcel-paid-${plans[0].id}")
            app.onNodeWithTag("cost-parcel-paid-${plans[0].id}").assertIsOn().assertIsNotEnabled()
            app.onNode(hasScrollAction()).performScrollToNode(hasText("Marcar como pago"))
            app.onNodeWithText("Marcar como pago").performClick()
            saveForm()
            assertEquals(3, store.load().payments.size)
            assertEquals(60_000L, Balance(store.load()).paid(CostKind.MATERIAL, service.id))
            assertTrue(CostPaymentCalendar(store.load()).pending.none { it.target.id == service.id })
            app.onNodeWithText("‹ Voltar").performClick()
            scrollTo("cashflow-category-$monday/LABOR")
            app.onNodeWithTag("cashflow-category-$monday/LABOR").performClick()
            scrollTo("cashflow-cost-$monday-${stage.id}")
            app.onNodeWithTag("cashflow-cost-$monday-${stage.id}").performClick()
            app.onNodeWithText("Programar pagamentos").performClick()
            app.onNodeWithText("Quantidade de vencimentos").performTextReplacement("3")
            app.onNodeWithText("Periodicidade").performScrollTo().performClick()
            app.onNodeWithText("Semanal").performClick()
            app.onNodeWithText("Gerar parcelas").performScrollTo().performClick()
            saveForm()
            val saved = store.load()
            assertEquals(3, saved.scheduledPayments.count { it.kind == CostKind.LABOR })
            assertEquals(3, saved.payments.size)
            assertEquals(2, saved.costPaymentPositions(CostKind.LABOR, stage.id).count { !it.completed })
            val archive = ByteArrayOutputStream()
            val backups = PortableBackup(documents)
            backups.write(archive, saved)
            assertEquals(saved, backups.read(ByteArrayInputStream(archive.toByteArray())))
            screenshot("custo-programado.png")
            val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
            val report = File(folder, "calendario-pagamentos.pdf")
            report.outputStream().use { FullReport.write(it, saved, today) }
            PdfRenderer(ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                if (Build.VERSION.SDK_INT >= 35) {
                    val text = (0 until pdf.pageCount).joinToString("\n") { index ->
                        pdf.openPage(index).use { it.textContents.joinToString(" ") { block -> block.text } }
                    }
                    assertTrue(text.contains("CALENDÁRIO DE PAGAMENTOS | POR SEMANA"))
                    assertTrue(text.contains("PROGRAMAÇÃO E QUITAÇÃO POR PARCELA"))
                    assertTrue(text.contains(stage.name) && text.contains(service.name) && text.contains(material.name))
                }
            }
            app.onNodeWithText("‹ Voltar").performClick()
            app.activityRule.scenario.recreate()
            assertEquals(saved, store.load())
            app.onNode(hasText("Pagamentos") and hasClickAction()).performClick()
            app.onNodeWithText("Semanas").performClick()
            scrollTo("cashflow-week-$monday")
            app.onNodeWithTag("cashflow-week-$monday-0").assertTextEquals(90_000L.brl())
            app.onNodeWithTag("cashflow-week-$monday-1").assertTextEquals(40_000L.brl())
        } finally { store.save(previous) }
    }

    private fun saveForm() {
        app.activity.runOnUiThread {
            app.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(app.activity.window.decorView.windowToken, 0)
        }
        app.waitForIdle()
        app.onNodeWithText("Salvar").performSemanticsAction(SemanticsActions.OnClick) { it() }
        app.waitForIdle()
    }

    private fun scrollTo(tag: String) { app.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag)) }

    private fun screenshot(name: String) {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val bitmap = app.onRoot().captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
