package com.santiagojorda.baul.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 -> v2: se agrega ConnectedAccountEntity (login con Credential Manager + scopes cacheados). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `connected_accounts` (
                `email` TEXT NOT NULL,
                `displayName` TEXT,
                `grantedScopes` TEXT NOT NULL DEFAULT '',
                `accessToken` TEXT,
                `accessTokenExpiresAt` INTEGER,
                PRIMARY KEY(`email`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * v2 -> v3: cambio a GoogleSignInClient + GoogleAuthUtil (ver commit b742ec6). Ya no se cachea
 * token/expiracion/scopes en `connected_accounts`, GoogleAuthUtil pide uno fresco por llamada.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `connected_accounts_new` (
                `email` TEXT NOT NULL,
                `displayName` TEXT,
                PRIMARY KEY(`email`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO `connected_accounts_new` (`email`, `displayName`) " +
                "SELECT `email`, `displayName` FROM `connected_accounts`",
        )
        db.execSQL("DROP TABLE `connected_accounts`")
        db.execSQL("ALTER TABLE `connected_accounts_new` RENAME TO `connected_accounts`")
    }
}

/** v3 -> v4: UploadLogEntity suma `sourceDeleted` para el borrado del original tras subida. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `upload_log` ADD COLUMN `sourceDeleted` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 -> v5: auto-sync de carpetas nuevas (ver commit bf1815d). `connectedAt` no tiene forma de
 * reconstruirse para cuentas ya conectadas, se usa 0 como centinela ("conectada antes de esta
 * migracion"); en el peor caso empata el orden con otras cuentas viejas, no rompe nada porque
 * solo se usa para elegir "la primera cuenta conectada" en carpetas nuevas.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `connected_accounts` ADD COLUMN `connectedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `rules` ADD COLUMN `folderRelativePath` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `rules` ADD COLUMN `isAutoCreated` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v5 -> v6: tabla nueva de carpetas excluidas del auto-sync, configurables desde la app. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `excluded_folders` (
                `folderName` TEXT NOT NULL,
                PRIMARY KEY(`folderName`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * v6 -> v8: la version 7 nunca llego a shippearse (el bump quedo pisado en el mismo commit que
 * el 8), asi que no existe un dispositivo real en ese estado intermedio y esta migracion cubre
 * los dos cambios de una: cuenta default en `connected_accounts` y la tabla de cuota de YouTube.
 */
val MIGRATION_6_8 = object : Migration(6, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `connected_accounts` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `youtube_quota_usage` (
                `date` TEXT NOT NULL,
                `unitsUsed` INTEGER NOT NULL,
                PRIMARY KEY(`date`)
            )
            """.trimIndent(),
        )
    }
}

/** v8 -> v9: progreso real de subida (bytes ya enviados / tamaño total del archivo). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `upload_log` ADD COLUMN `bytesUploaded` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `upload_log` ADD COLUMN `totalBytes` INTEGER NOT NULL DEFAULT 0")
    }
}

val APP_DATABASE_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_8,
    MIGRATION_8_9,
)
