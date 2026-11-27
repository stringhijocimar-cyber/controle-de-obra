package br.com.obracontrol

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
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

class GanttReadabilityFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.now()

    private fun example(): Project {
        val stage = Stage(name = "Bancada e revestimentos", budget = 100_000, progress = 40,
            plannedStart = today.minusDays(7), plannedEnd = today.plusDays(21), actualStart = today.minusDays(5))
        val next = Stage(name = "Iluminação e acabamento", budget = 80_000, plannedStart = today.plusDays(15), plannedEnd = today.plusDays(70))
        val stone = Purchase(name = "Pedra São Gabriel", total = 300_000, stageId = stage.id,
            deliveryExpected = today.minusDays(10), receivedDate = today.minusDays(9))
        val grout = Purchase(name = "Rejunte e insumos", total = 100_000, stageId = stage.id,
            deliveryExpected = today.plusDays(7), receivedProgress = 20)
        return Project(name = "Gantt gerencial · dados fictícios", stages = listOf(stage, next), purchases = listOf(stone, grout),
            payments = listOf(Payment(targetId = stage.id, amount = 25_000, date = today)), referenceDate = today)
    }

    @Test fun fitZoomTodayAndDetailsWorkInFullScreenWithoutChangingTheWorkRecords() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val project = example().copy(ganttScale = GanttScale.DAYS)
        val stage = project.stages.first()
        try {
            store.save(project); app.activityRule.scenario.recreate()
            tab("Etapas"); tab("Gantt")
            app.onNodeWithTag("gantt-full-screen").performScrollTo().performClick()
            val bars = app.onNodeWithTag("gantt-bars-${stage.id}", useUnmergedTree = true)
            val originalWidth = bars.getUnclippedBoundsInRoot().let { it.right - it.left }
            app.onNodeWithTag("gantt-fit").performClick()
            app.onNodeWithTag("gantt-zoom-label").assertTextEquals("Tudo")
            val fittedWidth = bars.getUnclippedBoundsInRoot().let { it.right - it.left }
            assertTrue(fittedWidth < originalWidth)
            app.onNodeWithTag("gantt-zoom-in").performClick()
            assertTrue(bars.getUnclippedBoundsInRoot().let { it.right - it.left } > fittedWidth)
            app.onNodeWithTag("gantt-today").performClick()
            app.onNodeWithTag("gantt-general-${stage.id}", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("60%")
            bars.performClick()
            app.onNodeWithTag("gantt-details").assertIsDisplayed()
            app.onNodeWithText("Pedra São Gabriel").performScrollTo().assertIsDisplayed()
            app.onNodeWithText("Previsão: ${project.purchases.first().deliveryExpected.dateText()} · Recebido em: ${project.purchases.first().receivedDate.dateText()}")
                .performScrollTo().assertIsDisplayed()
            screenshot("Gantt-Consulta-2.8.0.png", "gantt-details")
            app.onNodeWithTag("gantt-details-close").performClick()
            app.onNodeWithTag("gantt-details").assertDoesNotExist()
            app.onNodeWithTag("full-screen-chart").assertIsDisplayed()
            app.onNodeWithTag("gantt-help").performScrollTo().performClick()
            app.onNodeWithText("Como ler o Gantt").assertIsDisplayed()
            app.onNodeWithText("Entendi").performClick()
            app.onNodeWithTag("gantt-fit").performClick()
            app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            app.waitUntil(10_000) { app.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            app.waitForIdle()
            app.onNodeWithTag("gantt-zoom-label").assertTextEquals("Tudo")
            app.onNodeWithTag("gantt-general-${stage.id}", useUnmergedTree = true).assertIsDisplayed()
            screenshot("Gantt-Gerencial-2.8.0.png", "full-screen-chart")
            assertEquals(project, store.load())
            bars.performClick()
            app.onNodeWithTag("gantt-details-edit").performClick()
            app.onNodeWithTag("full-screen-chart").assertDoesNotExist()
            app.onNodeWithText("Nome da etapa").assertIsDisplayed()
            assertEquals(project, store.load())
        } finally {
            app.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            store.save(previous)
        }
    }

    @Test fun weeklyHeaderStaysVisibleAfterFittingLongPeriodsAndReportKeepsTheProgressAndNewLaneLabels() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val project = example()
        try {
            store.save(project); app.activityRule.scenario.recreate()
            tab("Etapas"); tab("Gantt")
            app.onNodeWithTag("gantt-full-screen").performScrollTo().performClick()
            app.onNodeWithTag("gantt-fit").performClick()
            app.onAllNodes(hasTestTag("gantt-calendar-header")).assertCountEquals(1)
            app.onNodeWithTag("gantt-calendar-header").assertIsDisplayed()
            app.onNodeWithTag("gantt-zoom-out").assertIsNotEnabled()
            app.onNodeWithTag("close-full-screen").performClick()
            app.onNodeWithTag("gantt-table").performScrollTo()
            val folder = folder()
            File(folder, "Relatorio-Gantt-2.8.0-Exemplo.pdf").outputStream().use { FullReport.write(it, project, today) }
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(File(folder, "Relatorio-Gantt-2.8.0-Exemplo.pdf"),
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                    val text = (0 until pdf.pageCount).joinToString(" ") { i -> pdf.openPage(i).use { page -> page.textContents.joinToString(" ") { it.text } } }
                    File(folder, "gantt-pdf-text-2.8.0.txt").writeText(text)
                    val compact = text.filterNot { it.isWhitespace() }
                    listOf("TOTAL ACUMULADO", "MÃO DE OBRA", "MATERIAL E SERVIÇO", "Geral da etapa: 60%", "CALENDÁRIO DE DESEMBOLSOS")
                        .forEach { assertTrue(it, compact.contains(it.filterNot { char -> char.isWhitespace() })) }
                }
            }
            assertEquals(project, store.load())
        } finally { store.save(previous) }
    }

    private fun tab(label: String) = app.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
    private fun folder() = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
    private fun screenshot(name: String, tag: String) {
        val image = app.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        File(folder(), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
