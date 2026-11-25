package br.com.obracontrol

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode

private const val FORMAT_VERSION = 9
internal const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

internal class ProjectStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "project.json"))
    private val older = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    fun load(): Project {
        val stream = try {
            file.openRead()
        } catch (_: FileNotFoundException) {
            val legacy = older.getString("project", null) ?: return Project()
            return ProjectCodec.decode(legacy).also(::save)
        }
        return stream.use { ProjectCodec.decode(readBackup(it)) }
    }

    fun save(project: Project) {
        val bytes = ProjectCodec.encode(project).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_BYTES) { "O cadastro excedeu o limite de 5 MB." }
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }
}

internal fun readBackup(stream: InputStream): String {
    val buffer = ByteArray(8192)
    val data = ByteArrayOutputStream()
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        require(data.size() + read <= MAX_BACKUP_BYTES) { "O backup deve ter até 5 MB." }
        data.write(buffer, 0, read)
    }
    return data.toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF")
}

internal object ProjectCodec {
    fun encode(project: Project): String = obj(
        "version" to FORMAT_VERSION,
        "project" to project.name,
        "start" to project.start.dateText(),
        "referenceDate" to project.referenceDate.dateText(),
        "laborBudgetCents" to project.laborBudget,
        "notes" to project.notes,
        "payerNames" to JSONArray(project.payerNames),
        "workPlans" to WorkPlanCodec.encode(project.workPlans),
        "scheduledPayments" to array(project.scheduledPayments.map { plan -> obj(
            "id" to plan.id, "kind" to plan.kind.name, "targetId" to plan.targetId,
            "amountCents" to plan.amount, "date" to plan.date.dateText(), "label" to plan.label,
        ) }),
        "stages" to array(project.stages.map { stage -> obj(
            "id" to stage.id,
            "name" to stage.name,
            "budgetCents" to stage.budget,
            "plannedStart" to stage.plannedStart.dateText(),
            "plannedEnd" to stage.plannedEnd.dateText(),
            "actualStart" to stage.actualStart.dateText(),
            "actualEnd" to stage.actualEnd.dateText(),
            "dueDate" to stage.dueDate.dateText(),
            "progress" to stage.progress,
            "notes" to stage.notes,
        ) }),
        "purchases" to array(project.purchases.map { purchase -> obj(
            "id" to purchase.id,
            "name" to purchase.name,
            "supplier" to purchase.supplier,
            "document" to purchase.document,
            "documentDate" to purchase.documentDate.dateText(),
            "deliveryExpected" to purchase.deliveryExpected.dateText(),
            "receivedDate" to purchase.receivedDate.dateText(),
            "payer" to purchase.payer,
            "method" to purchase.method,
            "installmentCount" to purchase.installmentCount,
            "nature" to purchase.nature.name,
            "dueDate" to purchase.dueDate.dateText(),
            "stageId" to purchase.stageId,
            "totalCents" to purchase.total,
            "discountCents" to purchase.discount,
            "extra" to purchase.extra,
            "notes" to purchase.notes,
            "documents" to encodeAttachments(purchase.documents),
            "lines" to array(purchase.lines.map { line -> obj(
                "id" to line.id,
                "name" to line.name,
                "quantity" to line.quantity?.toPlainString(),
                "unit" to line.unit,
                "unitPriceCents" to line.unitPrice,
                "documentSubtotalCents" to line.documentSubtotal,
            ) }),
        ) }),
        "payments" to array(project.payments.map { payment -> obj(
            "id" to payment.id,
            "kind" to payment.kind.name,
            "targetId" to payment.targetId,
            "amountCents" to payment.amount,
            "date" to payment.date.dateText(),
            "payer" to payment.payer,
            "method" to payment.method,
            "receipt" to payment.receipt,
            "notes" to payment.notes,
            "proofs" to encodeAttachments(payment.proofs),
            "scheduleId" to payment.scheduleId,
        ) }),
        "diaryEntries" to array(project.diaryEntries.map { entry -> obj(
            "id" to entry.id,
            "date" to entry.date.dateText(),
            "title" to entry.title,
            "stageId" to entry.stageId,
            "activities" to entry.activities,
            "occurrences" to entry.occurrences,
            "attachments" to encodeAttachments(entry.attachments),
        ) }),
        "issues" to array(project.issues.map { issue -> obj(
            "id" to issue.id, "title" to issue.title, "action" to issue.action, "closed" to issue.closed,
        ) }),
    ).toString(2)

