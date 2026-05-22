package io.neuralheads.kmpworker.persistence

import io.neuralheads.kmpworker.core.EventStore
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.persistence.Task_events
import io.neuralheads.kmpworker.persistence.db.KmpWorkerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [EventStore] that persists terminal task events to SQLite.
 *
 * All events are written atomically. On cold launch, call [replayAll] once at startup
 * (before the UI renders) to re-emit any events missed while the app was terminated.
 *
 * Thread-safety: All operations are dispatched to [Dispatchers.IO].
 *
 * Auto-pruning: Events are kept for 7 days after replay by default. Call [prune]
 * periodically (e.g., on app start after replay) to keep storage bounded.
 */
class SqlDelightEventStore(
    private val database: KmpWorkerDatabase
) : EventStore {

    private val queries get() = database.task_eventsQueries

    override suspend fun record(taskId: String, state: TaskState): Unit =
        withContext(Dispatchers.IO) {
            val (stateType, errorMsg, retryCount, willRetry) = state.toEventTuple()
            queries.insertEvent(
                task_id     = taskId,
                state_type  = stateType,
                error_msg   = errorMsg,
                retry_count = retryCount.toLong(),
                will_retry  = if (willRetry) 1L else 0L,
                created_at  = currentEpochMillis()
            )
        }

    override suspend fun replayAll(
        emit: suspend (taskId: String, state: TaskState) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val events = queries.getUnreplayed().executeAsList()
        for (event in events) {
            val state = event.toTaskState() ?: continue
            withContext(Dispatchers.Default) {
                emit(event.task_id, state)
            }
            queries.markReplayed(event.id)
        }
    }

    override suspend fun clear(taskId: String): Unit = withContext(Dispatchers.IO) {
        queries.deleteForTask(taskId)
    }

    override suspend fun prune(olderThanMillis: Long): Unit = withContext(Dispatchers.IO) {
        val cutoff = currentEpochMillis() - olderThanMillis
        queries.pruneOld(cutoff)
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    /**
     * Converts a [TaskState] to a flat tuple for SQL storage.
     * Only terminal states are expected here.
     */
    private fun TaskState.toEventTuple(): EventTuple = when (this) {
        is TaskState.Success   -> EventTuple("Success",   null, 0, false)
        is TaskState.Cancelled -> EventTuple("Cancelled", null, 0, false)
        is TaskState.Failed    -> EventTuple(
            stateType  = "Failed",
            errorMsg   = throwable.message,
            retryCount = retryCount,
            willRetry  = willRetry
        )
        else -> EventTuple("Success", null, 0, false) // Non-terminal fallback (shouldn't occur)
    }

    private data class EventTuple(
        val stateType: String,
        val errorMsg: String?,
        val retryCount: Int,
        val willRetry: Boolean
    )

    /**
     * Converts a raw SQLDelight row back to a [TaskState].
     * Returns null if the [state_type] is unrecognised.
     */
    private fun Task_events.toTaskState(): TaskState? =
        when (state_type) {
            "Success"   -> TaskState.Success
            "Cancelled" -> TaskState.Cancelled
            "Failed"    -> TaskState.Failed(
                throwable  = Exception(error_msg ?: "Unknown error"),
                retryCount = retry_count.toInt(),
                willRetry  = will_retry != 0L
            )
            else -> null
        }
}
