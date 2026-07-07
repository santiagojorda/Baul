package com.santiagojorda.mediasync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.santiagojorda.mediasync.domain.model.DestinationType
import com.santiagojorda.mediasync.domain.model.YouTubePrivacyStatus

/**
 * Fila plana con columnas nullable por destino en vez de polimorfismo, ya que Room no
 * soporta bien tipos "uno de varios" embebidos. Solo las columnas del [destinationType]
 * correspondiente deberían estar pobladas.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderUri: String,
    val folderDisplayName: String,
    val destinationType: DestinationType,
    val googleAccountEmail: String,
    val youTubeChannelId: String? = null,
    val youTubePlaylistId: String? = null,
    val youTubePrivacyStatus: YouTubePrivacyStatus? = null,
    val youTubeTags: List<String> = emptyList(),
    val photosAlbumId: String? = null,
    val photosAlbumName: String? = null,
    val driveFolderId: String? = null,
    val deleteSourceAfterUpload: Boolean = true,
    val wifiOnly: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
