package io.neuralheads.kmpworker.core

import kotlinx.serialization.Serializable

/**
 * Describes a background task to be scheduled.
 *
 * @param id Unique identifier for the task. Used for registration, cancellation, and observation.
 * @param type Whether this is a [TaskType.OneTime] or [TaskType.Periodic] task.
 * @param constraints Platform constraints that must be satisfied before execution.
 * @param retryPolicy How the task should be retried on failure.
 * @param priority Execution priority hint. See [TaskPriority].
 * @param tags Optional set of string labels for group cancellation or filtering.
 * @param payload Optional serialized data passed to the task handler via [TaskExecutionContext.payload].
 * @param label Optional human-readable display name for this task (used in logs and future
 *   foreground-service notifications). Defaults to [id] if not set.
 */
@Serializable
data class TaskRequest(
    val id: String,
    val type: TaskType,
    val constraints: Constraints = Constraints(),
    val retryPolicy: RetryPolicy = RetryPolicy.None,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val tags: Set<String> = emptySet(),
    val payload: String? = null,
    val label: String? = null
) {
    /** Human-readable display name — falls back to [id] if [label] is not provided. */
    val displayName: String get() = label ?: id
}
