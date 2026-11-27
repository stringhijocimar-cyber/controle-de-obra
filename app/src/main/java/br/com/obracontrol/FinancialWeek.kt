package br.com.obracontrol

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters.nextOrSame
import java.time.temporal.WeekFields

/** Each cash-flow column covers Saturday through the following Friday. */
internal fun LocalDate.cashWeekEnding(): LocalDate = with(nextOrSame(DayOfWeek.FRIDAY))

internal fun LocalDate.cashWeekLabel(): String {
    val number = get(WeekFields.ISO.weekOfWeekBasedYear()).toString().padStart(2, '0')
    val year = get(WeekFields.ISO.weekBasedYear())
    return "S$number" + (if (year != this.year) "/${year % 100}" else "") +
        "\n" + format(DateTimeFormatter.ofPattern("dd/MM", brazil))
}
