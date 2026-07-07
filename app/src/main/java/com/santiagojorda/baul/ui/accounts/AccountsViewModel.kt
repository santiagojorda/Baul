package com.santiagojorda.baul.ui.accounts

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiagojorda.baul.auth.GoogleAuthManager
import com.santiagojorda.baul.auth.SignInResult
import com.santiagojorda.baul.data.repository.ConnectedAccountRepository
import com.santiagojorda.baul.domain.model.ConnectedAccount
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AccountsEvent {
    data class Error(val message: String) : AccountsEvent
}

class AccountsViewModel(
    private val connectedAccountRepository: ConnectedAccountRepository,
    private val authManager: GoogleAuthManager,
) : ViewModel() {

    val accounts: StateFlow<List<ConnectedAccount>> = connectedAccountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = Channel<AccountsEvent>(Channel.BUFFERED)
    val events: Flow<AccountsEvent> = _events.receiveAsFlow()

    /** Google Sign-In pide email + los 3 scopes en un solo Intent, no hay un segundo paso. */
    fun signInIntent(activity: Activity): Intent = authManager.signInIntent(activity)

    fun onSignInResult(data: Intent?) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = authManager.handleSignInResult(data)) {
                is SignInResult.Success -> {
                    connectedAccountRepository.save(ConnectedAccount(email = result.email, displayName = result.displayName))
                }
                is SignInResult.Failure -> _events.send(AccountsEvent.Error(result.message))
            }
            _isLoading.value = false
        }
    }

    fun removeAccount(account: ConnectedAccount) {
        viewModelScope.launch { connectedAccountRepository.remove(account) }
    }

    /** Cuenta que usa el auto-sync de carpetas nuevas cuando hay más de una conectada. */
    fun setDefault(account: ConnectedAccount) {
        viewModelScope.launch { connectedAccountRepository.setDefault(account.email) }
    }
}