    fun decode(json: String): Project {
        val root = JSONObject(json)
        val version = if (root.has("version")) root.getInt("version") else 1
        val project = when (version) {
            1 -> migrateLegacy(root)
            2, 3, 4, 5, 6, 7, 8, FORMAT_VERSION -> decodeCurrent(root)
            else -> throw IllegalArgumentException("Versão de backup incompatível.")
        }
        project.validate()
        return project
    }

    private fun decodeCurrent(root: JSONObject) = Project(
        name = root.string("project"),
        start = root.date("start"),
        referenceDate = root.date("referenceDate"),
        laborBudget = root.cents("laborBudgetCents"),
        notes = root.string("notes"),
        workPlans = WorkPlanCodec.decode(root.optJSONArray("workPlans")),
        payerNames = root.optJSONArray("payerNames")?.let { names ->
            (0 until names.length()).map { names.getString(it) }
        }.orEmpty(),
        scheduledPayments = root.optionalObjects("scheduledPayments").map { plan -> ScheduledPayment(
            id = plan.getString("id"), kind = CostKind.valueOf(plan.getString("kind")),
            targetId = plan.getString("targetId"), amount = plan.cents("amountCents") ?: 0,
            date = plan.date("date"), label = plan.string("label"),
        ) },
        stages = root.objects("stages").map { stage -> Stage(
            id = stage.getString("id"),
            name = stage.string("name"),
            budget = stage.cents("budgetCents"),
            plannedStart = stage.date("plannedStart"),
            plannedEnd = stage.date("plannedEnd"),
            actualStart = stage.date("actualStart"),
            actualEnd = stage.date("actualEnd"),
            dueDate = stage.date("dueDate"),
            progress = stage.getInt("progress"),
            notes = stage.string("notes"),
        ) },
        purchases = root.objects("purchases").map { purchase -> Purchase(
            id = purchase.getString("id"),
            name = purchase.string("name"),
            supplier = purchase.string("supplier"),
            document = purchase.string("document"),
            documentDate = purchase.date("documentDate"),
            deliveryExpected = purchase.date("deliveryExpected"),
            receivedDate = purchase.date("receivedDate"),
            payer = purchase.string("payer"),
            method = purchase.string("method"),
            installmentCount = if (purchase.isNull("installmentCount")) null
                else BigDecimal(purchase.get("installmentCount").toString()).intValueExact(),
            nature = if (purchase.isNull("nature")) CostNature.MATERIAL
                else CostNature.valueOf(purchase.getString("nature")),
            dueDate = purchase.date("dueDate"),
            stageId = purchase.string("stageId").takeIf(String::isNotBlank),
            total = purchase.cents("totalCents"),
            discount = purchase.cents("discountCents") ?: 0,
            extra = purchase.optBoolean("extra"),
            notes = purchase.string("notes"),
            documents = purchase.attachments("documents"),
            lines = purchase.objects("lines").map { line -> PurchaseLine(
                id = line.getString("id"),
                name = line.string("name"),
                quantity = parseQuantity(line.string("quantity")),
                unit = line.string("unit"),
                unitPrice = line.cents("unitPriceCents"),
                documentSubtotal = line.cents("documentSubtotalCents"),
            ) },
        ) },
        payments = root.objects("payments").map { payment -> Payment(
            id = payment.getString("id"),
            kind = CostKind.valueOf(payment.getString("kind")),
            targetId = payment.getString("targetId"),
            amount = payment.cents("amountCents") ?: 0,
            date = payment.date("date"),
            payer = payment.string("payer"),
            method = payment.string("method"),
            receipt = payment.string("receipt"),
            notes = payment.string("notes"),
            proofs = payment.attachments("proofs"),
            scheduleId = payment.string("scheduleId").takeIf(String::isNotBlank),
        ) },
        diaryEntries = root.optionalObjects("diaryEntries").map { entry -> DiaryEntry(
            id = entry.getString("id"),
            date = requireNotNull(entry.date("date")) { "O diário contém um registro sem data." },
            title = entry.string("title"),
            stageId = entry.string("stageId").takeIf(String::isNotBlank),
            activities = entry.string("activities"),
            occurrences = entry.string("occurrences"),
            attachments = entry.attachments("attachments"),
        ) },
        issues = root.objects("issues").map { issue -> Issue(
            id = issue.getString("id"),
            title = issue.string("title"),
            action = issue.string("action"),
            closed = issue.optBoolean("closed"),
        ) },
    )

