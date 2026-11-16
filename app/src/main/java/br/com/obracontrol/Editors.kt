package br.com.obracontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun EditorScreen(
    route: String,
    project: Project,
    total: Balance,
    save: (Project) -> Unit,
    navigate: (String) -> Unit,
    back: () -> Unit,
    openLine: (String, String) -> Unit,
) {
    val parts = route.split('/')
    val id = parts.getOrNull(1)
    key(route) {
        when (parts.first()) {
            "obra" -> ProjectEditor(project) { save(it); back() }
            "etapa" -> {
                val existing = project.stages.firstOrNull { it.id == id }
                val stage = existing ?: remember { Stage() }
                val onDelete = if (existing != null) ({
                    require(project.payments.none { it.kind == CostKind.LABOR && it.targetId == stage.id }) {
                        "Exclua ou transfira os pagamentos vinculados antes de remover esta etapa."
                    }
                    save(project.copy(stages = project.stages.filterNot { it.id == stage.id },
                        purchases = project.purchases.map { if (it.stageId == stage.id) it.copy(stageId = null) else it },
                        diaryEntries = project.diaryEntries.map { if (it.stageId == stage.id) it.copy(stageId = null) else it }))
                    back()
                }) else null
                StageEditor(stage, onDelete) { next ->
                    save(project.copy(stages = project.stages.upsert(next, Stage::id)))
                    back()
                }
            }
            "compra" -> {
                val existing = project.purchases.firstOrNull { it.id == id }
                val order = existing ?: remember { Purchase() }
                val onDelete = if (existing != null) ({
                    require(project.payments.none { it.kind == CostKind.MATERIAL && it.targetId == order.id }) {
                        "Exclua ou transfira os pagamentos vinculados antes de remover esta compra."
                    }
                    save(project.copy(purchases = project.purchases.filterNot { it.id == order.id }))
                    back()
                }) else null
                PurchaseEditor(order, project.stages, existing != null, onDelete,
                    { next, line ->
                        save(project.copy(purchases = project.purchases.upsert(next, Purchase::id)))
                        if (line != null) openLine(next.id, line) else back()
                    },
                )
            }
            "linha" -> {
                val order = project.purchases.firstOrNull { it.id == id }
                if (order == null) {
                    MissingRecord(back)
                } else {
                    val existing = order.lines.firstOrNull { it.id == parts.getOrNull(2) }
                    val line = existing ?: remember { PurchaseLine() }
                    val update: (List<PurchaseLine>) -> Unit = { lines ->
                        save(project.copy(purchases = project.purchases.upsert(order.copy(lines = lines), Purchase::id)))
                        back()
                    }
                    LineEditor(line, if (existing != null) ({ update(order.lines.filterNot { it.id == line.id }) }) else null) {
                        update(order.lines.upsert(it, PurchaseLine::id))
                    }
                }
            }
            "pagamento" -> {
                val existing = project.payments.firstOrNull { it.id == id }
                val requestedKind = parts.getOrNull(2)?.let(CostKind::valueOf)
                val defaultKind = requestedKind ?: if (project.stages.isEmpty()) CostKind.MATERIAL else CostKind.LABOR
                val payment = existing ?: remember {
                    Payment(kind = defaultKind, targetId = parts.getOrNull(3).orEmpty(), date = java.time.LocalDate.now())
                }
                PaymentEditor(project, payment, if (existing != null) ({
                    save(project.copy(payments = project.payments.filterNot { it.id == payment.id }))
                    back()
                }) else null) {
                    save(project.copy(payments = project.payments.upsert(it, Payment::id)))
                    back()
                }
            }
            "diario" -> DiaryScreen(project, { navigate("registro/novo") }) { navigate("registro/$it") }
            "registro" -> {
                val existing = project.diaryEntries.firstOrNull { it.id == id }
                val entry = existing ?: remember { DiaryEntry() }
                DiaryEditor(entry, project.stages, if (existing != null) ({
                    save(project.copy(diaryEntries = project.diaryEntries.filterNot { it.id == entry.id }))
                    back()
                }) else null) {
                    save(project.copy(diaryEntries = project.diaryEntries.upsert(it, DiaryEntry::id)))
                    back()
                }
            }
            "pendencias" -> IssuesScreen(project, total, { navigate("pendencia/nova") }) { navigate("pendencia/$it") }
            "pendencia" -> {
                val existing = project.issues.firstOrNull { it.id == id }
                val issue = existing ?: remember { Issue() }
                IssueEditor(issue, if (existing != null) ({
                    save(project.copy(issues = project.issues.filterNot { it.id == issue.id }))
                    back()
                }) else null) {
                    save(project.copy(issues = project.issues.upsert(it, Issue::id)))
                    back()
                }
            }
        }
    }
}

