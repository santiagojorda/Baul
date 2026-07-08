package com.santiagojorda.baul.data.local.converter

import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.UploadStatus
import org.junit.Assert.assertEquals
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
}
