package com.santiagojorda.mediasync.data.repository

import android.content.Context
import android.net.Uri
import com.santiagojorda.mediasync.data.local.dao.RuleDao
import com.santiagojorda.mediasync.data.local.dao.UploadLogDao
import com.santiagojorda.mediasync.data.local.toDomain
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

    /** Vuelve a encolar la subida para un archivo que quedó en FAILED. */
    suspend fun retry(entry: UploadLogEntry) {
        val rule = ruleDao.getRuleById(entry.ruleId) ?: return
        UploadWorkScheduler.enqueue(
            context = context,
            ruleId = entry.ruleId,
            mediaUri = Uri.parse(entry.mediaUri),
            wifiOnly = rule.wifiOnly,
        )
    }
}
