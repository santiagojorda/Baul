package com.santiagojorda.baul.data.repository

import com.santiagojorda.baul.data.local.dao.RuleDao
import com.santiagojorda.baul.data.local.dao.StatusCount
import com.santiagojorda.baul.data.local.dao.UploadLogDao
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Solo cubre [UploadLogRepository.observeLogs] y [UploadLogRepository.getPendingDeletions]: son
 * los únicos métodos que no dependen de WorkManager/Glance (retry/cancel/markSourceDeleted sí, y
 * no hay work-testing en el proyecto para simularlos). El Context real de Robolectric solo hace
 * falta para poder construir el repositorio; estos dos métodos no lo tocan.
 */
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
