package io.neuralheads.kmpworker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryEngineTest {

    @Test
    fun `None policy returns 0 delay`() {
        assertEquals(0L, RetryEngine.nextDelay(0, RetryPolicy.None))
        assertEquals(0L, RetryEngine.nextDelay(5, RetryPolicy.None))
    }

    @Test
    fun `None policy should not retry`() {
        assertFalse(RetryEngine.shouldRetry(0, RetryPolicy.None))
        assertFalse(RetryEngine.shouldRetry(10, RetryPolicy.None))
    }

    @Test
    fun `Linear policy returns fixed delay`() {
        val policy = RetryPolicy.Linear(delayMillis = 3_000)
        assertEquals(3_000L, RetryEngine.nextDelay(0, policy))
        assertEquals(3_000L, RetryEngine.nextDelay(5, policy))
    }

    @Test
    fun `Linear policy always retries`() {
        val policy = RetryPolicy.Linear(delayMillis = 1_000)
        assertTrue(RetryEngine.shouldRetry(0, policy))
        assertTrue(RetryEngine.shouldRetry(100, policy))
    }

    @Test
    fun `Exponential policy doubles delay each retry`() {
        val policy = RetryPolicy.Exponential(initialDelayMillis = 5_000)
        assertEquals(5_000L, RetryEngine.nextDelay(0, policy))
        assertEquals(10_000L, RetryEngine.nextDelay(1, policy))
        assertEquals(20_000L, RetryEngine.nextDelay(2, policy))
        assertEquals(40_000L, RetryEngine.nextDelay(3, policy))
    }

    @Test
    fun `Exponential policy respects maxRetries`() {
        val policy = RetryPolicy.Exponential(initialDelayMillis = 1_000, maxRetries = 3)
        assertTrue(RetryEngine.shouldRetry(0, policy))
        assertTrue(RetryEngine.shouldRetry(2, policy))
        assertFalse(RetryEngine.shouldRetry(3, policy))
        assertFalse(RetryEngine.shouldRetry(10, policy))
    }

    @Test
    fun `Exponential large retryCount does not overflow`() {
        val policy = RetryPolicy.Exponential(initialDelayMillis = 1_000)
        // retryCount=100 should not throw ArithmeticException
        val delay = RetryEngine.nextDelay(100, policy)
        assertTrue(delay > 0)
    }
}
