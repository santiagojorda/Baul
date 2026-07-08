package com.santiagojorda.baul.data.local

import com.santiagojorda.baul.domain.model.ConnectedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectedAccountMapperTest {

    @Test
    fun `round trip toEntity-toDomain es la identidad`() {
        val account = ConnectedAccount(
            email = "user@example.com",
            displayName = "User",
            connectedAt = 1000,
            isDefault = true,
        )

        assertEquals(account, account.toEntity().toDomain())
    }

    @Test
    fun `displayName nulo se preserva en el mapeo`() {
        val account = ConnectedAccount(email = "user@example.com", displayName = null)

        assertNull(account.toEntity().toDomain().displayName)
    }

    @Test
    fun `isDefault false se preserva en el mapeo`() {
        val account = ConnectedAccount(email = "user@example.com", isDefault = false)

        assertEquals(false, account.toEntity().toDomain().isDefault)
    }
}
