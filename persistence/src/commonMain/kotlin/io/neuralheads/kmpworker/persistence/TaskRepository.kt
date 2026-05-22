package io.neuralheads.kmpworker.persistence

import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskType

/**
 * Repository abstraction for persisting task state across app restarts.
 *
 * The primary use case is task recovery:
 * On app restart, call [getPending] to find tasks that were interrupted
 * and re-enqueue them via [KmpWorker.enqueue].
 */
interface TaskRepository {

    /**
     * Persists a new [TaskRequest] with status PENDING.
     * No-ops if a task with the same ID already exists.
     */
    suspend fun insert(task: TaskRequest)

    /**
     * Updates the status and retry count for a task.
     *
     * @param id The task ID.
     * @param status One of: "PENDING", "RUNNING", "SUCCESS", "FAILED"
     * @param retryCount Current retry attempt count.
     */
    suspend fun updateStatus(id: String, status: String, retryCount: Int)

    /**
     * Removes a task from the store. Call after [TaskState.Success].
     */
    suspend fun delete(id: String)

    /**
     * Returns all persisted tasks.
     */
    suspend fun getAll(): List<TaskRequest>

    /**
     * Returns tasks with status PENDING or RUNNING.
     * Use this on app restart to recover and re-enqueue interrupted tasks.
     */
    suspend fun getPending(): List<TaskRequest>

    /**
     * Returns a single task by ID, or null if not found.
     */
    suspend fun getById(id: String): TaskRequest?
}
