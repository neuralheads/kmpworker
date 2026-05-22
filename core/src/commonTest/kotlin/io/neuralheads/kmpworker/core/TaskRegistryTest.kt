package io.neuralheads.kmpworker.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaskRegistryTest {

    @Test
    fun `register and execute handler`() = runTest {
        TaskRegistry.clearAll()
        var called = false
        TaskRegistry.register("test-task") { called = true }
        TaskRegistry.execute("test-task")
        assertTrue(called)
    }

    @Test
    fun `execute throws on unregistered task`() = runTest {
        TaskRegistry.clearAll()
        assertFailsWith<IllegalStateException> {
            TaskRegistry.execute("unknown-task")
        }
    }

    @Test
    fun `isRegistered returns correct value`() {
        TaskRegistry.clearAll()
        TaskRegistry.register("task-a") { }
        assertTrue(TaskRegistry.isRegistered("task-a"))
    }

    @Test
    fun `unregister removes handler`() = runTest {
        TaskRegistry.clearAll()
        TaskRegistry.register("task-b") { }
        TaskRegistry.unregister("task-b")
        assertFailsWith<IllegalStateException> {
            TaskRegistry.execute("task-b")
        }
    }

    @Test
    fun `later registration overwrites earlier one`() = runTest {
        TaskRegistry.clearAll()
        var value = 0
        TaskRegistry.register("overwrite-task") { value = 1 }
        TaskRegistry.register("overwrite-task") { value = 2 }
        TaskRegistry.execute("overwrite-task")
        assertEquals(2, value)
    }
}
