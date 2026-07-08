package com.santiagojorda.baul.ui.accounts

import android.content.Context
import androidx.room.Room
import com.santiagojorda.baul.auth.GoogleAuthManager
import com.santiagojorda.baul.auth.SignInResult
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.dao.ConnectedAccountDao
import com.santiagojorda.baul.data.local.entity.ConnectedAccountEntity
import com.santiagojorda.baul.data.repository.ConnectedAccountRepository
import com.santiagojorda.baul.domain.model.ConnectedAccount
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Evita tocar Google Sign-In de verdad: devuelve un resultado fijo, sin llamar a Play Services. */
private class FakeGoogleAuthManager(context: Context, private val result: SignInResult) : GoogleAuthManager(context) {
    override suspend fun handleSignInResult(data: android.content.Intent?): SignInResult = result
}

/** Solo registra si se disparó un barrido, sin tocar MediaStore ni el executor async de Room. */
private class FakeSyncCoordinator(context: Context, database: AppDatabase) : SyncCoordinator(
    context = context,
    database = database,
    metadataReader = MediaMetadataReader(context.contentResolver),
    scope = CoroutineScope(Dispatchers.Unconfined),
) {
    var scanTriggeredCount = 0
        private set

    override fun scanExistingFoldersForAutoSync() {
        scanTriggeredCount++
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
 * Robolectric solo hace falta para tener un Context real con el que construir [GoogleAuthManager]
 * (lo pide el constructor de [AccountsViewModel]). Los tests de `onSignInResult` usan
 * [FakeGoogleAuthManager] en vez del real, así que nunca se toca Google Sign-In de verdad.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun buildSyncCoordinator(): SyncCoordinator {
        val context = RuntimeEnvironment.getApplication()
        return SyncCoordinator(
            context = context,
            database = buildDatabase(),
            metadataReader = MediaMetadataReader(context.contentResolver),
            scope = CoroutineScope(testDispatcher),
        )
    }

    private fun viewModel(dao: FakeConnectedAccountDao) = AccountsViewModel(
        ConnectedAccountRepository(dao),
        GoogleAuthManager(RuntimeEnvironment.getApplication()),
        buildSyncCoordinator(),
    )

    @Test
    fun `accounts refleja lo que ya habia en el repositorio`() = runTest(testDispatcher) {
        val dao = FakeConnectedAccountDao()
        dao.flow.value = listOf(ConnectedAccountEntity(email = "a@example.com", connectedAt = 1000, isDefault = true))
        val vm = viewModel(dao)
        val job = launch { vm.accounts.collect {} }

        assertEquals(1, vm.accounts.value.size)
        assertEquals("a@example.com", vm.accounts.value[0].email)

        job.cancel()
    }

    @Test
    fun `removeAccount la saca del StateFlow`() = runTest(testDispatcher) {
        val dao = FakeConnectedAccountDao()
        dao.flow.value = listOf(ConnectedAccountEntity(email = "a@example.com", connectedAt = 1000, isDefault = true))
        val vm = viewModel(dao)
        val job = launch { vm.accounts.collect {} }

        vm.removeAccount(ConnectedAccount(email = "a@example.com", connectedAt = 1000, isDefault = true))

        assertTrue(vm.accounts.value.isEmpty())

        job.cancel()
    }

    @Test
    fun `setDefault mueve el default a la cuenta elegida`() = runTest(testDispatcher) {
        val dao = FakeConnectedAccountDao()
        dao.flow.value = listOf(
            ConnectedAccountEntity(email = "a@example.com", connectedAt = 1000, isDefault = true),
            ConnectedAccountEntity(email = "b@example.com", connectedAt = 2000, isDefault = false),
        )
        val vm = viewModel(dao)
        val job = launch { vm.accounts.collect {} }

        vm.setDefault(ConnectedAccount(email = "b@example.com", connectedAt = 2000))

        assertEquals(false, vm.accounts.value.first { it.email == "a@example.com" }.isDefault)
        assertTrue(vm.accounts.value.first { it.email == "b@example.com" }.isDefault)

        job.cancel()
    }

    @Test
    fun `onSignInResult con exito guarda la cuenta y dispara un nuevo barrido de carpetas existentes`() =
        runTest(testDispatcher) {
        val dao = FakeConnectedAccountDao()
        val context = RuntimeEnvironment.getApplication()
        val syncCoordinator = FakeSyncCoordinator(context, buildDatabase())
        val vm = AccountsViewModel(
            ConnectedAccountRepository(dao),
            FakeGoogleAuthManager(context, SignInResult.Success(email = "new@example.com", displayName = "Nueva")),
            syncCoordinator,
        )
        val job = launch { vm.accounts.collect {} }

        vm.onSignInResult(data = null)

        assertEquals(1, vm.accounts.value.size)
        assertEquals("new@example.com", vm.accounts.value[0].email)
        assertEquals(
            "conectar la primera cuenta debe re-disparar el barrido de carpetas existentes, " +
                "que al abrir la app corrió sin ninguna cuenta y no creó nada",
            1,
            syncCoordinator.scanTriggeredCount,
        )

        job.cancel()
    }

    @Test
    fun `onSignInResult con fallo no guarda cuenta ni dispara el barrido, y emite un error`() =
        runTest(testDispatcher) {
        val dao = FakeConnectedAccountDao()
        val context = RuntimeEnvironment.getApplication()
        val syncCoordinator = FakeSyncCoordinator(context, buildDatabase())
        val vm = AccountsViewModel(
            ConnectedAccountRepository(dao),
            FakeGoogleAuthManager(context, SignInResult.Failure(message = "cancelado por el usuario")),
            syncCoordinator,
        )
        val events = mutableListOf<AccountsEvent>()
        val eventsJob = launch { vm.events.collect { events += it } }

        vm.onSignInResult(data = null)

        assertTrue(vm.accounts.value.isEmpty())
        assertEquals(0, syncCoordinator.scanTriggeredCount)
        assertEquals(1, events.size)
        assertEquals("cancelado por el usuario", (events[0] as AccountsEvent.Error).message)

        eventsJob.cancel()
    }

    @Test
    fun `isLoading vuelve a false despues de resolver el sign-in, tanto en exito como en fallo`() =
        runTest(testDispatcher) {
        val context = RuntimeEnvironment.getApplication()
        val vm = AccountsViewModel(
            ConnectedAccountRepository(FakeConnectedAccountDao()),
            FakeGoogleAuthManager(context, SignInResult.Failure(message = "error")),
            FakeSyncCoordinator(context, buildDatabase()),
        )
        val eventsJob = launch { vm.events.collect {} }

        vm.onSignInResult(data = null)

        assertFalse(vm.isLoading.value)

        eventsJob.cancel()
    }
}
