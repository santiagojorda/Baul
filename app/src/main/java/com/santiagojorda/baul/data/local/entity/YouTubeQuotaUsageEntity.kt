package com.santiagojorda.baul.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** [date] en formato "yyyy-MM-dd" (huso horario local): una fila por día con lo gastado ese día. */
@Entity(tableName = "youtube_quota_usage")
data class YouTubeQuotaUsageEntity(
    @PrimaryKey val date: String,
    val unitsUsed: Int,
)
