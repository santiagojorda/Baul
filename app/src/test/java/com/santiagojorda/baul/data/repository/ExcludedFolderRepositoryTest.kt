package com.santiagojorda.baul.data.repository

import com.santiagojorda.baul.data.local.dao.ExcludedFolderDao
import com.santiagojorda.baul.data.local.entity.ExcludedFolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeExcludedFolderDao : ExcludedFolderDao {
    private val flow = MutableStateFlow<List<ExcludedFolderEntity>>(emptyList())

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

class ExcludedFolderRepositoryTest {

    private val dao = FakeExcludedFolderDao()
    private val repository = ExcludedFolderRepository(dao)

    @Test
    fun `add normaliza a minusculas y sin espacios al principio o final`() = runTest {
        repository.add("  MiCarpeta  ")

        assertEquals(setOf("micarpeta"), repository.getNames())
    }

    @Test
    fun `add con nombre vacio o solo espacios no inserta nada`() = runTest {
        repository.add("   ")

        assertTrue(repository.getNames().isEmpty())
    }

    @Test
    fun `add con el mismo nombre normalizado dos veces no duplica`() = runTest {
        repository.add("Foo")
        repository.add("foo")

        assertEquals(1, repository.getNames().size)
    }

    @Test
    fun `remove elimina por nombre exacto ya normalizado`() = runTest {
        repository.add("carpeta")

        repository.remove("carpeta")

        assertTrue(repository.getNames().isEmpty())
    }

    @Test
    fun `observeNames emite los nombres normalizados`() = runTest {
        repository.add("Foo")
        repository.add("Bar")

        val names = repository.observeNames().first()

        assertEquals(setOf("foo", "bar"), names.toSet())
    }
}
