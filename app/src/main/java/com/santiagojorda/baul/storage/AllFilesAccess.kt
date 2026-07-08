package com.santiagojorda.baul.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings

/**
 * Con este permiso otorgado, borrar un archivo que la app no creó no exige el diálogo de
 * confirmación de [android.provider.MediaStore.createDeleteRequest] — se puede borrar directo
 * vía [android.content.ContentResolver.delete]. Es un permiso especial: no hay popup in-app,
 * el usuario tiene que activarlo a mano en la pantalla de Ajustes que abre [requestIntent].
 */
object AllFilesAccess {

    fun isGranted(): Boolean = Environment.isExternalStorageManager()

    fun requestIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
}
