package br.com.obracontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun PlanningRouter(route: String, project: Project, save: (Project) -> Unit, navigate: (String) -> Unit, back: () -> Unit) {
    val path = route.split('/')
    val plan = project.workPlans.firstOrNull { it.id == path.getOrNull(2) }
    val stage = plan?.stages?.firstOrNull { it.id == path.getOrNull(3) }
    when (path.getOrNull(1)) {
        null -> WorkPlanList(project, navigate)
        "novo" -> NewWorkPlanEditor { save(project.addWorkPlan(it)); back() }
        "importar" -> AiPlanImportScreen(project, save, back)
        "plano" -> if (plan == null) PlanningMissing(back) else WorkPlanScreen(project, plan, navigate)
        "etapa" -> if (plan == null || stage == null) PlanningMissing(back) else PlanStageScreen(project, plan, stage, navigate)
        "ia" -> if (plan == null) PlanningMissing(back) else PlanningAiScreen(plan, navigate)
        "editar-etapa" -> if (plan == null) PlanningMissing(back) else PlanStageEditor(stage, if (stage != null && stage.approvals.isEmpty()) ({
            require(plan.stages.size > 1) { "Mantenha ao menos uma etapa no planejamento." }
            save(project.copy(workPlans = project.workPlans.map { if (it.id == plan.id) it.copy(stages = it.stages.filterNot { item -> item.id == stage.id }) else it })); back()
        }) else null) { updated ->
            save(if (stage == null) project.copy(workPlans = project.workPlans.map { if (it.id == plan.id) it.copy(stages = it.stages + updated) else it })
                else project.editPlanStage(plan.id, stage.id) { updated }); back()
        }
        "servico" -> if (plan == null || stage == null) PlanningMissing(back) else {
            val service = stage.services.firstOrNull { it.id == path.getOrNull(4) }
            PlanServiceEditor(service, if (service != null) ({
                save(project.editPlanStage(plan.id, stage.id) { it.copy(services = it.services.filterNot { value -> value.id == service.id }) }); back()
            }) else null) { updated ->
                save(project.editPlanStage(plan.id, stage.id) { it.copy(services = it.services.upsert(updated, PlanService::id)) }); back()
            }
        }
        "insumo" -> {
            val service = stage?.services?.firstOrNull { it.id == path.getOrNull(4) }
            if (plan == null || stage == null || service == null) PlanningMissing(back) else {
                val input = service.items.firstOrNull { it.id == path.getOrNull(5) }
                fun update(values: List<PlanInput>) {
                    save(project.editPlanStage(plan.id, stage.id) { value -> value.copy(services = value.services.map {
                        if (it.id == service.id) it.copy(items = values) else it
                    }) }); back()
                }
                PlanInputEditor(input, if (input != null) ({ update(service.items.filterNot { it.id == input.id }) }) else null) {
                    update(service.items.upsert(it, PlanInput::id))
                }
            }
        }
        "aprovar" -> if (plan == null || stage == null) PlanningMissing(back) else PlanApprovalEditor(stage) { approver, date, notes ->
            save(project.approvePlanStage(plan.id, stage.id, approver, date, notes)); back()
        }
        "ativar" -> if (plan == null || stage == null) PlanningMissing(back) else PlanActivationEditor(project, stage) { target, add ->
            save(project.activatePlanStage(plan.id, stage.id, target, add)); back()
        }
        else -> PlanningMissing(back)
    }
}

@Composable
private fun PlanningMissing(back: () -> Unit) {
    SheetCard { Heading("Planejamento não encontrado"); TextButton(onClick = back) { Text("Voltar") } }
}

@Composable
internal fun PlanningDashboardCard(project: Project, open: () -> Unit) {
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(AppSymbol.LAYERS)
            Column(Modifier.weight(1f)) {
                Heading("Planejar e orçar")
                Detail("Etapas, serviços, materiais e mão de obra")
            }
        }
        if (project.workPlans.isNotEmpty()) {
            val stages = project.workPlans.flatMap { it.stages }
            Detail("${project.workPlans.size} planejamento(s) · ${stages.count { it.approved }} de ${stages.size} etapas aprovadas")
        }
        Button(onClick = open, modifier = Modifier.fillMaxWidth().testTag("open-planning")) { Text("Abrir planejamento") }
    }
}

