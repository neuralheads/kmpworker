package io.neuralheads.kmpworker.core

import kotlinx.serialization.Serializable

/**
 * Defines how a task is scheduled.
 *
 * - [OneTime]: Executed once as soon as constraints are met.
 * - [Periodic]: Repeated on a fixed interval.
 * - [ExactTime]: Executed once at (or after) a specific wall-clock time.
 *
 * **iOS note**: iOS does not support truly exact scheduling. [ExactTime] maps to
 * `BGAppRefreshTaskRequest.earliestBeginDate` — the OS guarantees the task will
 * NOT run before that time, but may delay it further based on system conditions.
 *
 * **Android note**: [ExactTime] uses WorkManager's `setInitialDelay()`. This is
 * battery-friendly but not millisecond-precise. For hard real-time requirements,
 * use `AlarmManager` directly.
 */
@Serializable
sealed class TaskType {

    /** Execute the task once, as soon as possible given constraints. */
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

    /**
     * Execute the task once at or after [runAtMillis] (UTC epoch milliseconds).
     *
     * ```kotlin
     * val tomorrow = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
     * val request = TaskRequest(
     *     id = "daily-sync",
     *     type = TaskType.ExactTime(runAtMillis = tomorrow)
     * )
     * ```
     *
     * @param runAtMillis The earliest allowed execution time as UTC epoch milliseconds.
     */
    @Serializable
    data class ExactTime(
        val runAtMillis: Long
    ) : TaskType()

    /**
     * Execute the task once within a time window.
     *
     * Preferred over [ExactTime] on iOS — gives the OS more scheduling flexibility.
     *
     * ```kotlin
     * val now = System.currentTimeMillis()
     * val request = TaskRequest(
     *     id = "nightly-cleanup",
     *     type = TaskType.Windowed(
     *         earliestMillis = now + 2 * 60 * 60 * 1000L,  // 2 hours from now
     *         latestMillis   = now + 6 * 60 * 60 * 1000L   // 6 hours from now
     *     )
     * )
     * ```
     *
     * **Android**: Maps to `setInitialDelay(earliest)` with flex interval `latest - earliest`.
     * **iOS**: Maps to `BGAppRefreshTaskRequest` with `earliestBeginDate`.
     *
     * @param earliestMillis Earliest allowed execution (UTC epoch ms).
     * @param latestMillis Latest preferred execution (UTC epoch ms). Best-effort on both platforms.
     */
    @Serializable
    data class Windowed(
        val earliestMillis: Long,
        val latestMillis: Long
    ) : TaskType()
}
