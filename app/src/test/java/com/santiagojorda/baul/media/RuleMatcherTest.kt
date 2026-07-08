package com.santiagojorda.baul.media

import com.santiagojorda.baul.data.local.entity.RuleEntity
import com.santiagojorda.baul.domain.model.DestinationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Usa Robolectric porque [RuleMatcher] llama de verdad a `Uri.parse`/`DocumentsContract` (no son
 * puras funciones Kotlin) — sin un runtime Android esas llamadas tiran "not mocked" en un test JVM.
 */
@RunWith(RobolectricTestRunner::class)
class RuleMatcherTest {

    @Test
    fun `folderRelativePath ya resuelto se devuelve directo, sin tocar folderUri`() {
        val rule = ruleWith(folderUri = "", folderRelativePath = "DCIM/AutoCreada/")
        assertEquals("DCIM/AutoCreada/", RuleMatcher.expectedRelativePath(rule))
    }

    @Test
    fun `arbol SAF en volumen primary devuelve la ruta relativa con barra final`() {
        val rule = ruleWith(folderUri = treeUri("primary", "DCIM/Foo"))
        assertEquals("DCIM/Foo/", RuleMatcher.expectedRelativePath(rule))
    }

    @Test
    fun `volumen que no es primary (SD card) devuelve null`() {
        val rule = ruleWith(folderUri = treeUri("1234-5678", "DCIM/Foo"))
        assertNull(RuleMatcher.expectedRelativePath(rule))
    }

    @Test
    fun `uri sin segmento tree devuelve null en vez de tirar excepcion`() {
        val rule = ruleWith(folderUri = "content://com.android.externalstorage.documents/document/primary%3ADCIM")
        assertNull(RuleMatcher.expectedRelativePath(rule))
    }

    @Test
    fun `matches es case-insensitive`() {
        val rule = ruleWith(folderUri = treeUri("primary", "DCIM/Foo"))
        assertTrue(RuleMatcher.matches(rule, "dcim/foo/"))
        assertTrue(RuleMatcher.matches(rule, "DCIM/Foo/"))
    }

    @Test
    fun `matches con mediaRelativePath null devuelve false`() {
        val rule = ruleWith(folderUri = treeUri("primary", "DCIM/Foo"))
        assertFalse(RuleMatcher.matches(rule, null))
    }

    @Test
    fun `matches con ruta distinta devuelve false`() {
        val rule = ruleWith(folderUri = treeUri("primary", "DCIM/Foo"))
        assertFalse(RuleMatcher.matches(rule, "DCIM/Bar/"))
    }

    private fun treeUri(volume: String, path: String): String =
        "content://com.android.externalstorage.documents/tree/" +
            java.net.URLEncoder.encode("$volume:$path", "UTF-8")

    private fun ruleWith(folderUri: String, folderRelativePath: String? = null): RuleEntity = RuleEntity(
        folderUri = folderUri,
        folderDisplayName = "Foo",
        destinationType = DestinationType.GOOGLE_PHOTOS,
        googleAccountEmail = "user@example.com",
        folderRelativePath = folderRelativePath,
    )
}
