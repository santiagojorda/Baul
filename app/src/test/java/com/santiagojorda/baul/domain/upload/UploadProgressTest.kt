package com.santiagojorda.baul.domain.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadProgressTest {

    @Test
    fun `uploadPercent devuelve null si total es cero`() {
        assertNull(uploadPercent(uploaded = 0, total = 0))
    }

    @Test
    fun `uploadPercent devuelve null si total es negativo`() {
        assertNull(uploadPercent(uploaded = 0, total = -1))
    }

    @Test
    fun `uploadPercent calcula el porcentaje normal`() {
        assertEquals(50, uploadPercent(uploaded = 50, total = 100))
    }

    @Test
    fun `uploadPercent devuelve 0 cuando todavia no se subio nada`() {
        assertEquals(0, uploadPercent(uploaded = 0, total = 100))
    }

    @Test
    fun `uploadPercent devuelve 100 cuando ya se subio todo`() {
        assertEquals(100, uploadPercent(uploaded = 100, total = 100))
    }

    @Test
    fun `uploadPercent no supera 100 aunque uploaded exceda total`() {
        assertEquals(100, uploadPercent(uploaded = 150, total = 100))
    }
}
