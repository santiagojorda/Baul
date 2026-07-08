package com.santiagojorda.baul.data.local

import com.santiagojorda.baul.data.local.entity.ConnectedAccountEntity
import com.santiagojorda.baul.domain.model.ConnectedAccount

fun ConnectedAccountEntity.toDomain(): ConnectedAccount =
    ConnectedAccount(
        email = email,
        displayName = displayName,
        connectedAt = connectedAt,
        isDefault = isDefault,
        needsReauth = needsReauth,
    )

fun ConnectedAccount.toEntity(): ConnectedAccountEntity =
    ConnectedAccountEntity(
        email = email,
        displayName = displayName,
        connectedAt = connectedAt,
        isDefault = isDefault,
        needsReauth = needsReauth,
    )
