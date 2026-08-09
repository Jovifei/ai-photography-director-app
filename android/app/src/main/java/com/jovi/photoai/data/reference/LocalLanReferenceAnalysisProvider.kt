package com.jovi.photoai.data.reference

import android.net.Uri
import android.util.Base64
import com.jovi.photoai.reference.ReferenceBundle
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager
import java.util.concurrent.TimeUnit

internal data class LocalLanAnalysisConnection(
    val baseUrl: String,
    val accessToken: String,
    val certificatePin: String,
) {
    init {
        val parsed = Uri.parse(baseUrl)
        require(parsed.scheme == "https" && !parsed.host.isNullOrBlank())
        require(accessToken.isNotBlank() && accessToken.length <= 256)
        require(certificatePin.startsWith("sha256/") && certificatePin.length > 16)
    }
}

internal object LocalLanPairingClient {
    suspend fun pair(
        baseUrl: String,
        pairingCode: String,
        certificatePin: String,
    ): Result<LocalLanAnalysisConnection> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
            val parsed = Uri.parse(normalizedBaseUrl)
            parsed.host ?: error("PAIRING_HOST_MISSING")
            val client = createPinnedOkHttpClient(
                LocalLanAnalysisConnection(
                    baseUrl = normalizedBaseUrl,
                    accessToken = "pairing",
                    certificatePin = certificatePin,
                ),
            )
            val request = Request.Builder()
                .url("$normalizedBaseUrl/v1/pair")
                .header("Content-Type", "application/json")
                .post(JSONObject(mapOf("pairing_code" to pairingCode)).toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("PAIRING_REJECTED")
                val token = JSONObject(response.body?.string().orEmpty()).getString("access_token")
                LocalLanAnalysisConnection(normalizedBaseUrl, token, certificatePin)
            }
        }
    }
}

/** Android-side adapter. It sends only the already-sanitized App-private JPEG. */
internal class LocalLanReferenceAnalysisProvider(
    private val repository: ReferenceRepository,
    private val connection: LocalLanAnalysisConnection,
    private val client: OkHttpClient = createClient(connection),
) : ReferenceAnalysisProvider {
    override suspend fun analyze(request: ReferenceAnalysisRequest): ProviderAnalysisResult = withContext(Dispatchers.IO) {
        val input = repository.privateAnalysisInput(request.referenceId)
            ?: return@withContext ProviderAnalysisResult.Unavailable(SafeProviderErrorCode.REFERENCE_URI_UNAVAILABLE)
        val endpoint = Uri.parse(connection.baseUrl).buildUpon()
            .appendPath("v1")
            .appendPath("analyze")
            .appendPath(request.referenceId)
            .build()
        val httpRequest = Request.Builder()
            .url(endpoint.toString())
            .header("Authorization", "Bearer ${connection.accessToken}")
            .header("Accept", "application/json")
            .put(input.jpegBytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        runCatching {
            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use ProviderAnalysisResult.Failed(
                    SafeProviderErrorCode.PROVIDER_OUTPUT_EMPTY,
                    retryable = response.code >= 500,
                )
                parseEnvelope(request, body)
            }
        }.getOrElse {
            ProviderAnalysisResult.Unavailable(SafeProviderErrorCode.PROVIDER_UNAVAILABLE)
        }
    }

    private fun parseEnvelope(request: ReferenceAnalysisRequest, body: String): ProviderAnalysisResult {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return ProviderAnalysisResult.Failed(SafeProviderErrorCode.PROVIDER_OUTPUT_MALFORMED, false)
        return when (root.optString("status")) {
            "SUCCESS" -> parseReady(request, root)
            "FAILED" -> ProviderAnalysisResult.Failed(
                errorCode = safeError(root.optJSONObject("error")?.optString("code")),
                retryable = root.optBoolean("retryable", false),
            )
            "CANCELLED" -> ProviderAnalysisResult.Failed(SafeProviderErrorCode.PROVIDER_UNAVAILABLE, false)
            else -> ProviderAnalysisResult.Failed(SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID, false)
        }
    }

    private fun parseReady(request: ReferenceAnalysisRequest, root: JSONObject): ProviderAnalysisResult {
        val bundle = root.optJSONObject("bundle") ?: return ProviderAnalysisResult.Failed(
            SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID,
            false,
        )
        val parsedBundle = runCatching {
            ReferenceBundle(
                referenceId = bundle.getString("reference_id"),
                scene = bundle.getString("scene"),
                backgroundStory = bundle.getString("background_story"),
                lighting = bundle.getString("lighting"),
                composition = bundle.getString("composition"),
                subjectIntent = bundle.getString("subject_intent"),
                emotion = bundle.getString("emotion"),
                poseTemplate = bundle.getString("pose_template"),
                cameraPosition = bundle.getString("camera_position"),
                directorPrompt = bundle.getString("director_prompt"),
                version = bundle.getString("version"),
            )
        }.getOrNull() ?: return ProviderAnalysisResult.Failed(SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID, false)
        val provenance = root.optJSONObject("provenance")
        val providerId = root.optString("provider_id")
        val modelId = root.optString("model_id")
        val modelRevision = root.optString("model_revision")
        val artifactSha = root.optString("model_artifact_sha256")
        val runtimeId = root.optString("runtime_id")
        val startedAt = parseUtc(root.optString("started_at_utc"))
        val completedAt = parseUtc(root.optString("completed_at_utc"))
        if (provenance == null || providerId.isBlank() || modelId.isBlank() || modelRevision.isBlank() ||
            artifactSha.length != 64 || runtimeId.isBlank() || startedAt == null || completedAt == null
        ) return ProviderAnalysisResult.Failed(SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID, false)
        return ProviderAnalysisResult.Ready(
            bundle = parsedBundle,
            provenance = ProviderAnalysisProvenance(
                providerId = providerId,
                providerType = ProviderType.valueOf(root.optString("provider_type")),
                modelId = modelId,
                modelRevision = modelRevision,
                modelArtifactSha256 = artifactSha,
                runtimeId = runtimeId,
                startedAtEpochMillis = startedAt,
                completedAtEpochMillis = completedAt,
                latencyMillis = root.optLong("latency_ms", (completedAt - startedAt).coerceAtLeast(0)),
                confidenceSummary = root.optJSONObject("confidence_summary").toConfidenceSummary(),
                uncertaintyFlags = root.optJSONObject("uncertainty_flags").toUncertaintyFlags(),
                warnings = root.optJSONArray("warnings").toStringList(),
            ),
        ).validatedFor(request)
    }

    private companion object {
        fun createClient(connection: LocalLanAnalysisConnection): OkHttpClient {
            return createPinnedOkHttpClient(connection)
        }

        fun safeError(value: String?): SafeProviderErrorCode = runCatching {
            SafeProviderErrorCode.valueOf(value.orEmpty())
        }.getOrDefault(SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID)

        fun parseUtc(value: String): Long? = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value)?.time
        }.getOrNull()

        fun JSONObject?.toConfidenceSummary(): AnalysisConfidenceSummary = this?.let {
            AnalysisConfidenceSummary(
                directObservationFields = it.optJSONArray("direct_observation_fields").toStringList(),
                photographicInterpretationFields = it.optJSONArray("photographic_interpretation_fields").toStringList(),
                creativeRecommendationFields = it.optJSONArray("creative_recommendation_fields").toStringList(),
            )
        } ?: AnalysisConfidenceSummary()

        fun JSONObject?.toUncertaintyFlags(): Map<String, AnalysisUncertainty> {
            if (this == null) return emptyMap()
            return keys().asSequence().mapNotNull { key ->
                val value = optJSONObject(key) ?: return@mapNotNull null
                val level = runCatching { UncertaintyLevel.valueOf(value.optString("level")) }.getOrNull() ?: return@mapNotNull null
                val basis = runCatching { UncertaintyBasis.valueOf(value.optString("basis")) }.getOrNull() ?: return@mapNotNull null
                key to AnalysisUncertainty(level, basis)
            }.toMap()
        }

        fun org.json.JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else {
            (0 until length()).mapNotNull { index -> optString(index).takeIf(String::isNotBlank) }
        }
    }
}

