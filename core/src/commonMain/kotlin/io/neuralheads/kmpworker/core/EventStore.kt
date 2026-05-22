package io.neuralheads.kmpworker.core

/**
 * Persists task completion events to durable storage so they survive app termination.
 *
 * ## Problem it solves
 * [TaskMonitor] is in-memory. If a task completes while the app is in the background
 * (killed by OS), the Success/Failed event is emitted into the void. On cold launch,
 * the UI has no way to know what happened.
 *
 * ## Solution
 * [EventStore] writes terminal states to disk. On app startup, call [replayAll] to
 * re-emit all unread events through [TaskMonitor], updating the UI with missed results.
 *
 * ## Usage
 * ```kotlin
 * // At app startup (before UI renders):
 * val store = SqlDelightEventStore(database)
 * TaskMonitor.install(store)
 * TaskMonitor.replayPendingEvents()
 * ```
 */
interface EventStore {

    /**
     * Persists a terminal task state to durable storage.
     * Only called for terminal states: [TaskState.Success], [TaskState.Failed] (willRetry=false),
     * and [TaskState.Cancelled].
     */
    suspend fun record(taskId: String, state: TaskState)

    /**
     * Replays all previously recorded events that have not yet been replayed.
     * The [emit] lambda is called for each unread event in chronological order.
     * After calling [emit], the event is marked as replayed and will not appear again.
     */
    suspend fun replayAll(emit: suspend (taskId: String, state: TaskState) -> Unit)

    /**
     * Deletes all persisted events for the given task ID.
     * Call after a task is explicitly cancelled or removed.
     */
    suspend fun clear(taskId: String)

    /**
     * Deletes all events older than [olderThanMillis] milliseconds that have already been replayed.
     * Call periodically to prevent unbounded storage growth.
     */
    suspend fun prune(olderThanMillis: Long)
}
