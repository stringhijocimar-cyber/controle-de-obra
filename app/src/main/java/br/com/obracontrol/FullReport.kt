package br.com.obracontrol

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

internal object FullReport {
    private const val WIDTH = 842
    private const val HEIGHT = 595
    private const val LEFT = 34f
    private const val RIGHT = WIDTH - LEFT
    private const val CONTENT = RIGHT - LEFT
    private const val BOTTOM = 550f
    private val ink = Color.rgb(20, 38, 59)
    private val muted = Color.rgb(69, 92, 113)
    private val light = Color.rgb(240, 245, 248)
    private val track = Color.rgb(220, 231, 238)
    private val green = Color.rgb(8, 116, 91)
    private val greenTint = Color.rgb(230, 244, 239)
    private val blue = Color.rgb(39, 108, 159)
    private val gold = Color.rgb(145, 80, 9)
    private val goldTint = Color.rgb(255, 242, 221)
    private val shortDate = DateTimeFormatter.ofPattern("dd/MM", brazil)

    fun write(output: OutputStream, project: Project, today: LocalDate = LocalDate.now()) {
        val document = PdfDocument()
        try {
            val report = Report(document, project, today)
            try {
                report.dashboard()
                report.schedule()
                report.labor()
                report.purchases()
                report.projections()
                report.materials()
                report.payments()
                report.issues()
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
    private data class Period(val start: LocalDate, val end: LocalDate)
    private data class ScheduleRow(val stage: Stage, val planned: Period?, val actual: Period?)

    private class Report(
        private val pdf: PdfDocument,
        private val project: Project,
        private val today: LocalDate,
    ) {
        private val total = Balance(project)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
        private val bold = Typeface.create("sans-serif", Typeface.BOLD)
        private var page: PdfDocument.Page? = null
        private val canvas: Canvas get() = checkNotNull(page).canvas
        private var number = 0
        private var title = ""
        private var subtitle = ""
        private var y = 0f

        fun finish() {
            page?.let(pdf::finishPage)
            page = null
        }

        private fun section(heading: String, caption: String) {
            title = heading
            subtitle = caption
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

        private fun wrap(value: String, width: Float, size: Float): List<String> {
            paint.typeface = normal
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

        private fun table(columns: List<String>, widths: List<Float>, rows: List<List<String>>, empty: String) {
            if (rows.isEmpty()) {
                paragraph(empty)
                return
            }
            fun header() {
                box(LEFT, y, CONTENT, 25f, ink, 4f)
                var x = LEFT
                columns.zip(widths).forEach { (column, width) ->
                    text(x + 7, y + 16, column, 8.5f, Color.WHITE, true, width - 14)
                    x += width
                }
                y += 29f
            }
            ensure(60f)
            header()
            rows.forEachIndexed { rowIndex, row ->
                val lines = row.zip(widths).map { (value, width) -> wrap(value, width - 14f, 9.4f) }
                val depth = lines.maxOf { it.size }
                var offset = 0
                while (offset < depth) {
                    if (y + 24 > BOTTOM) {
                        startPage(continuation = true)
                        header()
                    }
                    val available = ((BOTTOM - y - 10f) / 12.5f).toInt().coerceAtLeast(1)
                    if (offset == 0 && depth <= 25 && depth > available) {
                        startPage(continuation = true)
                        header()
                    }
                    val count = minOf(depth - offset, ((BOTTOM - y - 10f) / 12.5f).toInt().coerceAtLeast(1))
                    val height = count * 12.5f + 10f
                    if (rowIndex % 2 == 0) box(LEFT, y, CONTENT, height, light, 2f)
                    var x = LEFT
                    lines.forEachIndexed { index, cell ->
                        cell.drop(offset).take(count).forEachIndexed { indexInCell, value ->
                            text(x + 7, y + 14f + indexInCell * 12.5f, value, 9.4f)
                        }
                        x += widths[index]
                    }
                    offset += count
                    y += height
                }
            }
            y += 13f
        }

        fun dashboard() {
            val ratio = if (total.total == 0L) 0.0 else total.paid.toDouble() / total.total
            section("PAINEL DE CONTROLE  |  ${project.name.uppercase(brazil)}",
                "Prestação de contas e acompanhamento da execução  •  Base: ${project.referenceDate.dateOr(today.dateText())}")
            tiles(
                Tile("PREVISÃO PARCIAL", total.total.brl(), "Materiais e mão de obra"),
                Tile("TOTAL JÁ PAGO", total.paid.brl(), "${percent(ratio)} da previsão", green),
                Tile("SALDO CONHECIDO", total.remaining.brl(), "Previsão parcial menos pagamentos", blue),
                Tile("ACRÉSCIMO DECLARADO", total.extras.brl(), "Compras marcadas como acréscimo", gold),
            )
            text(LEFT, y + 14, "COMPOSIÇÃO DOS CUSTOS", 12f, ink, true)
            bars(listOf(Bar("Materiais pagos", total.materialPaid, green), Bar("Compras a pagar", total.materialToPay, blue),
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
        }

        fun schedule() {
            section("CRONOGRAMA FÍSICO  |  VISÃO GANTT",
                "Verde = realizado  •  Azul = previsto  •  Âmbar = data-base quando dentro da escala")
            val rows = project.stages.map { stage ->
                val planned = stage.plannedStart?.let { start -> stage.plannedEnd?.let { Period(start, it) } }
                val actual = stage.actualStart?.let { start ->
                    (stage.actualEnd ?: (project.referenceDate ?: today)).takeIf { it >= start }?.let { Period(start, it) }
                } ?: stage.actualEnd?.let { Period(it, it) }
                ScheduleRow(stage, planned, actual)
            }
            val periods = rows.flatMap { listOfNotNull(it.planned, it.actual) }
            if (rows.isEmpty()) paragraph("Nenhuma etapa cadastrada.")
            if (rows.isNotEmpty() && periods.isEmpty()) paragraph("Informe início e fim previstos ou datas reais para gerar as barras do Gantt.")
            if (periods.isNotEmpty()) {
                val first = periods.minOf { it.start }
                val last = periods.maxOf { it.end }
                val days = ChronoUnit.DAYS.between(first, last) + 1
                val graphLeft = 258f
                val graphRight = 621f
                fun x(date: LocalDate) = graphLeft + (graphRight - graphLeft) *
                    (ChronoUnit.DAYS.between(first, date).toDouble() / days).toFloat()
                rows.chunked(10).forEachIndexed { batch, group ->
                    if (batch > 0) startPage(continuation = true)
                    val top = y + 36f
                    val bottom = top + group.size * 35f
                    group.forEachIndexed { index, row ->
                        val band = top + index * 35f
                        if (index % 2 == 0) box(LEFT, band, CONTENT, 35f, light, 1f)
                        text(LEFT + 7, band + 15, "${batch * 10 + index + 1}. ${row.stage.name}", 9.7f, ink, true, graphLeft - LEFT - 16f)
                        text(LEFT + 7, band + 28, "${row.stage.status} · ${row.stage.progress}%", 8.1f, muted, width = 200f)
                        row.planned?.let { period -> box(x(period.start), band + 7, (x(period.end.plusDays(1)) - x(period.start)).coerceAtLeast(1.5f), 8f, blue, 2f) }
                        row.actual?.let { period -> box(x(period.start), band + 19, (x(period.end.plusDays(1)) - x(period.start)).coerceAtLeast(1.5f), 8f, green, 2f) }
                        text(graphRight + 9, band + 13, if (row.planned != null) "Prev. ${range(row.planned)}" else "Prev. a definir", 8.2f, if (row.planned == null) muted else blue, width = 174f)
                        val actualLabel = row.actual?.let {
                            if (row.stage.actualEnd == null) "Real ${it.start.dateText()} · em curso" else "Real ${range(it)}"
                        } ?: "Real a definir"
                        text(graphRight + 9, band + 26, actualLabel, 8.2f, if (row.actual == null) muted else green, width = 174f)
                    }
                    val step = ceil(days / 6.0).toLong().coerceAtLeast(1)
                    (0L until days step step).forEach { offset ->
                        val date = first.plusDays(offset)
                        line(x(date), top - 4, x(date), bottom, track)
                        text(x(date), top - 10, date.format(shortDate), 8.3f, muted)
                    }
                    (project.referenceDate ?: today).takeIf { it in first..last }?.let { base ->
                        line(x(base), top - 3, x(base), bottom, gold, dashed = true)
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
        }

        private fun range(period: Period): String = "${period.start.dateText()} a ${period.end.dateText()}"

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
            heading("CONCILIAÇÃO E CRONOGRAMA FÍSICO-FINANCEIRO")
            table(listOf("ETAPA / OBSERVAÇÕES", "PREVISTO", "REALIZADO", "AVANÇO", "VALOR PREVISTO", "PAGO", "SALDO / DATA"),
                listOf(179f, 101f, 101f, 79f, 90f, 91f, 133f), project.stages.map { stage ->
                    val paid = total.paid(CostKind.LABOR, stage.id)
                    listOf(listOf(stage.name, stage.notes).filter(String::isNotBlank).joinToString("\n"),
                        "${stage.plannedStart.dateOr()} a ${stage.plannedEnd.dateOr()}", "${stage.actualStart.dateOr()} a ${stage.actualEnd.dateOr()}",
                        "${stage.progress}%\n${stage.status}", stage.budget.costText(), paid.brl(),
                        "${stage.budget?.minus(paid).costText()}\n${stage.dueDate.dateOr()}")
                }, "Nenhuma etapa cadastrada.")
            val undefined = project.stages.count { it.budget == null }
            if (undefined > 0) paragraph("$undefined etapa(s) com valor a definir. Confirme se os serviços estão cobertos pelo contrato informado.", gold)
        }

        fun purchases() {
            section("MATERIAIS  |  COMPRAS E VARIAÇÕES", "Fornecedores, compras pagas e acréscimos declarados")
            tiles(
                Tile("MATERIAIS PAGOS", total.materialPaid.brl(), "Pagamentos realizados", green),
                Tile("TOTAL CONHECIDO DE MATERIAIS", total.materialForecast.brl(), "Documentos, cotações e estimativas"),
                Tile("ACRÉSCIMOS DECLARADOS", total.extras.brl(), "Integram a previsão parcial", gold),
            )
            barSection("COMPRAS PAGAS POR FORNECEDOR", total.spendBySupplier.mapIndexed { index, (supplier, amount) ->
                Bar(supplier, amount, listOf(green, blue, gold)[index % 3])
            }, "Nenhuma compra paga foi registrada.")
            heading("RESUMO DAS COMPRAS")
            table(listOf("COMPRA / FORNECEDOR", "DOCUMENTO", "TOTAL PREVISTO", "PAGO", "SITUAÇÃO", "CLASSIFICAÇÃO"),
                listOf(220f, 100f, 110f, 100f, 123f, 121f), project.purchases.map { order ->
                    listOf("${order.name}\n${order.supplier.textOr("Fornecedor a definir")}", order.document.textOr(), order.forecast.costText(),
                        total.paid(CostKind.MATERIAL, order.id).brl(), total.purchaseStatus(order),
                        if (order.extra) "Acréscimo declarado" else "Escopo previsto")
                }, "Nenhuma compra cadastrada.")
        }

        fun projections() {
            section("PROJEÇÃO FINANCEIRA  |  COMPRAS E DESEMBOLSOS", "Valores a pagar conhecidos, compras a cotar e datas de pagamento previstas")
            tiles(
                Tile("COMPRAS A PAGAR", total.materialToPay.brl(), "Valores conhecidos", blue),
                Tile("MÃO DE OBRA A PAGAR", total.laborToPay.brl(), "Saldo do contrato conhecido", blue),
                Tile("SALDO DA PREVISÃO", total.remaining.brl(), "Inclui eventual pagamento a maior"),
                Tile("MATERIAIS A COTAR", project.purchases.count { it.forecast == null }.toString(), "Sem valor definido", gold),
            )
            barSection("MATERIAIS A ADQUIRIR OU PAGAR", total.projectedPurchases.mapNotNull { purchase ->
                total.materialOutstanding(purchase)?.takeIf { it > 0 }?.let { Bar(purchase.name, it, blue) }
            }, "Nenhuma compra com saldo conhecido.")
            heading("CALENDÁRIO DE DESEMBOLSOS")
            table(listOf("REFERÊNCIA", "TIPO", "DATA PREVISTA", "VALOR A PAGAR"),
                listOf(355f, 137f, 136f, 146f), total.disbursements.map {
                    listOf(it.name, it.kind.label, it.date.dateOr(), it.amount.costText())
                }, "Nenhum desembolso previsto foi cadastrado.")
            callout("CRITÉRIO DE CONTROLE", "Compras previstas permanecem no escopo da obra. Acréscimos são sinalizados apenas nas compras marcadas como acréscimo. Datas e valores em aberto aguardam confirmação.")
        }

        fun materials() {
            section("MATERIAIS  |  LISTA DETALHADA", "Descrição, quantidades, valores unitários, descontos e conciliação documental")
            if (project.purchases.isEmpty()) paragraph("Nenhuma compra cadastrada.")
            project.purchases.forEach { order ->
                ensure(100f)
                val stageName = project.stages.firstOrNull { it.id == order.stageId }?.name
                heading(order.name)
                paragraph(listOf(order.supplier.takeIf(String::isNotBlank), order.document.takeIf(String::isNotBlank),
                    order.documentDate.dateText().takeIf(String::isNotBlank), stageName, total.purchaseStatus(order),
                    if (order.extra) "Acréscimo declarado" else null).filterNotNull().joinToString("  •  "))
                table(listOf("DESCRIÇÃO", "QUANTIDADE", "UNIDADE", "PREÇO UNITÁRIO", "SUBTOTAL"),
                    listOf(374f, 87f, 78f, 113f, 122f), order.lines.map { item ->
                        listOf(item.name, item.quantity.quantityInput().textOr(UNDEFINED), item.unit.textOr(),
                            item.unitPrice.costText(), item.subtotal.costText())
                    }, "Sem discriminação cadastrada para esta compra.")
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
                y += 7f
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
                    listOf("${project.targetName(it)}\n${it.kind.label}", it.amount.brl(), it.date.dateOr(UNREPORTED),
                        it.payer.textOr(), it.method.textOr(), listOf(it.receipt.textOr(), it.notes).filter(String::isNotBlank).joinToString("\n"))
                }, "Nenhum pagamento registrado.")
            if (total.payers.isNotEmpty()) {
                heading("CONSOLIDADO POR PAGADOR")
                table(listOf("PAGADOR", "TIPO", "MEIO", "TOTAL PAGO"), listOf(242f, 167f, 191f, 174f),
                    total.payers.map { listOf(it.payer, it.kind.label, it.method, it.amount.brl()) }, "")
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
    }
}
