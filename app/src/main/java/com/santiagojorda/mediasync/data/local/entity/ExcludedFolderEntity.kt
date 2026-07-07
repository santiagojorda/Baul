package com.santiagojorda.mediasync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Nombre de carpeta (no ruta completa) que el usuario excluyó a mano del auto-sync a Google Photos. */
@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val folderName: String,
)
