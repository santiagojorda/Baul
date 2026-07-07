package com.santiagojorda.mediasync.domain.model

data class ConnectedAccount(
    val email: String,
    val displayName: String? = null,
    val grantedScopes: Set<String> = emptySet(),
    val accessToken: String? = null,
    /** Estimado (Play Services no expone el expires_in exacto): ver GoogleAuthManager. */
    val accessTokenExpiresAt: Long? = null,
) {
    fun hasValidToken(now: Long = System.currentTimeMillis()): Boolean =
        accessToken != null && accessTokenExpiresAt != null && now < accessTokenExpiresAt
}
