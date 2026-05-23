package io.neuralheads.kmpworker.core

/**
 * Represents the current lifecycle state of a background task.
 *
 * State machine:
 * ```
 * [Scheduled] → [Running] → [Success]
 *                         ↘ [Failed]
 *                         ↘ [TimedOut]   (task exceeded KmpWorkerConfig.taskTimeout)
 * [Scheduled] → [Cancelled]   (before execution begins)
 * ```
 */
sealed class TaskState {

    /** Task has been accepted and queued for execution. */
    data object Scheduled : TaskState()

    /** Task handler is currently executing. */
    data object Running : TaskState()

    /** Task completed successfully. */
    data object Success : TaskState()

    /**
     * Task failed with an exception.
     *
     * @param throwable The exception that caused the failure.
     * @param retryCount How many times this task has already been retried.
     * @param willRetry True if the task will be automatically retried per its [RetryPolicy].
     */
    data class Failed(
        val throwable: Throwable,
        val retryCount: Int = 0,
        val willRetry: Boolean = false
    ) : TaskState()

    /**
     * Task was explicitly cancelled via [KmpWorker.cancel].
     * No retry will occur after cancellation.
     */
    data object Cancelled : TaskState()

    /**
     * Task exceeded the [KmpWorkerConfig.taskTimeout] duration and was stopped.
     * No retry will occur after a timeout — treat the same as [Cancelled].
     *
     * @param afterMillis How many milliseconds the task ran before being timed out.
     */
    data class TimedOut(val afterMillis: Long) : TaskState()

    // ── Convenience properties ────────────────────────────────────────────────

    /** True if the task is in a terminal state (Success, Failed without retry, Cancelled, or TimedOut). */
    val isTerminal: Boolean get() = when (this) {
        is Success   -> true
        is Cancelled -> true
        is TimedOut  -> true
        is Failed    -> !willRetry
        else         -> false
    }

    /** True if the task is currently active (Scheduled or Running). */
    val isActive: Boolean get() = this is Scheduled || this is Running
}
