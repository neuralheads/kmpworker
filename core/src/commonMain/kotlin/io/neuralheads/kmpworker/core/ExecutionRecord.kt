package io.neuralheads.kmpworker.core

/**
 * A single execution record for telemetry and analytics.
 *
 * Captured automatically when a [TelemetryCollector] is installed.
 *
 * @param taskId The task that was executed.
 * @param startedAt Epoch millis when execution began.
 * @param completedAt Epoch millis when execution finished.
 * @param durationMs How long the execution took.
 * @param state Terminal state: "SUCCESS", "FAILED", "CANCELLED", "TIMED_OUT".
 * @param retryCount How many retries occurred before this result.
 * @param error Error message if the task failed, null otherwise.
 */
data class ExecutionRecord(
    val taskId: String,
    val startedAt: Long,
    val completedAt: Long,
    val durationMs: Long,
    val state: String,
    val retryCount: Int,
    val error: String?
)
