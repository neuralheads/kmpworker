package io.neuralheads.kmpworker.core

import kotlinx.serialization.Serializable

/**
 * Defines how a failed task should be retried.
 *
 * - [None]: No retry. Task fails immediately on error.
 * - [Linear]: Retry with a fixed delay between attempts.
 * - [Exponential]: Retry with exponentially increasing delay.
 *   Formula: `delay = initialDelayMillis × 2^retryCount`
 *
 * Example:
 * ```kotlin
 * retryPolicy = RetryPolicy.Exponential(initialDelayMillis = 5_000)
 * // Attempt 0: immediate
 * // Attempt 1: 5s
 * // Attempt 2: 10s
 * // Attempt 3: 20s
 * // ...
 * ```
 */
@Serializable
sealed class RetryPolicy {

    /** No retry on failure. */
    @Serializable
    data object None : RetryPolicy()

    /**
     * Retry with a fixed delay between each attempt.
     *
     * @param delayMillis Fixed delay in milliseconds between retries.
     */
    @Serializable
    data class Linear(
        val delayMillis: Long
    ) : RetryPolicy()

    /**
     * Retry with exponentially increasing delay.
     * delay(n) = initialDelayMillis × 2^n
     *
     * @param initialDelayMillis Base delay in milliseconds for the first retry.
     * @param maxRetries Maximum number of retry attempts. Default is unlimited.
     */
    @Serializable
    data class Exponential(
        val initialDelayMillis: Long,
        val maxRetries: Int = Int.MAX_VALUE
    ) : RetryPolicy()
}
