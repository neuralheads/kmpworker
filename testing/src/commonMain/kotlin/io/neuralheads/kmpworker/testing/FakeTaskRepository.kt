package io.neuralheads.kmpworker.testing

import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskType
import io.neuralheads.kmpworker.persistence.TaskRepository

/**
 * In-memory [TaskRepository] for use in tests.
 *
 * No SQLDelight or database required.
 * All data is stored in a mutable list and cleared between tests.
 *
 * Usage:
 * ```kotlin
 * val repo = FakeTaskRepository()
 * repo.insert(TaskRequest(id = "task-1", type = TaskType.OneTime))
 * assertEquals(1, repo.getPending().size)
 * ```
 */
class FakeTaskRepository : TaskRepository {

    data class StoredTask(
        val request: TaskRequest,
        var status: String = "PENDING",
        var retryCount: Int = 0
    )

    private val store = mutableListOf<StoredTask>()

    override suspend fun insert(task: TaskRequest) {
        if (store.none { it.request.id == task.id }) {
            store.add(StoredTask(request = task))
        }
    }

    override suspend fun updateStatus(id: String, status: String, retryCount: Int) {
        store.find { it.request.id == id }?.let {
            it.status = status
            it.retryCount = retryCount
        }
    }

    override suspend fun delete(id: String) {
        store.removeAll { it.request.id == id }
    }

    override suspend fun getAll(): List<TaskRequest> =
        store.map { it.request }

    override suspend fun getPending(): List<TaskRequest> =
        store
            .filter { it.status == "PENDING" || it.status == "RUNNING" }
            .map { it.request }

    override suspend fun getById(id: String): TaskRequest? =
        store.find { it.request.id == id }?.request

    /** Returns the stored status for a task ID. For assertion in tests. */
    fun statusFor(id: String): String? =
        store.find { it.request.id == id }?.status

    /** Clears all stored tasks. */
    fun reset() = store.clear()
}
