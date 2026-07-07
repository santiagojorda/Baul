package com.santiagojorda.mediasync.data.repository

import com.santiagojorda.mediasync.data.local.dao.ConnectedAccountDao
import com.santiagojorda.mediasync.data.local.toDomain
import com.santiagojorda.mediasync.data.local.toEntity
import com.santiagojorda.mediasync.domain.model.ConnectedAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConnectedAccountRepository(private val dao: ConnectedAccountDao) {

    fun observeAccounts(): Flow<List<ConnectedAccount>> =
        dao.observeAccounts().map { entities -> entities.map { it.toDomain() } }

    suspend fun getByEmail(email: String): ConnectedAccount? = dao.getByEmail(email)?.toDomain()

    suspend fun save(account: ConnectedAccount) = dao.upsert(account.toEntity())

    suspend fun remove(account: ConnectedAccount) = dao.delete(account.toEntity())
}
