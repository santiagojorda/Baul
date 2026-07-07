package com.santiagojorda.baul.data.repository

import android.content.Context
import android.net.Uri
import com.santiagojorda.baul.data.local.dao.RuleDao
import com.santiagojorda.baul.data.local.dao.UploadLogDao
import com.santiagojorda.baul.data.local.toDomain
import com.santiagojorda.baul.data.local.toEntity
import androidx.work.ExistingWorkPolicy
import com.santiagojorda.baul.domain.model.UploadLogEntry
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.widget.SyncStatusWidget
import com.santiagojorda.baul.work.UploadWorkScheduler
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UploadLogRepository(
    private val context: Context,
    private val uploadLogDao: UploadLogDao,
    private val ruleDao: RuleDao,
) {

    fun observeLogs(): Flow<List<UploadLogEntry>> =
        uploadLogDao.observeLogs().map { entities -> entities.map { it.toDomain() } }

    /**
     * Reintento manual: para un archivo en FAILED o PENDING/UPLOADING (por si quedó colgado).
     * Usa REPLACE en vez de KEEP porque, si todavía hay un work "vivo" con este mismo nombre
     * (encolado o corriendo), KEEP no haría nada — REPLACE lo cancela y arranca uno nuevo.
     */
    suspend fun retry(entry: UploadLogEntry) {
        val rule = ruleDao.getRuleById(entry.ruleId) ?: return
        UploadWorkScheduler.enqueue(
            context = context,
            ruleId = entry.ruleId,
            mediaUri = Uri.parse(entry.mediaUri),
            wifiOnly = rule.wifiOnly,
            policy = ExistingWorkPolicy.REPLACE,
        )
    }

    /** Reintenta todos los archivos que quedaron en FAILED (error real, no uno que ya reintentó solo) para una regla. */
    suspend fun retryAllFailedForRule(ruleId: Long) {
        val rule = ruleDao.getRuleById(ruleId) ?: return
        uploadLogDao.getFailedForRule(ruleId).forEach { entry ->
            UploadWorkScheduler.enqueue(
                context = context,
                ruleId = ruleId,
                mediaUri = Uri.parse(entry.mediaUri),
                wifiOnly = rule.wifiOnly,
                policy = ExistingWorkPolicy.REPLACE,
            )
        }
    }

    /** Subidas OK cuya regla pide borrar el original y todavía no se le pidió confirmación al usuario. */
    suspend fun getPendingDeletions(): List<UploadLogEntry> =
        uploadLogDao.getSuccessfulNotYetDeleted()
            .filter { entry -> ruleDao.getRuleById(entry.ruleId)?.deleteSourceAfterUpload == true }
            .map { it.toDomain() }

    suspend fun markSourceDeleted(entries: List<UploadLogEntry>) {
        if (entries.isEmpty()) return
        uploadLogDao.markSourceDeleted(entries.map { it.id })
    }

    /** Cancela una subida en curso (o encolada), sin contarla como un error real. */
    suspend fun cancel(entry: UploadLogEntry) {
        UploadWorkScheduler.cancel(context, entry.ruleId, Uri.parse(entry.mediaUri))
        uploadLogDao.upsert(
            entry.copy(
                status = UploadStatus.CANCELLED,
                errorMessage = "Cancelado por el usuario",
                updatedAt = System.currentTimeMillis(),
            ).toEntity(),
        )
        SyncStatusWidget().updateAll(context)
    }

    /**
     * Al desactivar una regla (switch apagado en Reglas) se corta lo que esté en vuelo, pero se
     * conserva el historial ya hecho — antes esto borraba todos los logs de la regla, perdiendo
     * el registro de qué se había subido. Reusa [cancel] para que cada archivo en curso quede
     * marcado CANCELLED (mismo tratamiento que una cancelación manual) en vez de desaparecer.
     */
    suspend fun cancelActiveUploadsForRule(ruleId: Long) {
        uploadLogDao.getForRule(ruleId)
            .filter { it.status == UploadStatus.PENDING || it.status == UploadStatus.UPLOADING }
            .forEach { cancel(it.toDomain()) }
    }
}
