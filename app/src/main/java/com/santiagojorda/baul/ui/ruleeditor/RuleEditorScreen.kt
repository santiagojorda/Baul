package com.santiagojorda.baul.ui.ruleeditor

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.santiagojorda.baul.domain.model.DestinationType

@Composable
fun RuleEditorScreen(
    viewModel: RuleEditorViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val connectedAccounts by viewModel.connectedAccounts.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
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

        DestinationTypeRow(state, viewModel)

        Text(text = "Cuenta de Google", style = MaterialTheme.typography.titleMedium)
        if (connectedAccounts.isEmpty()) {
            Text(text = "No hay cuentas conectadas todavía. Andá a la pestaña Cuentas para agregar una.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                connectedAccounts.forEach { account ->
                    FilterChip(
                        selected = state.googleAccountEmail == account.email,
                        onClick = { viewModel.onGoogleAccountEmailChanged(account.email) },
                        label = { Text(account.displayName ?: account.email) },
                    )
                }
            }
        }

        when (state.destinationType) {
            DestinationType.GOOGLE_PHOTOS -> GooglePhotosFields(state, viewModel)
            DestinationType.DRIVE -> DriveFields(state, viewModel)
        }

        HorizontalDivider()

        DeleteSourceToggleRow(
            checked = state.deleteSourceAfterUpload,
            onCheckedChange = viewModel::onDeleteSourceAfterUploadChanged,
        )
        WifiOnlyToggleRow(checked = state.wifiOnly, onCheckedChange = viewModel::onWifiOnlyChanged)

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
private fun DestinationTypeRow(state: RuleEditorUiState, viewModel: RuleEditorViewModel) {
    Text(text = "Destino", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Drive todavía no sube nada de verdad (DriveUploader es un stub) — se oculta de acá
        // para que no se pueda crear una regla que va a fallar siempre. El enum/modelo quedan
        // intactos para cuando se implemente la subida real.
        selectableDestinationTypes.forEach { type ->
            FilterChip(
                selected = state.destinationType == type,
                onClick = { viewModel.onDestinationTypeChanged(type) },
                label = { Text(destinationLabel(type)) },
            )
        }
    }
}

@Composable
private fun DeleteSourceToggleRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "Borrar original tras subir")
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WifiOnlyToggleRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "Solo con wifi")
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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

private val selectableDestinationTypes = listOf(DestinationType.GOOGLE_PHOTOS)

private fun destinationLabel(type: DestinationType): String = when (type) {
    DestinationType.GOOGLE_PHOTOS -> "Google Photos"
    DestinationType.DRIVE -> "Drive"
}

private fun folderDisplayNameFrom(uri: Uri): String {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: return uri.lastPathSegment.orEmpty()
    return docId.substringAfterLast('/').ifEmpty { docId }
}
