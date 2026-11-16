package br.com.obracontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkAgendaTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test
    fun datesUseTheCalendarDayAcrossTimeZones() {
        listOf(LocalDate.of(1999, 12, 31), LocalDate.of(2028, 2, 29), today).forEach {
            assertEquals(it, it.calendarMillis().calendarDate())
        }
        assertEquals(0L, LocalDate.of(1970, 1, 1).calendarMillis())
    }

    @Test
    fun agendaTracksOutstandingPaymentsAndPendingDeliveriesIndependently() {
        val stage = Stage(name = "Cobertura", plannedEnd = today.minusDays(1), dueDate = today, budget = 40000)
        val done = Stage(name = "Muro", plannedEnd = today.minusDays(1), progress = 100, dueDate = today, budget = 18000)
        val planned = Purchase(name = "Telhas", total = 50000, dueDate = today.plusDays(7), deliveryExpected = today.plusDays(8))
        val received = Purchase(name = "Ferramentas", total = 10000, dueDate = today.minusDays(1), deliveryExpected = today, receivedDate = today)
        val paid = Purchase(name = "Areia", total = 9000, dueDate = today, deliveryExpected = today.minusDays(1))
        val estimate = Purchase(name = "Pergolado sem cotação")
        val project = Project(stages = listOf(stage, done), purchases = listOf(planned, received, paid, estimate), payments = listOf(
            Payment(kind = CostKind.LABOR, targetId = stage.id, amount = 12000),
            Payment(kind = CostKind.MATERIAL, targetId = paid.id, amount = 9000),
        ))
        val agenda = WorkAgenda(project, Balance(project), today)

        assertEquals(3, agenda.inWindow(AgendaWindow.OVERDUE).size)
        assertTrue(agenda.inWindow(AgendaWindow.OVERDUE).any { it.targetId == paid.id && it.kind == AgendaKind.DELIVERY })
        assertEquals(28000L, agenda.paymentsIn(AgendaWindow.NEXT_SEVEN_DAYS).single { it.targetId == stage.id }.amount)
        assertEquals(50000L, agenda.paymentsIn(AgendaWindow.NEXT_SEVEN_DAYS).single { it.targetId == planned.id }.amount)
        assertTrue(agenda.inWindow(AgendaWindow.LATER).any { it.targetId == planned.id && it.kind == AgendaKind.DELIVERY })
        assertFalse(agenda.entries.any { it.targetId == received.id && it.kind == AgendaKind.DELIVERY })
        assertFalse(agenda.entries.any { it.targetId == done.id && it.kind == AgendaKind.STAGE })
        assertTrue(agenda.entries.any { it.targetId == done.id && it.isPayment })
        assertNull(agenda.paymentsIn(AgendaWindow.UNSCHEDULED).single { it.targetId == estimate.id }.amount)
    }

    @Test
    fun versionFourRoundTripsDeliveryAndDiaryFilesAndReadsVersionThree() {
        val stage = Stage(name = "Cobertura")
        val attachment = DocumentAttachment("e".repeat(64), "foto.png", DocumentType.PNG, 40)
        val diary = DiaryEntry(date = today, title = "Montagem", stageId = stage.id, activities = "Chapas fixadas",
            occurrences = "Chuva à tarde", attachments = listOf(attachment))
        val purchase = Purchase(name = "Telhas", deliveryExpected = today, receivedDate = today.plusDays(2))
        val project = Project(stages = listOf(stage), purchases = listOf(purchase), diaryEntries = listOf(diary))
        assertEquals(project, ProjectCodec.decode(ProjectCodec.encode(project)))
        assertEquals(listOf(attachment), project.documentAttachments())
        val legacy = """{"version":3,"project":"Obra anterior","stages":[],"purchases":[],"payments":[],"issues":[]}"""
        assertEquals(Project(name = "Obra anterior"), ProjectCodec.decode(legacy))
    }

    @Test
    fun mergeAddsDiaryEntriesAndKeepsLocalEdits() {
        val sourceStage = Stage(name = "Cobertura")
        val localStage = sourceStage.copy(id = "local")
        val old = DiaryEntry(date = today, title = "Registro corrigido", stageId = localStage.id)
        val new = DiaryEntry(date = today.plusDays(1), title = "Vistoria", stageId = sourceStage.id)
        val current = Project(name = "Obra teste", stages = listOf(localStage), diaryEntries = listOf(old))
        val incoming = Project(name = "Obra teste", stages = listOf(sourceStage),
            diaryEntries = listOf(old.copy(title = "Registro antigo", stageId = sourceStage.id), new))

        val imported = current.mergeFrom(incoming)
        assertEquals(1, imported.added)
        assertEquals(listOf(old, new.copy(stageId = localStage.id)), imported.project.diaryEntries)
        assertEquals(0, imported.project.mergeFrom(incoming).added)
    }
}
