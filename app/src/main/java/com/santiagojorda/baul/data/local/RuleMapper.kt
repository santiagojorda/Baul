package com.santiagojorda.baul.data.local

import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.DriveMetadata
import com.santiagojorda.baul.domain.model.GooglePhotosMetadata
import com.santiagojorda.baul.domain.model.Rule

fun RuleEntity.toDomain(): Rule = Rule(
    id = id,
    folderUri = folderUri,
    folderDisplayName = folderDisplayName,
    destinationType = destinationType,
    googleAccountEmail = googleAccountEmail,
    googlePhotosMetadata = takeIf { destinationType == DestinationType.GOOGLE_PHOTOS }?.let {
        GooglePhotosMetadata(albumId = photosAlbumId, albumName = photosAlbumName)
    },
    driveMetadata = takeIf { destinationType == DestinationType.DRIVE && driveFolderId != null }?.let {
        DriveMetadata(destinationFolderId = requireNotNull(driveFolderId))
    },
    deleteSourceAfterUpload = deleteSourceAfterUpload,
    wifiOnly = wifiOnly,
    isActive = isActive,
    createdAt = createdAt,
    folderRelativePath = folderRelativePath,
    isAutoCreated = isAutoCreated,
)

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    folderUri = folderUri,
    folderDisplayName = folderDisplayName,
    destinationType = destinationType,
    googleAccountEmail = googleAccountEmail,
    photosAlbumId = googlePhotosMetadata?.albumId,
    photosAlbumName = googlePhotosMetadata?.albumName,
    driveFolderId = driveMetadata?.destinationFolderId,
    deleteSourceAfterUpload = deleteSourceAfterUpload,
    wifiOnly = wifiOnly,
    isActive = isActive,
    createdAt = createdAt,
    folderRelativePath = folderRelativePath,
    isAutoCreated = isAutoCreated,
)
