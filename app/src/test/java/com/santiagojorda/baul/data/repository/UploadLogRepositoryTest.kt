package com.santiagojorda.baul.data.repository

import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.santiagojorda.baul.data.local.dao.RuleDao
import com.santiagojorda.baul.data.local.dao.StatusCount
import com.santiagojorda.baul.data.local.dao.UploadLogDao
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.data.local.toDomain
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private class FakeUploadLogDao : UploadLogDao {
    val flow = MutableStateFlow<List<UploadLogEntity>>(emptyList())

    override fun observeLogs(): Flow<List<UploadLogEntity>> = flow

    override suspend fun getByStatus(status: UploadStatus): List<UploadLogEntity> =
        flow.value.filter { it.status == status }

    override suspend fun getSuccessfulNotYetDeleted(): List<UploadLogEntity> =
        flow.value.filter { it.status == UploadStatus.SUCCESS && !it.sourceDeleted }

    override suspend fun getFailedForRule(ruleId: Long): List<UploadLogEntity> =
        flow.value.filter { it.ruleId == ruleId && it.status == UploadStatus.FAILED }

    override suspend fun getForRule(ruleId: Long): List<UploadLogEntity> =
        flow.value.filter { it.ruleId == ruleId }

    override suspend fun markSourceDeleted(ids: List<Long>) {
        flow.value = flow.value.map { if (it.id in ids) it.copy(sourceDeleted = true) else it }
    }

    override suspend fun updateProgress(id: Long, bytesUploaded: Long, totalBytes: Long, updatedAt: Long) {
        flow.value = flow.value.map {
            if (it.id == id) it.copy(bytesUploaded = bytesUploaded, totalBytes = totalBytes, updatedAt = updatedAt) else it
        }
    }

    override suspend fun getLogForMedia(ruleId: Long, mediaUri: String): UploadLogEntity? =
        flow.value.filter { it.ruleId == ruleId && it.mediaUri == mediaUri }.maxByOrNull { it.createdAt }

    override suspend fun upsert(entry: UploadLogEntity): Long {
        val id = if (entry.id == 0L) (flow.value.maxOfOrNull { it.id } ?: 0) + 1 else entry.id
        val stored = entry.copy(id = id)
        flow.value = flow.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun update(entry: UploadLogEntity) {
        flow.value = flow.value.map { if (it.id == entry.id) entry else it }
    }

    override suspend fun countsByStatus(): List<StatusCount> =
        flow.value.groupingBy { it.status }.eachCount().map { StatusCount(it.key, it.value) }

    override suspend fun countPendingDeletions(): Int =
        flow.value.count { it.status == UploadStatus.SUCCESS && !it.sourceDeleted }

    override suspend fun pruneOlderThan(successAndCancelledCutoff: Long, failedCutoff: Long): Int {
        val before = flow.value.size
        flow.value = flow.value.filterNot { entry ->
            val terminalOld = (entry.status == UploadStatus.SUCCESS || entry.status == UploadStatus.CANCELLED) &&
                entry.createdAt < successAndCancelledCutoff
            val failedOld = entry.status == UploadStatus.FAILED && entry.createdAt < failedCutoff
            terminalOld || failedOld
        }
        return before - flow.value.size
    }
}

private class FakeRuleDaoForLogs : RuleDao {
    val rules = mutableMapOf<Long, RuleEntity>()

    override fun observeRules(): Flow<List<RuleEntity>> = MutableStateFlow(rules.values.toList())
    override suspend fun getRuleById(id: Long): RuleEntity? = rules[id]
    override suspend fun getActiveRules(): List<RuleEntity> = rules.values.filter { it.isActive }
    override suspend fun getAllRules(): List<RuleEntity> = rules.values.toList()

    override suspend fun upsert(rule: RuleEntity): Long {
        rules[rule.id] = rule
        return rule.id
    }

    override suspend fun delete(rule: RuleEntity) {
        rules.remove(rule.id)
    }
}

@RunWith(RobolectricTestRunner::class)
class UploadLogRepositoryTest {

    private val uploadLogDao = FakeUploadLogDao()
    private val ruleDao = FakeRuleDaoForLogs()
    private val repository = UploadLogRepository(RuntimeEnvironment.getApplication(), uploadLogDao, ruleDao)

    /** retry/cancel terminan llamando a WorkManager de verdad vía UploadWorkScheduler. */
    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            RuntimeEnvironment.getApplication(),
            Configuration.Builder().build(),
        )
    }

    @Test
    fun `getPendingDeletions solo incluye subidas exitosas de reglas que piden borrar el original`() = runTest {
        ruleDao.rules[1] = sampleRule(id = 1, deleteSourceAfterUpload = true)
        ruleDao.rules[2] = sampleRule(id = 2, deleteSourceAfterUpload = false)
        uploadLogDao.flow.value = listOf(
            sampleLog(id = 1, ruleId = 1, status = UploadStatus.SUCCESS),
            sampleLog(id = 2, ruleId = 2, status = UploadStatus.SUCCESS),
            sampleLog(id = 3, ruleId = 1, status = UploadStatus.FAILED),
        )

        val pending = repository.getPendingDeletions()

        assertEquals(1, pending.size)
        assertEquals(1L, pending[0].id)
    }

    @Test
    fun `getPendingDeletions ignora subidas ya marcadas como sourceDeleted`() = runTest {
        ruleDao.rules[1] = sampleRule(id = 1, deleteSourceAfterUpload = true)
        uploadLogDao.flow.value = listOf(
            sampleLog(id = 1, ruleId = 1, status = UploadStatus.SUCCESS, sourceDeleted = true),
        )

        assertTrue(repository.getPendingDeletions().isEmpty())
    }

    @Test
    fun `getPendingDeletions ignora logs cuya regla ya no existe`() = runTest {
        uploadLogDao.flow.value = listOf(sampleLog(id = 1, ruleId = 99, status = UploadStatus.SUCCESS))

        assertTrue(repository.getPendingDeletions().isEmpty())
    }

    @Test
    fun `observeLogs mapea las entidades a dominio`() = runTest {
        uploadLogDao.flow.value = listOf(sampleLog(id = 1, ruleId = 1, status = UploadStatus.PENDING))

        val logs = repository.observeLogs().first()

        assertEquals(1, logs.size)
        assertEquals(UploadStatus.PENDING, logs[0].status)
    }

    @Test
    fun `pruneOldEntries borra SUCCESS y CANCELLED viejos pero conserva los recientes`() = runTest {
        val now = 1_000_000_000_000L
        val old = now - UploadLogRepository.SUCCESS_AND_CANCELLED_RETENTION_MS - 1
        val recent = now - 1
        uploadLogDao.flow.value = listOf(
            sampleLog(id = 1, ruleId = 1, status = UploadStatus.SUCCESS).copy(createdAt = old),
            sampleLog(id = 2, ruleId = 1, status = UploadStatus.CANCELLED).copy(createdAt = old),
            sampleLog(id = 3, ruleId = 1, status = UploadStatus.SUCCESS).copy(createdAt = recent),
        )

        val deleted = repository.pruneOldEntries(now)

        assertEquals(2, deleted)
        assertEquals(listOf(3L), uploadLogDao.flow.value.map { it.id })
    }

    @Test
    fun `pruneOldEntries conserva FAILED hasta su propio corte, mas largo que el de SUCCESS`() = runTest {
        val now = 1_000_000_000_000L
        val pastSuccessCutoffButNotFailed = now - UploadLogRepository.SUCCESS_AND_CANCELLED_RETENTION_MS - 1
        val pastFailedCutoff = now - UploadLogRepository.FAILED_RETENTION_MS - 1
        uploadLogDao.flow.value = listOf(
            sampleLog(id = 1, ruleId = 1, status = UploadStatus.FAILED).copy(createdAt = pastSuccessCutoffButNotFailed),
            sampleLog(id = 2, ruleId = 1, status = UploadStatus.FAILED).copy(createdAt = pastFailedCutoff),
        )

        val deleted = repository.pruneOldEntries(now)

        assertEquals(1, deleted)
        assertEquals(listOf(1L), uploadLogDao.flow.value.map { it.id })
    }

    @Test
    fun `pruneOldEntries nunca borra PENDING o UPLOADING sin importar la antiguedad`() = runTest {
        val now = 1_000_000_000_000L
        val ancient = 0L
        uploadLogDao.flow.value = listOf(
            sampleLog(id = 1, ruleId = 1, status = UploadStatus.PENDING).copy(createdAt = ancient),
            sampleLog(id = 2, ruleId = 1, status = UploadStatus.UPLOADING).copy(createdAt = ancient),
        )

        val deleted = repository.pruneOldEntries(now)

        assertEquals(0, deleted)
        assertEquals(2, uploadLogDao.flow.value.size)
    }

    @Test
    fun `retry encola un work de subida para el archivo`() = runTest {
        ruleDao.rules[1] = sampleRule(id = 1, deleteSourceAfterUpload = true)
        val entity = sampleLog(id = 1, ruleId = 1, status = UploadStatus.FAILED)
        uploadLogDao.flow.value = listOf(entity)

        repository.retry(entity.toDomain())

        val workInfos = WorkManager.getInstance(RuntimeEnvironment.getApplication())
            .getWorkInfosForUniqueWork("upload-1-${entity.mediaUri}")
            .get()
        assertTrue(workInfos.any { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `retry no encola nada si la regla ya no existe`() = runTest {
        val entity = sampleLog(id = 1, ruleId = 99, status = UploadStatus.FAILED)
        uploadLogDao.flow.value = listOf(entity)

        repository.retry(entity.toDomain())

        val workInfos = WorkManager.getInstance(RuntimeEnvironment.getApplication())
            .getWorkInfosForUniqueWork("upload-99-${entity.mediaUri}")
            .get()
        assertTrue(workInfos.isEmpty())
    }

    @Test
    fun `cancel marca la entrada como CANCELLED con mensaje`() = runTest {
        val entity = sampleLog(id = 1, ruleId = 1, status = UploadStatus.UPLOADING)
        uploadLogDao.flow.value = listOf(entity)

        repository.cancel(entity.toDomain())

        val updated = uploadLogDao.flow.value.first()
        assertEquals(UploadStatus.CANCELLED, updated.status)
        assertEquals("Cancelado por el usuario", updated.errorMessage)
    }

    @Test
    fun `retryAllFailedForRule encola un work por cada FAILED de la regla`() = runTest {
        ruleDao.rules[1] = sampleRule(id = 1, deleteSourceAfterUpload = true)
        val failed1 = sampleLog(id = 1, ruleId = 1, status = UploadStatus.FAILED)
        val failed2 = sampleLog(id = 2, ruleId = 1, status = UploadStatus.FAILED)
        val success = sampleLog(id = 3, ruleId = 1, status = UploadStatus.SUCCESS)
        uploadLogDao.flow.value = listOf(failed1, failed2, success)

        repository.retryAllFailedForRule(1)

        val workManager = WorkManager.getInstance(RuntimeEnvironment.getApplication())
        assertTrue(
            workManager.getWorkInfosForUniqueWork("upload-1-${failed1.mediaUri}").get()
                .any { it.state == WorkInfo.State.ENQUEUED },
        )
        assertTrue(
            workManager.getWorkInfosForUniqueWork("upload-1-${failed2.mediaUri}").get()
                .any { it.state == WorkInfo.State.ENQUEUED },
        )
    }

    @Test
    fun `cancelActiveUploadsForRule cancela PENDING y UPLOADING pero no toca SUCCESS`() = runTest {
        val pending = sampleLog(id = 1, ruleId = 1, status = UploadStatus.PENDING)
        val uploading = sampleLog(id = 2, ruleId = 1, status = UploadStatus.UPLOADING)
        val success = sampleLog(id = 3, ruleId = 1, status = UploadStatus.SUCCESS)
        uploadLogDao.flow.value = listOf(pending, uploading, success)

        repository.cancelActiveUploadsForRule(1)

        val statusById = uploadLogDao.flow.value.associate { it.id to it.status }
        assertEquals(UploadStatus.CANCELLED, statusById[1L])
        assertEquals(UploadStatus.CANCELLED, statusById[2L])
        assertEquals(UploadStatus.SUCCESS, statusById[3L])
    }

    private fun sampleRule(id: Long, deleteSourceAfterUpload: Boolean) = RuleEntity(
        id = id,
        folderUri = "content://tree/foo",
        folderDisplayName = "Foo",
        destinationType = DestinationType.GOOGLE_PHOTOS,
        googleAccountEmail = "user@example.com",
        deleteSourceAfterUpload = deleteSourceAfterUpload,
    )

    private fun sampleLog(id: Long, ruleId: Long, status: UploadStatus, sourceDeleted: Boolean = false) = UploadLogEntity(
        id = id,
        ruleId = ruleId,
        mediaUri = "content://media/$id",
        fileName = "IMG_000$id.jpg",
        status = status,
        createdAt = 1000,
        updatedAt = 1000,
        sourceDeleted = sourceDeleted,
    )
}
