package com.santiagojorda.mediasync.ui.rulelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.santiagojorda.mediasync.domain.model.DestinationType
import com.santiagojorda.mediasync.domain.model.UploadStatus

@Composable
fun RuleListScreen(
    viewModel: RuleListViewModel,
    onEditRule: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.items.collectAsState()

    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Todavía no hay reglas. Tocá + para crear la primera.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items, key = { it.rule.id }) { item ->
            RuleListRow(
                item = item,
                onToggleActive = { viewModel.setActive(item.rule, it) },
                onEdit = { onEditRule(item.rule.id) },
                onDelete = { viewModel.delete(item.rule) },
                onRetry = { viewModel.retryFailed(item.rule) },
            )
        }
    }
}

@Composable
private fun RuleListRow(
    item: RuleListItem,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.rule.folderDisplayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = destinationLabel(item.rule.destinationType) + if (item.rule.isAutoCreated) " · automática" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = lastSyncLabel(item.lastSyncStatus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = item.rule.isActive, onCheckedChange = onToggleActive)
            if (item.hasFailedUploads) {
                IconButton(onClick = onRetry) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reintentar fallidos")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar regla")
            }
        }
    }
}

private fun destinationLabel(type: DestinationType): String = when (type) {
    DestinationType.YOUTUBE -> "YouTube"
    DestinationType.GOOGLE_PHOTOS -> "Google Photos"
    DestinationType.DRIVE -> "Drive"
}

private fun lastSyncLabel(status: UploadStatus?): String = when (status) {
    null -> "Sin sincronizaciones todavía"
    UploadStatus.PENDING -> "Pendiente"
    UploadStatus.UPLOADING -> "Subiendo…"
    UploadStatus.SUCCESS -> "Última subida: OK"
    UploadStatus.FAILED -> "Última subida: error"
}
