package com.jovi.photoai.pose

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal const val POSE_DIRECTION_SCHEMA_ID = "pose-direction-bundle"
internal const val POSE_DIRECTION_SCHEMA_VERSION = 1
/** Default on-device sample for the pose screen (18-item non-authority reference set). */
internal const val POSE_DIRECTION_ASSET = "pose_direction/sample_pose_direction_bundle_v1.json"
/** Tiny synthetic fixture kept for unit tests; not the product default. */
internal const val POSE_DIRECTION_SYNTHETIC_ASSET = "pose_direction/synthetic_pose_direction_bundle_v1.json"

private const val MAX_DOCUMENT_CHARS = 1_500_000
private const val MAX_TEXT_CHARS = 2_000
private const val MAX_ITEMS = 64

private val ITEM_ID = Regex("^img-\\d{2}-[0-9a-f]{8}$")
private val SVG_FILE = Regex("^img-\\d{2}-[0-9a-f]{8}\\.svg$")
private val PRIVATE_PATH = Regex(
    "(?i)(?:[a-z][a-z0-9+.-]*://|(?:content|file):|[a-z]:[\\\\/]|\\\\|\\.\\.[\\\\/]|/sdcard(?:/|$)|/storage(?:/|$)|\\.(?:jpe?g|png|heic|webp|raw|dng|gif|bmp)\\b)",
)
private val ALLOWED_SVG_URLS = setOf(
    "http://www.w3.org/2000/svg",
    "http://www.w3.org/1999/xlink",
)

internal data class PoseWhyItWorks(
    val composition: String? = null,
    val light: String? = null,
    val gaze: String? = null,
    val clothing: String? = null,
)

internal data class PoseDirectionItem(
    val id: String,
    val spokenDirection: String,
    val svg: String?,
    val svgFile: String?,
    val whyItWorks: PoseWhyItWorks?,
    /** Short Chinese composition name for list/detail/overlay. */
    val title: String? = null,
    /**
     * Optional asset basename (same pattern as [id]) for
     * assets/pose_direction/thumbs/{name}.jpg — never a filesystem path or image extension.
     */
    val referenceImage: String? = null,
    /** Ordered Chinese coaching steps; preferred over a dense paragraph. */
    val spokenSteps: List<String> = emptyList(),
)

internal data class PoseDirectionBundle(
    val producerNote: String,
    val items: List<PoseDirectionItem>,
)

internal enum class PoseDirectionReject {
    EMPTY,
    MALFORMED,
    SCHEMA,
    AUTHORITY,
    MISSING_SPOKEN,
    MISSING_FIGURE,
    PRIVATE_PATH,
}

internal sealed interface PoseDirectionParseResult {
    data class Success(val bundle: PoseDirectionBundle) : PoseDirectionParseResult
    data class Failure(val reason: PoseDirectionReject) : PoseDirectionParseResult
}

/**
 * Fail-closed reader for pose-direction-bundle version 1.
 * Authority must be JSON false. Ids and figure files stay public hash names, never photo paths.
 */
internal object PoseDirectionBundleParser {
    private val rootKeys = setOf("schema_id", "schema_version", "authority", "producer_note", "items")
    private val itemKeys = setOf("id", "spoken_direction", "stick_figure", "why_it_works", "title", "reference_image", "spoken_steps")
    private val requiredItemKeys = setOf("id", "spoken_direction", "stick_figure")
    private val figureKeys = setOf("svg", "svg_file")
    private val whyKeys = setOf("composition", "light", "gaze", "clothing")

