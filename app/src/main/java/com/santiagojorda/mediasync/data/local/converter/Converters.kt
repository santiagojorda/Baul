package com.santiagojorda.mediasync.data.local.converter

import androidx.room.TypeConverter
import com.santiagojorda.mediasync.domain.model.DestinationType
import com.santiagojorda.mediasync.domain.model.UploadStatus
import com.santiagojorda.mediasync.domain.model.YouTubePrivacyStatus

/** Separador que no puede aparecer en un tag de YouTube ni en un scope OAuth. */
private const val LIST_SEPARATOR = ""

class Converters {

    @TypeConverter
    fun fromDestinationType(value: DestinationType): String = value.name

    @TypeConverter
    fun toDestinationType(value: String): DestinationType = DestinationType.valueOf(value)

    @TypeConverter
    fun fromYouTubePrivacyStatus(value: YouTubePrivacyStatus?): String? = value?.name

    @TypeConverter
    fun toYouTubePrivacyStatus(value: String?): YouTubePrivacyStatus? =
        value?.let { YouTubePrivacyStatus.valueOf(it) }

    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(separator = LIST_SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(LIST_SEPARATOR)
}