    private fun migrateLegacy(root: JSONObject): Project {
        require(root.has("project")) { "Arquivo de projeto inválido." }
        val payments = mutableListOf<Payment>()
        val stages = root.objects("stages").map { entry ->
            val completed = entry.optBoolean("completed")
            val stage = Stage(
                name = entry.getString("name"),
                budget = entry.legacyCents("budget"),
                plannedStart = entry.date("start").takeUnless { completed },
                plannedEnd = entry.date("end").takeUnless { completed },
                actualStart = entry.date("start").takeIf { completed },
                actualEnd = entry.date("end").takeIf { completed },
                progress = if (completed) 100 else 0,
                notes = entry.string("notes"),
            )
            val paid = entry.legacyCents("paid") ?: 0
            if (paid > 0) payments += entry.legacyPayment(CostKind.LABOR, stage.id, paid)
            stage
        }
        val purchases = root.objects("materials").map { entry ->
            val purchase = Purchase(
                name = entry.getString("name"),
                supplier = entry.string("supplier"),
                document = entry.string("document"),
                total = entry.legacyCents("value"),
                extra = entry.optBoolean("extra"),
                notes = entry.string("notes"),
                lines = entry.optionalObjects("items").map { item -> PurchaseLine(
                    name = item.getString("name"),
                    quantity = if (item.isNull("quantity")) null else parseQuantity(item.get("quantity").toString()),
                    unit = item.string("unit"),
                    unitPrice = item.legacyCents("unitPrice"),
                    documentSubtotal = item.legacyCents("total"),
                ) },
            )
            val amount = purchase.total ?: 0
            if (entry.optBoolean("paid") && amount > 0) {
                payments += entry.legacyPayment(CostKind.MATERIAL, purchase.id, amount)
            }
            purchase
        }
        return Project(root.getString("project"), root.date("start"), root.date("referenceDate"),
            root.legacyCents("laborBudget"), stages, purchases, payments, notes = root.string("notes"))
    }

    private fun JSONObject.legacyPayment(kind: CostKind, id: String, amount: Long) = Payment(
        kind = kind, targetId = id, amount = amount, date = date("paymentDate"),
        payer = string("payer").takeUnless { it == UNREPORTED }.orEmpty(),
        method = string("method").takeUnless { it == UNREPORTED }.orEmpty(),
    )

    private fun encodeAttachments(documents: List<DocumentAttachment>) = array(documents.map {
        obj("id" to it.id, "name" to it.name, "type" to it.type.name, "size" to it.size)
    })

    private fun JSONObject.attachments(key: String) = optionalObjects(key).map {
        DocumentAttachment(it.getString("id"), it.getString("name"),
            DocumentType.valueOf(it.getString("type")), it.getLong("size"))
    }

    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }

    private fun array(values: List<JSONObject>) = JSONArray(values)
    private fun JSONObject.objects(key: String) = getJSONArray(key).let { items ->
        List(items.length()) { items.getJSONObject(it) }
    }
    private fun JSONObject.optionalObjects(key: String) = if (isNull(key)) emptyList() else objects(key)
    private fun JSONObject.string(key: String) = if (isNull(key)) "" else getString(key)
    private fun JSONObject.date(key: String) = parseDate(string(key))
    private fun JSONObject.cents(key: String) = if (isNull(key)) null else checkedMoney(
        BigDecimal(get(key).toString()).longValueExact(),
    )
    private fun JSONObject.legacyCents(key: String) = if (isNull(key)) null else checkedMoney(
        BigDecimal(get(key).toString().replace(',', '.')).movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP).longValueExact(),
    )
}

