package br.com.obracontrol

internal enum class CostNature(val label: String) {
    MATERIAL("Material"), SERVICE("Serviço"),
}

internal fun Project.costNature(kind: CostKind, targetId: String): CostNature = when (kind) {
    CostKind.LABOR -> CostNature.SERVICE
    CostKind.MATERIAL -> purchases.firstOrNull { it.id == targetId }?.nature ?: CostNature.MATERIAL
}
