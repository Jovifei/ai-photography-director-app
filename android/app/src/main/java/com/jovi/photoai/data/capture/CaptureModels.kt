package com.jovi.photoai.data.capture

/** Captured outputs are not reference analyses. None of these states grants AI READY. */
internal enum class CaptureFileState { CAPTURING, AVAILABLE, INTERRUPTED, UNAVAILABLE, DELETING }
internal enum class CaptureExportState { NEVER, SELECTING, WRITING, SAVED, CANCELLED, FAILED, UNKNOWN }
internal enum class ExportPhase { RESERVED, SELECTING, WRITING, INTERRUPTED }

internal data class CaptureRecord(
    val id: String,
    val projectId: String?,
    val referenceId: String?,
    val createdAtMillis: Long,
    val fileState: CaptureFileState = CaptureFileState.CAPTURING,
    val byteCount: Long = 0,
    val fileSha256: String? = null,
    val exportState: CaptureExportState = CaptureExportState.NEVER,
    val lastExportedAtMillis: Long? = null,
)

/** The activity-result registry key contains token, never just "latest capture". */
internal data class CaptureExportTicket(
    val token: String,
    val captureId: String,
    val processId: String,
    val phase: ExportPhase = ExportPhase.RESERVED,
)

internal class CaptureProblem(val code: String) : Exception(code)
internal data class CaptureFileDigest(val bytes: Long, val sha256: String)

/** Synchronous, IO-thread-only port. Transactions must roll back on thrown exceptions. */
internal interface CaptureLedger {
    fun all(): List<CaptureRecord>
    fun read(id: String): CaptureRecord?
    fun insert(record: CaptureRecord)
    fun update(record: CaptureRecord)
    fun remove(id: String)
    fun pendingExport(): CaptureExportTicket?
    fun setPendingExport(ticket: CaptureExportTicket)
    fun clearPendingExport()
    fun <T> transaction(block: () -> T): T
}

internal fun captureExportMessage(record: CaptureRecord): String = when (record.exportState) {
    CaptureExportState.NEVER -> "已存入应用，尚未保存外部副本"
    CaptureExportState.SELECTING -> "等待选择保存位置，应用内原片保留"
    CaptureExportState.WRITING -> "正在保存副本，应用内原片保留"
    CaptureExportState.SAVED -> "已保存副本；应用不跟踪外部文件后续变化"
    CaptureExportState.CANCELLED -> "已取消另存为，应用内原片保留"
    CaptureExportState.FAILED -> "未能开始保存，应用内原片保留"
    CaptureExportState.UNKNOWN -> "保存结果待确认，请检查外部位置；应用内原片保留"
}
