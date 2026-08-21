package com.jovi.photoai.data.reference

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONException
import org.json.JSONObject

internal const val PHOTO_KNOWLEDGE_BUNDLE_VERSION = "1.0"
internal const val MAX_KNOWLEDGE_BUNDLE_BYTES = 512 * 1024

internal enum class KnowledgeBundleOrigin { LOCAL_SERVICE, PIPELINE }

internal data class KnowledgeBundleSource(
    val origin: KnowledgeBundleOrigin,
    val producerId: String,
    val releaseId: String,
)

internal data class KnowledgeBundlePhotography(
    val scene: String,
    val backgroundStory: String,
    val lighting: String,
    val composition: String,
    val subjectIntent: String,
    val emotion: String,
    val poseTemplate: String,
    val cameraPosition: String,
    val directorPrompt: String,
)

internal data class PhotoKnowledgeBundleItem(
    val referenceId: String,
    val photography: KnowledgeBundlePhotography,
)

internal data class PhotoKnowledgeBundle(
    val contractVersion: String,
    val bundleId: String,
    val source: KnowledgeBundleSource,
    val payloadSha256: String,
    val references: List<PhotoKnowledgeBundleItem>,
)

/** Provenance for a user-selected, integrity-checked offline bundle; it makes no model claim. */
internal data class KnowledgeBundleProvenance(
    val bundleId: String,
    val producerReferenceId: String,
    val producerId: String,
    val origin: KnowledgeBundleOrigin,
    val releaseId: String,
    val payloadSha256: String,
    val importedAtEpochMillis: Long,
) {
    init {
        require(listOf(bundleId, producerReferenceId, producerId, releaseId).all(::isOpaqueIdentifier))
        require(payloadSha256.matches(SHA256_REGEX))
        require(importedAtEpochMillis >= 0)
    }
}

internal enum class PhotoKnowledgeBundleErrorCode {
    DOCUMENT_NOT_SELECTED,
    DOCUMENT_UNAVAILABLE,
    DOCUMENT_EMPTY,
    DOCUMENT_TOO_LARGE,
    INVALID_UTF8,
    MALFORMED_JSON,
    SCHEMA_INVALID,
    VERSION_UNSUPPORTED,
    FIELD_INVALID,
    DUPLICATE_REFERENCE_ID,
    DIGEST_MISMATCH,
}

internal sealed interface PhotoKnowledgeBundleParseResult {
    data class Success(val bundle: PhotoKnowledgeBundle) : PhotoKnowledgeBundleParseResult
    data class Failure(val code: PhotoKnowledgeBundleErrorCode) : PhotoKnowledgeBundleParseResult
}

internal data class KnowledgeBundleBinding(
    val producerReferenceId: String,
    val localReferenceId: String,
)

internal enum class KnowledgeBundleApplyErrorCode {
    BUNDLE_INTEGRITY_INVALID,
    PROJECT_NOT_FOUND,
    BINDING_INCOMPLETE,
    BINDING_DUPLICATE,
    REFERENCE_NOT_FOUND,
    REFERENCE_NOT_ELIGIBLE,
    DATABASE_COMMIT_FAILED,
}

internal sealed interface KnowledgeBundleApplyResult {
    data class Success(val appliedCount: Int) : KnowledgeBundleApplyResult
    data class Failure(val code: KnowledgeBundleApplyErrorCode) : KnowledgeBundleApplyResult
}

internal object PhotoKnowledgeBundleParser {
    private val rootKeys = setOf("contract_version", "bundle_id", "source", "integrity", "references")
    private val sourceKeys = setOf("origin", "producer_id", "release_id")
    private val integrityKeys = setOf("algorithm", "payload_sha256")
    private val itemKeys = setOf("reference_id", "photography")
    private val photographyKeys = setOf(
        "scene",
        "background_story",
        "lighting",
        "composition",
        "subject_intent",
        "emotion",
        "pose_template",
        "camera_position",
        "director_prompt",
    )

