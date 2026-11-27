package br.com.obracontrol

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.toArgb

internal object FullReport {
    private const val WIDTH = 842
    private const val HEIGHT = 595
    private const val LEFT = 34f
    private const val RIGHT = WIDTH - LEFT
    private const val CONTENT = RIGHT - LEFT
    private const val BOTTOM = 550f
    private val ink = Navy.toArgb()
    private val muted = Muted.toArgb()
    private val light = Color.rgb(241, 245, 248)
    private val track = Line.toArgb()
    private val green = Green.toArgb()
    private val greenTint = Color.rgb(237, 246, 242)
    private val blue = Blue.toArgb()
    private val gold = Amber.toArgb()
    private val goldTint = Color.rgb(255, 242, 221)
    private val alternate = Color.rgb(250, 252, 253)
    private val red = Color.rgb(179, 38, 30)
    private val shortDate = DateTimeFormatter.ofPattern("dd/MM", brazil)
    private val shortDateWithYear = DateTimeFormatter.ofPattern("dd/MM/yy", brazil)

    fun write(output: OutputStream, project: Project, today: LocalDate = LocalDate.now()) {
        val document = PdfDocument()
        try {
            val report = Report(document, project, today)
            try {
                report.dashboard()
                report.management()
                report.paymentCharts()
                report.schedule()
                report.labor()
                report.purchases()
                report.purchaseInstallments()
                report.projections()
                report.costPaymentCalendar()
                report.weeklyMatrix()
                report.planning()
                report.materials()
                report.payments()
                report.issues()
                report.agenda()
                report.diary()
            } finally {
                report.finish()
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private data class Tile(val label: String, val value: String, val detail: String, val color: Int = ink)
    private data class Bar(val label: String, val amount: Long, val color: Int)

    private class Report(
        private val pdf: PdfDocument,
        private val project: Project,
        private val today: LocalDate,
    ) {
        private val total = Balance(project)
        private val laborSpend = LaborSpendCalendar(project, total)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
        private val bold = Typeface.create("sans-serif", Typeface.BOLD)
        private var page: PdfDocument.Page? = null
        private val canvas: Canvas get() = checkNotNull(page).canvas
        private var number = 0
        private var title = ""
        private var subtitle = ""
        private var y = 0f
        private var tableHeading: String? = null

        fun finish() {
            page?.let(pdf::finishPage)
            page = null
        }

        private fun section(heading: String, caption: String) {
            title = heading
            subtitle = caption
            tableHeading = null
            startPage()
        }

        private fun startPage(continuation: Boolean = false) {
            finish()
            number++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
            canvas.drawColor(Color.WHITE)
            box(0f, 0f, WIDTH.toFloat(), 65f, ink, 0f)
            text(LEFT, 32f, title + if (continuation) " (continuação)" else "", 19f, Color.WHITE, true, CONTENT)
            text(LEFT, 51f, subtitle, 10f, Color.rgb(216, 232, 243), width = CONTENT)
            box(LEFT, 565f, CONTENT, 1f, track, 0f)
            text(LEFT, 581f, "${project.name}  |  ObraControl  |  ${today.dateText()}", 8.5f, muted, width = CONTENT - 92)
            text(RIGHT - 75, 581f, "Página $number", 8.5f, muted, width = 75f, end = true)
            y = 87f
        }

        private fun ensure(height: Float) {
            if (y + height > BOTTOM) startPage(continuation = true)
        }

        private fun box(x: Float, top: Float, width: Float, height: Float, color: Int, radius: Float = 5f) {
            paint.color = color
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(x, top, x + width, top + height, radius, radius, paint)
        }

        private fun line(x: Float, top: Float, right: Float, bottom: Float, color: Int, dashed: Boolean = false) {
            paint.color = color
            paint.strokeWidth = 0.8f
            paint.pathEffect = if (dashed) DashPathEffect(floatArrayOf(3f, 2.5f), 0f) else null
            canvas.drawLine(x, top, right, bottom, paint)
            paint.pathEffect = null
        }

        private fun text(x: Float, baseline: Float, value: String, size: Float = 10f, color: Int = ink,
            strong: Boolean = false, width: Float? = null, end: Boolean = false) {
            paint.color = color
            paint.typeface = if (strong) bold else normal
            paint.textSize = size
            paint.textAlign = Paint.Align.LEFT
            val fitted = if (width != null && paint.measureText(value) > width) {
                val cut = paint.breakText(value, true, width - paint.measureText("…"), null)
                value.take(cut).trimEnd() + "…"
            } else value
            val left = if (end && width != null) x + width - paint.measureText(fitted) else x
            canvas.drawText(fitted, left, baseline, paint)
        }

        private fun wrap(value: String, width: Float, size: Float, strong: Boolean = false): List<String> {
            paint.typeface = if (strong) bold else normal
            paint.textSize = size
            return buildList {
                value.split('\n').forEach { paragraph ->
                    var remainder = paragraph.trim()
                    if (remainder.isEmpty()) add("")
                    while (remainder.isNotEmpty()) {
                        val count = paint.breakText(remainder, true, width, null).coerceAtLeast(1)
                        val cut = if (count == remainder.length) count
                            else remainder.lastIndexOf(' ', count).takeIf { it > 0 } ?: count
                        add(remainder.take(cut).trimEnd())
                        remainder = remainder.drop(cut).trimStart()
                    }
                }
            }
        }

        private fun heading(value: String) {
            ensure(38f)
            text(LEFT, y + 12f, value, 13f, ink, true, CONTENT)
            y += 25f
            tableHeading = value
        }

        private fun paragraph(value: String, color: Int = muted) {
            wrap(value, CONTENT, 10f).forEach { line ->
                ensure(15f)
                text(LEFT, y + 10f, line, 10f, color)
                y += 14f
            }
            y += 5f
        }

        private fun callout(label: String, value: String, warning: Boolean = false) {
            val lines = wrap(value, CONTENT - 26f, 10f)
            if (lines.size > 22) {
                heading(label)
                paragraph(value, if (warning) gold else muted)
                return
            }
            val height = 36f + 14f * lines.size
            ensure(height)
            box(LEFT, y, CONTENT, height, if (warning) goldTint else greenTint)
            text(LEFT + 13, y + 18, label, 11f, if (warning) gold else green, true, CONTENT - 26)
            lines.forEachIndexed { index, line -> text(LEFT + 13, y + 36 + 14 * index, line) }
            y += height + 14f
        }

        private fun tiles(vararg cells: Tile) {
            val width = (CONTENT - 10f * (cells.size - 1)) / cells.size
            ensure(83f)
            cells.forEachIndexed { index, cell ->
                val x = LEFT + index * (width + 10f)
                box(x, y, width, 78f, light, 7f)
                text(x + 11, y + 19f, cell.label, 9.2f, muted, true, width - 22)
                text(x + 11, y + 44f, cell.value, 17f, cell.color, true, width - 22)
                text(x + 11, y + 63f, cell.detail, 8.6f, muted, width = width - 22)
            }
            y += 99f
        }

        private fun bars(entries: List<Bar>, x: Float, top: Float, width: Float, labelWidth: Float = 160f) {
            val ceiling = entries.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L
            val barWidth = width - labelWidth - 93f
            entries.forEachIndexed { index, entry ->
                val row = top + index * 34f
                text(x, row + 14f, entry.label, 9.8f, width = labelWidth - 7f)
                box(x + labelWidth, row + 4f, barWidth, 14f, track, 4f)
                if (entry.amount > 0) box(x + labelWidth, row + 4f,
                    (barWidth * (entry.amount.toDouble() / ceiling)).toFloat(), 14f, entry.color, 4f)
                text(x + labelWidth + barWidth + 6f, row + 14f, entry.amount.brl(), 9f, ink, true, 87f, true)
            }
        }

        private fun barSection(heading: String, entries: List<Bar>, emptyText: String) {
            heading(heading)
            if (entries.isEmpty()) {
                paragraph(emptyText)
                return
            }
            val visible = entries.take(7)
            ensure(visible.size * 34f)
            bars(visible, LEFT, y, CONTENT, 250f)
            y += visible.size * 34f + 10f
            if (visible.size < entries.size) paragraph("A tabela desta seção traz todos os registros.")
        }

        private fun sumText(values: Iterable<Long?>): String {
            val amount = tableAmountTotal(values)
            return if (amount.entirelyUndefined) UNDEFINED else amount.known.brl() + if (amount.partial) " + ?" else ""
        }

        private fun columnColor(label: String): Int = when {
            "A PAGAR" in label || "SALDO" in label -> gold
            "PAGO A MAIOR" in label || "CRÉDITO" in label -> blue
            "PAGO" in label || "ABATIDO" in label -> green
            else -> ink
        }

        private fun statusColor(value: String): Int? = when (value.lowercase(brazil)) {
            "pago", "paga", "sim", "concluída", "concluído", "resolvida", "recebido" -> green
            "parcial", "em aberto", "a pagar", "não", "conferir", "em andamento", "a iniciar" -> gold
            "atrasado", "atrasada", "vencido", "vencida" -> red
            "parcial em atraso" -> red
            "pago a maior" -> blue
            "a definir", "total a confirmar", "sem custo" -> muted
            else -> null
        }

        private fun table(columns: List<String>, widths: List<Float>, rows: List<List<String>>, empty: String,
            totals: Map<Int, String> = emptyMap(), emphasizedRows: Set<Int> = emptySet()) {
            if (rows.isEmpty()) {
                paragraph(empty)
                return
            }
            require(columns.size == widths.size && rows.all { it.size == columns.size })
            val caption = tableHeading
            tableHeading = null
            val freshTop = 87f + if (caption != null) 25f else 0f
            val cellPadding = 8f
            val fontSize = 9.4f
            val lineHeight = 12.5f
            val numeric = columns.map { label ->
                listOf("VALOR", "TOTAL", "PAGO", "PREÇO", "SUBTOTAL", "SALDO", "ESTIMATIVA", "APROVADO", "PREVISÃO", "EXECUÇÃO", "MATERIAIS", "GERAL",
                    "QUANTIDADE", "CRÉDITO", "ABATIDO", "PROGRAMADO", "A PAGAR", "A DEFINIR", "PARCELAS", "AVANÇO")
                    .any { it in label } || Regex("S[0-9]+.*").matches(label)
            }
            fun cellLines(row: List<String>, strong: Boolean) = row.mapIndexed { index, value ->
                val status = ("SITUAÇÃO" in columns[index] || "CONCLUÍDO" in columns[index]) && statusColor(value) != null
                wrap(value, widths[index] - cellPadding * 2 - if (status) 18f else 0f, fontSize, strong)
            }
            val headerLines = columns.zip(widths).map { (label, width) -> wrap(label, width - cellPadding * 2, 8.3f, true) }
            val headerHeight = headerLines.maxOf { it.size } * 11f + 16f
            val footerValues = columns.indices.map { index ->
                totals[index] ?: if (index == 0) if (totals.isEmpty()) "${rows.size} registros" else "TOTAL" else ""
            }
            val footerLines = footerValues.zip(widths).map { (value, width) -> wrap(value, width - cellPadding * 2, fontSize, true) }
            val footerHeight = footerLines.maxOf { it.size } * lineHeight + 20f
            var tableTop = y
            fun separators(top: Float, bottom: Float) {
                var x = LEFT
                widths.dropLast(1).forEach { width -> x += width; line(x, top, x, bottom, track) }
            }
            fun frame() {
                paint.color = track
                paint.strokeWidth = 0.8f
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(LEFT, tableTop, RIGHT, y, 6f, 6f, paint)
                paint.style = Paint.Style.FILL
            }
            fun header() {
                tableTop = y
                box(LEFT, y, CONTENT, headerHeight, light, 6f)
                var x = LEFT
                headerLines.forEachIndexed { index, lines ->
                    lines.forEachIndexed { row, label ->
                        text(x + cellPadding, y + 15f + row * 11f, label, 8.3f, muted, true,
                            widths[index] - cellPadding * 2, numeric[index])
                    }
                    x += widths[index]
                }
                separators(y, y + headerHeight)
                y += headerHeight
                line(LEFT, y, RIGHT, y, track)
            }
            fun nextPage() {
                frame()
                startPage(continuation = true)
                if (caption != null) {
                    text(LEFT, y + 12f, caption, 13f, ink, true, CONTENT)
                    y += 25f
                }
                header()
            }
            val firstHeight = cellLines(rows.first(), 0 in emphasizedRows).maxOf { it.size } * lineHeight + 14f
            val initialPage = number
            ensure(headerHeight + minOf(firstHeight + if (rows.size == 1) footerHeight else 0f, BOTTOM - freshTop - headerHeight))
            if (initialPage != number && caption != null) {
                text(LEFT, y + 12f, caption, 13f, ink, true, CONTENT)
                y += 25f
            }
            header()
            rows.forEachIndexed { rowIndex, row ->
                val lines = cellLines(row, rowIndex in emphasizedRows)
                val depth = lines.maxOf { it.size }
                val reserve = if (rowIndex == rows.lastIndex) footerHeight else 0f
                val wholeHeight = depth * lineHeight + 14f
                if (wholeHeight + reserve <= BOTTOM - freshTop - headerHeight && y + wholeHeight + reserve > BOTTOM) nextPage()
                var offset = 0
                while (offset < depth) {
                    val remainingHeight = (depth - offset) * lineHeight + 14f
                    val keepFooter = reserve > 0 && remainingHeight + reserve <= BOTTOM - freshTop - headerHeight
                    val room = BOTTOM - y - if (keepFooter) reserve else 0f
                    if (room < lineHeight + 14f) { nextPage(); continue }
                    val count = minOf(depth - offset, ((room - 14f) / lineHeight).toInt())
                    val height = count * lineHeight + 14f
                    val emphasized = rowIndex in emphasizedRows
                    box(LEFT, y, CONTENT, height, if (emphasized) light else if (rowIndex % 2 == 0) Color.WHITE else alternate, 0f)
                    var x = LEFT
                    lines.forEachIndexed { index, cell ->
                        val visible = cell.drop(offset).take(count)
                        if (index == 0 && offset > 0 && visible.isEmpty()) {
                            text(x + cellPadding, y + 16f, cell.firstOrNull().orEmpty(), 8.2f, muted, true, widths[index] - cellPadding * 2)
                        }
                        val status = if ("SITUAÇÃO" in columns[index] || "CONCLUÍDO" in columns[index]) statusColor(row[index]) else null
                        if (status != null && visible.isNotEmpty()) {
                            val chipWidth = widths[index] - cellPadding * 2
                            box(x + cellPadding, y + 3f, chipWidth, visible.size * lineHeight + 8f,
                                when (status) { green -> greenTint; red -> Color.rgb(253, 235, 234)
                                    blue -> Color.rgb(234, 241, 249); gold -> goldTint; else -> light }, 6f)
                            paint.color = status
                            canvas.drawCircle(x + cellPadding + 7f, y + 13f, 2.5f, paint)
                            visible.forEachIndexed { indexInCell, value ->
                                text(x + cellPadding + 14f, y + 16f + indexInCell * lineHeight, value,
                                    8.6f, status, width = chipWidth - 18f)
                            }
                        } else visible.forEachIndexed { indexInCell, value ->
                            text(x + cellPadding, y + 16f + indexInCell * lineHeight, value, fontSize,
                                if (numeric[index]) columnColor(columns[index]) else statusColor(value) ?: ink,
                                emphasized, widths[index] - cellPadding * 2, numeric[index])
                        }
                        x += widths[index]
                    }
                    separators(y, y + height)
                    offset += count
                    y += height
                    line(LEFT, y, RIGHT, y, track)
                }
            }
            if (y + footerHeight > BOTTOM) nextPage()
            box(LEFT, y, CONTENT, footerHeight, greenTint, 0f)
            var x = LEFT
            footerLines.forEachIndexed { index, lines ->
                lines.forEachIndexed { row, value ->
                    text(x + cellPadding, y + 18f + row * lineHeight, value, fontSize,
                        if (index == 0) ink else columnColor(columns[index]), true,
                        widths[index] - cellPadding * 2, numeric[index])
                }
                x += widths[index]
            }
            separators(y, y + footerHeight)
            y += footerHeight
            frame()
            y += 13f
        }

        fun dashboard() {
            val ratio = if (total.total == 0L) 0.0 else total.paid.toDouble() / total.total
            section("PAINEL DE CONTROLE  |  ${project.name.uppercase(brazil)}",
                "Prestação de contas e acompanhamento da execução  •  Base: ${project.referenceDate.dateOr(today.dateText())}")
            tiles(
                Tile("PREVISÃO PARCIAL", total.total.brl(), "Compras e mão de obra"),
                Tile("TOTAL JÁ PAGO", total.paid.brl(), "${percent(ratio)} da previsão", green),
                Tile("SALDO LÍQUIDO", total.remaining.brl(), "Previsão parcial menos pagamentos", blue),
                Tile("ACRÉSCIMO DECLARADO", total.extras.brl(), "Compras marcadas como acréscimo", gold),
            )
            text(LEFT, y + 14, "COMPOSIÇÃO DOS CUSTOS", 12f, ink, true)
            bars(listOf(Bar("Compras pagas", total.materialPaid, green), Bar("Compras a pagar", total.materialToPay, blue),
                Bar("Mão de obra prevista", total.laborForecast, gold)), LEFT, y + 27f, 474f)
            box(534f, y, RIGHT - 534f, 148f, light, 6f)
            text(546f, y + 18, "QUEM PAGOU", 11f, ink, true)
            if (total.payers.isEmpty()) text(546f, y + 40, "Nenhum pagamento registrado.", 9f, muted)
            total.payers.take(3).forEachIndexed { index, group ->
                val row = y + 39 + 30 * index
                text(546f, row, "${group.payer}  |  ${group.method}", 9.5f, muted, width = 252f)
                text(546f, row + 13f, "${group.amount.brl()}  ·  ${group.kind.label}", 10f, ink, true, 250f)
            }
            if (total.payers.size > 3) text(546f, y + 138f, "Confira todos os registros na seção de pagamentos.", 8.4f, muted, width = 252f)
            y += 164f
            val latest = project.stages.filter { it.progress == 100 }.maxByOrNull { it.actualEnd ?: LocalDate.MIN }
            val marker = latest?.let { " Última etapa concluída: ${it.name}, em ${it.actualEnd.dateOr()}." }.orEmpty()
            callout("MARCO DA OBRA", "Início: ${project.start.dateOr()} • ${total.completed} de ${project.stages.size} etapas concluídas • Avanço físico médio de ${percent(total.physicalProgress / 100)}.$marker")
            paragraph("O avanço físico é a média simples dos percentuais das etapas. Os valores a cotar aparecem como pendências.")
            paragraph("Avanço geral das etapas: ${stagePercent(project.generalStageProgress)}. Combina execução e materiais recebidos com os pesos de cada etapa; a média da obra atribui o mesmo peso a cada etapa.")
        }

        fun paymentCharts() {
            val breakdown = project.paymentBreakdown()
            section("COMO A OBRA FOI PAGA", "Gráficos de participação por pagador e por forma de pagamento")
            paragraph("Somente pagamentos registrados. Compras previstas e parcelas futuras permanecem no saldo a pagar.")
            paymentPie("QUEM PAGOU", breakdown.byPayer)
            paymentPie("COMO FOI PAGO", breakdown.byMethod)
        }

        private fun paymentPie(label: String, shares: List<PaymentShare>) {
            ensure(213f)
            heading(label)
            val amount = shares.sumOf { it.amount }
            text(LEFT, y + 11f, "Total pago: ${amount.brl()}", 10.5f, green, true)
            y += 22f
            val chartTop = y
            val chartPage = number
            val radius = 76f
            val centerX = LEFT + radius
            val centerY = y + radius
            val bounds = RectF(LEFT, y, LEFT + 152f, y + 152f)
            if (amount == 0L) {
                paint.color = track
                canvas.drawCircle(centerX, centerY, radius, paint)
            } else {
                var angle = -90f
                shares.forEachIndexed { index, entry ->
                    val ratio = entry.amount.toDouble() / amount
                    val sweep = if (index == shares.lastIndex) 270f - angle else (ratio * 360).toFloat()
                    paint.color = paymentChartColor(index).toArgb()
                    paint.style = Paint.Style.FILL
                    canvas.drawArc(bounds, angle, sweep, true, paint)
                    if (ratio >= 0.12) {
                        val radians = Math.toRadians((angle + sweep / 2).toDouble())
                        val value = percent(ratio)
                        paint.typeface = bold
                        paint.textSize = 10f
                        val labelRadius = if (shares.size == 1) 0f else radius * 0.60f
                        text(centerX + cos(radians).toFloat() * labelRadius - paint.measureText(value) / 2,
                            centerY + sin(radians).toFloat() * labelRadius + 3f, value, 10f, Color.WHITE, true)
                    }
                    angle += sweep
                }
                if (shares.size > 1) {
                    var angle = -90.0
                    shares.forEach { entry ->
                        val radians = Math.toRadians(angle)
                        line(centerX, centerY, centerX + cos(radians).toFloat() * radius,
                            centerY + sin(radians).toFloat() * radius, Color.WHITE)
                        angle += entry.amount.toDouble() / amount * 360
                    }
                }
            }
            val legendX = LEFT + 190f
            var legendY = y + 5f
            if (shares.isEmpty()) text(legendX, legendY + 14f, "Nenhum pagamento registrado.", 10f, muted)
            shares.forEachIndexed { index, entry ->
                val lines = wrap(entry.label, CONTENT - 190f - 237f, 10f)
                lines.forEachIndexed { lineIndex, value ->
                    if (legendY + 19f > BOTTOM) {
                        startPage(continuation = true)
                        heading("$label | LEGENDA")
                        legendY = y
                    }
                    if (lineIndex == 0) {
                        paint.color = paymentChartColor(index).toArgb()
                        canvas.drawCircle(legendX + 4f, legendY + 9f, 4f, paint)
                        text(RIGHT - 205f, legendY + 13f, entry.amount.brl(), 10f, ink, true, 130f, end = true)
                        text(RIGHT - 65f, legendY + 13f, percent(entry.amount.toDouble() / amount), 10f, muted, width = 65f, end = true)
                    }
                    text(legendX + 17f, legendY + 13f, value, 10f, ink)
                    legendY += 13f
                }
                legendY += 16f
            }
            y = if (number == chartPage) maxOf(chartTop + 152f, legendY) + 16f else legendY + 16f
        }

        fun purchaseInstallments() {
            val orders = project.purchases.filter { order -> project.scheduledPayments.any { it.kind == CostKind.MATERIAL && it.targetId == order.id } }
            if (orders.isEmpty()) return
            val positions = orders.flatMap { order -> project.costPaymentPositions(CostKind.MATERIAL, order.id).filter { it.schedule != null } }
            section("PAGAMENTOS A PRAZO  |  CONTROLE DE PARCELAS", "Parcelas futuras permanecem a pagar; somente pagamentos registrados compõem o pago efetivo")
            val rows = orders.flatMap { order -> project.costPaymentPositions(CostKind.MATERIAL, order.id).filter { it.schedule != null }.map { position ->
                val actual = project.payments.filter { it.scheduleId == position.schedule?.id && it.targetId == order.id && it.kind == CostKind.MATERIAL }
                listOf("${order.name}\n${position.label}", position.gross.costText(), position.date.dateOr("Sem data"), position.applied.brl(),
                    position.remaining.costText(), if (actual.isNotEmpty()) actual.map { it.date.dateOr("Sem data") }.distinct().joinToString(" · ")
                    else if (position.applied > 0) "Abatimento anterior; ver pagamentos" else "—",
                    if (position.completed) "Paga" else if (position.applied > 0) "Parcial" else "Em aberto")
            } }
            table(listOf("COMPRA / PARCELA", "VALOR", "VENCIMENTO", "PAGO", "A PAGAR", "DATA DO PAGAMENTO", "SITUAÇÃO"),
                listOf(210f, 90f, 90f, 100f, 100f, 100f, 84f), rows, "",
                totals = mapOf(1 to sumText(positions.map { it.gross }),
                    3 to positions.sumOf { it.applied }.brl(), 4 to sumText(positions.map { it.remaining })))
            paragraph("Pagamentos antigos sem vínculo são abatidos por vencimento e permanecem no histórico original. Não são lançados novamente ao criar as parcelas.")
        }

        fun management() {
            val summary = ManagementReportSummary(project, today)
            section("RESUMO GERENCIAL  |  CUSTOS E DECISÕES", "Posição financeira e vencimentos calculados em ${today.dateText()} · Registros completos nas seções seguintes")
            tiles(
                Tile("CUSTO PREVISTO CONHECIDO", summary.forecast.brl(), "${summary.undefined} custo(s) com valor final a definir"),
                Tile("PAGO EFETIVO", summary.paid.brl(), "Somente pagamentos registrados", green),
                Tile("SALDO LÍQUIDO CONHECIDO", (summary.forecast - summary.paid).brl(), "Previsão menos pagamentos", blue),
            )
            table(listOf("TIPO DE CUSTO", "PREVISÃO CONHECIDA", "PAGO EFETIVO", "SALDO LÍQUIDO", "VALORES A DEFINIR"),
                listOf(186f, 161f, 146f, 146f, 135f), summary.groups.map {
                    listOf(it.category.label, it.forecast.brl(), it.paid.brl(), it.balance.brl(), it.undefined.toString())
                }, "", totals = mapOf(1 to summary.forecast.brl(), 2 to summary.paid.brl(),
                    3 to (summary.forecast - summary.paid).brl(), 4 to summary.undefined.toString()))
            heading("PROGRAMAÇÃO FINANCEIRA")
            table(listOf("PERÍODO", "SALDO A PAGAR CONHECIDO", "VENCIMENTOS COM VALOR A DEFINIR"), listOf(282f, 230f, 262f),
                DueHorizon.entries.map { horizon ->
                    val amount = summary.pending(horizon)
                    listOf(horizon.label, if (amount.entirelyUndefined) UNDEFINED else amount.known.brl(), amount.undefinedCount.toString())
                }, "", totals = mapOf(1 to sumText(CostPaymentCalendar(project).pending.map { it.amount }),
                    2 to CostPaymentCalendar(project).pending.count { it.amount == null }.toString()))
            paragraph("O saldo líquido pode incluir pagamentos a maior. A programação considera o saldo pendente de cada custo, com os pagamentos já abatidos. A mão de obra da programação segue as etapas; divergências com o contrato geral aparecem em Pendências.")
            ensure(145f)
            heading("CARTÃO E PARCELAMENTOS")
            table(listOf("COMPRA / SERVIÇO", "MÉTODO / CONDIÇÃO", "TOTAL", "PAGO EFETIVO", "A PAGAR"),
                listOf(219f, 225f, 110f, 110f, 110f), summary.cardPurchases.map { order ->
                    val paid = total.paid(CostKind.MATERIAL, order.id)
                    listOf(order.name, "${project.purchaseMethods(order)}\n${order.installmentText()}", order.forecast.costText(),
                        paid.brl(), InstallmentBalance(order, paid).remaining.costText())
                }, "Nenhuma compra identificada como cartão ou parcelada.", totals = mapOf(
                    2 to sumText(summary.cardPurchases.map { it.forecast }),
                    3 to summary.cardPurchases.sumOf { total.paid(CostKind.MATERIAL, it.id) }.brl(),
                    4 to sumText(summary.cardPurchases.map { InstallmentBalance(it, total.paid(CostKind.MATERIAL, it.id)).remaining })))
            paragraph("A condição do cartão descreve a divisão comercial do total. As parcelas futuras só reduzem o saldo quando o pagamento é registrado. Os vencimentos detalhados estão no calendário de pagamentos.")
            ensure(85f)
            heading("PRÓXIMAS DECISÕES")
            table(listOf("ASSUNTO", "AÇÃO"), listOf(230f, 544f), summary.decisions.take(8).map { listOf(it.first, it.second) },
                "Nenhuma decisão em aberto identificada no cadastro.")
            if (summary.decisions.size > 8) paragraph("${summary.decisions.size - 8} outras decisões estão detalhadas nas seções Pendências e Agenda.")
        }

        private fun ganttHeader(timeline: GanttTimeline, left: Float, right: Float, top: Float, rowHeight: Float) {
            val width = right - left
            repeat(timeline.rowCount) { row ->
                val rowTop = top + row * rowHeight
                box(left, rowTop, width, rowHeight, if (row % 2 == 0) ink else Color.rgb(37, 70, 85), 0f)
                timeline.bands(row, width * 3f).forEach { band ->
                    val x = left + width * timeline.axis.boundary(band.start)
                    val bandWidth = width * timeline.fraction(band)
                    line(x, rowTop, x, rowTop + rowHeight, muted)
                    val label = if (row == 2) band.label.replace("Sem. ", "S").replace(" / 20", "/") else band.label
                    val padding = if (row == 3) 0.7f else 2f
                    val minimum = when (row) { 0 -> 22f; 1 -> 18f; 2 -> 18f; else -> 9.9f }
                    if (bandWidth >= minimum) {
                        text(x + padding, rowTop + 9f, label, 7.2f, Color.WHITE, row == 0, bandWidth - padding * 2)
                    }
                }
            }
        }

        fun schedule() {
            section("CRONOGRAMA FÍSICO  |  VISÃO GANTT",
                "Barras azul/verde = previsto/realizado  •  Pontos azul-escuro/âmbar = pago/a pagar  •  Linha tracejada = data-base")
            val asOf = project.referenceDate ?: today
            val rows = project.stages.map { it.ganttRow(asOf, laborSpend.byStage[it.id].orEmpty()) }
            val axis = GanttAxis.from(rows)
            val timeline = axis?.let { GanttTimeline(it, project.ganttScale) }
            paragraph("Escala: ${project.ganttScale.label}. Cabeçalho: ano, mês" + if (project.ganttScale == GanttScale.MONTHS) "." else ", semana ISO" + if (project.ganttScale == GanttScale.DAYS) " e dia." else ".")
            paragraph("Avanço geral da obra: ${stagePercent(project.generalStageProgress)}. Execução e materiais recebidos aparecem separados; os pesos podem ser ajustados por etapa. Materiais sem etapa vinculada: ${project.unlinkedMaterials}.")
            if (rows.isEmpty()) paragraph("Nenhuma etapa cadastrada.")
            if (rows.isNotEmpty() && axis == null) paragraph("Informe as datas da execução ou do pagamento para montar o Gantt.")
            if (rows.isNotEmpty()) {
                val graphLeft = 258f
                val graphRight = 621f
                fun x(fraction: Float) = graphLeft + (graphRight - graphLeft) * fraction
                val rowsPerPage = 5
                val bandHeight = 58f
                rows.chunked(rowsPerPage).forEachIndexed { batch, group ->
                    if (batch > 0) startPage(continuation = true)
                    val headerHeight = (timeline?.rowCount ?: 1) * 13f
                    val top = y + headerHeight + 19f
                    val bottom = top + group.size * bandHeight
                    group.forEachIndexed { index, row ->
                        val band = top + index * bandHeight
                        if (index % 2 == 0) box(LEFT, band, CONTENT, bandHeight, light, 1f)
                        text(LEFT + 7, band + 13, "${batch * rowsPerPage + index + 1}. ${row.stage.name}", 9.7f, ink, true, graphLeft - LEFT - 16f)
                        val evolution = project.stageProgress(row.stage)
                        text(LEFT + 7, band + 25, "Execução: ${stagePercent(evolution.execution.toDouble())}", 8.5f, green, width = 200f)
                        text(LEFT + 7, band + 37, "Materiais: ${evolution.materialsText}", 8.5f, blue, width = 200f)
                        text(LEFT + 7, band + 50, "Geral da etapa: ${evolution.generalText}", 9f, ink, true, width = 200f)
                        if (axis != null) {
                            fun bar(period: GanttPeriod, offset: Float, color: Int) {
                                val left = x(axis.boundary(period.start))
                                val right = x(axis.boundary(period.end.plusDays(1)))
                                box(left, band + offset, (right - left).coerceAtLeast(1.5f), 8f, color, 2f)
                            }
                            row.planned?.let { bar(it, 5f, blue) }
                            row.actual?.let { bar(it, 16f, green) }
                            for (event in row.spend) {
                                val date = event.date ?: continue
                                val center = x(axis.dayCenter(date))
                                paint.color = if (event.paid) ink else gold
                                canvas.drawCircle(center, band + if (event.paid) 30f else 37f, 2.7f, paint)
                            }
                        }
                        text(graphRight + 9, band + 13, "Prev. ${row.plannedDates}", 8.2f, if (row.planned == null) muted else blue, width = 174f)
                        text(graphRight + 9, band + 26, "Real ${row.actualDates}", 8.2f, if (row.actual == null) muted else green, width = 174f)
                        text(graphRight + 9, band + 39, "Mão de obra ${row.stage.budget.costText()}", 8.2f, ink, true, width = 174f)
                        text(graphRight + 9, band + 51, evolution.weightsText, 7.5f, muted, width = 174f)
                    }
                    if (axis != null) {
                        timeline!!.gridDates((graphRight - graphLeft) * 3f).forEach { date ->
                            val tick = x(axis.boundary(date))
                            line(tick, top, tick, bottom, track)
                        }
                        ganttHeader(timeline, graphLeft, graphRight, top - headerHeight, 13f)
                        if (asOf in axis.start..axis.end) {
                            val base = x(axis.dayCenter(asOf))
                            line(base, top - 3, base, bottom, muted, dashed = true)
                        }
                    }
                    text(LEFT + 7, top - 10, "ETAPA", 9.5f, ink, true)
                    text(graphRight + 9, top - 10, "DATAS", 9.5f, ink, true)
                    y = bottom + 20f
                }
            }
            val completed = project.stages.filter { it.actualEnd != null }.maxOfOrNull { it.actualEnd!! }
            val forecast = project.stages.mapNotNull { it.plannedEnd }.maxOrNull()
            callout("MARCOS E PREVISÕES", "Início da obra: ${project.start.dateOr()}. Última conclusão registrada: ${completed.dateOr()}. Última data prevista informada: ${forecast.dateOr()}.")
            paragraph("O calendário depende da execução, das entregas de materiais e da disponibilidade da equipe. Etapas sem datas continuam abertas no cronograma.")
            paragraph("Semanas ISO começam na segunda-feira. Na virada do ano, o número da semana pode pertencer ao ano anterior ou seguinte. Em períodos extensos, rótulos intermediários são espaçados; o cronograma integral pode ser ampliado no app.")
        }

        fun labor() {
            val laborShare = if (total.laborForecast > 0) total.laborPaid.toDouble() / total.laborForecast else 0.0
            section("BALANÇO FINANCEIRO  |  ETAPAS E PROJEÇÃO", "Cronograma físico-financeiro, execução e conciliação da mão de obra")
            tiles(
                Tile("MÃO DE OBRA PREVISTA", total.laborForecast.brl(), "${total.laborAllocated.brl()} vinculados às etapas"),
                Tile("MÃO DE OBRA PAGA", total.laborPaid.brl(), "${percent(laborShare)} do valor previsto", green),
                Tile("SALDO DE MÃO DE OBRA", (total.laborForecast - total.laborPaid).brl(), "Previsão menos pagamentos", blue),
            )
            heading("PREVISTO E PAGO POR ETAPA")
            val visible = project.stages.take(8)
            if (visible.isEmpty()) paragraph("Nenhuma etapa cadastrada.")
            val max = visible.maxOfOrNull { maxOf(it.budget ?: 0, total.paid(CostKind.LABOR, it.id)) }?.coerceAtLeast(1) ?: 1
            visible.forEachIndexed { index, stage ->
                val paid = total.paid(CostKind.LABOR, stage.id)
                val barLeft = 257f
                val barWidth = 296f
                text(LEFT, y + 16f, "${index + 1}. ${stage.name}", 10f, width = 214f)
                box(barLeft, y + 2f, barWidth, 8f, track, 2f)
                if ((stage.budget ?: 0) > 0) box(barLeft, y + 2f, barWidth * (stage.budget!!.toFloat() / max), 8f, blue, 2f)
                if (paid > 0) box(barLeft, y + 13f, barWidth * (paid.toFloat() / max), 8f, green, 2f)
                text(564f, y + 10f, "Previsto ${stage.budget.costText()}", 8.5f, blue, width = 238f)
                text(564f, y + 22f, "Pago ${paid.brl()}", 8.5f, green, width = 238f)
                y += 29f
            }
            if (project.stages.size > visible.size) paragraph("Todas as etapas estão discriminadas na tabela físico-financeira.")
            if (project.stages.size > 4) startPage(continuation = true)
            heading("EVOLUÇÃO DAS ETAPAS | EXECUÇÃO E MATERIAIS")
            val evolutions = project.stages.map { project.stageProgress(it) }
            table(listOf("ETAPA", "EXECUÇÃO", "MATERIAIS", "GERAL", "PESOS", "APURAÇÃO DOS MATERIAIS"),
                listOf(220f, 88f, 88f, 88f, 132f, 158f), project.stages.zip(evolutions).map { (stage, evolution) ->
                    listOf(stage.name, stagePercent(evolution.execution.toDouble()), evolution.materialsText, evolution.generalText,
                        evolution.weightsText, evolution.basisText)
                }, "Nenhuma etapa cadastrada.", totals = mapOf(0 to "MÉDIA DAS ETAPAS", 1 to stagePercent(total.physicalProgress),
                    2 to evolutions.mapNotNull { it.materials }.takeIf { it.isNotEmpty() }?.average()?.let(::stagePercent).orEmpty(),
                    3 to stagePercent(project.generalStageProgress)))
            paragraph("O avanço geral é a média ponderada da execução e do recebimento dos materiais da etapa. Sem compras de material vinculadas, considera apenas a execução. A coluna Materiais da última linha considera as etapas com material. Pagamentos não alteram esses percentuais.")
            heading("CONCILIAÇÃO E CRONOGRAMA FÍSICO-FINANCEIRO")
            table(listOf("ETAPA / OBSERVAÇÕES", "PREVISTO", "REALIZADO", "AVANÇO", "VALOR PREVISTO", "PAGO EFETIVO", "A PAGAR / VENCIMENTO", "PAGO A MAIOR"),
                listOf(171f, 90f, 90f, 65f, 94f, 90f, 95f, 79f), project.stages.map { stage ->
                    val paid = total.paid(CostKind.LABOR, stage.id)
                    val next = project.nextPendingPosition(CostKind.LABOR, stage.id)
                    listOf(listOf(stage.name, stage.notes).filter(String::isNotBlank).joinToString("\n"),
                        "${stage.plannedStart.dateOr()} a ${stage.plannedEnd.dateOr()}", "${stage.actualStart.dateOr()} a ${stage.actualEnd.dateOr()}",
                        "${stage.progress}%\n${stage.status}", stage.budget.costText(), paid.brl(),
                        "${stage.budget?.let { (it - paid).coerceAtLeast(0) }.costText()}\n${(next?.date ?: stage.dueDate).dateOr("Sem data")}" +
                            if (next?.estimatedDate == true) "\nEstimativa pelo fim previsto" else "",
                        stage.budget?.let { (paid - it).coerceAtLeast(0) }.costText())
                }, "Nenhuma etapa cadastrada.", totals = mapOf(4 to sumText(project.stages.map { it.budget }),
                    5 to total.laborPaid.brl(),
                    6 to sumText(project.stages.map { stage -> stage.budget?.let { (it - total.paid(CostKind.LABOR, stage.id)).coerceAtLeast(0) } }),
                    7 to sumText(project.stages.map { stage -> stage.budget?.let { (total.paid(CostKind.LABOR, stage.id) - it).coerceAtLeast(0) } })))
            val undefined = project.stages.count { it.budget == null }
            if (undefined > 0) paragraph("$undefined etapa(s) com valor a definir. Confirme se os serviços estão cobertos pelo contrato informado.", gold)
        }

        fun purchases() {
            section("COMPRAS  |  MATERIAIS E SERVIÇOS", "Fornecedores, compras pagas e acréscimos declarados")
            tiles(
                Tile("COMPRAS PAGAS", total.materialPaid.brl(), "Pagamentos realizados", green),
                Tile("TOTAL CONHECIDO DE COMPRAS", total.materialForecast.brl(), "Documentos, cotações e estimativas"),
                Tile("ACRÉSCIMOS DECLARADOS", total.extras.brl(), "Integram a previsão parcial", gold),
            )
            barSection("COMPRAS PAGAS POR FORNECEDOR", total.spendBySupplier.mapIndexed { index, (supplier, amount) ->
                Bar(supplier, amount, listOf(green, blue, gold)[index % 3])
            }, "Nenhuma compra paga foi registrada.")
            heading("RESUMO DAS COMPRAS")
            table(listOf("COMPRA / FORNECEDOR", "TIPO", "DOCUMENTO", "TOTAL PREVISTO", "PAGO EFETIVO", "A PAGAR", "SITUAÇÃO", "PAGO A MAIOR"),
                listOf(160f, 65f, 80f, 100f, 100f, 100f, 89f, 80f), project.purchases.map { order ->
                    val position = InstallmentBalance(order, total.paid(CostKind.MATERIAL, order.id))
                    listOf("${order.name}\n${order.supplier.textOr("Fornecedor a definir")}\n" + if (order.extra) "Acréscimo declarado" else "Escopo previsto",
                        order.nature.label, order.document.textOr(), order.forecast.costText(), position.paid.brl(),
                        position.remaining.costText(), financialStatus(order.forecast, position.paid, project.purchaseNextDue(order), today).label, position.credit.costText())
                }, "Nenhuma compra cadastrada.", totals = mapOf(3 to sumText(project.purchases.map { it.forecast }),
                    4 to total.materialPaid.brl(), 5 to sumText(project.purchases.map { total.materialOutstanding(it) }),
                    7 to sumText(project.purchases.map { InstallmentBalance(it, total.paid(CostKind.MATERIAL, it.id)).credit })))
            if (project.purchases.any { it.installmentCount != null }) {
                heading("CONDIÇÕES DE PAGAMENTO DAS COMPRAS")
                table(listOf("COMPRA", "MÉTODO", "PARCELAMENTO"), listOf(330f, 180f, 264f),
                    project.purchases.map { listOf(it.name, project.purchaseMethods(it), it.installmentText()) }, "")
            }
            val installments = project.installmentBalances(total)
            if (installments.isNotEmpty()) {
                heading("PARCELADOS | PAGOS E A PAGAR")
                paragraph("O pago efetivo soma apenas os pagamentos registrados. As parcelas futuras permanecem no saldo a pagar; a condição de parcelamento não representa quitação.")
                table(listOf("COMPRA", "PARCELAS", "TOTAL", "PAGO EFETIVO", "A PAGAR", "CRÉDITO"),
                    listOf(228f, 58f, 120f, 124f, 124f, 120f), installments.map { position ->
                        listOf(position.purchase.name, position.purchase.installmentCount.toString(), position.total.costText(),
                            position.paid.brl(), position.remaining.costText(), position.credit.costText())
                    }, "", totals = mapOf(2 to sumText(installments.map { it.total }), 3 to installments.sumOf { it.paid }.brl(),
                        4 to sumText(installments.map { it.remaining }), 5 to sumText(installments.map { it.credit })))
            }
        }

        fun projections() {
            section("PROJEÇÃO FINANCEIRA  |  COMPRAS E DESEMBOLSOS", "Valores a pagar conhecidos, compras a cotar e datas de pagamento previstas")
            tiles(
                Tile("COMPRAS A PAGAR", total.materialToPay.brl(), "Valores conhecidos", blue),
                Tile("MÃO DE OBRA A PAGAR", total.laborToPay.brl(), "Saldo do contrato conhecido", blue),
                Tile("SALDO DA PREVISÃO", total.remaining.brl(), "Inclui eventual pagamento a maior"),
                Tile("COMPRAS A COTAR", project.purchases.count { it.forecast == null }.toString(), "Sem valor definido", gold),
            )
            barSection("COMPRAS A ADQUIRIR OU PAGAR", total.projectedPurchases.mapNotNull { purchase ->
                total.materialOutstanding(purchase)?.takeIf { it > 0 }?.let { Bar(purchase.name, it, blue) }
            }, "Nenhuma compra com saldo conhecido.")
            heading("CALENDÁRIO DE DESEMBOLSOS")
            table(listOf("REFERÊNCIA", "TIPO", "DATA PREVISTA", "VALOR A PAGAR"),
                listOf(355f, 137f, 136f, 146f), CostPaymentCalendar(project).pending.sortedWith(compareBy(nullsLast()) { it.date }).map {
                    val date = it.date.dateOr("Sem data")
                    listOf("${it.target.name}\n${it.label}", it.target.category.label,
                        if (it.estimatedDate) "$date\nEstimativa pelo fim previsto" else date, it.amount.costText())
                }, "Nenhum desembolso previsto foi cadastrado.",
                totals = mapOf(3 to sumText(CostPaymentCalendar(project).pending.map { it.amount })))
            heading("DESEMBOLSOS DE MÃO DE OBRA POR SEMANA")
            table(listOf("SEMANA", "TOTAL PAGO", "SALDO CONHECIDO A PAGAR", "VALORES A DEFINIR"),
                listOf(250f, 135f, 205f, 184f), laborSpend.weeks.map {
                    listOf(it.label, it.paid.brl(), it.balance.brl(), it.undefined.toString())
                }, "Nenhum desembolso de mão de obra registrado ou previsto.", totals = mapOf(
                    1 to laborSpend.weeks.sumOf { it.paid }.brl(), 2 to laborSpend.weeks.sumOf { it.balance }.brl(),
                    3 to laborSpend.weeks.sumOf { it.undefined }.toString()))
            callout("CRITÉRIO DE CONTROLE", "Compras previstas permanecem no escopo da obra. Acréscimos são sinalizados apenas nas compras marcadas como acréscimo. Datas e valores em aberto aguardam confirmação.")
        }

        fun costPaymentCalendar() {
            val calendar = CostPaymentCalendar(project)
            section("CALENDÁRIO DE PAGAMENTOS | POR SEMANA", "Materiais, serviços e mão de obra com detalhamento de cada custo")
            paragraph("Pago efetivo segue a data do pagamento. A pagar segue o vencimento do saldo. Parcelas quitadas antecipadamente não são cobradas novamente na semana prevista. Sem data e valores a definir permanecem identificados.")
            table(listOf("SEMANA", "PAGO EFETIVO", "A PAGAR CONHECIDO", "TOTAL CONHECIDO", "A DEFINIR"),
                listOf(250f, 125f, 160f, 155f, 84f), calendar.weeks.map { week ->
                    listOf(week.label, week.paid.brl(), if (week.pending.entirelyUndefined) UNDEFINED else week.pending.known.brl(),
                        week.knownTotal.brl(), week.pending.undefinedCount.toString())
                }, "Nenhum custo ou pagamento cadastrado.", totals = mapOf(1 to total.paid.brl(),
                    2 to sumText(calendar.pending.map { it.amount }), 3 to sumText(calendar.entries.map { it.amount }),
                    4 to calendar.pending.count { it.amount == null }.toString()))
            calendar.weeks.forEach { week ->
                heading(week.label)
                table(listOf("CUSTO / PARCELA", "TIPO", "DATA", "SITUAÇÃO", "VALOR"),
                    listOf(335f, 112f, 105f, 100f, 122f), week.entries.map { entry ->
                        listOf("${entry.target.name}\n${entry.label}", entry.target.category.label,
                            entry.date.dateOr("Sem data") + if (entry.estimatedDate) "\nEstimativa pelo fim previsto" else "",
                            if (entry.paid) "Pago" else "A pagar", entry.amount.costText())
                    }, "", totals = mapOf(0 to "TOTAL DA SEMANA", 4 to sumText(week.entries.map { it.amount })))
            }
            if (project.scheduledPayments.isNotEmpty()) {
                heading("PROGRAMAÇÃO E QUITAÇÃO POR PARCELA")
                paragraph("Abatido considera pagamentos vinculados à parcela e pagamentos anteriores distribuídos por ordem de vencimento. O histórico e os comprovantes continuam na prestação de contas.")
                table(listOf("CUSTO / PARCELA", "VENCIMENTO", "PROGRAMADO", "ABATIDO", "A PAGAR", "CONCLUÍDO"),
                    listOf(294f, 100f, 100f, 100f, 100f, 80f), calendar.positions.filter { it.schedule != null }.map { position ->
                        listOf("${position.target.name}\n${position.label}", position.date.dateOr("Sem data"), position.gross.costText(),
                            position.applied.brl(), position.remaining.costText(), if (position.completed) "Sim" else "Não")
                    }, "", totals = calendar.positions.filter { it.schedule != null }.let { positions -> mapOf(
                        2 to sumText(positions.map { it.gross }), 3 to positions.sumOf { it.applied }.brl(),
                        4 to sumText(positions.map { it.remaining })) })
            }
        }

        fun materials() {
            section("COMPRAS  |  LISTA DETALHADA", "Descrição, quantidades, valores unitários, descontos e conciliação documental")
            if (project.purchases.isEmpty()) paragraph("Nenhuma compra cadastrada.")
            project.purchases.forEach { order ->
                ensure(100f)
                val stageName = project.stageName(order.stageId)
                heading(order.name)
                paragraph("Tipo: ${order.nature.label}")
                paragraph(listOf(order.supplier.takeIf(String::isNotBlank), order.document.takeIf(String::isNotBlank),
                    order.documentDate.dateText().takeIf(String::isNotBlank), stageName, total.purchaseStatus(order),
                    if (order.extra) "Acréscimo declarado" else null).filterNotNull().joinToString("  •  "))
                if (order.deliveryExpected != null || order.receivedDate != null) {
                    paragraph("Entrega prevista: ${order.deliveryExpected.dateOr()}  •  Recebido em: ${order.receivedDate.dateOr()}")
                }
                if (order.nature == CostNature.MATERIAL) paragraph("Material recebido: ${stagePercent(order.receivedPercent.toDouble())}. Alimenta o avanço dos materiais da etapa vinculada.")
                if (order.method.isNotBlank() || order.payer.isNotBlank()) {
                    paragraph("Método de pagamento: ${order.method.textOr()}  •  Quem pagou: ${order.payer.textOr()}")
                }
                if (order.installmentCount != null) paragraph("Condição de pagamento: ${order.installmentText()}")
                table(listOf("DESCRIÇÃO", "QUANTIDADE", "UNIDADE", "PREÇO UNITÁRIO", "SUBTOTAL"),
                    listOf(374f, 87f, 78f, 113f, 122f), order.lines.map { item ->
                        listOf(item.name, item.quantity.quantityInput().textOr(UNDEFINED), item.unit.textOr(),
                            item.unitPrice.costText(), item.subtotal.costText())
                    }, "Sem discriminação cadastrada para esta compra.",
                    totals = mapOf(0 to "TOTAL DOS ITENS", 4 to sumText(order.lines.map { it.subtotal })))
                val amounts = buildList {
                    order.lineSum?.let { add("Subtotal dos itens ${it.brl()}") }
                    if (order.discount != 0L) add("Desconto ${order.discount.brl()}")
                    add("Total previsto ${order.forecast.costText()}")
                    add("Pago ${total.paid(CostKind.MATERIAL, order.id).brl()}")
                }
                paragraph(amounts.joinToString("  •  "))
                order.variance?.takeIf { it != 0L }?.let {
                    paragraph("Diferença de ${it.brl()} entre o total do documento e os itens após desconto.", gold)
                }
                if (order.notes.isNotBlank()) paragraph(order.notes)
                if (order.documents.isNotEmpty()) paragraph("Documentos anexados: ${order.documents.joinToString { it.name }}")
                y += 7f
            }
        }

        fun weeklyMatrix() {
            val model = DisbursementTable(project)
            section("DESEMBOLSOS  |  TABELA SEMANAL EM CAMADAS", "Material e Serviço · Mão de obra integra Serviço · Etapas e custos com seus subtotais")
            paragraph("As linhas de tipo e etapa são subtotais; a última linha é o total da obra. Pago e a pagar são totais gerais. As colunas semanais somam pagamentos reais e saldos nos vencimentos.")
            if (model.entries.isEmpty()) { paragraph("Nenhum desembolso cadastrado."); return }
            val rows = model.visible(model.roots.flatMap { it.children }.map { it.key }.toSet() + model.roots.map { it.key })
                .filter { it.first.layer != FlowLayer.ENTRY }
            val windows = (0 until model.pageCount).flatMap { model.columns(it).chunked(3) }
            windows.forEachIndexed { index, dates ->
                if (index > 0) startPage(continuation = true)
                val dated = dates.filterNotNull()
                heading(if (dated.isEmpty()) "SEM DATA DEFINIDA" else "${dated.first().dateText()} A ${dated.last().plusDays(6).dateText()}")
                val labels = dates.map { date -> if (date == null) "SEM DATA" else "S${date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())} ${date.format(shortDate)}" }
                val weekWidth = 234f / dates.size.coerceAtLeast(1)
                fun row(node: FlowNode, prefix: String): List<String> = listOf(prefix + node.label, model.people(node, false), model.people(node, true),
                    node.paid.brl(), if (node.pending.entirelyUndefined) UNDEFINED else node.pending.known.brl() + if (node.pending.partial) " + ?" else "") +
                    dates.map { date ->
                        val amount = node.amounts(date, CashView.TOTAL)
                        if (amount.entirelyUndefined) UNDEFINED else amount.known.brl() + if (amount.partial) " + ?" else ""
                    }
                table(listOf("ETAPA / CUSTO", "QUEM PAGA", "COMO PAGA", "PAGO EFETIVO", "A PAGAR") + labels,
                    listOf(180f, 90f, 90f, 90f, 90f) + dates.map { weekWidth },
                    rows.map { (node, depth) -> row(node, when (depth) { 1 -> "Etapa: "; 2 -> "Custo: "; else -> "" }) }, "",
                    totals = row(model.total, "").mapIndexed { column, value -> column to if (column == 0) "TOTAL DA OBRA" else value }.toMap(),
                    emphasizedRows = rows.indices.filter { rows[it].second < 2 }.toSet())
            }
        }

        fun planning() {
            if (project.workPlans.isEmpty()) return
            project.workPlans.forEach { plan ->
                section("PLANEJAMENTO E ORÇAMENTO POR ETAPA", "${plan.title} · ${plan.type.label} · ${plan.location.textOr()}")
                if (plan.scope.isNotBlank()) paragraph(plan.scope)
                tiles(
                    Tile("ESTIMATIVA CONHECIDA", plan.knownTotal.brl(), "${plan.missing} pendências de orçamento"),
                    Tile("ÚLTIMO ORÇAMENTO APROVADO", plan.approvedTotal.brl(), "Última aprovação de cada etapa", green),
                    Tile("ETAPAS APROVADAS", "${plan.stages.count { it.approved }} / ${plan.stages.size}", "A revisão do rascunho exige nova aprovação"),
                )
                table(listOf("ETAPA / SITUAÇÃO", "ESTIMATIVA", "APROVADO", "PREVISÃO ATUAL", "PAGO EFETIVO", "DESVIO / AVANÇO"),
                    listOf(235f, 105f, 110f, 110f, 110f, 104f), plan.stages.map { stage ->
                        val control = project.planControl(stage)
                        listOf("${stage.name}\n${stage.status}", stage.knownTotal.brl(), stage.latestApproval?.total.costText(),
                            control?.let { it.forecast.brl() + if (it.undefined > 0) " (parcial)" else "" } ?: "Sem vínculo",
                            control?.paid.costText(), control?.let { value ->
                                (stage.latestApproval?.let { (value.forecast - it.total).brl() } ?: UNDEFINED) + "\n${value.progress}% físico"
                            } ?: "Sem vínculo")
                    }, "Sem etapas.", totals = mapOf(1 to plan.knownTotal.brl() + if (plan.missing > 0) " + ?" else "",
                        2 to sumText(plan.stages.map { it.latestApproval?.total }),
                        3 to sumText(plan.stages.map { project.planControl(it)?.forecast }) +
                            if (plan.stages.any { (project.planControl(it)?.undefined ?: 0) > 0 } && plan.stages.all { project.planControl(it) != null }) " + ?" else "",
                        4 to sumText(plan.stages.map { project.planControl(it)?.paid })))
                paragraph("A previsão atual usa a mão de obra da etapa vinculada e todas as compras / serviços vinculados a ela. O pago usa apenas os pagamentos registrados. O rascunho e a aprovação não criam pagamentos.")
                (plan.assumptions + plan.questions).forEach { paragraph("• $it") }
                plan.stages.forEach { stage ->
                    heading(stage.name.uppercase(brazil))
                    if (stage.notes.isNotBlank()) paragraph(stage.notes)
                    stage.services.forEach { service ->
                        ensure(125f)
                        heading(service.name)
                        table(listOf("INSUMO / TIPO", "QUANTIDADE", "UNIDADE", "PREÇO UNIT.", "SUBTOTAL", "FONTE / DATA / CONDIÇÕES"),
                            listOf(230f, 78f, 85f, 96f, 100f, 185f), service.items.map { item ->
                                listOf("${item.name}\n${item.category.label}" + if (item.estimated && item.subtotal != null) " · Estimado" else "",
                                    item.quantity.quantityInput().ifBlank { UNDEFINED }, item.unit.textOr(UNDEFINED),
                                    item.unitPrice.costText(), item.subtotal.costText(),
                                    "${item.priceSource.textOr(UNDEFINED)}\n${item.priceDate.dateOr()}\n${item.notes}".trim())
                            }, "Serviço sem insumos: detalhar antes de aprovar.",
                            totals = mapOf(0 to "TOTAL DOS INSUMOS", 4 to sumText(service.items.map { it.subtotal })))
                    }
                    if (stage.approvals.isNotEmpty()) {
                        heading("HISTÓRICO DE APROVAÇÃO")
                        table(listOf("REVISÃO", "DATA", "RESPONSÁVEL", "APROVADO", "CONDIÇÕES"),
                            listOf(75f, 95f, 170f, 110f, 324f), stage.approvals.map { approval ->
                                listOf(approval.revision.toString(), approval.date.dateText(), approval.approver,
                                    approval.total.brl(), approval.notes.textOr())
                            }, "")
                    }
                }
            }
        }

        fun payments() {
            section("PAGAMENTOS  |  PRESTAÇÃO DE CONTAS", "Cada desembolso aparece vinculado à etapa ou compra correspondente")
            tiles(
                Tile("TOTAL PAGO", total.paid.brl(), "Todos os pagamentos registrados", green),
                Tile("COMPRAS PAGAS", total.materialPaid.brl(), "Inclui pagamentos parciais"),
                Tile("MÃO DE OBRA PAGA", total.laborPaid.brl(), "Inclui adiantamentos"),
            )
            table(listOf("REFERÊNCIA / TIPO", "VALOR", "DATA", "PAGADOR", "MEIO", "COMPROVANTE / OBSERVAÇÕES"),
                listOf(194f, 90f, 81f, 109f, 98f, 202f), project.payments.sortedWith(compareBy(nullsLast()) { it.date }).map {
                    listOf("${project.targetName(it)}\n${project.costNature(it.kind, it.targetId).label} · ${it.kind.label}", it.amount.brl(), it.date.dateOr(UNREPORTED),
                        it.payer.textOr(), it.method.textOr(), buildList {
                            if (it.receipt.isNotBlank()) add(it.receipt)
                            addAll(it.proofs.map { proof -> proof.name })
                            if (it.notes.isNotBlank()) add(it.notes)
                        }.ifEmpty { listOf(UNREPORTED) }.joinToString("\n"))
                }, "Nenhum pagamento registrado.", totals = mapOf(1 to total.paid.brl()))
            if (total.payers.isNotEmpty()) {
                heading("CONSOLIDADO POR PAGADOR")
                table(listOf("PAGADOR", "TIPO", "MEIO", "TOTAL PAGO"), listOf(242f, 167f, 191f, 174f),
                    total.payers.map { listOf(it.payer, it.kind.label, it.method, it.amount.brl()) }, "",
                    totals = mapOf(3 to total.paid.brl()))
            }
            if (project.paymentAudit.isNotEmpty()) {
                ensure(120f)
                heading("HISTÓRICO DE REVISÃO DO PAGO EFETIVO")
                paragraph("Os valores abaixo são registros anteriores ou retirados do total após revisão. Não são somados novamente ao pago efetivo.")
                table(listOf("CUSTO", "VALOR ANTERIOR", "DATA ORIGINAL", "REVISÃO / SITUAÇÃO", "MOTIVO / COMPROVANTES"),
                    listOf(180f, 110f, 100f, 165f, 219f), project.paymentAudit.map { entry ->
                        listOf(entry.costName.ifBlank { project.targetName(entry.payment) }, entry.payment.amount.brl(), entry.payment.date.dateOr("Sem data"),
                            "${entry.date.dateText()}\n" + if (!entry.voided) "Valor anterior" else if (entry.restoredAt != null) "Restaurado ${entry.restoredAt.dateText()}" else "Fora do total pago",
                            entry.reason + "\n" + entry.payment.proofs.joinToString { it.name })
                    }, "")
            }
        }

        fun issues() {
            section("CONTROLE  |  PENDÊNCIAS E PRÓXIMAS DECISÕES", "O que falta confirmar e o histórico das pendências da obra")
            val alerts = total.alerts().map { (subject, action) -> listOf(subject, "Conferir", action) }
            val entered = project.issues.map { issue -> listOf(issue.title, if (issue.closed) "Resolvida" else "Em aberto", issue.action.textOr()) }
            table(listOf("ASSUNTO", "SITUAÇÃO", "AÇÃO / OBSERVAÇÕES"), listOf(218f, 131f, 425f),
                alerts + entered, "Nenhuma pendência identificada.")
            if (project.notes.isNotBlank()) {
                heading("OBSERVAÇÕES DA OBRA")
                paragraph(project.notes)
            }
            heading("REGRAS DE ATUALIZAÇÃO")
            paragraph("Registre os dados do documento de cada compra. Vincule cada pagamento à compra ou etapa e informe o comprovante. Marque como acréscimo somente o que for confirmado. Mantenha as datas previstas e reais atualizadas conforme a execução.")
        }

        fun agenda() {
            val agenda = WorkAgenda(project, total, today)
            section("AGENDA DA OBRA", "Compromissos em aberto  •  Período de ${today.dateText()} a ${agenda.cutoff.dateText()}")
            val dueSoon = agenda.paymentsIn(AgendaWindow.NEXT_SEVEN_DAYS)
            tiles(
                Tile("PRAZOS ATRASADOS", agenda.inWindow(AgendaWindow.OVERDUE).size.toString(), "Etapas, entregas e pagamentos", gold),
                Tile("COMPROMISSOS ATÉ 7 DIAS", agenda.inWindow(AgendaWindow.NEXT_SEVEN_DAYS).size.toString(), "Inclui os vencimentos de hoje"),
                Tile("VALORES A PAGAR ATÉ 7 DIAS", dueSoon.sumOf { it.amount ?: 0 }.brl(), "${dueSoon.count { it.amount == null }} pagamento(s) com valor a definir", blue),
            )
            table(listOf("REFERÊNCIA", "COMPROMISSO", "PRAZO", "SITUAÇÃO", "SALDO A PAGAR"),
                listOf(295f, 142f, 101f, 121f, 115f), agenda.entries.map {
                    listOf(it.name, it.kind.label, it.date.dateOr("Sem data"), agenda.window(it.date).label,
                        if (it.isPayment) it.amount.costText() else "—")
                }, "Nenhum compromisso em aberto.", totals = mapOf(4 to sumText(agenda.entries.filter { it.isPayment }.map { it.amount })))
        }

        fun diary() {
            section("DIÁRIO DE OBRA", "Atividades, ocorrências e arquivos vinculados ao registro")
            table(listOf("DATA", "REGISTRO / ETAPA", "ATIVIDADES REALIZADAS", "OCORRÊNCIAS E IMPEDIMENTOS", "ARQUIVOS"),
                listOf(83f, 151f, 201f, 205f, 134f), project.diaryNewestFirst().map {
                    listOf(it.date.dateText(), listOfNotNull(it.title, project.stageName(it.stageId)).joinToString("\n"),
                        it.activities.ifBlank { "—" }, it.occurrences.ifBlank { "—" },
                        it.attachments.joinToString("\n") { attachment -> attachment.name }.ifBlank { "—" })
                }, "Nenhum registro no diário.")
        }

    }
}
