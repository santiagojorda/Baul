package com.santiagojorda.mediasync.data.local

import com.santiagojorda.mediasync.data.local.entity.UploadLogEntity
import com.santiagojorda.mediasync.domain.model.UploadLogEntry

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
)
