package com.santiagojorda.baul.data.local

import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.domain.model.UploadLogEntry
import com.santiagojorda.baul.domain.model.UploadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadLogMapperTest {

    @Test
    fun `toDomain preserva todos los campos de la entidad`() {
        val entity = UploadLogEntity(
            id = 1,
            ruleId = 2,
            mediaUri = "content://media/1",
            fileName = "IMG_0001.jpg",
            status = UploadStatus.FAILED,
            errorMessage = "network error",
            remoteId = "remote-1",
            attemptCount = 3,
            createdAt = 1000,
            updatedAt = 2000,
            sourceDeleted = true,
            bytesUploaded = 500,
            totalBytes = 1000,
        )

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.ruleId, domain.ruleId)
        assertEquals(entity.mediaUri, domain.mediaUri)
        assertEquals(entity.fileName, domain.fileName)
        assertEquals(entity.status, domain.status)
        assertEquals(entity.errorMessage, domain.errorMessage)
        assertEquals(entity.remoteId, domain.remoteId)
        assertEquals(entity.attemptCount, domain.attemptCount)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.updatedAt, domain.updatedAt)
        assertEquals(entity.sourceDeleted, domain.sourceDeleted)
        assertEquals(entity.bytesUploaded, domain.bytesUploaded)
        assertEquals(entity.totalBytes, domain.totalBytes)
    }

    @Test
    fun `toEntity preserva todos los campos del dominio`() {
        val entry = UploadLogEntry(
            id = 1,
            ruleId = 2,
            mediaUri = "content://media/1",
            fileName = "IMG_0001.jpg",
            status = UploadStatus.PENDING,
            errorMessage = null,
            remoteId = null,
            attemptCount = 0,
            createdAt = 1000,
            updatedAt = 1000,
            sourceDeleted = false,
            bytesUploaded = 0,
            totalBytes = 0,
        )

        val entity = entry.toEntity()

        assertEquals(entry.id, entity.id)
        assertEquals(entry.ruleId, entity.ruleId)
        assertEquals(entry.mediaUri, entity.mediaUri)
        assertEquals(entry.fileName, entity.fileName)
        assertEquals(entry.status, entity.status)
        assertEquals(entry.errorMessage, entity.errorMessage)
        assertEquals(entry.remoteId, entity.remoteId)
        assertEquals(entry.attemptCount, entity.attemptCount)
        assertEquals(entry.createdAt, entity.createdAt)
        assertEquals(entry.updatedAt, entity.updatedAt)
        assertEquals(entry.sourceDeleted, entity.sourceDeleted)
        assertEquals(entry.bytesUploaded, entity.bytesUploaded)
        assertEquals(entry.totalBytes, entity.totalBytes)
    }

    @Test
    fun `round trip toEntity-toDomain es la identidad`() {
        val entry = UploadLogEntry(
            id = 5,
            ruleId = 9,
            mediaUri = "content://media/2",
            fileName = "VID_0002.mp4",
            status = UploadStatus.SUCCESS,
            remoteId = "yt-abc123",
            createdAt = 1000,
            updatedAt = 2000,
        )

        assertEquals(entry, entry.toEntity().toDomain())
    }
}
