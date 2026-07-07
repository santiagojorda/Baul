package com.santiagojorda.baul.ui.excludedfolders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiagojorda.baul.data.repository.ExcludedFolderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExcludedFoldersViewModel(private val repository: ExcludedFolderRepository) : ViewModel() {

    val excludedFolders: StateFlow<List<String>> = repository.observeNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(folderName: String) {
        viewModelScope.launch { repository.add(folderName) }
    }

    fun remove(folderName: String) {
        viewModelScope.launch { repository.remove(folderName) }
    }
}
