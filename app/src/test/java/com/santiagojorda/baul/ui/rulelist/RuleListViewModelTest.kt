package com.santiagojorda.baul.ui.rulelist

import androidx.room.Room
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.dao.ExcludedFolderDao
import com.santiagojorda.baul.data.local.dao.RuleDao
import com.santiagojorda.baul.data.local.dao.StatusCount
import com.santiagojorda.baul.data.local.dao.UploadLogDao
import com.santiagojorda.baul.data.local.entity.ExcludedFolderEntity
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.data.repository.ExcludedFolderRepository
import com.santiagojorda.baul.data.repository.RuleRepository
import com.santiagojorda.baul.data.repository.UploadLogRepository
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.Rule
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.media.MediaMetadataReader
import com.santiagojorda.baul.media.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
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

private class FakeRuleDao : RuleDao {
    val flow = MutableStateFlow<List<RuleEntity>>(emptyList())

    override fun observeRules(): Flow<List<RuleEntity>> = flow
    override suspend fun getRuleById(id: Long): RuleEntity? = flow.value.find { it.id == id }
    override suspend fun getActiveRules(): List<RuleEntity> = flow.value.filter { it.isActive }
    override suspend fun getAllRules(): List<RuleEntity> = flow.value

    override suspend fun upsert(rule: RuleEntity): Long {
        val id = if (rule.id == 0L) (flow.value.maxOfOrNull { it.id } ?: 0) + 1 else rule.id
        val stored = rule.copy(id = id)
        flow.value = flow.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun delete(rule: RuleEntity) {
        flow.value = flow.value.filterNot { it.id == rule.id }
    }
}

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

private class FakeExcludedFolderDao : ExcludedFolderDao {
    val flow = MutableStateFlow<List<ExcludedFolderEntity>>(emptyList())

    override fun observeAll(): Flow<List<ExcludedFolderEntity>> = flow
    override suspend fun getAllNames(): List<String> = flow.value.map { it.folderName }

    override suspend fun insert(entity: ExcludedFolderEntity) {
        if (flow.value.none { it.folderName == entity.folderName }) {
            flow.value = flow.value + entity
        }
    }

