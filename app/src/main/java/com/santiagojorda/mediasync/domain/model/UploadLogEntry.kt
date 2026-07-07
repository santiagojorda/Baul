package com.santiagojorda.mediasync.domain.model

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
)
