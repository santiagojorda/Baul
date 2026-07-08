package com.santiagojorda.baul.ui.ruleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiagojorda.baul.data.repository.ConnectedAccountRepository
import com.santiagojorda.baul.data.repository.RuleRepository
import com.santiagojorda.baul.domain.model.ConnectedAccount
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.DriveMetadata
import com.santiagojorda.baul.domain.model.GooglePhotosMetadata
import com.santiagojorda.baul.domain.model.Rule
import com.santiagojorda.baul.domain.model.YouTubeMetadata
import com.santiagojorda.baul.domain.model.YouTubePrivacyStatus
import com.santiagojorda.baul.media.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RuleEditorViewModel(
    private val ruleRepository: RuleRepository,
    connectedAccountRepository: ConnectedAccountRepository,
    private val syncCoordinator: SyncCoordinator,
    private val ruleId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleEditorUiState(isLoading = ruleId != null))
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    val connectedAccounts: StateFlow<List<ConnectedAccount>> = connectedAccountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (ruleId != null) {
            viewModelScope.launch {
                val rule = ruleRepository.getRuleById(ruleId)
                _uiState.value = rule?.toEditorUiState() ?: RuleEditorUiState()
            }
        }
    }

    /** Elegir una carpeta a mano convierte la regla en explícita: se pisa folderRelativePath. */
    fun onFolderPicked(uri: String, displayName: String) =
        _uiState.update { it.copy(folderUri = uri, folderDisplayName = displayName, folderRelativePath = null) }

    fun onDestinationTypeChanged(type: DestinationType) = _uiState.update { it.copy(destinationType = type) }

    fun onGoogleAccountEmailChanged(value: String) = _uiState.update { it.copy(googleAccountEmail = value) }

    fun onYouTubeChannelIdChanged(value: String) = _uiState.update { it.copy(youTubeChannelId = value) }

    fun onYouTubePlaylistIdChanged(value: String) = _uiState.update { it.copy(youTubePlaylistId = value) }

    fun onYouTubePrivacyStatusChanged(value: YouTubePrivacyStatus) =
        _uiState.update { it.copy(youTubePrivacyStatus = value) }

    fun onYouTubeTagsChanged(value: String) = _uiState.update { it.copy(youTubeTags = value) }

    /** Si el usuario cambia el nombre a mano, se despega del álbum ya asociado (id viejo). */
    fun onPhotosAlbumNameChanged(value: String) = _uiState.update { it.copy(photosAlbumName = value, photosAlbumId = null) }

    fun onDriveFolderIdChanged(value: String) = _uiState.update { it.copy(driveFolderId = value) }

    fun onDeleteSourceAfterUploadChanged(value: Boolean) = _uiState.update { it.copy(deleteSourceAfterUpload = value) }

    fun onWifiOnlyChanged(value: Boolean) = _uiState.update { it.copy(wifiOnly = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            val rule = Rule(
                id = ruleId ?: 0,
                folderUri = state.folderUri ?: "",
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
                    GooglePhotosMetadata(albumId = state.photosAlbumId, albumName = state.photosAlbumName)
                } else null,
                driveMetadata = if (state.destinationType == DestinationType.DRIVE) {
                    DriveMetadata(destinationFolderId = state.driveFolderId)
                } else null,
                deleteSourceAfterUpload = state.deleteSourceAfterUpload,
                wifiOnly = state.wifiOnly,
                isActive = state.isActive,
                createdAt = state.createdAt,
                folderRelativePath = state.folderRelativePath,
                isAutoCreated = state.folderRelativePath != null,
            )
            val savedId = ruleRepository.save(rule)
            syncCoordinator.backfillRule(savedId)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
