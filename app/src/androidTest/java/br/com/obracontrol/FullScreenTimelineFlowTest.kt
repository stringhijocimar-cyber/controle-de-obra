package br.com.obracontrol

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class FullScreenTimelineFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.of(2026, 11, 27)

    private fun example(): Project {
        val stage = Stage(name = "Bancada e acabamentos", budget = 200_000, progress = 40,
            plannedStart = today.minusDays(7), plannedEnd = today.plusDays(14), actualStart = today.minusDays(5))
        val other = Stage(name = "Instalações finais", budget = 60_000, plannedStart = today.plusDays(8), plannedEnd = today.plusDays(21))
        val stone = Purchase(name = "Pedra e insumos", total = 150_000, stageId = stage.id,
            deliveryExpected = today.minusDays(9), receivedDate = today.minusDays(8), dueDate = today,
            method = "Pix", payer = "Pessoa A")
        val grout = Purchase(name = "Revestimento e rejunte", total = 50_000, stageId = stage.id,
            deliveryExpected = today.plusDays(7), receivedProgress = 20, dueDate = today.plusDays(8))
        val service = Purchase(name = "Vidro sob encomenda", nature = CostNature.SERVICE, total = 240_000,
            stageId = other.id, dueDate = today.plusDays(1), installmentCount = 3, method = "Cartão", payer = "Pessoa B")
        return Project(name = "Área gourmet · exemplo com dados fictícios", start = today.minusDays(15),
            stages = listOf(stage, other), purchases = listOf(stone, grout, service, Purchase(name = "Orçamento a confirmar")),
            scheduledPayments = buildPaymentSchedule(CostKind.MATERIAL, service.id, 240_000, 3, today.plusDays(1), false),
            payments = listOf(Payment(targetId = stage.id, amount = 60_000, date = today, payer = "Pessoa A", method = "Pix"),
                Payment(kind = CostKind.MATERIAL, targetId = stone.id, amount = 150_000, date = today.minusDays(1), payer = "Pessoa A", method = "Pix")),
            diaryEntries = listOf(DiaryEntry(date = today, title = "Conferência do exemplo", activities = "Verificar entregas e execução por etapa.")))
    }

    @Test fun paymentCalendarKeepsCategoryModePageAndLayersWhenExpandedRotatedAndClosed() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val base = example()
        val project = base.copy(purchases = base.purchases + Purchase(name = "Serviço futuro", nature = CostNature.SERVICE,
            total = 10_000, dueDate = today.plusDays(200)))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            tab("Pagamentos")
            app.onNodeWithTag("payments-category-SERVICE").performScrollTo().performClick()
            tab("Semanas")
            app.onNodeWithTag("flow-full-screen").performScrollTo().performClick()
            app.onNodeWithTag("full-screen-chart").assertIsDisplayed()
            mode("A pagar").performClick()
            app.onNodeWithTag("flow-pending-total", useUnmergedTree = true).assertTextEquals(250_000L.brl())
            app.onNodeWithTag("flow-table-body").performScrollToNode(hasTestTag("flow-toggle-stage-SERVICE-${base.stages[1].id}"))
            app.onNodeWithTag("flow-toggle-stage-SERVICE-${base.stages[1].id}").performClick()
            app.onNodeWithTag("flow-table-body").performScrollToNode(hasText("Vidro sob encomenda"))
            screenshot("Desembolsos-Tela-Cheia-2.7.0.png")
            app.onNodeWithText("Próximo ›").performClick()
            app.onNodeWithText("2 / 2").assertIsDisplayed()
            app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            app.waitUntil(10_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            app.waitForIdle()
            app.onNodeWithTag("full-screen-chart").assertIsDisplayed()
            app.onNodeWithText("2 / 2").assertIsDisplayed()
            mode("A pagar").assertIsSelected()
            app.onNodeWithTag("flow-pending-total", useUnmergedTree = true).assertTextEquals(250_000L.brl())
            screenshot("Desembolsos-Paisagem-2.7.0.png")
            app.onNodeWithTag("close-full-screen").performClick()
            app.onNodeWithTag("full-screen-chart").assertDoesNotExist()
            mode("A pagar").assertIsSelected()
            assertEquals(project, store.load())
            app.onNodeWithTag("flow-full-screen").performScrollTo().performClick()
            app.waitUntil(5_000) { !app.activity.window.decorView.hasWindowFocus() }
            app.waitForIdle()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            app.waitUntil(5_000) { app.onAllNodesWithTag("full-screen-chart").fetchSemanticsNodes().isEmpty() }
            app.onNodeWithTag("full-screen-chart").assertDoesNotExist()
            assertEquals(project, store.load())
        } finally {
            app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            store.save(previous)
        }
    }

    @Test fun ganttFullScreenShowsDeliveryMilestonesDurationAndProgressAndReturnsToTheSameStage() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val project = example()
        val stage = project.stages.first()
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            tab("Etapas")
            tab("Gantt")
            app.onNodeWithTag("gantt-full-screen").performScrollTo().performClick()
            app.onNodeWithTag("gantt-general-${stage.id}", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("60%")
            val bars = app.onNodeWithTag("gantt-bars-${stage.id}", useUnmergedTree = true)
            bars.assert(hasContentDescription("Material Pedra e insumos", substring = true))
            bars.assert(hasContentDescription("recebido 19/11/2026", substring = true))
            bars.assert(hasContentDescription("Fim da execução previsto 11/12/2026, real A definir", substring = true))
            screenshot("Gantt-Tela-Cheia-2.7.0.png")
            app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            app.waitUntil(10_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            app.waitForIdle()
            app.onNodeWithTag("full-screen-chart").assertIsDisplayed()
            app.onNodeWithTag("gantt-general-${stage.id}", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("60%")
            screenshot("Gantt-Paisagem-2.7.0.png")
            tab("Meses")
            assertEquals(GanttScale.MONTHS, store.load().ganttScale)
            app.onNodeWithText(stage.name).performClick()
            app.onNodeWithTag("full-screen-chart").assertDoesNotExist()
            app.onNodeWithText("Nome da etapa").assertIsDisplayed()
            assertEquals(project.copy(ganttScale = GanttScale.MONTHS), store.load())
        } finally {
            app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            store.save(previous)
        }
    }

    @Test fun reportSharesTheWeeklyClosingAndDeliveryDatesAndKeepsAllSectionsAndFooters() {
        val project = example()
        val before = ProjectCodec.encode(project)
        val file = File(folder(), "Relatorio-Gerencial-2.7.0-Exemplo.pdf")
        file.outputStream().use { FullReport.write(it, project, today) }
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            if (Build.VERSION.SDK_INT >= 35) {
                val pages = (0 until pdf.pageCount).map { index -> pdf.openPage(index).use { page -> page.textContents.joinToString(" ") { it.text } } }
                val text = pages.joinToString(" ").replace(Regex("\\s+"), " ")
                listOf("ETAPA / DESCRIÇÃO", "27/11", "04/12", "TOTAL DA OBRA", "Serviço terceirizado", "SEM DATA DEFINIDA",
                    "VISÃO GANTT", "Entregas:", "PRESTAÇÃO DE CONTAS", "DIÁRIO DE OBRA", "AGENDA DO CALENDÁRIO SEMANAL").forEach { assertTrue(it, text.contains(it)) }
                assertFalse(text.contains("SEGUNDA") && text.contains("DOMINGO"))
                pages.forEachIndexed { index, page -> assertTrue("Rodapé ${index + 1}", page.filterNot { it.isWhitespace() }.contains("Página${index + 1}")) }
                val calendarPages = pages.indices.filter { pages[it].contains("CALENDÁRIO DE DESEMBOLSOS | SEMANAS") }
                assertTrue(calendarPages.isNotEmpty())
                calendarPages.forEach { assertTrue(pages[it].contains("ETAPA / DESCRIÇÃO")); assertTrue(pages[it].contains("TOTAL DA OBRA")) }
            }
        }
        assertEquals(before, ProjectCodec.encode(project))
    }

    private fun mode(label: String) = app.onNode(hasText(label) and hasAnyAncestor(hasTestTag("flow-screen")) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private fun tab(label: String) = app.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
    private fun folder() = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
    private fun screenshot(name: String) {
        val image = app.onNodeWithTag("full-screen-chart").captureToImage().asAndroidBitmap()
        File(folder(), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
