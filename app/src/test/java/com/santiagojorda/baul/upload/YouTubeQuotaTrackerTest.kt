package com.santiagojorda.baul.upload

import com.santiagojorda.baul.data.local.dao.YouTubeQuotaDao
import com.santiagojorda.baul.data.local.entity.YouTubeQuotaUsageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mismo formato que usa [YouTubeQuotaTracker.todayKey] internamente, para poder sembrar el fake. */
private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private class FakeYouTubeQuotaDao : YouTubeQuotaDao {
    val rows = mutableMapOf<String, YouTubeQuotaUsageEntity>()

    override suspend fun getForDate(date: String): YouTubeQuotaUsageEntity? = rows[date]

    override suspend fun upsert(entity: YouTubeQuotaUsageEntity) {
        rows[entity.date] = entity
    }
}

class YouTubeQuotaTrackerTest {

    @Test
    fun `sin uso previo hoy hay lugar para subir`() = runTest {
        val tracker = YouTubeQuotaTracker(FakeYouTubeQuotaDao())
        assertTrue(tracker.hasRoomForUpload())
    }

    @Test
    fun `uso acumulado que supera el limite seguro bloquea la subida`() = runTest {
        val dao = FakeYouTubeQuotaDao()
        // 9000 (DAILY_SAFE_LIMIT) - 1600 (UPLOAD_COST_UNITS) + 1 = ya no entra otra subida de 1600.
        dao.rows[todayKey()] = YouTubeQuotaUsageEntity(date = todayKey(), unitsUsed = 7401)
        val tracker = YouTubeQuotaTracker(dao)
        assertFalse(tracker.hasRoomForUpload())
    }

    @Test
    fun `justo en el limite todavia permite la subida (menor o igual)`() = runTest {
        val dao = FakeYouTubeQuotaDao()
        // 7400 + 1600 = 9000 = DAILY_SAFE_LIMIT exacto.
        dao.rows[todayKey()] = YouTubeQuotaUsageEntity(date = todayKey(), unitsUsed = 7400)
        val tracker = YouTubeQuotaTracker(dao)
        assertTrue(tracker.hasRoomForUpload())
    }

    @Test
    fun `recordUpload acumula sobre lo ya usado hoy, no lo pisa`() = runTest {
        val dao = FakeYouTubeQuotaDao()
        dao.rows[todayKey()] = YouTubeQuotaUsageEntity(date = todayKey(), unitsUsed = 1000)
        val tracker = YouTubeQuotaTracker(dao)

        tracker.recordUpload()

        assertEquals(2600, dao.rows[todayKey()]?.unitsUsed)
    }
}
