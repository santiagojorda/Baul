package com.santiagojorda.baul.data.repository

import com.santiagojorda.baul.data.local.dao.ExcludedFolderDao
import com.santiagojorda.baul.data.local.entity.ExcludedFolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExcludedFolderRepository(private val dao: ExcludedFolderDao) {

    fun observeNames(): Flow<List<String>> = dao.observeAll().map { entities -> entities.map { it.folderName } }

    suspend fun getNames(): Set<String> = dao.getAllNames().toSet()

    suspend fun add(folderName: String) {
        val normalized = folderName.trim().lowercase()
        if (normalized.isEmpty()) return
        dao.insert(ExcludedFolderEntity(normalized))
    }

    suspend fun remove(folderName: String) {
        dao.delete(ExcludedFolderEntity(folderName))
    }
}
