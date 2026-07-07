package com.santiagojorda.mediasync.data.local

import com.santiagojorda.mediasync.data.local.entity.ConnectedAccountEntity
import com.santiagojorda.mediasync.domain.model.ConnectedAccount

fun ConnectedAccountEntity.toDomain(): ConnectedAccount =
    ConnectedAccount(email = email, displayName = displayName, connectedAt = connectedAt, isDefault = isDefault)

fun ConnectedAccount.toEntity(): ConnectedAccountEntity =
    ConnectedAccountEntity(email = email, displayName = displayName, connectedAt = connectedAt, isDefault = isDefault)
