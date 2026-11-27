package br.com.obracontrol

internal enum class CostNature(val label: String) {
    MATERIAL("Material"), SERVICE("Serviço terceirizado"),
}

/** The storage kind keeps existing payment links; the category describes the cost to the user. */
internal fun Project.costCategory(kind: CostKind, targetId: String): CostCategory = when {
    kind == CostKind.LABOR -> CostCategory.LABOR
    costNature(kind, targetId) == CostNature.SERVICE -> CostCategory.SERVICE
    else -> CostCategory.MATERIAL
}

internal val CostCategory.filterLabel: String get() = when (this) {
    CostCategory.MATERIAL -> "Materiais"
    CostCategory.LABOR -> "Mão de obra"
    CostCategory.SERVICE -> "Terceirizados"
}

internal fun Project.costNature(kind: CostKind, targetId: String): CostNature = when (kind) {
    CostKind.LABOR -> CostNature.SERVICE
    CostKind.MATERIAL -> purchases.firstOrNull { it.id == targetId }?.nature ?: CostNature.MATERIAL
}
