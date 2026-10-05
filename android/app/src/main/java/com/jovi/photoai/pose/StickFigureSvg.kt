package com.jovi.photoai.pose

internal data class StickFigureGraphic(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float,
    val shapes: List<StickShape>,
)

internal sealed interface StickShape {
    val strokeWidth: Float

    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        override val strokeWidth: Float,
    ) : StickShape

    data class Circle(
        val cx: Float,
        val cy: Float,
        val r: Float,
        override val strokeWidth: Float,
    ) : StickShape

    data class Polyline(
        val points: List<Pair<Float, Float>>,
        override val strokeWidth: Float,
    ) : StickShape

    data class Rect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        override val strokeWidth: Float,
    ) : StickShape
}

/** Draws the producer stick-figure subset: line, circle, polyline, and rect. No external resources. */
internal fun parseStickFigureSvg(svg: String): StickFigureGraphic? {
    val viewBox = Regex("""viewBox\s*=\s*"([^"]+)"""").find(svg) ?: return null
    val parts = viewBox.groupValues[1].trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
    if (parts.size != 4 || parts[2] <= 0f || parts[3] <= 0f) return null
    val shapes = mutableListOf<StickShape>()
    val tags = Regex("""<(line|circle|polyline|rect)\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    tags.findAll(svg).forEach { match ->
        val name = match.groupValues[1].lowercase()
        val attrs = attributes(match.groupValues[2])
        val stroke = attrs["stroke-width"]?.toFloatOrNull() ?: 4f
        when (name) {
            "line" -> {
                val x1 = attrs["x1"]?.toFloatOrNull() ?: return@forEach
                val y1 = attrs["y1"]?.toFloatOrNull() ?: return@forEach
                val x2 = attrs["x2"]?.toFloatOrNull() ?: return@forEach
                val y2 = attrs["y2"]?.toFloatOrNull() ?: return@forEach
                shapes += StickShape.Line(x1, y1, x2, y2, stroke)
            }
            "circle" -> {
                val cx = attrs["cx"]?.toFloatOrNull() ?: return@forEach
                val cy = attrs["cy"]?.toFloatOrNull() ?: return@forEach
                val r = attrs["r"]?.toFloatOrNull() ?: return@forEach
                if (r > 0f) shapes += StickShape.Circle(cx, cy, r, stroke)
            }
            "polyline" -> {
                val points = parsePoints(attrs["points"] ?: return@forEach) ?: return@forEach
                shapes += StickShape.Polyline(points, stroke)
            }
            "rect" -> {
                val x = attrs["x"]?.toFloatOrNull() ?: 0f
                val y = attrs["y"]?.toFloatOrNull() ?: 0f
                val width = attrs["width"]?.toFloatOrNull() ?: return@forEach
                val height = attrs["height"]?.toFloatOrNull() ?: return@forEach
                if (width > 0f && height > 0f) shapes += StickShape.Rect(x, y, width, height, stroke)
            }
        }
    }
    if (shapes.isEmpty()) return null
    return StickFigureGraphic(parts[0], parts[1], parts[2], parts[3], shapes)
}

private fun attributes(raw: String): Map<String, String> =
    Regex("""([\w:-]+)\s*=\s*"([^"]*)"""").findAll(raw).associate { it.groupValues[1] to it.groupValues[2] }

private fun parsePoints(raw: String): List<Pair<Float, Float>>? {
    val numbers = Regex("[-+]?(?:\\d+\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?").findAll(raw).map { it.value.toFloat() }.toList()
    if (numbers.size < 4 || numbers.size % 2 != 0) return null
    return numbers.chunked(2).map { it[0] to it[1] }
}