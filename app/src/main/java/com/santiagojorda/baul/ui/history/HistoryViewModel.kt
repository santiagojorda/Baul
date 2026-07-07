package com.santiagojorda.baul.ui.history

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

data class HistoryGroup(
    val ruleId: Long,
    val folderName: String,
    val entries: List<UploadLogEntry>,
)

class HistoryViewModel(
    private val uploadLogRepository: UploadLogRepository,
    ruleRepository: RuleRepository,
) : ViewModel() {

    val groups: StateFlow<List<HistoryGroup>> = combine(
        uploadLogRepository.observeLogs(),
        ruleRepository.observeRules(),
    ) { logs, rules ->
        val folderNameByRuleId = rules.associate { it.id to it.folderDisplayName }
        logs.groupBy { it.ruleId }
            .map { (ruleId, entries) ->
                HistoryGroup(
                    ruleId = ruleId,
                    folderName = folderNameByRuleId[ruleId] ?: "Carpeta eliminada",
                    entries = entries.sortedByDescending { it.updatedAt },
                )
            }
            .sortedByDescending { group -> group.entries.maxOf { it.updatedAt } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(entry: UploadLogEntry) {
        viewModelScope.launch { uploadLogRepository.retry(entry) }
    }

    fun cancel(entry: UploadLogEntry) {
        viewModelScope.launch { uploadLogRepository.cancel(entry) }
    }
}
