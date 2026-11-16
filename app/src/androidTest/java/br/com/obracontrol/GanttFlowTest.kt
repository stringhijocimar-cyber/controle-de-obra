package br.com.obracontrol

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class GanttFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun ganttDrawsThePeriodsAndOpensTheSameStageAfterScrolling() {
        val store = ProjectStore(app.activity)
        val previous = store.load()
        val today = LocalDate.now()
        val stage = Stage(name = "Cobertura de teste", plannedStart = today.minusDays(10), plannedEnd = today.plusDays(100),
            actualStart = today.minusDays(7), actualEnd = today.minusDays(2), progress = 100)
        val next = Stage(name = "Sem datas de teste")
        try {
            store.save(previous.copy(stages = listOf(stage, next) + previous.stages))
            app.activityRule.scenario.recreate()
            app.onNodeWithText("Etapas").performClick()
            app.onNodeWithText("Gantt").performClick()
            app.onNodeWithText("Cobertura de teste").assertIsDisplayed()
            app.onNodeWithText("Datas a definir · 0%").assertIsDisplayed()
            val planned = "${stage.plannedStart.dateText()} a ${stage.plannedEnd.dateText()}"
            val actual = "${stage.actualStart.dateText()} a ${stage.actualEnd.dateText()}"
            val bars = app.onNodeWithContentDescription("Previsto $planned · Realizado $actual", useUnmergedTree = true)
            val bitmap = bars.captureToImage().toPixelMap()
            val colors = mutableSetOf(Blue.toArgb(), Green.toArgb())
            for (y in 0 until bitmap.height step 2) {
                for (x in 0 until bitmap.width step 2) colors.remove(bitmap[x, y].toArgb())
            }
            assertTrue("As duas barras devem ser visíveis", colors.isEmpty())

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
}
