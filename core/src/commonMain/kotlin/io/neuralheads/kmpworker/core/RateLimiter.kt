package io.neuralheads.kmpworker.core

import kotlinx.coroutines.sync.Semaphore

/**
 * Rate limiter for controlling concurrent task execution.
 *
 * ```kotlin
 * val config = KmpWorkerConfig.current()
 * val limiter = RateLimiter(maxConcurrent = 3)
 *
 * // In task execution:
 * limiter.acquire()
 * try {
 *     doWork()
 * } finally {
 *     limiter.release()
 * }
 * ```
 *
 * @param maxConcurrent Maximum number of tasks that can execute simultaneously.
 */
@ExperimentalKmpWorkerApi
class RateLimiter(val maxConcurrent: Int) {

    private val semaphore = Semaphore(maxConcurrent)

    /** Number of currently executing tasks. */
    val activeCount: Int get() = maxConcurrent - semaphore.availablePermits

    /** Acquires a permit, suspending if the limit is reached. */
    suspend fun acquire() = semaphore.acquire()

    /** Releases a permit. */
    fun release() = semaphore.release()

    /** Executes [block] within a rate-limited context. */
    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        return try {
            block()
        } finally {
            release()
        }
    }
}
