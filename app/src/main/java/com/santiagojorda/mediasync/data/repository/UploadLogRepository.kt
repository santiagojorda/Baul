package com.santiagojorda.mediasync.data.repository

import android.content.Context
import android.net.Uri
import com.santiagojorda.mediasync.data.local.dao.RuleDao
import com.santiagojorda.mediasync.data.local.dao.UploadLogDao
import com.santiagojorda.mediasync.data.local.toDomain
import androidx.work.ExistingWorkPolicy
import com.santiagojorda.mediasync.domain.model.UploadLogEntry
import com.santiagojorda.mediasync.work.UploadWorkScheduler
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
}
