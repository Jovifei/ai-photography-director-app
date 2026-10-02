package com.jovi.photoai.ui.reference

import com.jovi.photoai.reference.DirectorCard
import java.security.MessageDigest

internal enum class PreparationStep(val bit: Int) {
    LOCATION(1), ENVIRONMENT(2), SUBJECT(4), EMOTION(8), CAMERA(16),
}

/** UI-only user checklist. It is never used to set READY, certify safety or authorize capture. */
internal data class DirectorPreparation(val scope: String, val completed: Int = 0) {
    fun maskFor(currentScope: String): Int = if (scope == currentScope) completed and 31 else 0

    fun toggle(currentScope: String, step: PreparationStep): DirectorPreparation =
        DirectorPreparation(currentScope, maskFor(currentScope) xor step.bit)

    fun countFor(currentScope: String): Int = Integer.bitCount(maskFor(currentScope))
}

/** Bound to the visible content and source; only this digest and five bits enter saved state. */
internal fun directorPreparationScope(card: DirectorCard, sourceLabel: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (text in listOf(sourceLabel, card.environment, card.subject, card.emotion, card.camera)) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        digest.update(':'.code.toByte())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal const val DIRECTOR_REFERENCE_NOTICE =
    "以下建议来自当前参考内容，来源见上方；不是实时环境识别或人体姿态检测。勾选只记录你的准备，不代表 AI 已核验现场。"
