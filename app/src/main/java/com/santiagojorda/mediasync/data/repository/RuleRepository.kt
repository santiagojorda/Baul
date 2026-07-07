package com.santiagojorda.mediasync.data.repository

import com.santiagojorda.mediasync.data.local.dao.RuleDao
import com.santiagojorda.mediasync.data.local.toDomain
import com.santiagojorda.mediasync.data.local.toEntity
import com.santiagojorda.mediasync.domain.model.Rule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RuleRepository(private val ruleDao: RuleDao) {

    fun observeRules(): Flow<List<Rule>> =
        ruleDao.observeRules().map { entities -> entities.map { it.toDomain() } }

    suspend fun getRuleById(id: Long): Rule? = ruleDao.getRuleById(id)?.toDomain()

    suspend fun save(rule: Rule): Long = ruleDao.upsert(rule.toEntity())

    suspend fun setActive(rule: Rule, isActive: Boolean) {
        ruleDao.upsert(rule.copy(isActive = isActive).toEntity())
    }

    suspend fun delete(rule: Rule) {
        ruleDao.delete(rule.toEntity())
    }
}
