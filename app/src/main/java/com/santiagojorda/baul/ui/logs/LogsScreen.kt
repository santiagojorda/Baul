package com.santiagojorda.baul.ui.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.ui.common.EmptyState
import com.santiagojorda.baul.ui.common.ListItemCard
import com.santiagojorda.baul.ui.common.StatusChip
import com.santiagojorda.baul.ui.common.presentation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timestampFormat = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())

@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    modifier: Modifier = Modifier,
) {
    val rows by viewModel.rows.collectAsState()

    if (rows.isEmpty()) {
        EmptyState(message = "No hay errores registrados.", modifier = modifier)
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(rows, key = { it.entry.id }) { row ->
            LogRow(
                row = row,
                onRetry = { viewModel.retry(row.entry) },
                onCancel = { viewModel.cancel(row.entry) },
            )
        }
    }
}

@Composable
private fun LogRow(row: LogEntryRow, onRetry: () -> Unit, onCancel: () -> Unit) {
    val entry = row.entry
    ListItemCard {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.fileName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${row.folderName} · ${timestampFormat.format(Date(entry.updatedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusChip(
                presentation = entry.status.presentation(entry.attemptCount),
                modifier = Modifier.padding(top = 4.dp),
            )
            entry.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, end = 16.dp),
                )
            }
        }
        Row {
            when (entry.status) {
                UploadStatus.UPLOADING, UploadStatus.PENDING -> IconButton(onClick = onCancel) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancelar subida")
                }
                UploadStatus.FAILED, UploadStatus.CANCELLED -> IconButton(onClick = onRetry) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reintentar")
                }
                else -> Unit
            }
        }
    }
}
