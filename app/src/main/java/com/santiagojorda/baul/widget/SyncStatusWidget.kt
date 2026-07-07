package com.santiagojorda.baul.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.Spacer
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.santiagojorda.baul.MainActivity
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.dao.StatusCount
import com.santiagojorda.baul.domain.model.UploadStatus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/** Conteos por estado que muestra el widget, ya resueltos (no exponen [UploadStatus] a la UI). */
private data class SyncCounts(
    val pending: Int,
    val uploading: Int,
    val failed: Int,
    val success: Int,
) {
    val unsynced: Int get() = pending + uploading

    companion object {
        fun from(rows: List<StatusCount>): SyncCounts {
            val byStatus = rows.associate { it.status to it.count }
            return SyncCounts(
                pending = byStatus[UploadStatus.PENDING] ?: 0,
                uploading = byStatus[UploadStatus.UPLOADING] ?: 0,
                failed = byStatus[UploadStatus.FAILED] ?: 0,
                success = byStatus[UploadStatus.SUCCESS] ?: 0,
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
        val counts = SyncCounts.from(AppDatabase.getInstance(context).uploadLogDao().countsByStatus())
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

@androidx.compose.runtime.Composable
private fun WidgetContent(counts: SyncCounts) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primaryContainer)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            text = "Baúl",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onPrimaryContainer,
            ),
        )
        Spacer(modifier = GlanceModifier.size(6.dp))
        if (counts.unsynced == 0 && counts.failed == 0) {
            Text(
                text = "Todo sincronizado (${counts.success})",
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer),
            )
        } else {
            StatusRow(label = "Sin subir", value = counts.unsynced, dotColor = Color(0xFFF2A93B))
            if (counts.failed > 0) {
                StatusRow(label = "Fallaron", value = counts.failed, dotColor = Color(0xFFD9463C))
            }
            StatusRow(label = "Listos", value = counts.success, dotColor = Color(0xFF3B9E4C))
        }
    }
}

@androidx.compose.runtime.Composable
private fun StatusRow(label: String, value: Int, dotColor: Color) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(modifier = GlanceModifier.size(8.dp).background(ColorProvider(dotColor))) {}
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "$label: $value",
            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer),
        )
    }
}
