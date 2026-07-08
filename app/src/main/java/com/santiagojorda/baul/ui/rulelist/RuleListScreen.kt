package com.santiagojorda.baul.ui.rulelist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.ui.common.EmptyState
import com.santiagojorda.baul.ui.common.ListItemCard
import com.santiagojorda.baul.ui.common.StatusChip
import com.santiagojorda.baul.ui.common.presentation

@Composable
fun RuleListScreen(
    viewModel: RuleListViewModel,
    onEditRule: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.items.collectAsState()

    if (items.isEmpty()) {
        EmptyState(message = "Todavía no hay reglas. Tocá + para crear la primera.", modifier = modifier)
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items, key = { it.rule.id }) { item ->
            RuleListRow(
                item = item,
                onToggleActive = { viewModel.setActive(item.rule, it) },
                onEdit = { onEditRule(item.rule.id) },
                onDelete = { viewModel.delete(item.rule) },
                onExclude = { viewModel.exclude(item.rule) },
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
    onExclude: () -> Unit,
    onRetry: () -> Unit,
) {
    ListItemCard(onClick = onEdit) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.rule.folderDisplayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = destinationLabel(item.rule.destinationType) + if (item.rule.isAutoCreated) " · automática" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            val lastSyncPresentation = item.lastSyncStatus?.presentation(item.lastSyncAttemptCount)
            if (lastSyncPresentation != null) {
                StatusChip(presentation = lastSyncPresentation, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(
                    text = "Sin sincronizaciones todavía",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = item.rule.isActive, onCheckedChange = onToggleActive)
        if (item.hasFailedUploads) {
            IconButton(onClick = onRetry) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reintentar fallidos")
            }
        }
        IconButton(onClick = onExclude) {
            Icon(imageVector = Icons.Default.Block, contentDescription = "Excluir carpeta (no volver a sincronizar sola)")
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar regla")
        }
    }
}

private fun destinationLabel(type: DestinationType): String = when (type) {
    DestinationType.GOOGLE_PHOTOS -> "Google Photos"
    DestinationType.DRIVE -> "Drive"
}
