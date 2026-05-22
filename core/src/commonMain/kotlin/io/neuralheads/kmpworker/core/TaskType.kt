package io.neuralheads.kmpworker.core

import kotlinx.serialization.Serializable

/**
 * Defines how a task is scheduled.
 *
 * - [OneTime]: Executed once. Use for uploads, syncs, one-off operations.
 * - [Periodic]: Repeated on a fixed interval.
 *
 * **iOS note**: Periodic tasks use BGProcessingTask which is best-effort.
 * Apple controls actual execution timing.
 */
@Serializable
sealed class TaskType {

    /** Execute the task exactly once. */
    @Serializable
    data object OneTime : TaskType()

    /**
     * Execute the task repeatedly.
     *
     * @param repeatIntervalMillis Minimum interval between executions in milliseconds.
     * Note: On iOS, this is a hint to the system. Actual interval is determined by Apple.
     */
    @Serializable
    data class Periodic(
        val repeatIntervalMillis: Long
    ) : TaskType()
}
