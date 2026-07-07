package com.santiagojorda.baul.domain.model

/**
 * No cachea ningún token: con GoogleAuthUtil (ver GoogleAuthManager) se pide uno fresco en el
 * momento de cada subida, Play Services se encarga del cacheo/refresh real a nivel de cuenta.
 */
data class ConnectedAccount(
    val email: String,
    val displayName: String? = null,
    val connectedAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false,
)
