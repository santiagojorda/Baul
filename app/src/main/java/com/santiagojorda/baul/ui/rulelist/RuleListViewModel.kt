package com.santiagojorda.baul.ui.rulelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiagojorda.baul.data.repository.ExcludedFolderRepository
import com.santiagojorda.baul.data.repository.RuleRepository
import com.santiagojorda.baul.data.repository.UploadLogRepository
import com.santiagojorda.baul.domain.model.Rule
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.media.MediaSyncCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RuleListItem(
    val rule: Rule,
    val lastSyncStatus: UploadStatus? = null,
    val lastSyncAt: Long? = null,
    val lastSyncAttemptCount: Int = 0,
    /** Error real (ya agotó los reintentos), no un fallo transitorio que todavía está reintentando solo. */
    val hasFailedUploads: Boolean = false,
)

class RuleListViewModel(
    private val ruleRepository: RuleRepository,
    private val uploadLogRepository: UploadLogRepository,
    private val mediaSyncCoordinator: MediaSyncCoordinator,
    private val excludedFolderRepository: ExcludedFolderRepository,
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
                lastSyncAttemptCount = lastLog?.attemptCount ?: 0,
                hasFailedUploads = ruleLogs.any { it.status == UploadStatus.FAILED },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setActive(rule: Rule, isActive: Boolean) {
        viewModelScope.launch {
            ruleRepository.setActive(rule, isActive)
            if (isActive) {
                // Al prenderla, sincroniza ya lo que ya estaba en la carpeta en vez de esperar al
                // próximo escaneo periódico (hasta 15 min) o a que se abra la app de nuevo.
                mediaSyncCoordinator.backfillRule(rule.id)
            } else {
                // Al apagarla, se corta lo que esté en vuelo pero se conserva el historial ya hecho.
                uploadLogRepository.cancelActiveUploadsForRule(rule.id)
            }
        }
    }

    fun delete(rule: Rule) {
        viewModelScope.launch { ruleRepository.delete(rule) }
    }

    /**
     * Excluye la carpeta del auto-sync (para que no se vuelva a crear sola) y borra esta regla
     * de una: es un solo botón para "no quiero esto nunca más", a diferencia del switch (que solo
     * la pausa) o del borrado simple (que no evita que se vuelva a crear si sigue llegando contenido).
     */
    fun exclude(rule: Rule) {
        viewModelScope.launch {
            excludedFolderRepository.add(rule.folderDisplayName)
            ruleRepository.delete(rule)
        }
    }

    fun retryFailed(rule: Rule) {
        viewModelScope.launch { uploadLogRepository.retryAllFailedForRule(rule.id) }
    }
}
