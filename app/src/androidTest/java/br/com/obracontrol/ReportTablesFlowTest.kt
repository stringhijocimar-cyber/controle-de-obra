package br.com.obracontrol

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

class ReportTablesFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.of(2026, 11, 26)

    @Test fun completeReportUsesTableTotalsWithoutChangingDataOrLosingSections() {
        val stage = Stage(name = "Acabamento e instalações", budget = 600_000, progress = 35,
            plannedStart = today.minusDays(7), plannedEnd = today.plusDays(21), actualStart = today.minusDays(5))
        val tiled = Purchase(name = "Revestimento parcelado", supplier = "Fornecedor de exemplo A", document = "Exemplo 001",
            total = 600_000, installmentCount = 6, stageId = stage.id, method = "Cartão", payer = "Pessoa A",
            lines = listOf(PurchaseLine(name = "Revestimento - item de exemplo", quantity = BigDecimal(60), unit = "m²", unitPrice = 10_000)))
        val material = Purchase(name = "Materiais de acabamento", supplier = "Fornecedor de exemplo B", total = 300_000,
            stageId = stage.id, dueDate = today.minusDays(2), method = "Pix", payer = "Pessoa B")
        val service = Purchase(name = "Instalação elétrica", supplier = "Prestador de exemplo", total = 80_000,
            nature = CostNature.SERVICE, stageId = stage.id, extra = true)
        val unknown = Purchase(name = "Marcenaria a cotar", stageId = stage.id)
        val plans = buildPaymentSchedule(CostKind.MATERIAL, tiled.id, 600_000, 6, today, false) +
            buildPaymentSchedule(CostKind.LABOR, stage.id, 600_000, 3, today, true)
        val project = Project(name = "Obra de exemplo - dados fictícios", start = today.minusDays(10), referenceDate = today,
            laborBudget = 600_000, stages = listOf(stage), purchases = listOf(tiled, material, service, unknown), scheduledPayments = plans,
            payments = listOf(Payment(targetId = stage.id, amount = 200_000, date = today, payer = "Pessoa B", method = "Pix"),
                Payment(kind = CostKind.MATERIAL, targetId = tiled.id, amount = 100_000, date = today, payer = "Pessoa A", method = "Cartão", scheduleId = plans.first().id),
                Payment(kind = CostKind.MATERIAL, targetId = service.id, amount = 100_000, date = today, payer = "Pessoa B", method = "Pix")),
            diaryEntries = listOf(DiaryEntry(date = today, title = "Vistoria de exemplo", stageId = stage.id,
                activities = "Conferência do acabamento e das instalações.")))
        project.validate()
        val encoded = ProjectCodec.encode(project)
        val pages = export(project, "relatorio-tabelas-exemplo-2.4.0.pdf")
        if (Build.VERSION.SDK_INT >= 35) {
            val text = pages.joinToString(" ").replace(Regex("\\s+"), " ")
            listOf("RESUMO GERENCIAL", "QUEM PAGOU", "COMO FOI PAGO", "VISÃO GANTT", "CRONOGRAMA FÍSICO-FINANCEIRO",
                "CONTROLE DE PARCELAS", "CALENDÁRIO DE DESEMBOLSOS", "PRESTAÇÃO DE CONTAS", "PENDÊNCIAS", "AGENDA DA OBRA", "DIÁRIO DE OBRA",
                "TOTAL DOS ITENS", "TOTAL DA OBRA", "PAGO A MAIOR", "A definir", "Pago a maior", "Atrasado").forEach { assertTrue(it, text.contains(it)) }
            val accounts = pages.filter { it.contains("PRESTAÇÃO DE CONTAS") }.joinToString(" ")
            assertTrue(accounts.contains(400_000L.brl()))
            assertTrue(text.contains(20_000L.brl()))
            assertTrue(text.contains("+ ?"))
            pages.filter { it.contains("RESUMO DAS COMPRAS") }.forEach { page ->
                assertTrue("O título deve acompanhar o cabeçalho da tabela", page.contains("COMPRA / FORNECEDOR"))
            }
        }
        assertEquals(encoded, ProjectCodec.encode(project))
        assertEquals(35, project.stages.single().progress)
    }

    @Test fun repeatedHeadersAndLongRowsPreserveAllRecordsAndUndefinedAmounts() {
        val purchases = (1..18).map { index -> Purchase(name = "REFERENCIA-${index.toString().padStart(2, '0')}",
            total = if (index == 18) null else 10_000L * index, nature = if (index % 2 == 0) CostNature.SERVICE else CostNature.MATERIAL) }
        val project = Project(name = "Teste de paginação - dados fictícios", purchases = purchases,
            issues = listOf(Issue(title = "Registro com observação extensa", action = "INICIO-OBSERVACAO " +
                List(260) { "Conferir materiais e serviços antes da aprovação do orçamento." }.joinToString(" ") + " FIM-OBSERVACAO-240")))
        val pages = export(project, "relatorio-tabelas-paginacao-2.4.0.pdf")
        if (Build.VERSION.SDK_INT >= 35) {
            val text = pages.joinToString(" ")
            purchases.forEach { assertTrue(it.name, text.contains(it.name)) }
            assertTrue(text.contains("INICIO-OBSERVACAO"))
            assertTrue(text.contains("FIM-OBSERVACAO-240"))
            val issuePages = pages.filter { it.contains("PRÓXIMAS DECISÕES") && it.contains("AÇÃO / OBSERVAÇÕES") }
            assertTrue("A observação extensa deve ocupar mais de uma página", issuePages.size >= 2)
            assertTrue(text.contains("+ ?"))
        }
        export(Project(name = "Obra vazia - exemplo"), "relatorio-tabelas-vazio-2.4.0.pdf")
    }

    private fun export(project: Project, name: String): List<String> {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val report = File(folder, name)
        report.outputStream().use { FullReport.write(it, project, today) }
        return PdfRenderer(ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            assertTrue(pdf.pageCount >= 10)
            (0 until pdf.pageCount).map { index -> pdf.openPage(index).use { page ->
                assertEquals(842, page.width)
                assertEquals(595, page.height)
                if (Build.VERSION.SDK_INT >= 35) page.textContents.joinToString(" ") { it.text } else ""
            } }
        }
    }
}
