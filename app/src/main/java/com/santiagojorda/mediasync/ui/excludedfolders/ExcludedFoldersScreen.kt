package com.santiagojorda.mediasync.ui.excludedfolders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExcludedFoldersScreen(
    viewModel: ExcludedFoldersViewModel,
    modifier: Modifier = Modifier,
) {
    val excludedFolders by viewModel.excludedFolders.collectAsState()
    var newFolderName by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "El auto-sync a Google Photos ignora carpetas nuevas cuyo nombre empiece con \"_\", " +
                "las de Cámara/Screenshots/WhatsApp/Telegram, y las que agregues acá.",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                label = { Text("Nombre de carpeta") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    viewModel.add(newFolderName)
                    newFolderName = ""
                },
                enabled = newFolderName.isNotBlank(),
            ) {
                Text("Agregar")
            }
        }

        if (excludedFolders.isEmpty()) {
            Text(
                text = "Todavía no agregaste ninguna exclusión manual.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(excludedFolders, key = { it }) { folderName ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = folderName)
                            IconButton(onClick = { viewModel.remove(folderName) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Quitar exclusión")
                            }
                        }
                    }
                }
            }
        }
    }
}
