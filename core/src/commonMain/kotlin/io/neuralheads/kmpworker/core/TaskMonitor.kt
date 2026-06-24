@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.neuralheads.kmpworker.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.concurrent.Volatile

/**
 * Broadcasts task state changes across the application.
 *
 * ## Features
 * - `replay=1`: New collectors immediately receive the last known state for a task.
 * - `extraBufferCapacity=64`: Emitters never block due to slow collectors.
 * - **Persistent EventStore**: Install an [EventStore] via [install] to persist terminal
 *   states to disk. Call [replayPendingEvents] at app startup to recover missed completions.
 *
 * ## Usage
 * ```kotlin
 * // App startup:
 * TaskMonitor.install(SqlDelightEventStore(database))
 * TaskMonitor.replayPendingEvents()  // replay missed completions from last session
 *
 * // Observe:
 * TaskMonitor.observe("sync-users").collect { state -> ... }
 *
 * // Emit:
 * TaskMonitor.emit("sync-users", TaskState.Running())
 * ```
 */
object TaskMonitor {

    /**
     * Internal broadcast channel. replay=1 ensures late collectors receive
     * the most recent state immediately on subscription.
     * extraBufferCapacity=64 prevents slow collectors from blocking emitters.
     */
    private val states = MutableSharedFlow<Pair<String, TaskState>>(
        replay = 1,
        extraBufferCapacity = 64
    )

    /** Optional persistent store for terminal-state events. */
    @Volatile private var eventStore: EventStore? = null

    /**
     * Installs an [EventStore] that will receive all terminal state events.
     * Terminal states are: [TaskState.Success], [TaskState.Cancelled], and
     * [TaskState.Failed] with `willRetry = false`.
     *
     * Call once at app startup, before [replayPendingEvents].
     */
    fun install(store: EventStore) {
        eventStore = store
        KmpWorkerLogger.i("TaskMonitor: EventStore installed — cold-launch replay enabled")
    }

    /**
     * Replays all persisted terminal events that have not yet been delivered.
     * Call at app startup (e.g., in Application.onCreate or AppDelegate) to ensure
     * the UI reflects task completions that happened while the app was terminated.
     *
     * No-op if no [EventStore] is installed.
     */
    suspend fun replayPendingEvents() {
        val store = eventStore ?: return
        KmpWorkerLogger.i("TaskMonitor: replaying persisted events…")
        store.replayAll { taskId, state ->
            KmpWorkerLogger.d("TaskMonitor: replaying '$taskId' → $state")
            states.emit(taskId to state)
        }
    }

    /**
     * Emits a new state for the given task ID.
     * Terminal states are automatically persisted to the [EventStore] if installed.
     * Safe to call from any coroutine context.
     */
    suspend fun emit(taskId: String, state: TaskState) {
        KmpWorkerLogger.d("TaskMonitor: $taskId → $state")
        // Persist terminal states before emitting in-memory (so even if the process dies
        // immediately after, the event is safely on disk)
        if (state.isTerminal) {
            eventStore?.record(taskId, state)
        }
        states.emit(taskId to state)
    }

    /**
     * Emits a state without suspending. Drops the event if the buffer is full.
     * Prefer [emit] when a coroutine context is available.
     * Note: Does NOT persist to [EventStore] — use [emit] for terminal states.
     */
    fun tryEmit(taskId: String, state: TaskState): Boolean {
        KmpWorkerLogger.d("TaskMonitor: $taskId → $state (tryEmit)")
        return states.tryEmit(taskId to state)
    }

    /**
     * Returns a [Flow] of [TaskState] updates filtered to the given task ID.
     * The flow never completes — it remains active for the lifetime of the app.
     */
    fun observe(taskId: String): Flow<TaskState> {
        return states
            .filter { (id, _) -> id == taskId }
            .map { (_, state) -> state }
    }

    /**
     * Returns a [Flow] of ALL task state updates as (taskId, [TaskState]) pairs.
     * Useful for global dashboards, monitoring, and [TaskChainExecutor].
     */
    fun observeAll(): Flow<Pair<String, TaskState>> = states

    /**
     * Emits [TaskState.Cancelled] for the given task ID and removes any stored events.
     */
    suspend fun cancel(taskId: String, reason: String = "") {
        eventStore?.clear(taskId)
        emit(taskId, TaskState.Cancelled(reason))
    }

    /**
     * Prunes persisted events older than [olderThanMillis] ms that have already been replayed.
     * Call once per session to keep storage bounded (e.g., at app startup after replay).
     * No-op if no [EventStore] is installed.
     */
    suspend fun pruneOldEvents(olderThanMillis: Long = 7 * 24 * 60 * 60 * 1_000L) {
        eventStore?.prune(olderThanMillis)
    }

    /** Resets the shared flow replay cache. For use in tests only. */
    internal fun reset() { states.resetReplayCache() }

    /** Public alias for [reset]. Accessible from androidTest and integration test modules. */
    fun resetAll() { states.resetReplayCache() }
}