private fun <T> List<T>.upsert(value: T, id: (T) -> String): List<T> =
    if (any { id(it) == id(value) }) map { if (id(it) == id(value)) value else it } else this + value

@Composable
private fun MissingRecord(back: () -> Unit) {
    ObraList { item { SheetCard { Detail("Registro não encontrado."); TextButton(onClick = back) { Text("Voltar") } } } }
}

@Composable
private fun ProjectEditor(project: Project, save: (Project) -> Unit) {
    var name by rememberSaveable { mutableStateOf(project.name) }
    var start by rememberSaveable { mutableStateOf(project.start.dateText()) }
    var reference by rememberSaveable { mutableStateOf(project.referenceDate.dateText()) }
    var budget by rememberSaveable { mutableStateOf(project.laborBudget.moneyInput()) }
    var notes by rememberSaveable { mutableStateOf(project.notes) }
    Form(save = {
        save(project.copy(name = name.trim(), start = parseDate(start), referenceDate = parseDate(reference),
            laborBudget = parseMoney(budget), notes = notes.trim()))
    }) {
        Field("Nome da obra", name, { name = it })
        CalendarField("Início da obra", start, { start = it })
        CalendarField("Data-base do relatório", reference, { reference = it })
        Field("Contrato de mão de obra (R$)", budget, { budget = it }, KeyboardType.Decimal)
        Detail("Quando o valor do contrato está em branco, a previsão usa os valores conhecidos das etapas.")
        Field("Observações da obra", notes, { notes = it }, multiline = true)
    }
}

@Composable
private fun StageEditor(stage: Stage, remove: (() -> Unit)?, save: (Stage) -> Unit) {
    var name by rememberSaveable { mutableStateOf(stage.name) }
    var budget by rememberSaveable { mutableStateOf(stage.budget.moneyInput()) }
    var plannedStart by rememberSaveable { mutableStateOf(stage.plannedStart.dateText()) }
    var plannedEnd by rememberSaveable { mutableStateOf(stage.plannedEnd.dateText()) }
    var actualStart by rememberSaveable { mutableStateOf(stage.actualStart.dateText()) }
    var actualEnd by rememberSaveable { mutableStateOf(stage.actualEnd.dateText()) }
    var dueDate by rememberSaveable { mutableStateOf(stage.dueDate.dateText()) }
    var completion by rememberSaveable { mutableStateOf(stage.progress.toString()) }
    var notes by rememberSaveable { mutableStateOf(stage.notes) }
    Form(remove = remove, deletePrompt = "Excluir esta etapa? Compras e registros do diário ficarão sem o vínculo com ela.", save = {
        val progress = completion.toIntOrNull() ?: throw IllegalArgumentException("Informe o avanço em número inteiro.")
        save(stage.copy(name = name.trim(), budget = parseMoney(budget), plannedStart = parseDate(plannedStart),
            plannedEnd = parseDate(plannedEnd), actualStart = parseDate(actualStart), actualEnd = parseDate(actualEnd),
            dueDate = parseDate(dueDate), progress = progress, notes = notes.trim()))
    }) {
        Field("Nome da etapa", name, { name = it })
        Field("Avanço físico (%)", completion, { completion = it }, KeyboardType.Number)
        Field("Valor da etapa (R$)", budget, { budget = it }, KeyboardType.Decimal)
        CalendarField("Início previsto", plannedStart, { plannedStart = it })
        CalendarField("Fim previsto", plannedEnd, { plannedEnd = it })
        CalendarField("Início real", actualStart, { actualStart = it })
        CalendarField("Fim real", actualEnd, { actualEnd = it })
        CalendarField("Pagamento previsto", dueDate, { dueDate = it })
        Field("Observações da etapa", notes, { notes = it }, multiline = true)
    }
}

