package com.santiagojorda.mediasync.ui.ruleeditor

import com.santiagojorda.mediasync.domain.model.DestinationType
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.model.YouTubePrivacyStatus

/**
 * Los campos de metadata por destino se guardan todos juntos y se filtran recién al armar el
 * [Rule] en [RuleEditorViewModel.save]; solo se muestran/usan los que correspondan a
 * [destinationType]. `googleAccountEmail` es un campo de texto libre hasta que exista el
 * selector de cuentas real (Credential Manager).
 */
data class RuleEditorUiState(
    val folderUri: String? = null,
    val folderDisplayName: String = "",
    val destinationType: DestinationType = DestinationType.YOUTUBE,
    val googleAccountEmail: String = "",
    val youTubeChannelId: String = "",
    val youTubePlaylistId: String = "",
    val youTubePrivacyStatus: YouTubePrivacyStatus = YouTubePrivacyStatus.PRIVATE,
    val youTubeTags: String = "",
    val photosAlbumName: String = "",
    val driveFolderId: String = "",
    val deleteSourceAfterUpload: Boolean = true,
    val wifiOnly: Boolean = true,
    /** Se preservan al editar una regla existente; no son parte del formulario visible. */
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
) {
    val canSave: Boolean
        // folderUri puede ser "" (no null) en una regla auto-creada por carpeta (ver
        // AutoSyncFolderPolicy): sin una carpeta SAF elegida a mano no se puede guardar, para no
        // pisar el folderRelativePath que la hacía funcionar.
        get() = !folderUri.isNullOrBlank() && googleAccountEmail.isNotBlank() && when (destinationType) {
            DestinationType.YOUTUBE -> youTubeChannelId.isNotBlank()
            DestinationType.GOOGLE_PHOTOS -> photosAlbumName.isNotBlank()
            DestinationType.DRIVE -> driveFolderId.isNotBlank()
        }
}

fun Rule.toEditorUiState(): RuleEditorUiState = RuleEditorUiState(
    folderUri = folderUri,
    folderDisplayName = folderDisplayName,
    destinationType = destinationType,
    googleAccountEmail = googleAccountEmail,
    youTubeChannelId = youTubeMetadata?.channelId ?: "",
    youTubePlaylistId = youTubeMetadata?.playlistId ?: "",
    youTubePrivacyStatus = youTubeMetadata?.privacyStatus ?: YouTubePrivacyStatus.PRIVATE,
    youTubeTags = youTubeMetadata?.tags?.joinToString(", ") ?: "",
    photosAlbumName = googlePhotosMetadata?.albumName ?: "",
    driveFolderId = driveMetadata?.destinationFolderId ?: "",
    deleteSourceAfterUpload = deleteSourceAfterUpload,
    wifiOnly = wifiOnly,
    isActive = isActive,
    createdAt = createdAt,
)
