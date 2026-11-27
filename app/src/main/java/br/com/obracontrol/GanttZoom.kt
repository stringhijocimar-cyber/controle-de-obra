package br.com.obracontrol

internal enum class GanttZoom(val factor: Float?, val label: String) {
    FIT(null, "Tudo"), HALF(0.5f, "50%"), NORMAL(1f, "100%"), NEAR(1.5f, "150%"),
    DOUBLE(2f, "200%"), TRIPLE(3f, "300%"), FOUR(4f, "400%");

    fun width(viewport: Float, preferred: Float): Float =
        if (factor == null) viewport else maxOf(viewport, minOf(24_000f, preferred * factor))

    fun next(viewport: Float, preferred: Float, increase: Boolean): GanttZoom? {
        val current = width(viewport, preferred)
        return entries.filter { if (increase) it.width(viewport, preferred) > current + 0.5f
            else it.width(viewport, preferred) < current - 0.5f }
            .minByOrNull { kotlin.math.abs(it.width(viewport, preferred) - current) }
    }
}
