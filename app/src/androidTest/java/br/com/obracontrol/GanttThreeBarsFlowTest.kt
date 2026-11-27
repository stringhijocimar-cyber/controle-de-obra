package br.com.obracontrol

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class GanttThreeBarsFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.now()
    private fun example(): Project {
        val stage = Stage(name = "Bancada gourmet", progress = 60, budget = 200_000,
            plannedStart = today.minusDays(7), plannedEnd = today.plusDays(14), actualStart = today.minusDays(5))
        val material = Purchase(name = "Pedra São Gabriel", total = 100_000, stageId = stage.id,
            deliveryExpected = today.minusDays(2), receivedDate = today.minusDays(1))
        val service = Purchase(name = "Fabricação e instalação", nature = CostNature.SERVICE, total = 100_000, stageId = stage.id,
            deliveryExpected = today.plusDays(7), receivedProgress = 60)
        return Project(name = "Gantt com três barras · dados fictícios", stages = listOf(stage), purchases = listOf(material, service),
            payments = listOf(Payment(targetId = stage.id, amount = 50_000, date = today)))
    }

    @Test fun weeksAreCompactDaysExpandAndServiceCompletionUpdatesTheThreeBarsWithoutNewPayments() {
        val store = ProjectStore(app.activity); val previous = store.load(); val project = example(); val stage = project.stages.single()
        try {
            store.save(project); app.activityRule.scenario.recreate()
            tab("Etapas"); tab("Gantt")
            app.onNodeWithTag("gantt-full-screen").performScrollTo().performClick()
            val bars = app.onNodeWithTag("gantt-bars-${stage.id}", useUnmergedTree = true)
            bars.assert(hasContentDescription("Total acumulado: 70%", substring = true))
            bars.assert(hasContentDescription("Mão de obra: 60%", substring = true))
            bars.assert(hasContentDescription("Material e serviço: 80%", substring = true))
            app.onNodeWithTag("gantt-zoom-label").assertTextEquals("Auto")
            val week = bars.getUnclippedBoundsInRoot(); val weekWidth = week.right - week.left; val weekHeight = week.bottom - week.top
            tab("Dias")
            app.onNodeWithTag("gantt-zoom-label").assertTextEquals("Auto")
            val day = bars.getUnclippedBoundsInRoot()
            assertTrue(day.right - day.left > weekWidth)
            assertTrue(day.bottom - day.top > weekHeight)
            tab("Semanas")
            app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            app.waitUntil(10_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            app.waitForIdle()
            screenshot("Gantt-Semanas-2.9.0.png")
            tab("Dias"); screenshot("Gantt-Dias-2.9.0.png"); tab("Semanas")
            app.onNodeWithTag("close-full-screen").performClick()
            app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            app.waitUntil(10_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            app.waitForIdle()
            tab("Compras")
            app.onNodeWithTag("financial-table-body").performScrollToNode(hasTestTag("table-row-${project.purchases.last().id}"))
            app.onNodeWithTag("table-row-${project.purchases.last().id}").performSemanticsAction(SemanticsActions.OnClick) { it() }
            app.onNodeWithText("Serviço concluído (%)").performScrollTo().performTextReplacement("100")
            app.onNodeWithText("Salvar").performSemanticsAction(SemanticsActions.OnClick) { it() }
            tab("Etapas"); tab("Gantt")
            app.onNodeWithTag("gantt-table").performScrollTo()
            app.onNodeWithTag("gantt-general-${stage.id}", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("80%")
            app.onNodeWithTag("gantt-materials-${stage.id}", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("100%")
            val saved = store.load()
            assertEquals(project.payments, saved.payments)
            assertEquals(project.scheduledPayments, saved.scheduledPayments)
            assertEquals(60, saved.stages.single().progress)
            assertEquals(saved, ProjectCodec.decode(ProjectCodec.encode(saved)))
        } finally {
            app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            store.save(previous)
        }
    }

    @Test fun fullReportIncludesExactlyTheThreeProgressComponentsAndCompleteFinancialSections() {
        val project = example(); val before = ProjectCodec.encode(project)
        val file = File(folder(), "Relatorio-Gantt-2.9.0-Exemplo.pdf")
        file.outputStream().use { FullReport.write(it, project, today) }
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            if (Build.VERSION.SDK_INT >= 35) {
                val pages = (0 until pdf.pageCount).map { i -> pdf.openPage(i).use { page -> page.textContents.joinToString(" ") { it.text } } }
                val text = pages.joinToString(" ").filterNot { it.isWhitespace() }
                listOf("TOTAL ACUMULADO", "MÃO DE OBRA", "MATERIAL E SERVIÇO", "70%", "60%", "80%", "CALENDÁRIO DE DESEMBOLSOS", "PRESTAÇÃO DE CONTAS")
                    .forEach { assertTrue(it, text.contains(it.filterNot { c -> c.isWhitespace() })) }
                val chart = pages.first { it.contains("VISÃO GANTT") }
                assertFalse(chart.contains("TEMPO / PRAZO TOTAL"))
                pages.forEachIndexed { i, value -> assertTrue(value.filterNot { it.isWhitespace() }.contains("Página${i + 1}")) }
            }
        }
        assertEquals(before, ProjectCodec.encode(project))
    }

    private fun tab(label: String) = app.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        .performSemanticsAction(SemanticsActions.OnClick) { it() }
    private fun folder() = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
    private fun screenshot(name: String) {
        val bitmap = app.onNodeWithTag("full-screen-chart").captureToImage().asAndroidBitmap()
        File(folder(), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
