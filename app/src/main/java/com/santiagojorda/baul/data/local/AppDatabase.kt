package com.santiagojorda.baul.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.santiagojorda.baul.data.local.converter.Converters
import com.santiagojorda.baul.data.local.dao.ConnectedAccountDao
import com.santiagojorda.baul.data.local.dao.ExcludedFolderDao
import com.santiagojorda.baul.data.local.dao.RuleDao
import com.santiagojorda.baul.data.local.dao.UploadLogDao
import com.santiagojorda.baul.data.local.dao.YouTubeQuotaDao
import com.santiagojorda.baul.data.local.entity.ConnectedAccountEntity
import com.santiagojorda.baul.data.local.entity.ExcludedFolderEntity
import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.data.local.entity.UploadLogEntity
import com.santiagojorda.baul.data.local.entity.YouTubeQuotaUsageEntity
import com.santiagojorda.baul.data.local.migration.APP_DATABASE_MIGRATIONS

@Database(
    entities = [
        RuleEntity::class,
        UploadLogEntity::class,
        ConnectedAccountEntity::class,
        ExcludedFolderEntity::class,
        YouTubeQuotaUsageEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun uploadLogDao(): UploadLogDao
    abstract fun connectedAccountDao(): ConnectedAccountDao
    abstract fun excludedFolderDao(): ExcludedFolderDao
    abstract fun youTubeQuotaDao(): YouTubeQuotaDao

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
                    .addMigrations(*APP_DATABASE_MIGRATIONS)
                    // Solo para downgrade (instalar una version mas vieja de la app sobre una
                    // base ya migrada): no hay forma de deshacer un ALTER TABLE, y es un caso
                    // borde raro (reinstalar un APK anterior), asi que ahi si se acepta perder
                    // los datos en vez de mantener migraciones hacia atras.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
