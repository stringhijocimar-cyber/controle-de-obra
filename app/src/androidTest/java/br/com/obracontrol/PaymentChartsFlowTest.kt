package br.com.obracontrol

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PaymentChartsFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun piesReconcileToActualPaymentsRenderBothLegendsAndExportInTheCompleteReport() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val stage = Stage(name = "Montagem", budget = 90_000)
        val order = Purchase(name = "Materiais", total = 100_000, installmentCount = 5)
        val future = Purchase(name = "Compra futura", total = 900_000, installmentCount = 6,
            payer = "Pessoa planejada", method = "Boleto")
        val project = Project(name = "Obra de exemplo", stages = listOf(stage), purchases = listOf(order, future), payments = listOf(
            Payment(targetId = stage.id, amount = 40_000, payer = "Pessoa A", method = "Pix"),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 10_000, payer = "Pessoa A", method = "Cartão de crédito"),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 30_000, payer = "Pessoa B", method = "pix"),
            Payment(kind = CostKind.MATERIAL, targetId = order.id, amount = 20_000, payer = "Pessoa B", method = "Dinheiro")))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            openChart("payer")
            app.onNodeWithTag("payment-chart-total-payer").assertIsDisplayed().assertTextEquals(100_000L.brl())
            app.onNode(hasText("Pessoa A") and hasAnyAncestor(hasTestTag("payment-chart-payer"))).assertIsDisplayed()
            app.onNodeWithText(future.payer).assertDoesNotExist()
            assertColors("payer", 2)
            screenshot("quem-pagou.png", "payer")
            openChart("method")
            app.onNodeWithTag("payment-chart-total-method").assertIsDisplayed().assertTextEquals(100_000L.brl())
            app.onNode(hasText("70%") and hasAnyAncestor(hasTestTag("payment-chart-method"))).assertIsDisplayed()
            app.onNodeWithText(future.method).assertDoesNotExist()
            assertColors("method", 3)
            screenshot("como-foi-pago.png", "method")
            assertEquals(project, store.load())

            val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
            val report = File(folder, "graficos-pagamentos.pdf")
            report.outputStream().use { FullReport.write(it, project) }
            PdfRenderer(ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                assertTrue(pdf.pageCount >= 11)
                if (Build.VERSION.SDK_INT >= 35) {
                    val pages = (0 until pdf.pageCount).map { index ->
                        pdf.openPage(index).use { page -> page.textContents.joinToString(" ") { it.text } }
                    }
                    assertTrue(pages.any { it.contains("COMO A OBRA FOI PAGA") && it.contains("QUEM PAGOU") })
                    assertTrue(pages.any { it.contains("COMO FOI PAGO") && it.contains("70%") && it.contains("Pix") })
                    assertTrue(pages.any { it.contains("CONSOLIDADO POR PAGADOR") })
                    assertTrue(pages.any { it.contains("VISÃO GANTT") })
                }
            }

            store.save(project.copy(payments = project.payments.mapIndexed { index, payment ->
                if (index == 0) payment.copy(amount = 50_000) else payment
            }))
            app.activityRule.scenario.recreate()
            openChart("method")
            app.onNodeWithTag("payment-chart-total-method").assertTextEquals(110_000L.brl())
            store.save(project.copy(payments = emptyList()))
            app.activityRule.scenario.recreate()
            openChart("payer")
            app.onNodeWithTag("payment-chart-total-payer").assertTextEquals(0L.brl())
            app.onNodeWithTag("payment-share-payer-0").assertDoesNotExist()
        } finally {
            store.save(previous)
        }
    }

    private fun openChart(key: String) {
        app.onNode(hasScrollAction()).performScrollToNode(hasTestTag("payment-chart-$key"))
        app.onNodeWithTag("payment-chart-$key").assertIsDisplayed()
    }

    private fun assertColors(key: String, count: Int) {
        val pixels = app.onNodeWithTag("payment-pie-$key").captureToImage().toPixelMap()
        val colors = (0 until count).map { paymentChartColor(it).toArgb() }.toMutableSet()
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) colors.remove(pixels[x, y].toArgb())
        assertTrue("As fatias do gráfico precisam estar visíveis", colors.isEmpty())
    }

    private fun screenshot(name: String, key: String) {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val bitmap = app.onNodeWithTag("payment-chart-$key").captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
