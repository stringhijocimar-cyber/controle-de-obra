package br.com.obracontrol

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class ManagementFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun calendarSavesDatesAndDiaryCanBeEditedFromDashboard() {
        val today = LocalDate.now().dateText()
        app.onNodeWithText("Dados da obra").performClick()
        app.onNodeWithText("Início da obra").performClick()
        app.onNodeWithText("Hoje").performClick()
        app.onNodeWithText("Aplicar").performClick()
        app.onNodeWithText(today).assertExists()
        app.onNodeWithText("Salvar").performClick()
        app.onNodeWithText("Início $today").assertExists()

        app.onNode(hasScrollAction()).performScrollToNode(hasText("Abrir diário"))
        app.onNodeWithText("Abrir diário").performClick()
        app.onNodeWithText("Novo registro").performClick()
        app.onNodeWithText("Título").performTextInput("Vistoria de teste")
        app.onNodeWithText("Salvar").performClick()
        app.onNodeWithText("Vistoria de teste").assertExists()
        app.onNodeWithText(today).assertExists()
    }

    @Test
    fun backupRestoresDiaryAttachmentAndReportOpensInAndroid() {
        val context = app.activity.applicationContext
        val files = DocumentFiles(context)
        val backups = PortableBackup(files)
        val bytes = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW/UAAAAASUVORK5CYII=", 0,
        )
        val photo = files.save(ByteArrayInputStream(bytes), "vistoria-teste.png")
        val today = LocalDate.of(2026, 11, 16)
        val stage = Stage(name = "Instalação do forro", budget = 120_000, plannedStart = today, plannedEnd = today.plusDays(2))
        val payment = Payment(targetId = stage.id, amount = 20_000, date = today)
        val project = Project(name = "Obra teste", stages = listOf(stage), payments = listOf(payment),
            purchases = listOf(Purchase(name = "Telhas", deliveryExpected = today, payer = "Pagador de teste", method = "Pix")),
            payerNames = listOf("Pagador de teste"),
            diaryEntries = listOf(DiaryEntry(date = today, title = "Vistoria", attachments = listOf(photo))))
        val output = ByteArrayOutputStream()
        backups.write(output, project)
        assertTrue(files.requireFile(photo).delete())
        val restored = backups.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(project, restored)
        assertTrue(bytes.contentEquals(files.requireFile(photo).readBytes()))

        val report = File.createTempFile("relatorio-teste", ".pdf", context.cacheDir)
        try {
            report.outputStream().use { FullReport.write(it, restored, today) }
            val fd = ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(fd).use { pdf ->
                assertTrue(pdf.pageCount >= 10)
                if (Build.VERSION.SDK_INT >= 35) {
                    val pages = (0 until pdf.pageCount).map { index ->
                        pdf.openPage(index).use { page -> page.textContents.joinToString(" ") { it.text } }
                    }
                    val gantt = pages.single { it.contains("VISÃO GANTT") }
                    assertTrue(gantt.contains(stage.name) && gantt.replace('\u00a0', ' ').contains(120_000L.brl()))
                    assertTrue(pages.any { it.contains("DESEMBOLSOS DE MÃO DE OBRA POR SEMANA") })
                    assertTrue(pages.any { it.contains("Estimativa pelo fim previsto") })
                    assertTrue(pages.any { it.contains("Método de pagamento") && it.contains("Pagador de teste") })
                }
            }
        } finally {
            report.delete()
        }
    }
}
