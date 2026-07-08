package com.santiagojorda.baul.data.local.converter

import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.domain.model.YouTubePrivacyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `DestinationType ida y vuelta para todos los valores`() {
        DestinationType.entries.forEach {
            assertEquals(it, converters.toDestinationType(converters.fromDestinationType(it)))
        }
    }

    @Test
    fun `UploadStatus ida y vuelta para todos los valores`() {
        UploadStatus.entries.forEach {
            assertEquals(it, converters.toUploadStatus(converters.fromUploadStatus(it)))
        }
    }

    @Test
    fun `YouTubePrivacyStatus nulo ida y vuelta`() {
        assertNull(converters.fromYouTubePrivacyStatus(null))
        assertNull(converters.toYouTubePrivacyStatus(null))
    }

    @Test
    fun `YouTubePrivacyStatus no nulo ida y vuelta para todos los valores`() {
        YouTubePrivacyStatus.entries.forEach {
            assertEquals(it, converters.toYouTubePrivacyStatus(converters.fromYouTubePrivacyStatus(it)))
        }
    }

    @Test
    fun `lista vacia se serializa como string vacio y deserializa como lista vacia`() {
        assertEquals("", converters.fromStringList(emptyList()))
        assertTrue(converters.toStringList("").isEmpty())
    }

    @Test
    fun `lista de tags ida y vuelta preserva orden y valores`() {
        val tags = listOf("uno", "dos", "tres")
        assertEquals(tags, converters.toStringList(converters.fromStringList(tags)))
    }

    @Test
    fun `tags con espacios y guiones no colisionan con el separador no imprimible`() {
        val tags = listOf("tag con espacios", "otro-tag", "MAYUSCULAS")
        assertEquals(tags, converters.toStringList(converters.fromStringList(tags)))
    }

    @Test
    fun `lista de un solo elemento no agrega separador`() {
        assertEquals("solo", converters.fromStringList(listOf("solo")))
    }
}
