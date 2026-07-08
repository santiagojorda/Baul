package com.santiagojorda.baul.data.local.dao

import androidx.room.Room
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.UploadStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Contra una base Room real en memoria (no un fake): [UploadLogRepositoryTest] usa un
 * `FakeUploadLogDao` que nunca ejercita el SQL real de estas queries — en particular
 * [UploadLogDao.countPendingDeletions], que depende de un JOIN entre `upload_log` y `rules`.
 */
@RunWith(RobolectricTestRunner::class)
class UploadLogDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: UploadLogDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.uploadLogDao()
    }

    @Test
    fun `pruneOlderThan borra viejos, conserva recientes y respeta el corte mas largo de FAILED`() = runTest {
        seedRule(id = 1)
        val successCutoff = 10_000L
        val failedCutoff = 5_000L
        // vieja -> borrar
        dao.upsert(logEntity(id = 1, status = UploadStatus.SUCCESS, createdAt = successCutoff - 1))
        // vieja -> borrar
        dao.upsert(logEntity(id = 2, status = UploadStatus.CANCELLED, createdAt = successCutoff - 1))
        // reciente -> conservar
        dao.upsert(logEntity(id = 3, status = UploadStatus.SUCCESS, createdAt = successCutoff + 1))
        // pasó el corte de SUCCESS pero no el suyo -> conservar
        dao.upsert(logEntity(id = 4, status = UploadStatus.FAILED, createdAt = successCutoff - 1))
        // pasó su propio corte -> borrar
        dao.upsert(logEntity(id = 5, status = UploadStatus.FAILED, createdAt = failedCutoff - 1))

        val deleted = dao.pruneOlderThan(successCutoff, failedCutoff)

        assertEquals(3, deleted)
        assertEquals(setOf(3L, 4L), dao.getForRule(1).map { it.id }.toSet())
    }

    @Test
    fun `pruneOlderThan nunca borra PENDING ni UPLOADING sin importar la antiguedad`() = runTest {
        seedRule(id = 1)
        dao.upsert(logEntity(id = 1, status = UploadStatus.PENDING, createdAt = 0))
        dao.upsert(logEntity(id = 2, status = UploadStatus.UPLOADING, createdAt = 0))

        val deleted = dao.pruneOlderThan(successAndCancelledCutoff = Long.MAX_VALUE, failedCutoff = Long.MAX_VALUE)

        assertEquals(0, deleted)
        assertEquals(2, dao.getForRule(1).size)
    }

    @Test
    fun `pruneOlderThan es estricto (menor que), no borra una entrada justo en el corte`() = runTest {
        seedRule(id = 1)
        val cutoff = 10_000L
        dao.upsert(logEntity(id = 1, status = UploadStatus.SUCCESS, createdAt = cutoff))

        val deleted = dao.pruneOlderThan(successAndCancelledCutoff = cutoff, failedCutoff = cutoff)

        assertEquals(0, deleted)
        assertEquals(1, dao.getForRule(1).size)
    }

    @Test
    fun `getSuccessfulNotYetDeleted solo trae SUCCESS con sourceDeleted en false`() = runTest {
        seedRule(id = 1)
        dao.upsert(logEntity(id = 1, status = UploadStatus.SUCCESS, createdAt = 0))
        dao.upsert(logEntity(id = 2, status = UploadStatus.FAILED, createdAt = 0))
        dao.markSourceDeleted(listOf(1))
        dao.upsert(logEntity(id = 3, status = UploadStatus.SUCCESS, createdAt = 0))

        val pending = dao.getSuccessfulNotYetDeleted()

        assertEquals(listOf(3L), pending.map { it.id })
    }

    @Test
    fun `countPendingDeletions solo cuenta reglas que piden borrar el original`() = runTest {
        seedRule(id = 1, deleteSourceAfterUpload = true)
        seedRule(id = 2, deleteSourceAfterUpload = false)
        dao.upsert(logEntity(id = 1, ruleId = 1, status = UploadStatus.SUCCESS, createdAt = 0))
        dao.upsert(logEntity(id = 2, ruleId = 2, status = UploadStatus.SUCCESS, createdAt = 0))

        assertEquals(1, dao.countPendingDeletions())
    }

    @Test
    fun `countPendingDeletions no cuenta lo que ya se marco sourceDeleted`() = runTest {
        seedRule(id = 1, deleteSourceAfterUpload = true)
        dao.upsert(logEntity(id = 1, ruleId = 1, status = UploadStatus.SUCCESS, createdAt = 0))

        dao.markSourceDeleted(listOf(1))

        assertEquals(0, dao.countPendingDeletions())
    }

    @Test
    fun `getLogForMedia devuelve el mas reciente cuando hay mas de un intento`() = runTest {
        seedRule(id = 1)
        dao.upsert(logEntity(id = 1, status = UploadStatus.FAILED, createdAt = 1000, mediaUri = "content://media/x"))
        dao.upsert(logEntity(id = 2, status = UploadStatus.SUCCESS, createdAt = 2000, mediaUri = "content://media/x"))

        val log = dao.getLogForMedia(ruleId = 1, mediaUri = "content://media/x")

        assertEquals(2L, log?.id)
    }

    @Test
    fun `countsByStatus agrupa correctamente por status`() = runTest {
        seedRule(id = 1)
        dao.upsert(logEntity(id = 1, status = UploadStatus.SUCCESS, createdAt = 0))
        dao.upsert(logEntity(id = 2, status = UploadStatus.SUCCESS, createdAt = 0))
        dao.upsert(logEntity(id = 3, status = UploadStatus.FAILED, createdAt = 0))

        val counts = dao.countsByStatus().associate { it.status to it.count }

        assertEquals(2, counts[UploadStatus.SUCCESS])
        assertEquals(1, counts[UploadStatus.FAILED])
    }

    @Test
    fun `updateProgress actualiza bytes sin tocar el status`() = runTest {
        seedRule(id = 1)
        dao.upsert(logEntity(id = 1, status = UploadStatus.UPLOADING, createdAt = 0))

        dao.updateProgress(id = 1, bytesUploaded = 512, totalBytes = 1024, updatedAt = 999)

        val log = requireNotNull(dao.getForRule(1).first())
        assertEquals(512, log.bytesUploaded)
        assertEquals(1024, log.totalBytes)
        assertEquals(UploadStatus.UPLOADING, log.status)
    }

    private suspend fun seedRule(id: Long, deleteSourceAfterUpload: Boolean = true) {
        database.ruleDao().upsert(
            RuleEntity(
                id = id,
                folderUri = "content://tree/foo",
                folderDisplayName = "Foo",
                destinationType = DestinationType.GOOGLE_PHOTOS,
                googleAccountEmail = "user@example.com",
                deleteSourceAfterUpload = deleteSourceAfterUpload,
            ),
        )
    }

    private fun logEntity(
        id: Long,
        status: UploadStatus,
        createdAt: Long,
        ruleId: Long = 1,
        mediaUri: String = "content://media/$id",
    ) = UploadLogEntity(
        id = id,
        ruleId = ruleId,
        mediaUri = mediaUri,
        fileName = "IMG_000$id.jpg",
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
