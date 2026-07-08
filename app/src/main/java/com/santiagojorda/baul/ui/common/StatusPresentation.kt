package com.santiagojorda.baul.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.santiagojorda.baul.domain.model.UploadLogEntry.Companion.MAX_RETRY_ATTEMPTS
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.ui.theme.success

data class StatusPresentation(val label: String, val color: Color)

/**
 * Única fuente de verdad para texto + color de cada [UploadStatus], para que Historial y Reglas
 * muestren siempre lo mismo por el mismo estado. `attemptCount > 0` en `PENDING` significa que ya
 * falló una vez y está reintentando solo — se distingue de "recién encolado" para que un fallo
 * transitorio no sea invisible hasta agotar los reintentos.
 */
@Composable
fun UploadStatus.presentation(attemptCount: Int = 0): StatusPresentation = when (this) {
    UploadStatus.PENDING -> if (attemptCount > 0) {
        StatusPresentation(
            label = "Reintentando (intento $attemptCount/$MAX_RETRY_ATTEMPTS)",
            color = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        StatusPresentation(label = "En cola", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    UploadStatus.UPLOADING -> StatusPresentation(label = "Subiendo…", color = MaterialTheme.colorScheme.primary)
    UploadStatus.SUCCESS -> StatusPresentation(label = "Subido", color = MaterialTheme.colorScheme.success)
    UploadStatus.FAILED -> StatusPresentation(label = "Error", color = MaterialTheme.colorScheme.error)
    UploadStatus.CANCELLED -> StatusPresentation(label = "Cancelado", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
