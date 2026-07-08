package com.santiagojorda.baul.media

import android.net.Uri
import com.santiagojorda.baul.domain.model.UploadLogEntry

/**
 * Decide qué hacer con subidas ya confirmadas (SUCCESS) que están esperando el borrado del
 * original, sin llamar a Android directo: [mediaStillExists] y [deleteDirectly] son los únicos
 * puntos de I/O real, inyectados para poder probar acá la regla de "nunca borrar sin permiso de
 * acceso total u orden de confirmación del sistema" sin necesitar un dispositivo.
 */
class SafeDeleteCoordinator(
    private val mediaStillExists: (Uri) -> Boolean,
    private val deleteDirectly: (Uri) -> Unit,
) {

    data class Outcome(
        /** Ya no estaban en MediaStore (borrados a mano, por otra app, o en una corrida anterior). */
        val alreadyGone: List<UploadLogEntry>,
        /** Existían al momento de resolver. Si [requiresConfirmation] es false, ya se borraron acá. */
        val existing: List<UploadLogEntry>,
        val requiresConfirmation: Boolean,
    )

    /**
     * [pending] debe venir ya filtrado a subidas SUCCESS de reglas que piden borrar el original
     * (ver [com.santiagojorda.baul.data.repository.UploadLogRepository.getPendingDeletions]).
     */
    fun resolve(pending: List<UploadLogEntry>, hasAllFilesAccess: Boolean): Outcome {
        val (missing, existing) = pending.partition { entry -> !mediaStillExists(Uri.parse(entry.mediaUri)) }
        if (existing.isEmpty() || !hasAllFilesAccess) {
            return Outcome(alreadyGone = missing, existing = existing, requiresConfirmation = existing.isNotEmpty())
        }

        existing.forEach { entry -> deleteDirectly(Uri.parse(entry.mediaUri)) }
        return Outcome(alreadyGone = missing, existing = existing, requiresConfirmation = false)
    }
}
