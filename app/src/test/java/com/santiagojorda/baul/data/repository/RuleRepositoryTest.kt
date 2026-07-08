package com.santiagojorda.baul.data.repository

import com.santiagojorda.baul.data.local.dao.RuleDao
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.Rule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRuleDao : RuleDao {
    private val flow = MutableStateFlow<List<RuleEntity>>(emptyList())
    private var nextId = 1L

    override fun observeRules(): Flow<List<RuleEntity>> = flow

    override suspend fun getRuleById(id: Long): RuleEntity? = flow.value.find { it.id == id }

    override suspend fun getActiveRules(): List<RuleEntity> = flow.value.filter { it.isActive }

    override suspend fun getAllRules(): List<RuleEntity> = flow.value

    override suspend fun upsert(rule: RuleEntity): Long {
        val id = if (rule.id == 0L) nextId++ else rule.id
        val stored = rule.copy(id = id)
        flow.value = flow.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun delete(rule: RuleEntity) {
        flow.value = flow.value.filterNot { it.id == rule.id }
    }
}

class RuleRepositoryTest {

    private val dao = FakeRuleDao()
    private val repository = RuleRepository(dao)

    @Test
    fun `save asigna un id autogenerado y getRuleById lo encuentra`() = runTest {
        val id = repository.save(sampleRule())

        val fetched = repository.getRuleById(id)

        assertEquals(id, fetched?.id)
        assertEquals("Foo", fetched?.folderDisplayName)
    }

    @Test
    fun `getRuleById para un id inexistente devuelve null`() = runTest {
        assertNull(repository.getRuleById(999))
    }

    @Test
    fun `setActive persiste el nuevo estado sin tocar el resto de la regla`() = runTest {
        val id = repository.save(sampleRule(isActive = true))
        val saved = requireNotNull(repository.getRuleById(id))

        repository.setActive(saved, isActive = false)

        val updated = requireNotNull(repository.getRuleById(id))
        assertEquals(false, updated.isActive)
        assertEquals(saved.folderDisplayName, updated.folderDisplayName)
        assertEquals(saved.id, updated.id)
    }

    @Test
    fun `delete quita la regla del listado`() = runTest {
        val id = repository.save(sampleRule())
        val saved = requireNotNull(repository.getRuleById(id))

        repository.delete(saved)

        assertNull(repository.getRuleById(id))
    }

    @Test
    fun `observeRules mapea las entidades guardadas a dominio`() = runTest {
        repository.save(sampleRule(folderDisplayName = "Camera"))

        val rules = repository.observeRules().first()

        assertEquals(1, rules.size)
        assertEquals("Camera", rules[0].folderDisplayName)
    }

    @Test
    fun `save con la misma regla dos veces (mismo id) actualiza en vez de duplicar`() = runTest {
        val id = repository.save(sampleRule(folderDisplayName = "Original"))
        val saved = requireNotNull(repository.getRuleById(id))

        repository.save(saved.copy(folderDisplayName = "Renombrada"))

        val rules = repository.observeRules().first()
        assertEquals(1, rules.size)
        assertTrue(rules.all { it.folderDisplayName == "Renombrada" })
    }

    private fun sampleRule(isActive: Boolean = true, folderDisplayName: String = "Foo") = Rule(
        folderUri = "content://tree/foo",
        folderDisplayName = folderDisplayName,
        destinationType = DestinationType.GOOGLE_PHOTOS,
        googleAccountEmail = "user@example.com",
        isActive = isActive,
        createdAt = 1000,
    )
}
