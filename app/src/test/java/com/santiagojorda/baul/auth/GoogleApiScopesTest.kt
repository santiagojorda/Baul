package com.santiagojorda.baul.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleApiScopesTest {

    @Test
    fun `ALL contiene exactamente los 3 scopes esperados, sin duplicados`() {
        assertEquals(
            setOf(
                "https://www.googleapis.com/auth/drive.file",
                "https://www.googleapis.com/auth/photoslibrary.appendonly",
                "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata",
            ),
            GoogleApiScopes.ALL,
        )
    }

    @Test
    fun `ALL incluye cada constante individual`() {
        assertTrue(GoogleApiScopes.DRIVE_FILE in GoogleApiScopes.ALL)
        assertTrue(GoogleApiScopes.PHOTOS_APPEND_ONLY in GoogleApiScopes.ALL)
        assertTrue(GoogleApiScopes.PHOTOS_READONLY_APP_CREATED_DATA in GoogleApiScopes.ALL)
    }
}
