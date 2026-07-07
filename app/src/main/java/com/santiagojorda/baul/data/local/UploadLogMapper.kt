package com.santiagojorda.baul.data.local

import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.domain.model.UploadLogEntry

fun UploadLogEntity.toDomain(): UploadLogEntry = UploadLogEntry(
    id = id,
    ruleId = ruleId,
    mediaUri = mediaUri,
    fileName = fileName,
    status = status,
    errorMessage = errorMessage,
    remoteId = remoteId,
    attemptCount = attemptCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourceDeleted = sourceDeleted,
    bytesUploaded = bytesUploaded,
    totalBytes = totalBytes,
)

fun UploadLogEntry.toEntity(): UploadLogEntity = UploadLogEntity(
    id = id,
    ruleId = ruleId,
    mediaUri = mediaUri,
    fileName = fileName,
    status = status,
    errorMessage = errorMessage,
    remoteId = remoteId,
    attemptCount = attemptCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourceDeleted = sourceDeleted,
    bytesUploaded = bytesUploaded,
    totalBytes = totalBytes,
)
