package com.santiagojorda.mediasync.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.santiagojorda.mediasync.data.local.converter.Converters
import com.santiagojorda.mediasync.data.local.dao.ConnectedAccountDao
import com.santiagojorda.mediasync.data.local.dao.RuleDao
import com.santiagojorda.mediasync.data.local.dao.UploadLogDao
import com.santiagojorda.mediasync.data.local.entity.ConnectedAccountEntity
import com.santiagojorda.mediasync.data.local.entity.RuleEntity
import com.santiagojorda.mediasync.data.local.entity.UploadLogEntity

@Database(
    entities = [RuleEntity::class, UploadLogEntity::class, ConnectedAccountEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun uploadLogDao(): UploadLogDao
    abstract fun connectedAccountDao(): ConnectedAccountDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mediasync.db",
                )
                    // Pre-lanzamiento, sin usuarios reales todavía: mientras el esquema siga
                    // cambiando seguido, mejor recrear la base que escribir Migrations a mano.
                    // Sacar esto y agregar Migration reales antes de tener datos que importen.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
