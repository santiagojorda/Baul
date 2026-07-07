package com.santiagojorda.mediasync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.santiagojorda.mediasync.data.local.entity.YouTubeQuotaUsageEntity

@Dao
interface YouTubeQuotaDao {

    @Query("SELECT * FROM youtube_quota_usage WHERE date = :date")
    suspend fun getForDate(date: String): YouTubeQuotaUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: YouTubeQuotaUsageEntity)
}
