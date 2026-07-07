package com.santiagojorda.mediasync.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.santiagojorda.mediasync.R
import java.security.SecureRandom
import kotlinx.coroutines.tasks.await

sealed interface SignInResult {
    data class Success(val email: String, val displayName: String?) : SignInResult
    data class Failure(val message: String) : SignInResult
}

sealed interface AuthorizationOutcome {
    data class Granted(val accessToken: String?, val grantedScopes: Set<String>) : AuthorizationOutcome
    data class NeedsResolution(val intentSender: IntentSender, val grantedScopes: Set<String>) : AuthorizationOutcome
    data class Failure(val message: String) : AuthorizationOutcome
}

/**
 * Login (identidad) con Credential Manager + autorización de scopes con la Authorization API
 * de Play Services. Son dos APIs separadas: la primera solo confirma "quién sos", la segunda
 * es la que da permiso real para llamar YouTube/Drive/Photos.
 *
 * El access token que devuelve la Authorization API dura ~1h y Play Services no expone el
 * `expires_in` exacto, así que [ConnectedAccount.accessTokenExpiresAt] es una estimación
 * conservadora (ver [saveTokenExpiryEstimate]). Sin backend propio no hay refresh token: cuando
 * se vence, hay que volver a llamar [requestAuthorization] (normalmente sin UI, si Play
 * Services todavía tiene la cuenta autorizada).
 */
class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(activity: Activity): SignInResult {
        val webClientId = context.getString(R.string.google_web_client_id)
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(generateNonce())
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = credentialManager.getCredential(request = request, context = activity)
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                SignInResult.Success(email = googleIdTokenCredential.id, displayName = googleIdTokenCredential.displayName)
            } else {
                SignInResult.Failure("Tipo de credencial inesperado")
            }
        } catch (e: GetCredentialException) {
            SignInResult.Failure(e.message ?: "No se pudo iniciar sesión")
        } catch (e: GoogleIdTokenParsingException) {
            SignInResult.Failure("Token de Google inválido")
        }
    }

    /** [activity] hace falta porque, si el usuario nunca otorgó estos scopes, hay que mostrarle un consentimiento. */
    suspend fun requestAuthorization(activity: Activity, scopes: Set<String> = GoogleApiScopes.ALL): AuthorizationOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map { Scope(it) })
            .build()

        return try {
            val result = Identity.getAuthorizationClient(activity).authorize(request).await()
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                    ?: return AuthorizationOutcome.Failure("Google no devolvió un intent de consentimiento")
                AuthorizationOutcome.NeedsResolution(pendingIntent.intentSender, scopes)
            } else {
                AuthorizationOutcome.Granted(accessToken = result.accessToken, grantedScopes = scopes)
            }
        } catch (e: ApiException) {
            AuthorizationOutcome.Failure(e.message ?: "Error al autorizar")
        }
    }

    /** Se llama con el Intent que vuelve del [IntentSenderRequest] lanzado para el caso NeedsResolution. */
    fun handleAuthorizationResolution(data: Intent?, scopes: Set<String>): AuthorizationOutcome {
        return try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            AuthorizationOutcome.Granted(accessToken = result.accessToken, grantedScopes = scopes)
        } catch (e: ApiException) {
            AuthorizationOutcome.Failure(e.message ?: "El usuario no otorgó el permiso")
        }
    }

    /** Ventana conservadora bajo la ~1h real, para no usar un token vencido. */
    fun estimateTokenExpiry(now: Long = System.currentTimeMillis()): Long = now + 50 * 60 * 1000L

    private fun generateNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}
