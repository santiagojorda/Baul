package com.santiagojorda.baul.domain.model

/**
 * Una regla "carpeta -> destino con esta metadata". `googlePhotosMetadata` y `driveMetadata`
 * son mutuamente excluyentes: solo el que corresponde a [destinationType] debería estar presente.
 */
data class Rule(
    val id: Long = 0,
    val folderUri: String,
    val folderDisplayName: String,
    val destinationType: DestinationType,
    val googleAccountEmail: String,
    val googlePhotosMetadata: GooglePhotosMetadata? = null,
    val driveMetadata: DriveMetadata? = null,
    val deleteSourceAfterUpload: Boolean = true,
    val wifiOnly: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val folderRelativePath: String? = null,
    val isAutoCreated: Boolean = false,
)

data class GooglePhotosMetadata(
    val albumId: String? = null,
    val albumName: String? = null,
)

data class DriveMetadata(
    val destinationFolderId: String,
)
