package com.santiagojorda.mediasync.domain.model

/**
 * Una regla "carpeta -> destino con esta metadata". `youTubeMetadata`, `googlePhotosMetadata`
 * y `driveMetadata` son mutuamente excluyentes: solo el que corresponde a [destinationType]
 * debería estar presente.
 */
data class Rule(
    val id: Long = 0,
    val folderUri: String,
    val folderDisplayName: String,
    val destinationType: DestinationType,
    val googleAccountEmail: String,
    val youTubeMetadata: YouTubeMetadata? = null,
    val googlePhotosMetadata: GooglePhotosMetadata? = null,
    val driveMetadata: DriveMetadata? = null,
    val deleteSourceAfterUpload: Boolean = true,
    val wifiOnly: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

data class YouTubeMetadata(
    val channelId: String,
    val playlistId: String? = null,
    val privacyStatus: YouTubePrivacyStatus = YouTubePrivacyStatus.PRIVATE,
    val tags: List<String> = emptyList(),
)

data class GooglePhotosMetadata(
    val albumId: String? = null,
    val albumName: String? = null,
)

data class DriveMetadata(
    val destinationFolderId: String,
)
