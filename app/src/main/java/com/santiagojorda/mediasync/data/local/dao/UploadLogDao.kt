package com.santiagojorda.mediasync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.santiagojorda.mediasync.data.local.entity.UploadLogEntity
import com.santiagojorda.mediasync.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadLogDao {

    @Query("SELECT * FROM upload_log ORDER BY createdAt DESC")
    fun observeLogs(): Flow<List<UploadLogEntity>>

    @Query("SELECT * FROM upload_log WHERE status = :status")
    suspend fun getByStatus(status: UploadStatus): List<UploadLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: UploadLogEntity): Long

    @Update
    suspend fun update(entry: UploadLogEntity)
}