    fun parse(bytes: ByteArray): PhotoKnowledgeBundleParseResult {
        if (bytes.isEmpty()) return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_EMPTY)
        if (bytes.size > MAX_KNOWLEDGE_BUNDLE_BYTES) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_TOO_LARGE)
        }
        if (bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.INVALID_UTF8)
        }
        val json = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.INVALID_UTF8)
        }
        if (json.isBlank()) return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_EMPTY)
        if (hasDuplicateObjectKeys(json)) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID)
        }

        return try {
            val root = JSONObject(json)
            if (!root.hasExactKeys(rootKeys)) return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID)
            val version = root.strictString("contract_version")
            if (version != PHOTO_KNOWLEDGE_BUNDLE_VERSION) {
                return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.VERSION_UNSUPPORTED)
            }
            val bundleId = root.strictOpaqueId("bundle_id")
            val sourceObject = root.getJSONObject("source")
            if (!sourceObject.hasExactKeys(sourceKeys)) return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID)
            val origin = runCatching { KnowledgeBundleOrigin.valueOf(sourceObject.strictString("origin")) }
                .getOrElse { return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.FIELD_INVALID) }
            val source = KnowledgeBundleSource(
                origin = origin,
                producerId = sourceObject.strictOpaqueId("producer_id"),
                releaseId = sourceObject.strictOpaqueId("release_id"),
            )
            val integrity = root.getJSONObject("integrity")
            if (!integrity.hasExactKeys(integrityKeys) || integrity.strictString("algorithm") != "SHA-256") {
                return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID)
            }
            val declaredDigest = integrity.strictString("payload_sha256").lowercase()
            if (!declaredDigest.matches(SHA256_REGEX)) {
                return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.FIELD_INVALID)
            }
            val array = root.getJSONArray("references")
            if (array.length() !in 1..MAX_PROJECT_PHOTOS) {
                return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID)
            }
            val references = (0 until array.length()).map { index ->
                val itemObject = array.getJSONObject(index)
                if (!itemObject.hasExactKeys(itemKeys)) throw KnowledgeBundleSchemaException()
                val photographyObject = itemObject.getJSONObject("photography")
                if (!photographyObject.hasExactKeys(photographyKeys)) throw KnowledgeBundleSchemaException()
                PhotoKnowledgeBundleItem(
                    referenceId = itemObject.strictOpaqueId("reference_id"),
                    photography = KnowledgeBundlePhotography(
                        scene = photographyObject.strictSafeText("scene"),
                        backgroundStory = photographyObject.strictSafeText("background_story"),
                        lighting = photographyObject.strictSafeText("lighting"),
                        composition = photographyObject.strictSafeText("composition"),
                        subjectIntent = photographyObject.strictSafeText("subject_intent"),
                        emotion = photographyObject.strictSafeText("emotion"),
                        poseTemplate = photographyObject.strictSafeText("pose_template"),
                        cameraPosition = photographyObject.strictSafeText("camera_position"),
                        directorPrompt = photographyObject.strictSafeText("director_prompt", MAX_DIRECTOR_PROMPT_CODE_POINTS),
                    ),
                )
            }
            if (references.map(PhotoKnowledgeBundleItem::referenceId).distinct().size != references.size) {
                return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DUPLICATE_REFERENCE_ID)
            }
            val bundle = PhotoKnowledgeBundle(version, bundleId, source, declaredDigest, references)
            if (!MessageDigest.isEqual(declaredDigest.hexBytes(), canonicalPayloadSha256(bundle).hexBytes())) {
                return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DIGEST_MISMATCH)
            }
            PhotoKnowledgeBundleParseResult.Success(bundle)
        } catch (_: KnowledgeBundleFieldException) {
            PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.FIELD_INVALID)
        } catch (_: KnowledgeBundleSchemaException) {
            PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID)
        } catch (_: JSONException) {
            PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.MALFORMED_JSON)
        } catch (_: Exception) {
            PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID)
        }
    }
}

/**
 * PKB1 canonical bytes: ASCII header, then ordered name/value pairs. Each UTF-8 token is encoded
 * as its decimal byte length, ':', raw bytes, and '\n'. Reference array order is preserved.
 */
