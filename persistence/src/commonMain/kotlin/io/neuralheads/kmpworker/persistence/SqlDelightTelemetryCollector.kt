package io.neuralheads.kmpworker.persistence

import io.neuralheads.kmpworker.core.ExecutionRecord
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.core.TelemetryCollector
import io.neuralheads.kmpworker.persistence.db.KmpWorkerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed implementation of [TelemetryCollector].
 *
 * Persists execution records in the `execution_history` table,
 * enabling post-mortem analysis, analytics uploads, and debugging.
 *
 * ```kotlin
 * val db = KmpWorkerDatabaseFactory.create(context)
 * val telemetry = SqlDelightTelemetryCollector(db)
 * val worker = AndroidKmpWorker(context, telemetry = telemetry)
 *
 * // Later:
 * val last50 = telemetry.getHistory(50)
 * val failures = telemetry.getHistory(10, stateFilter = "FAILED")
 * ```
 */
class SqlDelightTelemetryCollector(
    private val database: KmpWorkerDatabase
) : TelemetryCollector {

    private val queries get() = database.execution_historyQueries
    private val startTimes = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    override suspend fun onTaskStarted(taskId: String, timestamp: Long) {
        mutex.withLock { startTimes[taskId] = timestamp }
    }

    override suspend fun onTaskCompleted(
        taskId: String,
        state: TaskState,
        timestamp: Long,
        retryCount: Int
    ): Unit = withContext(Dispatchers.Default) {
        val startedAt = mutex.withLock { startTimes.remove(taskId) } ?: timestamp
        val durationMs = timestamp - startedAt
        val stateStr = when (state) {
            is TaskState.Success -> "SUCCESS"
            is TaskState.Failed -> "FAILED"
            is TaskState.Cancelled -> "CANCELLED"
            is TaskState.TimedOut -> "TIMED_OUT"
            else -> "UNKNOWN"
        }
        val errorMsg = when (state) {
            is TaskState.Failed -> state.throwable.message
            is TaskState.TimedOut -> "Timed out after ${state.afterMillis}ms"
            is TaskState.Cancelled -> state.reason.takeIf { it.isNotEmpty() }
            else -> null
        }

        queries.insertRecord(
            task_id = taskId,
            started_at = startedAt,
            completed_at = timestamp,
            duration_ms = durationMs,
            state = stateStr,
            retry_count = retryCount.toLong(),
            error_msg = errorMsg
        )
    }

    override suspend fun getHistory(
        limit: Int,
        stateFilter: String?
    ): List<ExecutionRecord> = withContext(Dispatchers.Default) {
        val rows = if (stateFilter != null) {
            queries.getByState(stateFilter, limit.toLong()).executeAsList()
        } else {
            queries.getRecent(limit.toLong()).executeAsList()
        }
        rows.map { row ->
            ExecutionRecord(
                taskId = row.task_id,
                startedAt = row.started_at,
                completedAt = row.completed_at,
                durationMs = row.duration_ms,
                state = row.state,
                retryCount = row.retry_count.toInt(),
                error = row.error_msg
            )
        }
    }

    override suspend fun clearHistory(): Unit = withContext(Dispatchers.Default) {
        queries.deleteAll()
    }

    override suspend fun pruneHistory(olderThanMillis: Long): Unit = withContext(Dispatchers.Default) {
        val cutoff = currentEpochMillis() - olderThanMillis
        queries.pruneOld(cutoff)
    }
}
