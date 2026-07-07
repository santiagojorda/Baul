package com.santiagojorda.baul.domain.model

data class UploadLogEntry(
    val id: Long = 0,
    val ruleId: Long,
    val mediaUri: String,
    val fileName: String,
    val status: UploadStatus,
    val errorMessage: String? = null,
    val remoteId: String? = null,
    val attemptCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceDeleted: Boolean = false,
    val bytesUploaded: Long = 0,
    val totalBytes: Long = 0,
) {
    companion object {
        /** Cuántas veces se reintenta un fallo transitorio antes de darlo por perdido de verdad. */
        const val MAX_RETRY_ATTEMPTS = 5
    }
}
