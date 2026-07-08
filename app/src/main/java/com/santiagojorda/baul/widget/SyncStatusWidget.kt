package com.santiagojorda.baul.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.Spacer
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.santiagojorda.baul.MainActivity
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.dao.StatusCount
import com.santiagojorda.baul.domain.model.UploadStatus
import com.santiagojorda.baul.work.MediaScanWorker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/** Conteos por estado que muestra el widget, ya resueltos (no exponen [UploadStatus] a la UI). */
private data class SyncCounts(
    val pending: Int,
    val uploading: Int,
    val failed: Int,
    val success: Int,
    val pendingDeletions: Int,
) {
    val unsynced: Int get() = pending + uploading

    companion object {
        fun from(rows: List<StatusCount>, pendingDeletions: Int): SyncCounts {
            val byStatus = rows.associate { it.status to it.count }
            return SyncCounts(
                pending = byStatus[UploadStatus.PENDING] ?: 0,
                uploading = byStatus[UploadStatus.UPLOADING] ?: 0,
                failed = byStatus[UploadStatus.FAILED] ?: 0,
                success = byStatus[UploadStatus.SUCCESS] ?: 0,
                pendingDeletions = pendingDeletions,
            )
        }
    }
}

/**
 * Widget de pantalla de inicio con el estado de la sincronización: cuántos archivos faltan
 * subir (pendientes + subiendo ahora), cuántos fallaron, y cuántos ya se sincronizaron. No lleva
 * estado propio: lee directo de [com.santiagojorda.baul.data.local.dao.UploadLogDao] cada vez
 * que Android pide refrescarlo (por [android:updatePeriodMillis], o cuando la app llama
 * [androidx.glance.appwidget.updateAll] después de escribir en `upload_log` — ver
 * [com.santiagojorda.baul.work.UploadWorker] y
 * [com.santiagojorda.baul.data.repository.UploadLogRepository.cancel]).
 */
class SyncStatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val logDao = AppDatabase.getInstance(context).uploadLogDao()
        val counts = SyncCounts.from(logDao.countsByStatus(), logDao.countPendingDeletions())
        provideContent {
            GlanceTheme {
                WidgetContent(counts)
            }
        }
    }
}

class SyncStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SyncStatusWidget()
}

/**
 * Vuelve a correr [com.santiagojorda.baul.media.SyncCoordinator.scanAndDispatchAll] a mano,
 * por si la app venía cerrada y todavía no pasó el escaneo periódico de 15 min (o para forzar un
 * reintento de lo que haya quedado en FAILED). Nombre de work único distinto al periódico, así
 * un tap acá no le pisa el ciclo a [MediaScanWorker.UNIQUE_WORK_NAME].
 */
class RetriggerScanAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MediaScanWorker.MANUAL_TRIGGER_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MediaScanWorker>().build(),
        )
        SyncStatusWidget().updateAll(context)
    }
}

/** Widget de una sola fila (2x1): todo en horizontal para que entre en la altura mínima. */
@androidx.compose.runtime.Composable
private fun WidgetContent(counts: SyncCounts) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primaryContainer)
            .padding(horizontal = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (counts.unsynced == 0 && counts.failed == 0 && counts.pendingDeletions == 0) {
            Text(
                text = "✓ Todo sincronizado",
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer),
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                var needsSpacer = false
                if (counts.unsynced > 0) {
                    CountBadge(value = counts.unsynced, dotColor = Color(0xFFF2A93B))
                    needsSpacer = true
                }
                if (counts.failed > 0) {
                    if (needsSpacer) Spacer(modifier = GlanceModifier.width(10.dp))
                    CountBadge(value = counts.failed, dotColor = Color(0xFFD9463C))
                    needsSpacer = true
                }
                if (counts.pendingDeletions > 0) {
                    // Subidos pero todavía ocupando espacio en el celular: falta confirmar el
                    // borrado, y eso solo pasa al abrir la app (ver DeleteUploadedSourcesEffect).
                    if (needsSpacer) Spacer(modifier = GlanceModifier.width(10.dp))
                    CountBadge(value = counts.pendingDeletions, dotColor = Color(0xFF3B82C4))
                }
            }
        }
        Text(
            text = "↻",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onPrimaryContainer,
            ),
            modifier = GlanceModifier
                .clickable(actionRunCallback<RetriggerScanAction>())
                .padding(4.dp),
        )
    }
}

/** Punto de color + número, ej. el punto naranja + "3" de "sin subir". */
@androidx.compose.runtime.Composable
private fun CountBadge(value: Int, dotColor: Color) {
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Box(modifier = GlanceModifier.size(8.dp).background(ColorProvider(dotColor))) {}
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = "$value",
            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer),
        )
    }
}
