package com.santiagojorda.mediasync.data.local

import com.santiagojorda.mediasync.data.local.entity.ConnectedAccountEntity
import com.santiagojorda.mediasync.domain.model.ConnectedAccount

fun ConnectedAccountEntity.toDomain(): ConnectedAccount = ConnectedAccount(
    email = email,
    displayName = displayName,
    grantedScopes = grantedScopes.toSet(),
    accessToken = accessToken,
    accessTokenExpiresAt = accessTokenExpiresAt,
)

fun ConnectedAccount.toEntity(): ConnectedAccountEntity = ConnectedAccountEntity(
    email = email,
    displayName = displayName,
    grantedScopes = grantedScopes.toList(),
    accessToken = accessToken,
    accessTokenExpiresAt = accessTokenExpiresAt,
)