@Composable
private fun PurchaseEditor(
    order: Purchase,
    stages: List<Stage>,
    exists: Boolean,
    remove: (() -> Unit)?,
    save: (Purchase, String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(order.name) }
    var supplier by rememberSaveable { mutableStateOf(order.supplier) }
    var document by rememberSaveable { mutableStateOf(order.document) }
    var documentDate by rememberSaveable { mutableStateOf(order.documentDate.dateText()) }
    var dueDate by rememberSaveable { mutableStateOf(order.dueDate.dateText()) }
    var delivery by rememberSaveable { mutableStateOf(order.deliveryExpected.dateText()) }
    var received by rememberSaveable { mutableStateOf(order.receivedDate.dateText()) }
    var stageId by rememberSaveable { mutableStateOf(order.stageId.orEmpty()) }
    var total by rememberSaveable { mutableStateOf(order.total.moneyInput()) }
    var discount by rememberSaveable { mutableStateOf(order.discount.takeUnless { it == 0L }.moneyInput()) }
    var extra by rememberSaveable { mutableStateOf(order.extra) }
    var notes by rememberSaveable { mutableStateOf(order.notes) }
    var documents by rememberAttachments(order.documents)
    var processing by remember { mutableStateOf(false) }
    fun readFields() = order.copy(name = name.trim(), supplier = supplier.trim(), document = document.trim(),
        documentDate = parseDate(documentDate), dueDate = parseDate(dueDate),
        deliveryExpected = parseDate(delivery), receivedDate = parseDate(received),
        stageId = stageId.takeIf(String::isNotBlank), total = parseMoney(total),
        discount = parseMoney(discount) ?: 0, extra = extra, notes = notes.trim(), documents = documents)

    Form(remove = remove, deletePrompt = "Excluir esta compra e os seus itens?", available = !processing, save = { save(readFields(), null) }) {
        Field("Nome da compra ou material", name, { name = it })
        DocumentEditor(DocumentUse.PURCHASE, documents, { documents = it }, { processing = it }) { amount, field ->
            if (field == MoneyField.DISCOUNT) discount = amount.cents.moneyInput()
            else total = amount.cents.moneyInput()
        }
        HorizontalDivider()
        Field("Fornecedor", supplier, { supplier = it })
        Field("Número ou referência do documento", document, { document = it })
        CalendarField("Data do documento", documentDate, { documentDate = it })
        CalendarField("Pagamento previsto", dueDate, { dueDate = it })
        CalendarField("Entrega prevista", delivery, { delivery = it })
        CalendarField("Recebido em", received, { received = it })
        if (stages.isNotEmpty()) {
            Select("Etapa vinculada", stageId, listOf("" to "Sem vínculo") + stages.map { it.id to it.name }) { stageId = it }
        }
        Field("Total do documento ou da cotação (R$)", total, { total = it }, KeyboardType.Decimal)
        Field("Desconto total (R$)", discount, { discount = it }, KeyboardType.Decimal)
        Detail("O valor do documento tem prioridade. Se ficar em branco, o app calcula o total quando todos os itens tiverem subtotal.")
        Check("Acréscimo declarado", extra) { extra = it }
        Field("Observações da compra", notes, { notes = it }, multiline = true)
        HorizontalDivider()
        Heading("Itens da compra")
        order.lineSum?.let { LedgerLine("Soma dos itens", it.brl()) }
        order.variance?.takeIf { it != 0L }?.let { Detail("Diferença de ${it.brl()} entre o documento e os itens após desconto.", Amber) }
        if (order.lines.isEmpty()) Detail("Nenhum item discriminado.")
        order.lines.forEach { line ->
            key(line.id) {
                LedgerLine(line.name, line.subtotal.costText())
                Detail(listOf(line.quantity.quantityInput(), line.unit, line.unitPrice?.brl().orEmpty()).filter(String::isNotBlank).joinToString(" · "))
                TextButton(enabled = !processing, onClick = { submit { save(readFields(), line.id) } }) { Text("Editar item") }
                HorizontalDivider()
            }
        }
        OutlinedButton(enabled = !processing, onClick = { submit { save(readFields(), "nova") } }) {
            Text(if (exists) "Adicionar item" else "Salvar e adicionar item")
        }
    }
}

