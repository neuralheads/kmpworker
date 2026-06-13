package io.neuralheads.kmpworker.core

import kotlinx.coroutines.flow.Flow

/**
 * Collects execution metrics for background tasks.
 *
 * Install a [TelemetryCollector] on your [KmpWorker] to track execution timing,
 * success rates, retry counts, and error patterns across all tasks.
 *
 * ```kotlin
 * val telemetry = SqlDelightTelemetryCollector(database)
 * val worker = AndroidKmpWorker(context, telemetry = telemetry)
 *
 * // Query history
 * val records = telemetry.getHistory(limit = 50)
 * val failedRecords = telemetry.getHistory(limit = 10, stateFilter = "FAILED")
 * ```
 */
interface TelemetryCollector {

    /** Records the start of a task execution. */
    suspend fun onTaskStarted(taskId: String, timestamp: Long)

    /** Records the completion of a task execution. */
    suspend fun onTaskCompleted(taskId: String, state: TaskState, timestamp: Long, retryCount: Int)

    /** Returns execution records, most recent first. */
    suspend fun getHistory(limit: Int = 100, stateFilter: String? = null): List<ExecutionRecord>

    /** Removes all execution history records. */
    suspend fun clearHistory()

    /** Removes records older than [olderThanMillis] milliseconds. */
    suspend fun pruneHistory(olderThanMillis: Long)
}
