package com.santiagojorda.baul.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.santiagojorda.baul.data.local.entity.ExcludedFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedFolderDao {

    @Query("SELECT * FROM excluded_folders ORDER BY folderName")
    fun observeAll(): Flow<List<ExcludedFolderEntity>>

    @Query("SELECT folderName FROM excluded_folders")
    suspend fun getAllNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ExcludedFolderEntity)

    @Delete
    suspend fun delete(entity: ExcludedFolderEntity)
}
