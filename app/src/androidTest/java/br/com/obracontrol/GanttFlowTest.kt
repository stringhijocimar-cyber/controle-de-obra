package br.com.obracontrol

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.previousOrSame

class GanttFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun ganttDrawsThePeriodsAndOpensTheSameStageAfterScrolling() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val today = LocalDate.now()
        val stage = Stage(name = "Cobertura de teste", plannedStart = today.minusDays(10), plannedEnd = today.plusDays(100),
            actualStart = today.minusDays(7), actualEnd = today.minusDays(2), progress = 100,
            budget = 260_000, dueDate = today.minusDays(1))
        val paid = Payment(targetId = stage.id, amount = 60_000, date = today.minusDays(6))
        val next = Stage(name = "Sem datas de teste")
        try {
            store.save(Project(stages = listOf(stage, next), payments = listOf(paid)))
            app.activityRule.scenario.recreate()
            app.onNodeWithText("Etapas").performClick()
            app.onNodeWithText("Gantt").performClick()
            app.onNodeWithText("Cobertura de teste").assertIsDisplayed()
            app.onNodeWithText(260_000L.brl()).assertIsDisplayed()
            app.onNodeWithText("Datas a definir · 0%").assertIsDisplayed()
            val planned = "${stage.plannedStart.dateText()} a ${stage.plannedEnd.dateText()}"
            val actual = "${stage.actualStart.dateText()} a ${stage.actualEnd.dateText()}"
            val spend = "Pago ${paid.amount.brl()} em ${paid.date.dateText()} · A pagar ${200_000L.brl()} em ${stage.dueDate.dateText()}"
            val bars = app.onNodeWithContentDescription("Previsto $planned · Realizado $actual · $spend", useUnmergedTree = true)
            val bitmap = bars.captureToImage().toPixelMap()
            val colors = mutableSetOf(Blue.toArgb(), Green.toArgb(), Navy.toArgb(), Amber.toArgb())
            for (y in 0 until bitmap.height step 2) {
                for (x in 0 until bitmap.width step 2) colors.remove(bitmap[x, y].toArgb())
            }
            assertTrue("As barras e os dois pontos de pagamento devem ser visíveis", colors.isEmpty())

            bars.performTouchInput { swipeLeft() }
            app.onNodeWithText("Cobertura de teste").assertIsDisplayed()
            app.onNodeWithText("Ir para hoje").performClick()
            app.onNodeWithText("Cobertura de teste").performClick()
            app.onNodeWithText("Nome da etapa").assertIsDisplayed()
            app.onNodeWithText("‹ Voltar").performClick()
            app.onNodeWithText("Cobertura de teste").assertIsDisplayed()
            app.onNodeWithText("Ir para hoje").assertIsDisplayed()
        } finally {
            store.save(previous)
        }
    }

    @Test
    fun spendTabShowsTheWeeklyBalanceAndEstimatedPaymentDate() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val monday = LocalDate.now().with(previousOrSame(DayOfWeek.MONDAY))
        val stage = Stage(name = "Forro de teste", budget = 120_000, plannedEnd = monday.plusDays(2))
        val payment = Payment(targetId = stage.id, amount = 20_000, date = monday)
        try {
            store.save(Project(stages = listOf(stage), payments = listOf(payment)))
            app.activityRule.scenario.recreate()
            app.onNodeWithText("Etapas").performClick()
            app.onNodeWithText("Desembolsos").performClick()
            app.onNodeWithText("Calendário de desembolsos").assertIsDisplayed()
            app.onNodeWithTag("flow-paid-total", useUnmergedTree = true).assertTextEquals(20_000L.brl())
            app.onNodeWithTag("flow-pending-total", useUnmergedTree = true).assertTextEquals(100_000L.brl())
            app.onNodeWithTag("flow-toggle-stage-SERVICE-${stage.id}").performClick()
            app.onNodeWithTag("flow-table-body").performScrollToNode(hasTestTag("flow-row-cost-LABOR/${stage.id}"))
            app.onNodeWithText("Mão de obra da etapa").performClick()
            app.onNodeWithText("Editar cadastro do custo").performClick()
            app.onNodeWithText("Nome da etapa").assertIsDisplayed()
        } finally {
            store.save(previous)
        }
    }
}
