package br.com.obracontrol

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

internal object WorkPlanCodec {
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
    private fun JSONObject.text(key: String) = if (isNull(key)) "" else getString(key)
    private fun JSONObject.list(key: String): List<JSONObject> = if (isNull(key)) emptyList() else getJSONArray(key).let { a ->
        List(a.length()) { a.getJSONObject(it) }
    }
    private fun JSONObject.strings(key: String): List<String> = if (isNull(key)) emptyList() else getJSONArray(key).let { a ->
        List(a.length()) { a.getString(it) }
    }
    private fun JSONObject.intValue(key: String) = BigDecimal(get(key).toString()).intValueExact()
    private fun JSONObject.cents(key: String): Long? = if (isNull(key)) null else checkedMoney(BigDecimal(get(key).toString()).longValueExact())
    private fun JSONObject.date(key: String) = parseDate(text(key))
    private fun JSONObject.id(ai: Boolean) = if (ai) newId() else getString("id")

    private fun encodeInput(item: PlanInput): JSONObject = obj(
        "id" to item.id, "name" to item.name, "category" to item.category.name,
        "quantity" to item.quantity?.toPlainString(), "unit" to item.unit, "unitPriceCents" to item.unitPrice,
        "priceSource" to item.priceSource, "priceDate" to item.priceDate.dateText(), "estimated" to item.estimated,
        "notes" to item.notes, "purchaseId" to item.purchaseId,
    )

    private fun decodeInput(source: JSONObject, ai: Boolean): PlanInput = PlanInput(
        id = source.id(ai), name = source.getString("name").trim(), category = CostCategory.valueOf(source.getString("category")),
        quantity = parseQuantity(source.text("quantity")), unit = source.text("unit").trim(), unitPrice = source.cents("unitPriceCents"),
        priceSource = source.text("priceSource").trim(), priceDate = source.date("priceDate"),
        estimated = if (source.isNull("estimated")) true else source.getBoolean("estimated"), notes = source.text("notes"),
        purchaseId = if (ai) null else source.text("purchaseId").takeIf(String::isNotBlank),
    )

    fun encode(plans: List<WorkPlan>) = JSONArray(plans.map { plan -> obj(
        "id" to plan.id, "title" to plan.title, "type" to plan.type.name, "scope" to plan.scope, "location" to plan.location,
        "area" to plan.area?.toPlainString(), "lossPercent" to plan.lossPercent.toPlainString(), "created" to plan.created.dateText(),
        "assumptions" to JSONArray(plan.assumptions), "questions" to JSONArray(plan.questions), "originKey" to plan.originKey,
        "stages" to JSONArray(plan.stages.map { stage -> obj(
            "id" to stage.id, "name" to stage.name, "notes" to stage.notes, "revision" to stage.revision, "linkedStageId" to stage.linkedStageId,
            "services" to JSONArray(stage.services.map { service -> obj("id" to service.id, "name" to service.name,
                "items" to JSONArray(service.items.map(::encodeInput))) }),
            "approvals" to JSONArray(stage.approvals.map { approval -> obj(
                "revision" to approval.revision, "date" to approval.date.dateText(), "approver" to approval.approver,
                "notes" to approval.notes, "items" to JSONArray(approval.items.map(::encodeInput))) }),
        ) }),
    ) })

    fun decode(array: JSONArray?): List<WorkPlan> = if (array == null) emptyList() else List(array.length()) { index ->
        decodePlan(array.getJSONObject(index), ai = false).also(WorkPlan::validate)
    }

