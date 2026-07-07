package com.santiagojorda.baul.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connected_accounts")
data class ConnectedAccountEntity(
    @PrimaryKey val email: String,
    val displayName: String? = null,
    val connectedAt: Long = System.currentTimeMillis(),
    /** Cuál usa el auto-sync de carpetas nuevas cuando hay más de una cuenta conectada. */
    val isDefault: Boolean = false,
)
