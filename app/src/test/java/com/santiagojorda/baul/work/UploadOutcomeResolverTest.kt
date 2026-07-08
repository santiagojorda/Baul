package com.santiagojorda.baul.work

import androidx.work.ListenableWorker.Result
import com.santiagojorda.baul.domain.model.UploadLogEntry.Companion.MAX_RETRY_ATTEMPTS
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.domain.upload.UploadResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [UploadStatus.SUCCESS] es el único estado que [com.santiagojorda.baul.data.repository.UploadLogRepository.getPendingDeletions]
 * deja pasar para el borrado seguro del original — estos tests fijan que ese estado (y ningún
 * otro) solo puede salir de un [UploadResult.Success] real.
 */
class UploadOutcomeResolverTest {

    @Test
    fun `un upload exitoso siempre resuelve en SUCCESS, sin importar el intento`() {
        val outcome = UploadOutcomeResolver.resolve(
            uploadResult = UploadResult.Success(remoteId = "remote-1"),
            runAttemptCount = 3,
            totalBytes = 1_000L,
        )

        assertEquals(UploadStatus.SUCCESS, outcome.status)
        assertEquals(Result.success(), outcome.workResult)
        assertEquals("remote-1", outcome.remoteId)
        assertEquals(1_000L, outcome.bytesUploaded)
    }

    @Test
    fun `un fallo reintentable por debajo del limite queda PENDING y pide reintentar`() {
        val outcome = UploadOutcomeResolver.resolve(
            uploadResult = UploadResult.Failure(message = "timeout", retryable = true),
            runAttemptCount = MAX_RETRY_ATTEMPTS - 1,
            totalBytes = 1_000L,
        )

        assertEquals(UploadStatus.PENDING, outcome.status)
        assertEquals(Result.retry(), outcome.workResult)
        assertEquals(0L, outcome.bytesUploaded)
        assertEquals("timeout", outcome.errorMessage)
    }

    @Test
    fun `un fallo reintentable que ya agoto los intentos queda FAILED, no PENDING para siempre`() {
        val outcome = UploadOutcomeResolver.resolve(
            uploadResult = UploadResult.Failure(message = "timeout", retryable = true),
            runAttemptCount = MAX_RETRY_ATTEMPTS,
            totalBytes = 1_000L,
        )

        assertEquals(UploadStatus.FAILED, outcome.status)
        assertEquals(Result.failure(), outcome.workResult)
    }

    @Test
    fun `un fallo no reintentable queda FAILED en el primer intento`() {
        val outcome = UploadOutcomeResolver.resolve(
            uploadResult = UploadResult.Failure(message = "cuenta revocada", retryable = false),
            runAttemptCount = 0,
            totalBytes = 1_000L,
        )

        assertEquals(UploadStatus.FAILED, outcome.status)
        assertEquals(Result.failure(), outcome.workResult)
    }

    @Test
    fun `nunca devuelve SUCCESS para un resultado que no es Success`() {
        val runAttemptCounts = 0..(MAX_RETRY_ATTEMPTS + 2)

        runAttemptCounts.forEach { attempt ->
            val outcome = UploadOutcomeResolver.resolve(
                uploadResult = UploadResult.Failure(message = "error", retryable = true),
                runAttemptCount = attempt,
                totalBytes = 1_000L,
            )
            assertEquals("intento $attempt no debería ser SUCCESS", false, outcome.status == UploadStatus.SUCCESS)
        }
    }
}