    private fun decodePlan(root: JSONObject, ai: Boolean): WorkPlan = WorkPlan(
        id = root.id(ai), title = root.getString("title").trim(),
        type = if (ai) WorkPlanType.CUSTOM else WorkPlanType.valueOf(root.getString("type")),
        scope = root.text("scope"), location = root.text("location"), area = parseQuantity(root.text("area")),
        lossPercent = parseQuantity(root.text("lossPercent")) ?: BigDecimal.TEN,
        created = if (ai) LocalDate.now() else requireNotNull(root.date("created")),
        assumptions = root.strings("assumptions"), questions = root.strings("questions"), originKey = if (ai) "" else root.text("originKey"),
        stages = root.list("stages").map { stage -> PlanStage(
            id = stage.id(ai), name = stage.getString("name").trim(), notes = stage.text("notes"),
            revision = if (ai) 1 else stage.intValue("revision"),
            linkedStageId = if (ai) null else stage.text("linkedStageId").takeIf(String::isNotBlank),
            services = stage.list("services").map { service -> PlanService(id = service.id(ai), name = service.getString("name").trim(),
                items = service.list("items").map { decodeInput(it, ai) }) },
            approvals = if (ai) emptyList() else stage.list("approvals").map { approval -> PlanApproval(
                revision = approval.intValue("revision"), date = requireNotNull(approval.date("date")),
                approver = approval.getString("approver"), notes = approval.text("notes"),
                items = approval.list("items").map { decodeInput(it, false) }) },
        ) },
    )

    fun decodeAi(text: String): WorkPlan {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "O planejamento deve ter até 5 MB." }
        val raw = text.trim().removePrefix("\uFEFF")
        val json = if (raw.startsWith("```")) raw.substringAfter('\n').substringBeforeLast("```").trim() else raw
        val root = JSONObject(json)
        require(root.getString("format") == "obracontrol-planning-v1") { "Use o formato do roteiro de planejamento do ObraControl." }
        val result = decodePlan(root, ai = true).also(WorkPlan::validate)
        return result.copy(originKey = "ai:" + result.contentFingerprint())
    }

    fun aiPrompt(plan: WorkPlan): String = """
        Atue como orçamentista e planejador de obras. Prepare uma EAP editável para o ObraControl.
        Objetivo: ${plan.title}
        Escopo: ${plan.scope}
        Local: ${plan.location.ifBlank { "a confirmar" }}
        Tipo: ${plan.type.label}
        Área informada: ${plan.area.quantityInput().ifBlank { "a confirmar" }} m². A área pode ser insuficiente para quantificar os serviços.
        Perda de referência informada: ${plan.lossPercent.quantityInput()}%. Aplicar somente onde fizer sentido e explicar.
        Etapas atuais do rascunho: ${plan.stages.joinToString { it.name }}.

        Organize em etapas > serviços > insumos. Separe MATERIAL, LABOR (mão de obra) e SERVICE (terceiros, frete, equipamentos ou taxas).
        Evite duplicar mão de obra já inclusa numa contratação. Inclua preparativos, resíduos, acabamentos e entrega quando aplicáveis.
        Explique hipóteses em assumptions e liste informações faltantes em questions. Estrutura e fundações dependem de projeto; não dimensione por regra de área.
        Quantidades desconhecidas ficam null. Preços sem cotação ou fonte verificável ficam null: nunca preencha zero para representar preço ausente.
        Se usar referência de preço, informe fonte, local e data. Não invente código, valor ou consulta ao SINAPI; referências sem preço não representam custo zero.
        Estimativas devem ter estimated=true e explicar a base. Não aprovar etapas nem criar pagamentos.
        Responda SOMENTE com um objeto JSON válido no formato abaixo. Valores monetários em CENTAVOS inteiros, quantidades em string decimal com ponto, datas dd/MM/aaaa ou null. Sem IDs.
        {
          "format":"obracontrol-planning-v1", "title":"${plan.title.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")}",
          "scope":"descrever", "location":"cidade/UF", "area":null, "lossPercent":"${plan.lossPercent.toPlainString()}",
          "assumptions":["hipóteses"], "questions":["dados faltantes"],
          "stages":[{"name":"Nome da etapa", "notes":"escopo e condições",
            "services":[{"name":"Nome do serviço", "items":[
              {"name":"Descrição do material", "category":"MATERIAL", "quantity":null, "unit":"m²",
               "unitPriceCents":null, "priceSource":"", "priceDate":null, "estimated":true, "notes":"como quantificar"},
              {"name":"Execução", "category":"LABOR", "quantity":null, "unit":"m²",
               "unitPriceCents":null, "priceSource":"", "priceDate":null, "estimated":true, "notes":"o que inclui"}
            ]}]}]
        }
        Este JSON será importado como rascunho adicional. O usuário revisará quantidades, preços, etapas e aprovação no app.
    """.trimIndent()
}
