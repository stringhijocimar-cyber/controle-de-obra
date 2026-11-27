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
import java.io.File
import java.time.LocalDate

class PaymentOrganizationFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.of(2026, 11, 27)

    @Test fun categoryFiltersApplyToAllPaymentViewsWhileOverallTotalsStayVisibleAndDataIsPreserved() {
        val store = ProjectStore(app.activity)
        val before = store.load()
        val stage = Stage(name = "Equipe da bancada", budget = 100_000, dueDate = today)
        val material = Purchase(name = "Insumos da bancada", total = 60_000, installmentCount = 2, dueDate = today)
        val service = Purchase(name = "Pia sob encomenda", total = 240_000, installmentCount = 3, nature = CostNature.SERVICE, dueDate = today)
        val project = Project(name = "Obra de exemplo - dados fictícios", laborBudget = stage.budget, stages = listOf(stage),
            purchases = listOf(material, service), payments = listOf(
                Payment(targetId = stage.id, amount = 20_000, date = today),
                Payment(kind = CostKind.MATERIAL, targetId = material.id, amount = 10_000, date = today),
                Payment(kind = CostKind.MATERIAL, targetId = service.id, amount = 80_000, date = today)))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            tab("Pagamentos")
            overallTotals()
            app.onNodeWithTag("payments-category-MATERIAL").performClick()
            app.onNodeWithTag("table-name-${project.payments[1].id}", useUnmergedTree = true).assertExists()
            app.onNodeWithTag("table-name-${project.payments[0].id}", useUnmergedTree = true).assertDoesNotExist()
            app.onNodeWithTag("table-name-${project.payments[2].id}", useUnmergedTree = true).assertDoesNotExist()
            app.onNodeWithTag("table-total-0").assertTextEquals(10_000L.brl())
            tab("A pagar")
            app.onNodeWithTag("table-total-0").assertTextEquals(50_000L.brl())
            overallTotals()
            app.onNodeWithTag("payments-category-SERVICE").performClick()
            app.onNodeWithTag("table-total-0").assertTextEquals(160_000L.brl())
            tab("Parcelados")
            app.onNodeWithTag("table-total-0").assertTextEquals(240_000L.brl())
            app.onNodeWithTag("table-total-1").assertTextEquals(80_000L.brl())
            screenshot("pagamentos-terceirizados-2.6.0.png")
            tab("Semanas")
            app.onNodeWithTag("flow-paid-total", useUnmergedTree = true).assertTextEquals(80_000L.brl())
            app.onNodeWithTag("flow-pending-total", useUnmergedTree = true).assertTextEquals(160_000L.brl())
            app.onNodeWithTag("payments-category-LABOR").performClick()
            app.onNodeWithTag("flow-paid-total", useUnmergedTree = true).assertTextEquals(20_000L.brl())
            app.onNodeWithTag("flow-pending-total", useUnmergedTree = true).assertTextEquals(80_000L.brl())
            tab("Registrados")
            app.onNodeWithTag("table-total-0").assertTextEquals(20_000L.brl())
            overallTotals()
            app.onNodeWithTag("payments-category-ALL").performClick()
            screenshot("pagamentos-visao-geral-2.6.0.png")
            assertEquals(project, store.load())
            assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(store.load())))
        } finally { store.save(before) }
    }

    @Test fun reportContainsMonthlyCalendarAndEveryAgendaEntryWithRepeatedPageHeaders() {
        val stage = Stage(name = "Acabamento", budget = 600_000, progress = 40,
            plannedStart = today.minusDays(10), plannedEnd = today.plusDays(30), dueDate = today.plusDays(7))
        val material = Purchase(name = "Revestimento", total = 600_000, installmentCount = 3, stageId = stage.id, method = "Cartão", payer = "Pessoa A")
        val service = Purchase(name = "Pia e vidro sob encomenda", total = 350_000, nature = CostNature.SERVICE, stageId = stage.id, dueDate = today.plusDays(1))
        val many = (1..25).map { Purchase(name = "CUSTO-AGENDA-${it.toString().padStart(2, '0')}", total = 10_000L * it,
            dueDate = today, nature = if (it % 2 == 0) CostNature.SERVICE else CostNature.MATERIAL) }
        val schedule = buildPaymentSchedule(CostKind.MATERIAL, material.id, 600_000, 3, today, false)
        val project = Project(name = "Área gourmet - exemplo com dados fictícios", start = today.minusDays(15), laborBudget = stage.budget,
            stages = listOf(stage), purchases = listOf(material, service, Purchase(name = "Orçamento a confirmar")) + many,
            scheduledPayments = schedule, payments = listOf(
                Payment(kind = CostKind.MATERIAL, targetId = material.id, amount = 200_000, date = today, scheduleId = schedule.first().id, payer = "Pessoa A", method = "Cartão"),
                Payment(targetId = stage.id, amount = 150_000, date = today, payer = "Pessoa B", method = "Pix")),
            issues = listOf(Issue(title = "PAGINACAO-INICIO", action = List(320) { "Conferir execução e recebimentos antes de aprovar." }.joinToString(" ") + " PAGINACAO-FIM")))
        project.validate()
        val before = ProjectCodec.encode(project)
        val pdf = File(folder(), "Relatorio-Gerencial-2.6.0-Exemplo.pdf")
        pdf.outputStream().use { FullReport.write(it, project, today) }
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            val pages = (0 until renderer.pageCount).map { index -> renderer.openPage(index).use { page ->
                assertEquals(842, page.width)
                assertEquals(595, page.height)
                if (Build.VERSION.SDK_INT >= 35) page.textContents.joinToString(" ") { it.text } else ""
            } }
            if (Build.VERSION.SDK_INT >= 35) {
                val text = pages.joinToString(" ").replace(Regex("\\s+"), " ")
                listOf("SEGUNDA", "DOMINGO", "NOVEMBRO / 2026", "DEZEMBRO / 2026", "JANEIRO / 2027", "SEM DATA DEFINIDA",
                    "PAGINACAO-FIM", "QUEM PAGOU", "VISÃO GANTT", "PRESTAÇÃO DE CONTAS", "DIÁRIO DE OBRA").forEach { assertTrue(it, text.contains(it)) }
                assertFalse(text.contains("TABELA SEMANAL EM CAMADAS"))
                many.forEach { assertTrue(it.name, text.contains(it.name)) }
                assertTrue(pages.count { it.contains("CALENDÁRIO DE DESEMBOLSOS | AGENDA") && it.contains("NOVEMBRO / 2026") } >= 2)
                pages.forEachIndexed { index, content -> assertTrue("Rodapé da página ${index + 1}", content.contains("Página ${index + 1}")) }
                val calendarIndex = pages.indexOfFirst { it.contains("SEGUNDA") && it.contains("NOVEMBRO / 2026") }
                renderer.openPage(calendarIndex).use { page ->
                    val image = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(folder(), "calendario-mensal-2.6.0.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        assertEquals(before, ProjectCodec.encode(project))
    }

    private fun overallTotals() {
        app.onNodeWithTag("payments-overview-forecast").assertIsDisplayed().assertTextEquals(400_000L.brl())
        app.onNodeWithTag("payments-overview-paid").assertIsDisplayed().assertTextEquals(110_000L.brl())
        app.onNodeWithTag("payments-overview-pending").assertIsDisplayed().assertTextEquals(290_000L.brl())
    }
    private fun tab(label: String) = app.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
    private fun folder() = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
    private fun screenshot(name: String) {
        val image = app.onRoot().captureToImage().asAndroidBitmap()
        File(folder(), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
