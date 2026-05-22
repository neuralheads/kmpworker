package io.neuralheads.kmpworker.queue

import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.core.TaskType
import io.neuralheads.kmpworker.testing.FakeKmpWorker
import io.neuralheads.kmpworker.testing.FakeNetworkMonitor
import io.neuralheads.kmpworker.testing.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineQueueTest {

    private fun buildQueue(
        online: Boolean = true
    ): Triple<OfflineQueue, FakeKmpWorker, FakeTaskRepository> {
        val worker = FakeKmpWorker()
        val repo = FakeTaskRepository()
        val monitor = FakeNetworkMonitor(initiallyOnline = online)
        val queue = OfflineQueue(worker, repo, monitor)
        queue.start()

        val task = TaskRequest(id = "default-task", type = TaskType.OneTime)
        worker.register(task.id) { /* no-op */ }

        return Triple(queue, worker, repo)
    }

    @Test
    fun `enqueue when online dispatches task immediately`() = runTest {
        val (queue, worker, repo) = buildQueue(online = true)
        val request = TaskRequest(id = "task-1", type = TaskType.OneTime)
        worker.register(request.id) {}

        queue.enqueue(request)

        assertTrue(worker.wasEnqueued(request.id))
        assertEquals(0, repo.getAll().size) // should NOT be persisted
    }

    @Test
    fun `enqueue when offline persists task to repository`() = runTest {
        val (queue, worker, repo) = buildQueue(online = false)
        val request = TaskRequest(id = "task-2", type = TaskType.OneTime)

        queue.enqueue(request)

        assertFalse(worker.wasEnqueued(request.id))
        assertEquals(1, repo.getAll().size)
        assertEquals("task-2", repo.getAll().first().id)
    }

    @Test
    fun `replay dispatches all pending tasks`() = runTest {
        val worker = FakeKmpWorker()
        val repo = FakeTaskRepository()
        val monitor = FakeNetworkMonitor(initiallyOnline = false)
        val queue = OfflineQueue(worker, repo, monitor)
        queue.start()

        val task1 = TaskRequest(id = "replay-1", type = TaskType.OneTime)
        val task2 = TaskRequest(id = "replay-2", type = TaskType.OneTime)
        worker.register(task1.id) {}
        worker.register(task2.id) {}

        repo.insert(task1)
        repo.insert(task2)

        queue.replay()

        assertTrue(worker.wasEnqueued(task1.id))
        assertTrue(worker.wasEnqueued(task2.id))
        assertEquals(0, repo.getAll().size) // should be cleared after replay
    }

    @Test
    fun `replay skips already delivered tasks`() = runTest {
        val (queue, worker, repo) = buildQueue(online = false)

        val request = TaskRequest(id = "once-task", type = TaskType.OneTime)
        worker.register(request.id) {}
        repo.insert(request)
        repo.updateStatus(request.id, "SUCCESS", 0)

        queue.replay() // Only PENDING/RUNNING are replayed

        assertFalse(worker.wasEnqueued(request.id))
    }

    @Test
    fun `start is idempotent`() = runTest {
        val worker = FakeKmpWorker()
        val repo = FakeTaskRepository()
        val monitor = FakeNetworkMonitor()
        val queue = OfflineQueue(worker, repo, monitor)

        queue.start()
        queue.start() // Should not throw or duplicate subscriptions
        queue.start()
    }
}