internal class LocalLanProjectSummaryProvider(
    private val connection: LocalLanAnalysisConnection,
    private val client: OkHttpClient = createPinnedOkHttpClient(connection),
) : ProjectSummaryProvider {
    override suspend fun summarize(request: ProjectSummaryRequest): ProjectSummaryResult = withContext(Dispatchers.IO) {
        val endpoint = Uri.parse(connection.baseUrl).buildUpon()
            .appendPath("v1")
            .appendPath("summarize")
            .build()
        val readyItems = JSONArray(request.readyItems.map { input ->
            JSONObject().put("reference_id", input.referenceId).put("bundle", JSONObject().apply {
                put("reference_id", input.bundle.referenceId)
                put("scene", input.bundle.scene)
                put("background_story", input.bundle.backgroundStory)
                put("lighting", input.bundle.lighting)
                put("composition", input.bundle.composition)
                put("subject_intent", input.bundle.subjectIntent)
                put("emotion", input.bundle.emotion)
                put("pose_template", input.bundle.poseTemplate)
                put("camera_position", input.bundle.cameraPosition)
                put("director_prompt", input.bundle.directorPrompt)
                put("version", input.bundle.version)
            })
        }).toString()
        val body = JSONObject()
            .put("project_id", request.projectId)
            .put("ready_items", JSONArray(readyItems))
            .put("failed_count", request.failedCount)
            .toString()
        val httpRequest = Request.Builder()
            .url(endpoint.toString())
            .header("Authorization", "Bearer ${connection.accessToken}")
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(httpRequest).execute().use { response ->
                parseSummaryResponse(request, response.body?.string().orEmpty())
            }
        }.getOrElse { ProjectSummaryResult.Failed(SafeProviderErrorCode.PROVIDER_UNAVAILABLE) }
    }

    private fun parseSummaryResponse(request: ProjectSummaryRequest, body: String): ProjectSummaryResult {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return ProjectSummaryResult.Failed(SafeProviderErrorCode.PROVIDER_OUTPUT_MALFORMED)
        if (root.optString("status") != "SUCCESS") {
            return ProjectSummaryResult.Failed(safeError(root.optJSONObject("error")?.optString("code")))
        }
        val summary = root.optJSONObject("summary")
            ?: return ProjectSummaryResult.Failed(SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID)
        val readyIds = request.readyItems.map { it.referenceId }.toSet()
        val recommended = summary.optString("recommended_primary_reference")
        val strongest = summary.optJSONArray("strongest_references")
        val strongestReferences = if (strongest == null) emptyList() else (0 until strongest.length()).mapNotNull { index ->
            strongest.optJSONObject(index)?.let { item ->
                val id = item.optString("reference_id")
                val reason = item.optString("reason")
                if (id in readyIds && reason.isNotBlank()) StrongestReference(id, reason) else null
            }
        }
        val commonFields = listOf(
            "common_scene_direction", "common_lighting_direction", "common_composition_direction",
            "common_subject_direction", "photographer_action_summary",
        )
        if (commonFields.any { summary.optString(it).isBlank() } || recommended !in readyIds || strongestReferences.size > 3) {
            return ProjectSummaryResult.Failed(SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID)
        }
        val differencesArray = summary.optJSONArray("differences")
        val differences = if (differencesArray == null) emptyList() else (0 until differencesArray.length()).mapNotNull {
            differencesArray.optString(it).takeIf(String::isNotBlank)
        }
        return ProjectSummaryResult.Ready(
            PersistedProjectSummary(
                projectId = request.projectId,
                status = ProjectSummaryStatus.SUCCESS,
                readyCount = request.readyItems.size,
                failedCount = request.failedCount,
                inputDigest = request.inputDigest,
                summaryVersion = root.optString("summary_version").takeIf(String::isNotBlank),
                modelId = root.optString("model_id").takeIf(String::isNotBlank),
                modelRevision = root.optString("model_revision").takeIf(String::isNotBlank),
                modelArtifactSha256 = root.optString("model_artifact_sha256").takeIf { it.length == 64 },
                runtimeId = root.optString("runtime_id").takeIf(String::isNotBlank),
                commonSceneDirection = summary.optString("common_scene_direction"),
                commonLightingDirection = summary.optString("common_lighting_direction"),
                commonCompositionDirection = summary.optString("common_composition_direction"),
                commonSubjectDirection = summary.optString("common_subject_direction"),
                differences = differences,
                strongestReferences = strongestReferences,
                recommendedPrimaryReference = recommended,
                photographerActionSummary = summary.optString("photographer_action_summary"),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        fun safeError(value: String?): SafeProviderErrorCode = runCatching {
            SafeProviderErrorCode.valueOf(value.orEmpty())
        }.getOrDefault(SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID)
    }
}

private fun createPinnedOkHttpClient(connection: LocalLanAnalysisConnection): OkHttpClient {
    val host = Uri.parse(connection.baseUrl).host ?: error("LOCAL_SERVICE_HOST_MISSING")
    val expectedPin = connection.certificatePin.removePrefix("sha256/")
    require(runCatching { Base64.decode(expectedPin, Base64.DEFAULT) }.getOrNull()?.size == 32) { "CERTIFICATE_PIN_INVALID" }
    val trustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = validate(chain)

        private fun validate(chain: Array<X509Certificate>) {
            val certificate = chain.firstOrNull() ?: throw java.security.cert.CertificateException("CERTIFICATE_CHAIN_EMPTY")
            certificate.checkValidity()
            val actualPin = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded),
                Base64.NO_WRAP,
            )
            if (!MessageDigest.isEqual(actualPin.toByteArray(Charsets.US_ASCII), expectedPin.toByteArray(Charsets.US_ASCII))) {
                throw java.security.cert.CertificateException("CERTIFICATE_PIN_MISMATCH")
            }
            if (!certificateMatchesHost(certificate, host)) {
                throw java.security.cert.CertificateException("CERTIFICATE_HOSTNAME_MISMATCH")
            }
        }
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier(HostnameVerifier { requestedHost: String, session: SSLSession ->
            (session.peerCertificates.firstOrNull() as? X509Certificate)?.let { certificateMatchesHost(it, requestedHost) } == true
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(6, TimeUnit.MINUTES)
        .build()
}

private fun certificateMatchesHost(certificate: X509Certificate, host: String): Boolean {
    val names = runCatching { certificate.subjectAlternativeNames.orEmpty() }.getOrDefault(emptyList())
    return names.any { entry ->
        if (entry.size < 2) return@any false
        val type = entry[0] as? Int ?: return@any false
        val value = entry[1]?.toString() ?: return@any false
        when (type) {
            2 -> value.equals(host, ignoreCase = true) || (value.startsWith("*.") && host.endsWith(value.removePrefix("*"), ignoreCase = true))
            7 -> value == host
            else -> false
        }
    }
}
