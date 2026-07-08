package com.santiagojorda.baul.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncFolderPolicyTest {

    @Test
    fun `carpeta fuera de DCIM esta excluida`() {
        assertTrue(AutoSyncFolderPolicy.isExcluded("Pictures/Vacaciones/"))
        assertTrue(AutoSyncFolderPolicy.isExcluded("Download/Vacaciones/"))
    }

    @Test
    fun `carpetas de la lista fija estan excluidas, case-insensitive`() {
        assertTrue(AutoSyncFolderPolicy.isExcluded("DCIM/Camera/"))
        assertTrue(AutoSyncFolderPolicy.isExcluded("DCIM/CAMERA/"))
        assertTrue(AutoSyncFolderPolicy.isExcluded("DCIM/Screenshots/"))
        assertTrue(AutoSyncFolderPolicy.isExcluded("DCIM/WhatsApp Images/"))
        assertTrue(AutoSyncFolderPolicy.isExcluded("DCIM/Telegram Video/"))
    }

    @Test
    fun `carpeta con prefijo guion bajo esta excluida`() {
        assertTrue(AutoSyncFolderPolicy.isExcluded("DCIM/_Privado/"))
    }

    @Test
    fun `carpeta en la lista de exclusiones custom esta excluida`() {
        assertTrue(AutoSyncFolderPolicy.isExcluded("DCIM/MiCarpeta/", customExcludedNames = setOf("micarpeta")))
    }

    @Test
    fun `carpeta normal dentro de DCIM no esta excluida`() {
        assertFalse(AutoSyncFolderPolicy.isExcluded("DCIM/Vacaciones2026/"))
    }

    @Test
    fun `folderDisplayName ignora barra inicial y final`() {
        assertEquals("Foo", AutoSyncFolderPolicy.folderDisplayName("DCIM/Foo/"))
        assertEquals("Foo", AutoSyncFolderPolicy.folderDisplayName("DCIM/Foo"))
        assertEquals("Foo", AutoSyncFolderPolicy.folderDisplayName("/DCIM/Foo/"))
    }
}
