package com.santiagojorda.mediasync.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.santiagojorda.mediasync.data.local.converter.Converters
import com.santiagojorda.mediasync.data.local.dao.RuleDao
import com.santiagojorda.mediasync.data.local.dao.UploadLogDao
import com.santiagojorda.mediasync.data.local.entity.RuleEntity
import com.santiagojorda.mediasync.data.local.entity.UploadLogEntity

@Database(
    entities = [RuleEntity::class, UploadLogEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun uploadLogDao(): UploadLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mediasync.db",
                ).build().also { instance = it }
            }
    }
}
