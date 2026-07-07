package com.santiagojorda.baul.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.santiagojorda.baul.MainActivity
import com.santiagojorda.baul.data.local.AppDatabase
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.domain.model.UploadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Único indicador de "hay una sincronización corriendo" fuera de la pantalla Historial. No lleva
 * su propio estado: observa la misma [com.santiagojorda.baul.data.local.dao.UploadLogDao]
 * que ya usa Historial, así que siempre coincide con lo que se ve ahí. Cualquier [UploadWorker]
 * puede pedir que arranque (llamada idempotente); el servicio decide solo cuándo pararse mirando
 * si sigue habiendo filas en `UPLOADING`.
 */
class UploadNotificationService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(applicationContext)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(emptyList()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        val logDao = AppDatabase.getInstance(applicationContext).uploadLogDao()
        scope.launch {
            logDao.observeLogs()
                .map { logs -> logs.filter { it.status == UploadStatus.UPLOADING } }
                .collect { uploading ->
                    if (uploading.isEmpty()) {
                        // Entre un archivo y el siguiente puede haber un instante sin ninguno en
                        // UPLOADING; esperar un poco antes de parar evita que la notificación
                        // parpadee (aparecer/desaparecer) en una tanda de varios archivos.
                        delay(STOP_DEBOUNCE_MS)
                        if (logDao.getByStatus(UploadStatus.UPLOADING).isEmpty()) {
                            stopSelf()
                            return@collect
                        }
                    }
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(uploading))
                }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(uploading: List<UploadLogEntity>): Notification {
        val contentText = when {
            uploading.isEmpty() -> "Sincronizando…"
            uploading.size == 1 -> uploading.first().let { entry ->
                if (entry.totalBytes > 0) {
                    "${entry.fileName} — ${(entry.bytesUploaded * 100 / entry.totalBytes)}%"
                } else {
                    entry.fileName
                }
            }
            else -> "Subiendo ${uploading.size} archivos"
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("MediaSync")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val single = uploading.singleOrNull()
        if (single != null && single.totalBytes > 0) {
            builder.setProgress(100, (single.bytesUploaded * 100 / single.totalBytes).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, uploading.isNotEmpty())
        }
        return builder.build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        private const val CHANNEL_ID = "uploads"
        private const val STOP_DEBOUNCE_MS = 2000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, UploadNotificationService::class.java))
        }

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Subidas en curso", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progreso de las subidas de MediaSync"
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
