package com.santiagojorda.baul.data.repository

import com.santiagojorda.baul.data.local.dao.ConnectedAccountDao
import com.santiagojorda.baul.data.local.entity.ConnectedAccountEntity
import com.santiagojorda.baul.domain.model.ConnectedAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    override suspend fun upsert(account: ConnectedAccountEntity) {
        flow.value = flow.value.filterNot { it.email == account.email } + account
    }

    override suspend fun delete(account: ConnectedAccountEntity) {
        flow.value = flow.value.filterNot { it.email == account.email }
    }
}

class ConnectedAccountRepositoryTest {

    private val dao = FakeConnectedAccountDao()
    private val repository = ConnectedAccountRepository(dao)

    @Test
    fun `la primera cuenta conectada queda marcada como default`() = runTest {
        repository.save(ConnectedAccount(email = "a@example.com"))

        val saved = requireNotNull(repository.getByEmail("a@example.com"))
        assertTrue(saved.isDefault)
    }

    @Test
    fun `una segunda cuenta no le pisa el default a la primera`() = runTest {
        repository.save(ConnectedAccount(email = "a@example.com", connectedAt = 1000))
        repository.save(ConnectedAccount(email = "b@example.com", connectedAt = 2000))

        assertTrue(requireNotNull(repository.getByEmail("a@example.com")).isDefault)
        assertEquals(false, requireNotNull(repository.getByEmail("b@example.com")).isDefault)
    }

    @Test
    fun `guardar de nuevo una cuenta existente conserva si era default o no`() = runTest {
        repository.save(ConnectedAccount(email = "a@example.com", connectedAt = 1000))
        repository.save(ConnectedAccount(email = "b@example.com", connectedAt = 2000))

        repository.save(ConnectedAccount(email = "b@example.com", displayName = "B renombrada", connectedAt = 2000))

        assertEquals(false, requireNotNull(repository.getByEmail("b@example.com")).isDefault)
        assertEquals("B renombrada", repository.getByEmail("b@example.com")?.displayName)
    }

    @Test
    fun `getDefault cae a la primera conectada si ninguna quedo marcada como default`() = runTest {
        dao.flow.value = listOf(
            ConnectedAccountEntity(email = "a@example.com", connectedAt = 1000, isDefault = false),
            ConnectedAccountEntity(email = "b@example.com", connectedAt = 2000, isDefault = false),
        )

        val default = repository.getDefault()

        assertEquals("a@example.com", default?.email)
    }

    @Test
    fun `getDefault devuelve null si no hay ninguna cuenta conectada`() = runTest {
        assertNull(repository.getDefault())
    }

    @Test
    fun `setDefault mueve el default de una cuenta a otra`() = runTest {
        repository.save(ConnectedAccount(email = "a@example.com"))
        repository.save(ConnectedAccount(email = "b@example.com"))

        repository.setDefault("b@example.com")

        assertEquals(false, requireNotNull(repository.getByEmail("a@example.com")).isDefault)
        assertTrue(requireNotNull(repository.getByEmail("b@example.com")).isDefault)
    }

    @Test
    fun `remove elimina la cuenta`() = runTest {
        repository.save(ConnectedAccount(email = "a@example.com"))

        repository.remove(requireNotNull(repository.getByEmail("a@example.com")))

        assertNull(repository.getByEmail("a@example.com"))
    }
}
