package com.santiagojorda.mediasync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connected_accounts")
data class ConnectedAccountEntity(
    @PrimaryKey val email: String,
    val displayName: String? = null,
    val grantedScopes: List<String> = emptyList(),
    val accessToken: String? = null,
    val accessTokenExpiresAt: Long? = null,
)
