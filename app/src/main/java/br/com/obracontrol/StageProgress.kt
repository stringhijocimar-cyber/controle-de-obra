package br.com.obracontrol

import java.text.NumberFormat

/** Delivery is physical availability. Payments never enter this calculation. */
internal val Purchase.receivedPercent: Int get() = if (receivedDate != null) 100 else receivedProgress ?: 0

internal data class StageProgress(
    val execution: Int,
    val materials: Double?,
    val materialWeight: Int,
    val materialCount: Int,
    val receivedCount: Int,
    val byValue: Boolean,
) {
    val executionWeight get() = 100 - materialWeight
    val general: Double get() = materials?.let { (execution * executionWeight + it * materialWeight) / 100.0 } ?: execution.toDouble()
    val materialsText get() = materials?.let(::stagePercent) ?: "Sem materiais/serviços"
    val generalText get() = stagePercent(general)
    val weightsText get() = if (materials == null) "Mão de obra 100%" else "Mão de obra $executionWeight% / materiais e serviços $materialWeight%"
    val basisText get() = when {
        materials == null -> "Vincule materiais e serviços a esta etapa para incluir seus percentuais no avanço geral."
        byValue -> "Materiais recebidos e serviços concluídos, ponderados pelo valor previsto de cada compra."
        else -> "Materiais e serviços pela média dos percentuais das compras; há compras sem valor positivo definido."
    }
}

internal fun Project.stageProgress(stage: Stage): StageProgress {
    val materials = purchases.filter { it.stageId == stage.id }
    val byValue = materials.isNotEmpty() && materials.all { (it.forecast ?: 0) > 0 }
    val received = when {
        materials.isEmpty() -> null
        byValue -> materials.sumOf { it.forecast!!.toDouble() * it.receivedPercent } / materials.sumOf { it.forecast!!.toDouble() }
        else -> materials.map { it.receivedPercent }.average()
    }
    return StageProgress(stage.progress, received, if (materials.isEmpty()) 0 else stage.materialWeight,
        materials.size, materials.count { it.receivedPercent == 100 }, byValue)
}

internal val Project.generalStageProgress: Double get() = stages.map { stageProgress(it).general }.average().takeUnless(Double::isNaN) ?: 0.0
internal val Project.unlinkedMaterials: Int get() = purchases.count { it.stageId == null }

internal fun stagePercent(value: Double): String = NumberFormat.getPercentInstance(brazil).apply {
    maximumFractionDigits = 1
}.format(value / 100.0)
