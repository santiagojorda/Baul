package com.santiagojorda.baul.data.local

import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * getInstance() no lo ejercita ningún otro test (todos usan Room.inMemoryDatabaseBuilder directo
 * para no compartir estado entre tests) — este es el único que prueba el singleton en sí.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {

    @Test
    fun `getInstance devuelve siempre la misma instancia`() {
        val context = RuntimeEnvironment.getApplication()

        val first = AppDatabase.getInstance(context)
        val second = AppDatabase.getInstance(context)

        assertSame(first, second)
    }
}
