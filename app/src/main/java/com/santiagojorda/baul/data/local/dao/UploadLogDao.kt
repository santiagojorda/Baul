package com.santiagojorda.baul.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadLogDao {

    @Query("SELECT * FROM upload_log ORDER BY createdAt DESC")
    fun observeLogs(): Flow<List<UploadLogEntity>>

    @Query("SELECT * FROM upload_log WHERE status = :status")
    suspend fun getByStatus(status: UploadStatus): List<UploadLogEntity>

    @Query("SELECT * FROM upload_log WHERE status = 'SUCCESS' AND sourceDeleted = 0")
    suspend fun getSuccessfulNotYetDeleted(): List<UploadLogEntity>

    @Query("SELECT * FROM upload_log WHERE ruleId = :ruleId AND status = 'FAILED'")
    suspend fun getFailedForRule(ruleId: Long): List<UploadLogEntity>

    @Query("SELECT * FROM upload_log WHERE ruleId = :ruleId")
    suspend fun getForRule(ruleId: Long): List<UploadLogEntity>

    @Query("UPDATE upload_log SET sourceDeleted = 1 WHERE id IN (:ids)")
    suspend fun markSourceDeleted(ids: List<Long>)

    @Query(
        "UPDATE upload_log SET bytesUploaded = :bytesUploaded, totalBytes = :totalBytes, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateProgress(id: Long, bytesUploaded: Long, totalBytes: Long, updatedAt: Long)

    @Query(
        "SELECT * FROM upload_log WHERE ruleId = :ruleId AND mediaUri = :mediaUri " +
            "ORDER BY createdAt DESC LIMIT 1",
    )
    suspend fun getLogForMedia(ruleId: Long, mediaUri: String): UploadLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: UploadLogEntity): Long

    @Update
    suspend fun update(entry: UploadLogEntity)

    @Query("SELECT status, COUNT(*) AS count FROM upload_log GROUP BY status")
    suspend fun countsByStatus(): List<StatusCount>

    /**
     * Subidos con éxito, cuya regla pide borrar el original, y que todavía no se confirmaron
     * (el pedido de borrado real necesita una Activity en pantalla — ver
     * [com.santiagojorda.baul.ui.navigation.BaulApp] — así que si no abrís la app, se quedan acá
     * juntándose sin liberar espacio en el celular).
     */
    @Query(
        "SELECT COUNT(*) FROM upload_log ul " +
            "INNER JOIN rules r ON r.id = ul.ruleId " +
            "WHERE ul.status = 'SUCCESS' AND ul.sourceDeleted = 0 AND r.deleteSourceAfterUpload = 1",
    )
    suspend fun countPendingDeletions(): Int

    /**
     * Poda el historial para que `upload_log` no crezca sin límite. SUCCESS/CANCELLED vencen
     * antes porque ya cumplieron su propósito una vez confirmados; FAILED se conserva más tiempo
     * porque es justo lo que alguien va a querer revisar para diagnosticar un problema. PENDING y
     * UPLOADING nunca se tocan acá sin importar la antigüedad: son trabajo en curso, no historial.
     */
    @Query(
        "DELETE FROM upload_log WHERE " +
            "(status IN ('SUCCESS', 'CANCELLED') AND createdAt < :successAndCancelledCutoff) OR " +
            "(status = 'FAILED' AND createdAt < :failedCutoff)",
    )
    suspend fun pruneOlderThan(successAndCancelledCutoff: Long, failedCutoff: Long): Int
}

/** Cuántos archivos hay en cada [UploadStatus], para el widget de pantalla de inicio. */
data class StatusCount(val status: UploadStatus, val count: Int)
