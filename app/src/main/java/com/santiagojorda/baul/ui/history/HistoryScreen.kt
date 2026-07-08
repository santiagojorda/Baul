package com.santiagojorda.baul.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.santiagojorda.baul.domain.model.UploadLogEntry
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.ui.common.EmptyState
import com.santiagojorda.baul.ui.common.ListItemCard
import com.santiagojorda.baul.ui.common.StatusChip
import com.santiagojorda.baul.ui.common.presentation
import com.santiagojorda.baul.ui.theme.success

private val LongSetSaver = listSaver<Set<Long>, Long>(
    save = { it.toList() },
    restore = { it.toSet() },
)

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val groups by viewModel.groups.collectAsState()
    // Desplegado solo a mano (Set vacío = todo plegado); así al entrar a Historial no hay que
    // scrollear entre archivos de todas las carpetas para encontrar la que importa.
    var expandedRuleIds by rememberSaveable(stateSaver = LongSetSaver) { mutableStateOf(emptySet<Long>()) }

    if (groups.isEmpty()) {
        EmptyState(message = "Todavía no se procesó ningún archivo.", modifier = modifier)
        return
    }

    val uploadingNow = groups.flatMap { group -> group.entries.filter { it.status == UploadStatus.UPLOADING }.map { group to it } }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (uploadingNow.isNotEmpty()) {
            item(key = "uploading-now") {
                UploadingNowCard(uploadingNow)
            }
        }
        groups.forEach { group ->
            val isExpanded = group.ruleId in expandedRuleIds
            val isFinished = group.entries.isNotEmpty() && group.entries.all { it.status == UploadStatus.SUCCESS }
            item(key = "header-${group.ruleId}") {
                HistoryGroupHeader(
                    group = group,
                    isExpanded = isExpanded,
                    isFinished = isFinished,
                    onToggle = {
                        expandedRuleIds = if (isExpanded) {
                            expandedRuleIds - group.ruleId
                        } else {
                            expandedRuleIds + group.ruleId
                        }
                    },
                )
            }
            if (isExpanded) {
                for (bucket in StatusBucket.entries) {
                    val bucketEntries = group.entries.filter { bucket.matches(it.status) }
                    if (bucketEntries.isEmpty()) continue
                    item(key = "bucket-${group.ruleId}-${bucket.name}") {
                        Text(
                            text = "${bucket.label} (${bucketEntries.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(bucketEntries, key = { it.id }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onRetry = { viewModel.retry(entry) },
                            onCancel = { viewModel.cancel(entry) },
                        )
                    }
                }
            }
        }
    }
}

/** Subdivide los logs de una carpeta para que fallados/cancelados/subiendo/en cola/subidos no se mezclen. */
private enum class StatusBucket(val label: String, val matches: (UploadStatus) -> Boolean) {
    FAILED("Fallaron", { it == UploadStatus.FAILED }),
    UPLOADING("Subiendo ahora", { it == UploadStatus.UPLOADING }),
    PENDING("En cola", { it == UploadStatus.PENDING }),
    CANCELLED("Cancelados", { it == UploadStatus.CANCELLED }),
    SUCCESS("Sincronizados", { it == UploadStatus.SUCCESS }),
}

private fun progressFraction(entry: UploadLogEntry): Float? =
    if (entry.totalBytes > 0) (entry.bytesUploaded.toFloat() / entry.totalBytes.toFloat()).coerceIn(0f, 1f) else null

/**
 * Resumen fijo arriba de todo con lo que está subiendo en este momento, en cualquier carpeta.
 * Google Photos sube de a uno solo (ver [com.santiagojorda.baul.upload.GooglePhotosUploader]
 * para el por qué), así que en la práctica esta lista casi siempre tiene 0 o 1 elemento.
 */
@Composable
private fun UploadingNowCard(uploadingNow: List<Pair<HistoryGroup, UploadLogEntry>>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                Text(
                    text = "Subiendo ahora (${uploadingNow.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            uploadingNow.forEach { (group, entry) ->
                val fraction = progressFraction(entry)
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (fraction == null) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            text = buildString {
                                append(entry.fileName)
                                append(" — ")
                                append(group.folderName)
                                if (fraction != null) append(" (${(fraction * 100).toInt()}%)")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = if (fraction == null) 12.dp else 0.dp),
                        )
                    }
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryGroupHeader(
    group: HistoryGroup,
    isExpanded: Boolean,
    isFinished: Boolean,
    onToggle: () -> Unit,
) {
    val finishedColor = MaterialTheme.colorScheme.success
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isFinished) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completado",
                    tint = finishedColor,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                )
            }
            Text(
                text = "${group.folderName} (${group.entries.size})",
                style = MaterialTheme.typography.titleMedium,
                color = if (isFinished) finishedColor else MaterialTheme.colorScheme.onSurface,
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Plegar" else "Desplegar",
            tint = if (isFinished) finishedColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryRow(entry: UploadLogEntry, onRetry: () -> Unit, onCancel: () -> Unit) {
    ListItemCard {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.fileName, style = MaterialTheme.typography.titleMedium)
            StatusChip(
                presentation = entry.status.presentation(entry.attemptCount),
                modifier = Modifier.padding(top = 4.dp),
            )
            val fraction = progressFraction(entry)
            if (entry.status == UploadStatus.UPLOADING && fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, end = 16.dp),
                )
            }
            entry.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
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
