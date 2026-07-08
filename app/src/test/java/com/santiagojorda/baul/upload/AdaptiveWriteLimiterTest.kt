package com.santiagojorda.baul.upload

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [AdaptiveWriteLimiter] es un `object` (singleton de proceso) — se resetea antes y después de
 * cada test para que el orden de ejecución no contamine el estado entre tests.
 */
class AdaptiveWriteLimiterTest {

    @Before
    fun resetBefore() = runTest { AdaptiveWriteLimiter.resetForTesting() }

    @After
    fun resetAfter() = runTest { AdaptiveWriteLimiter.resetForTesting() }

    @Test
    fun `arranca en softLimit 1`() = runTest {
        assertEquals(1, AdaptiveWriteLimiter.currentSoftLimit())
    }

    @Test
    fun `con softLimit 1, dos withSlot no corren al mismo tiempo`() = runTest {
        val concurrent = AtomicInteger(0)
        val maxConcurrentSeen = AtomicInteger(0)

        suspend fun slot() {
            AdaptiveWriteLimiter.withSlot {
                val now = concurrent.incrementAndGet()
                maxConcurrentSeen.updateAndGet { current -> maxOf(current, now) }
                delay(50)
                concurrent.decrementAndGet()
            }
        }

        val job1 = launch { slot() }
        val job2 = launch { slot() }
        job1.join()
        job2.join()

        assertEquals(1, maxConcurrentSeen.get())
    }

    @Test
    fun `tras SUCCESSES_TO_GROW exitos seguidos, softLimit sube en 1`() = runTest {
        repeat(AdaptiveWriteLimiter.SUCCESSES_TO_GROW) {
            AdaptiveWriteLimiter.onResult(quotaExceeded = false)
        }
        assertEquals(2, AdaptiveWriteLimiter.currentSoftLimit())
    }

    @Test
    fun `softLimit nunca supera MAX_CONCURRENCY por mas exitos que se acumulen`() = runTest {
        repeat(AdaptiveWriteLimiter.SUCCESSES_TO_GROW * (AdaptiveWriteLimiter.MAX_CONCURRENCY + 5)) {
            AdaptiveWriteLimiter.onResult(quotaExceeded = false)
        }
        assertEquals(AdaptiveWriteLimiter.MAX_CONCURRENCY, AdaptiveWriteLimiter.currentSoftLimit())
    }

    @Test
    fun `error de cuota baja softLimit, nunca por debajo de 1`() = runTest {
        AdaptiveWriteLimiter.onResult(quotaExceeded = true)
        assertEquals(1, AdaptiveWriteLimiter.currentSoftLimit())
    }

    @Test
    fun `error de cuota resetea el contador de exitos seguidos`() = runTest {
        repeat(AdaptiveWriteLimiter.SUCCESSES_TO_GROW - 1) {
            AdaptiveWriteLimiter.onResult(quotaExceeded = false)
        }
        AdaptiveWriteLimiter.onResult(quotaExceeded = true)
        // Un solo exito mas no alcanza para crecer: el contador se reinició con el error de cuota.
        AdaptiveWriteLimiter.onResult(quotaExceeded = false)
        assertEquals(1, AdaptiveWriteLimiter.currentSoftLimit())
    }
}