@Composable
private fun WorkPlanList(project: Project, navigate: (String) -> Unit) {
    ObraList {
        item { ListHeading("Planejamento de custos", "+ Novo", { navigate("planejamento/novo") }, "Do escopo ao orçamento aprovado e à execução") }
        item { SheetCard {
            Detail("Comece por um modelo de reforma, revestimento ou casa nova. Revise os serviços e insumos, preencha as cotações e aprove cada etapa.")
            OutlinedButton(onClick = { navigate("planejamento/importar") }, modifier = Modifier.fillMaxWidth()) { Text("Importar proposta da IA") }
        } }
        items(project.workPlans, key = { it.id }) { plan ->
            SheetCard(Modifier.testTag("work-plan-${plan.id}").clickable { navigate("planejamento/plano/${plan.id}") }) {
                Heading(plan.title, plan.type.label + plan.location.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty())
                LedgerLine("Estimativa conhecida", plan.knownTotal.brl())
                LedgerLine("Último orçamento aprovado", plan.approvedTotal.brl())
                Detail("${plan.stages.size} etapas · ${plan.stages.count { it.approved }} aprovadas · ${plan.missing} pendências de orçamento")
                Text("Abrir etapas ›", color = Green, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NewWorkPlanEditor(save: (WorkPlan) -> Unit) {
    var type by rememberSaveable { mutableStateOf(WorkPlanType.RENOVATION.name) }
    var title by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("") }
    var loss by rememberSaveable { mutableStateOf("10") }
    Form(save = { save(createWorkPlan(WorkPlanType.valueOf(type), title, scope, location, parseQuantity(area),
        requireNotNull(parseQuantity(loss)) { "Informe a perda estimada." })) }) {
        Heading("Novo planejamento", "O modelo cria um rascunho editável. Compras e pagamentos só entram no controle após aprovação e ativação.")
        Select("Tipo de obra", type, WorkPlanType.entries.map { it.name to it.label }) { type = it }
        Field("Nome do planejamento", title, { title = it })
        Field("O que deseja executar?", scope, { scope = it }, multiline = true)
        Field("Cidade / UF", location, { location = it })
        Field("Área de referência (m², opcional)", area, { area = it }, KeyboardType.Decimal)
        Field("Perda estimada do revestimento (%)", loss, { loss = it }, KeyboardType.Decimal)
        Detail("A área só gera quantidades onde o modelo explicita o cálculo. Consumos de argamassa, rejunte, estrutura e outros itens serão definidos após medição / especificação.")
    }
}

@Composable
private fun WorkPlanScreen(project: Project, plan: WorkPlan, navigate: (String) -> Unit) {
    ObraList {
        item { Heading(plan.title, listOf(plan.type.label, plan.location).filter(String::isNotBlank).joinToString(" · ")) }
        item { SheetCard {
            if (plan.scope.isNotBlank()) Detail(plan.scope)
            LedgerLine("Estimativa conhecida", plan.knownTotal.brl())
            LedgerLine("Último orçamento aprovado", plan.approvedTotal.brl())
            Detail("${plan.missing} pendências de orçamento. O aprovado soma a última aprovação de cada etapa; revisões ficam pendentes até nova aprovação.")
            Button(onClick = { navigate("planejamento/ia/${plan.id}") }, modifier = Modifier.fillMaxWidth()) { Text("Planejar com ChatGPT") }
        } }
        items(plan.stages, key = { it.id }) { stage ->
            val control = project.planControl(stage)
            SheetCard(Modifier.testTag("plan-stage-${stage.id}").clickable { navigate("planejamento/etapa/${plan.id}/${stage.id}") }) {
                Heading("${plan.stages.indexOf(stage) + 1}. ${stage.name}")
                Tag(stage.status, warning = !stage.approved)
                LedgerLine("Estimativa conhecida", stage.knownTotal.brl())
                LedgerLine("Aprovado", stage.latestApproval?.total.costText())
                if (control != null) {
                    LedgerLine(if (control.undefined > 0) "Previsão atual parcial" else "Previsão atual", control.forecast.brl())
                    LedgerLine("Pago efetivo", control.paid.brl())
                    Detail("Execução física: ${control.progress}%")
                }
                Detail("${stage.services.size} serviço(s) · ${stage.items.size} insumo(s) · Abrir camadas ›")
            }
        }
        item { OutlinedButton(onClick = { navigate("planejamento/editar-etapa/${plan.id}/nova") }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar etapa") } }
        item { SheetCard {
            Heading("Premissas e informações a confirmar")
            plan.assumptions.forEach { Detail("• $it") }
            plan.questions.forEach { Detail("• $it", Amber) }
        } }
    }
}

@Composable
private fun PlanStageScreen(project: Project, plan: WorkPlan, stage: PlanStage, navigate: (String) -> Unit) {
    val control = project.planControl(stage)
    ObraList {
        item { Heading(stage.name, plan.title) }
        item { SheetCard(Modifier.testTag("planning-stage-summary")) {
            Tag(stage.status, warning = !stage.approved)
            if (stage.notes.isNotBlank()) Detail(stage.notes)
            LedgerLine("Estimativa conhecida", stage.knownTotal.brl())
            LedgerLine("Último orçamento aprovado", stage.latestApproval?.total.costText())
            if (control != null) {
                LedgerLine(if (control.undefined > 0) "Previsão atual parcial" else "Previsão atual", control.forecast.brl())
                LedgerLine("Pago efetivo", control.paid.brl())
                stage.latestApproval?.let { approved ->
                    LedgerLine(if (control.undefined > 0) "Desvio parcial da previsão" else "Desvio da previsão", (control.forecast - approved.total).brl())
                }
                Detail("Avanço físico: ${control.progress}%. O pagamento e o avanço da execução são controlados separadamente.")
                OutlinedButton(onClick = { navigate("etapa/${stage.linkedStageId}") }, modifier = Modifier.fillMaxWidth()) { Text("Cronograma e execução") }
                OutlinedButton(onClick = { navigate("custo/LABOR/${stage.linkedStageId}") }, modifier = Modifier.fillMaxWidth()) { Text("Pagamentos da mão de obra") }
            }
            TextButton(onClick = { navigate("planejamento/editar-etapa/${plan.id}/${stage.id}") }) { Text("Editar etapa") }
            if (!stage.approved) Button(onClick = { navigate("planejamento/aprovar/${plan.id}/${stage.id}") },
                modifier = Modifier.fillMaxWidth().testTag("approve-plan-stage")) { Text("Revisar e aprovar orçamento") }
            if (stage.linkedStageId == null && stage.approved) Button(onClick = { navigate("planejamento/ativar/${plan.id}/${stage.id}") },
                modifier = Modifier.fillMaxWidth().testTag("activate-plan-stage")) { Text("Ativar no controle da obra") }
            if (stage.linkedStageId != null && !stage.approved) Detail("A revisão do rascunho não altera compras, pagamentos ou previsões já cadastradas. Após revisar o orçamento, ajuste os custos vinculados conforme a contratação.", Amber)
        } }
        items(stage.services, key = { it.id }) { service ->
            var expanded by rememberSaveable(service.id) { mutableStateOf(false) }
            SheetCard(Modifier.testTag("plan-service-${service.id}")) {
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.testTag("plan-service-toggle-${service.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Heading(service.name)
                        Detail("${service.items.size} insumo(s) · ${service.items.sumOf { it.subtotal ?: 0 }.brl()} conhecidos")
                    }
                    Text(if (expanded) "−" else "+", color = Green, style = MaterialTheme.typography.titleLarge)
                }
                if (expanded) {
                    service.items.forEach { item ->
                        HorizontalDivider(color = Line)
                        Column(Modifier.fillMaxWidth().clickable { navigate("planejamento/insumo/${plan.id}/${stage.id}/${service.id}/${item.id}") }
                            .padding(vertical = 6.dp).testTag("plan-input-${item.id}"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(item.subtotal.costText(), color = if (item.subtotal == null) Amber else Navy, fontWeight = FontWeight.Bold)
                            }
                            Detail("${item.category.label} · ${item.quantity.quantityInput().ifBlank { "Qtd. a definir" }} ${item.unit} × ${item.unitPrice.costText()}")
                            if (item.priceSource.isNotBlank()) Detail("${item.priceSource} · ${item.priceDate.dateOr()}")
                            if (item.estimated && item.subtotal != null) Detail("Estimativa para revisão", Amber)
                            if (item.notes.isNotBlank()) Detail(item.notes)
                        }
                        item.purchaseId?.let { purchaseId ->
                            TextButton(onClick = { navigate("custo/MATERIAL/$purchaseId") }) { Text("Compra / serviço e pagamentos") }
                        }
                    }
                    HorizontalDivider(color = Line)
                    LedgerLine("Total conhecido do serviço", service.items.sumOf { it.subtotal ?: 0L }.brl())
                    TextButton(onClick = { navigate("planejamento/insumo/${plan.id}/${stage.id}/${service.id}/novo") }) { Text("Adicionar insumo") }
                    TextButton(onClick = { navigate("planejamento/servico/${plan.id}/${stage.id}/${service.id}") }) { Text("Editar serviço") }
                }
            }
        }
        item { OutlinedButton(onClick = { navigate("planejamento/servico/${plan.id}/${stage.id}/novo") }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar serviço") } }
        if (stage.approvals.isNotEmpty()) item { SheetCard {
            Heading("Histórico de aprovação")
            stage.approvals.asReversed().forEach { approval ->
                Detail("Rev. ${approval.revision} · ${approval.date.dateText()} · ${approval.approver} · ${approval.total.brl()}")
                if (approval.notes.isNotBlank()) Detail(approval.notes)
            }
        } }
    }
}

@Composable
private fun PlanStageEditor(existing: PlanStage?, remove: (() -> Unit)?, save: (PlanStage) -> Unit) {
    val id = rememberSaveable { existing?.id ?: newId() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    Form(remove = remove, deletePrompt = "Excluir esta etapa do rascunho de planejamento?", save = { require(name.isNotBlank()) { "Informe o nome da etapa." }; save((existing ?: PlanStage(id = id)).copy(name = name.trim(), notes = notes.trim())) }) {
        Field("Nome da etapa planejada", name, { name = it })
        Field("Escopo e condições", notes, { notes = it }, multiline = true)
        Detail("Editar uma etapa aprovada abre uma nova revisão e preserva o histórico de aprovação.")
    }
}

@Composable
private fun PlanServiceEditor(existing: PlanService?, remove: (() -> Unit)?, save: (PlanService) -> Unit) {
    val id = rememberSaveable { existing?.id ?: newId() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    Form(remove = remove, deletePrompt = "Excluir este serviço e seus insumos do rascunho? Custos e pagamentos já cadastrados serão preservados.", save = { require(name.isNotBlank()) { "Informe o serviço." }; save((existing ?: PlanService(id = id)).copy(name = name.trim())) }) {
        Heading("Serviço da etapa", "Cada serviço reúne os materiais, a mão de obra e os demais custos necessários.")
        Field("Descrição do serviço", name, { name = it })
    }
}

@Composable
private fun PlanInputEditor(existing: PlanInput?, remove: (() -> Unit)?, save: (PlanInput) -> Unit) {
    val id = rememberSaveable { existing?.id ?: newId() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(existing?.category?.name ?: CostCategory.MATERIAL.name) }
    var quantity by rememberSaveable { mutableStateOf(existing?.quantity.quantityInput()) }
    var unit by rememberSaveable { mutableStateOf(existing?.unit.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(existing?.unitPrice.moneyInput()) }
    var source by rememberSaveable { mutableStateOf(existing?.priceSource.orEmpty()) }
    var date by rememberSaveable { mutableStateOf((existing?.priceDate ?: LocalDate.now()).dateText()) }
    var estimated by rememberSaveable { mutableStateOf(existing?.estimated ?: true) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    Form(remove = remove, deletePrompt = "Remover este insumo do rascunho? Compras e pagamentos já cadastrados serão preservados.", save = {
        val item = (existing ?: PlanInput(id = id)).copy(name = name.trim(), category = CostCategory.valueOf(category), quantity = parseQuantity(quantity),
            unit = unit.trim(), unitPrice = parseMoney(price), priceSource = source.trim(), priceDate = parseDate(date), estimated = estimated, notes = notes.trim())
        item.validate(); save(item)
    }) {
        Heading("Insumo e preço de referência", "Deixe valores desconhecidos em branco. Para aprovar a etapa, preencha quantidade, unidade, preço, fonte e data.")
        Field("Descrição do insumo", name, { name = it })
        Select("Tipo de custo", category, CostCategory.entries.map { it.name to it.label }) { category = it }
        Field("Quantidade", quantity, { quantity = it }, KeyboardType.Decimal)
        Field("Unidade", unit, { unit = it })
        Field("Preço unitário (R$)", price, { price = it }, KeyboardType.Decimal)
        runCatching { PlanInput(quantity = parseQuantity(quantity), unitPrice = parseMoney(price)).subtotal }.getOrNull()?.let { LedgerLine("Subtotal", it.brl()) }
        Field("Fonte / fornecedor da cotação", source, { source = it })
        CalendarField("Data da referência de preço", date, { date = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(estimated, { estimated = it }); Text("Valor estimado") }
        Field("Condições, consumo e observações", notes, { notes = it }, multiline = true)
        existing?.purchaseId?.let { Detail("Este insumo tem uma compra vinculada. A revisão do orçamento preserva os valores já cadastrados nessa compra.") }
    }
}

@Composable
private fun PlanApprovalEditor(stage: PlanStage, save: (String, LocalDate, String) -> Unit) {
    var approver by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().dateText()) }
    var notes by rememberSaveable { mutableStateOf("") }
    Form(save = { save(approver, requireNotNull(parseDate(date)) { "Informe a data de aprovação." }, notes) }) {
        Heading("Aprovar orçamento da etapa", stage.name)
        LedgerLine("Revisão", stage.revision.toString())
        LedgerLine("Total conhecido", stage.knownTotal.brl())
        Detail("${stage.missing} insumo(s) com dados pendentes. A aprovação registra uma cópia dos insumos, preços, fontes e data.")
        CostCategory.entries.forEach { type -> LedgerLine(type.label, stage.items.filter { it.category == type }.sumOf { it.subtotal ?: 0 }.brl()) }
        if (stage.items.any { it.estimated && it.subtotal != null }) Detail("O orçamento contém valores estimados. Confira as premissas antes de aprovar.", Amber)
        Field("Responsável pela aprovação", approver, { approver = it })
        CalendarField("Data de aprovação", date, { date = it }, optional = false)
        Field("Condições da aprovação", notes, { notes = it }, multiline = true)
        Detail("A aprovação autoriza o orçamento no app. Ela não registra pagamento nem altera a execução física.")
    }
}

@Composable
private fun PlanActivationEditor(project: Project, stage: PlanStage, save: (String?, Boolean) -> Unit) {
    var target by rememberSaveable { mutableStateOf("") }
    var add by rememberSaveable { mutableStateOf(false) }
    val used = project.workPlans.flatMap { it.stages }.mapNotNull { it.linkedStageId }.toSet()
    Form(save = { save(target.takeIf(String::isNotBlank), add) }) {
        Heading("Ativar etapa aprovada", stage.name)
        Select("Como acompanhar a execução?", target, listOf("" to "Criar etapa e custos previstos") + project.stages.filter { it.id !in used }.map { it.id to it.name }) { target = it }
        if (target.isBlank()) {
            val labor = stage.items.filter { it.category == CostCategory.LABOR }.sumOf { it.subtotal ?: 0 }
            LedgerLine("Mão de obra da etapa", labor.brl())
            Detail("Serão criados ${stage.items.count { it.category != CostCategory.LABOR }} custos previstos em Compras, com seus tipos e quantidades. Depois, ajuste fornecedor, datas e condições de pagamento nos cadastros.")
            if (project.laborBudget != null) {
                LedgerLine("Orçamento geral de mão de obra atual", project.laborBudget.brl())
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(add, { add = it }); Text("Somar esta mão de obra ao orçamento geral") }
                Detail("Se a etapa já estiver incluída no contrato geral, mantenha esta opção desmarcada.")
            }
        } else Detail("A etapa existente será vinculada para comparar previsão e pagamentos com o orçamento aprovado. Nenhuma compra, valor ou pagamento será criado ou alterado.")
        Detail("O calendário será preenchido conforme os vencimentos cadastrados. A ativação não cria pagamentos realizados.")
    }
}

@Composable
private fun PlanningAiScreen(plan: WorkPlan, navigate: (String) -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    fun copyPrompt() {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Planejamento ObraControl", WorkPlanCodec.aiPrompt(plan)))
        message = "Roteiro copiado. Cole no ChatGPT e envie para gerar a proposta."
    }
    ObraList {
        item { SheetCard {
            Heading("Planejar com ChatGPT", plan.title)
            Detail("1. Copie o roteiro com o escopo, local e área informados.")
            Detail("2. Abra o ChatGPT, cole e envie o roteiro. Confira as perguntas e a proposta gerada.")
            Detail("3. Importe a resposta no ObraControl para revisar e aprovar cada etapa.")
            Button(onClick = ::copyPrompt, modifier = Modifier.fillMaxWidth()) { Text("Copiar roteiro para IA") }
            OutlinedButton(onClick = {
                copyPrompt()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com"))) }
                    .onFailure { message = "Roteiro copiado. Abra o ChatGPT e cole a mensagem." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Abrir ChatGPT") }
            OutlinedButton(onClick = { navigate("planejamento/importar") }, modifier = Modifier.fillMaxWidth()) { Text("Importar resposta da IA") }
            message?.let { Detail(it, Green) }
        } }
        item { SheetCard {
            Heading("Preços e revisão")
            Detail("A análise é feita no ChatGPT. O app recebe a proposta como um novo rascunho, sem aprovação ou pagamentos. Quantidades e preços sem base ficam a definir.")
            Detail("Use cotações locais e referências verificáveis com data. Confira se a mão de obra já está incluída em serviços contratados.")
        } }
    }
}

@Composable
private fun AiPlanImportScreen(project: Project, save: (Project) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var json by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<WorkPlan?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use(::readBackup)
                    ?: throw IllegalArgumentException("Não foi possível abrir o arquivo.") } }
                    .onSuccess { json = it; preview = null; message = "Arquivo carregado. Confira a proposta antes de salvar." }
                    .onFailure { message = "Não foi possível ler o arquivo. Use a resposta gerada com o roteiro do app." }
                busy = false
            }
        }
    }
    Form(available = !busy, save = {
        val plan = requireNotNull(preview) { "Confira a proposta antes de importar." }
        save(project.addWorkPlan(plan)); back()
    }) {
        Heading("Importar proposta da IA", "Cole a resposta gerada com o roteiro do app ou selecione o arquivo recebido. A importação acrescenta um rascunho e preserva o cadastro atual.")
        OutlinedButton(enabled = !busy, onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Carregar arquivo da proposta") }
        Field("Resposta da IA", json, { json = it; preview = null; message = null }, multiline = true)
        Button(enabled = !busy, onClick = {
            preview = null
            runCatching { WorkPlanCodec.decodeAi(json) }.onSuccess { preview = it; message = null }
                .onFailure { message = (it as? IllegalArgumentException)?.message ?: "Resposta inválida. Gere a proposta usando o roteiro do app e cole o objeto completo." }
        }, modifier = Modifier.fillMaxWidth().testTag("preview-ai-plan")) { Text("Conferir proposta") }
        message?.let { Detail(it, Amber) }
        preview?.let { plan ->
            Heading(plan.title)
            Detail("${plan.stages.size} etapas · ${plan.stages.sumOf { it.services.size }} serviços · ${plan.stages.sumOf { it.items.size }} insumos")
            LedgerLine("Estimativa conhecida", plan.knownTotal.brl())
            Detail("${plan.missing} pendências de orçamento. Todas as etapas entram sem aprovação.")
            plan.stages.forEach { Detail("• ${it.name}") }
            if (project.workPlans.any { it.originKey == plan.originKey }) Detail("Esta proposta já foi importada. Salvar preservará o rascunho existente sem duplicar.", Green)
        }
    }
}