@Composable
private fun LineEditor(line: PurchaseLine, remove: (() -> Unit)?, save: (PurchaseLine) -> Unit) {
    var name by rememberSaveable { mutableStateOf(line.name) }
    var count by rememberSaveable { mutableStateOf(line.quantity.quantityInput()) }
    var unit by rememberSaveable { mutableStateOf(line.unit) }
    var price by rememberSaveable { mutableStateOf(line.unitPrice.moneyInput()) }
    var subtotal by rememberSaveable { mutableStateOf(line.documentSubtotal.moneyInput()) }
    Form(remove = remove, deletePrompt = "Excluir este item?", save = {
        save(line.copy(name = name.trim(), quantity = parseQuantity(count), unit = unit.trim(),
            unitPrice = parseMoney(price), documentSubtotal = parseMoney(subtotal)))
    }) {
        Field("Descrição do item", name, { name = it })
        Field("Quantidade", count, { count = it }, KeyboardType.Decimal)
        Field("Unidade (un, m², kg etc.)", unit, { unit = it })
        Field("Preço unitário (R$)", price, { price = it }, KeyboardType.Decimal)
        Field("Subtotal informado no documento (R$)", subtotal, { subtotal = it }, KeyboardType.Decimal)
        Detail("Deixe o subtotal em branco para calcular pela quantidade e preço unitário.")
    }
}

@Composable
private fun PaymentEditor(project: Project, payment: Payment, remove: (() -> Unit)?, save: (Payment) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(payment.kind.name) }
    var target by rememberSaveable { mutableStateOf(payment.targetId) }
    var amount by rememberSaveable { mutableStateOf(payment.amount.takeUnless { it == 0L }.moneyInput()) }
    var date by rememberSaveable { mutableStateOf(payment.date.dateText()) }
    var payer by rememberSaveable { mutableStateOf(payment.payer) }
    var method by rememberSaveable { mutableStateOf(payment.method) }
    var receipt by rememberSaveable { mutableStateOf(payment.receipt) }
    var notes by rememberSaveable { mutableStateOf(payment.notes) }
    var proofs by rememberAttachments(payment.proofs)
    var processing by remember { mutableStateOf(false) }
    val targets = project.targets(CostKind.valueOf(kind))
    Form(remove = remove, deletePrompt = "Excluir este pagamento? Os totais do painel serão recalculados.", available = !processing, save = {
        save(payment.copy(kind = CostKind.valueOf(kind), targetId = target, amount = parseMoney(amount) ?: 0,
            date = parseDate(date), payer = payer.trim(), method = method.trim(), receipt = receipt.trim(), notes = notes.trim(), proofs = proofs))
    }) {
        Select("Tipo", kind, CostKind.entries.map { it.name to it.label }) {
            if (kind != it) { kind = it; target = "" }
        }
        if (targets.isEmpty()) Detail("Cadastre uma etapa ou compra desse tipo antes de lançar um pagamento.")
        else Select(if (kind == CostKind.LABOR.name) "Etapa" else "Compra", target, targets) { target = it }
        Field("Valor pago (R$)", amount, { amount = it }, KeyboardType.Decimal)
        CalendarField("Data do pagamento", date, { date = it })
        Field("Quem pagou", payer, { payer = it })
        Field("Meio de pagamento", method, { method = it })
        Field("Referência do comprovante", receipt, { receipt = it })
        DocumentEditor(DocumentUse.PAYMENT, proofs, { proofs = it }, { processing = it }) { recognized, _ ->
            amount = recognized.cents.moneyInput()
        }
        Field("Observações do pagamento", notes, { notes = it }, multiline = true)
    }
}

