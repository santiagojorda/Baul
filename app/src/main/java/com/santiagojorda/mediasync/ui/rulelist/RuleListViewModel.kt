package com.santiagojorda.mediasync.ui.rulelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiagojorda.mediasync.data.repository.RuleRepository
import com.santiagojorda.mediasync.data.repository.UploadLogRepository
import com.santiagojorda.mediasync.domain.model.Rule
import com.santiagojorda.mediasync.domain.model.UploadStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RuleListItem(
    val rule: Rule,
    val lastSyncStatus: UploadStatus? = null,
    val lastSyncAt: Long? = null,
    /** Error real (ya agotó los reintentos), no un fallo transitorio que todavía está reintentando solo. */
    val hasFailedUploads: Boolean = false,
)

class RuleListViewModel(
    private val ruleRepository: RuleRepository,
    private val uploadLogRepository: UploadLogRepository,
) : ViewModel() {

    val items: StateFlow<List<RuleListItem>> = combine(
        ruleRepository.observeRules(),
        uploadLogRepository.observeLogs(),
    ) { rules, logs ->
        rules.map { rule ->
            val ruleLogs = logs.filter { it.ruleId == rule.id }
            val lastLog = ruleLogs.maxByOrNull { it.updatedAt }
            RuleListItem(
                rule = rule,
                lastSyncStatus = lastLog?.status,
                lastSyncAt = lastLog?.updatedAt,
                hasFailedUploads = ruleLogs.any { it.status == UploadStatus.FAILED },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setActive(rule: Rule, isActive: Boolean) {
        viewModelScope.launch { ruleRepository.setActive(rule, isActive) }
    }

    fun delete(rule: Rule) {
        viewModelScope.launch { ruleRepository.delete(rule) }
    }

    fun retryFailed(rule: Rule) {
        viewModelScope.launch { uploadLogRepository.retryAllFailedForRule(rule.id) }
    }
}
