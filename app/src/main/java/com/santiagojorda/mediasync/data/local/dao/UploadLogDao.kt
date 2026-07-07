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

    @Query("SELECT * FROM upload_log WHERE status = 'SUCCESS' AND sourceDeleted = 0")
    suspend fun getSuccessfulNotYetDeleted(): List<UploadLogEntity>

    @Query("SELECT * FROM upload_log WHERE ruleId = :ruleId AND status = 'FAILED'")
    suspend fun getFailedForRule(ruleId: Long): List<UploadLogEntity>

    @Query("UPDATE upload_log SET sourceDeleted = 1 WHERE id IN (:ids)")
    suspend fun markSourceDeleted(ids: List<Long>)

    @Query(
        "SELECT * FROM upload_log WHERE ruleId = :ruleId AND mediaUri = :mediaUri " +
            "ORDER BY createdAt DESC LIMIT 1",
    )
    suspend fun getLogForMedia(ruleId: Long, mediaUri: String): UploadLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: UploadLogEntity): Long

    @Update
    suspend fun update(entry: UploadLogEntity)
}
