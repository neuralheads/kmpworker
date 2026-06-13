package io.neuralheads.kmpworker.core

/**
 * Context provided to a task handler at execution time.
 *
 * Available as the receiver inside handlers registered with context support:
 * ```kotlin
 * kmpWorker.register("upload") { ctx ->
 *     val data = ctx.payload?.let { Json.decodeFromString<MyData>(it) }
 *     println("Attempt ${ctx.retryCount + 1} for ${ctx.taskId}")
 *     uploadService.upload(data)
 * }
 * ```
 *
 * @param taskId The unique ID of the task currently executing.
 * @param retryCount Zero-based number of times this task has been retried.
 * @param payload Optional serialized data from [TaskRequest.payload].
 * @param tags Tags attached to the original [TaskRequest], if any.
 */
data class TaskExecutionContext(
    val taskId: String,
    val retryCount: Int = 0,
    val payload: String? = null,
    val tags: Set<String> = emptySet()
) {
    /** Returns true if this is the first execution attempt (not a retry). */
    val isFirstAttempt: Boolean get() = retryCount == 0

    /** Returns true if this is a retry attempt. */
    val isRetry: Boolean get() = retryCount > 0

    /**
     * Reports progress from within a task handler.
     *
     * Emits a [TaskState.Running] with progress info to [TaskMonitor],
     * which flows to any active observers.
     *
     * ```kotlin
     * kmpWorker.registerWithContext("upload") {
     *     for (i in 0..100 step 10) {
     *         reportProgress(i / 100f, "Uploading chunk $i")
     *         delay(500)
     *     }
     * }
     * ```
     *
     * @param progress Percentage complete (0.0–1.0).
     * @param message Optional human-readable status.
     */
    suspend fun reportProgress(progress: Float, message: String? = null) {
        TaskMonitor.emit(taskId, TaskState.Running(progress = progress, message = message))
    }
}
