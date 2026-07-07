package com.santiagojorda.baul.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.santiagojorda.baul.domain.model.UploadStatus

@Entity(
    tableName = "upload_log",
    foreignKeys = [
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ruleId")],
)
data class UploadLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val mediaUri: String,
    val fileName: String,
    val status: UploadStatus = UploadStatus.PENDING,
    val errorMessage: String? = null,
    val remoteId: String? = null,
    val attemptCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    /** El borrado necesita confirmación del sistema (Activity), no lo puede hacer el Worker solo. */
    val sourceDeleted: Boolean = false,
    val bytesUploaded: Long = 0,
    val totalBytes: Long = 0,
)
