package br.com.obracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class AppSymbol {
    HOME, STAGES, PURCHASE, PAYMENT, REPORT, SETTINGS, PLUS, EXPAND, CHECK, CLOCK, ALERT,
    CALENDAR, DIARY, UPLOAD, DOWNLOAD, TREND, LAYERS;
}

private val appSymbols = AppSymbol.entries.associateWith { symbol ->
    ImageVector.Builder(symbol.name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            when (symbol) {
                AppSymbol.HOME -> {
                    moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
                    moveTo(5f, 9f); verticalLineTo(21f); horizontalLineTo(19f); verticalLineTo(9f)
                    moveTo(9f, 21f); verticalLineTo(14f); horizontalLineTo(15f); verticalLineTo(21f)
                }
                AppSymbol.STAGES -> {
                    moveTo(5f, 4f); verticalLineTo(21f); moveTo(3f, 7f); horizontalLineTo(21f)
                    moveTo(9f, 11f); horizontalLineTo(16f); moveTo(12f, 15f); horizontalLineTo(20f)
                    moveTo(8f, 19f); horizontalLineTo(14f)
                }
                AppSymbol.PURCHASE -> {
                    moveTo(4f, 8f); horizontalLineTo(20f); lineTo(21f, 21f); horizontalLineTo(3f); close()
                    moveTo(8f, 8f); verticalLineTo(6f); curveTo(8f, 1f, 16f, 1f, 16f, 6f); verticalLineTo(8f)
                }
                AppSymbol.PAYMENT -> {
                    moveTo(20f, 7f); verticalLineTo(4f); horizontalLineTo(5f)
                    curveTo(2f, 4f, 2f, 8f, 5f, 8f); horizontalLineTo(21f); verticalLineTo(20f)
                    horizontalLineTo(5f); curveTo(3f, 20f, 3f, 19f, 3f, 17f); verticalLineTo(6f)
                    moveTo(21f, 12f); horizontalLineTo(15f); verticalLineTo(16f); horizontalLineTo(21f)
                    moveTo(17f, 14f); horizontalLineTo(17.1f)
                }
                AppSymbol.REPORT -> {
                    moveTo(14f, 3f); horizontalLineTo(5f); verticalLineTo(21f); horizontalLineTo(19f)
                    verticalLineTo(8f); close(); moveTo(14f, 3f); verticalLineTo(8f); horizontalLineTo(19f)
                    moveTo(8f, 16f); verticalLineTo(17f); moveTo(12f, 13f); verticalLineTo(17f)
                    moveTo(16f, 11f); verticalLineTo(17f)
                }
                AppSymbol.SETTINGS -> {
                    moveTo(3f, 6f); horizontalLineTo(9f); moveTo(13f, 6f); horizontalLineTo(21f)
                    moveTo(3f, 12f); horizontalLineTo(15f); moveTo(19f, 12f); horizontalLineTo(21f)
                    moveTo(3f, 18f); horizontalLineTo(5f); moveTo(9f, 18f); horizontalLineTo(21f)
                    moveTo(9f, 4f); horizontalLineTo(13f); verticalLineTo(8f); horizontalLineTo(9f); close()
                    moveTo(15f, 10f); horizontalLineTo(19f); verticalLineTo(14f); horizontalLineTo(15f); close()
                    moveTo(5f, 16f); horizontalLineTo(9f); verticalLineTo(20f); horizontalLineTo(5f); close()
                }
                AppSymbol.PLUS -> { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) }
                AppSymbol.EXPAND -> {
                    moveTo(9f, 3f); horizontalLineTo(3f); verticalLineTo(9f)
                    moveTo(15f, 3f); horizontalLineTo(21f); verticalLineTo(9f)
                    moveTo(3f, 15f); verticalLineTo(21f); horizontalLineTo(9f)
                    moveTo(21f, 15f); verticalLineTo(21f); horizontalLineTo(15f)
                }
                AppSymbol.CHECK -> { moveTo(5f, 12f); lineTo(10f, 17f); lineTo(19f, 7f) }
                AppSymbol.CLOCK -> {
                    moveTo(21f, 12f); curveTo(21f, 24f, 3f, 24f, 3f, 12f)
                    curveTo(3f, 0f, 21f, 0f, 21f, 12f); close()
                    moveTo(12f, 7f); verticalLineTo(12f); lineTo(16f, 14f)
                }
                AppSymbol.ALERT -> {
                    moveTo(12f, 3f); lineTo(22f, 21f); horizontalLineTo(2f); close()
                    moveTo(12f, 9f); verticalLineTo(14f); moveTo(12f, 17f); verticalLineTo(17.1f)
                }
                AppSymbol.CALENDAR -> {
                    moveTo(4f, 5f); horizontalLineTo(20f); verticalLineTo(21f); horizontalLineTo(4f); close()
                    moveTo(8f, 3f); verticalLineTo(7f); moveTo(16f, 3f); verticalLineTo(7f)
                    moveTo(4f, 10f); horizontalLineTo(20f); moveTo(8f, 14f); horizontalLineTo(10f)
                    moveTo(14f, 14f); horizontalLineTo(16f); moveTo(8f, 17f); horizontalLineTo(10f)
                }
                AppSymbol.DIARY -> {
                    moveTo(5f, 3f); horizontalLineTo(20f); verticalLineTo(21f); horizontalLineTo(5f); close()
                    moveTo(8f, 3f); verticalLineTo(21f); moveTo(12f, 8f); horizontalLineTo(17f)
                    moveTo(12f, 12f); horizontalLineTo(17f); moveTo(12f, 16f); horizontalLineTo(15f)
                }
                AppSymbol.UPLOAD, AppSymbol.DOWNLOAD -> {
                    moveTo(4f, 16f); verticalLineTo(21f); horizontalLineTo(20f); verticalLineTo(16f)
                    moveTo(12f, 3f); verticalLineTo(15f)
                    if (symbol == AppSymbol.UPLOAD) { moveTo(7f, 8f); lineTo(12f, 3f); lineTo(17f, 8f) }
                    else { moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f) }
                }
                AppSymbol.TREND -> {
                    moveTo(3f, 17f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(21f, 6f)
                    moveTo(15f, 6f); horizontalLineTo(21f); verticalLineTo(12f)
                }
                AppSymbol.LAYERS -> {
                    moveTo(12f, 3f); lineTo(22f, 8f); lineTo(12f, 13f); lineTo(2f, 8f); close()
                    moveTo(2f, 12f); lineTo(12f, 17f); lineTo(22f, 12f)
                    moveTo(2f, 16f); lineTo(12f, 21f); lineTo(22f, 16f)
                }
            }
        }
    }.build()
}

@Composable
internal fun AppIcon(symbol: AppSymbol, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(appSymbols.getValue(symbol), contentDescription = null, modifier = modifier.size(22.dp), tint = tint)
}

@Composable
internal fun IconBadge(symbol: AppSymbol, color: Color = Green) {
    Box(Modifier.size(38.dp).background(color.copy(alpha = 0.09f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) { AppIcon(symbol, tint = color) }
}

@Composable
internal fun ViewTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Line.copy(alpha = 0.45f), RoundedCornerShape(13.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Surface(Modifier.weight(1f), color = if (active) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp), shadowElevation = if (active) 1.dp else 0.dp) {
                Box(Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) }).padding(horizontal = 3.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Text(label, color = if (active) Green else Muted, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
