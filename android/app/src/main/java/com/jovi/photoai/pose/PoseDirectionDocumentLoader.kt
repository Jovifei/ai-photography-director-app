package com.jovi.photoai.pose

import java.io.File

/**
 * Fail-closed optional external document path for local developer use.
 * Do not hardcode private drive paths into committed defaults.
 * Private photo identities are still rejected by [PoseDirectionBundleParser].
 */
internal object PoseDirectionDocumentLoader {
    const val MAX_EXTERNAL_CHARS = 400_000

    fun readExternalOrNull(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching {
            val text = file.readText(Charsets.UTF_8)
            when {
                text.isBlank() -> null
                text.length > MAX_EXTERNAL_CHARS -> null
                else -> text
            }
        }.getOrNull()
    }

    /** Prefer an explicit external path when readable; otherwise use the shipped sample/asset text. */
    fun resolveDocument(assetText: String?, externalPath: String? = null): String? {
        return readExternalOrNull(externalPath) ?: assetText?.takeIf { it.isNotBlank() }
    }
}