    override suspend fun delete(entity: ExcludedFolderEntity) {
        flow.value = flow.value.filterNot { it.folderName == entity.folderName }
    }
}

/**
 * RuleListViewModel pide un SyncCoordinator en el constructor, que a su vez pide una AppDatabase
 * de Room real. Se le da una in-memory vacía y aislada de los fakes de arriba: como nunca tiene
 * las reglas que usan los tests, sus lookups siempre devuelven null y sus efectos (backfillRule)
 * quedan en no-op, sin tocar MediaStore. Con WorkManagerTestInitHelper sí se puede ejercitar
 * setActive/retryFailed de verdad (llegan a WorkManager), a diferencia de rondas anteriores.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RuleListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication(), Configuration.Builder().build())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildSyncCoordinator(): SyncCoordinator {
        val context = RuntimeEnvironment.getApplication()
        val emptyDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return SyncCoordinator(
            context = context,
            database = emptyDatabase,
            metadataReader = MediaMetadataReader(context.contentResolver),
            scope = CoroutineScope(testDispatcher),
        )
    }

    private fun buildViewModel(
        ruleDao: FakeRuleDao,
        uploadLogDao: FakeUploadLogDao,
        excludedFolderDao: FakeExcludedFolderDao,
    ): RuleListViewModel {
        val context = RuntimeEnvironment.getApplication()
        return RuleListViewModel(
            RuleRepository(ruleDao),
            UploadLogRepository(context, uploadLogDao, ruleDao),
            buildSyncCoordinator(),
            ExcludedFolderRepository(excludedFolderDao),
        )
    }

    @Test
    fun `items combina reglas y logs, con el ultimo estado y hasFailedUploads`() = runTest(testDispatcher) {
        val ruleDao = FakeRuleDao()
        val uploadLogDao = FakeUploadLogDao()
        ruleDao.flow.value = listOf(sampleRuleEntity(id = 1))
        uploadLogDao.flow.value = listOf(
            sampleLog(id = 1, ruleId = 1, status = UploadStatus.FAILED, updatedAt = 1000),
            sampleLog(id = 2, ruleId = 1, status = UploadStatus.SUCCESS, updatedAt = 2000),
        )
        val viewModel = buildViewModel(ruleDao, uploadLogDao, FakeExcludedFolderDao())
        val job = launch { viewModel.items.collect {} }

        val item = viewModel.items.value.first()
        assertEquals(UploadStatus.SUCCESS, item.lastSyncStatus)
        assertEquals(2000L, item.lastSyncAt)
        assertTrue(item.hasFailedUploads)

        job.cancel()
    }

    @Test
    fun `delete quita la regla del repositorio`() = runTest(testDispatcher) {
        val ruleDao = FakeRuleDao()
        ruleDao.flow.value = listOf(sampleRuleEntity(id = 1))
        val viewModel = buildViewModel(ruleDao, FakeUploadLogDao(), FakeExcludedFolderDao())
        val job = launch { viewModel.items.collect {} }

        viewModel.delete(sampleDomainRule(id = 1))

        assertTrue(ruleDao.flow.value.isEmpty())

        job.cancel()
    }

    @Test
    fun `exclude agrega la carpeta a excluidas y borra la regla`() = runTest(testDispatcher) {
        val ruleDao = FakeRuleDao()
        val excludedFolderDao = FakeExcludedFolderDao()
        ruleDao.flow.value = listOf(sampleRuleEntity(id = 1, folderDisplayName = "Camera"))
        val viewModel = buildViewModel(ruleDao, FakeUploadLogDao(), excludedFolderDao)
        val job = launch { viewModel.items.collect {} }

        viewModel.exclude(sampleDomainRule(id = 1, folderDisplayName = "Camera"))

        assertTrue(ruleDao.flow.value.isEmpty())
        assertEquals(listOf("camera"), excludedFolderDao.flow.value.map { it.folderName })

        job.cancel()
    }

    @Test
    fun `setActive true prende la regla`() = runTest(testDispatcher) {
        val ruleDao = FakeRuleDao()
        ruleDao.flow.value = listOf(sampleRuleEntity(id = 1, isActive = false))
        val viewModel = buildViewModel(ruleDao, FakeUploadLogDao(), FakeExcludedFolderDao())
        val job = launch { viewModel.items.collect {} }

        viewModel.setActive(sampleDomainRule(id = 1, isActive = false), isActive = true)

        assertTrue(requireNotNull(ruleDao.flow.value.find { it.id == 1L }).isActive)

        job.cancel()
    }

    @Test
    fun `setActive false cancela las subidas en curso de la regla`() = runTest(testDispatcher) {
        val ruleDao = FakeRuleDao()
        val uploadLogDao = FakeUploadLogDao()
        ruleDao.flow.value = listOf(sampleRuleEntity(id = 1, isActive = true))
        uploadLogDao.flow.value = listOf(sampleLog(id = 1, ruleId = 1, status = UploadStatus.PENDING, updatedAt = 1000))
        val viewModel = buildViewModel(ruleDao, uploadLogDao, FakeExcludedFolderDao())
        val job = launch { viewModel.items.collect {} }

        viewModel.setActive(sampleDomainRule(id = 1, isActive = true), isActive = false)

        assertEquals(false, requireNotNull(ruleDao.flow.value.find { it.id == 1L }).isActive)
        assertEquals(UploadStatus.CANCELLED, requireNotNull(uploadLogDao.flow.value.find { it.id == 1L }).status)

        job.cancel()
    }

    @Test
    fun `retryFailed no revienta cuando no hay nada para reintentar`() = runTest(testDispatcher) {
        val ruleDao = FakeRuleDao()
        ruleDao.flow.value = listOf(sampleRuleEntity(id = 1))
        val viewModel = buildViewModel(ruleDao, FakeUploadLogDao(), FakeExcludedFolderDao())
        val job = launch { viewModel.items.collect {} }

        viewModel.retryFailed(sampleDomainRule(id = 1))

        job.cancel()
    }

    private fun sampleRuleEntity(id: Long, folderDisplayName: String = "Foo", isActive: Boolean = true) = RuleEntity(
        id = id,
        folderUri = "content://tree/foo",
        folderDisplayName = folderDisplayName,
        destinationType = DestinationType.GOOGLE_PHOTOS,
        googleAccountEmail = "user@example.com",
        isActive = isActive,
    )

    private fun sampleDomainRule(id: Long, folderDisplayName: String = "Foo", isActive: Boolean = true) = Rule(
        id = id,
        folderUri = "content://tree/foo",
        folderDisplayName = folderDisplayName,
        destinationType = DestinationType.GOOGLE_PHOTOS,
        googleAccountEmail = "user@example.com",
        isActive = isActive,
        createdAt = 1000,
    )

    private fun sampleLog(id: Long, ruleId: Long, status: UploadStatus, updatedAt: Long) = UploadLogEntity(
        id = id,
        ruleId = ruleId,
        mediaUri = "content://media/$id",
        fileName = "IMG_000$id.jpg",
        status = status,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )
}
