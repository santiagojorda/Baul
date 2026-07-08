package com.santiagojorda.baul.ui.ruleeditor

import androidx.room.Room
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.dao.ConnectedAccountDao
import com.santiagojorda.baul.data.local.dao.RuleDao
import com.santiagojorda.baul.data.local.entity.ConnectedAccountEntity
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.data.repository.ConnectedAccountRepository
import com.santiagojorda.baul.data.repository.RuleRepository
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.media.MediaMetadataReader
import com.santiagojorda.baul.media.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

private class FakeConnectedAccountDao : ConnectedAccountDao {
    val flow = MutableStateFlow<List<ConnectedAccountEntity>>(emptyList())

    override fun observeAccounts(): Flow<List<ConnectedAccountEntity>> = flow
    override suspend fun getByEmail(email: String): ConnectedAccountEntity? = flow.value.find { it.email == email }
    override suspend fun getFirstConnected(): ConnectedAccountEntity? = flow.value.minByOrNull { it.connectedAt }
    override suspend fun getDefault(): ConnectedAccountEntity? = flow.value.find { it.isDefault }
    override suspend fun count(): Int = flow.value.size

    override suspend fun clearDefault() {
        flow.value = flow.value.map { it.copy(isDefault = false) }
    }

    override suspend fun markAsDefault(email: String) {
        flow.value = flow.value.map { if (it.email == email) it.copy(isDefault = true) else it }
    }

    override suspend fun markNeedsReauth(email: String) {
        flow.value = flow.value.map { if (it.email == email) it.copy(needsReauth = true) else it }
    }

    override suspend fun upsert(account: ConnectedAccountEntity) {
        flow.value = flow.value.filterNot { it.email == account.email } + account
    }

    override suspend fun delete(account: ConnectedAccountEntity) {
        flow.value = flow.value.filterNot { it.email == account.email }
    }
}

/**
 * Igual que en RuleListViewModelTest: el SyncCoordinator necesita una AppDatabase de Room real
 * para construirse, pero se le da una in-memory vacía y aislada del FakeRuleDao de arriba — el
 * backfillRule que dispara save() no encuentra nada ahí y no hace nada, sin tocar MediaStore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RuleEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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

    private fun buildViewModel(ruleDao: FakeRuleDao, accountDao: FakeConnectedAccountDao, ruleId: Long?) =
        RuleEditorViewModel(
            RuleRepository(ruleDao),
            ConnectedAccountRepository(accountDao),
            buildSyncCoordinator(),
            ruleId,
        )

    @Test
    fun `onDestinationTypeChanged actualiza el uiState`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(FakeRuleDao(), FakeConnectedAccountDao(), ruleId = null)

        viewModel.onDestinationTypeChanged(DestinationType.DRIVE)

        assertEquals(DestinationType.DRIVE, viewModel.uiState.value.destinationType)
    }

    @Test
    fun `onFolderPicked setea la carpeta y limpia folderRelativePath (pasa a ser explicita)`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(FakeRuleDao(), FakeConnectedAccountDao(), ruleId = null)

        viewModel.onFolderPicked("content://tree/foo", "Foo")

        val state = viewModel.uiState.value
        assertEquals("content://tree/foo", state.folderUri)
        assertEquals("Foo", state.folderDisplayName)
        assertNull(state.folderRelativePath)
    }

    @Test
    fun `onPhotosAlbumNameChanged limpia photosAlbumId al escribir un nombre nuevo`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(FakeRuleDao(), FakeConnectedAccountDao(), ruleId = null)

        viewModel.onPhotosAlbumNameChanged("Vacaciones")

        val state = viewModel.uiState.value
        assertEquals("Vacaciones", state.photosAlbumName)
        assertNull(state.photosAlbumId)
    }

    @Test
    fun `con ruleId existente, el uiState inicial carga los datos de la regla`() = runTest(testDispatcher) {
        val ruleDao = FakeRuleDao()
        ruleDao.flow.value = listOf(
            RuleEntity(
                id = 1,
                folderUri = "content://tree/foo",
                folderDisplayName = "Foo",
                destinationType = DestinationType.GOOGLE_PHOTOS,
                googleAccountEmail = "user@example.com",
                photosAlbumName = "Vacaciones",
            ),
        )
        val viewModel = buildViewModel(ruleDao, FakeConnectedAccountDao(), ruleId = 1)

        val state = viewModel.uiState.value
        assertEquals(DestinationType.GOOGLE_PHOTOS, state.destinationType)
        assertEquals("Vacaciones", state.photosAlbumName)
        assertFalse(state.isLoading)
    }

    @Test
    fun `con ruleId nulo, el uiState arranca sin isLoading`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(FakeRuleDao(), FakeConnectedAccountDao(), ruleId = null)

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `save no hace nada si canSave es false`() = runTest(testDispatcher) {
        val ruleDao = FakeRuleDao()
        val viewModel = buildViewModel(ruleDao, FakeConnectedAccountDao(), ruleId = null)

        viewModel.save()

        assertTrue(ruleDao.flow.value.isEmpty())
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `save persiste una regla nueva valida y marca isSaved`() = runTest(testDispatcher) {
        val ruleDao = FakeRuleDao()
        val viewModel = buildViewModel(ruleDao, FakeConnectedAccountDao(), ruleId = null)
        viewModel.onFolderPicked("content://tree/foo", "Foo")
        viewModel.onGoogleAccountEmailChanged("user@example.com")
        viewModel.onDestinationTypeChanged(DestinationType.DRIVE)
        viewModel.onDriveFolderIdChanged("drive-folder-1")

        viewModel.save()

        assertEquals(1, ruleDao.flow.value.size)
        assertEquals("drive-folder-1", ruleDao.flow.value.first().driveFolderId)
        assertTrue(viewModel.uiState.value.isSaved)
    }
}
