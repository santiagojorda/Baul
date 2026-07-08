package com.santiagojorda.baul.data.local.dao

import androidx.room.Room
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.domain.model.DestinationType
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
 * Contra una base Room real en memoria (no un fake): [RuleRepositoryTest] y compañía usan un
 * `FakeRuleDao` que nunca ejercita el SQL real de estas queries.
 */
@RunWith(RobolectricTestRunner::class)
class RuleDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: RuleDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.ruleDao()
    }

    @Test
    fun `upsert autogenera el id cuando se inserta con id 0`() = runTest {
        val id = dao.upsert(sampleRule(id = 0, folderDisplayName = "Camera"))

        assertTrue("el id autogenerado debe ser positivo", id > 0)
        assertEquals("Camera", dao.getRuleById(id)?.folderDisplayName)
    }

    @Test
    fun `upsert con OnConflictStrategy REPLACE pisa la fila existente en vez de duplicarla`() = runTest {
        val id = dao.upsert(sampleRule(id = 0, folderDisplayName = "Camera"))

        dao.upsert(sampleRule(id = id, folderDisplayName = "Camera renombrada"))

        assertEquals(1, dao.getAllRules().size)
        assertEquals("Camera renombrada", dao.getRuleById(id)?.folderDisplayName)
    }

    @Test
    fun `getActiveRules solo devuelve las que tienen isActive en true`() = runTest {
        dao.upsert(sampleRule(id = 0, folderDisplayName = "Activa", isActive = true))
        dao.upsert(sampleRule(id = 0, folderDisplayName = "Inactiva", isActive = false))

        val active = dao.getActiveRules()

        assertEquals(1, active.size)
        assertEquals("Activa", active[0].folderDisplayName)
    }

    @Test
    fun `getRuleById devuelve null para un id que no existe`() = runTest {
        assertNull(dao.getRuleById(999))
    }

    @Test
    fun `delete saca la regla de getAllRules`() = runTest {
        val id = dao.upsert(sampleRule(id = 0, folderDisplayName = "Camera"))
        val rule = requireNotNull(dao.getRuleById(id))

        dao.delete(rule)

        assertTrue(dao.getAllRules().isEmpty())
    }

    @Test
    fun `observeRules emite en orden de createdAt descendente`() = runTest {
        dao.upsert(sampleRule(id = 0, folderDisplayName = "Mas vieja", createdAt = 1000))
        dao.upsert(sampleRule(id = 0, folderDisplayName = "Mas nueva", createdAt = 2000))

        val rules = dao.observeRules().first()

        assertEquals(listOf("Mas nueva", "Mas vieja"), rules.map { it.folderDisplayName })
    }

    private fun sampleRule(
        id: Long,
        folderDisplayName: String,
        isActive: Boolean = true,
        createdAt: Long = System.currentTimeMillis(),
    ) = RuleEntity(
        id = id,
        folderUri = "content://tree/foo",
        folderDisplayName = folderDisplayName,
        destinationType = DestinationType.GOOGLE_PHOTOS,
        googleAccountEmail = "user@example.com",
        isActive = isActive,
        createdAt = createdAt,
    )
}
