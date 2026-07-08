package com.santiagojorda.baul.data.local.converter

import androidx.room.TypeConverter
import com.santiagojorda.baul.domain.model.DestinationType
import com.santiagojorda.baul.domain.model.UploadStatus

class Converters {

    @TypeConverter
    fun fromDestinationType(value: DestinationType): String = value.name

    @TypeConverter
    fun toDestinationType(value: String): DestinationType = DestinationType.valueOf(value)

    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)
}
