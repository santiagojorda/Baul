package com.santiagojorda.baul.media

import android.net.Uri
import com.santiagojorda.baul.domain.model.UploadLogEntry
import com.santiagojorda.baul.domain.model.UploadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La garantía central de Baul es que un archivo original nunca se borra antes de que el upload
 * se haya confirmado y, si no hay "Acceso a todos los archivos", antes de que el usuario confirme
 * el borrado en el diálogo del sistema. Estos tests prueban esa regla directo sobre
 * [SafeDeleteCoordinator], sin pasar por el Composable que lo llama en producción — ver
 * [com.santiagojorda.baul.ui.navigation.BaulApp] — que necesitaría una Activity real.
 */
@RunWith(RobolectricTestRunner::class)
class SafeDeleteCoordinatorTest {

    @Test
    fun `sin entradas pendientes no hace ni borrados ni pedidos de confirmacion`() {
        val deletedUris = mutableListOf<Uri>()
        val coordinator = SafeDeleteCoordinator(
            mediaStillExists = { true },
            deleteDirectly = { uri -> deletedUris += uri },
        )

        val outcome = coordinator.resolve(emptyList(), hasAllFilesAccess = true)

        assertTrue(outcome.alreadyGone.isEmpty())
        assertTrue(outcome.existing.isEmpty())
        assertEquals(false, outcome.requiresConfirmation)
        assertTrue(deletedUris.isEmpty())
    }

    @Test
    fun `sin Acceso a todos los archivos nunca borra directo, aunque el archivo exista`() {
        val deletedUris = mutableListOf<Uri>()
        val coordinator = SafeDeleteCoordinator(
            mediaStillExists = { true },
            deleteDirectly = { uri -> deletedUris += uri },
        )
        val pending = listOf(sampleEntry(id = 1))

        val outcome = coordinator.resolve(pending, hasAllFilesAccess = false)

        assertTrue("no debe borrar sin permiso ni confirmación", deletedUris.isEmpty())
        assertEquals(pending, outcome.existing)
        assertEquals(true, outcome.requiresConfirmation)
        assertTrue(outcome.alreadyGone.isEmpty())
    }

    @Test
    fun `con Acceso a todos los archivos otorgado borra directo y no pide confirmacion`() {
        val deletedUris = mutableListOf<Uri>()
        val coordinator = SafeDeleteCoordinator(
            mediaStillExists = { true },
            deleteDirectly = { uri -> deletedUris += uri },
        )
        val pending = listOf(sampleEntry(id = 1), sampleEntry(id = 2))

        val outcome = coordinator.resolve(pending, hasAllFilesAccess = true)

        assertEquals(pending.map { Uri.parse(it.mediaUri) }, deletedUris)
        assertEquals(pending, outcome.existing)
        assertEquals(false, outcome.requiresConfirmation)
        assertTrue(outcome.alreadyGone.isEmpty())
    }

    @Test
    fun `entradas cuyo archivo ya no existe se marcan como alreadyGone y nunca se intentan borrar`() {
        val deletedUris = mutableListOf<Uri>()
        val missingEntry = sampleEntry(id = 1)
        val coordinator = SafeDeleteCoordinator(
            mediaStillExists = { uri -> uri != Uri.parse(missingEntry.mediaUri) },
            deleteDirectly = { uri -> deletedUris += uri },
        )

        val outcome = coordinator.resolve(listOf(missingEntry), hasAllFilesAccess = true)

        assertEquals(listOf(missingEntry), outcome.alreadyGone)
        assertTrue(outcome.existing.isEmpty())
        assertEquals(false, outcome.requiresConfirmation)
        assertTrue("un archivo que ya no existe no se intenta borrar de nuevo", deletedUris.isEmpty())
    }

    @Test
    fun `lote mixto separa lo que ya no existe de lo que si, sin borrar nada si falta el permiso`() {
        val deletedUris = mutableListOf<Uri>()
        val missingEntry = sampleEntry(id = 1)
        val existingEntry = sampleEntry(id = 2)
        val coordinator = SafeDeleteCoordinator(
            mediaStillExists = { uri -> uri != Uri.parse(missingEntry.mediaUri) },
            deleteDirectly = { uri -> deletedUris += uri },
        )

        val outcome = coordinator.resolve(listOf(missingEntry, existingEntry), hasAllFilesAccess = false)

        assertEquals(listOf(missingEntry), outcome.alreadyGone)
        assertEquals(listOf(existingEntry), outcome.existing)
        assertEquals(true, outcome.requiresConfirmation)
        assertTrue(deletedUris.isEmpty())
    }

    private fun sampleEntry(id: Long) = UploadLogEntry(
        id = id,
        ruleId = 1,
        mediaUri = "content://media/external/images/media/$id",
        fileName = "IMG_000$id.jpg",
        status = UploadStatus.SUCCESS,
        createdAt = 1000,
        updatedAt = 1000,
    )
}
