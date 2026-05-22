package io.neuralheads.kmpworker.core

import kotlinx.coroutines.flow.Flow

/**
 * Main entry point for KMPWorker.
 *
 * Platform-specific implementations:
 * - Android: [io.neuralheads.kmpworker.android.AndroidKmpWorker]
 * - iOS: [io.neuralheads.kmpworker.ios.IOSKmpWorker]
 *
 * Quick start:
 * ```kotlin
 * // Register handler
 * kmpWorker.register("sync-users") { repository.syncUsers() }
 *
 * // Or register with execution context (taskId, retryCount, payload)
 * kmpWorker.register("upload") { ctx ->
 *     if (ctx.isRetry) logger.warn("Retrying upload, attempt ${ctx.retryCount}")
 *     uploader.upload(ctx.payload)
 * }
 *
 * // Enqueue
 * kmpWorker.enqueue(TaskRequest(id = "sync-users", type = TaskType.OneTime))
 *
 * // Observe
 * kmpWorker.observe("sync-users").collect { state -> println(state) }
 * ```
 */
interface KmpWorker {

    /**
     * Schedules a background task. The task handler must be registered
     * via [register] before calling enqueue.
     */
    suspend fun enqueue(request: TaskRequest)

    /**
     * Cancels a previously scheduled task by its ID.
     * Emits [TaskState.Cancelled] to any active observers.
     */
    suspend fun cancel(taskId: String)

    /**
     * Cancels all tasks matching the given [tag].
     * Useful for cancelling groups of related tasks at once.
     */
    suspend fun cancelByTag(tag: String)

    /**
     * Returns a [Flow] of [TaskState] updates for the given task ID.
     * Emits: Scheduled → Running → Success | Failed | Cancelled
     */
    fun observe(taskId: String): Flow<TaskState>

    /**
     * Returns a [Flow] emitting [Pair] of (taskId, [TaskState]) for all tasks.
     * Useful for global task dashboards or monitoring screens.
     */
    fun observeAll(): Flow<Pair<String, TaskState>>

    /**
     * Registers a no-context suspend handler for the given task ID.
     * Must be called before [enqueue].
     */
    fun register(taskId: String, block: suspend () -> Unit)

    /**
     * Registers a handler that receives a [TaskExecutionContext] with the task ID,
     * retry count, payload, and tags at execution time.
     */
    fun registerWithContext(taskId: String, block: suspend TaskExecutionContext.() -> Unit)

    /**
     * Enqueues a [TaskChain] for sequential execution.
     *
     * Each step executes only after the previous one succeeds. Step progress is
     * persisted so chains resume correctly after app termination.
     *
     * Observe chain-level states via [observeChain]:
     * ```kotlin
     * kmpWorker.enqueueChain(chain)
     * kmpWorker.observeChain(chain.id).collect { state ->
     *     when (state) {
     *         is TaskState.Success -> println("Chain complete!")
     *         is TaskState.Failed  -> println("Chain failed at step: ${state.throwable.message}")
     *         else -> {}
     *     }
     * }
     * ```
     */
    suspend fun enqueueChain(chain: TaskChain)

    /**
     * Returns a [Flow] of [TaskState] updates for the chain as a whole.
     * Emits [TaskState.Success] when ALL steps complete, or [TaskState.Failed] if any step fails.
     */
    fun observeChain(chainId: String): Flow<TaskState>
}
