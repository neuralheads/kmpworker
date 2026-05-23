package io.neuralheads.kmpworker.core

/**
 * Base class for all exceptions thrown by KMPWorker.
 *
 * Use the specific subclasses to handle individual error conditions:
 * ```kotlin
 * try {
 *     kmpWorker.enqueue(request)
 * } catch (e: KmpWorkerException.TaskAlreadyEnqueuedException) {
 *     // task is already scheduled — safe to ignore or cancel first
 * }
 * ```
 */
sealed class KmpWorkerException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    /**
     * Thrown when [KmpWorker.enqueue] is called with an [id] that is already
     * active (Scheduled or Running) and the caller did not set `replaceExisting = true`.
     *
     * @param taskId The duplicate task ID.
     */
    class TaskAlreadyEnqueuedException(val taskId: String) : KmpWorkerException(
        "Task '$taskId' is already enqueued. Cancel it first or use replaceExisting = true."
    )

    /**
     * Thrown when [KmpWorker.observe], [KmpWorker.cancel], or [KmpWorker.getState]
     * references a task ID that has never been registered or has been purged.
     *
     * @param taskId The unknown task ID.
     */
    class TaskNotFoundException(val taskId: String) : KmpWorkerException(
        "Task '$taskId' was not found. Make sure it was registered before enqueueing."
    )

    /**
     * Thrown when a task exceeds [KmpWorkerConfig.taskTimeout] and is forcefully stopped.
     * The task transitions to [TaskState.TimedOut] before this exception propagates.
     *
     * @param taskId The timed-out task ID.
     * @param timeoutMillis The timeout threshold in milliseconds.
     */
    class TaskTimeoutException(val taskId: String, val timeoutMillis: Long) : KmpWorkerException(
        "Task '$taskId' timed out after ${timeoutMillis}ms."
    )

    /**
     * Thrown when a [TaskChain] fails at one of its steps and cannot continue.
     * The chain transitions to a failed state; already-completed steps are not rolled back.
     *
     * @param chainId The ID of the chain that failed.
     * @param failedStepId The task ID of the step that caused the failure.
     * @param cause The underlying exception from the step handler.
     */
    class ChainExecutionException(
        val chainId: String,
        val failedStepId: String,
        cause: Throwable
    ) : KmpWorkerException(
        "Chain '$chainId' failed at step '$failedStepId': ${cause.message}",
        cause
    )

    /**
     * Thrown when a [TaskRequest] is constructed with invalid parameters —
     * e.g. a blank [TaskRequest.id] or a negative repeat interval.
     *
     * @param reason Human-readable explanation of what is invalid.
     */
    class InvalidTaskRequestException(val reason: String) : KmpWorkerException(
        "Invalid TaskRequest: $reason"
    )
}
