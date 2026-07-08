package com.santiagojorda.baul.data.local.dao

import androidx.room.Room
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.entity.ExcludedFolderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Contra una base Room real en memoria (no un fake): [ExcludedFolderRepositoryTest] usa un fake
 * que nunca ejercita el SQL real, en particular el `OnConflictStrategy.IGNORE` de [ExcludedFolderDao.insert].
 */
@RunWith(RobolectricTestRunner::class)
class ExcludedFolderDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ExcludedFolderDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.excludedFolderDao()
    }

    @Test
    fun `insert con el mismo folderName dos veces no falla, gracias a OnConflictStrategy IGNORE`() = runTest {
        dao.insert(ExcludedFolderEntity(folderName = "Screenshots"))

        dao.insert(ExcludedFolderEntity(folderName = "Screenshots"))

        assertEquals(listOf("Screenshots"), dao.getAllNames())
    }

    @Test
    fun `getAllNames devuelve todas las carpetas excluidas`() = runTest {
        dao.insert(ExcludedFolderEntity(folderName = "Screenshots"))
        dao.insert(ExcludedFolderEntity(folderName = "WhatsApp Images"))

        assertEquals(setOf("Screenshots", "WhatsApp Images"), dao.getAllNames().toSet())
    }

    @Test
    fun `delete saca la carpeta de la lista`() = runTest {
        dao.insert(ExcludedFolderEntity(folderName = "Screenshots"))

        dao.delete(ExcludedFolderEntity(folderName = "Screenshots"))

        assertTrue(dao.getAllNames().isEmpty())
    }

    @Test
    fun `observeAll emite ordenado por folderName`() = runTest {
        dao.insert(ExcludedFolderEntity(folderName = "Zebra"))
        dao.insert(ExcludedFolderEntity(folderName = "Abeja"))

        val names = dao.observeAll().first().map { it.folderName }

        assertEquals(listOf("Abeja", "Zebra"), names)
    }
}
