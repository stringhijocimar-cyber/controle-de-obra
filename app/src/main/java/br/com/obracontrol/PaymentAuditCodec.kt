package br.com.obracontrol

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

internal object PaymentAuditCodec {
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (k,v) -> put(k, v ?: JSONObject.NULL) } }
    private fun JSONObject.text(key: String) = if (isNull(key)) "" else getString(key)
    fun encode(entries: List<PaymentAudit>) = JSONArray(entries.map { entry ->
        val p = entry.payment
        obj("id" to entry.id, "date" to entry.date.dateText(), "reason" to entry.reason, "voided" to entry.voided,
            "restoredAt" to entry.restoredAt.dateText(), "costName" to entry.costName, "payment" to obj(
                "id" to p.id, "kind" to p.kind.name, "targetId" to p.targetId, "amountCents" to p.amount,
                "date" to p.date.dateText(), "payer" to p.payer, "method" to p.method, "receipt" to p.receipt,
                "notes" to p.notes, "scheduleId" to p.scheduleId,
                "proofs" to JSONArray(p.proofs.map { obj("id" to it.id, "name" to it.name, "type" to it.type.name, "size" to it.size) })))
    })
    fun decode(array: JSONArray?): List<PaymentAudit> = if (array == null) emptyList() else List(array.length()) { index ->
        val e = array.getJSONObject(index)
        val p = e.getJSONObject("payment")
        val proofs = p.getJSONArray("proofs")
        PaymentAudit(id = e.getString("id"), date = requireNotNull(parseDate(e.getString("date"))), reason = e.getString("reason"),
            voided = e.getBoolean("voided"), restoredAt = parseDate(e.text("restoredAt")), costName = e.text("costName"),
            payment = Payment(id = p.getString("id"), kind = CostKind.valueOf(p.getString("kind")), targetId = p.getString("targetId"),
                amount = checkedMoney(BigDecimal(p.get("amountCents").toString()).longValueExact()), date = parseDate(p.text("date")),
                payer = p.text("payer"), method = p.text("method"), receipt = p.text("receipt"), notes = p.text("notes"),
                scheduleId = p.text("scheduleId").takeIf(String::isNotBlank), proofs = List(proofs.length()) { i ->
                    val a = proofs.getJSONObject(i)
                    DocumentAttachment(a.getString("id"), a.getString("name"), DocumentType.valueOf(a.getString("type")), a.getLong("size"))
                }))
    }
}
