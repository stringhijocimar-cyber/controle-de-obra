package br.com.obracontrol

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class FinancialIntegrationFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test fun pendingTablesUseTheSameParcelDatesAsCalendarAgendaAndFullReport() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val today = LocalDate.now()
        val stage = Stage(name = "Etapa de exemplo", budget = 100_000, progress = 35, plannedEnd = today.plusDays(1))
        val material = Purchase(name = "Material parcelado", total = 300_000, installmentCount = 3, stageId = stage.id, dueDate = today.minusDays(20))
        val service = Purchase(name = "Serviço parcelado", total = 100_000, installmentCount = 2, stageId = stage.id, nature = CostNature.SERVICE)
        val plans = buildPaymentSchedule(CostKind.MATERIAL, material.id, 300_000, 3, today, false) +
            buildPaymentSchedule(CostKind.MATERIAL, service.id, 100_000, 2, today.plusDays(3), false) +
            buildPaymentSchedule(CostKind.LABOR, stage.id, 100_000, 2, today.plusDays(7), true)
        val project = Project(name = "Obra de exemplo", laborBudget = stage.budget, stages = listOf(stage), purchases = listOf(material, service),
            scheduledPayments = plans, payments = listOf(Payment(kind = CostKind.MATERIAL, targetId = material.id, amount = 100_000,
                date = today, scheduleId = plans.first().id, payer = "Pessoa A", method = "Pix"),
                Payment(targetId = stage.id, amount = 20_000, date = today, payer = "Pessoa B", method = "Dinheiro")))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            navigate("Pagamentos")
            app.onNodeWithText("A pagar").performScrollTo().performClick()
            app.onNodeWithTag("table-total-0").assertTextEquals(380_000L.brl())
            due("MATERIAL/${material.id}", today.plusMonths(1))
            screenshot("integracao-vencimentos-a-pagar.png")
            navigate("Compras")
            due(material.id, today.plusMonths(1))
            navigate("Etapas")
            due(stage.id, today.plusDays(7))
            navigate("Pagamentos")
            app.onNodeWithText("Semanas").performScrollTo().performClick()
            app.onNodeWithTag("flow-paid-total", useUnmergedTree = true).assertTextEquals(120_000L.brl())
            app.onNodeWithTag("flow-pending-total", useUnmergedTree = true).assertTextEquals(380_000L.brl())
            assertEquals(120_000L, project.paymentBreakdown().total)
            assertEquals(380_000L, WorkAgenda(project, Balance(project), today).entries.filter { it.isPayment }.sumOf { it.amount ?: 0 })
            assertEquals(120_000L, ManagementReportSummary(project, today).paid)
            val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
            val pdf = File(folder, "integracao-financeira-relatorio.pdf")
            pdf.outputStream().use { FullReport.write(it, project, today) }
            if (Build.VERSION.SDK_INT >= 35) PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                val text = (0 until renderer.pageCount).joinToString("\n") { index -> renderer.openPage(index).use { page -> page.textContents.joinToString(" ") { it.text } } }
                assertTrue(text.contains(120_000L.brl()))
                assertTrue(text.contains(380_000L.brl()))
                assertTrue(text.contains("CONTROLE DE PARCELAS"))
            }
            val backups = PortableBackup(DocumentFiles(app.activity))
            val output = ByteArrayOutputStream()
            backups.write(output, store.load())
            assertEquals(project, backups.read(ByteArrayInputStream(output.toByteArray())))
            app.activityRule.scenario.recreate()
            assertEquals(project, store.load())
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    @Test fun overpaidCostsDoNotHideOtherBillsAndPendingColumnsNeverTurnNegative() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val stage = Stage(name = "Etapa paga a maior", budget = 10_000)
        val material = Purchase(name = "Material pago a maior", total = 10_000)
        val service = Purchase(name = "Serviço em aberto", total = 10_000, nature = CostNature.SERVICE)
        val project = Project(name = "Obra de exemplo", stages = listOf(stage), purchases = listOf(material, service), payments = listOf(
            Payment(targetId = stage.id, amount = 15_000), Payment(kind = CostKind.MATERIAL, targetId = material.id, amount = 15_000)))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            navigate("Compras")
            app.onNodeWithTag("table-total-0").assertTextEquals(20_000L.brl())
            app.onNodeWithTag("table-total-1").assertTextEquals(15_000L.brl())
            app.onNodeWithTag("table-total-2").assertTextEquals(10_000L.brl())
            navigate("Etapas")
            app.onNodeWithTag("table-total-2").assertTextEquals(0L.brl())
            app.onNodeWithTag("table-credit-${stage.id}", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            app.onNodeWithText("50,00", useUnmergedTree = true).assertExists()
            navigate("Pagamentos")
            app.onNodeWithText("A pagar").performScrollTo().performClick()
            app.onNodeWithTag("table-total-0").assertTextEquals(10_000L.brl())
            app.onNodeWithText(service.name).assertExists()
            assertEquals(0L, Balance(store.load()).remaining)
            assertEquals(10_000L, Balance(store.load()).overpaidByCost)
            assertEquals(project, store.load())
            screenshot("integracao-saldos-por-custo.png")
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    private fun navigate(label: String) {
        app.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
    }
    private fun due(key: String, expected: LocalDate) {
        app.onNodeWithTag("financial-table-body").performScrollToNode(hasTestTag("table-row-$key"))
        app.onNodeWithTag("table-due-$key", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        app.onNodeWithText(expected.dateText(), useUnmergedTree = true).assertExists()
    }
    private fun screenshot(name: String) {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val bitmap = app.onRoot().captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun captureFailure() {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        runCatching { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "integracao-falha.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } }
        File(folder, "integracao-falha.txt").writeText(runCatching { app.onRoot(useUnmergedTree = true).printToString() }.getOrDefault("Sem árvore"))
    }
}
