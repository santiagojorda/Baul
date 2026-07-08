package com.santiagojorda.baul.storage

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowEnvironment

@RunWith(RobolectricTestRunner::class)
class AllFilesAccessTest {

    @Test
    fun `requestIntent apunta a la pantalla de Ajustes de este paquete`() {
        val context = RuntimeEnvironment.getApplication()

        val intent = AllFilesAccess.requestIntent(context)

        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }

    @Test
    fun `isGranted refleja el estado real de Environment isExternalStorageManager`() {
        ShadowEnvironment.setIsExternalStorageManager(true)
        assertTrue(AllFilesAccess.isGranted())

        ShadowEnvironment.setIsExternalStorageManager(false)
        assertFalse(AllFilesAccess.isGranted())
    }
}
