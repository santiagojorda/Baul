package com.santiagojorda.mediasync.ui.history

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.santiagojorda.mediasync.domain.model.UploadLogEntry
import com.santiagojorda.mediasync.domain.model.UploadStatus

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val logs by viewModel.logs.collectAsState()

    if (logs.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Todavía no se procesó ningún archivo.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(logs, key = { it.id }) { entry ->
            HistoryRow(entry = entry, onRetry = { viewModel.retry(entry) })
        }
    }
}

@Composable
private fun HistoryRow(entry: UploadLogEntry, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.fileName, style = MaterialTheme.typography.titleMedium)
                Text(text = statusLabel(entry.status), style = MaterialTheme.typography.bodyMedium)
                entry.errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (entry.status == UploadStatus.FAILED) {
                IconButton(onClick = onRetry) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reintentar")
                }
            }
        }
    }
}

private fun statusLabel(status: UploadStatus): String = when (status) {
    UploadStatus.PENDING -> "Pendiente"
    UploadStatus.UPLOADING -> "Subiendo…"
    UploadStatus.SUCCESS -> "Subido"
    UploadStatus.FAILED -> "Error"
}
