package io.neuralheads.kmpworker.testing

import io.neuralheads.kmpworker.core.ChainPolicy
import io.neuralheads.kmpworker.core.ExecutionRecord
import io.neuralheads.kmpworker.core.KmpWorker
import io.neuralheads.kmpworker.core.TaskChain
import io.neuralheads.kmpworker.core.TaskExecutionContext
import io.neuralheads.kmpworker.core.TaskMonitor
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Test double for [KmpWorker] that executes tasks immediately in-process.
 *
 * No WorkManager or BGTaskScheduler involved — runs handlers synchronously
 * in the calling coroutine context.
 *
 * Features:
 * - Immediate execution (no background scheduling delay)
 * - Tracks all enqueued and cancelled task IDs
 * - Supports simulating failures via [failureCount]
 * - Supports retry scenario simulation
 * - Fully observable via [observe] and [observeAll]
 * - Supports both `register()` and `registerWithContext()`
 * - Group cancellation via `cancelByTag()`
 *
 * Usage:
 * ```kotlin
 * val fakeWorker = FakeKmpWorker()
 *
 * fakeWorker.register("sync") { repository.sync() }
 * fakeWorker.enqueue(TaskRequest(id = "sync", type = TaskType.OneTime))
 *
 * assertEquals(TaskState.Success, fakeWorker.lastStateFor("sync"))
 * ```
 */
class FakeKmpWorker : KmpWorker {

    private val handlers = mutableMapOf<String, suspend TaskExecutionContext.() -> Unit>()
    private val states = MutableSharedFlow<Pair<String, TaskState>>(
        replay = 32,
        extraBufferCapacity = 64
    )

    /** All task requests that have been enqueued. */
    val enqueuedTasks = mutableListOf<TaskRequest>()

    /** All task IDs that have been cancelled. */
    val cancelledTasks = mutableListOf<String>()

    /** All tags that have been cancelled via [cancelByTag]. */
    val cancelledTags = mutableListOf<String>()

    /**
     * Map of task ID → number of times it should fail before succeeding.
     * Use to simulate retry scenarios.
     *
     * Example: `fakeWorker.failureCount["upload"] = 2` → fails 2 times, then succeeds.
     */
    val failureCount = mutableMapOf<String, Int>()

    private val executionCount = mutableMapOf<String, Int>()

    override suspend fun enqueue(request: TaskRequest) {
        enqueuedTasks.add(request)
        emitState(request.id, TaskState.Scheduled)

        val failTimes = failureCount[request.id] ?: 0
        val attempts = executionCount[request.id] ?: 0
        executionCount[request.id] = attempts + 1

        emitState(request.id, TaskState.Running())

        if (attempts < failTimes) {
            val error = Exception("Simulated failure (attempt ${attempts + 1} of $failTimes)")
            emitState(request.id, TaskState.Failed(throwable = error, retryCount = attempts, willRetry = attempts + 1 < failTimes))
            return
        }

        val handler = handlers[request.id]
        if (handler == null) {
            emitState(request.id, TaskState.Failed(
                throwable = IllegalStateException("No handler registered for '${request.id}'")
            ))
            return
        }

        val ctx = TaskExecutionContext(
            taskId = request.id,
            retryCount = attempts,
            payload = request.payload,
            tags = request.tags
        )

        try {
            handler.invoke(ctx)
            emitState(request.id, TaskState.Success)
        } catch (e: Exception) {
            emitState(request.id, TaskState.Failed(throwable = e, retryCount = attempts))
        }
    }

    override suspend fun cancel(taskId: String) {
        cancelledTasks.add(taskId)
        emitState(taskId, TaskState.Cancelled)
    }

    override suspend fun cancelByTag(tag: String) {
        cancelledTags.add(tag)
        // Cancel all enqueued tasks that carry this tag
        enqueuedTasks
            .filter { it.tags.contains(tag) }
            .forEach { cancel(it.id) }
    }

    override fun observe(taskId: String): Flow<TaskState> =
        states
            .filter { (id, _) -> id == taskId }
            .map { (_, state) -> state }

    override fun observeAll(): Flow<Pair<String, TaskState>> = states

    override fun register(taskId: String, block: suspend () -> Unit) {
        handlers[taskId] = { block() }
    }

    override fun registerWithContext(taskId: String, block: suspend TaskExecutionContext.() -> Unit) {
        handlers[taskId] = block
    }

    /**
     * Executes a [TaskChain] step-by-step in-process (no background scheduling).
     * Ideal for unit tests — each step runs immediately and synchronously.
     * Chain-level Success/Failed states are emitted to both [states] and [TaskMonitor].
     */
    override suspend fun enqueueChain(chain: TaskChain, policy: ChainPolicy) {
        emitState(chain.id, TaskState.Scheduled)
        for ((index, step) in chain.steps.withIndex()) {
            // stepRequest() is internal to :core — inline its effect:
            // it scopes the id as "${chain.id}:step:$index" internally, then
            // FakeKmpWorker would restore step.id anyway. Net result: just use step.
            enqueue(step)
            val lastState = allStatesFor(step.id).lastOrNull()
            if (lastState is TaskState.Failed && !lastState.willRetry) {
                // Chain fails at this step
                emitState(chain.id, TaskState.Failed(
                    throwable  = Exception("Chain '${chain.id}' failed at step $index ('${step.id}'): ${lastState.throwable.message}"),
                    willRetry  = false
                ))
                return
            }
        }
        emitState(chain.id, TaskState.Success)
    }

    override fun observeChain(chainId: String): Flow<TaskState> = observe(chainId)

    // ── Assertion helpers ────────────────────────────────────────────────────

    /** Returns the last emitted state for a given task ID. */
    fun lastStateFor(taskId: String): TaskState? =
        states.replayCache.lastOrNull { it.first == taskId }?.second

    /** Returns all emitted states for a given task ID in order. */
    fun allStatesFor(taskId: String): List<TaskState> =
        states.replayCache.filter { it.first == taskId }.map { it.second }

    /** Returns true if the task was enqueued at least once. */
    fun wasEnqueued(taskId: String): Boolean =
        enqueuedTasks.any { it.id == taskId }

    /** Returns true if the task was cancelled. */
    fun wasCancelled(taskId: String): Boolean =
        cancelledTasks.contains(taskId)

    /** Returns true if a tag was cancelled. */
    fun wasTagCancelled(tag: String): Boolean =
        cancelledTags.contains(tag)

    /** Returns how many times the given task was executed. */
    fun executionCountFor(taskId: String): Int =
        executionCount[taskId] ?: 0

    /** Resets all state — handlers, enqueued/cancelled lists, execution counts, and state history. */
    fun reset() {
        handlers.clear()
        enqueuedTasks.clear()
        cancelledTasks.clear()
        cancelledTags.clear()
        failureCount.clear()
        executionCount.clear()
        states.resetReplayCache()
    }

    private suspend fun emitState(taskId: String, state: TaskState) {
        states.emit(taskId to state)
    }
}
