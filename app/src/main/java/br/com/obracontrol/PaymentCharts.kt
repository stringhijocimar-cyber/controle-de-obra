package br.com.obracontrol

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val paymentPalette = listOf(
    Color(0xFF087B62), Color(0xFF326CA3), Color(0xFF995B12), Color(0xFF7951B0),
    Color(0xFF007E89), Color(0xFFB94F50), Color(0xFF636C33), Color(0xFF9B4D88),
)

internal fun paymentChartColor(index: Int): Color = paymentPalette.getOrNull(index)
    ?: Color.hsv(((index * 137.508f) % 360f), 0.65f, 0.62f)

@Composable
internal fun PaymentPieCard(title: String, shares: List<PaymentShare>, key: String) {
    val total = shares.sumOf { it.amount }
    SheetCard(Modifier.fillMaxWidth().testTag("payment-chart-$key")) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Heading(title)
            Detail("Participação nos pagamentos registrados")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Total pago", Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text(total.brl(), Modifier.testTag("payment-chart-total-$key"), color = Green,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PaymentPie(title, shares, key, Modifier.size(168.dp))
            if (total == 0L) Text("Sem pagamentos", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        shares.forEachIndexed { index, entry ->
            Row(Modifier.fillMaxWidth().testTag("payment-share-$key-$index"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(10.dp).background(paymentChartColor(index), CircleShape))
                Text(entry.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Column(horizontalAlignment = Alignment.End) {
                    Text(entry.amount.brl(), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold)
                    Text(percent(entry.amount.toDouble() / total), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PaymentPie(title: String, shares: List<PaymentShare>, key: String, modifier: Modifier) {
    val total = shares.sumOf { it.amount }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.White.toArgb()
        }
    }
    val description = if (total == 0L) "$title. Nenhum pagamento registrado."
        else "$title. Total pago: ${total.brl()}. " + shares.joinToString(". ") {
            "${it.label}: ${it.amount.brl()}, ${percent(it.amount.toDouble() / total)}"
        }
    Canvas(modifier.testTag("payment-pie-$key").semantics { contentDescription = description }) {
        val radius = size.minDimension / 2 - 3.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        if (total == 0L) {
            drawCircle(Line.copy(alpha = 0.65f), radius, center)
            return@Canvas
        }
        val topLeft = Offset(center.x - radius, center.y - radius)
        val diameter = Size(radius * 2, radius * 2)
        var start = -90f
        shares.forEachIndexed { index, entry ->
            val ratio = entry.amount.toDouble() / total
            val sweep = if (index == shares.lastIndex) 270f - start else (ratio * 360).toFloat()
            drawArc(paymentChartColor(index), start, sweep, useCenter = true, topLeft = topLeft, size = diameter)
            if (ratio >= 0.12) {
                val angle = Math.toRadians((start + sweep / 2).toDouble())
                labelPaint.textSize = 12.sp.toPx()
                val labelRadius = if (shares.size == 1) 0f else radius * 0.60f
                val x = center.x + cos(angle).toFloat() * labelRadius
                val y = center.y + sin(angle).toFloat() * labelRadius - (labelPaint.ascent() + labelPaint.descent()) / 2
                drawContext.canvas.nativeCanvas.drawText(percent(ratio), x, y, labelPaint)
            }
            start += sweep
        }
        if (shares.size > 1) {
            var angle = -90.0
            shares.forEach { entry ->
                val radians = Math.toRadians(angle)
                drawLine(Color.White, center,
                    Offset(center.x + cos(radians).toFloat() * radius, center.y + sin(radians).toFloat() * radius), 1.5.dp.toPx())
                angle += entry.amount.toDouble() / total * 360
            }
        }
    }
}
