package com.santiagojorda.baul.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiagojorda.baul.data.repository.RuleRepository
import com.santiagojorda.baul.data.repository.UploadLogRepository
import com.santiagojorda.baul.domain.model.UploadLogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LogEntryRow(val entry: UploadLogEntry, val folderName: String)

/**
 * Lista plana (sin agrupar ni plegar por carpeta) de todo lo que tiene un [UploadLogEntry.errorMessage],
 * para poder ver de un vistazo por qué está fallando o reintentando un archivo sin tener que
 * desplegar cada carpeta en Historial.
 */
class LogsViewModel(
    private val uploadLogRepository: UploadLogRepository,
    ruleRepository: RuleRepository,
) : ViewModel() {

    val rows: StateFlow<List<LogEntryRow>> = combine(
        uploadLogRepository.observeLogs(),
        ruleRepository.observeRules(),
    ) { logs, rules ->
        val folderNameByRuleId = rules.associate { it.id to it.folderDisplayName }
        logs.filter { it.errorMessage != null }
            .sortedByDescending { it.updatedAt }
            .map { entry -> LogEntryRow(entry, folderNameByRuleId[entry.ruleId] ?: "Carpeta eliminada") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(entry: UploadLogEntry) {
        viewModelScope.launch { uploadLogRepository.retry(entry) }
    }

    fun cancel(entry: UploadLogEntry) {
        viewModelScope.launch { uploadLogRepository.cancel(entry) }
    }
}
