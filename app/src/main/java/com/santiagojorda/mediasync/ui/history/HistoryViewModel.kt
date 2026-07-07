package com.santiagojorda.mediasync.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiagojorda.mediasync.data.repository.UploadLogRepository
import com.santiagojorda.mediasync.domain.model.UploadLogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val uploadLogRepository: UploadLogRepository) : ViewModel() {

    val logs: StateFlow<List<UploadLogEntry>> = uploadLogRepository.observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(entry: UploadLogEntry) {
        viewModelScope.launch { uploadLogRepository.retry(entry) }
    }
}
