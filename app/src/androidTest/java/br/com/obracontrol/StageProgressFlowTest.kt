package br.com.obracontrol

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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class StageProgressFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test fun changingPartialReceiptsAndStageWeightsUpdatesGanttReportAndBackupWithoutNewPayments() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val today = LocalDate.now()
        val stage = Stage(name = "Revestimento de exemplo", budget = 100_000, progress = 40,
            plannedStart = today.minusDays(3), plannedEnd = today.plusDays(7), actualStart = today.minusDays(2))
        val tiles = Purchase(name = "Piso de exemplo", stageId = stage.id, total = 300_000, receivedProgress = 100)
        val grout = Purchase(name = "Rejunte de exemplo", stageId = stage.id, total = 100_000, receivedProgress = 20)
        val service = Purchase(name = "Serviço de exemplo", nature = CostNature.SERVICE, stageId = stage.id,
            total = 900_000, receivedDate = today)
        val project = Project(name = "Obra de exemplo - dados fictícios", stages = listOf(stage), purchases = listOf(tiles, grout, service),
            payments = listOf(Payment(targetId = stage.id, amount = 50_000, date = today, payer = "Pessoa A", method = "Pix")))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            gantt()
            values(stage.id, 40.0, 80.0, 60.0)
            screenshot("gantt-evolucao-2.5.0.png")
            navigate("Compras")
            app.onNodeWithTag("table-row-${grout.id}").performSemanticsAction(SemanticsActions.OnClick) { it() }
            app.onNodeWithText("Material recebido (%)").performScrollTo().performTextReplacement("60")
            save()
            assertEquals(60, store.load().purchases.first { it.id == grout.id }.receivedProgress)
            gantt()
            values(stage.id, 40.0, 90.0, 65.0)
            app.onNodeWithText(stage.name).performClick()
            app.onNodeWithText("Peso dos materiais no avanço geral (%)").performScrollTo().performTextReplacement("40")
            save()
            values(stage.id, 40.0, 90.0, 60.0)
            val saved = store.load()
            assertEquals(project.payments, saved.payments)
            assertEquals(project.scheduledPayments, saved.scheduledPayments)
            assertEquals(40, saved.stages.single().progress)
            assertNull(saved.stages.single().actualEnd)
            val backups = PortableBackup(DocumentFiles(app.activity))
            val output = ByteArrayOutputStream()
            backups.write(output, saved)
            assertEquals(saved, backups.read(ByteArrayInputStream(output.toByteArray())))
            val pdf = File(folder(), "gantt-evolucao-2.5.0.pdf")
            pdf.outputStream().use { FullReport.write(it, saved, today) }
            PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                if (Build.VERSION.SDK_INT >= 35) {
                    val pages = (0 until renderer.pageCount).map { index -> renderer.openPage(index).use { page -> page.textContents.joinToString(" ") { it.text } } }
                    val chart = pages.first { it.contains("VISÃO GANTT") }
                    assertTrue(chart.contains("Execução: ${stagePercent(40.0)}"))
                    assertTrue(chart.contains("Materiais: ${stagePercent(90.0)}"))
                    assertTrue(chart.contains("Geral da etapa: ${stagePercent(60.0)}"))
                    assertTrue(pages.any { it.contains("EVOLUÇÃO DAS ETAPAS") })
                }
            }
            app.activityRule.scenario.recreate()
            assertEquals(saved, store.load())
        } finally { store.save(previous) }
    }

    @Test fun datedLegacyReceiptsCanBecomePartialAndStagesWithoutDatesStillShowTheThreeIndicators() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val stage = Stage(name = "Etapa sem datas", progress = 70)
        val purchase = Purchase(name = "Entrega antiga", stageId = stage.id, total = 10_000, receivedDate = LocalDate.now())
        try {
            store.save(Project(stages = listOf(stage), purchases = listOf(purchase)))
            app.activityRule.scenario.recreate()
            gantt()
            values(stage.id, 70.0, 100.0, 85.0)
            navigate("Compras")
            app.onNodeWithTag("table-row-${purchase.id}").performSemanticsAction(SemanticsActions.OnClick) { it() }
            app.onNodeWithText("Material recebido (%)").performScrollTo().performTextReplacement("20")
            save()
            assertNull(store.load().purchases.single().receivedDate)
            assertEquals(20, store.load().purchases.single().receivedProgress)
            gantt()
            values(stage.id, 70.0, 20.0, 45.0)
            assertTrue(store.load().payments.isEmpty())
        } finally { store.save(previous) }
    }

    private fun navigate(label: String) {
        app.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
    }
    private fun gantt() { navigate("Etapas"); app.onNodeWithText("Gantt").performSemanticsAction(SemanticsActions.OnClick) { it() } }
    private fun save() { app.onNodeWithText("Salvar").performSemanticsAction(SemanticsActions.OnClick) { it() } }
    private fun values(id: String, execution: Double, materials: Double, general: Double) {
        app.onNodeWithTag("gantt-execution-$id", useUnmergedTree = true).assertTextEquals(stagePercent(execution))
        app.onNodeWithTag("gantt-materials-$id", useUnmergedTree = true).assertTextEquals(stagePercent(materials))
        app.onNodeWithTag("gantt-general-$id", useUnmergedTree = true).assertTextEquals(stagePercent(general))
    }
    private fun folder() = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
    private fun screenshot(name: String) {
        val image = app.onRoot().captureToImage().asAndroidBitmap()
        File(folder(), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