    fun parse(text: String): PoseDirectionParseResult {
        if (text.isBlank()) return failure(PoseDirectionReject.EMPTY)
        if (text.length > MAX_DOCUMENT_CHARS) return failure(PoseDirectionReject.SCHEMA)
        val root = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return failure(PoseDirectionReject.MALFORMED)
        }
        return try {
            if (!root.has("authority") || root.opt("authority") != false) reject(PoseDirectionReject.AUTHORITY)
            if (root.keySet() != rootKeys) reject(PoseDirectionReject.SCHEMA)
            if (root.opt("schema_id") != POSE_DIRECTION_SCHEMA_ID) reject(PoseDirectionReject.SCHEMA)
            if (root.opt("schema_version") != POSE_DIRECTION_SCHEMA_VERSION) reject(PoseDirectionReject.SCHEMA)
            val note = root.opt("producer_note")
            if (note !is String || note.length > MAX_TEXT_CHARS) reject(PoseDirectionReject.SCHEMA)
            if (containsPrivate(note)) reject(PoseDirectionReject.PRIVATE_PATH)
            val itemsValue = root.opt("items")
            if (itemsValue !is JSONArray) reject(PoseDirectionReject.SCHEMA)
            if (itemsValue.length() == 0) reject(PoseDirectionReject.EMPTY)
            if (itemsValue.length() > MAX_ITEMS) reject(PoseDirectionReject.SCHEMA)
            val items = ArrayList<PoseDirectionItem>(itemsValue.length())
            val seen = HashSet<String>()
            for (index in 0 until itemsValue.length()) {
                val item = itemsValue.opt(index)
                if (item !is JSONObject) reject(PoseDirectionReject.SCHEMA)
                val parsed = parseItem(item)
                if (!seen.add(parsed.id)) reject(PoseDirectionReject.SCHEMA)
                items += parsed
            }
            PoseDirectionParseResult.Success(PoseDirectionBundle(note, items))
        } catch (rejected: Rejected) {
            failure(rejected.reason)
        }
    }

    private fun parseItem(item: JSONObject): PoseDirectionItem {
        val keys = item.keySet()
        if ("spoken_direction" !in keys) reject(PoseDirectionReject.MISSING_SPOKEN)
        if ("stick_figure" !in keys) reject(PoseDirectionReject.MISSING_FIGURE)
        if (!keys.containsAll(requiredItemKeys) || !itemKeys.containsAll(keys)) reject(PoseDirectionReject.SCHEMA)
        val id = item.opt("id")
        if (id !is String) reject(PoseDirectionReject.SCHEMA)
        if (containsPrivate(id)) reject(PoseDirectionReject.PRIVATE_PATH)
        if (!ITEM_ID.matches(id)) reject(PoseDirectionReject.SCHEMA)
        val spoken = item.opt("spoken_direction")
        if (spoken !is String || spoken.isBlank()) reject(PoseDirectionReject.MISSING_SPOKEN)
        if (spoken.length > MAX_TEXT_CHARS || hasDisallowedControl(spoken)) reject(PoseDirectionReject.SCHEMA)
        if (containsPrivate(spoken)) reject(PoseDirectionReject.PRIVATE_PATH)
        val figureValue = item.opt("stick_figure")
        if (figureValue !is JSONObject) reject(PoseDirectionReject.MISSING_FIGURE)
        val (svg, svgFile) = parseFigure(figureValue)
        val why = if (!item.has("why_it_works")) {
            null
        } else when (val whyValue = item.opt("why_it_works")) {
            is JSONObject -> parseWhy(whyValue)
            else -> reject(PoseDirectionReject.SCHEMA)
        }
        val title = parseOptionalTitle(item)
        val referenceImage = parseOptionalReferenceImage(item)
        val spokenSteps = parseSpokenSteps(item, spoken)
        return PoseDirectionItem(id, spoken, svg, svgFile, why, title, referenceImage, spokenSteps)
    }


    private fun parseSpokenSteps(item: JSONObject, spokenFallback: String): List<String> {
        if (item.has("spoken_steps")) {
            val value = item.opt("spoken_steps")
            if (value !is JSONArray || value.length() == 0) reject(PoseDirectionReject.SCHEMA)
            if (value.length() > 12) reject(PoseDirectionReject.SCHEMA)
            val steps = ArrayList<String>(value.length())
            for (index in 0 until value.length()) {
                val step = value.opt(index)
                if (step !is String || step.isBlank() || step.length > MAX_TEXT_CHARS || hasDisallowedControl(step)) {
                    reject(PoseDirectionReject.SCHEMA)
                }
                if (containsPrivate(step)) reject(PoseDirectionReject.PRIVATE_PATH)
                steps += step.trim()
            }
            return steps
        }
        return spokenFallback
            .split("\n", "\r")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseOptionalTitle(item: JSONObject): String? {
        if (!item.has("title")) return null
        val value = item.opt("title")
        if (value !is String || value.isBlank() || value.length > 64 || hasDisallowedControl(value)) {
            reject(PoseDirectionReject.SCHEMA)
        }
        if (containsPrivate(value)) reject(PoseDirectionReject.PRIVATE_PATH)
        return value
    }

    private fun parseOptionalReferenceImage(item: JSONObject): String? {
        if (!item.has("reference_image")) return null
        val value = item.opt("reference_image")
        if (value !is String || value.isBlank()) reject(PoseDirectionReject.SCHEMA)
        // Basename only, same public hash pattern as id — no extension, no path separators.
        if (!ITEM_ID.matches(value)) {
            if (containsPrivate(value) || value.any { it == '/' || it == '\\' || it == ':' || it == '.' }) {
                reject(PoseDirectionReject.PRIVATE_PATH)
            }
            reject(PoseDirectionReject.SCHEMA)
        }
        return value
    }

    private fun parseFigure(figure: JSONObject): Pair<String?, String?> {
        val keys = figure.keySet()
        if (keys.isEmpty()) reject(PoseDirectionReject.MISSING_FIGURE)
        if (!figureKeys.containsAll(keys)) reject(PoseDirectionReject.SCHEMA)
        var svg: String? = null
        var svgFile: String? = null
        if ("svg" in keys) {
            val value = figure.opt("svg")
            if (value !is String || value.isBlank()) reject(PoseDirectionReject.MISSING_FIGURE)
            when (val problem = svgProblem(value)) {
                null -> svg = value
                else -> reject(problem)
            }
        }
        if ("svg_file" in keys) {
            val value = figure.opt("svg_file")
            if (value !is String || value.isBlank()) reject(PoseDirectionReject.MISSING_FIGURE)
            if (!SVG_FILE.matches(value)) {
                if (containsPrivate(value) || value.any { it == '/' || it == '\\' || it == ':' }) {
                    reject(PoseDirectionReject.PRIVATE_PATH)
                }
                reject(PoseDirectionReject.SCHEMA)
            }
            svgFile = value
        }
        if (svg == null && svgFile == null) reject(PoseDirectionReject.MISSING_FIGURE)
        return svg to svgFile
    }

    private fun parseWhy(why: JSONObject): PoseWhyItWorks {
        val keys = why.keySet()
        if (!whyKeys.containsAll(keys)) reject(PoseDirectionReject.SCHEMA)
        fun text(name: String): String? {
            if (name !in keys) return null
            val value = why.opt(name)
            if (value !is String || value.isBlank() || value.length > MAX_TEXT_CHARS || hasDisallowedControl(value)) {
                reject(PoseDirectionReject.SCHEMA)
            }
            if (containsPrivate(value)) reject(PoseDirectionReject.PRIVATE_PATH)
            return value
        }
        return PoseWhyItWorks(
            composition = text("composition"),
            light = text("light"),
            gaze = text("gaze"),
            clothing = text("clothing"),
        )
    }

    private fun svgProblem(value: String): PoseDirectionReject? {
        if (value.length > MAX_DOCUMENT_CHARS) return PoseDirectionReject.SCHEMA
        if (value.contains('\\') || Regex("(?i)\\.\\./|\\.\\.\\\\|(?<![a-z])[a-z]:[/\\\\]").containsMatchIn(value)) {
            return PoseDirectionReject.PRIVATE_PATH
        }
        if (Regex("(?i)\\.(?:jpe?g|png|heic|webp|raw|dng|gif|bmp)\\b").containsMatchIn(value)) {
            return PoseDirectionReject.PRIVATE_PATH
        }
        val lower = value.lowercase()
        if (
            "<script" in lower ||
            "<image" in lower ||
            "javascript:" in lower ||
            "file:" in lower ||
            "content:" in lower ||
            "xlink:href" in lower ||
            "href=" in lower
        ) {
            return PoseDirectionReject.PRIVATE_PATH
        }
        if ("<svg" !in lower || "</svg>" !in lower) return PoseDirectionReject.MISSING_FIGURE
        val urls = Regex("(?i)[a-z][a-z0-9+.-]*://[^\\s\"']+").findAll(value).map { it.value.lowercase() }
        if (urls.any { it !in ALLOWED_SVG_URLS }) return PoseDirectionReject.PRIVATE_PATH
        return null
    }

    /** Allow newline step separators; reject other ISO controls. */
    private fun hasDisallowedControl(value: String): Boolean =
        value.any { ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' }

    private fun containsPrivate(value: String): Boolean = PRIVATE_PATH.containsMatchIn(value)

    private fun failure(reason: PoseDirectionReject) = PoseDirectionParseResult.Failure(reason)

    private fun reject(reason: PoseDirectionReject): Nothing = throw Rejected(reason)

    private class Rejected(val reason: PoseDirectionReject) : RuntimeException()
}

private fun JSONObject.keySet(): Set<String> {
    val names = mutableSetOf<String>()
    val iterator = keys()
    while (iterator.hasNext()) names += iterator.next()
    return names
}