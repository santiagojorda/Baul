package com.santiagojorda.mediasync.ui.ruleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiagojorda.mediasync.data.repository.RuleRepository
import com.santiagojorda.mediasync.domain.model.DestinationType
import com.santiagojorda.mediasync.domain.model.DriveMetadata
import com.santiagojorda.mediasync.domain.model.GooglePhotosMetadata
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.model.YouTubeMetadata
import com.santiagojorda.mediasync.domain.model.YouTubePrivacyStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RuleEditorViewModel(
    private val ruleRepository: RuleRepository,
    private val ruleId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleEditorUiState(isLoading = ruleId != null))
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    init {
        if (ruleId != null) {
            viewModelScope.launch {
                val rule = ruleRepository.getRuleById(ruleId)
                _uiState.value = rule?.toEditorUiState() ?: RuleEditorUiState()
            }
        }
    }

    fun onFolderPicked(uri: String, displayName: String) =
        _uiState.update { it.copy(folderUri = uri, folderDisplayName = displayName) }

    fun onDestinationTypeChanged(type: DestinationType) = _uiState.update { it.copy(destinationType = type) }

    fun onGoogleAccountEmailChanged(value: String) = _uiState.update { it.copy(googleAccountEmail = value) }

    fun onYouTubeChannelIdChanged(value: String) = _uiState.update { it.copy(youTubeChannelId = value) }

    fun onYouTubePlaylistIdChanged(value: String) = _uiState.update { it.copy(youTubePlaylistId = value) }

    fun onYouTubePrivacyStatusChanged(value: YouTubePrivacyStatus) =
        _uiState.update { it.copy(youTubePrivacyStatus = value) }

    fun onYouTubeTagsChanged(value: String) = _uiState.update { it.copy(youTubeTags = value) }

    fun onPhotosAlbumNameChanged(value: String) = _uiState.update { it.copy(photosAlbumName = value) }

    fun onDriveFolderIdChanged(value: String) = _uiState.update { it.copy(driveFolderId = value) }

    fun onDeleteSourceAfterUploadChanged(value: Boolean) = _uiState.update { it.copy(deleteSourceAfterUpload = value) }

    fun onWifiOnlyChanged(value: Boolean) = _uiState.update { it.copy(wifiOnly = value) }

    fun save() {
        val state = _uiState.value
        val folderUri = state.folderUri ?: return
        if (!state.canSave) return

        viewModelScope.launch {
            val rule = Rule(
                id = ruleId ?: 0,
                folderUri = folderUri,
                folderDisplayName = state.folderDisplayName,
                destinationType = state.destinationType,
                googleAccountEmail = state.googleAccountEmail,
                youTubeMetadata = if (state.destinationType == DestinationType.YOUTUBE) {
                    YouTubeMetadata(
                        channelId = state.youTubeChannelId,
                        playlistId = state.youTubePlaylistId.ifBlank { null },
                        privacyStatus = state.youTubePrivacyStatus,
                        tags = state.youTubeTags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    )
                } else null,
                googlePhotosMetadata = if (state.destinationType == DestinationType.GOOGLE_PHOTOS) {
                    GooglePhotosMetadata(albumName = state.photosAlbumName)
                } else null,
                driveMetadata = if (state.destinationType == DestinationType.DRIVE) {
                    DriveMetadata(destinationFolderId = state.driveFolderId)
                } else null,
                deleteSourceAfterUpload = state.deleteSourceAfterUpload,
                wifiOnly = state.wifiOnly,
                isActive = state.isActive,
                createdAt = state.createdAt,
            )
            ruleRepository.save(rule)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
