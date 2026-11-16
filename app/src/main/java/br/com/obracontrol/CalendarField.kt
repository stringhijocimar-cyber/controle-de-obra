package br.com.obracontrol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal fun LocalDate.calendarMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.calendarDate(): LocalDate = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarField(label: String, value: String, change: (String) -> Unit, optional: Boolean = true) {
    var open by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(value.ifBlank { "Selecionar data" }, style = MaterialTheme.typography.bodyLarge)
        }
        CalendarIcon()
    }
    if (open) {
        val current = parseDate(value)
        val first = minOf(1900, current?.year ?: today.year, today.year)
        val last = maxOf(2100, current?.year ?: today.year, today.year)
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = current?.calendarMillis(),
            initialDisplayedMonthMillis = (current ?: today).calendarMillis(),
            yearRange = first..last,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        picker.selectedDateMillis?.let { change(it.calendarDate().dateText()) }
                        open = false
                    },
                ) { Text("Aplicar") }
            },
            dismissButton = {
                if (optional && value.isNotBlank()) {
                    TextButton(onClick = { change(""); open = false }) { Text("Limpar data") }
                }
                TextButton(onClick = { open = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(
                state = picker,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                title = {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = {
                            picker.selectedDateMillis = today.calendarMillis()
                            picker.displayedMonthMillis = today.withDayOfMonth(1).calendarMillis()
                        }) { Text("Hoje") }
                    }
                },
                headline = {
                    Text(picker.selectedDateMillis?.calendarDate().dateOr("Selecionar data"),
                        Modifier.padding(start = 24.dp), style = MaterialTheme.typography.headlineSmall)
                },
            )
        }
    }
}

@Composable
private fun CalendarIcon() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.6.dp.toPx()
        val corner = 2.2.dp.toPx()
        drawRoundRect(color, Offset(size.width * 0.12f, size.height * 0.22f), Size(size.width * 0.76f, size.height * 0.66f),
            CornerRadius(corner, corner), style = Stroke(stroke))
        drawLine(color, Offset(size.width * 0.12f, size.height * 0.45f), Offset(size.width * 0.88f, size.height * 0.45f), stroke)
        for (position in listOf(0.34f, 0.66f)) {
            drawLine(color, Offset(size.width * position, size.height * 0.1f), Offset(size.width * position, size.height * 0.31f), stroke)
        }
    }
}
