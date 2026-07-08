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
 * Contra una base Room real en memoria (no un fake), para validar que la query SQL de
 * [UploadLogDao.pruneOlderThan] hace exactamente lo que dice — el resto de los tests de esta
 * lógica (ver UploadLogRepositoryTest) usan un fake y no ejercitan el SQL de verdad.
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

    private suspend fun seedRule(id: Long) {
        database.ruleDao().upsert(
            RuleEntity(
                id = id,
                folderUri = "content://tree/foo",
                folderDisplayName = "Foo",
                destinationType = DestinationType.GOOGLE_PHOTOS,
                googleAccountEmail = "user@example.com",
            ),
        )
    }

    private fun logEntity(id: Long, status: UploadStatus, createdAt: Long) = UploadLogEntity(
        id = id,
        ruleId = 1,
        mediaUri = "content://media/$id",
        fileName = "IMG_000$id.jpg",
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
