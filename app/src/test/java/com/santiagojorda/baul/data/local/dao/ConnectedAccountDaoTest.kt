package com.santiagojorda.baul.data.local.dao

import androidx.room.Room
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.entity.ConnectedAccountEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Contra una base Room real en memoria (no un fake): [ConnectedAccountRepositoryTest] usa un
 * `FakeConnectedAccountDao` que nunca ejercita el SQL real de estas queries.
 */
@RunWith(RobolectricTestRunner::class)
class ConnectedAccountDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ConnectedAccountDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.connectedAccountDao()
    }

    @Test
    fun `getFirstConnected devuelve la de menor connectedAt, no la ultima insertada`() = runTest {
        dao.upsert(sampleAccount(email = "b@example.com", connectedAt = 2000))
        dao.upsert(sampleAccount(email = "a@example.com", connectedAt = 1000))

        assertEquals("a@example.com", dao.getFirstConnected()?.email)
    }

    @Test
    fun `getDefault devuelve null si ninguna cuenta esta marcada como default`() = runTest {
        dao.upsert(sampleAccount(email = "a@example.com"))

        assertNull(dao.getDefault())
    }

    @Test
    fun `clearDefault y markAsDefault son mutuamente excluyentes entre cuentas`() = runTest {
        dao.upsert(sampleAccount(email = "a@example.com", isDefault = true))
        dao.upsert(sampleAccount(email = "b@example.com", isDefault = false))

        dao.clearDefault()
        dao.markAsDefault("b@example.com")

        assertEquals(false, requireNotNull(dao.getByEmail("a@example.com")).isDefault)
        assertTrue(requireNotNull(dao.getByEmail("b@example.com")).isDefault)
        assertEquals("b@example.com", dao.getDefault()?.email)
    }

    @Test
    fun `markNeedsReauth solo marca la cuenta indicada, no las demas`() = runTest {
        dao.upsert(sampleAccount(email = "a@example.com"))
        dao.upsert(sampleAccount(email = "b@example.com"))

        dao.markNeedsReauth("a@example.com")

        assertTrue(requireNotNull(dao.getByEmail("a@example.com")).needsReauth)
        assertEquals(false, requireNotNull(dao.getByEmail("b@example.com")).needsReauth)
    }

    @Test
    fun `count refleja cuantas cuentas hay conectadas`() = runTest {
        assertEquals(0, dao.count())

        dao.upsert(sampleAccount(email = "a@example.com"))
        dao.upsert(sampleAccount(email = "b@example.com"))

        assertEquals(2, dao.count())
    }

    @Test
    fun `upsert con OnConflictStrategy REPLACE actualiza la cuenta existente por email`() = runTest {
        dao.upsert(sampleAccount(email = "a@example.com", displayName = "Original"))

        dao.upsert(sampleAccount(email = "a@example.com", displayName = "Renombrada"))

        assertEquals(1, dao.count())
        assertEquals("Renombrada", dao.getByEmail("a@example.com")?.displayName)
    }

    @Test
    fun `delete saca la cuenta de observeAccounts`() = runTest {
        dao.upsert(sampleAccount(email = "a@example.com"))

        dao.delete(requireNotNull(dao.getByEmail("a@example.com")))

        assertTrue(dao.observeAccounts().first().isEmpty())
    }

    @Test
    fun `observeAccounts emite ordenado por email`() = runTest {
        dao.upsert(sampleAccount(email = "z@example.com"))
        dao.upsert(sampleAccount(email = "a@example.com"))

        val accounts = dao.observeAccounts().first()

        assertEquals(listOf("a@example.com", "z@example.com"), accounts.map { it.email })
    }

    private fun sampleAccount(
        email: String,
        displayName: String? = null,
        connectedAt: Long = System.currentTimeMillis(),
        isDefault: Boolean = false,
    ) = ConnectedAccountEntity(
        email = email,
        displayName = displayName,
        connectedAt = connectedAt,
        isDefault = isDefault,
    )
}
