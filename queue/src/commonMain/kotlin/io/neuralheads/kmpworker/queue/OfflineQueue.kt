package io.neuralheads.kmpworker.queue

import io.neuralheads.kmpworker.core.KmpWorker
import io.neuralheads.kmpworker.core.KmpWorkerLogger
import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.persistence.TaskRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Offline-first task queue that persists tasks when the device is offline
 * and automatically replays them when connectivity is restored.
 *
 * Flow:
 * ```
 * enqueue(request)
 *      ↓
 * isOnline?
 *      ↓ YES → execute immediately via KmpWorker
 *      ↓ NO  → persist to TaskRepository (status = PENDING)
 *              ↓
 *         network restored
 *              ↓
 *         replay() → re-enqueue all PENDING tasks
 * ```
 *
 * **Recovery on app restart**: Pending tasks survive app termination because
 * they are persisted to SQLDelight. On next app start, call [replay] manually
 * or ensure [start] is called so the queue can re-enqueue them.
 *
 * Usage:
 * ```kotlin
 * val queue = OfflineQueue(kmpWorker, repository, networkMonitor)
 * queue.start()
 *
 * queue.enqueue(myTaskRequest)
 * ```
 */
class OfflineQueue(
    private val worker: KmpWorker,
    private val repository: TaskRepository,
    private val networkMonitor: NetworkMonitor
) {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        KmpWorkerLogger.e("OfflineQueue: unhandled exception in background scope", throwable)
    }

    private val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + exceptionHandler
    )

    // Not using @Volatile — started is set once from main thread during init.
    private var started = false

    /**
     * Starts the offline queue.
     *
     * Sets up automatic replay when network is restored.
     * Call this once during app initialization, after [NetworkMonitor.start].
     *
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun start() {
        if (started) return
        started = true

        networkMonitor.isOnline
            .onEach { online ->
                if (online) {
                    KmpWorkerLogger.i("OfflineQueue: network restored, replaying pending tasks")
                    safeReplay()
                }
            }
            .launchIn(scope)

        KmpWorkerLogger.i("OfflineQueue: started")
    }

    /**
     * Enqueues a task request.
     *
     * - If online: executes immediately via [KmpWorker.enqueue].
     * - If offline: persists to [TaskRepository] for later replay.
     *
     * **Deduplication**: If a task with the same ID already exists in the
     * repository (status PENDING or RUNNING), it will not be inserted again.
     *
     * @param request The task to schedule.
     */
    suspend fun enqueue(request: TaskRequest) {
        if (networkMonitor.isCurrentlyOnline()) {
            KmpWorkerLogger.d("OfflineQueue: online — dispatching '${request.id}' immediately")
            executeNow(request)
        } else {
            // Deduplication: don't persist duplicate pending task IDs
            val alreadyPending = repository.getById(request.id) != null
            if (alreadyPending) {
                KmpWorkerLogger.d("OfflineQueue: '${request.id}' already pending, skipping duplicate")
                return
            }
            KmpWorkerLogger.d("OfflineQueue: offline — persisting '${request.id}' for later replay")
            repository.insert(request)
        }
    }

    /**
     * Manually triggers replay of all pending tasks from the database.
     *
     * Called automatically on network restore, but can also be triggered
     * manually on app foreground or explicit user action.
     */
    suspend fun replay() {
        val pending = repository.getPending()
        if (pending.isEmpty()) {
            KmpWorkerLogger.d("OfflineQueue: replay — no pending tasks")
            return
        }

        KmpWorkerLogger.i("OfflineQueue: replaying ${pending.size} pending task(s)")
        pending.forEach { request ->
            try {
                executeNow(request)
                repository.delete(request.id)
            } catch (e: Exception) {
                KmpWorkerLogger.e("OfflineQueue: failed to replay '${request.id}'", e)
                // Leave in DB — will be retried on next replay
            }
        }
    }

    /**
     * Returns the number of tasks currently pending in the offline queue.
     * A task is counted if its status is PENDING or RUNNING.
     */
    suspend fun pendingCount(): Int = repository.getPending().size

    /** Stops the coroutine scope. Call in cleanup/onDestroy if needed. */
    fun stop() {
        scope.coroutineContext[Job]?.cancel()
        KmpWorkerLogger.i("OfflineQueue: stopped")
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private fun safeReplay() {
        scope.launch {
            try {
                replay()
            } catch (e: Exception) {
                KmpWorkerLogger.e("OfflineQueue: replay failed", e)
            }
        }
    }

    /**
     * Executes a task request immediately by forwarding it to [KmpWorker.enqueue].
     *
     * Called from suspend contexts ([enqueue], [replay]) so we invoke directly
     * rather than launching a new coroutine — this keeps execution deterministic
     * under test dispatchers.
     */
    private suspend fun executeNow(request: TaskRequest) {
        worker.enqueue(request)
    }
}
