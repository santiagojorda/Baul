package com.santiagojorda.baul.ui.logs

import com.santiagojorda.baul.data.local.dao.RuleDao
import com.santiagojorda.baul.data.local.dao.StatusCount
import com.santiagojorda.baul.data.local.dao.UploadLogDao
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.data.repository.RuleRepository
import com.santiagojorda.baul.data.repository.UploadLogRepository
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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

    override suspend fun pruneOlderThan(successAndCancelledCutoff: Long, failedCutoff: Long): Int = 0
}

private class FakeRuleDao : RuleDao {
    val flow = MutableStateFlow<List<RuleEntity>>(emptyList())

    override fun observeRules(): Flow<List<RuleEntity>> = flow
    override suspend fun getRuleById(id: Long): RuleEntity? = flow.value.find { it.id == id }
    override suspend fun getActiveRules(): List<RuleEntity> = flow.value.filter { it.isActive }
    override suspend fun getAllRules(): List<RuleEntity> = flow.value

    override suspend fun upsert(rule: RuleEntity): Long {
        flow.value = flow.value.filterNot { it.id == rule.id } + rule
        return rule.id
    }

    override suspend fun delete(rule: RuleEntity) {
        flow.value = flow.value.filterNot { it.id == rule.id }
    }
}

/**
 * Robolectric solo hace falta para poder construir [UploadLogRepository] (pide un Context real);
 * ninguno de los métodos ejercitados acá (`rows`) lo llega a usar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LogsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rows solo incluye logs con errorMessage, sin importar el estado`() = runTest(testDispatcher) {
        val uploadLogDao = FakeUploadLogDao()
        val ruleDao = FakeRuleDao()
        ruleDao.flow.value = listOf(sampleRule(id = 1, folderDisplayName = "Camera"))
        uploadLogDao.flow.value = listOf(
            sampleLog(id = 1, ruleId = 1, status = UploadStatus.SUCCESS, errorMessage = null, updatedAt = 1000),
            sampleLog(id = 2, ruleId = 1, status = UploadStatus.PENDING, errorMessage = "Timeout de red", updatedAt = 2000),
            sampleLog(id = 3, ruleId = 1, status = UploadStatus.FAILED, errorMessage = "Cuota excedida", updatedAt = 3000),
        )
        val viewModel = LogsViewModel(
            UploadLogRepository(RuntimeEnvironment.getApplication(), uploadLogDao, ruleDao),
            RuleRepository(ruleDao),
        )
        val job = launch { viewModel.rows.collect {} }

        val rows = viewModel.rows.value
        assertEquals(2, rows.size)
        assertTrue(rows.none { it.entry.id == 1L })
        assertEquals(3L, rows[0].entry.id)
        assertEquals("Camera", rows[0].folderName)

        job.cancel()
    }

    @Test
    fun `rows queda vacio cuando ningun log tiene errorMessage`() = runTest(testDispatcher) {
        val uploadLogDao = FakeUploadLogDao()
        val ruleDao = FakeRuleDao()
        uploadLogDao.flow.value = listOf(
            sampleLog(id = 1, ruleId = 1, status = UploadStatus.SUCCESS, errorMessage = null, updatedAt = 1000),
        )
        val viewModel = LogsViewModel(
            UploadLogRepository(RuntimeEnvironment.getApplication(), uploadLogDao, ruleDao),
            RuleRepository(ruleDao),
        )
        val job = launch { viewModel.rows.collect {} }

        assertTrue(viewModel.rows.value.isEmpty())

        job.cancel()
    }

    private fun sampleRule(id: Long, folderDisplayName: String) = RuleEntity(
        id = id,
        folderUri = "content://tree/foo",
        folderDisplayName = folderDisplayName,
        destinationType = DestinationType.GOOGLE_PHOTOS,
        googleAccountEmail = "user@example.com",
    )

    private fun sampleLog(id: Long, ruleId: Long, status: UploadStatus, errorMessage: String?, updatedAt: Long) = UploadLogEntity(
        id = id,
        ruleId = ruleId,
        mediaUri = "content://media/$id",
        fileName = "IMG_000$id.jpg",
        status = status,
        errorMessage = errorMessage,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )
}
