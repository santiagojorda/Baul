package com.santiagojorda.baul.upload

import com.santiagojorda.baul.data.local.dao.YouTubeQuotaDao
import com.santiagojorda.baul.data.local.entity.YouTubeQuotaUsageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Trackea a mano cuánta cuota de la YouTube Data API se gastó hoy: la API no expone un endpoint
 * para consultar cuánto queda, así que hay que llevar la cuenta localmente. Sirve para frenar
 * ANTES de intentar una subida que sabemos que va a rebotar por cuota, en vez de gastar la
 * llamada igual y enterarnos recién con el error 403 de Google.
 */
class YouTubeQuotaTracker(private val dao: YouTubeQuotaDao) {

    private val mutex = Mutex()

    suspend fun hasRoomForUpload(): Boolean = mutex.withLock {
        val used = dao.getForDate(todayKey())?.unitsUsed ?: 0
        used + UPLOAD_COST_UNITS <= DAILY_SAFE_LIMIT
    }

    suspend fun recordUpload() = mutex.withLock {
        val today = todayKey()
        val used = dao.getForDate(today)?.unitsUsed ?: 0
        dao.upsert(YouTubeQuotaUsageEntity(date = today, unitsUsed = used + UPLOAD_COST_UNITS))
    }

    // Nueva instancia en cada llamada: SimpleDateFormat no es thread-safe.
    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private companion object {
        // Costo real documentado de videos.insert; el limite real de la cuenta es 10_000/dia, se
        // deja un margen de seguridad para no comerse otras llamadas (playlistItems.insert, etc).
        const val UPLOAD_COST_UNITS = 1600
        const val DAILY_SAFE_LIMIT = 9000
    }
}