@Composable
private fun DiaryEditor(entry: DiaryEntry, stages: List<Stage>, remove: (() -> Unit)?, save: (DiaryEntry) -> Unit) {
    var date by rememberSaveable { mutableStateOf(entry.date.dateText()) }
    var title by rememberSaveable { mutableStateOf(entry.title) }
    var stageId by rememberSaveable { mutableStateOf(entry.stageId.orEmpty()) }
    var activities by rememberSaveable { mutableStateOf(entry.activities) }
    var occurrences by rememberSaveable { mutableStateOf(entry.occurrences) }
    var attachments by rememberAttachments(entry.attachments)
    var processing by remember { mutableStateOf(false) }
    Form(remove = remove, deletePrompt = "Excluir este registro do diário?", available = !processing, save = {
        val day = requireNotNull(parseDate(date)) { "Informe a data do registro." }
        save(entry.copy(date = day, title = title.trim(), stageId = stageId.takeIf(String::isNotBlank),
            activities = activities.trim(), occurrences = occurrences.trim(), attachments = attachments))
    }) {
        CalendarField("Data do registro", date, { date = it }, optional = false)
        Field("Título", title, { title = it })
        if (stages.isNotEmpty()) {
            Select("Etapa vinculada", stageId, listOf("" to "Sem vínculo") + stages.map { it.id to it.name }) { stageId = it }
        }
        Field("Atividades realizadas", activities, { activities = it }, multiline = true)
        Field("Ocorrências e impedimentos", occurrences, { occurrences = it }, multiline = true)
        DocumentEditor(DocumentUse.DIARY, attachments, { attachments = it }, { processing = it })
    }
}

@Composable
private fun IssuesScreen(project: Project, total: Balance, add: () -> Unit, edit: (String) -> Unit) {
    val alerts = total.alerts()
    ObraList {
        item { ListHeading("Pendências da obra", "Nova pendência", add) }
        if (project.issues.isEmpty() && alerts.isEmpty()) item { SheetCard { Detail("Nenhuma pendência identificada.") } }
        items(project.issues, key = { it.id }) { issue ->
            SheetCard {
                Heading(issue.title)
                Tag(if (issue.closed) "Resolvida" else "Em aberto", !issue.closed)
                if (issue.action.isNotBlank()) Detail(issue.action)
                TextButton(onClick = { edit(issue.id) }) { Text("Editar pendência") }
            }
        }
        if (alerts.isNotEmpty()) item { Heading("Conferência dos registros") }
        items(alerts) { (title, action) -> SheetCard { Heading(title); Detail(action) } }
    }
}

@Composable
private fun IssueEditor(issue: Issue, remove: (() -> Unit)?, save: (Issue) -> Unit) {
    var title by rememberSaveable { mutableStateOf(issue.title) }
    var action by rememberSaveable { mutableStateOf(issue.action) }
    var closed by rememberSaveable { mutableStateOf(issue.closed) }
    Form(remove = remove, deletePrompt = "Excluir esta pendência?", save = {
        save(issue.copy(title = title.trim(), action = action.trim(), closed = closed))
    }) {
        Field("Título da pendência", title, { title = it })
        Field("Próxima ação", action, { action = it }, multiline = true)
        Check("Resolvida", closed) { closed = it }
    }
}

private class FormActions(val submit: (() -> Unit) -> Unit)

@Composable
private fun Form(
    remove: (() -> Unit)? = null,
    deletePrompt: String = "",
    available: Boolean = true,
    save: () -> Unit,
    body: @Composable FormActions.() -> Unit,
) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val actions = FormActions { operation ->
        error = try {
            operation()
            null
        } catch (failure: IllegalArgumentException) {
            failure.message ?: "Confira os dados preenchidos."
        } catch (_: Exception) {
            "Não foi possível salvar. Verifique o espaço disponível e tente novamente."
        }
    }
    if (deleting && remove != null) {
        AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Confirmar exclusão") },
            text = { Text(deletePrompt) },
            confirmButton = { TextButton(onClick = { deleting = false; actions.submit(remove) }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } })
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) { actions.body() }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (remove != null) {
                        OutlinedButton(enabled = available, onClick = { deleting = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Excluir") }
                    }
                    Button(enabled = available, onClick = { actions.submit(save) }) { Text("Salvar") }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    change: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(), singleLine = !multiline, minLines = if (multiline) 2 else 1,
    )
}

@Composable
private fun Check(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, change)
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Select(label: String, selected: String, options: List<Pair<String, String>>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = options.firstOrNull { it.first == selected }?.second.orEmpty(), onValueChange = {},
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, singleLine = true)
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { change(id); expanded = false })
            }
        }
    }
}
