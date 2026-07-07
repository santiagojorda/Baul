package com.santiagojorda.mediasync.ui.accounts

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.santiagojorda.mediasync.auth.AuthorizationOutcome
import com.santiagojorda.mediasync.auth.GoogleApiScopes
import com.santiagojorda.mediasync.auth.GoogleAuthManager
import com.santiagojorda.mediasync.auth.SignInResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santiagojorda.mediasync.data.repository.ConnectedAccountRepository
import com.santiagojorda.mediasync.domain.model.ConnectedAccount
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
    data class LaunchAuthorizationResolution(val intentSender: IntentSender, val scopes: Set<String>) : AccountsEvent
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

    private var pendingSignIn: SignInResult.Success? = null

    fun addAccount(activity: Activity) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val signInResult = authManager.signIn(activity)) {
                is SignInResult.Success -> {
                    pendingSignIn = signInResult
                    authorize(activity, signInResult, GoogleApiScopes.ALL)
                }
                is SignInResult.Failure -> {
                    _isLoading.value = false
                    _events.send(AccountsEvent.Error(signInResult.message))
                }
            }
        }
    }

    /** Se llama luego de que el usuario resuelve el consentimiento lanzado desde la pantalla. */
    fun onAuthorizationResolutionResult(data: Intent?, scopes: Set<String>) {
        val signIn = pendingSignIn ?: run { _isLoading.value = false; return }
        viewModelScope.launch {
            when (val outcome = authManager.handleAuthorizationResolution(data, scopes)) {
                is AuthorizationOutcome.Granted -> persist(signIn, outcome)
                is AuthorizationOutcome.Failure -> {
                    _isLoading.value = false
                    _events.send(AccountsEvent.Error(outcome.message))
                }
                is AuthorizationOutcome.NeedsResolution -> _isLoading.value = false
            }
        }
    }

    fun removeAccount(account: ConnectedAccount) {
        viewModelScope.launch { connectedAccountRepository.remove(account) }
    }

    private suspend fun authorize(activity: Activity, signIn: SignInResult.Success, scopes: Set<String>) {
        when (val outcome = authManager.requestAuthorization(activity, scopes)) {
            is AuthorizationOutcome.Granted -> persist(signIn, outcome)
            is AuthorizationOutcome.NeedsResolution -> {
                _isLoading.value = false
                _events.send(AccountsEvent.LaunchAuthorizationResolution(outcome.intentSender, outcome.grantedScopes))
            }
            is AuthorizationOutcome.Failure -> {
                _isLoading.value = false
                _events.send(AccountsEvent.Error(outcome.message))
            }
        }
    }

    private suspend fun persist(signIn: SignInResult.Success, granted: AuthorizationOutcome.Granted) {
        connectedAccountRepository.save(
            ConnectedAccount(
                email = signIn.email,
                displayName = signIn.displayName,
                grantedScopes = granted.grantedScopes,
                accessToken = granted.accessToken,
                accessTokenExpiresAt = authManager.estimateTokenExpiry(),
            ),
        )
        pendingSignIn = null
        _isLoading.value = false
    }
}
