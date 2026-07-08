package com.santiagojorda.baul.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.santiagojorda.baul.data.local.migration.MIGRATION_1_2
import com.santiagojorda.baul.data.local.migration.MIGRATION_2_3
import com.santiagojorda.baul.data.local.migration.MIGRATION_3_4
import com.santiagojorda.baul.data.local.migration.MIGRATION_4_5
import com.santiagojorda.baul.data.local.migration.MIGRATION_5_6
import com.santiagojorda.baul.data.local.migration.MIGRATION_6_8
import com.santiagojorda.baul.data.local.migration.MIGRATION_8_9
import com.santiagojorda.baul.data.local.migration.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TEST_DB = "migration-test.db"

/**
 * No usa los `schemas/{version}.json` exportados como base para la v1: las versiones 3/4/5/7 nunca se
 * generaron en disco (esa carpeta esta gitignored) y las de 1/2 que sí quedaron reflejan un
 * estado intermedio del commit 902fc12 (con `grantedScopes`/`accessToken` en
 * `connected_accounts`), no el v1 real que shippeo el commit 81e13ca. En vez de confiar en esos
 * archivos, esta prueba arma la v1 real a mano con el SQL de ese commit y deja que Room corra
 * toda la cadena de Migrations al abrir la base, validando el esquema final contra lo compilado.
 */
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrateFrom1ToLatest_preservesExistingRowsAndAppliesNewSchema() {
        seedVersion1Database()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
            )
            .build()
        helper.closeWhenFinished(db)

        val migrated = db.openHelper.writableDatabase

        migrated.query("SELECT folderDisplayName, isAutoCreated FROM rules WHERE id = 1").use { cursor ->
            assertTrue("la regla sembrada en v1 deberia sobrevivir la migracion", cursor.moveToFirst())
            assertEquals("Camera", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        migrated.query("SELECT sourceDeleted, bytesUploaded, totalBytes FROM upload_log WHERE id = 1").use { cursor ->
            assertTrue("el historial sembrado en v1 deberia sobrevivir la migracion", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
        // Tablas agregadas en versiones posteriores: deben existir y estar vacias, sin romper el open().
        for (table in listOf("connected_accounts", "excluded_folders")) {
            migrated.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        // youtube_quota_usage se elimina en MIGRATION_9_10: ya no deberia existir.
        migrated.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'youtube_quota_usage'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        // Las columnas youTube* de `rules` (presentes desde v1) tambien deben haber desaparecido.
        migrated.query("PRAGMA table_info(`rules`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val columnNames = generateSequence { if (cursor.moveToNext()) cursor.getString(nameColumn) else null }.toList()
            assertFalse(columnNames.any { it.startsWith("youTube") })
        }
    }

    @Test
    fun migrateFrom1ToLatest_dropsExistingYouTubeRulesAndTheirHistory() {
        seedVersion1Database()
        seedVersion1YouTubeRuleWithHistory()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
            )
            .build()
        helper.closeWhenFinished(db)

        val migrated = db.openHelper.writableDatabase

        migrated.query("SELECT COUNT(*) FROM `rules` WHERE `destinationType` = 'YOUTUBE'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM `upload_log` WHERE `ruleId` = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        // La regla de Google Photos sembrada en la v1 base no deberia verse afectada.
        migrated.query("SELECT COUNT(*) FROM `rules` WHERE `id` = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    private fun seedVersion1Database() {
        val dbFile = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(TEST_DB)
        dbFile.parentFile?.mkdirs()
        val seed = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        seed.execSQL(
            """
            CREATE TABLE `rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `folderUri` TEXT NOT NULL, `folderDisplayName` TEXT NOT NULL,
            `destinationType` TEXT NOT NULL, `googleAccountEmail` TEXT NOT NULL,
            `youTubeChannelId` TEXT, `youTubePlaylistId` TEXT, `youTubePrivacyStatus` TEXT,
            `youTubeTags` TEXT NOT NULL, `photosAlbumId` TEXT, `photosAlbumName` TEXT,
            `driveFolderId` TEXT, `deleteSourceAfterUpload` INTEGER NOT NULL,
            `wifiOnly` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)
            """.trimIndent(),
        )
        seed.execSQL(
            """
            CREATE TABLE `upload_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `ruleId` INTEGER NOT NULL, `mediaUri` TEXT NOT NULL, `fileName` TEXT NOT NULL,
            `status` TEXT NOT NULL, `errorMessage` TEXT, `remoteId` TEXT,
            `attemptCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
            FOREIGN KEY(`ruleId`) REFERENCES `rules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent(),
        )
        seed.execSQL("CREATE INDEX `index_upload_log_ruleId` ON `upload_log` (`ruleId`)")
        // Room crea esta tabla en el onCreate real; hay que emularla a mano porque acá estamos
        // saltando ese paso y arrancando directo desde el archivo ya "instalado" en v1.
        seed.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        seed.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '')")

        seed.execSQL(
            "INSERT INTO `rules` (`id`, `folderUri`, `folderDisplayName`, `destinationType`, " +
                "`googleAccountEmail`, `youTubeTags`, `deleteSourceAfterUpload`, `wifiOnly`, " +
                "`isActive`, `createdAt`) VALUES " +
                "(1, 'content://tree/foo', 'Camera', 'GOOGLE_PHOTOS', 'user@example.com', '', 1, 1, 1, 1000)",
        )
        seed.execSQL(
            "INSERT INTO `upload_log` (`id`, `ruleId`, `mediaUri`, `fileName`, `status`, " +
                "`attemptCount`, `createdAt`, `updatedAt`) VALUES " +
                "(1, 1, 'content://media/1', 'IMG_0001.jpg', 'SUCCESS', 0, 1000, 1000)",
        )
        seed.version = 1
        seed.close()
    }

    /** Regla YOUTUBE + historial ligado, sembrada aparte para probar que MIGRATION_9_10 los descarta. */
    private fun seedVersion1YouTubeRuleWithHistory() {
        val dbFile = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(TEST_DB)
        val seed = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        seed.execSQL(
            "INSERT INTO `rules` (`id`, `folderUri`, `folderDisplayName`, `destinationType`, " +
                "`googleAccountEmail`, `youTubeChannelId`, `youTubeTags`, `deleteSourceAfterUpload`, " +
                "`wifiOnly`, `isActive`, `createdAt`) VALUES " +
                "(2, 'content://tree/bar', 'Clips', 'YOUTUBE', 'user@example.com', 'channel-1', '', 1, 1, 1, 1000)",
        )
        seed.execSQL(
            "INSERT INTO `upload_log` (`id`, `ruleId`, `mediaUri`, `fileName`, `status`, " +
                "`attemptCount`, `createdAt`, `updatedAt`) VALUES " +
                "(2, 2, 'content://media/2', 'clip.mp4', 'SUCCESS', 0, 1000, 1000)",
        )
        seed.close()
    }
}
