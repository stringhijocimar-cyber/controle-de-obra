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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

class WorkPlanningFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    private fun pricedPlan(): WorkPlan {
        fun item(name: String, type: CostCategory, cents: Long) = PlanInput(name = name, category = type,
            quantity = BigDecimal("20"), unit = "m²", unitPrice = cents, priceDate = LocalDate.now(),
            priceSource = "Cotação de exemplo · MG")
        return WorkPlan(title = "Reforma de exemplo", location = "Cidade / MG", scope = "Revestir o ambiente de 20 m²",
            stages = listOf(PlanStage(name = "Assentamento de piso", services = listOf(
                PlanService(name = "Materiais e execução", items = listOf(item("Revestimento", CostCategory.MATERIAL, 5_000),
                    item("Assentamento", CostCategory.LABOR, 3_000))),
                PlanService(name = "Logística", items = listOf(PlanInput(name = "Frete", category = CostCategory.SERVICE,
                    quantity = BigDecimal.ONE, unit = "serviço", unitPrice = 10_000, priceDate = LocalDate.now(), priceSource = "Cotação de exemplo · MG"))),
            ))))
    }

    @Test fun modelCreatesEditableLayersAndAiImportAddsOnlyDraftsWithoutReplacingTheWork() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val existing = Stage(name = "Etapa já cadastrada", budget = 40_000)
        val payment = Payment(targetId = existing.id, amount = 10_000, date = LocalDate.now())
        val project = Project(name = "Obra de exemplo", stages = listOf(existing), payments = listOf(payment))
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            openPlanning()
            app.onNodeWithText("Novo").performClick()
            app.onNodeWithText("Tipo de obra").performClick()
            app.onNodeWithText("Revestimento").performClick()
            app.onNodeWithText("Nome do planejamento").performTextInput("Piso do ambiente")
            app.onNodeWithText("O que deseja executar?").performTextInput("Assentar revestimento cerâmico")
            app.onNodeWithText("Cidade / UF").performTextInput("Cidade / MG")
            app.onNodeWithText("Área de referência (m², opcional)").performScrollTo().performTextInput("20")
            saveForm()
            val first = store.load().workPlans.single()
            scrollTo("work-plan-${first.id}")
            app.onNodeWithTag("work-plan-${first.id}").performClick()
            screenshot("planejamento-etapas.png")
            val stage = first.stages.single { it.name == "Assentamento e rejuntamento" }
            scrollTo("plan-stage-${stage.id}")
            app.onNodeWithTag("plan-stage-${stage.id}").performClick()
            val service = stage.services.single()
            scrollTo("plan-service-toggle-${service.id}")
            app.onNodeWithTag("plan-service-toggle-${service.id}").performClick()
            val input = service.items.first()
            scrollTo("plan-input-${input.id}")
            app.onNodeWithTag("plan-input-${input.id}").performClick()
            app.onNodeWithText("Preço unitário (R$)").performScrollTo().performTextInput("50")
            app.onNodeWithText("Fonte / fornecedor da cotação").performScrollTo().performTextInput("Cotação de exemplo")
            saveForm()
            val revised = store.load().workPlans.single().stages.single { it.id == stage.id }
            assertEquals(0, BigDecimal("22").compareTo(revised.items.first().quantity))
            assertEquals(110_000L, revised.items.first().subtotal)
            assertFalse(revised.approved)
            assertEquals(project.stages, store.load().stages)
            assertEquals(project.payments, store.load().payments)
            app.onNodeWithText("‹ Voltar").performClick()
            app.onNodeWithText("Planejar com ChatGPT").performScrollTo().performClick()
            app.onNodeWithText("Copiar roteiro para IA").performClick()
            screenshot("planejamento-ia.png")
            app.onNodeWithText("Importar resposta da IA").performClick()
            val root = WorkPlanCodec.encode(listOf(pricedPlan())).getJSONObject(0).put("format", "obracontrol-planning-v1")
            app.onNodeWithText("Resposta da IA").performTextInput(root.toString())
            hideKeyboard()
            app.onNodeWithTag("preview-ai-plan").performScrollTo().performClick()
            saveForm()
            val imported = store.load()
            assertEquals(2, imported.workPlans.size)
            assertFalse(imported.workPlans.last().stages.single().approved)
            assertNull(imported.workPlans.last().stages.single().linkedStageId)
            assertEquals(project.stages, imported.stages)
            assertEquals(project.payments, imported.payments)
            assertTrue(imported.purchases.isEmpty())
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    @Test fun approvalActivationPaymentAndRevisionKeepBaselineAndBackupConsistent() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val plan = pricedPlan()
        val stage = plan.stages.single()
        try {
            store.save(Project(name = "Obra de exemplo", workPlans = listOf(plan)))
            app.activityRule.scenario.recreate()
            openPlanning()
            app.onNodeWithTag("work-plan-${plan.id}").performClick()
            app.onNodeWithTag("plan-stage-${stage.id}").performClick()
            app.onNodeWithTag("approve-plan-stage").performScrollTo().performClick()
            app.onNodeWithText("Responsável pela aprovação").performScrollTo().performTextInput("Pessoa A")
            saveForm()
            assertTrue(store.load().workPlans.single().stages.single().approved)
            assertTrue(store.load().stages.isEmpty() && store.load().purchases.isEmpty() && store.load().payments.isEmpty())
            app.onNodeWithTag("activate-plan-stage").performScrollTo().performClick()
            saveForm()
            val active = store.load()
            assertEquals(1, active.stages.size)
            assertEquals(2, active.purchases.size)
            assertEquals(170_000L, Balance(active).total)
            app.onNodeWithText("Pagamentos da mão de obra").performScrollTo().performClick()
            app.onNodeWithText("Marcar como pago").performScrollTo().performClick()
            saveForm()
            app.onNodeWithText("‹ Voltar").performClick()
            scrollTo("planning-stage-summary")
            screenshot("planejamento-controle.png")
            val service = stage.services.first()
            scrollTo("plan-service-toggle-${service.id}")
            app.onNodeWithTag("plan-service-toggle-${service.id}").performClick()
            val input = service.items.first()
            scrollTo("plan-input-${input.id}")
            screenshot("planejamento-camadas.png")
            app.onNodeWithTag("plan-input-${input.id}").performClick()
            app.onNodeWithText("Preço unitário (R$)").performScrollTo().performTextReplacement("55")
            saveForm()
            val saved = store.load()
            val revised = saved.workPlans.single().stages.single()
            assertFalse(revised.approved)
            assertEquals(170_000L, revised.latestApproval!!.total)
            assertEquals(180_000L, revised.knownTotal)
            assertEquals(170_000L, saved.planControl(revised)!!.forecast)
            assertEquals(60_000L, saved.planControl(revised)!!.paid)
            assertEquals(active.stages, saved.stages)
            assertEquals(active.purchases, saved.purchases)
            val archive = ByteArrayOutputStream()
            val backups = PortableBackup(DocumentFiles(app.activity))
            backups.write(archive, saved)
            assertEquals(saved, backups.read(ByteArrayInputStream(archive.toByteArray())))
            val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
            val report = File(folder, "planejamento-relatorio.pdf")
            report.outputStream().use { FullReport.write(it, saved) }
            PdfRenderer(ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                if (Build.VERSION.SDK_INT >= 35) {
                    val text = (0 until pdf.pageCount).joinToString("\n") { index -> pdf.openPage(index).use { it.textContents.joinToString(" ") { block -> block.text } } }
                    assertTrue(text.contains("PLANEJAMENTO E ORÇAMENTO POR ETAPA"))
                    assertTrue(text.contains("HISTÓRICO DE APROVAÇÃO"))
                    assertTrue(text.contains("CALENDÁRIO DE DESEMBOLSOS"))
                    assertTrue(text.contains("PAGAMENTOS | PRESTAÇÃO DE CONTAS") || text.contains("PAGAMENTOS  |  PRESTAÇÃO DE CONTAS"))
                }
            }
            app.activityRule.scenario.recreate()
            assertEquals(saved, store.load())
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    @Test fun approvingAndLinkingAnExistingStageNeverRecreatesItsCostsOrPayments() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val plan = pricedPlan()
        val stage = plan.stages.single()
        val existing = Stage(name = "Etapa já em execução", budget = 80_000, progress = 45)
        val order = Purchase(name = "Compra já registrada", stageId = existing.id, total = 90_000)
        val project = Project(name = "Obra de exemplo", stages = listOf(existing), purchases = listOf(order), laborBudget = 200_000,
            payments = listOf(Payment(targetId = existing.id, amount = 20_000, date = LocalDate.now())), workPlans = listOf(plan))
            .approvePlanStage(plan.id, stage.id, "Pessoa A", LocalDate.now(), "")
        try {
            store.save(project)
            app.activityRule.scenario.recreate()
            openPlanning()
            app.onNodeWithTag("work-plan-${plan.id}").performClick()
            app.onNodeWithTag("plan-stage-${stage.id}").performClick()
            app.onNodeWithTag("activate-plan-stage").performScrollTo().performClick()
            app.onNodeWithText("Como acompanhar a execução?").performClick()
            app.onNodeWithText(existing.name).performClick()
            saveForm()
            val saved = store.load()
            assertEquals(project.stages, saved.stages)
            assertEquals(project.purchases, saved.purchases)
            assertEquals(project.payments, saved.payments)
            assertEquals(project.laborBudget, saved.laborBudget)
            assertEquals(existing.id, saved.workPlans.single().stages.single().linkedStageId)
        } catch (failure: Throwable) { captureFailure(); throw failure }
        finally { store.save(previous) }
    }

    private fun openPlanning() {
        scrollTo("open-planning")
        app.onNodeWithTag("open-planning").performClick()
    }
    private fun scrollTo(tag: String) {
        app.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        app.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }
    private fun hideKeyboard() {
        app.activity.runOnUiThread { app.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(app.activity.window.decorView.windowToken, 0) }
        app.waitForIdle()
    }
    private fun saveForm() {
        hideKeyboard()
        app.onNodeWithText("Salvar").performSemanticsAction(SemanticsActions.OnClick) { it() }
        app.waitForIdle()
    }
    private fun screenshot(name: String) {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        val bitmap = app.onRoot().captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun captureFailure() {
        val folder = File(app.activity.filesDir, "screenshots").apply { mkdirs() }
        runCatching { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "planejamento-falha.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } }
        File(folder, "planejamento-falha.txt").writeText(runCatching { app.onRoot(useUnmergedTree = true).printToString() }.getOrDefault("Sem árvore"))
    }
}
