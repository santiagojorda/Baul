package com.santiagojorda.baul.data.repository

import com.santiagojorda.baul.data.local.dao.ConnectedAccountDao
import com.santiagojorda.baul.data.local.toDomain
import com.santiagojorda.baul.data.local.toEntity
import com.santiagojorda.baul.domain.model.ConnectedAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConnectedAccountRepository(private val dao: ConnectedAccountDao) {

    fun observeAccounts(): Flow<List<ConnectedAccount>> =
        dao.observeAccounts().map { entities -> entities.map { it.toDomain() } }

    suspend fun getByEmail(email: String): ConnectedAccount? = dao.getByEmail(email)?.toDomain()

    /** La que usa el auto-sync de carpetas nuevas. Si por algún motivo ninguna quedó marcada, cae a la primera conectada. */
    suspend fun getDefault(): ConnectedAccount? = (dao.getDefault() ?: dao.getFirstConnected())?.toDomain()

    /** La primera cuenta que se conecta queda default automáticamente; las siguientes no le pisan el default a la existente. */
    suspend fun save(account: ConnectedAccount) {
        val existing = dao.getByEmail(account.email)
        val isDefault = existing?.isDefault ?: (dao.count() == 0)
        dao.upsert(account.copy(isDefault = isDefault).toEntity())
    }

    suspend fun setDefault(email: String) {
        dao.clearDefault()
        dao.markAsDefault(email)
    }

    suspend fun remove(account: ConnectedAccount) = dao.delete(account.toEntity())
}