internal fun Project.validate() {
    require(name.isNotBlank()) { "Informe o nome da obra." }
    require(payerNames.all(String::isNotBlank)) { "O cadastro contém um pagador sem nome." }
    requireDistinctIds(stages.map { it.id })
    requireDistinctIds(purchases.map { it.id })
    requireDistinctIds(payments.map { it.id })
    requireDistinctIds(scheduledPayments.map { it.id })
    requireDistinctIds(issues.map { it.id })
    requireDistinctIds(diaryEntries.map { it.id })
    requireDistinctIds(workPlans.map { it.id })
    require(workPlans.size <= 50) { "O cadastro comporta até 50 planejamentos." }
    workPlans.forEach { plan ->
        plan.validate()
        plan.stages.forEach { stage ->
            require(stage.linkedStageId == null || stages.any { it.id == stage.linkedStageId }) { "A etapa vinculada ao planejamento não existe." }
            stage.items.forEach { item ->
                require(item.purchaseId == null || purchases.any { it.id == item.purchaseId && it.stageId == stage.linkedStageId }) {
                    "O insumo planejado está vinculado a uma compra de outra etapa."
                }
            }
        }
    }
    val linked = workPlans.flatMap { it.stages }.mapNotNull { it.linkedStageId }
    require(linked.distinct().size == linked.size) { "Uma etapa de execução só pode estar vinculada a uma etapa de planejamento." }
    stages.forEach(Stage::validate)
    purchases.forEach { order ->
        require(order.name.isNotBlank()) { "Informe o nome da compra." }
        require(order.installmentCount == null || order.installmentCount > 0) { "Informe uma quantidade de parcelas maior que zero." }
        require(order.stageId == null || stages.any { it.id == order.stageId }) { "A etapa da compra não existe." }
        requireDistinctIds(order.lines.map { it.id })
        order.lines.forEach(PurchaseLine::validate)
        requireDistinctIds(order.documents.map { it.id })
    }
    payments.forEach {
        it.validate(this)
        require(it.scheduleId == null || scheduledPayments.any { plan ->
            plan.id == it.scheduleId && plan.kind == it.kind && plan.targetId == it.targetId
        }) { "A parcela vinculada ao pagamento não pertence a este custo." }
        requireDistinctIds(it.proofs.map { proof -> proof.id })
        require(it.proofs.none { proof -> proof.type == DocumentType.XML }) { "Comprovantes precisam ser imagens ou PDFs." }
    }
    scheduledPayments.forEach { plan ->
        require(costTarget(plan.kind, plan.targetId) != null) { "O custo da programação não existe." }
        require(plan.label.isNotBlank() && plan.amount > 0) { "Informe a descrição e um valor positivo para cada parcela." }
        checkedMoney(plan.amount)
    }
    scheduledPayments.groupBy { it.kind to it.targetId }.forEach { (target, rows) ->
        val value = costTarget(target.first, target.second)?.total
        require(value != null) { "Defina o valor do custo antes de programar parcelas." }
        require(rows.sumOf { it.amount } <= value) { "A programação supera o valor deste custo. Ajuste as parcelas ou o valor do cadastro." }
    }
    diaryEntries.forEach { entry ->
        require(entry.title.isNotBlank()) { "Informe um título para o registro do diário." }
        require(entry.stageId == null || stageName(entry.stageId) != null) { "A etapa do diário não existe." }
        requireDistinctIds(entry.attachments.map { it.id })
        require(entry.attachments.none { it.type == DocumentType.XML }) { "Anexe imagens ou PDFs ao diário." }
    }
    documentAttachments().forEach(DocumentAttachment::validate)
    require(issues.all { it.title.isNotBlank() }) { "Informe um título para a pendência." }
}

internal fun requireDistinctIds(ids: List<String>) {
    require(ids.all { it.matches(Regex("[A-Za-z0-9_-]+")) && it != "nova" && it != "novo" } && ids.distinct().size == ids.size) {
        "O projeto contém registros repetidos ou inválidos."
    }
}