internal fun canonicalPayloadBytes(bundle: PhotoKnowledgeBundle): ByteArray {
    val output = ByteArrayOutputStream()
    output.write("PKB1\n".toByteArray(StandardCharsets.US_ASCII))
    fun append(name: String, value: String) {
        listOf(name, value).forEach { token ->
            val bytes = token.toByteArray(StandardCharsets.UTF_8)
            output.write(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
            output.write(':'.code)
            output.write(bytes)
            output.write('\n'.code)
        }
    }
    append("contract_version", bundle.contractVersion)
    append("bundle_id", bundle.bundleId)
    append("source.origin", bundle.source.origin.name)
    append("source.producer_id", bundle.source.producerId)
    append("source.release_id", bundle.source.releaseId)
    append("references.count", bundle.references.size.toString())
    bundle.references.forEachIndexed { index, item ->
        val prefix = "references[$index]"
        append("$prefix.reference_id", item.referenceId)
        val photo = item.photography
        append("$prefix.photography.scene", photo.scene)
        append("$prefix.photography.background_story", photo.backgroundStory)
        append("$prefix.photography.lighting", photo.lighting)
        append("$prefix.photography.composition", photo.composition)
        append("$prefix.photography.subject_intent", photo.subjectIntent)
        append("$prefix.photography.emotion", photo.emotion)
        append("$prefix.photography.pose_template", photo.poseTemplate)
        append("$prefix.photography.camera_position", photo.cameraPosition)
        append("$prefix.photography.director_prompt", photo.directorPrompt)
    }
    return output.toByteArray()
}

internal fun canonicalPayloadSha256(bundle: PhotoKnowledgeBundle): String = MessageDigest.getInstance("SHA-256")
    .digest(canonicalPayloadBytes(bundle))
    .joinToString("") { "%02x".format(it) }

internal fun isKnowledgeBundleTargetEligible(status: PhotoAnalysisStatus): Boolean = status in setOf(
    PhotoAnalysisStatus.EXAMPLE_GUIDANCE,
    PhotoAnalysisStatus.IMPORTED,
    PhotoAnalysisStatus.FAILED,
    PhotoAnalysisStatus.CANCELLED,
    PhotoAnalysisStatus.UNAVAILABLE,
)

private fun JSONObject.hasExactKeys(expected: Set<String>): Boolean {
    val actual = mutableSetOf<String>()
    val iterator = keys()
    while (iterator.hasNext()) actual += iterator.next()
    return actual == expected
}

/** JSONObject otherwise accepts a later duplicate value; reject duplicate keys before parsing. */
private fun hasDuplicateObjectKeys(json: String): Boolean {
    val containers = mutableListOf<MutableSet<String>?>()
    var index = 0
    while (index < json.length) {
        when (json[index]) {
            '{' -> containers.add(mutableSetOf())
            '[' -> containers.add(null)
            '}', ']' -> if (containers.isNotEmpty()) containers.removeAt(containers.lastIndex)
            '"' -> {
                val start = index
                index += 1
                var escaped = false
                while (index < json.length) {
                    val character = json[index]
                    if (!escaped && character == '"') break
                    escaped = !escaped && character == '\\'
                    if (character != '\\') escaped = false
                    index += 1
                }
                if (index >= json.length) return false
                var next = index + 1
                while (next < json.length && json[next].isWhitespace()) next += 1
                if (next < json.length && json[next] == ':' && containers.lastOrNull() != null) {
                    val rawKey = json.substring(start, index + 1)
                    val decodedKey = runCatching {
                        val wrapper = JSONObject("{$rawKey:null}")
                        wrapper.keys().next()
                    }.getOrNull() ?: return false
                    if (!containers.last()!!.add(decodedKey)) return true
                }
            }
        }
        index += 1
    }
    return false
}

private fun JSONObject.strictString(name: String): String {
    val value = get(name)
    if (value !is String) throw KnowledgeBundleFieldException()
    return value
}

private fun JSONObject.strictOpaqueId(name: String): String = strictString(name).also {
    if (!isOpaqueIdentifier(it)) throw KnowledgeBundleFieldException()
}

private fun JSONObject.strictSafeText(name: String, maxCodePoints: Int = MAX_TEXT_CODE_POINTS): String = strictString(name).also { value ->
    val codePoints = value.codePointCount(0, value.length)
    if (value.isBlank() || codePoints > maxCodePoints || value.any(Character::isISOControl) || UNSAFE_TRANSPORT_TEXT.containsMatchIn(value)) {
        throw KnowledgeBundleFieldException()
    }
}

private fun isOpaqueIdentifier(value: String): Boolean = value.matches(OPAQUE_ID_REGEX)

private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private class KnowledgeBundleFieldException : RuntimeException()
private class KnowledgeBundleSchemaException : RuntimeException()

private const val MAX_TEXT_CODE_POINTS = 800
private const val MAX_DIRECTOR_PROMPT_CODE_POINTS = 1_200
private val OPAQUE_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
private val SHA256_REGEX = Regex("^[A-Fa-f0-9]{64}$")
private val UNSAFE_TRANSPORT_TEXT = Regex(
    pattern = "(?i)(?:[a-z][a-z0-9+.-]*://|(?:content|file):|(?:^|\\s)[a-z]:[\\\\/]|/sdcard(?:/|$)|/storage(?:/|$)|\\.\\.[\\\\/])",
)
