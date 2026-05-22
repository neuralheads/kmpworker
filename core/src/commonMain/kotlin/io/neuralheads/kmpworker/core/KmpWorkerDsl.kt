package io.neuralheads.kmpworker.core

import kotlin.time.Duration

/**
 * Kotlin DSL extensions for [KmpWorker] to provide a more ergonomic API.
 *
 * Usage:
 * ```kotlin
 * // Periodic task with Kotlin Duration
 * kmpWorker.periodic(id = "sync", repeatInterval = 6.hours) {
 *     repository.sync()
 * }
 *
 * // One-time task with inline registration + enqueue
 * kmpWorker.oneTime(id = "upload") {
 *     uploader.run()
 * }
 * ```
 */

/**
 * Registers and enqueues a one-time task in a single call.
 *
 * @param id Unique task identifier.
 * @param constraints Platform constraints for execution.
 * @param retryPolicy How to retry on failure.
 * @param payload Optional serialized data.
 * @param block The suspend handler to execute.
 */
suspend fun KmpWorker.oneTime(
    id: String,
    constraints: Constraints = Constraints(),
    retryPolicy: RetryPolicy = RetryPolicy.None,
    payload: String? = null,
    block: suspend () -> Unit
) {
    register(id, block)
    enqueue(
        TaskRequest(
            id = id,
            type = TaskType.OneTime,
            constraints = constraints,
            retryPolicy = retryPolicy,
            payload = payload
        )
    )
}

/**
 * Registers and enqueues a periodic task using a [Duration] interval.
 *
 * @param id Unique task identifier.
 * @param repeatInterval How often the task should run.
 * @param constraints Platform constraints for execution.
 * @param retryPolicy How to retry on failure.
 * @param block The suspend handler to execute.
 */
suspend fun KmpWorker.periodic(
    id: String,
    repeatInterval: Duration,
    constraints: Constraints = Constraints(),
    retryPolicy: RetryPolicy = RetryPolicy.None,
    block: suspend () -> Unit
) {
    register(id, block)
    enqueue(
        TaskRequest(
            id = id,
            type = TaskType.Periodic(repeatIntervalMillis = repeatInterval.inWholeMilliseconds),
            constraints = constraints,
            retryPolicy = retryPolicy
        )
    )
}

/**
 * Enqueues a task with exponential backoff using [Duration]-based delay.
 */
fun exponentialRetry(
    initialDelay: Duration,
    maxRetries: Int = KmpWorkerConfig.current().maxRetries
): RetryPolicy.Exponential {
    return RetryPolicy.Exponential(
        initialDelayMillis = initialDelay.inWholeMilliseconds,
        maxRetries = maxRetries
    )
}

/**
 * Enqueues a task with linear retry using [Duration]-based delay.
 */
fun linearRetry(delay: Duration): RetryPolicy.Linear {
    return RetryPolicy.Linear(delayMillis = delay.inWholeMilliseconds)
}
