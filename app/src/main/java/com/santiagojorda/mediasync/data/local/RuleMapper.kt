package com.santiagojorda.mediasync.data.local

import com.santiagojorda.mediasync.data.local.entity.RuleEntity
import com.santiagojorda.mediasync.domain.model.DestinationType
import com.santiagojorda.mediasync.domain.model.DriveMetadata
import com.santiagojorda.mediasync.domain.model.GooglePhotosMetadata
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.model.YouTubeMetadata
import com.santiagojorda.mediasync.domain.model.YouTubePrivacyStatus

fun RuleEntity.toDomain(): Rule = Rule(
    id = id,
    folderUri = folderUri,
    folderDisplayName = folderDisplayName,
    destinationType = destinationType,
    googleAccountEmail = googleAccountEmail,
    youTubeMetadata = takeIf { destinationType == DestinationType.YOUTUBE && youTubeChannelId != null }?.let {
        YouTubeMetadata(
            channelId = requireNotNull(youTubeChannelId),
            playlistId = youTubePlaylistId,
            privacyStatus = youTubePrivacyStatus ?: YouTubePrivacyStatus.PRIVATE,
            tags = youTubeTags,
        )
    },
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
)

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    folderUri = folderUri,
    folderDisplayName = folderDisplayName,
    destinationType = destinationType,
    googleAccountEmail = googleAccountEmail,
    youTubeChannelId = youTubeMetadata?.channelId,
    youTubePlaylistId = youTubeMetadata?.playlistId,
    youTubePrivacyStatus = youTubeMetadata?.privacyStatus,
    youTubeTags = youTubeMetadata?.tags ?: emptyList(),
    photosAlbumId = googlePhotosMetadata?.albumId,
    photosAlbumName = googlePhotosMetadata?.albumName,
    driveFolderId = driveMetadata?.destinationFolderId,
    deleteSourceAfterUpload = deleteSourceAfterUpload,
    wifiOnly = wifiOnly,
    isActive = isActive,
    createdAt = createdAt,
)
