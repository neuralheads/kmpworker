package io.neuralheads.kmpworker.testing

import io.neuralheads.kmpworker.core.TaskRequest
import io.neuralheads.kmpworker.core.TaskState
import io.neuralheads.kmpworker.core.TaskType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeKmpWorkerTest {

    @Test
    fun `register and enqueue succeeds`() = runTest {
        val fake = FakeKmpWorker()
        fake.register("task-a") { /* no-op */ }
        fake.enqueue(TaskRequest("task-a", TaskType.OneTime))

        assertTrue(fake.wasEnqueued("task-a"))
        assertEquals(TaskState.Success, fake.lastStateFor("task-a"))
    }

    @Test
    fun `fails with no registered handler`() = runTest {
        val fake = FakeKmpWorker()
        fake.enqueue(TaskRequest("unregistered", TaskType.OneTime))

        val state = fake.lastStateFor("unregistered")
        assertIs<TaskState.Failed>(state)
    }

    @Test
    fun `cancel tracks task id`() = runTest {
        val fake = FakeKmpWorker()
        fake.cancel("task-x")
        assertTrue(fake.wasCancelled("task-x"))
        assertFalse(fake.wasCancelled("task-y"))
    }

    @Test
    fun `failure count simulates retries`() = runTest {
        val fake = FakeKmpWorker()
        fake.register("flaky") { /* succeeds */ }
        fake.failureCount["flaky"] = 2

        // First two enqueues should fail
        fake.enqueue(TaskRequest("flaky", TaskType.OneTime))
        assertIs<TaskState.Failed>(fake.lastStateFor("flaky"))

        fake.enqueue(TaskRequest("flaky", TaskType.OneTime))
        assertIs<TaskState.Failed>(fake.lastStateFor("flaky"))

        // Third should succeed
        fake.enqueue(TaskRequest("flaky", TaskType.OneTime))
        assertEquals(TaskState.Success, fake.lastStateFor("flaky"))
    }

    @Test
    fun `allStatesFor returns complete state sequence`() = runTest {
        val fake = FakeKmpWorker()
        fake.register("seq") { /* no-op */ }
        fake.enqueue(TaskRequest("seq", TaskType.OneTime))

        val states = fake.allStatesFor("seq")
        assertTrue(states.contains(TaskState.Scheduled))
        assertTrue(states.contains(TaskState.Running))
        assertTrue(states.contains(TaskState.Success))
    }

    @Test
    fun `reset clears all state`() = runTest {
        val fake = FakeKmpWorker()
        fake.register("r") {}
        fake.enqueue(TaskRequest("r", TaskType.OneTime))
        fake.cancel("r")

        fake.reset()

        assertFalse(fake.wasEnqueued("r"))
        assertFalse(fake.wasCancelled("r"))
        assertNull(fake.lastStateFor("r"))
    }

    @Test
    fun `handler throwing exception emits Failed state`() = runTest {
        val fake = FakeKmpWorker()
        fake.register("boom") { throw RuntimeException("Kaboom") }
        fake.enqueue(TaskRequest("boom", TaskType.OneTime))

        val state = fake.lastStateFor("boom")
        assertIs<TaskState.Failed>(state)
        assertEquals("Kaboom", (state as TaskState.Failed).throwable.message)
    }
}
