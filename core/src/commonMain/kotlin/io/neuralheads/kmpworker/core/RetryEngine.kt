package io.neuralheads.kmpworker.core

/**
 * Pure retry delay calculator. Stateless — no side effects.
 *
 * Used by platform workers (Android CoroutineWorker, iOS task handler)
 * to determine how long to wait before the next attempt.
 *
 * Delay formula by policy:
 * - [RetryPolicy.None]        → 0ms (caller should not retry)
 * - [RetryPolicy.Linear]      → delayMillis (fixed)
 * - [RetryPolicy.Exponential] → initialDelayMillis × 2^retryCount
 *
 * Example:
 * ```kotlin
 * val policy = RetryPolicy.Exponential(initialDelayMillis = 5_000)
 * RetryEngine.nextDelay(0, policy) // 5_000ms
 * RetryEngine.nextDelay(1, policy) // 10_000ms
 * RetryEngine.nextDelay(2, policy) // 20_000ms
 * ```
 */
object RetryEngine {

    /**
     * Returns the delay in milliseconds before the next retry attempt.
     *
     * @param retryCount Zero-based index of the current retry attempt.
     * @param policy The configured retry policy.
     * @return Delay in milliseconds. Returns 0 for [RetryPolicy.None].
     */
    fun nextDelay(retryCount: Int, policy: RetryPolicy): Long {
        return when (policy) {
            is RetryPolicy.None -> 0L
            is RetryPolicy.Linear -> policy.delayMillis
            is RetryPolicy.Exponential -> {
                // Guard against overflow: 2^63 overflows Long. Cap the delay at
                // Long.MAX_VALUE / 2 (~106 billion days) which is a safe practical ceiling.
                val maxDelay = Long.MAX_VALUE / 2
                val shift = retryCount.coerceIn(0, 62)
                val multiplier = 1L shl shift  // 2^shift, max 2^62 ≈ 4.6×10^18
                // Use saturating multiplication to avoid overflow
                if (multiplier > maxDelay / policy.initialDelayMillis.coerceAtLeast(1L)) {
                    maxDelay
                } else {
                    policy.initialDelayMillis * multiplier
                }
            }
        }
    }

    /**
     * Returns true if the task should be retried given the policy and current attempt count.
     *
     * @param retryCount Zero-based index of the current retry attempt.
     * @param policy The configured retry policy.
     */
    fun shouldRetry(retryCount: Int, policy: RetryPolicy): Boolean {
        return when (policy) {
            is RetryPolicy.None -> false
            is RetryPolicy.Linear -> true
            is RetryPolicy.Exponential -> retryCount < policy.maxRetries
        }
    }
}
