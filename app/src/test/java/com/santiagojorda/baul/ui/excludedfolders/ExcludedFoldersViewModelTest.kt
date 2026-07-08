package com.santiagojorda.baul.ui.excludedfolders

import com.santiagojorda.baul.data.local.dao.ExcludedFolderDao
import com.santiagojorda.baul.data.local.entity.ExcludedFolderEntity
import com.santiagojorda.baul.data.repository.ExcludedFolderRepository
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
import org.junit.Before
import org.junit.Test

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
 * viewModelScope usa Dispatchers.Main.immediate; sin Robolectric ni Main de verdad, hay que
 * pisarlo con un TestDispatcher para que las coroutines del ViewModel corran en el test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExcludedFoldersViewModelTest {

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
    fun `excludedFolders refleja lo que ya habia en el repositorio`() = runTest(testDispatcher) {
        val dao = FakeExcludedFolderDao()
        dao.flow.value = listOf(ExcludedFolderEntity("camera"), ExcludedFolderEntity("screenshots"))
        val viewModel = ExcludedFoldersViewModel(ExcludedFolderRepository(dao))
        val job = launch { viewModel.excludedFolders.collect {} }

        assertEquals(setOf("camera", "screenshots"), viewModel.excludedFolders.value.toSet())

        job.cancel()
    }

    @Test
    fun `add normaliza el nombre y lo refleja en el StateFlow`() = runTest(testDispatcher) {
        val dao = FakeExcludedFolderDao()
        val viewModel = ExcludedFoldersViewModel(ExcludedFolderRepository(dao))
        val job = launch { viewModel.excludedFolders.collect {} }

        viewModel.add("  MiCarpeta  ")

        assertEquals(listOf("micarpeta"), viewModel.excludedFolders.value)

        job.cancel()
    }

    @Test
    fun `remove lo saca del StateFlow`() = runTest(testDispatcher) {
        val dao = FakeExcludedFolderDao()
        dao.flow.value = listOf(ExcludedFolderEntity("camera"))
        val viewModel = ExcludedFoldersViewModel(ExcludedFolderRepository(dao))
        val job = launch { viewModel.excludedFolders.collect {} }

        viewModel.remove("camera")

        assertEquals(emptyList<String>(), viewModel.excludedFolders.value)

        job.cancel()
    }
}
