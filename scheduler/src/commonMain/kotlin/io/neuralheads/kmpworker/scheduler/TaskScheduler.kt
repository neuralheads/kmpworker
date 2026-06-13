package io.neuralheads.kmpworker.scheduler

import io.neuralheads.kmpworker.core.ChainPolicy
import io.neuralheads.kmpworker.core.KmpWorker
import io.neuralheads.kmpworker.core.TaskChain
import io.neuralheads.kmpworker.core.TaskExecutionContext
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Internal scheduling abstraction used by platform implementations.
 *
 * Extends [KmpWorker] with no additional API surface — exists as a named type
 * so platform modules can implement a single, focused contract.
 *
 * [enqueueChain] and [observeChain] are intentionally NOT implemented here —
 * they are handled at the [KmpWorker] layer (AndroidKmpWorker / IOSKmpWorker).
 *
 * Platform implementations:
 * - Android → [io.neuralheads.kmpworker.android.AndroidTaskScheduler]
 * - iOS     → [io.neuralheads.kmpworker.ios.IOSTaskScheduler]
 */
interface TaskScheduler : KmpWorker {
    override suspend fun enqueue(request: TaskRequest)
    override suspend fun cancel(taskId: String)
    override suspend fun cancelByTag(tag: String)
    override fun observe(taskId: String): Flow<TaskState>
    override fun observeAll(): Flow<Pair<String, TaskState>>
    override fun register(taskId: String, block: suspend () -> Unit)
    override fun registerWithContext(taskId: String, block: suspend TaskExecutionContext.() -> Unit)

    // Chain methods are delegated to the KmpWorker implementation layer — schedulers don't handle them.
    override suspend fun enqueueChain(chain: TaskChain, policy: ChainPolicy) =
        error("enqueueChain() is not supported at the TaskScheduler level. Use KmpWorker.enqueueChain().")

    override fun observeChain(chainId: String): Flow<TaskState> = emptyFlow()
}
