package com.santiagojorda.mediasync.ui.ruleeditor

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.santiagojorda.mediasync.domain.model.DestinationType
import com.santiagojorda.mediasync.domain.model.YouTubePrivacyStatus

@Composable
fun RuleEditorScreen(
    viewModel: RuleEditorViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onFolderPicked(uri.toString(), folderDisplayNameFrom(uri))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Carpeta a vigilar", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { folderPicker.launch(null) }) {
            Text(text = state.folderDisplayName.ifBlank { "Elegir carpeta…" })
        }

        HorizontalDivider()

        Text(text = "Destino", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DestinationType.entries.forEach { type ->
                FilterChip(
                    selected = state.destinationType == type,
                    onClick = { viewModel.onDestinationTypeChanged(type) },
                    label = { Text(destinationLabel(type)) },
                )
            }
        }

        OutlinedTextField(
            value = state.googleAccountEmail,
            onValueChange = viewModel::onGoogleAccountEmailChanged,
            label = { Text("Cuenta de Google (email)") },
            modifier = Modifier.fillMaxWidth(),
        )

        when (state.destinationType) {
            DestinationType.YOUTUBE -> YouTubeFields(state, viewModel)
            DestinationType.GOOGLE_PHOTOS -> GooglePhotosFields(state, viewModel)
            DestinationType.DRIVE -> DriveFields(state, viewModel)
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Borrar original tras subir")
            Switch(checked = state.deleteSourceAfterUpload, onCheckedChange = viewModel::onDeleteSourceAfterUploadChanged)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Solo con wifi")
            Switch(checked = state.wifiOnly, onCheckedChange = viewModel::onWifiOnlyChanged)
        }

        Button(
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Guardar regla")
        }
    }
}

@Composable
private fun YouTubeFields(state: RuleEditorUiState, viewModel: RuleEditorViewModel) {
    OutlinedTextField(
        value = state.youTubeChannelId,
        onValueChange = viewModel::onYouTubeChannelIdChanged,
        label = { Text("Channel ID") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.youTubePlaylistId,
        onValueChange = viewModel::onYouTubePlaylistIdChanged,
        label = { Text("Playlist ID (opcional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        YouTubePrivacyStatus.entries.forEach { status ->
            FilterChip(
                selected = state.youTubePrivacyStatus == status,
                onClick = { viewModel.onYouTubePrivacyStatusChanged(status) },
                label = { Text(privacyLabel(status)) },
            )
        }
    }
    OutlinedTextField(
        value = state.youTubeTags,
        onValueChange = viewModel::onYouTubeTagsChanged,
        label = { Text("Tags (separados por coma)") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GooglePhotosFields(state: RuleEditorUiState, viewModel: RuleEditorViewModel) {
    OutlinedTextField(
        value = state.photosAlbumName,
        onValueChange = viewModel::onPhotosAlbumNameChanged,
        label = { Text("Nombre de álbum (se crea si no existe)") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DriveFields(state: RuleEditorUiState, viewModel: RuleEditorViewModel) {
    OutlinedTextField(
        value = state.driveFolderId,
        onValueChange = viewModel::onDriveFolderIdChanged,
        label = { Text("ID de carpeta de Drive destino") },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun destinationLabel(type: DestinationType): String = when (type) {
    DestinationType.YOUTUBE -> "YouTube"
    DestinationType.GOOGLE_PHOTOS -> "Google Photos"
    DestinationType.DRIVE -> "Drive"
}

private fun privacyLabel(status: YouTubePrivacyStatus): String = when (status) {
    YouTubePrivacyStatus.PRIVATE -> "Privado"
    YouTubePrivacyStatus.UNLISTED -> "No listado"
    YouTubePrivacyStatus.PUBLIC -> "Público"
}

private fun folderDisplayNameFrom(uri: Uri): String {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: return uri.lastPathSegment.orEmpty()
    return docId.substringAfterLast('/').ifEmpty { docId }
}
